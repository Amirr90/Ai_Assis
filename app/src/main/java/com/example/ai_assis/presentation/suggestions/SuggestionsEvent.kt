package com.example.ai_assis.presentation.suggestions

import com.example.ai_assis.domain.model.ChatMessage

sealed interface SuggestionsEvent {
    data class NewMessageArrived(val chatMessage: ChatMessage) : SuggestionsEvent
    data class SuggestionClicked(val text: String) : SuggestionsEvent
    data object RefreshSuggestions : SuggestionsEvent
    data object DismissError : SuggestionsEvent
}
