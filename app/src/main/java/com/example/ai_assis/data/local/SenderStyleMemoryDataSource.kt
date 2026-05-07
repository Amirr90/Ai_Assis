package com.example.ai_assis.data.local

import com.example.ai_assis.domain.model.AdaptiveConversationProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight per-chat hint cache for on-device styling (derived from adaptive profile).
 */
@Singleton
class SenderStyleMemoryDataSource @Inject constructor() {
    private val mutex = Mutex()
    private val hintBySender = LinkedHashMap<String, String>()
    private val profileLineBySender = LinkedHashMap<String, String>()

    suspend fun rememberFromProfile(appPackage: String, sender: String, profile: AdaptiveConversationProfile) {
        val key = chatKey(appPackage, sender)
        val hint = when {
            profile.textingBehavior == "short_reactive" -> "short"
            profile.formalityLevel == "formal_light" -> "professional"
            profile.slangLevel.contains("heavy") -> "casual_slang"
            else -> "friendly"
        }
        mutex.withLock {
            hintBySender[key] = hint
            profileLineBySender[key] = profile.compactPromptLine
            if (hintBySender.size > maxEntries) trimOldest(hintBySender)
            if (profileLineBySender.size > maxEntries) trimOldest(profileLineBySender)
        }
    }

    suspend fun getStyleHint(appPackage: String, sender: String): String? {
        return mutex.withLock { hintBySender[chatKey(appPackage, sender)] }
    }

    suspend fun getLastProfileHintLine(appPackage: String, sender: String): String? {
        return mutex.withLock { profileLineBySender[chatKey(appPackage, sender)] }
    }

    private fun chatKey(appPackage: String, sender: String) = "$appPackage:$sender"

    private fun <V> trimOldest(map: LinkedHashMap<String, V>) {
        val firstKey = map.keys.firstOrNull() ?: return
        map.remove(firstKey)
    }

    private companion object {
        const val maxEntries = 200
    }
}
