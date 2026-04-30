package com.example.ai_assis.service

import android.os.Bundle

internal object BusinessMessageDetector {
    private val businessMarkerRegexes = listOf(
        Regex("\\bbusiness\\s+account\\b"),
        Regex("\\bofficial\\s+business\\b"),
        Regex("\\bverified\\s+business\\b"),
        Regex("\\bmeta\\s+verified\\b"),
        Regex("\\bwhatsapp\\s+business\\b"),
    )

    fun shouldSkipNotification(
        packageName: String,
        parsedSender: String,
        extras: Bundle,
    ): Boolean {
        if (packageName == WHATSAPP_BUSINESS_PACKAGE) return true
        if (packageName != WHATSAPP_PACKAGE) return false

        val candidates = buildList {
            add(parsedSender)
            add(extras.getCharSequence("android.title")?.toString().orEmpty())
            add(extras.getCharSequence("android.subText")?.toString().orEmpty())
            add(extras.getCharSequence("android.summaryText")?.toString().orEmpty())
            add(extras.getCharSequence("android.conversationTitle")?.toString().orEmpty())
        }.map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        return hasBusinessMarkers(candidates)
    }

    internal fun hasBusinessMarkers(candidates: List<String>): Boolean {
        return candidates.map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .any { candidate ->
                businessMarkerRegexes.any { marker -> marker.containsMatchIn(candidate) }
            }
    }

    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
}
