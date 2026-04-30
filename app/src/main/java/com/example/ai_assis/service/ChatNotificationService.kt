package com.example.ai_assis.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.MessageDirection
import com.example.ai_assis.domain.repository.MonitoredAppsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatNotificationService : NotificationListenerService() {

    @Inject
    lateinit var monitoredAppsRepository: MonitoredAppsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentMonitoredPackages: Set<String> = setOf(
        "com.whatsapp",
        "com.instagram.android",
    )
    private val recentNotificationFingerprints = LinkedHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            monitoredAppsRepository.monitoredPackagesFlow.collect { packages ->
                currentMonitoredPackages = packages
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!currentMonitoredPackages.contains(sbn.packageName)) return
        if (sbn.packageName == BusinessMessageDetector.WHATSAPP_BUSINESS_PACKAGE) {
            Log.d(logTag, "Skipping WhatsApp Business notification package=${sbn.packageName}")
            return
        }

        val extras = sbn.notification.extras
        val parsedContent = parseNotificationContent(
            packageName = sbn.packageName,
            extras = extras,
        )
        val text = parsedContent.messageText
        if (text.isBlank()) return
        val messageType = MessageTypeDetector.detect(
            packageName = sbn.packageName,
            messageText = text,
            title = extras.getCharSequence("android.title")?.toString().orEmpty(),
            subText = extras.getCharSequence("android.subText")?.toString().orEmpty(),
        )
        if (!NotificationContentPolicy.shouldEmit(parsedContent.isSummary, parsedContent.sourceHint)) {
            Log.d(
                logTag,
                "Skipping summary-only notification package=${sbn.packageName} source=${parsedContent.sourceHint} text=$text",
            )
            return
        }

        val sender = parsedContent.sender.ifBlank { "Unknown" }
        if (BusinessMessageDetector.shouldSkipNotification(sbn.packageName, sender, extras)) {
            Log.d(logTag, "Skipping business chat notification package=${sbn.packageName} sender=$sender")
            return
        }
        if (parsedContent.isLikelySelfMessage) {
            Log.d(
                logTag,
                "Suppressed notification reason=self_sender_marker package=${sbn.packageName} source=${parsedContent.sourceHint}",
            )
            return
        }
        if (OutgoingMessageSuppressor.shouldSuppressIncoming(sbn.packageName, text)) {
            Log.d(logTag, "Suppressed notification reason=recent_outgoing_echo package=${sbn.packageName}")
            return
        }
        if (isDuplicateNotification(sbn = sbn, sender = sender, text = text, isSummary = parsedContent.isSummary)) return
        Log.d(
            logTag,
            "Notification captured from ${sbn.packageName} (${sender.ifBlank { "Unknown" }}) source=${parsedContent.sourceHint} summary=${parsedContent.isSummary}",
        )

        val replyAction = sbn.notification.actions
            ?.selectBestDirectReplyAction()
        val actionKey = if (replyAction != null) {
            val key = "${sbn.packageName}_${sbn.id}_${System.currentTimeMillis()}"
            DirectReplyRegistry.register(
                key = key,
                action = replyAction,
                packageName = sbn.packageName,
                sender = sender,
            )
            key
        } else {
            null
        }

        NotificationEventBus.tryEmit(
            ChatMessage(
                sender = sender.ifBlank { "Unknown" },
                message = text,
                appSource = sbn.packageName,
                replyActionKey = actionKey,
                isSummaryNotification = parsedContent.isSummary,
                messageType = messageType,
                direction = MessageDirection.INCOMING,
            ),
        )
        Log.d(logTag, "Event emitted for ${sbn.packageName}")
    }

    private fun isDuplicateNotification(
        sbn: StatusBarNotification,
        sender: String,
        text: String,
        isSummary: Boolean,
    ): Boolean {
        val now = System.currentTimeMillis()
        val fingerprint = "${sbn.packageName}|${sender.trim()}|${text.trim()}|${sbn.id}|summary=$isSummary"

        synchronized(recentNotificationFingerprints) {
            val itr = recentNotificationFingerprints.entries.iterator()
            while (itr.hasNext()) {
                val (_, ts) = itr.next()
                if (now - ts > duplicateWindowMs) itr.remove()
            }

            val lastSeen = recentNotificationFingerprints[fingerprint]
            if (lastSeen != null && now - lastSeen <= duplicateWindowMs) {
                Log.d(logTag, "Duplicate notification skipped for ${sbn.packageName}")
                return true
            }

            recentNotificationFingerprints[fingerprint] = now
            if (recentNotificationFingerprints.size > maxFingerprintCache) {
                val oldestKey = recentNotificationFingerprints.entries.firstOrNull()?.key
                if (oldestKey != null) recentNotificationFingerprints.remove(oldestKey)
            }
        }
        return false
    }

    private fun parseNotificationContent(
        packageName: String,
        extras: Bundle,
    ): ParsedNotificationContent {
        val fallbackSender = extras.getCharSequence("android.title")?.toString().orEmpty().trim()
        extractFromMessagingStyle(
            packageName = packageName,
            extras = extras,
        )?.let { parsed ->
            if (parsed.messageText.isNotBlank()) {
                return parsed.copy(
                    sender = parsed.sender.ifBlank { fallbackSender },
                    isSummary = parsed.isSummary || isLikelySummaryText(parsed.messageText),
                )
            }
        }

        val fallbackText = extractFallbackText(extras)
        if (fallbackText.isBlank()) {
            return ParsedNotificationContent(
                messageText = "",
                sender = fallbackSender,
                isSummary = false,
                sourceHint = "empty",
            )
        }

        return ParsedNotificationContent(
            messageText = fallbackText,
            sender = fallbackSender,
            isSummary = isLikelySummaryText(fallbackText),
            sourceHint = "extras_fallback",
            isLikelySelfMessage = SelfMessageHeuristics.isLikelySelfText(fallbackText),
        )
    }

    private fun extractFromMessagingStyle(
        packageName: String,
        extras: Bundle,
    ): ParsedNotificationContent? {
        val bundleArray = extras.getParcelableArray("android.messages") ?: return null
        val messages = runCatching {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundleArray)
        }.getOrNull() ?: return null

        val latest = messages.asReversed()
            .firstOrNull { candidate ->
                val text = candidate.text?.toString().orEmpty().trim()
                text.isNotBlank() && !isLikelySummaryText(text)
            }
            ?: messages.lastOrNull { !it.text.isNullOrBlank() }
            ?: return null
        val text = latest.text?.toString().orEmpty().trim()
        if (text.isBlank()) return null

        return ParsedNotificationContent(
            messageText = text,
            sender = latest.sender?.toString().orEmpty().trim(),
            isSummary = false,
            sourceHint = "messaging_style",
            isLikelySelfMessage = SelfMessageHeuristics.isLikelySelfSender(
                sender = latest.sender?.toString(),
                packageName = packageName,
            ),
        )
    }

    private fun extractFallbackText(extras: Bundle): String {
        val directText = extras.getCharSequence("android.text")?.toString().orEmpty().trim()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty().trim()
        val textLines = extras.getCharSequenceArray("android.textLines")
            ?.map { it?.toString().orEmpty().trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val candidates = buildList {
            if (directText.isNotEmpty()) add(directText)
            if (bigText.isNotEmpty()) {
                add(bigText)
                // Big text often contains multiple lines; the last non-summary line is usually the latest message.
                addAll(bigText.lines().map { it.trim() }.filter { it.isNotEmpty() })
            }
            addAll(textLines)
        }
        if (candidates.isEmpty()) return ""

        return candidates.asReversed().firstOrNull { !isLikelySummaryText(it) }
            ?: candidates.last()
    }

    private fun isLikelySummaryText(text: String): Boolean {
        return NotificationSummaryDetector.isLikelySummaryText(text)
    }

    private companion object {
        const val logTag = "SmartAssistant"
        const val duplicateWindowMs = 3_500L
        const val maxFingerprintCache = 80
    }

    private data class ParsedNotificationContent(
        val messageText: String,
        val sender: String,
        val isSummary: Boolean,
        val sourceHint: String,
        val isLikelySelfMessage: Boolean = false,
    )
}

private fun Array<Notification.Action>.selectBestDirectReplyAction(): Notification.Action? {
    val withRemoteInput = this.filter { action ->
        val firstInput = action.remoteInputs?.firstOrNull()
        firstInput != null && firstInput.allowFreeFormInput
    }
    if (withRemoteInput.isEmpty()) return null

    // Prefer explicit reply-labeled actions to avoid picking unrelated RemoteInput actions.
    return withRemoteInput.firstOrNull { action ->
        val title = action.title?.toString().orEmpty().lowercase()
        title.contains("reply") || title.contains("respond") || title.contains("send")
    } ?: withRemoteInput.first()
}

internal object NotificationContentPolicy {
    fun shouldEmit(isSummary: Boolean, sourceHint: String): Boolean {
        if (!isSummary) return true
        return sourceHint == "messaging_style"
    }
}
