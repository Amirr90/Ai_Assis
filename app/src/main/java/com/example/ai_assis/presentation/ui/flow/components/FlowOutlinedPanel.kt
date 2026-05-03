package com.example.ai_assis.presentation.ui.flow.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private val PanelShape = RoundedCornerShape(28.dp)

@Composable
fun FlowOutlinedPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .border(
                width = 1.dp,
                color = scheme.primary.copy(alpha = 0.22f),
                shape = PanelShape,
            )
            .padding(2.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surface.copy(alpha = 0.42f))
            .border(
                width = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}
