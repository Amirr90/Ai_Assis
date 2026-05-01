package com.example.ai_assis

import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import com.example.ai_assis.domain.usecase.ResolveReplyPolicyUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveReplyPolicyUseCaseTest {
    @Test
    fun `app override wins over defaults`() = runTest {
        val repo = FakeFeaturePreferencesRepository().apply {
            setAppToneOverride("com.whatsapp", ReplyTone.PROFESSIONAL)
            setAppLengthOverride("com.whatsapp", ReplyLength.DETAILED)
        }
        val useCase = ResolveReplyPolicyUseCase(repo)

        val tone = useCase.resolveTone("com.whatsapp", ReplyTone.CASUAL)
        val length = useCase.resolveLength("com.whatsapp", ReplyLength.SHORT)

        assertEquals(ReplyTone.PROFESSIONAL, tone)
        assertEquals(ReplyLength.DETAILED, length)
    }
}

private class FakeFeaturePreferencesRepository : FeaturePreferencesRepository {
    override val aiEnabledFlow: Flow<Boolean> = MutableStateFlow(true)
    override val replyLengthFlow: Flow<ReplyLength> = MutableStateFlow(ReplyLength.MEDIUM)
    private val toneOverrides = mutableMapOf<String, ReplyTone>()
    private val lengthOverrides = mutableMapOf<String, ReplyLength>()

    override suspend fun setAiEnabled(enabled: Boolean) = Unit
    override suspend fun setReplyLength(length: ReplyLength) = Unit
    override suspend fun setAppToneOverride(appPackage: String, tone: ReplyTone?) {
        if (tone == null) toneOverrides.remove(appPackage) else toneOverrides[appPackage] = tone
    }
    override suspend fun setAppLengthOverride(appPackage: String, length: ReplyLength?) {
        if (length == null) lengthOverrides.remove(appPackage) else lengthOverrides[appPackage] = length
    }
    override suspend fun getAppToneOverride(appPackage: String): ReplyTone? = toneOverrides[appPackage]
    override suspend fun getAppLengthOverride(appPackage: String): ReplyLength? = lengthOverrides[appPackage]
}
