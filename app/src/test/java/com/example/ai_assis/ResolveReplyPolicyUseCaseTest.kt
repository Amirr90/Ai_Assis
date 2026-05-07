package com.example.ai_assis

import com.example.ai_assis.domain.model.MemoryDepth
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import com.example.ai_assis.domain.usecase.ResolveReplyPolicyUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveReplyPolicyUseCaseTest {

    @Test
    fun `resolveLength uses app override when present`() = runTest {
        val repo = FakeFeaturePreferencesRepo().apply {
            lengthOverrides["com.whatsapp"] = ReplyLength.DETAILED
        }
        val useCase = ResolveReplyPolicyUseCase(repo)
        val length = useCase.resolveLength("com.whatsapp", ReplyLength.SHORT)
        assertEquals(ReplyLength.DETAILED, length)
    }

    @Test
    fun `resolveMemoryDepth uses app override when present`() = runTest {
        val repo = FakeFeaturePreferencesRepo().apply {
            memoryOverrides["com.whatsapp"] = MemoryDepth.RICH
        }
        val useCase = ResolveReplyPolicyUseCase(repo)
        val depth = useCase.resolveMemoryDepth("com.whatsapp", MemoryDepth.LIGHT)
        assertEquals(MemoryDepth.RICH, depth)
    }

    private class FakeFeaturePreferencesRepo : FeaturePreferencesRepository {
        val lengthOverrides = mutableMapOf<String, ReplyLength>()
        val memoryOverrides = mutableMapOf<String, MemoryDepth>()

        override val aiEnabledFlow = flowOf(true)
        override val replyLengthFlow = flowOf(ReplyLength.MEDIUM)
        override val adaptiveRepliesFlow = flowOf(true)
        override val memoryDepthFlow = flowOf(MemoryDepth.BALANCED)
        override val languagePreferenceFlow = flowOf(com.example.ai_assis.domain.model.LanguagePreference.AUTO)
        override val rememberContextFlow = flowOf(true)

        override suspend fun setAiEnabled(enabled: Boolean) = Unit
        override suspend fun setReplyLength(length: ReplyLength) = Unit
        override suspend fun setAdaptiveRepliesEnabled(enabled: Boolean) = Unit
        override suspend fun setMemoryDepth(depth: MemoryDepth) = Unit
        override suspend fun setLanguagePreference(preference: com.example.ai_assis.domain.model.LanguagePreference) = Unit
        override suspend fun setRememberContext(enabled: Boolean) = Unit

        override suspend fun setAppLengthOverride(appPackage: String, length: ReplyLength?) {
            if (length == null) lengthOverrides.remove(appPackage) else lengthOverrides[appPackage] = length
        }

        override suspend fun getAppLengthOverride(appPackage: String): ReplyLength? = lengthOverrides[appPackage]

        override suspend fun setAppMemoryDepthOverride(appPackage: String, depth: MemoryDepth?) {
            if (depth == null) memoryOverrides.remove(appPackage) else memoryOverrides[appPackage] = depth
        }

        override suspend fun getAppMemoryDepthOverride(appPackage: String): MemoryDepth? = memoryOverrides[appPackage]
    }
}
