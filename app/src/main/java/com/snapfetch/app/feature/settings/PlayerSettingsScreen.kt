package com.snapfetch.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import kotlinx.coroutines.launch

@Composable
fun PlayerSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }

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
            Text("Player settings", style = SnapTypography.ScreenTitle, color = colors.textPrimary)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingSwitchRow(
                    title = "Resume playback position",
                    subtitle = "Remember timestamp every 5s and restore on reopen",
                    checked = settings.resumePlaybackEnabled,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setResumePlayback(it) } }
                )
            }
            item {
                SettingChoiceRow(
                    title = "Default playback speed",
                    subtitle = "${settings.defaultPlaybackSpeed}x",
                    onClick = { showSpeedDialog = true }
                )
            }
            item {
                SettingChoiceRow(
                    title = "Double-tap seek step",
                    subtitle = "±${settings.seekStepSeconds} seconds",
                    onClick = { showSeekDialog = true }
                )
            }
            item {
                SettingSwitchRow(
                    title = "Auto-play next in queue",
                    subtitle = "Advance automatically when video/audio finishes",
                    checked = settings.autoPlayNext,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setAutoPlayNext(it) } }
                )
            }
            item {
                SettingSwitchRow(
                    title = "Background audio playback",
                    subtitle = "Keep playing via MediaSession when minimized",
                    checked = settings.backgroundAudioEnabled,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setBackgroundAudio(it) } }
                )
            }
            item {
                SettingSwitchRow(
                    title = "Brightness & volume swipe gestures",
                    subtitle = "Swipe left edge for brightness, right edge for volume",
                    checked = settings.gestureControlsEnabled,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setGestureControls(it) } }
                )
            }
            item {
                SettingSwitchRow(
                    title = "Prefer software decoder fallback",
                    subtitle = "Useful for older devices with H.264 hardware quirks",
                    checked = settings.preferSoftwareDecoderFallback,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setPreferSoftwareDecoder(it) } }
                )
            }
        }
    }

    if (showSpeedDialog) {
        OptionPickerDialog(
            title = "Default playback speed",
            options = listOf(0.75f to "0.75x", 1.0f to "1.0x (Normal)", 1.25f to "1.25x", 1.5f to "1.5x", 2.0f to "2.0x"),
            current = settings.defaultPlaybackSpeed,
            onDismiss = { showSpeedDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setDefaultPlaybackSpeed(it) } }
        )
    }

    if (showSeekDialog) {
        OptionPickerDialog(
            title = "Double-tap seek step",
            options = listOf(5 to "5 seconds", 10 to "10 seconds", 15 to "15 seconds"),
            current = settings.seekStepSeconds,
            onDismiss = { showSeekDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setSeekStepSeconds(it) } }
        )
    }
}
