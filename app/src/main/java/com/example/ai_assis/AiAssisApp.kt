package com.example.ai_assis

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.ai_assis.di.NotificationBootstrapEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AiAssisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            this,
            NotificationBootstrapEntryPoint::class.java,
        )
        entryPoint.appNotificationManager().ensureChannelsCreated()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
