package com.example.ai_assis.service

import android.app.Notification
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores RemoteInput-capable notification actions keyed by a generated action key.
 * The key is stored on ChatMessage.replyActionKey, allowing OverlayService to look up
 * the original notification action for direct-send without copying to clipboard.
 */
object DirectReplyRegistry {
    private const val MAX_ENTRIES = 10
    private val actions = ConcurrentHashMap<String, Notification.Action>()

    fun register(key: String, action: Notification.Action) {
        if (actions.size >= MAX_ENTRIES) {
            actions.keys.toList().take(actions.size - MAX_ENTRIES + 1).forEach { actions.remove(it) }
        }
        actions[key] = action
    }

    fun get(key: String): Notification.Action? = actions[key]

    fun remove(key: String) {
        actions.remove(key)
    }
}
