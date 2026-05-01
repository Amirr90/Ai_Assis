package com.example.ai_assis.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ai_assis.R

class ReplyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER).orEmpty().ifBlank { "chat" }
        val appLabel = inputData.getString(KEY_APP_LABEL).orEmpty().ifBlank { "your app" }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(channelId, "Reply reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val hasPostNotificationPermission =
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasPostNotificationPermission) return Result.success()

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reply reminder")
            .setContentText("You planned to reply to $sender on $appLabel.")
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        return Result.success()
    }

    private companion object {
        const val channelId = "reply_reminder_channel"
        const val KEY_SENDER = "sender"
        const val KEY_APP_LABEL = "app_label"
    }
}
