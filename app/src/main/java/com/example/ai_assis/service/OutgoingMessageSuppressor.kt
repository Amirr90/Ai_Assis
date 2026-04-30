package com.example.ai_assis.service

internal object OutgoingMessageSuppressor {
    private val recentOutgoingMessages = LinkedHashMap<String, Long>()

    fun registerOutgoing(
        packageName: String,
        text: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val variants = normalizedVariants(text)
        if (variants.isEmpty()) return
        synchronized(recentOutgoingMessages) {
            pruneExpired(nowMs)
            variants.forEach { normalized ->
                recentOutgoingMessages[key(packageName, normalized)] = nowMs
            }
            if (recentOutgoingMessages.size > maxEntries) {
                val oldestKey = recentOutgoingMessages.entries.firstOrNull()?.key
                if (oldestKey != null) recentOutgoingMessages.remove(oldestKey)
            }
        }
    }

    fun shouldSuppressIncoming(
        packageName: String,
        text: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val variants = normalizedVariants(text)
        if (variants.isEmpty()) return false
        synchronized(recentOutgoingMessages) {
            pruneExpired(nowMs)
            variants.forEach { normalized ->
                val messageKey = key(packageName, normalized)
                if (recentOutgoingMessages.remove(messageKey) != null) return true
            }
            return false
        }
    }

    private fun key(packageName: String, normalizedMessage: String): String = "$packageName|$normalizedMessage"

    private fun normalize(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
    }

    private fun normalizedVariants(value: String): Set<String> {
        val normalized = normalize(value)
        if (normalized.isBlank()) return emptySet()
        val variants = linkedSetOf(normalized)
        val withoutSelfPrefix = normalized
            .removePrefix("you ")
            .removePrefix("me ")
            .trim()
        if (withoutSelfPrefix.isNotBlank() && withoutSelfPrefix != normalized) {
            variants.add(withoutSelfPrefix)
        }
        return variants
    }

    private fun pruneExpired(nowMs: Long) {
        val itr = recentOutgoingMessages.entries.iterator()
        while (itr.hasNext()) {
            val (_, timestamp) = itr.next()
            if (nowMs - timestamp > suppressWindowMs) itr.remove()
        }
    }

    private const val suppressWindowMs = 10_000L
    private const val maxEntries = 80
}
