package com.example.sangeet.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.PlayerStyle
import com.example.sangeet.playback.PlaybackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContainer(
    playbackManager: PlaybackManager,
    onDismiss: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSpatial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by playbackManager.currentTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val positionMs by playbackManager.currentPositionMs.collectAsState()
    val durationMs by playbackManager.durationMs.collectAsState()
    val isShuffle by playbackManager.isShuffleEnabled.collectAsState()
    val repeatMode by playbackManager.repeatMode.collectAsState()
    val playerStyle by playbackManager.playerStyle.collectAsState()
    val queue by playbackManager.queue.collectAsState()

    var showStyleMenu by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    if (currentTrack == null) {
        onDismiss()
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_player_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PLAYING FROM LIBRARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = playerStyle.displayName + " Style",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("player_collapse_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Style Switcher
                    Box {
                        IconButton(
                            onClick = { showStyleMenu = true },
                            modifier = Modifier.testTag("player_style_picker")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Style,
                                contentDescription = "Switch Style",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showStyleMenu,
                            onDismissRequest = { showStyleMenu = false }
                        ) {
                            PlayerStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = style.displayName,
                                                fontWeight = if (style == playerStyle) FontWeight.Bold else FontWeight.Normal,
                                                color = if (style == playerStyle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = style.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        playbackManager.setPlayerStyle(style)
                                        showStyleMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // 15-Band EQ Shortcut
                    IconButton(
                        onClick = onNavigateToEqualizer,
                        modifier = Modifier.testTag("player_eq_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 360 Spatial Shortcut
                    IconButton(
                        onClick = onNavigateToSpatial,
                        modifier = Modifier.testTag("player_spatial_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SurroundSound,
                            contentDescription = "360 Spatial Audio",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Lyrics Shortcut
                    IconButton(
                        onClick = { showLyricsSheet = true },
                        modifier = Modifier.testTag("player_lyrics_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = "Lyrics",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Queue Shortcut
                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier.testTag("player_queue_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Render the currently selected style
            when (playerStyle) {
                PlayerStyle.CLASSIC -> ClassicPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) },
                    onToggleShuffle = { playbackManager.toggleShuffle() },
                    onCycleRepeat = { playbackManager.cycleRepeatMode() }
                )
                PlayerStyle.GESTURE -> GesturePlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) }
                )
                PlayerStyle.EDITORIAL -> EditorialPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) },
                    onOpenLyrics = { showLyricsSheet = true }
                )
                PlayerStyle.CANVAS -> CanvasPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) }
                )
                PlayerStyle.BENTO -> BentoPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenEq = onNavigateToEqualizer,
                    onOpenSpatial = onNavigateToSpatial
                )
                PlayerStyle.STICKER -> StickerPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) }
                )
                PlayerStyle.MORPH -> MorphPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) }
                )
                PlayerStyle.DIAL -> DialPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) }
                )
                PlayerStyle.HERO -> HeroPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onNext = { playbackManager.playNext() },
                    onPrev = { playbackManager.playPrevious() },
                    onSeek = { playbackManager.seekTo(it) }
                )
            }
        }
    }

    // Lyrics Modal Bottom Sheet
    if (showLyricsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLyricsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LyricsSheetContent(
                track = currentTrack!!,
                currentPositionMs = positionMs,
                onSeek = { playbackManager.seekTo(it) },
                onClose = { showLyricsSheet = false }
            )
        }
    }

    // Queue Modal Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QueueSheetContent(
                queue = queue,
                currentTrack = currentTrack,
                onSelectTrack = { playbackManager.playTrack(it) },
                onRemoveTrack = { playbackManager.removeFromQueue(it) },
                onClose = { showQueueSheet = false }
            )
        }
    }
}
