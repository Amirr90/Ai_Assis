package com.example.ai_assis.domain.model

data class KnownApp(
    val packageName: String,
    val label: String,
    val shortLabel: String,
)

val knownMonitorableApps: List<KnownApp> = listOf(
    KnownApp("com.whatsapp", "WhatsApp", "WA"),
    KnownApp("com.instagram.android", "Instagram", "IG"),
    KnownApp("com.linkedin.android", "LinkedIn", "LI"),
    KnownApp("org.telegram.messenger", "Telegram", "TG"),
    KnownApp("com.twitter.android", "X (Twitter)", "X"),
    KnownApp("com.facebook.orca", "Messenger", "FB"),
    KnownApp("com.snapchat.android", "Snapchat", "SC"),
    KnownApp("com.google.android.apps.messaging", "Messages", "SMS"),
)

fun appLabelFor(packageName: String): String =
    knownMonitorableApps.find { it.packageName == packageName }
        ?.shortLabel
        ?: packageName.substringAfterLast(".").uppercase().take(3)
