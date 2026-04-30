package com.example.ai_assis.data.local

import com.example.ai_assis.domain.model.MessageType
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaReplyProvider @Inject constructor() {
    fun getReplies(messageType: MessageType, appPackage: String): List<Suggestion> {
        if (messageType == MessageType.TEXT) return emptyList()

        val replies = when (messageType) {
            MessageType.REEL -> if (appPackage == INSTAGRAM_PACKAGE) {
                listOf("😂😂", "Nice reel 🔥", "Bro this is crazy 😄", "Seen 👍")
            } else {
                listOf("😂😂", "Nice one 🔥", "Crazy 😄", "Seen 👍")
            }

            MessageType.IMAGE, MessageType.VIDEO -> {
                listOf("Nice 👍", "Looks good", "😂😂", "What is this 😂")
            }

            MessageType.STICKER -> {
                listOf("😄", "Lol 😂", "Same 😭")
            }

            MessageType.AUDIO -> {
                listOf("Heard it 👍", "🎵", "Playing it now")
            }
            
            MessageType.TEXT -> emptyList()
        }

        val finalReplies = if (replies.isNotEmpty()) replies else fallbackReplies
        return finalReplies.map { text ->
            Suggestion(
                text = text,
                confidence = 1.0,
                source = SuggestionSource.ON_DEVICE,
            )
        }
    }

    private companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        val fallbackReplies = listOf("👍", "Seen", "😂")
    }
}
