package com.snapfetch.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.storage.JunkSummary
import com.snapfetch.app.core.storage.LargeFileItem
import com.snapfetch.app.core.storage.StatusFile
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.MediaType
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

@Composable
fun HelperToolStubScreen(
    toolId: String,
    container: AppContainer,
    onBack: () -> Unit,
    onPlayUri: (String) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    val subscriptions by container.database.subscribedChannelDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    val screenTitle = when (toolId) {
        "trash" -> "Recover deleted files"
        "whatsapp" -> "WhatsApp status saver"
        "vault" -> "Vault"
        "junk" -> "Junk clean"
        "large_files" -> "Large files clean"
        "account" -> "Account"
        "language" -> "Language"
        else -> "About SnapFetch"
    }

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
            Text(screenTitle, style = SnapTypography.ScreenTitle, color = colors.textPrimary)
        }

        when (toolId) {
            "junk" -> {
                var summary by remember { mutableStateOf(JunkSummary(0, 0, 0)) }
                var freedMsg by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    val active = container.database.downloadDao().getActivePartPaths().toSet()
                    summary = container.storageScanner.scanJunk(active)
                }
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoMetricCard("Image Cache", FormatSizeCalculator.formatBytes(summary.imageCacheBytes))
                    InfoMetricCard("Orphaned Partial Downloads", FormatSizeCalculator.formatBytes(summary.orphanPartBytes))
                    InfoMetricCard("Compression Temp Files", FormatSizeCalculator.formatBytes(summary.tempFilesBytes))
                    Button(
                        onClick = {
                            scope.launch {
                                val active = container.database.downloadDao().getActivePartPaths().toSet()
                                val freed = container.storageScanner.cleanJunk(active)
                                summary = container.storageScanner.scanJunk(active)
                                freedMsg = "Cleaned ${FormatSizeCalculator.formatBytes(freed)}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                    ) {
                        Text("Clean ${FormatSizeCalculator.formatBytes(summary.totalBytes)}", fontWeight = FontWeight.Bold)
                    }
                    freedMsg?.let { Text(it, style = SnapTypography.Metadata, color = colors.textPrimary) }
                }
            }
            "large_files" -> {
                var files by remember { mutableStateOf<List<LargeFileItem>>(emptyList()) }
                LaunchedEffect(Unit) { files = container.storageScanner.scanLargeFiles() }
                if (files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No files over 50 MB found", style = SnapTypography.ListRowTitle, color = colors.textSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(files, key = { it.uri.toString() }) { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayUri(f.uri.toString()) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(f.name, style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                                    Text(FormatSizeCalculator.formatBytes(f.sizeBytes), style = SnapTypography.Metadata, color = colors.textSecondary)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        container.storageScanner.moveUriToTrash(f.uri.toString(), f.name)
                                        files = container.storageScanner.scanLargeFiles()
                                    }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Move to trash", tint = colors.textMuted)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)
                        }
                    }
                }
            }
            "trash" -> {
                var trashFiles by remember { mutableStateOf(container.storageScanner.listTrashFiles()) }
                if (trashFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Trash bin is empty", style = SnapTypography.ListRowTitle, color = colors.textSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(trashFiles, key = { it.absolutePath }) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name.substringAfter('_'), style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                                    Text(FormatSizeCalculator.formatBytes(file.length()), style = SnapTypography.Metadata, color = colors.textSecondary)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        container.mediaStoreSaver.saveCompletedFile(
                                            sourceFile = file,
                                            displayName = file.name.substringAfter('_'),
                                            mimeType = "video/mp4",
                                            mediaType = MediaType.VIDEO,
                                            safTreeUriString = settings.saveFolderUri
                                        )
                                        file.delete()
                                        trashFiles = container.storageScanner.listTrashFiles()
                                    }
                                }) {
                                    Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = colors.textPrimary)
                                }
                                IconButton(onClick = {
                                    file.delete()
                                    trashFiles = container.storageScanner.listTrashFiles()
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete permanently", tint = colors.textMuted)
                                }
                            }
                        }
                    }
                }
            }
            "whatsapp" -> {
                var statuses by remember { mutableStateOf<List<StatusFile>>(emptyList()) }
                var savedNote by remember { mutableStateOf<String?>(null) }
                val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                    if (uri != null) {
                        container.safHelper.takePersistablePermission(uri)
                        statuses = container.safHelper.listStatusesFromTree(uri.toString())
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { treePicker.launch(null) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                    ) {
                        Text("Select WhatsApp .Statuses Folder", fontWeight = FontWeight.Bold)
                    }
                    savedNote?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, style = SnapTypography.Metadata, color = colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn {
                        items(statuses, key = { it.uri.toString() }) { st ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(st.name, style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                                    Text(FormatSizeCalculator.formatBytes(st.sizeBytes), style = SnapTypography.Metadata, color = colors.textSecondary)
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val tmp = File(context.cacheDir, st.name)
                                            context.contentResolver.openInputStream(st.uri)?.use { inp ->
                                                tmp.outputStream().use { out -> inp.copyTo(out) }
                                            }
                                            val type = if (st.mimeType.startsWith("image")) MediaType.PICTURE else MediaType.VIDEO
                                            container.mediaStoreSaver.saveCompletedFile(tmp, st.name, st.mimeType, type, settings.saveFolderUri)
                                            tmp.delete()
                                            savedNote = "Saved ${st.name}"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                                ) {
                                    Text("Save", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            "vault" -> {
                var pinInput by remember { mutableStateOf("") }
                var unlocked by remember { mutableStateOf(settings.vaultPinHash == null) }
                val vaultFiles = remember(unlocked) { if (unlocked) container.storageScanner.listVaultFiles() else emptyList() }
                if (!unlocked) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Enter Vault PIN", style = SnapTypography.SectionHeading, color = colors.textPrimary)
                        OutlinedTextField(value = pinInput, onValueChange = { pinInput = it }, singleLine = true, label = { Text("4-digit PIN") })
                        Button(
                            onClick = {
                                if (sha256(pinInput) == settings.vaultPinHash) unlocked = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                        ) {
                            Text("Unlock", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (settings.vaultPinHash == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { pinInput = it },
                                    singleLine = true,
                                    label = { Text("Set Vault PIN") },
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (pinInput.length >= 4) {
                                            scope.launch { container.settingsDataStore.setVaultPinHash(sha256(pinInput)) }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                                ) {
                                    Text("Set PIN", fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (vaultFiles.isEmpty()) {
                            Text("Vault is empty. Move media here via Play → 3-dot menu → Move to Vault.", style = SnapTypography.Metadata, color = colors.textSecondary)
                        } else {
                            LazyColumn {
                                items(vaultFiles, key = { it.absolutePath }) { f ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPlayUri(Uri.fromFile(f).toString()) }
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(f.name.substringAfter('_'), style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                                        Text(FormatSizeCalculator.formatBytes(f.length()), style = SnapTypography.Metadata, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "account" -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local Subscribed Channels (${subscriptions.size})", style = SnapTypography.SectionHeading, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (subscriptions.isEmpty()) {
                        Text("No channels subscribed locally yet.", style = SnapTypography.Metadata, color = colors.textSecondary)
                    } else {
                        subscriptions.forEach { ch ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(ch.name, style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                                    Text(ch.subscriberText, style = SnapTypography.Metadata, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
            "language" -> {
                val langs = listOf("" to "System Default", "en" to "English (US)", "ar" to "العربية", "es" to "Español", "hi" to "हिन्दी", "pt" to "Português", "fr" to "Français")
                LazyColumn {
                    items(langs) { (tag, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { container.settingsDataStore.setAppLanguageTag(tag) }
                                    val list = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                                    AppCompatDelegate.setApplicationLocales(list)
                                    onBack()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                            if (settings.appLanguageTag == tag) {
                                Text("Active", style = SnapTypography.Metadata, color = colors.wordmarkYellow, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoMetricCard("SnapFetch", "Version 1.0.0 • Lite Core Architecture")
                    InfoMetricCard("Open Source Components", "AndroidX Media3 (ExoPlayer & Transformer), NewPipeExtractor, Room, WorkManager, Coil 3, OkHttp, Jetpack Compose")
                    InfoMetricCard("Device Profile", if (container.lowRamConfig.isLowRamDevice) "Low-RAM mode active (10 MB Coil cache, RGB_565, 1 worker)" else "Standard RAM profile (16 MB Coil cache)")
                }
            }
        }
    }
}

@Composable
private fun InfoMetricCard(title: String, value: String) {
    val colors = LocalSnapFetchColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.cardSurface
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = SnapTypography.Metadata, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = SnapTypography.ListRowTitle, color = colors.textPrimary)
        }
    }
}

private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
