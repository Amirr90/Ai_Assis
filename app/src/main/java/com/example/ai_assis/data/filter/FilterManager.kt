package com.example.ai_assis.data.filter

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class FilterResult {
    data object Ignore : FilterResult()
    data object Media : FilterResult()
    data object Process : FilterResult()
}

/** Optional notification fields for WhatsApp consumer business-chat detection. */
data class FilterExtras(
    val sender: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val conversationTitle: String? = null,
)

@Singleton
class FilterManager @Inject constructor() {

    fun shouldProcess(
        packageName: String,
        title: String?,
        message: String?,
        extras: FilterExtras? = null,
    ): FilterResult {
        if (packageName == WHATSAPP_BUSINESS_PACKAGE) return FilterResult.Ignore

        if (packageName == WHATSAPP_PACKAGE && hasBusinessMarkers(title, extras)) {
            return FilterResult.Ignore
        }

        if (packageName !in allowedPackages) return FilterResult.Ignore

        val body = message?.trim().orEmpty()
        if (body.isBlank()) return FilterResult.Ignore

        if (matchesLayer2Noise(body)) return FilterResult.Ignore

        if (matchesMedia(body)) return FilterResult.Media

        return FilterResult.Process
    }

    private fun hasBusinessMarkers(title: String?, extras: FilterExtras?): Boolean {
        val candidates = buildList {
            extras?.sender?.let { add(it) }
            title?.let { add(it) }
            extras?.subText?.let { add(it) }
            extras?.summaryText?.let { add(it) }
            extras?.conversationTitle?.let { add(it) }
        }
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        return candidates.any { candidate ->
            businessMarkerRegexes.any { regex -> regex.containsMatchIn(candidate) }
        }
    }

    private fun matchesLayer2Noise(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT)
        if (systemPhrases.any { phrase -> lower.contains(phrase) }) return true
        if (systemSingleWordPatterns.any { it.containsMatchIn(lower) }) return true
        if (promoWordPatterns.any { it.containsMatchIn(lower) }) return true
        return false
    }

    private fun matchesMedia(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT)
        if (lower.contains(MEDIA_PHRASE_SENT_REEL)) return true
        if (mediaWordPatterns.any { it.containsMatchIn(lower) }) return true
        return false
    }

    private companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"

        val allowedPackages = setOf(WHATSAPP_PACKAGE, INSTAGRAM_PACKAGE)

        private val businessMarkerRegexes = listOf(
            Regex("\\bbusiness\\s+account\\b"),
            Regex("\\bofficial\\s+business\\b"),
            Regex("\\bverified\\s+business\\b"),
            Regex("\\bmeta\\s+verified\\b"),
            Regex("\\bwhatsapp\\s+business\\b"),
        )

        private val systemPhrases = listOf(
            "checking for new messages",
            "security code",
        )

        private val systemSingleTokens = listOf(
            "backup",
            "restored",
            "web",
        )

        private val promoTokens = listOf(
            "offer",
            "sale",
            "discount",
            "order",
            "delivery",
            "cashback",
            "deal",
        )

        private val mediaSingleTokens = listOf(
            "photo",
            "video",
            "reel",
            "image",
        )

        private const val MEDIA_PHRASE_SENT_REEL = "sent a reel"

        private fun wordBoundaryPattern(token: String): Regex =
            Regex("\\b${Regex.escape(token)}\\b", RegexOption.IGNORE_CASE)

        private val systemSingleWordPatterns = systemSingleTokens.map(::wordBoundaryPattern)
        private val promoWordPatterns = promoTokens.map(::wordBoundaryPattern)
        private val mediaWordPatterns = mediaSingleTokens.map(::wordBoundaryPattern)
    }
}
