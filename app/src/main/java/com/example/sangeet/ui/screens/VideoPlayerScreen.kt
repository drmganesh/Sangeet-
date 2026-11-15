package com.example.sangeet.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.sangeet.data.model.VideoTrack
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.screens.player.formatMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: VideoTrack,
    repository: MediaRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    LaunchedEffect(video.id) {
        PlaybackManager.getInstance(context).pause()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(video.watchProgressMs) }
    var durationMs by remember { mutableLongStateOf(video.durationMs) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Feedback indicators for brightness and volume gestures
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }

    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = if (video.contentUri.isNotBlank()) Uri.parse(video.contentUri) else Uri.parse(video.path)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            if (video.watchProgressMs > 0) {
                seekTo(video.watchProgressMs)
            }
            play()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = videoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        videoPlayer.addListener(listener)

        onDispose {
            coroutineScope.launch {
                repository.updateVideoProgress(video.id, videoPlayer.currentPosition)
            }
            videoPlayer.removeListener(listener)
            videoPlayer.release()
        }
    }

    // Auto-progress tracker
    LaunchedEffect(Unit) {
        while (true) {
            if (videoPlayer.isPlaying) {
                currentPosMs = videoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(500L)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000L)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_screen")
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            videoPlayer.seekTo((videoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                            gestureIndicatorText = "-10s"
                        } else {
                            videoPlayer.seekTo((videoPlayer.currentPosition + 10000L).coerceAtMost(videoPlayer.duration))
                            gestureIndicatorText = "+10s"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val isRightSide = change.position.x > size.width / 2
                    if (isRightSide) {
                        // Volume control
                        val direction = if (dragAmount < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        gestureIndicatorText = "Vol: ${(currentVol * 100) / maxVol}%"
                    } else {
                        // Brightness control
                        val activity = context as? Activity
                        val layoutParams = activity?.window?.attributes
                        if (layoutParams != null) {
                            var currentBrightness = layoutParams.screenBrightness
                            if (currentBrightness < 0f) currentBrightness = 0.5f
                            val delta = -dragAmount / size.height
                            layoutParams.screenBrightness = (currentBrightness + delta).coerceIn(0.05f, 1.0f)
                            activity.window.attributes = layoutParams
                            gestureIndicatorText = "Brightness: ${(layoutParams.screenBrightness * 100).toInt()}%"
                        }
                    }
                }
            }
    ) {
        // ExoPlayer Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = videoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture HUD feedback overlay
        if (gestureIndicatorText != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Text(
                    text = gestureIndicatorText!!,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            LaunchedEffect(gestureIndicatorText) {
                delay(1200L)
                gestureIndicatorText = null
            }
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close", tint = Color(0xFFFFD666))
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        CassetteHeaderBadge(text = "${video.resolution} • OFFLINE VIDEO")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    // Speed toggle
                    TextButton(onClick = {
                        playbackSpeed = when (playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            2.0f -> 0.75f
                            else -> 1.0f
                        }
                        videoPlayer.setPlaybackSpeed(playbackSpeed)
                    }) {
                        Text("${playbackSpeed}x", color = Color(0xFFFFD666), fontWeight = FontWeight.Bold)
                    }
                }

                // Center Play / Pause
                IconButton(
                    onClick = {
                        if (videoPlayer.isPlaying) {
                            videoPlayer.pause()
                        } else {
                            videoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                        contentDescription = "Play/Pause",
                        tint = Color(0xFFFFD666),
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Bottom Seek Bar & Timers
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Slider(
                        value = if (durationMs > 0) (currentPosMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { ratio ->
                            val targetMs = (ratio * durationMs).toLong()
                            videoPlayer.seekTo(targetMs)
                            currentPosMs = targetMs
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMs(currentPosMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Text(formatMs(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
