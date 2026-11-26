package com.snapfetch.app.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.components.VideoRowItem
import com.snapfetch.app.core.perf.SnapThumbnail
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.data.extractor.ExtractedStreamBundle
import com.snapfetch.app.data.local.entities.PlaybackPositionEntity
import com.snapfetch.app.domain.model.VideoItem
import com.snapfetch.app.domain.model.formatDurationSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UnifiedPlayerScreen(
    mediaKey: String,
    isLocal: Boolean,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenFormatSheet: (VideoItem) -> Unit,
    onOpenRelatedVideo: (VideoItem) -> Unit,
    onOpenAddToPlaylist: (String, String) -> Unit,
    onOpenCompressSheet: (String, String) -> Unit,
    isInPipMode: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(
        initialValue = com.snapfetch.app.core.datastore.UserSettings()
    )
    val queueState by container.playbackQueueController.state.collectAsStateWithLifecycle()

    var activeMediaKey by remember(mediaKey) { mutableStateOf(mediaKey) }
    var activeIsLocal by remember(isLocal) { mutableStateOf(isLocal) }

    var streamBundle by remember { mutableStateOf<ExtractedStreamBundle?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var player by remember { mutableStateOf<Player?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(settings.defaultPlaybackSpeed) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var gestureFeedback by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val thumbPx = container.lowRamConfig.effectiveThumbnailPx(settings.thumbnailQuality, settings.dataSaverEnabled)
    val preferRgb565 = container.lowRamConfig.isLowRamDevice || settings.dataSaverEnabled

    // Connect to Media3 MediaSessionService for background audio & lockscreen controls
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                val controller = future.get()
                player = controller
            }
        }, context.mainExecutor)
        onDispose {
            MediaController.releaseFuture(future)
            player = null
        }
    }

    // Helper to save playback position
    suspend fun persistPosition(key: String, pos: Long, dur: Long, speed: Float) {
        if (dur <= 0L) return
        val finished = pos >= (dur * 0.95).toLong() || (dur - pos) < 5_000L
        container.database.playbackPositionDao().upsert(
            PlaybackPositionEntity(
                mediaKey = key,
                positionMs = pos,
                durationMs = dur,
                playbackSpeed = speed,
                lastPlayedAtEpochMs = System.currentTimeMillis(),
                isFinished = finished
            )
        )
    }

    // Load media metadata & start playback
    LaunchedEffect(activeMediaKey, activeIsLocal, player) {
        val p = player ?: return@LaunchedEffect
        isLoading = true

        val resolvedUri: String?
        val title: String
        val subtitle: String

        if (activeIsLocal) {
            val fromQueue = queueState.tracks.firstOrNull { it.mediaKey == activeMediaKey }
            val fromDb = container.database.downloadDao().getById(activeMediaKey)
            resolvedUri = fromQueue?.uri ?: fromDb?.localFileUri ?: activeMediaKey
            title = fromQueue?.title ?: fromDb?.title ?: Uri.parse(resolvedUri).lastPathSegment ?: "Local Media"
            subtitle = fromQueue?.subtitle ?: fromDb?.channelName ?: "Offline Library"
            streamBundle = null
        } else {
            val bundle = withContext(Dispatchers.IO) {
                container.resolveFormatsUseCase(activeMediaKey)
            }
            streamBundle = bundle
            resolvedUri = bundle.previewStreamUrl
                ?: bundle.formats.firstOrNull { it.streamUrl.isNotBlank() }?.streamUrl
            title = bundle.video.title
            subtitle = bundle.video.channelName
            if (queueState.tracks.isEmpty()) {
                val tracks = listOf(
                    QueueTrack(
                        mediaKey = bundle.video.id,
                        title = title,
                        subtitle = subtitle,
                        uri = resolvedUri.orEmpty(),
                        thumbnailUrl = bundle.video.thumbnailUrl,
                        durationSec = bundle.video.durationSec,
                        isLocal = false
                    )
                ) + bundle.relatedVideos.map {
                    QueueTrack(it.id, it.title, it.channelName, it.streamPageUrl, it.thumbnailUrl, it.durationSec, false)
                }
                container.playbackQueueController.setQueue(tracks, 0)
            }
        }

        if (!resolvedUri.isNullOrBlank()) {
            val mediaItem = MediaItem.Builder()
                .setMediaId(activeMediaKey)
                .setUri(resolvedUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(subtitle)
                        .build()
                )
                .build()
            p.setMediaItem(mediaItem)
            p.setPlaybackSpeed(playbackSpeed)
            p.prepare()

            val saved = if (settings.resumePlaybackEnabled) {
                container.database.playbackPositionDao().getPosition(activeMediaKey)
            } else null

            if (saved != null && saved.positionMs > 2_000L && !saved.isFinished) {
                p.seekTo(saved.positionMs)
                playbackSpeed = saved.playbackSpeed
                p.setPlaybackSpeed(saved.playbackSpeed)
                val ts = formatDurationSeconds((saved.positionMs / 1000L).toInt())
                scope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "Resumed at $ts",
                        actionLabel = "Start over",
                        withDismissAction = true
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        p.seekTo(0L)
                    }
                }
            }
            p.playWhenReady = true
        }
        isLoading = false
    }

    // Track listener + periodic 5s position saver + auto-play next
    DisposableEffect(player, activeMediaKey) {
        val p = player ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    val pos = p.currentPosition
                    val dur = p.duration.coerceAtLeast(0L)
                    scope.launch { persistPosition(activeMediaKey, pos, dur, playbackSpeed) }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val dur = p.duration.coerceAtLeast(0L)
                    scope.launch { persistPosition(activeMediaKey, dur, dur, playbackSpeed) }
                    if (settings.autoPlayNext) {
                        val next = container.playbackQueueController.nextTrack(autoAdvance = true)
                        if (next != null) {
                            if (next.mediaKey == activeMediaKey) {
                                p.seekTo(0L)
                                p.play()
                            } else {
                                activeMediaKey = next.mediaKey
                                activeIsLocal = next.isLocal
                            }
                        }
                    }
                }
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    // Poll UI progress + persist position every 5s
    LaunchedEffect(player, activeMediaKey) {
        var tick = 0
        while (true) {
            val p = player
            if (p != null) {
                currentPosMs = p.currentPosition.coerceAtLeast(0L)
                durationMs = p.duration.coerceAtLeast(0L)
                isPlaying = p.isPlaying
                if (p.isPlaying && tick % 10 == 0) {
                    persistPosition(activeMediaKey, currentPosMs, durationMs, playbackSpeed)
                }
            }
            tick++
            delay(500L)
        }
    }

    // Save position on ON_STOP
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player, activeMediaKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val p = player ?: return@LifecycleEventObserver
                val pos = p.currentPosition
                val dur = p.duration.coerceAtLeast(0L)
                scope.launch { persistPosition(activeMediaKey, pos, dur, playbackSpeed) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Fullscreen system bar & orientation handling
    DisposableEffect(isFullscreen) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (isFullscreen && activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }

    val currentVideo: VideoItem? = streamBundle?.video ?: queueState.currentTrack?.let {
        VideoItem(
            id = it.mediaKey,
            title = it.title,
            channelName = it.subtitle,
            thumbnailUrl = it.thumbnailUrl,
            durationSec = it.durationSec,
            streamPageUrl = it.uri
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullscreen || isInPipMode) Color.Black else colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top 16:9 (or full-screen) video surface + overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFullscreen || isInPipMode) Modifier.fillMaxSize()
                        else Modifier
                            .background(Color.Black)
                            .statusBarsPadding()
                            .aspectRatio(16f / 9f)
                    )
                    .background(Color.Black)
            ) {
                if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                this.player = player
                            }
                        },
                        update = { view ->
                            if (view.player !== player) view.player = player
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!currentVideo?.thumbnailUrl.isNullOrBlank()) {
                    SnapThumbnail(
                        model = currentVideo?.thumbnailUrl,
                        contentDescription = currentVideo?.title,
                        modifier = Modifier.fillMaxSize(),
                        targetWidthPx = 480,
                        preferRgb565 = preferRgb565
                    )
                }

                // Gesture & tap layer (double tap left/right seek, single tap toggle controls, vertical swipe brightness/volume)
                if (!isInPipMode) {
                    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(controlsLocked, settings.seekStepSeconds) {
                                detectTapGestures(
                                    onTap = { controlsVisible = !controlsVisible },
                                    onDoubleTap = { offset ->
                                        if (controlsLocked) return@detectTapGestures
                                        val p = player ?: return@detectTapGestures
                                        val stepMs = settings.seekStepSeconds * 1000L
                                        if (offset.x < size.width / 2f) {
                                            p.seekTo((p.currentPosition - stepMs).coerceAtLeast(0L))
                                            gestureFeedback = "-${settings.seekStepSeconds}s"
                                        } else {
                                            val max = p.duration.coerceAtLeast(0L)
                                            p.seekTo((p.currentPosition + stepMs).coerceAtMost(if (max > 0) max else Long.MAX_VALUE))
                                            gestureFeedback = "+${settings.seekStepSeconds}s"
                                        }
                                    }
                                )
                            }
                            .pointerInput(controlsLocked, settings.gestureControlsEnabled) {
                                if (!settings.gestureControlsEnabled || controlsLocked) return@pointerInput
                                var startX = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { pos -> startX = pos.x },
                                    onDragEnd = { gestureFeedback = null },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        if (startX < size.width / 2f && activity != null) {
                                            val lp = activity.window.attributes
                                            val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                            val next = (cur - dragAmount / 600f).coerceIn(0.05f, 1.0f)
                                            lp.screenBrightness = next
                                            activity.window.attributes = lp
                                            gestureFeedback = "Brightness ${(next * 100).toInt()}%"
                                        } else {
                                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            if (dragAmount < -18f) {
                                                val next = (curVol + 1).coerceAtMost(maxVol)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                                gestureFeedback = "Volume ${(next * 100) / maxVol.coerceAtLeast(1)}%"
                                            } else if (dragAmount > 18f) {
                                                val next = (curVol - 1).coerceAtLeast(0)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                                gestureFeedback = "Volume ${(next * 100) / maxVol.coerceAtLeast(1)}%"
                                            }
                                        }
                                    }
                                )
                            }
                    )
                }

                // Clear double-tap feedback automatically
                LaunchedEffect(gestureFeedback) {
                    if (gestureFeedback != null) {
                        delay(900L)
                        gestureFeedback = null
                    }
                }

                if (gestureFeedback != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xAA000000))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(gestureFeedback.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Controls overlay matching 05-video-page.webp
                if (!isInPipMode && controlsVisible) {
                    if (controlsLocked) {
                        IconButton(
                            onClick = { controlsLocked = false },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color(0x88000000), CircleShape)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = "Unlock", tint = Color.White)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x44000000))
                        ) {
                            // Top row: Back, PiP, Lock, Speed, More
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    if (isFullscreen) isFullscreen = false else onBack()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        Text(
                                            text = "${playbackSpeed}x",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { showSpeedMenu = true }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                        DropdownMenu(
                                            expanded = showSpeedMenu,
                                            onDismissRequest = { showSpeedMenu = false }
                                        ) {
                                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                                DropdownMenuItem(
                                                    text = { Text("${spd}x") },
                                                    onClick = {
                                                        playbackSpeed = spd
                                                        player?.setPlaybackSpeed(spd)
                                                        showSpeedMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = { controlsLocked = true }) {
                                        Icon(Icons.Filled.LockOpen, contentDescription = "Lock controls", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { enterPip() }) {
                                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(21.dp))
                                    }
                                    IconButton(onClick = { showQueueSheet = true }) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }

                            // Center transport: Previous | Play/Pause | Next
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(32.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    val prev = container.playbackQueueController.previousTrack()
                                    if (prev != null) {
                                        activeMediaKey = prev.mediaKey
                                        activeIsLocal = prev.isLocal
                                    } else {
                                        player?.seekTo(0L)
                                    }
                                }) {
                                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val p = player ?: return@IconButton
                                        if (p.isPlaying) p.pause() else p.play()
                                    },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    val next = container.playbackQueueController.nextTrack(autoAdvance = false)
                                    if (next != null) {
                                        activeMediaKey = next.mediaKey
                                        activeIsLocal = next.isLocal
                                    }
                                }) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                                }
                            }

                            // Bottom scrubber row: 00:05 [====yellow====] 03:31 [Fullscreen]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDurationSeconds((currentPosMs / 1000L).toInt()),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                Slider(
                                    value = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { frac ->
                                        if (durationMs > 0L) {
                                            val target = (frac * durationMs).toLong()
                                            currentPosMs = target
                                            player?.seekTo(target)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.accent,
                                        activeTrackColor = colors.accent,
                                        inactiveTrackColor = Color(0x66FFFFFF)
                                    )
                                )
                                Text(
                                    text = formatDurationSeconds((durationMs / 1000L).toInt()),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                IconButton(
                                    onClick = { isFullscreen = !isFullscreen },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Below-player details matching 05-video-page.webp
            if (!isFullscreen && !isInPipMode) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    item(key = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                val avatarUrl = streamBundle?.channel?.avatarUrl ?: currentVideo?.thumbnailUrl
                                SnapThumbnail(
                                    model = avatarUrl,
                                    contentDescription = currentVideo?.channelName,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(colors.cardSurface),
                                    targetWidthPx = 120,
                                    preferRgb565 = preferRgb565
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentVideo?.title ?: "Playing Media",
                                        style = SnapTypography.ListRowTitle.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium),
                                        color = colors.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!currentVideo?.channelName.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = currentVideo?.channelName.orEmpty(),
                                            style = SnapTypography.Metadata,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Wide yellow Download pill button + Headphones (background audio) + 3-dot overflow
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (activeIsLocal) {
                                            onOpenCompressSheet(
                                                queueState.currentTrack?.uri ?: activeMediaKey,
                                                currentVideo?.title ?: "Video"
                                            )
                                        } else {
                                            currentVideo?.let(onOpenFormatSheet)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(SnapSpacing.VideoDownloadButtonHeight),
                                    shape = RoundedCornerShape(SnapSpacing.PillButtonRadius),
                                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.buttonTextOnAccent
                                    )
                                ) {
                                    Text(
                                        text = if (activeIsLocal) "Reduce video size" else "Download",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            container.settingsDataStore.setBackgroundAudio(true)
                                            snackbarHostState.showSnackbar("Background audio active — continue listening when minimized")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Headphones,
                                        contentDescription = "Background Audio",
                                        tint = colors.textPrimary
                                    )
                                }

                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "More options",
                                            tint = colors.textPrimary
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Add to playlist") },
                                            onClick = {
                                                showMoreMenu = false
                                                onOpenAddToPlaylist(
                                                    activeMediaKey,
                                                    currentVideo?.title ?: "Media"
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Up next queue") },
                                            onClick = {
                                                showMoreMenu = false
                                                showQueueSheet = true
                                            }
                                        )
                                        if (activeIsLocal) {
                                            DropdownMenuItem(
                                                text = { Text("Reduce video size") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    onOpenCompressSheet(
                                                        queueState.currentTrack?.uri ?: activeMediaKey,
                                                        currentVideo?.title ?: "Video"
                                                    )
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            onClick = {
                                                showMoreMenu = false
                                                val shareText = currentVideo?.streamPageUrl?.ifBlank { currentVideo.title } ?: activeMediaKey
                                                val send = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                }
                                                context.startActivity(Intent.createChooser(send, "Share"))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy link") },
                                            onClick = {
                                                showMoreMenu = false
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val link = currentVideo?.streamPageUrl?.ifBlank { activeMediaKey } ?: activeMediaKey
                                                cm.setPrimaryClip(ClipData.newPlainText("SnapFetch Link", link))
                                                scope.launch { snackbarHostState.showSnackbar("Link copied") }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Comments #F6F6F6 card (05-video-page)
                    val comments = streamBundle?.comments.orEmpty()
                    if (!activeIsLocal) {
                        item(key = "comments_card") {
                            val preview = comments.firstOrNull()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.cardSurface)
                                    .clickable { showCommentsSheet = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Comments",
                                    style = SnapTypography.ListRowTitle.copy(fontSize = 13.5.sp),
                                    color = colors.textBodyDark
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFC2185B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preview?.author?.firstOrNull()?.lowercase() ?: "r",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = preview?.text ?: "Tap to view comments...",
                                        style = SnapTypography.ListRowTitle.copy(fontSize = 13.5.sp),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Related videos feed (first featured large thumbnail like 05-video-page, then standard rows)
                        val related = streamBundle?.relatedVideos.orEmpty()
                        if (related.isNotEmpty()) {
                            val hero = related.first()
                            item(key = "related_hero_${hero.id}") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable { onOpenRelatedVideo(hero) }
                                ) {
                                    SnapThumbnail(
                                        model = hero.thumbnailUrl,
                                        contentDescription = hero.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.cardSurface),
                                        targetWidthPx = 480,
                                        preferRgb565 = preferRgb565
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = hero.title,
                                        style = SnapTypography.ListRowTitle,
                                        color = colors.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${hero.channelName} • ${hero.formattedDuration()}",
                                        style = SnapTypography.Metadata,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            items(
                                items = related.drop(1),
                                key = { "rel_${it.id}" }
                            ) { video ->
                                VideoRowItem(
                                    video = video,
                                    thumbPx = thumbPx,
                                    preferRgb565 = preferRgb565,
                                    isSelectionMode = false,
                                    isSelected = false,
                                    onClick = { onOpenRelatedVideo(video) },
                                    onLongClick = {},
                                    onDownloadClick = { onOpenFormatSheet(video) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queueState = queueState,
            onDismiss = { showQueueSheet = false },
            onSelectTrack = { idx ->
                val picked = container.playbackQueueController.selectIndex(idx)
                if (picked != null) {
                    activeMediaKey = picked.mediaKey
                    activeIsLocal = picked.isLocal
                }
                showQueueSheet = false
            },
            onToggleShuffle = { container.playbackQueueController.toggleShuffle() },
            onCycleRepeat = { container.playbackQueueController.cycleRepeat() },
            onReorder = { from, to -> container.playbackQueueController.reorder(from, to) },
            onRemove = { idx -> container.playbackQueueController.removeAt(idx) }
        )
    }

    if (showCommentsSheet) {
        CommentsSheet(
            comments = streamBundle?.comments.orEmpty(),
            onDismiss = { showCommentsSheet = false }
        )
    }
}
