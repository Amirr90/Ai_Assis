package com.example.ai_assis.presentation.suggestions

sealed interface SuggestionsEffect {
    data class CopyToClipboard(val text: String) : SuggestionsEffect
    data class ShowToast(val message: String) : SuggestionsEffect
}
