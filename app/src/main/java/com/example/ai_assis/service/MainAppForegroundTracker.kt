package com.example.ai_assis.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When the main app's activity is visible, the overlay chat-head is hidden so it
 * cannot cover dashboard UI ([MainActivity]); panel mode stays unchanged.
 */
object MainAppForegroundTracker {
    private val _mainAppForeground = MutableStateFlow(false)
    val mainAppForeground: StateFlow<Boolean> = _mainAppForeground.asStateFlow()

    fun setMainAppInForeground(isForeground: Boolean) {
        _mainAppForeground.value = isForeground
    }

    fun isMainAppInForeground(): Boolean = _mainAppForeground.value
}
