package com.snapfetch.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.data.extractor.MockPlatformCatalog
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchInputScreen(
    initialQuery: String = "",
    container: AppContainer,
    onBack: () -> Unit,
    onSubmitSearch: (String, String?) -> Unit,
    onOpenMoreTab: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val recentSearches by container.database.searchHistoryDao().observeRecent().collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            suggestions = emptyList()
        } else {
            delay(250L)
            suggestions = container.extractorRepository.getSuggestions(trimmed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Floating search bar matching 02-search.webp & 03-suggestions.webp
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SnapSpacing.ResultsSearchBarMargin, vertical = 8.dp)
                .height(SnapSpacing.ResultsSearchBarHeight),
            shape = RoundedCornerShape(SnapSpacing.ResultsSearchBarRadius),
            color = colors.elevatedSurface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search or paste link",
                            style = SnapTypography.ListRowTitle,
                            color = colors.textSecondary
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = SnapTypography.ListRowTitle.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (query.isNotBlank()) onSubmitSearch(query.trim(), null)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = colors.textSecondary)
                    }
                }
            }
        }

        if (query.isBlank()) {
            // Recent searches + Hot searches + View sites (02-search.webp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (recentSearches.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent searches", style = SnapTypography.Metadata.copy(fontSize = 14.sp), color = colors.textSecondary)
                        IconButton(onClick = {
                            scope.launch { container.database.searchHistoryDao().clearAll() }
                        }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear history", tint = colors.textMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSearches.take(8).forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = colors.cardSurface,
                                modifier = Modifier.clickable { onSubmitSearch(entry.query, null) }
                            ) {
                                Text(
                                    text = entry.query,
                                    style = SnapTypography.ListRowTitle.copy(fontSize = 13.5.sp),
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                Text("Hot searches", style = SnapTypography.Metadata.copy(fontSize = 14.sp), color = colors.textSecondary)
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MockPlatformCatalog.popularQueries.forEach { hot ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.cardSurface,
                            modifier = Modifier.clickable { onSubmitSearch(hot, null) }
                        ) {
                            Text(
                                text = hot,
                                style = SnapTypography.ListRowTitle.copy(fontSize = 13.5.sp),
                                color = colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
                Text("View sites", style = SnapTypography.Metadata.copy(fontSize = 14.sp), color = colors.textSecondary)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SiteShortcutCircle("YouTube", Color(0xFFFF0000), "▶") { onSubmitSearch("trending music", "ALL") }
                    SiteShortcutCircle("Instagram", Color(0xFFE1306C), "IG") { onSubmitSearch("reels", "INSTAGRAM") }
                    SiteShortcutCircle("Facebook", Color(0xFF1877F2), "f") { onSubmitSearch("videos", "FACEBOOK") }
                    SiteShortcutCircle("TikTok", Color(0xFF111111), "♪") { onSubmitSearch("trending", "TIKTOK") }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onOpenMoreTab() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.MoreHoriz, contentDescription = "View all", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("View all", style = SnapTypography.Metadata, color = colors.textSecondary)
                    }
                }
            }
        } else {
            // Live suggestions matching 03-suggestions.webp with yellow query highlight
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(suggestions, key = { it }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubmitSearch(item, null) }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        val qLower = query.trim().lowercase()
                        val idx = item.lowercase().indexOf(qLower)
                        val annotated = buildAnnotatedString {
                            if (idx >= 0 && qLower.isNotEmpty()) {
                                append(item.substring(0, idx))
                                withStyle(SpanStyle(color = colors.wordmarkYellow, fontWeight = FontWeight.SemiBold)) {
                                    append(item.substring(idx, idx + qLower.length))
                                }
                                append(item.substring(idx + qLower.length))
                            } else {
                                append(item)
                            }
                        }
                        Text(
                            text = annotated,
                            style = SnapTypography.ListRowTitle,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteShortcutCircle(
    label: String,
    badgeColor: Color,
    symbol: String,
    onClick: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            if (symbol == "▶") {
                Icon(Icons.Filled.PlayArrow, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = SnapTypography.Metadata, color = colors.textSecondary)
    }
}
