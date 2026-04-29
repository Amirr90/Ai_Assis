package com.example.ai_assis.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai_assis.domain.repository.MonitoredAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.monitoredAppsDataStore by preferencesDataStore(name = "monitored_apps")

@Singleton
class MonitoredAppsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : MonitoredAppsRepository {

    private companion object {
        val appsKey: Preferences.Key<String> = stringPreferencesKey("monitored_packages")
        val defaultApps: Set<String> = setOf("com.whatsapp", "com.instagram.android")
        const val SEPARATOR = ","
    }

    override val monitoredPackagesFlow: Flow<Set<String>> =
        context.monitoredAppsDataStore.data.map { prefs ->
            prefs[appsKey]
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: defaultApps
        }

    override suspend fun setMonitoredPackages(packages: Set<String>) {
        context.monitoredAppsDataStore.edit { prefs ->
            prefs[appsKey] = packages.joinToString(SEPARATOR)
        }
    }
}
