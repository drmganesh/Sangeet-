package com.example.sangeet.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.ArtworkMotionPlanner
import com.example.sangeet.ui.components.rememberIsAppResumed

private val WalkmanBackdropTop = Color(0xFF0A0805)
private val WalkmanBackdropBottom = Color(0xFF000000)
private val WalkmanAccentGold = Color(0xFFE8B429)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContainer(
    playbackManager: PlaybackManager,
    onDismiss: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSpatial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack by playbackManager.currentTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val isStopped by playbackManager.isStopped.collectAsState()
    val positionMs by playbackManager.currentPositionMs.collectAsState()
    val durationMs by playbackManager.durationMs.collectAsState()
    val isShuffle by playbackManager.isShuffleEnabled.collectAsState()
    val repeatMode by playbackManager.repeatMode.collectAsState()
    val cassetteStyle by playbackManager.cassetteStyle.collectAsState()
    val mechanicalSounds by playbackManager.walkmanMechanicalSounds.collectAsState()
    val queue by playbackManager.queue.collectAsState()

    val isAppResumed by rememberIsAppResumed()
    val systemAnimatorsEnabled = remember(context, isAppResumed) {
        ArtworkMotionPlanner.isSystemAnimationEnabled(context)
    }
    val reducedMotion = !systemAnimatorsEnabled

    var showCassetteStyleSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    var seekImpulseDirection by remember { mutableIntStateOf(0) }
    var seekImpulseToken by remember { mutableIntStateOf(0) }

    val track = currentTrack
    if (track == null) {
        onDismiss()
        return
    }

    val safeDuration = durationMs.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1L)
    val progressFraction = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    val transportState = when {
        isPlaying -> WalkmanTransportState.PLAYING
        isStopped -> WalkmanTransportState.STOPPED
        else -> WalkmanTransportState.PAUSED
    }

    val swapController = rememberCassetteSwapController(
        currentTrack = track,
        reducedMotion = reducedMotion,
        onPlayMechanicalSound = { soundType ->
            WalkmanSoundEngine.playIfEnabled(mechanicalSounds, soundType)
        }
    )

    val reelAngles = rememberCassetteReelAngles(
        isPlaying = isPlaying && isAppResumed,
        isCassetteSeated = swapController.isCassetteSeated,
        progressFraction = progressFraction,
        seekImpulseDirection = seekImpulseDirection,
        seekImpulseToken = seekImpulseToken,
        reducedMotion = reducedMotion
    )

    fun resolveNeighborTrack(offset: Int): AudioTrack? {
        if (queue.isEmpty()) return null
        val curIdx = queue.indexOfFirst { it.id == track.id }
        if (curIdx == -1) return queue.firstOrNull()
        val targetIdx = ((curIdx + offset) % queue.size + queue.size) % queue.size
        return queue.getOrNull(targetIdx)
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
                            text = "SANGEET WALKMAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.4.sp
                            ),
                            color = WalkmanAccentGold
                        )
                        Text(
                            text = "Portable Cassette Stereo • Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_collapse_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Collapse Player",
                            tint = Color(0xFFF3F4F6)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCassetteStyleSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("walkman_cassette_picker_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = "Customize Cassette Design",
                            tint = WalkmanAccentGold
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSpatial,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_spatial_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SurroundSound,
                            contentDescription = "360° Spatial Audio",
                            tint = Color(0xFFF3F4F6)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(WalkmanBackdropTop, WalkmanBackdropBottom, WalkmanBackdropBottom)
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Signature Vintage Walkman Cassette Player
                WalkmanBody(
                    track = track,
                    cassetteStyle = cassetteStyle,
                    transportState = transportState,
                    swapController = swapController,
                    reelAngles = reelAngles,
                    progressFraction = progressFraction,
                    reducedMotion = reducedMotion,
                    onRewind = {
                        WalkmanSoundEngine.playIfEnabled(mechanicalSounds, MechanicalSoundType.BUTTON_PRESS)
                        seekImpulseDirection = -1
                        seekImpulseToken++
                        playbackManager.seekBackward(10_000L)
                    },
                    onPrevious = {
                        WalkmanSoundEngine.playIfEnabled(mechanicalSounds, MechanicalSoundType.BUTTON_PRESS)
                        if (queue.size > 1) {
                            swapController.triggerManualSwap(
                                currentDisplayedTrack = track,
                                swapDirection = CassetteSwapDirection.PREVIOUS,
                                expectedNextTrack = resolveNeighborTrack(-1)
                            ) {
                                playbackManager.skipToPrevious(forcePreviousTrack = true)
                            }
                        } else {
                            playbackManager.skipToPrevious(forcePreviousTrack = true)
                        }
                    },
                    onPlay = {
                        WalkmanSoundEngine.playIfEnabled(mechanicalSounds, MechanicalSoundType.LATCH_ENGAGE)
                        playbackManager.play()
                    },
                    onPause = {
                        WalkmanSoundEngine.playIfEnabled(mechanicalSounds, MechanicalSoundType.LATCH_ENGAGE)
                        playbackManager.pause()
                    },
                    onStop = {
                        WalkmanSoundEngine.playIfEnabled(mechanicalSounds, MechanicalSoundType.LATCH_ENGAGE)
                        playbackManager.stopPlaybackPreservingQueue()
                    },
                    onNext = {
                        WalkmanSoundEngine.playIfEnabled(mechanicalSounds, MechanicalSoundType.BUTTON_PRESS)
                        if (queue.size > 1) {
                            swapController.triggerManualSwap(
                                currentDisplayedTrack = track,
                                swapDirection = CassetteSwapDirection.NEXT,
                                expectedNextTrack = resolveNeighborTrack(+1)
                            ) {
                                playbackManager.skipToNext()
                            }
                        } else {
                            playbackManager.skipToNext()
                        }
                    },
                    onFastForward = {
                        WalkmanSoundEngine.playIfEnabled(mechanicalSounds, MechanicalSoundType.BUTTON_PRESS)
                        seekImpulseDirection = 1
                        seekImpulseToken++
                        playbackManager.seekForward(10_000L)
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Track Information, Progress Slider & Time Readout
                PlaybackProgress(
                    track = track,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek = { targetMs -> playbackManager.seekTo(targetMs) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Clean Utility Row: Shuffle, Queue, 15-Band EQ, Lyrics, Repeat
                WalkmanUtilityBar(
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onToggleShuffle = { playbackManager.toggleShuffle() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenEqualizer = onNavigateToEqualizer,
                    onOpenLyrics = { showLyricsSheet = true },
                    onCycleRepeat = { playbackManager.cycleRepeatMode() }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Cassette Appearance Customization Sheet
    if (showCassetteStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCassetteStyleSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CassetteStyleSheetContent(
                selectedStyle = cassetteStyle,
                onSelectStyle = { newStyle ->
                    playbackManager.setCassetteStyle(newStyle)
                    showCassetteStyleSheet = false
                }
            )
        }
    }

    // Lyrics Modal Bottom Sheet
    if (showLyricsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLyricsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LyricsSheetContent(
                track = track,
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
                currentTrack = track,
                isPlaying = isPlaying,
                onSelectTrack = { selected -> playbackManager.playTrack(selected, queue) },
                onRemoveTrack = { idx -> playbackManager.removeFromQueue(idx) },
                onMoveTrack = { from, to -> playbackManager.reorderQueue(from, to) },
                onMoveToNext = { idx -> playbackManager.moveQueueItemToPlayNext(idx) },
                onClearQueue = { playbackManager.clearQueue(keepCurrent = true) },
                onClose = { showQueueSheet = false }
            )
        }
    }
}
