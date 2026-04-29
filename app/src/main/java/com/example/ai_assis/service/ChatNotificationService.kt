package com.example.ai_assis.service

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.ai_assis.domain.model.ChatMessage
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

        val extras = sbn.notification.extras
        val text = extractMessageText(extras)
        if (text.isBlank()) return

        val sender = extras.getCharSequence("android.title")?.toString().orEmpty()
        if (isDuplicateNotification(sbn = sbn, sender = sender, text = text)) return
        Log.d(logTag, "Notification captured from ${sbn.packageName} (${sender.ifBlank { "Unknown" }})")

        val replyAction = sbn.notification.actions
            ?.find { action -> action.remoteInputs?.isNotEmpty() == true }
        val actionKey = if (replyAction != null) {
            val key = "${sbn.packageName}_${sbn.id}_${System.currentTimeMillis()}"
            DirectReplyRegistry.register(key, replyAction)
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
            ),
        )
        Log.d(logTag, "Event emitted for ${sbn.packageName}")
    }

    private fun isDuplicateNotification(
        sbn: StatusBarNotification,
        sender: String,
        text: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val fingerprint = "${sbn.packageName}|${sender.trim()}|${text.trim()}|${sbn.id}"

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

    private fun extractMessageText(extras: Bundle): String {
        val directText = extras.getCharSequence("android.text")?.toString().orEmpty().trim()
        if (directText.isNotEmpty()) return directText

        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty().trim()
        if (bigText.isNotEmpty()) return bigText

        val textLines = extras.getCharSequenceArray("android.textLines")
            ?.map { it?.toString().orEmpty().trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
        if (textLines.isNotEmpty()) return textLines

        return ""
    }

    private companion object {
        const val logTag = "SmartAssistant"
        const val duplicateWindowMs = 3_500L
        const val maxFingerprintCache = 80
    }
}
