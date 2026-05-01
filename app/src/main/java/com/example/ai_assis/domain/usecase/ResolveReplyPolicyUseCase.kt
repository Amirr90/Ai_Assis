package com.example.ai_assis.domain.usecase

import com.example.ai_assis.domain.model.AppBehaviorPolicies
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import javax.inject.Inject

class ResolveReplyPolicyUseCase @Inject constructor(
    private val featurePreferencesRepository: FeaturePreferencesRepository,
) {
    suspend fun resolveTone(appPackage: String, globalTone: ReplyTone): ReplyTone {
        return featurePreferencesRepository.getAppToneOverride(appPackage)
            ?: AppBehaviorPolicies.forApp(appPackage)?.defaultTone
            ?: globalTone
    }

    suspend fun resolveLength(appPackage: String, globalLength: ReplyLength): ReplyLength {
        return featurePreferencesRepository.getAppLengthOverride(appPackage)
            ?: AppBehaviorPolicies.forApp(appPackage)?.defaultLength
            ?: globalLength
    }
}
