package com.example.ai_assis.service

import com.example.ai_assis.domain.model.MessageType
import java.util.Locale

object MessageTypeDetector {
    fun detect(
        packageName: String,
        messageText: String,
        title: String,
        subText: String,
    ): MessageType {
        val normalizedMessage = messageText.normalize()
        val normalizedTitle = title.normalize()
        val normalizedSubText = subText.normalize()

        return when (packageName) {
            INSTAGRAM_PACKAGE -> detectInstagramType(
                normalizedMessage = normalizedMessage,
                normalizedTitle = normalizedTitle,
                normalizedSubText = normalizedSubText,
            )

            WHATSAPP_PACKAGE -> detectWhatsAppType(
                normalizedMessage = normalizedMessage,
                normalizedTitle = normalizedTitle,
                normalizedSubText = normalizedSubText,
            )

            else -> detectGenericType(
                normalizedMessage = normalizedMessage,
                normalizedTitle = normalizedTitle,
                normalizedSubText = normalizedSubText,
            )
        }
    }

    private fun detectInstagramType(
        normalizedMessage: String,
        normalizedTitle: String,
        normalizedSubText: String,
    ): MessageType {
        val combined = "$normalizedMessage $normalizedTitle $normalizedSubText"
        return when {
            combined.hasAny("sent a reel", "shared a reel", "shared a video", "shared a post") -> MessageType.REEL
            combined.hasAny("sent a photo", "sent a picture", "shared a photo") -> MessageType.IMAGE
            combined.hasAny("sent a video", "shared a clip", "video call") -> MessageType.VIDEO
            combined.hasAny("sent an audio", "sent a voice message", "voice message") -> MessageType.AUDIO
            else -> MessageType.TEXT
        }
    }

    private fun detectWhatsAppType(
        normalizedMessage: String,
        normalizedTitle: String,
        normalizedSubText: String,
    ): MessageType {
        val combined = "$normalizedMessage $normalizedTitle $normalizedSubText"
        return when {
            combined.hasAny("📷", "photo", "image", "picture") -> MessageType.IMAGE
            combined.hasAny("📹", "video", "clip") -> MessageType.VIDEO
            combined.hasAny("🎵", "🎤", "audio", "voice message", "ptt") -> MessageType.AUDIO
            combined.hasAny("sticker", "gif") -> MessageType.STICKER
            else -> MessageType.TEXT
        }
    }

    private fun detectGenericType(
        normalizedMessage: String,
        normalizedTitle: String,
        normalizedSubText: String,
    ): MessageType {
        val combined = "$normalizedMessage $normalizedTitle $normalizedSubText"
        return when {
            combined.hasAny("reel") -> MessageType.REEL
            combined.hasAny("📷", "photo", "image", "picture") -> MessageType.IMAGE
            combined.hasAny("📹", "video", "clip") -> MessageType.VIDEO
            combined.hasAny("🎵", "🎤", "audio", "voice message") -> MessageType.AUDIO
            combined.hasAny("sticker", "gif") -> MessageType.STICKER
            else -> MessageType.TEXT
        }
    }

    private fun String.normalize(): String = lowercase(Locale.ROOT).trim()

    private fun String.hasAny(vararg needles: String): Boolean = needles.any { contains(it) }

    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
}
