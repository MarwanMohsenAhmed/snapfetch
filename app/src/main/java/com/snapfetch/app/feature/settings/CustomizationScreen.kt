package com.snapfetch.app.feature.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.datastore.LibraryLayoutDensity
import com.snapfetch.app.core.datastore.StartupTab
import com.snapfetch.app.core.datastore.ThemeMode
import com.snapfetch.app.core.datastore.ThumbnailQuality
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.theme.AccentPresets
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.core.theme.parseHexColor
import kotlinx.coroutines.launch

@Composable
fun CustomizationScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

    var showThemeDialog by remember { mutableStateOf(false) }
    var showStartupDialog by remember { mutableStateOf(false) }
    var showDensityDialog by remember { mutableStateOf(false) }
    var showThumbDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Text("Theme & Customization", style = SnapTypography.ScreenTitle, color = colors.textPrimary)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingChoiceRow(
                    title = "Theme mode",
                    subtitle = when (settings.themeMode) {
                        ThemeMode.LIGHT -> "Light (Default #FFFFFF)"
                        ThemeMode.DARK -> "Dark (#121212)"
                        ThemeMode.AMOLED -> "AMOLED Black (#000000)"
                    },
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Accent color", style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccentPresets.forEach { (label, hex) ->
                            val selected = settings.accentColorHex.equals(hex, ignoreCase = true)
                            val swatch = parseHexColor(hex)
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { scope.launch { container.settingsDataStore.setAccentColorHex(hex) } },
                                shape = CircleShape,
                                color = swatch,
                                border = if (selected) BorderStroke(2.dp, colors.textPrimary) else null
                            ) {
                                if (selected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Check, contentDescription = label, tint = colors.buttonTextOnAccent, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingChoiceRow(
                    title = "Startup tab",
                    subtitle = when (settings.startupTab) {
                        StartupTab.SEARCH -> "Search (Home)"
                        StartupTab.YOUTUBE -> "YouTube"
                        StartupTab.MUSIC -> "Music"
                        StartupTab.PLAY_LIBRARY -> "Play (Offline Library)"
                    },
                    onClick = { showStartupDialog = true }
                )
            }
            item {
                SettingChoiceRow(
                    title = "Library layout density",
                    subtitle = when (settings.libraryLayoutDensity) {
                        LibraryLayoutDensity.COMPACT_LIST -> "Compact List"
                        LibraryLayoutDensity.LARGE_CARDS -> "Large Cards"
                    },
                    onClick = { showDensityDialog = true }
                )
            }
            item {
                SettingChoiceRow(
                    title = "Thumbnail quality",
                    subtitle = when (settings.thumbnailQuality) {
                        ThumbnailQuality.AUTO_LOW_RAM -> "Auto (Low-RAM optimized)"
                        ThumbnailQuality.LOW -> "Low (320px • Data Saver)"
                        ThumbnailQuality.MEDIUM -> "Medium (480px)"
                        ThumbnailQuality.HIGH -> "High (720px)"
                    },
                    onClick = { showThumbDialog = true }
                )
            }
        }
    }

    if (showThemeDialog) {
        OptionPickerDialog(
            title = "Theme mode",
            options = listOf(
                ThemeMode.LIGHT to "Light (Default #FFFFFF)",
                ThemeMode.DARK to "Dark (#121212)",
                ThemeMode.AMOLED to "AMOLED Black (#000000)"
            ),
            current = settings.themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setThemeMode(it) } }
        )
    }

    if (showStartupDialog) {
        OptionPickerDialog(
            title = "Startup tab",
            options = listOf(
                StartupTab.SEARCH to "Search (Home)",
                StartupTab.YOUTUBE to "YouTube",
                StartupTab.MUSIC to "Music",
                StartupTab.PLAY_LIBRARY to "Play (Offline Library)"
            ),
            current = settings.startupTab,
            onDismiss = { showStartupDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setStartupTab(it) } }
        )
    }

    if (showDensityDialog) {
        OptionPickerDialog(
            title = "Library layout density",
            options = listOf(
                LibraryLayoutDensity.COMPACT_LIST to "Compact List",
                LibraryLayoutDensity.LARGE_CARDS to "Large Cards"
            ),
            current = settings.libraryLayoutDensity,
            onDismiss = { showDensityDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setLibraryLayoutDensity(it) } }
        )
    }

    if (showThumbDialog) {
        OptionPickerDialog(
            title = "Thumbnail quality",
            options = listOf(
                ThumbnailQuality.AUTO_LOW_RAM to "Auto (Low-RAM optimized)",
                ThumbnailQuality.LOW to "Low (320px)",
                ThumbnailQuality.MEDIUM to "Medium (480px)",
                ThumbnailQuality.HIGH to "High (720px)"
            ),
            current = settings.thumbnailQuality,
            onDismiss = { showThumbDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setThumbnailQuality(it) } }
        )
    }
}
