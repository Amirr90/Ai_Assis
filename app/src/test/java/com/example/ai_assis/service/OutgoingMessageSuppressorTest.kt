package com.example.ai_assis.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingMessageSuppressorTest {
    @Test
    fun `suppresses matching incoming message in time window`() {
        val now = 100_000L
        OutgoingMessageSuppressor.registerOutgoing(
            packageName = "com.whatsapp",
            text = "On my way",
            nowMs = now,
        )

        assertTrue(
            OutgoingMessageSuppressor.shouldSuppressIncoming(
                packageName = "com.whatsapp",
                text = " on   my way ",
                nowMs = now + 2_000L,
            ),
        )
    }

    @Test
    fun `does not suppress once entry has expired`() {
        val now = 300_000L
        OutgoingMessageSuppressor.registerOutgoing(
            packageName = "com.whatsapp",
            text = "Reached",
            nowMs = now,
        )

        assertFalse(
            OutgoingMessageSuppressor.shouldSuppressIncoming(
                packageName = "com.whatsapp",
                text = "Reached",
                nowMs = now + 11_000L,
            ),
        )
    }

    @Test
    fun `normalization suppresses punctuation variants once`() {
        val now = 500_000L
        OutgoingMessageSuppressor.registerOutgoing(
            packageName = "com.instagram.android",
            text = "Seen 👍",
            nowMs = now,
        )

        assertTrue(
            OutgoingMessageSuppressor.shouldSuppressIncoming(
                packageName = "com.instagram.android",
                text = " seen ",
                nowMs = now + 1_000L,
            ),
        )
        assertFalse(
            OutgoingMessageSuppressor.shouldSuppressIncoming(
                packageName = "com.instagram.android",
                text = "seen",
                nowMs = now + 1_500L,
            ),
        )
    }

    @Test
    fun `suppresses incoming text with leading self prefix`() {
        val now = 700_000L
        OutgoingMessageSuppressor.registerOutgoing(
            packageName = "com.whatsapp",
            text = "hello",
            nowMs = now,
        )

        assertTrue(
            OutgoingMessageSuppressor.shouldSuppressIncoming(
                packageName = "com.whatsapp",
                text = "You: hello",
                nowMs = now + 800L,
            ),
        )
    }
}
