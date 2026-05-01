package com.example.ai_assis.domain.repository

import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.ReplyTone
import kotlinx.coroutines.flow.Flow

interface FeaturePreferencesRepository {
    val aiEnabledFlow: Flow<Boolean>
    val replyLengthFlow: Flow<ReplyLength>

    suspend fun setAiEnabled(enabled: Boolean)
    suspend fun setReplyLength(length: ReplyLength)
    suspend fun setAppToneOverride(appPackage: String, tone: ReplyTone?)
    suspend fun setAppLengthOverride(appPackage: String, length: ReplyLength?)
    suspend fun getAppToneOverride(appPackage: String): ReplyTone?
    suspend fun getAppLengthOverride(appPackage: String): ReplyLength?
}
