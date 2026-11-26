package com.snapfetch.app.feature.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapfetch.app.core.components.DownloadArrowIcon
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography

@Composable
fun HomeScreen(
    onSearchBarClick: () -> Unit,
    onDetectedUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalSnapFetchColors.current
    var clipboardUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
            if (!text.isNullOrBlank() && (text.startsWith("http://", true) || text.startsWith("https://", true))) {
                clipboardUrl = text
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        WatermarkBackground(modifier = Modifier.align(Alignment.TopCenter))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 128.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SnapFetch",
                style = SnapTypography.BrandWordmark,
                color = colors.wordmarkYellow
            )

            Spacer(modifier = Modifier.height(34.dp))

            // Exact 52dp search pill with left download icon + right #FFCC21 search circle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SnapSpacing.HomeSearchPillHeight)
                    .clickable(onClick = onSearchBarClick),
                shape = RoundedCornerShape(SnapSpacing.HomeSearchPillRadius),
                color = colors.elevatedSurface,
                border = BorderStroke(1.dp, colors.borderLight),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = SnapSpacing.HomeSearchCircleInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DownloadArrowIcon(
                        color = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search or paste video URL",
                        style = SnapTypography.ListRowTitle,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(SnapSpacing.HomeSearchCircleSize)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = colors.buttonTextOnAccent,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            if (clipboardUrl != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDetectedUrlClick(clipboardUrl!!) },
                    shape = RoundedCornerShape(12.dp),
                    color = colors.accent.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Copied link ready to fetch", style = SnapTypography.Metadata, color = colors.textPrimary)
                            Text(
                                text = clipboardUrl.orEmpty(),
                                style = SnapTypography.Metadata,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
