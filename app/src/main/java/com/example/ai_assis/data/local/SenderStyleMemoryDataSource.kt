package com.example.ai_assis.data.local

import com.example.ai_assis.domain.model.SuggestionTone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SenderStyleMemoryDataSource @Inject constructor() {
    private val mutex = Mutex()
    private val styleBySender = LinkedHashMap<String, String>()

    suspend fun rememberStyle(appPackage: String, sender: String, tone: SuggestionTone) {
        val key = "$appPackage:$sender"
        val style = when (tone) {
            SuggestionTone.PROFESSIONAL -> "professional"
            SuggestionTone.HUMOROUS -> "friendly"
            SuggestionTone.SHORT -> "short"
            SuggestionTone.CASUAL -> "friendly"
        }
        mutex.withLock {
            styleBySender[key] = style
            if (styleBySender.size > maxStyleEntries) {
                val firstKey = styleBySender.keys.firstOrNull()
                if (firstKey != null) styleBySender.remove(firstKey)
            }
        }
    }

    suspend fun getStyleHint(appPackage: String, sender: String): String? {
        return mutex.withLock { styleBySender["$appPackage:$sender"] }
    }

    private companion object {
        const val maxStyleEntries = 200
    }
}

