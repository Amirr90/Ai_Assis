package com.example.ai_assis.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.example.ai_assis.service.ChatNotificationService

object PermissionUtils {
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val component = ComponentName(context, ChatNotificationService::class.java).flattenToString()
        return enabled.contains(component)
    }
}
