package com.example.ai_assis.data.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterManagerTest {
    private val filter = FilterManager()

    @Test
    fun `ignores WhatsApp Business app package`() {
        assertEquals(
            FilterResult.Ignore,
            filter.shouldProcess(
                packageName = "com.whatsapp.w4b",
                title = "X",
                message = "Hi",
            ),
        )
    }

    @Test
    fun `detects business marker phrases on consumer WhatsApp`() {
        assertEquals(
            FilterResult.Ignore,
            filter.shouldProcess(
                packageName = "com.whatsapp",
                title = "Some Sender",
                message = "Hello",
                extras = FilterExtras(
                    sender = "Official Business Account",
                ),
            ),
        )
        assertEquals(
            FilterResult.Ignore,
            filter.shouldProcess(
                packageName = "com.whatsapp",
                title = "Meta Verified",
                message = "Hello",
            ),
        )
    }

    @Test
    fun `allows normal private WhatsApp senders`() {
        val result = filter.shouldProcess(
            packageName = "com.whatsapp",
            title = "Rahul",
            message = "Where are you?",
        )
        assertTrue(result is FilterResult.Process)
    }

    @Test
    fun `ignores disallowed package`() {
        assertEquals(
            FilterResult.Ignore,
            filter.shouldProcess(
                packageName = "com.example.other",
                title = "A",
                message = "Hi",
            ),
        )
    }

    @Test
    fun `media path for photo keyword`() {
        assertEquals(
            FilterResult.Media,
            filter.shouldProcess(
                packageName = "com.whatsapp",
                title = "A",
                message = "Sent a photo",
            ),
        )
    }

    @Test
    fun `layer2 does not match web inside website`() {
        val result = filter.shouldProcess(
            packageName = "com.whatsapp",
            title = "A",
            message = "Check this website for details",
        )
        assertTrue(result is FilterResult.Process)
    }
}
