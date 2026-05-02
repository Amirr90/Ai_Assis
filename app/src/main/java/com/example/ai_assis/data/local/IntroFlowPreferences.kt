package com.example.ai_assis.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai_assis.util.PermissionUtils
import kotlinx.coroutines.flow.first

private val Context.introFlowDataStore by preferencesDataStore(name = "intro_flow")

private val introFlowCompletedKey = booleanPreferencesKey("intro_flow_completed")
private val introFlowMigratedKey = booleanPreferencesKey("intro_flow_migrated")

suspend fun Context.isIntroFlowCompleted(): Boolean {
    migrateIntroFlowFromLegacyIfNeeded()
    return introFlowDataStore.data.first()[introFlowCompletedKey] ?: false
}

suspend fun Context.setIntroFlowCompleted(value: Boolean) {
    introFlowDataStore.edit { prefs ->
        prefs[introFlowCompletedKey] = value
    }
}

/** One-time: existing installs with permissions already granted skip the intro flow. */
private suspend fun Context.migrateIntroFlowFromLegacyIfNeeded() {
    val snapshot = introFlowDataStore.data.first()
    if (snapshot[introFlowMigratedKey] == true) return
    val shouldAutoComplete =
        PermissionUtils.hasNotificationAccess(this) &&
            PermissionUtils.hasOverlayPermission(this)
    introFlowDataStore.edit { prefs ->
        prefs[introFlowMigratedKey] = true
        if (prefs[introFlowCompletedKey] != true && shouldAutoComplete) {
            prefs[introFlowCompletedKey] = true
        }
    }
}
