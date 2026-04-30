package com.example.ai_assis.service

import android.app.Notification

/**
 * Stores RemoteInput-capable notification actions keyed by a generated action key.
 * The key is stored on ChatMessage.replyActionKey, allowing OverlayService to look up
 * the original notification action for direct-send without copying to clipboard.
 */
object DirectReplyRegistry {
    private const val MAX_ENTRIES = 120
    private val actions = LinkedHashMap<String, StoredAction>()

    fun register(
        key: String,
        action: Notification.Action,
        packageName: String,
        sender: String,
    ) {
        synchronized(actions) {
            actions[key] = StoredAction(
                action = action,
                packageName = packageName,
                sender = sender.trim().lowercase(),
                createdAtMs = System.currentTimeMillis(),
            )
            while (actions.size > MAX_ENTRIES) {
                val oldestKey = actions.entries.firstOrNull()?.key ?: break
                actions.remove(oldestKey)
            }
        }
    }

    fun get(key: String): Notification.Action? = synchronized(actions) { actions[key]?.action }

    fun resolveForChat(
        key: String?,
        packageName: String,
        sender: String,
    ): Notification.Action? = synchronized(actions) {
        if (!key.isNullOrBlank()) {
            actions[key]?.action?.let { return@synchronized it }
        }
        val normalizedSender = sender.trim().lowercase()
        actions.values.lastOrNull {
            it.packageName == packageName && it.sender == normalizedSender
        }?.action ?: actions.values.lastOrNull { it.packageName == packageName }?.action
    }

    fun remove(key: String) {
        synchronized(actions) {
            actions.remove(key)
        }
    }

    private data class StoredAction(
        val action: Notification.Action,
        val packageName: String,
        val sender: String,
        val createdAtMs: Long,
    )
}
