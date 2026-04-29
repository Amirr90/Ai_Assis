package com.example.ai_assis.domain.repository

import kotlinx.coroutines.flow.Flow

interface MonitoredAppsRepository {
    val monitoredPackagesFlow: Flow<Set<String>>
    suspend fun setMonitoredPackages(packages: Set<String>)
}
