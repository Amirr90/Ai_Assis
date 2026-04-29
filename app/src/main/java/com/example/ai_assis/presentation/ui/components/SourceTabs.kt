package com.example.ai_assis.presentation.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai_assis.R
import com.example.ai_assis.domain.model.appDisplayLabelFor

sealed class SourceTab(
    val key: String,
    val title: String,
    val packageName: String?,
    val iconRes: Int?,
) {
    data object All : SourceTab(
        key = "all",
        title = "All",
        packageName = null,
        iconRes = R.drawable.ic_source_all,
    )

    data object WhatsApp : SourceTab(
        key = "com.whatsapp",
        title = "WhatsApp",
        packageName = "com.whatsapp",
        iconRes = R.drawable.ic_whatsapp,
    )

    data object Instagram : SourceTab(
        key = "com.instagram.android",
        title = "Instagram",
        packageName = "com.instagram.android",
        iconRes = R.drawable.ic_instagram,
    )

    data object LinkedIn : SourceTab(
        key = "com.linkedin.android",
        title = "LinkedIn",
        packageName = "com.linkedin.android",
        iconRes = R.drawable.ic_linkedin,
    )
}

val defaultSourceTabs: List<SourceTab> = listOf(
    SourceTab.All,
    SourceTab.WhatsApp,
    SourceTab.Instagram,
    SourceTab.LinkedIn,
)

fun sourceTabFromKey(key: String): SourceTab {
    return defaultSourceTabs.find { it.key == key } ?: SourceTab.All
}

@Composable
fun SourceTabsRow(
    tabs: List<SourceTab>,
    selectedTab: SourceTab,
    onTabSelected: (SourceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        modifier = modifier,
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab.key == selectedTab.key,
                onClick = { onTabSelected(tab) },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppSourceIcon(
                            packageName = tab.packageName,
                            iconRes = tab.iconRes,
                            fallbackLabel = tab.title.take(1),
                        )
                        Text(
                            text = tab.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun AppSourceIcon(
    packageName: String?,
    @DrawableRes iconRes: Int?,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
) {
    val background = sourceColorFor(packageName)
    if (iconRes != null) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = packageName?.let { appDisplayLabelFor(it) } ?: "All sources",
            modifier = modifier
                .size(18.dp)
                .clip(CircleShape),
        )
    } else {
        Row(
            modifier = modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(background),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = fallbackLabel.uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@DrawableRes
fun appIconResFor(packageName: String): Int? {
    return when (packageName) {
        "com.whatsapp" -> R.drawable.ic_whatsapp
        "com.instagram.android" -> R.drawable.ic_instagram
        "com.linkedin.android" -> R.drawable.ic_linkedin
        else -> null
    }
}

private fun sourceColorFor(packageName: String?): Color {
    return when (packageName) {
        "com.whatsapp" -> Color(0xFF25D366)
        "com.instagram.android" -> Color(0xFFE1306C)
        "com.linkedin.android" -> Color(0xFF0A66C2)
        "org.telegram.messenger" -> Color(0xFF2CA5E0)
        "com.twitter.android" -> Color(0xFF1DA1F2)
        "com.facebook.orca" -> Color(0xFF0084FF)
        "com.snapchat.android" -> Color(0xFFE3A008)
        else -> Color(0xFF6750A4)
    }
}
