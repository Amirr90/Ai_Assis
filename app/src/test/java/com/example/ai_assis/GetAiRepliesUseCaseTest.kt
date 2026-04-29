package com.example.ai_assis

import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.OpenAiRepository
import com.example.ai_assis.domain.repository.ToneRepository
import com.example.ai_assis.domain.usecase.GetAiRepliesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetAiRepliesUseCaseTest {

    private val openAiRepository: OpenAiRepository = mockk()
    private val toneRepository: ToneRepository = mockk()
    private lateinit var useCase: GetAiRepliesUseCase

    @Before
    fun setUp() {
        useCase = GetAiRepliesUseCase(openAiRepository, toneRepository)
    }

    @Test
    fun `invoke emits success with replies when API call succeeds`() = runTest {
        val expectedReplies = listOf("Sure, on my way!", "Be there soon", "Running a bit late")
        every { toneRepository.toneFlow } returns flowOf(ReplyTone.CASUAL)
        coEvery { openAiRepository.getReplies(any(), any()) } returns expectedReplies

        val result = useCase("Are you coming today?").first()

        assertTrue(result.isSuccess)
        assertEquals(expectedReplies, result.getOrNull())
    }

    @Test
    fun `invoke emits failure when API throws exception`() = runTest {
        every { toneRepository.toneFlow } returns flowOf(ReplyTone.CASUAL)
        coEvery { openAiRepository.getReplies(any(), any()) } throws RuntimeException("Network error")

        val result = useCase("Hello?").first()

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke passes correct tone from repository to API`() = runTest {
        val expectedReplies = listOf("I will share the report shortly.")
        every { toneRepository.toneFlow } returns flowOf(ReplyTone.PROFESSIONAL)
        coEvery { openAiRepository.getReplies(any(), eq(ReplyTone.PROFESSIONAL)) } returns expectedReplies

        val result = useCase("Send me the report").first()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { openAiRepository.getReplies(any(), ReplyTone.PROFESSIONAL) }
    }

    @Test
    fun `invoke passes the message text to the API unchanged`() = runTest {
        val message = "kaha ho bhai"
        every { toneRepository.toneFlow } returns flowOf(ReplyTone.CASUAL)
        coEvery { openAiRepository.getReplies(eq(message), any()) } returns listOf("Bas aa raha hoon")

        val result = useCase(message).first()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { openAiRepository.getReplies(message, any()) }
    }

    @Test
    fun `invoke returns success with empty list when API returns no replies`() = runTest {
        every { toneRepository.toneFlow } returns flowOf(ReplyTone.SHORT)
        coEvery { openAiRepository.getReplies(any(), any()) } returns emptyList()

        val result = useCase("Hello?").first()

        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), result.getOrNull())
    }

    @Test
    fun `invoke emits failure when tone repository throws`() = runTest {
        every { toneRepository.toneFlow } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("DataStore error")
        }

        val result = useCase("Test message").first()

        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke always reads tone from repository before calling API`() = runTest {
        every { toneRepository.toneFlow } returns flowOf(ReplyTone.FUNNY)
        coEvery { openAiRepository.getReplies(any(), ReplyTone.FUNNY) } returns listOf("Ha, sure thing!")

        useCase("Come over?").first()

        coVerify(exactly = 1) { openAiRepository.getReplies(any(), ReplyTone.FUNNY) }
    }
}
