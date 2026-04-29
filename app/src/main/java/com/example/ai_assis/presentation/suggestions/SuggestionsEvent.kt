package com.example.ai_assis.presentation.suggestions

import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.SuggestionTone

sealed interface SuggestionsEvent {
    data class NewMessageArrived(val chatMessage: ChatMessage) : SuggestionsEvent
    data class ToneChanged(val tone: SuggestionTone) : SuggestionsEvent
    data object RefreshSuggestions : SuggestionsEvent
    data class SuggestionClicked(val text: String) : SuggestionsEvent
    data object DismissError : SuggestionsEvent
}
