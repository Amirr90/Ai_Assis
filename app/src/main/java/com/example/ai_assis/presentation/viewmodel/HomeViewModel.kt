package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.data.remote.model.UserUsageRecord
import com.example.ai_assis.domain.model.CustomTemplate
import com.example.ai_assis.domain.model.LanguagePreference
import com.example.ai_assis.domain.model.MemoryDepth
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import com.example.ai_assis.domain.repository.TemplateRepository
import com.example.ai_assis.service.NotificationEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val adaptiveRepliesEnabled: Boolean = true,
    val memoryDepth: MemoryDepth = MemoryDepth.BALANCED,
    val languagePreference: LanguagePreference = LanguagePreference.AUTO,
    val rememberContext: Boolean = true,
    val replyLength: ReplyLength = ReplyLength.MEDIUM,
    val aiEnabled: Boolean = true,
    val templates: List<CustomTemplate> = emptyList(),
    val chatHistory: List<NotificationEventBus.ChatSuggestionItem> = emptyList(),
    val overlayMeta: NotificationEventBus.OverlayMetaState = NotificationEventBus.OverlayMetaState(),
    val userUsageRecord: UserUsageRecord = UserUsageRecord(),
)

sealed interface DashboardEvent {
    data class AdaptiveRepliesToggled(val enabled: Boolean) : DashboardEvent
    data class MemoryDepthSelected(val depth: MemoryDepth) : DashboardEvent
    data class LanguagePreferenceSelected(val preference: LanguagePreference) : DashboardEvent
    data class RememberContextToggled(val enabled: Boolean) : DashboardEvent
    data class ReplyLengthSelected(val length: ReplyLength) : DashboardEvent
    data class AiToggled(val enabled: Boolean) : DashboardEvent
    data class AddTemplate(val text: String, val appPackage: String? = null) : DashboardEvent
    data class RemoveTemplate(val templateId: String) : DashboardEvent
    data class MarkTemplateUsed(val templateId: String) : DashboardEvent
    data object ClearHistoryClicked : DashboardEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val featurePreferencesRepository: FeaturePreferencesRepository,
    private val templateRepository: TemplateRepository,
    usageManager: UsageManager,
) : ViewModel() {

    val adaptiveRepliesEnabled = featurePreferencesRepository.adaptiveRepliesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    val memoryDepth = featurePreferencesRepository.memoryDepthFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoryDepth.BALANCED,
    )

    val languagePreference = featurePreferencesRepository.languagePreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LanguagePreference.AUTO,
    )

    val rememberContext = featurePreferencesRepository.rememberContextFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    val replyLength = featurePreferencesRepository.replyLengthFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReplyLength.MEDIUM,
    )

    val aiEnabled = featurePreferencesRepository.aiEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    val templates = templateRepository.templatesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val chatHistory = NotificationEventBus.chatHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val overlayMeta = NotificationEventBus.metaState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationEventBus.OverlayMetaState(),
    )

    private val userUsageRecord = usageManager.userRecord

    private data class AdaptiveSettingsSnapshot(
        val adaptiveRepliesEnabled: Boolean,
        val memoryDepth: MemoryDepth,
        val languagePreference: LanguagePreference,
        val rememberContext: Boolean,
        val replyLength: ReplyLength,
        val aiEnabled: Boolean,
    )

    private val adaptiveSettingsSnapshot: Flow<AdaptiveSettingsSnapshot> = combine(
        combine(adaptiveRepliesEnabled, memoryDepth) { adaptive, depth -> Pair(adaptive, depth) },
        combine(languagePreference, rememberContext) { lang, remember -> Pair(lang, remember) },
        combine(replyLength, aiEnabled) { length, ai -> Pair(length, ai) },
    ) { adaptiveAndDepth, langAndRemember, lengthAndAi ->
        AdaptiveSettingsSnapshot(
            adaptiveRepliesEnabled = adaptiveAndDepth.first,
            memoryDepth = adaptiveAndDepth.second,
            languagePreference = langAndRemember.first,
            rememberContext = langAndRemember.second,
            replyLength = lengthAndAi.first,
            aiEnabled = lengthAndAi.second,
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        adaptiveSettingsSnapshot,
        templates,
        chatHistory,
        overlayMeta,
        userUsageRecord,
    ) { snap, templateList, history, meta, record ->
        DashboardUiState(
            adaptiveRepliesEnabled = snap.adaptiveRepliesEnabled,
            memoryDepth = snap.memoryDepth,
            languagePreference = snap.languagePreference,
            rememberContext = snap.rememberContext,
            replyLength = snap.replyLength,
            aiEnabled = snap.aiEnabled,
            templates = templateList,
            chatHistory = history,
            overlayMeta = meta,
            userUsageRecord = record,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.AdaptiveRepliesToggled -> setAdaptiveReplies(event.enabled)
            is DashboardEvent.MemoryDepthSelected -> setMemoryDepth(event.depth)
            is DashboardEvent.LanguagePreferenceSelected -> setLanguagePreference(event.preference)
            is DashboardEvent.RememberContextToggled -> setRememberContext(event.enabled)
            is DashboardEvent.ReplyLengthSelected -> saveReplyLength(event.length)
            is DashboardEvent.AiToggled -> toggleAi(event.enabled)
            is DashboardEvent.AddTemplate -> addTemplate(event.text, event.appPackage)
            is DashboardEvent.RemoveTemplate -> removeTemplate(event.templateId)
            is DashboardEvent.MarkTemplateUsed -> markTemplateUsed(event.templateId)
            DashboardEvent.ClearHistoryClicked -> clearHistory()
        }
    }

    fun clearHistory() {
        NotificationEventBus.clearHistory()
    }

    private fun setAdaptiveReplies(enabled: Boolean) {
        viewModelScope.launch { featurePreferencesRepository.setAdaptiveRepliesEnabled(enabled) }
    }

    private fun setMemoryDepth(depth: MemoryDepth) {
        viewModelScope.launch { featurePreferencesRepository.setMemoryDepth(depth) }
    }

    private fun setLanguagePreference(preference: LanguagePreference) {
        viewModelScope.launch { featurePreferencesRepository.setLanguagePreference(preference) }
    }

    private fun setRememberContext(enabled: Boolean) {
        viewModelScope.launch { featurePreferencesRepository.setRememberContext(enabled) }
    }

    private fun saveReplyLength(length: ReplyLength) {
        viewModelScope.launch { featurePreferencesRepository.setReplyLength(length) }
    }

    private fun toggleAi(enabled: Boolean) {
        viewModelScope.launch { featurePreferencesRepository.setAiEnabled(enabled) }
    }

    private fun addTemplate(text: String, appPackage: String?) {
        viewModelScope.launch { templateRepository.saveTemplate(text = text, appPackage = appPackage) }
    }

    private fun removeTemplate(templateId: String) {
        viewModelScope.launch { templateRepository.deleteTemplate(templateId) }
    }

    private fun markTemplateUsed(templateId: String) {
        viewModelScope.launch { templateRepository.markTemplateUsed(templateId) }
    }
}
