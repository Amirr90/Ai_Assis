package com.example.ai_assis.service

import android.os.Bundle
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.ai_assis.domain.model.ChatMessage

class ChatNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!supportedPackages.contains(sbn.packageName)) return

        val extras = sbn.notification.extras
        val text = extractMessageText(extras)
        if (text.isBlank()) return

        val sender = extras.getCharSequence("android.title")?.toString().orEmpty()
        Log.d(logTag, "Notification captured from ${sbn.packageName} (${sender.ifBlank { "Unknown" }})")
        NotificationEventBus.tryEmit(
            ChatMessage(
                sender = sender.ifBlank { "Unknown" },
                message = text,
                appSource = sbn.packageName,
            ),
        )
        Log.d(logTag, "Event emitted for ${sbn.packageName}")
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
        val supportedPackages = setOf("com.whatsapp", "com.instagram.android")
    }
}
