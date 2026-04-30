package com.example.ai_assis.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessMessageDetectorTest {
    @Test
    fun `detects business marker phrases`() {
        assertTrue(
            BusinessMessageDetector.hasBusinessMarkers(
                listOf("Official Business Account", "Some Sender"),
            ),
        )
        assertTrue(
            BusinessMessageDetector.hasBusinessMarkers(
                listOf("Meta Verified"),
            ),
        )
    }

    @Test
    fun `ignores normal private sender names`() {
        assertFalse(
            BusinessMessageDetector.hasBusinessMarkers(
                listOf("Rahul", "Where are you?"),
            ),
        )
    }
}
