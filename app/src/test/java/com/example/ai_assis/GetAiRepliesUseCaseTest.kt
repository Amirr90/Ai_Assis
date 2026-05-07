package com.example.ai_assis

import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.OpenAiRepository
import com.example.ai_assis.domain.usecase.GetAiRepliesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetAiRepliesUseCaseTest {

    private val openAiRepository: OpenAiRepository = mockk()

    @Test
    fun `emits replies on success`() = runTest {
        val expectedReplies = listOf("Sounds good!")
        coEvery { openAiRepository.getReplies(any(), any()) } returns expectedReplies
        val useCase = GetAiRepliesUseCase(openAiRepository)

        val result = useCase("Let's meet tomorrow").first()

        assertTrue(result.isSuccess)
        assertEquals(expectedReplies, result.getOrNull())
        coVerify(exactly = 1) { openAiRepository.getReplies("Let's meet tomorrow", ReplyTone.CASUAL) }
    }

    @Test
    fun `emits failure when repository throws`() = runTest {
        coEvery { openAiRepository.getReplies(any(), any()) } throws IllegalStateException("network")
        val useCase = GetAiRepliesUseCase(openAiRepository)

        val result = useCase("Hi").first()

        assertTrue(result.isFailure)
    }
}
