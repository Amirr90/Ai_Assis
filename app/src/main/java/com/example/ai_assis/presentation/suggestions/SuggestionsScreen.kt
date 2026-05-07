package com.example.ai_assis.presentation.suggestions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(
    uiState: SuggestionsUiState,
    onBack: () -> Unit,
    onEvent: (SuggestionsEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Suggestions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = uiState.senderName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = uiState.latestMessage,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Source: ${uiState.source.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            uiState.mediaTypeLabel?.let { mediaLabel ->
                AssistChip(
                    onClick = {},
                    label = { Text(mediaLabel) },
                )
            }

            Text(
                text = "Replies adapt automatically from your chat style.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            uiState.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.suggestions) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(SuggestionsEvent.SuggestionClicked(item.text)) },
                    ) {
                        Text(
                            text = item.text,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
