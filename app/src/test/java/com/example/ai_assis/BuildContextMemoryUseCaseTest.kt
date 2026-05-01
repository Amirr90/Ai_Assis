package com.example.ai_assis

import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.usecase.BuildContextMemoryUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildContextMemoryUseCaseTest {
    @Test
    fun `keeps two previous and latest message`() {
        val useCase = BuildContextMemoryUseCase()
        val result = useCase(
            message = ChatMessage(sender = "Sam", message = "latest", appSource = "com.whatsapp"),
            history = listOf("old1", "old2", "old3"),
        )
        assertEquals(listOf("old2", "old3", "latest"), result)
    }
}
