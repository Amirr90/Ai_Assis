package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.domain.model.CustomTemplate
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import com.example.ai_assis.domain.repository.TemplateRepository
import com.example.ai_assis.domain.repository.ToneRepository
import com.example.ai_assis.service.NotificationEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val selectedTone: ReplyTone = ReplyTone.CASUAL,
    val replyLength: ReplyLength = ReplyLength.MEDIUM,
    val aiEnabled: Boolean = true,
    val templates: List<CustomTemplate> = emptyList(),
    val chatHistory: List<NotificationEventBus.ChatSuggestionItem> = emptyList(),
    val overlayMeta: NotificationEventBus.OverlayMetaState = NotificationEventBus.OverlayMetaState(),
)

sealed interface DashboardEvent {
    data class ToneSelected(val tone: ReplyTone) : DashboardEvent
    data class ReplyLengthSelected(val length: ReplyLength) : DashboardEvent
    data class AiToggled(val enabled: Boolean) : DashboardEvent
    data class AddTemplate(val text: String, val appPackage: String? = null) : DashboardEvent
    data class RemoveTemplate(val templateId: String) : DashboardEvent
    data class MarkTemplateUsed(val templateId: String) : DashboardEvent
    data object ClearHistoryClicked : DashboardEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val toneRepository: ToneRepository,
    private val featurePreferencesRepository: FeaturePreferencesRepository,
    private val templateRepository: TemplateRepository,
) : ViewModel() {
    val selectedTone: StateFlow<ReplyTone> = toneRepository.toneFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReplyTone.CASUAL,
    )

    val toneOptions: StateFlow<List<ReplyTone>> = selectedTone.map { ReplyTone.entries }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReplyTone.entries,
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

    private data class DashboardPartialState(
        val selectedTone: ReplyTone,
        val replyLength: ReplyLength,
        val aiEnabled: Boolean,
        val templates: List<CustomTemplate>,
        val chatHistory: List<NotificationEventBus.ChatSuggestionItem>,
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedTone,
        replyLength,
        aiEnabled,
        templates,
        chatHistory,
    ) { tone, length, aiOn, templateList, history ->
        DashboardPartialState(
            selectedTone = tone,
            replyLength = length,
            aiEnabled = aiOn,
            templates = templateList,
            chatHistory = history,
        )
    }.combine(overlayMeta) { partial, meta ->
        DashboardUiState(
            selectedTone = partial.selectedTone,
            replyLength = partial.replyLength,
            aiEnabled = partial.aiEnabled,
            templates = partial.templates,
            chatHistory = partial.chatHistory,
            overlayMeta = meta,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.ToneSelected -> saveTone(event.tone)
            is DashboardEvent.ReplyLengthSelected -> saveReplyLength(event.length)
            is DashboardEvent.AiToggled -> toggleAi(event.enabled)
            is DashboardEvent.AddTemplate -> addTemplate(event.text, event.appPackage)
            is DashboardEvent.RemoveTemplate -> removeTemplate(event.templateId)
            is DashboardEvent.MarkTemplateUsed -> markTemplateUsed(event.templateId)
            DashboardEvent.ClearHistoryClicked -> clearHistory()
        }
    }

    fun saveTone(tone: ReplyTone) {
        viewModelScope.launch {
            toneRepository.saveTone(tone)
        }
    }

    fun clearHistory() {
        NotificationEventBus.clearHistory()
    }

    private fun saveReplyLength(length: ReplyLength) {
        viewModelScope.launch {
            featurePreferencesRepository.setReplyLength(length)
        }
    }

    private fun toggleAi(enabled: Boolean) {
        viewModelScope.launch {
            featurePreferencesRepository.setAiEnabled(enabled)
        }
    }

    private fun addTemplate(text: String, appPackage: String?) {
        viewModelScope.launch {
            templateRepository.saveTemplate(text = text, appPackage = appPackage)
        }
    }

    private fun removeTemplate(templateId: String) {
        viewModelScope.launch {
            templateRepository.deleteTemplate(templateId)
        }
    }

    private fun markTemplateUsed(templateId: String) {
        viewModelScope.launch {
            templateRepository.markTemplateUsed(templateId)
        }
    }
}
