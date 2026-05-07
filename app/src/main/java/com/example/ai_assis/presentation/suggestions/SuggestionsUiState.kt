package com.example.ai_assis.presentation.suggestions

data class SuggestionsUiState(
    val latestMessage: String = "",
    val senderName: String = "",
    val suggestions: List<SuggestionUiModel> = emptyList(),
    val mediaTypeLabel: String? = null,
    val isLoading: Boolean = false,
    val source: SuggestionSourceUi = SuggestionSourceUi.ON_DEVICE,
    val errorMessage: String? = null,
    val showPermissionBanner: Boolean = false,
)

data class SuggestionUiModel(
    val text: String,
    val confidence: Double,
)

enum class SuggestionSourceUi {
    ON_DEVICE,
    CLOUD,
}
