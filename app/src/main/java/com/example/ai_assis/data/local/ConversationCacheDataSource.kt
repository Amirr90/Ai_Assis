package com.example.ai_assis.data.local

import com.example.ai_assis.domain.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ConversationCacheDataSource @Inject constructor() {
    private val mutex = Mutex()
    private val messagesBySender = LinkedHashMap<String, MutableList<String>>()

    suspend fun appendMessage(message: ChatMessage) {
        mutex.withLock {
            val key = "${message.appSource}:${message.sender}"
            val list = messagesBySender.getOrPut(key) { mutableListOf() }
            list += message.message
            if (list.size > maxPerSenderHistory) {
                list.removeAt(0)
            }
        }
    }

    suspend fun recentMessages(appPackage: String, sender: String): List<String> {
        return mutex.withLock {
            messagesBySender["$appPackage:$sender"]
                ?.takeLast(maxContextMessages)
                .orEmpty()
        }
    }

    private companion object {
        const val maxPerSenderHistory = 20
        const val maxContextMessages = 8
    }
}
