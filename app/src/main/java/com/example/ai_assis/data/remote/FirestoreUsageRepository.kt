package com.example.ai_assis.data.remote

import com.example.ai_assis.data.remote.model.OpenAiTokenUsage
import com.example.ai_assis.data.remote.model.PlanIds
import com.example.ai_assis.data.remote.model.PlanRecord
import com.example.ai_assis.data.remote.model.SubscriptionType
import com.example.ai_assis.data.remote.model.UserUsageRecord
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface FirestoreUsageRepository {
    fun observeUserRecord(uid: String): Flow<UserUsageRecord>
    fun observePlans(): Flow<List<PlanRecord>>
    suspend fun getPlans(): List<PlanRecord>
    suspend fun canGenerate(uid: String): Boolean
    suspend fun getOrCreateUser(uid: String): UserUsageRecord
    /** [dateKey] = "YYYY-MM-DD", [hourKey] = "YYYY-MM-DD_HH" */
    suspend fun incrementSuggestion(uid: String, dateKey: String, hourKey: String): Result<Unit>
    /** One successful OpenAI completion response; increments call count and token totals. */
    suspend fun recordOpenAiUsage(uid: String, usage: OpenAiTokenUsage?): Result<Unit>
}

@Singleton
class FirestoreUsageRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : FirestoreUsageRepository {
    override fun observePlans(): Flow<List<PlanRecord>> = callbackFlow {
        val registration = firestore.collection(PLANS_COLLECTION)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val plans = snapshot.documents.map { it.toPlanRecord() }.sortedBy { it.sortOrder }
                trySend(plans)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getPlans(): List<PlanRecord> {
        val snapshot = firestore.collection(PLANS_COLLECTION)
            .whereEqualTo("isActive", true)
            .get()
            .await()
        return snapshot.documents.map { it.toPlanRecord() }.sortedBy { it.sortOrder }
    }


    private fun userDoc(uid: String) = firestore.collection(USERS_COLLECTION).document(uid)

    override fun observeUserRecord(uid: String): Flow<UserUsageRecord> = callbackFlow {
        val registration = userDoc(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(snapshot.toUserUsageRecord())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun canGenerate(uid: String): Boolean {
        return try {
            val record = getOrCreateUser(uid)
            record.canGenerate()
        } catch (_: Exception) {
            // Fall back to allowing generation on network error to avoid blocking user
            true
        }
    }

    override suspend fun getOrCreateUser(uid: String): UserUsageRecord {
        runCatching {
            functions
                .getHttpsCallable(ENSURE_USER_ENTITLEMENTS_CALLABLE)
                .call(mapOf("uid" to uid))
                .await()
        }

        val doc = userDoc(uid).get().await()
        return if (doc.exists()) {
            val record = doc.toUserUsageRecord()
            if (record.isLegacyRecord()) {
                val migrated = record.migratedWithDefaults(nowMs = System.currentTimeMillis())
                userDoc(uid).set(migrated.toMap(), com.google.firebase.firestore.SetOptions.merge()).await()
                migrated
            } else {
                record
            }
        } else {
            val default = UserUsageRecord().migratedWithDefaults(nowMs = System.currentTimeMillis())
            userDoc(uid).set(default.toMap()).await()
            default
        }
    }

    override suspend fun incrementSuggestion(
        uid: String,
        dateKey: String,
        hourKey: String,
    ): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val ref = userDoc(uid)
                val snapshot = transaction.get(ref)
                val record = snapshot.toUserUsageRecord()

                val updates = mutableMapOf<String, Any>()

                // Always increment daily and hourly analytics counts
                val currentDaily = record.dailyCounts[dateKey] ?: 0
                updates["dailyCounts.$dateKey"] = currentDaily + 1
                val currentHourly = record.hourlyCounts[hourKey] ?: 0
                updates["hourlyCounts.$hourKey"] = currentHourly + 1

                when {
                    record.hasUnlimitedSuggestions() -> Unit
                    record.resolvedPlanId() == PlanIds.CREDITS -> {
                        if (record.creditsRemaining > 0) {
                            updates["creditsRemaining"] = record.creditsRemaining - 1
                        }
                    }
                    else -> {
                        updates["freeUsageCount"] = record.freeUsageCount + 1
                    }
                }

                transaction.update(ref, updates)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordOpenAiUsage(uid: String, usage: OpenAiTokenUsage?): Result<Unit> {
        val prompt = usage?.promptTokens?.toLong() ?: 0L
        val completion = usage?.completionTokens?.toLong() ?: 0L
        val total = usage?.totalTokens?.toLong() ?: 0L
        val ref = userDoc(uid)
        val updates = mapOf(
            FIELD_OPEN_AI_CALLS to FieldValue.increment(1),
            FIELD_OPEN_AI_PROMPT_TOKENS to FieldValue.increment(prompt),
            FIELD_OPEN_AI_COMPLETION_TOKENS to FieldValue.increment(completion),
            FIELD_OPEN_AI_TOTAL_TOKENS to FieldValue.increment(total),
        )
        return try {
            ref.update(updates).await()
            Result.success(Unit)
        } catch (_: Exception) {
            try {
                getOrCreateUser(uid)
                ref.update(updates).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val PLANS_COLLECTION = "plans"
        const val ENSURE_USER_ENTITLEMENTS_CALLABLE = "ensureUserEntitlements"
        const val FIELD_OPEN_AI_CALLS = "openAiApiCalls"
        const val FIELD_OPEN_AI_PROMPT_TOKENS = "openAiPromptTokensTotal"
        const val FIELD_OPEN_AI_COMPLETION_TOKENS = "openAiCompletionTokensTotal"
        const val FIELD_OPEN_AI_TOTAL_TOKENS = "openAiTotalTokensTotal"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUserUsageRecord(): UserUsageRecord {
    if (!exists()) return UserUsageRecord()
    @Suppress("UNCHECKED_CAST")
    fun mapOf(field: String): Map<String, Int> =
        (get(field) as? Map<String, Any>)
            ?.mapValues { (_, v) -> (v as? Long)?.toInt() ?: (v as? Int) ?: 0 }
            ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    fun longMapOf(field: String): Map<String, Long> =
        (get(field) as? Map<String, Any>)
            ?.mapValues { (_, v) -> (v as? Number)?.toLong() ?: 0L }
            ?: emptyMap()
    return UserUsageRecord(
        activePlanId = getString("activePlanId") ?: "",
        subscriptionType = getString("subscriptionType") ?: SubscriptionType.FREE,
        entitlements = longMapOf("entitlements"),
        creditsRemaining = getLong("creditsRemaining")?.toInt() ?: 0,
        freeUsageCount = getLong("freeUsageCount")?.toInt() ?: 0,
        planStartedAtMs = getLong("planStartedAtMs") ?: 0L,
        planUpdatedAtMs = getLong("planUpdatedAtMs") ?: 0L,
        planExpiresAtMs = getLong("planExpiresAtMs") ?: 0L,
        dailyCounts = mapOf("dailyCounts"),
        hourlyCounts = mapOf("hourlyCounts"),
        openAiApiCalls = getLong("openAiApiCalls") ?: 0L,
        openAiPromptTokensTotal = getLong("openAiPromptTokensTotal") ?: 0L,
        openAiCompletionTokensTotal = getLong("openAiCompletionTokensTotal") ?: 0L,
        openAiTotalTokensTotal = getLong("openAiTotalTokensTotal") ?: 0L,
    )
}

private fun UserUsageRecord.toMap(): Map<String, Any> = mapOf(
    "activePlanId" to activePlanId,
    "subscriptionType" to subscriptionType,
    "entitlements" to entitlements,
    "creditsRemaining" to creditsRemaining,
    "freeUsageCount" to freeUsageCount,
    "planStartedAtMs" to planStartedAtMs,
    "planUpdatedAtMs" to planUpdatedAtMs,
    "planExpiresAtMs" to planExpiresAtMs,
    "dailyCounts" to dailyCounts,
    "hourlyCounts" to hourlyCounts,
    "openAiApiCalls" to openAiApiCalls,
    "openAiPromptTokensTotal" to openAiPromptTokensTotal,
    "openAiCompletionTokensTotal" to openAiCompletionTokensTotal,
    "openAiTotalTokensTotal" to openAiTotalTokensTotal,
)

private fun com.google.firebase.firestore.DocumentSnapshot.toPlanRecord(): PlanRecord {
    @Suppress("UNCHECKED_CAST")
    fun mapLong(field: String): Map<String, Long> =
        (get(field) as? Map<String, Any>)
            ?.mapValues { (_, v) -> (v as? Number)?.toLong() ?: 0L }
            ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun stringList(field: String): List<String> =
        (get(field) as? List<Any>)?.mapNotNull { it as? String } ?: emptyList()

    return PlanRecord(
        planId = getString("planId") ?: id,
        name = getString("name") ?: id.replaceFirstChar { it.titlecase() },
        type = getString("type") ?: (getString("planId") ?: id),
        features = stringList("features"),
        limits = mapLong("limits"),
        price = getLong("price") ?: 0L,
        currency = getString("currency") ?: "INR",
        isActive = getBoolean("isActive") ?: true,
        sortOrder = getLong("sortOrder")?.toInt() ?: Int.MAX_VALUE,
        creditsToAdd = getLong("creditsToAdd")?.toInt() ?: 0,
    )
}
