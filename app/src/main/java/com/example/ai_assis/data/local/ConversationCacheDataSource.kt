package com.example.ai_assis.data.local

import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.ConversationTurn
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ConversationCacheDataSource @Inject constructor(
    private val conversationMemoryStore: ConversationMemoryStore,
) {
    private val mutex = Mutex()
    private val turnsByChatKey = LinkedHashMap<String, MutableList<ConversationTurn>>()

    suspend fun hydrateFromDiskIfNeeded(chatKey: String, rememberContext: Boolean) {
        if (!rememberContext) return
        mutex.withLock {
            val existing = turnsByChatKey[chatKey]
            if (!existing.isNullOrEmpty()) return@withLock
            val loaded = conversationMemoryStore.load(chatKey)
            if (loaded.isNotEmpty()) {
                turnsByChatKey[chatKey] = loaded.toMutableList()
            }
        }
    }

    suspend fun appendMessage(
        message: ChatMessage,
        rememberContext: Boolean,
        maxStoredTurns: Int,
    ) {
        val key = "${message.appSource}:${message.sender}"
        hydrateFromDiskIfNeeded(key, rememberContext)
        mutex.withLock {
            val list = turnsByChatKey.getOrPut(key) { mutableListOf() }
            list += ConversationTurn(
                text = message.message,
                direction = message.direction,
            )
            while (list.size > maxStoredTurns) {
                list.removeAt(0)
            }
        }
        if (rememberContext) {
            mutex.withLock {
                val list = turnsByChatKey[key].orEmpty()
                conversationMemoryStore.save(key, list)
            }
        }
    }

    suspend fun recentTurns(
        appPackage: String,
        sender: String,
        limit: Int,
    ): List<ConversationTurn> {
        return mutex.withLock {
            turnsByChatKey["$appPackage:$sender"]
                ?.takeLast(limit.coerceAtLeast(1))
                .orEmpty()
        }
    }

    suspend fun clearSessionCache(chatKey: String) {
        mutex.withLock {
            turnsByChatKey.remove(chatKey)
        }
    }

    suspend fun clearPersistentMemory(chatKey: String) {
        conversationMemoryStore.clear(chatKey)
    }
}
