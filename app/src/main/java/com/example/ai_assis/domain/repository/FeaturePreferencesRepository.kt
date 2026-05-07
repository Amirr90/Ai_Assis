package com.example.ai_assis.domain.repository

import com.example.ai_assis.domain.model.LanguagePreference
import com.example.ai_assis.domain.model.MemoryDepth
import com.example.ai_assis.domain.model.ReplyLength
import kotlinx.coroutines.flow.Flow

interface FeaturePreferencesRepository {
    val aiEnabledFlow: Flow<Boolean>
    val replyLengthFlow: Flow<ReplyLength>
    val adaptiveRepliesFlow: Flow<Boolean>
    val memoryDepthFlow: Flow<MemoryDepth>
    val languagePreferenceFlow: Flow<LanguagePreference>
    val rememberContextFlow: Flow<Boolean>

    suspend fun setAiEnabled(enabled: Boolean)
    suspend fun setReplyLength(length: ReplyLength)
    suspend fun setAdaptiveRepliesEnabled(enabled: Boolean)
    suspend fun setMemoryDepth(depth: MemoryDepth)
    suspend fun setLanguagePreference(preference: LanguagePreference)
    suspend fun setRememberContext(enabled: Boolean)

    suspend fun setAppLengthOverride(appPackage: String, length: ReplyLength?)
    suspend fun getAppLengthOverride(appPackage: String): ReplyLength?

    suspend fun setAppMemoryDepthOverride(appPackage: String, depth: MemoryDepth?)
    suspend fun getAppMemoryDepthOverride(appPackage: String): MemoryDepth?
}
