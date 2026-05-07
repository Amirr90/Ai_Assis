package com.example.ai_assis.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai_assis.domain.model.CustomTemplate
import com.example.ai_assis.domain.model.LanguagePreference
import com.example.ai_assis.domain.model.MemoryDepth
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import com.example.ai_assis.domain.repository.TemplateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.featureDataStore by preferencesDataStore(name = "feature_preferences")

@Singleton
class FeaturePreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : FeaturePreferencesRepository, TemplateRepository {

    override val aiEnabledFlow: Flow<Boolean> = context.featureDataStore.data.map { prefs ->
        prefs[aiEnabledKey] ?: true
    }

    override val replyLengthFlow: Flow<ReplyLength> = context.featureDataStore.data.map { prefs ->
        prefs[replyLengthKey]?.let { raw -> ReplyLength.entries.firstOrNull { it.name == raw } } ?: ReplyLength.MEDIUM
    }

    override val adaptiveRepliesFlow: Flow<Boolean> = context.featureDataStore.data.map { prefs ->
        prefs[adaptiveRepliesKey] ?: true
    }

    override val memoryDepthFlow: Flow<MemoryDepth> = context.featureDataStore.data.map { prefs ->
        prefs[memoryDepthKey]?.let { raw -> MemoryDepth.entries.firstOrNull { it.name == raw } }
            ?: MemoryDepth.BALANCED
    }

    override val languagePreferenceFlow: Flow<LanguagePreference> = context.featureDataStore.data.map { prefs ->
        prefs[languagePreferenceKey]?.let { raw -> LanguagePreference.entries.firstOrNull { it.name == raw } }
            ?: LanguagePreference.AUTO
    }

    override val rememberContextFlow: Flow<Boolean> = context.featureDataStore.data.map { prefs ->
        prefs[rememberContextKey] ?: true
    }

    override val templatesFlow: Flow<List<CustomTemplate>> = context.featureDataStore.data.map { prefs ->
        decodeTemplates(prefs[templatesKey])
    }

    override suspend fun setAiEnabled(enabled: Boolean) {
        context.featureDataStore.edit { prefs -> prefs[aiEnabledKey] = enabled }
    }

    override suspend fun setReplyLength(length: ReplyLength) {
        context.featureDataStore.edit { prefs -> prefs[replyLengthKey] = length.name }
    }

    override suspend fun setAdaptiveRepliesEnabled(enabled: Boolean) {
        context.featureDataStore.edit { prefs -> prefs[adaptiveRepliesKey] = enabled }
    }

    override suspend fun setMemoryDepth(depth: MemoryDepth) {
        context.featureDataStore.edit { prefs -> prefs[memoryDepthKey] = depth.name }
    }

    override suspend fun setLanguagePreference(preference: LanguagePreference) {
        context.featureDataStore.edit { prefs -> prefs[languagePreferenceKey] = preference.name }
    }

    override suspend fun setRememberContext(enabled: Boolean) {
        context.featureDataStore.edit { prefs -> prefs[rememberContextKey] = enabled }
    }

    override suspend fun setAppLengthOverride(appPackage: String, length: ReplyLength?) {
        updateOverrides { current ->
            current.copy(
                lengthOverrides = current.lengthOverrides
                    .toMutableMap()
                    .apply {
                        if (length == null) remove(appPackage) else put(appPackage, length.name)
                    },
            )
        }
    }

    override suspend fun getAppLengthOverride(appPackage: String): ReplyLength? {
        val overrides = loadOverrides()
        return overrides.lengthOverrides[appPackage]?.let { raw -> ReplyLength.entries.firstOrNull { it.name == raw } }
    }

    override suspend fun setAppMemoryDepthOverride(appPackage: String, depth: MemoryDepth?) {
        updateOverrides { current ->
            current.copy(
                memoryDepthOverrides = current.memoryDepthOverrides
                    .toMutableMap()
                    .apply {
                        if (depth == null) remove(appPackage) else put(appPackage, depth.name)
                    },
            )
        }
    }

    override suspend fun getAppMemoryDepthOverride(appPackage: String): MemoryDepth? {
        val overrides = loadOverrides()
        return overrides.memoryDepthOverrides[appPackage]?.let { raw ->
            MemoryDepth.entries.firstOrNull { it.name == raw }
        }
    }

    override suspend fun saveTemplate(text: String, appPackage: String?) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        context.featureDataStore.edit { prefs ->
            val current = decodeTemplates(prefs[templatesKey]).toMutableList()
            val duplicate = current.firstOrNull {
                it.text.equals(normalizedText, ignoreCase = true) && it.appPackage == appPackage
            }
            if (duplicate != null) return@edit
            current += CustomTemplate(
                id = UUID.randomUUID().toString(),
                text = normalizedText,
                appPackage = appPackage,
                lastUsedAtMs = System.currentTimeMillis(),
            )
            prefs[templatesKey] = encodeTemplates(current.takeLast(maxTemplates))
        }
    }

    override suspend fun deleteTemplate(templateId: String) {
        context.featureDataStore.edit { prefs ->
            val updated = decodeTemplates(prefs[templatesKey]).filterNot { it.id == templateId }
            prefs[templatesKey] = encodeTemplates(updated)
        }
    }

    override suspend fun markTemplateUsed(templateId: String) {
        context.featureDataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val updated = decodeTemplates(prefs[templatesKey]).map { template ->
                if (template.id == templateId) template.copy(lastUsedAtMs = now) else template
            }
            prefs[templatesKey] = encodeTemplates(updated)
        }
    }

    private suspend fun updateOverrides(transform: (AppOverridePayload) -> AppOverridePayload) {
        context.featureDataStore.edit { prefs ->
            val current = decodeOverrides(prefs[appOverridesKey])
            prefs[appOverridesKey] = json.encodeToString(transform(current))
        }
    }

    private suspend fun loadOverrides(): AppOverridePayload {
        val prefs = context.featureDataStore.data.first()
        return decodeOverrides(prefs[appOverridesKey])
    }

    private fun decodeTemplates(raw: String?): List<CustomTemplate> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CustomTemplate>>(raw) }.getOrDefault(emptyList())
    }

    private fun encodeTemplates(list: List<CustomTemplate>): String = json.encodeToString(list)

    private fun decodeOverrides(raw: String?): AppOverridePayload {
        if (raw.isNullOrBlank()) return AppOverridePayload()
        return runCatching { json.decodeFromString<AppOverridePayload>(raw) }.getOrDefault(AppOverridePayload())
    }

    @Serializable
    private data class AppOverridePayload(
        /** Legacy tone overrides ignored at runtime but kept for decode compatibility. */
        val toneOverrides: Map<String, String> = emptyMap(),
        val lengthOverrides: Map<String, String> = emptyMap(),
        val memoryDepthOverrides: Map<String, String> = emptyMap(),
    )

    private companion object {
        val aiEnabledKey = booleanPreferencesKey("ai_enabled")
        val replyLengthKey = stringPreferencesKey("reply_length")
        val adaptiveRepliesKey = booleanPreferencesKey("adaptive_replies_enabled")
        val memoryDepthKey = stringPreferencesKey("memory_depth")
        val languagePreferenceKey = stringPreferencesKey("language_preference")
        val rememberContextKey = booleanPreferencesKey("remember_context_enabled")
        val appOverridesKey = stringPreferencesKey("app_overrides")
        val templatesKey = stringPreferencesKey("custom_templates")
        val json = Json { ignoreUnknownKeys = true }
        const val maxTemplates = 40
    }
}
