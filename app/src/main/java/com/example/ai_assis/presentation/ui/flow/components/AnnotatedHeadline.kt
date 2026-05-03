package com.example.ai_assis.presentation.ui.flow.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun AnnotatedHeadline(
    fullText: String,
    accentPhrases: List<String>,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    titleFontSize: TextUnit = 22.sp,
    letterSpacing: TextUnit = 0.sp,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val annotated = remember(fullText, accentPhrases, primary, onSurface, titleFontSize, letterSpacing) {
        buildAnnotatedString {
            if (accentPhrases.isEmpty()) {
                append(fullText)
                addStyle(
                    SpanStyle(
                        color = onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleFontSize,
                        letterSpacing = letterSpacing,
                    ),
                    0,
                    length,
                )
                return@buildAnnotatedString
            }

            var remaining = fullText
            while (remaining.isNotEmpty()) {
                val match = accentPhrases
                    .mapNotNull { phrase ->
                        if (phrase.isBlank()) return@mapNotNull null
                        val idx = remaining.indexOf(phrase)
                        if (idx >= 0) Triple(phrase, idx, idx + phrase.length) else null
                    }
                    .minByOrNull { it.second }

                if (match == null) {
                    val start = length
                    append(remaining)
                    addStyle(
                        SpanStyle(
                            color = onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = titleFontSize,
                            letterSpacing = letterSpacing,
                        ),
                        start,
                        length,
                    )
                    break
                }

                val (phrase, startIdx, endIdx) = match
                if (startIdx > 0) {
                    val start = length
                    append(remaining.substring(0, startIdx))
                    addStyle(
                        SpanStyle(
                            color = onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = titleFontSize,
                            letterSpacing = letterSpacing,
                        ),
                        start,
                        length,
                    )
                }
                val accentStart = length
                append(phrase)
                addStyle(
                    SpanStyle(
                        color = primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleFontSize,
                        letterSpacing = letterSpacing,
                    ),
                    accentStart,
                    length,
                )
                remaining = remaining.substring(endIdx)
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall.copy(textAlign = textAlign),
    )
}
