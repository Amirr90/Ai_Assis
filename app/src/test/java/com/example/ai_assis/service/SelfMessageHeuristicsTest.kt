package com.example.ai_assis.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfMessageHeuristicsTest {
    @Test
    fun `marks explicit self sender tokens as self messages`() {
        assertTrue(SelfMessageHeuristics.isLikelySelfSender("You", "com.whatsapp"))
        assertTrue(SelfMessageHeuristics.isLikelySelfSender("me:", "com.whatsapp"))
    }

    @Test
    fun `does not mark regular sender names as self messages`() {
        assertFalse(SelfMessageHeuristics.isLikelySelfSender("Ayesha", "com.whatsapp"))
        assertFalse(SelfMessageHeuristics.isLikelySelfSender("", "com.whatsapp"))
    }

    @Test
    fun `marks fallback message text with self prefix`() {
        assertTrue(SelfMessageHeuristics.isLikelySelfText("You: on my way"))
        assertTrue(SelfMessageHeuristics.isLikelySelfText("me hello"))
        assertFalse(SelfMessageHeuristics.isLikelySelfText("Ayesha: hello"))
    }
}
