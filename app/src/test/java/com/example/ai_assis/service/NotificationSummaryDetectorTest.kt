package com.example.ai_assis.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSummaryDetectorTest {
    @Test
    fun `detects grouped summary format`() {
        assertTrue(NotificationSummaryDetector.isLikelySummaryText("3 messages from 2 chats"))
        assertTrue(NotificationSummaryDetector.isLikelySummaryText("12 new messages"))
        assertTrue(NotificationSummaryDetector.isLikelySummaryText("new messages in multiple chats"))
    }

    @Test
    fun `does not mark normal message as summary`() {
        assertFalse(NotificationSummaryDetector.isLikelySummaryText("Are you coming now?"))
        assertFalse(NotificationSummaryDetector.isLikelySummaryText("Let's meet at 7"))
    }
}
