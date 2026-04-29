package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.domain.model.KnownApp
import com.example.ai_assis.domain.model.knownMonitorableApps
import com.example.ai_assis.domain.repository.MonitoredAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppFilterUiState(
    val isLoaded: Boolean = false,
    val apps: List<Pair<KnownApp, Boolean>> = emptyList(),
)

@HiltViewModel
class AppFilterViewModel @Inject constructor(
    private val monitoredAppsRepository: MonitoredAppsRepository,
) : ViewModel() {

    val uiState: StateFlow<AppFilterUiState> =
        monitoredAppsRepository.monitoredPackagesFlow
            .map { monitored ->
                AppFilterUiState(
                    isLoaded = true,
                    apps = knownMonitorableApps.map { app -> app to (app.packageName in monitored) },
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AppFilterUiState(),
            )

    fun toggleApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = monitoredAppsRepository.monitoredPackagesFlow.first()
            val updated = if (enabled) current + packageName else current - packageName
            monitoredAppsRepository.setMonitoredPackages(updated)
        }
    }
}
