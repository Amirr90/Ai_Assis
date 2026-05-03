package com.example.ai_assis.data.remote

import com.example.ai_assis.data.remote.model.SubscriptionType
import com.example.ai_assis.data.remote.model.UserUsageRecord
import com.example.ai_assis.presentation.ui.screen.PricingPlan
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface FirestoreUsageRepository {
    fun observeUserRecord(uid: String): Flow<UserUsageRecord>
    suspend fun canGenerate(uid: String): Boolean
    suspend fun getOrCreateUser(uid: String): UserUsageRecord
    /** [dateKey] = "YYYY-MM-DD", [hourKey] = "YYYY-MM-DD_HH" */
    suspend fun incrementSuggestion(uid: String, dateKey: String, hourKey: String): Result<Unit>
    suspend fun setSubscription(uid: String, plan: PricingPlan, creditsToAdd: Int = 0)
}

@Singleton
class FirestoreUsageRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : FirestoreUsageRepository {

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
        val doc = userDoc(uid).get().await()
        return if (doc.exists()) {
            doc.toUserUsageRecord()
        } else {
            val default = UserUsageRecord()
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

                when (record.subscriptionType) {
                    SubscriptionType.FREE -> {
                        updates["freeUsageCount"] = record.freeUsageCount + 1
                    }
                    SubscriptionType.CREDITS -> {
                        if (record.creditsRemaining > 0) {
                            updates["creditsRemaining"] = record.creditsRemaining - 1
                        }
                    }
                    // Monthly / Yearly: unlimited, only analytics counts incremented
                }

                transaction.update(ref, updates)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setSubscription(uid: String, plan: PricingPlan, creditsToAdd: Int) {
        val subscriptionType = when (plan) {
            PricingPlan.Free -> SubscriptionType.FREE
            PricingPlan.Monthly -> SubscriptionType.MONTHLY
            PricingPlan.Yearly -> SubscriptionType.YEARLY
            PricingPlan.Credits -> SubscriptionType.CREDITS
        }

        val updates = mutableMapOf<String, Any>(
            "subscriptionType" to subscriptionType,
        )

        if (plan == PricingPlan.Credits && creditsToAdd > 0) {
            val current = try {
                userDoc(uid).get().await().getLong("creditsRemaining")?.toInt() ?: 0
            } catch (_: Exception) {
                0
            }
            updates["creditsRemaining"] = current + creditsToAdd
        }

        userDoc(uid).set(updates, SetOptions.merge()).await()
    }

    private companion object {
        const val USERS_COLLECTION = "users"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUserUsageRecord(): UserUsageRecord {
    if (!exists()) return UserUsageRecord()
    @Suppress("UNCHECKED_CAST")
    fun mapOf(field: String): Map<String, Int> =
        (get(field) as? Map<String, Any>)
            ?.mapValues { (_, v) -> (v as? Long)?.toInt() ?: (v as? Int) ?: 0 }
            ?: emptyMap()
    return UserUsageRecord(
        subscriptionType = getString("subscriptionType") ?: SubscriptionType.FREE,
        creditsRemaining = getLong("creditsRemaining")?.toInt() ?: 0,
        freeUsageCount = getLong("freeUsageCount")?.toInt() ?: 0,
        dailyCounts = mapOf("dailyCounts"),
        hourlyCounts = mapOf("hourlyCounts"),
    )
}

private fun UserUsageRecord.toMap(): Map<String, Any> = mapOf(
    "subscriptionType" to subscriptionType,
    "creditsRemaining" to creditsRemaining,
    "freeUsageCount" to freeUsageCount,
    "dailyCounts" to dailyCounts,
    "hourlyCounts" to hourlyCounts,
)
