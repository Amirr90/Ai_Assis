package com.example.ai_assis.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentPolicyTest {
    @Test
    fun `allows normal notification text from fallback`() {
        assertTrue(
            NotificationContentPolicy.shouldEmit(
                isSummary = false,
                sourceHint = "extras_fallback",
            ),
        )
    }

    @Test
    fun `blocks grouped summary text from fallback`() {
        assertFalse(
            NotificationContentPolicy.shouldEmit(
                isSummary = true,
                sourceHint = "extras_fallback",
            ),
        )
    }

    @Test
    fun `allows messaging style even when summary detector matches`() {
        assertTrue(
            NotificationContentPolicy.shouldEmit(
                isSummary = true,
                sourceHint = "messaging_style",
            ),
        )
    }
}
