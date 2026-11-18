package com.example.sangeet.ui.screens.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.repository.LyricsDisplayPreferencesSnapshot
import com.example.sangeet.data.repository.LyricsFontStyle
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.scanner.*
import com.example.sangeet.ui.components.ArtworkPathCache
import com.example.sangeet.ui.components.CassetteEmptyStateCard
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.components.MiniCassetteArtwork
import com.example.sangeet.ui.components.rememberIsAppResumed
import com.example.sangeet.ui.components.rememberVisibleCassetteReelAngle
import com.example.sangeet.ui.motion.LocalSangeetMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val GoldAccent = Color(0xFFE8B429)
private val GoldBright = Color(0xFFFFD666)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenLyricsScreen(
    playbackManager: PlaybackManager,
    onClose: () -> Unit,
    onOpenLyricsSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lyricsManager = remember { LyricsManager(context) }
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    val currentTrack by playbackManager.currentTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val flowPositionMs by playbackManager.currentPositionMs.collectAsState()
    val flowDurationMs by playbackManager.durationMs.collectAsState()
    val prefs by settingsRepo.lyricsPreferences.collectAsState(initial = LyricsDisplayPreferencesSnapshot())
    val sangeetMotion = LocalSangeetMotion.current
    val isAppResumed by rememberIsAppResumed()

    val track = currentTrack
    if (track == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val effectiveReducedMotion = prefs.reducedMotion || sangeetMotion.isReducedMotion

    // High-precision playback position synchronized to actual ExoPlayer clock while playing & visible
    var livePositionMs by remember(track.id) { mutableLongStateOf(playbackManager.getRealtimePositionMs()) }

    LaunchedEffect(flowPositionMs, track.id) {
        livePositionMs = flowPositionMs
    }

    LaunchedEffect(isPlaying, track.id, isAppResumed) {
        livePositionMs = playbackManager.getRealtimePositionMs()
        if (isPlaying && isAppResumed) {
            while (isActive) {
                livePositionMs = playbackManager.getRealtimePositionMs()
                delay(45L)
            }
        }
    }

    // Resolve lyrics for active track
    var resolvedLyrics by remember(track.id) { mutableStateOf(ResolvedLyrics()) }
    var timingOffsetMs by remember(track.id) { mutableLongStateOf(0L) }
    var isLoadingLyrics by remember(track.id) { mutableStateOf(true) }

    LaunchedEffect(track.id) {
        isLoadingLyrics = true
        val resolved = lyricsManager.resolveLyricsDetailed(track)
        resolvedLyrics = resolved
        timingOffsetMs = resolved.offsetMs
        isLoadingLyrics = false
    }

    val parsedDoc by remember(track.id, resolvedLyrics.rawContent) {
        mutableStateOf(lyricsManager.getOrParseDocument(track.id, resolvedLyrics.rawContent))
    }

    // Effective offset = per-track offset + [offset:] tag if per-track offset wasn't customized yet
    val effectiveOffsetMs = timingOffsetMs

    val currentLineIndex by remember(parsedDoc, effectiveOffsetMs) {
        derivedStateOf {
            LyricsSyncEngine.findActiveLineIndex(
                lines = parsedDoc.lines,
                playbackPositionMs = livePositionMs,
                offsetMs = effectiveOffsetMs
            )
        }
    }

    // UI overlays state
    var isImmersive by remember { mutableStateOf(false) }
    var isManualScrolling by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showTimingSheet by remember { mutableStateOf(false) }
    var showEditorSheet by remember { mutableStateOf(false) }

    // Search sheet state
    var searchTitle by remember(track.id) { mutableStateOf(track.title) }
    var searchArtist by remember(track.id) { mutableStateOf(track.artist) }
    var searchCandidates by remember(track.id) { mutableStateOf<List<LocalLyricsCandidate>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Pending overwrite confirmation
    var pendingSaveRawContent by remember { mutableStateOf<String?>(null) }
    var pendingSaveSourceType by remember { mutableStateOf(LyricsSourceType.MANUAL_LOCAL) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    // SAF Open Document (.lrc / .txt)
    val openLyricsDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val loaded = lyricsManager.readLyricsFromUri(uri)
                if (loaded.isNotBlank()) {
                    if (resolvedLyrics.hasLyrics) {
                        pendingSaveSourceType = LyricsSourceType.LOCAL_FILE
                        pendingSaveRawContent = loaded
                    } else {
                        lyricsManager.saveCustomLyrics(track.id, loaded, timingOffsetMs, isManual = true)
                        resolvedLyrics = ResolvedLyrics(
                            rawContent = loaded,
                            offsetMs = timingOffsetMs,
                            sourceType = LyricsSourceType.LOCAL_FILE,
                            sourceDetail = "Imported file",
                            isManual = true
                        )
                        showSearchSheet = false
                        Toast.makeText(context, "Offline lyrics imported", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Selected file is empty or unreadable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // SAF Export .lrc Document
    val exportLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val contentToWrite = pendingExportContent
        pendingExportContent = null
        if (uri != null && !contentToWrite.isNullOrBlank()) {
            coroutineScope.launch {
                val ok = lyricsManager.writeLyricsToUri(uri, contentToWrite)
                Toast.makeText(
                    context,
                    if (ok) "Exported .lrc file successfully" else "Failed to export .lrc file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun triggerLocalSearch() {
        isSearching = true
        coroutineScope.launch {
            searchCandidates = lyricsManager.searchLocalLyrics(searchTitle, searchArtist, track)
            isSearching = false
        }
    }

    // Blurred Album Artwork Background (API 26+ compatible downscaled StackBlur)
    val blurStep = (prefs.artworkBlurRadius / 2f).roundToInt() * 2
    val blurredBackdropBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = track.id,
        key2 = blurStep
    ) {
        value = loadBlurredArtworkBitmap(context, track, blurStep)
    }

    // List State & Manual Scroll Detection
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                isManualScrolling = true
            }
        }
    }

    // Reset manual scroll pause on track switch
    LaunchedEffect(track.id) {
        isManualScrolling = false
    }

    // Smooth auto-scroll centering active lyric line
    LaunchedEffect(currentLineIndex, isManualScrolling, prefs.autoScrollEnabled, parsedDoc.isSynchronized) {
        if (!isManualScrolling && prefs.autoScrollEnabled && parsedDoc.isSynchronized &&
            currentLineIndex in parsedDoc.lines.indices && !listState.isScrollInProgress
        ) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val centerOffset = if (viewportHeight > 0) -(viewportHeight * 0.30f).toInt() else -220
            if (effectiveReducedMotion) {
                listState.scrollToItem(currentLineIndex, centerOffset)
            } else {
                listState.animateScrollToItem(currentLineIndex, centerOffset)
            }
        }
    }

    val highlightColor = Color(prefs.highlightColorHex)
    val fontFamily = remember(prefs.fontStyle) {
        when (prefs.fontStyle) {
            LyricsFontStyle.SANS -> FontFamily.SansSerif
            LyricsFontStyle.SERIF -> FontFamily.Serif
            LyricsFontStyle.MONO -> FontFamily.Monospace
            LyricsFontStyle.DISPLAY -> FontFamily.Default
        }
    }

    val safeDuration = flowDurationMs.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1L)
    var isScrubbing by remember(track.id) { mutableStateOf(false) }
    var scrubValue by remember(track.id) { mutableFloatStateOf(0f) }
    val shownFraction = if (isScrubbing) scrubValue else (livePositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    val miniReelAngle by rememberVisibleCassetteReelAngle(
        isActivelyPlayingAndVisible = isPlaying && isAppResumed && !effectiveReducedMotion
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("fullscreen_lyrics_screen")
    ) {
        // 1. Blurred Album Artwork Backdrop + Dark Readability Gradient Overlay
        AnimatedContent(
            targetState = blurredBackdropBitmap,
            transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(450)) },
            label = "lyrics_backdrop_art"
        ) { artBitmap ->
            if (artBitmap != null) {
                Image(
                    bitmap = artBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    GoldAccent.copy(alpha = 0.22f),
                                    Color(0xFF0A0906),
                                    Color.Black
                                )
                            )
                        )
                )
            }
        }

        // Configurable Dark Readability Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = (prefs.backgroundDimming + 0.14f).coerceIn(0.45f, 0.96f)),
                            Color.Black.copy(alpha = prefs.backgroundDimming),
                            Color.Black.copy(alpha = (prefs.backgroundDimming + 0.18f).coerceIn(0.55f, 0.98f))
                        )
                    )
                )
        )

        // 2. Main Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Top Header Bar (hidden smoothly in Immersive Mode)
            AnimatedVisibility(
                visible = !isImmersive,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("fullscreen_lyrics_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Close Full-Screen Lyrics",
                                tint = Color(0xFFF9FAFB)
                            )
                        }

                        MiniCassetteArtwork(
                            track = track,
                            isPlaying = isPlaying && isAppResumed,
                            reelAngleDegrees = miniReelAngle,
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("fullscreen_lyrics_cassette_art")
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color(0xFFF9FAFB),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldBright.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { isImmersive = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("fullscreen_lyrics_immersive_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = "Immersive Mode",
                                tint = GoldBright
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Sync Status Badge + Tool Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CassetteHeaderBadge(text = parsedDoc.syncType.badgeLabel)
                            if (resolvedLyrics.hasLyrics) {
                                Text(
                                    text = buildString {
                                        append(resolvedLyrics.sourceType.label)
                                        if (effectiveOffsetMs != 0L) {
                                            append(" • ${if (effectiveOffsetMs > 0) "+" else ""}${effectiveOffsetMs}ms")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD1D5DB),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    triggerLocalSearch()
                                    showSearchSheet = true
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fullscreen_lyrics_search_btn")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search Local Lyrics", tint = GoldBright)
                            }
                            IconButton(
                                onClick = { showTimingSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fullscreen_lyrics_timing_btn")
                            ) {
                                Icon(Icons.Rounded.Tune, contentDescription = "Adjust Lyrics Timing", tint = GoldBright)
                            }
                            IconButton(
                                onClick = { showEditorSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fullscreen_lyrics_editor_btn")
                            ) {
                                Icon(Icons.Rounded.EditNote, contentDescription = "Edit or Sync Lyrics", tint = GoldBright)
                            }
                            IconButton(
                                onClick = onOpenLyricsSettings,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fullscreen_lyrics_settings_btn")
                            ) {
                                Icon(Icons.Rounded.Palette, contentDescription = "Lyrics Appearance Settings", tint = Color(0xFFE5E7EB))
                            }
                        }
                    }
                }
            }

            // Center Lyrics Stage (occupies maximum screen height)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (parsedDoc.lines.isNotEmpty()) {
                            isImmersive = !isImmersive
                        }
                    }
            ) {
                when {
                    isLoadingLyrics -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GoldAccent)
                        }
                    }

                    parsedDoc.lines.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CassetteEmptyStateCard(
                                icon = Icons.Rounded.Lyrics,
                                title = "No Synchronized Lyrics Found",
                                subtitle = "Search local .lrc files across device folders, import a lyrics file, or tap-to-sync your own.",
                                action = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Button(
                                            onClick = {
                                                triggerLocalSearch()
                                                showSearchSheet = true
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GoldAccent,
                                                contentColor = Color.Black
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("empty_lyrics_search_btn")
                                        ) {
                                            Icon(Icons.Rounded.Search, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Search Lyrics (Offline)", fontWeight = FontWeight.Bold)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    openLyricsDocLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                                            ) {
                                                Icon(Icons.Rounded.FileOpen, contentDescription = null, tint = GoldBright, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Import .LRC", color = GoldBright)
                                            }
                                            OutlinedButton(
                                                onClick = { showEditorSheet = true },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                                            ) {
                                                Icon(Icons.Rounded.Edit, contentDescription = null, tint = GoldBright, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Write / Sync", color = GoldBright)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("fullscreen_lyrics_list"),
                            contentPadding = PaddingValues(
                                start = 24.dp,
                                end = 24.dp,
                                top = if (isImmersive) 64.dp else 44.dp,
                                bottom = if (isImmersive) 84.dp else 110.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(prefs.lineSpacingDp.dp)
                        ) {
                            itemsIndexed(
                                items = parsedDoc.lines,
                                key = { idx, line -> "${idx}_${line.timestampMs}" }
                            ) { index, line ->
                                val isCurrent = parsedDoc.isSynchronized && index == currentLineIndex
                                val isPast = parsedDoc.isSynchronized && index < currentLineIndex

                                LyricLineItem(
                                    line = line,
                                    isSynchronizedDoc = parsedDoc.isSynchronized,
                                    isCurrent = isCurrent,
                                    isPast = isPast,
                                    prefs = prefs,
                                    highlightColor = highlightColor,
                                    fontFamily = fontFamily,
                                    reducedMotion = effectiveReducedMotion,
                                    wordFillProvider = if (isCurrent && prefs.wordByWordEnabled && line.hasWordSync) {
                                        { word ->
                                            LyricsSyncEngine.wordFillFraction(
                                                word = word,
                                                playbackPositionMs = livePositionMs,
                                                offsetMs = effectiveOffsetMs
                                            )
                                        }
                                    } else null,
                                    onTapLine = {
                                        if (parsedDoc.isSynchronized) {
                                            val seekTarget = LyricsSyncEngine.effectiveLineTimestamp(
                                                line.timestampMs,
                                                effectiveOffsetMs
                                            )
                                            playbackManager.seekTo(seekTarget)
                                            livePositionMs = seekTarget
                                            isManualScrolling = false
                                        } else {
                                            isImmersive = !isImmersive
                                        }
                                    },
                                    modifier = Modifier.testTag("lyric_line_$index")
                                )
                            }
                        }

                        // Floating "Follow Playback" pill when user manually scrolled
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isManualScrolling && parsedDoc.isSynchronized,
                            enter = fadeIn() + slideInVertically { it / 2 },
                            exit = fadeOut() + slideOutVertically { it / 2 },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            Button(
                                onClick = { isManualScrolling = false },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldAccent,
                                    contentColor = Color.Black
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("lyrics_follow_playback_btn")
                            ) {
                                Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Follow Playback", fontWeight = FontWeight.Black)
                            }
                        }

                        // Minimal floating Exit Immersive button
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isImmersive,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp)
                        ) {
                            Surface(
                                onClick = { isImmersive = false },
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fullscreen_lyrics_exit_immersive_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Immersive", tint = GoldBright, modifier = Modifier.size(18.dp))
                                    Text("Controls", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Minimal Playback Controls Bar (hidden smoothly in Immersive Mode)
            AnimatedVisibility(
                visible = !isImmersive,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color(0xFF0A0A0E).copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Slider(
                            value = shownFraction,
                            onValueChange = { frac ->
                                isScrubbing = true
                                scrubValue = frac.coerceIn(0f, 1f)
                            },
                            onValueChangeFinished = {
                                val target = (scrubValue * safeDuration).toLong().coerceIn(0L, safeDuration)
                                isScrubbing = false
                                playbackManager.seekTo(target)
                                livePositionMs = target
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = GoldBright,
                                activeTrackColor = GoldAccent,
                                inactiveTrackColor = Color(0xFF282A34)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fullscreen_lyrics_seek_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatWalkmanTime(if (isScrubbing) (scrubValue * safeDuration).toLong() else livePositionMs),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = GoldBright
                            )
                            Text(
                                text = formatWalkmanTime(safeDuration),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF9CA3AF)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { playbackManager.skipToPrevious(forcePreviousTrack = true) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fullscreen_lyrics_prev_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Previous Track",
                                    tint = Color(0xFFF3F4F6),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = { playbackManager.togglePlayPause() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = GoldAccent,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(10.dp, CircleShape)
                                    .testTag("fullscreen_lyrics_play_pause_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = { playbackManager.skipToNext() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fullscreen_lyrics_next_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = Color(0xFFF3F4F6),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Timing Offset Sheet
    if (showTimingSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTimingSheet = false },
            containerColor = Color(0xFF0E1015)
        ) {
            LyricsTimingAdjustmentSheet(
                track = track,
                currentOffsetMs = timingOffsetMs,
                onOffsetChange = { newOffset ->
                    timingOffsetMs = newOffset
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, newOffset) }
                },
                onResetOffset = {
                    timingOffsetMs = 0L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, 0L) }
                },
                onClose = { showTimingSheet = false }
            )
        }
    }

    // Offline Search Sheet
    if (showSearchSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            containerColor = Color(0xFF0E1015)
        ) {
            LyricsOfflineSearchSheet(
                track = track,
                searchTitle = searchTitle,
                searchArtist = searchArtist,
                isSearching = isSearching,
                candidates = searchCandidates,
                onSearchQueryChange = { t, a ->
                    searchTitle = t
                    searchArtist = a
                },
                onRunLocalSearch = { triggerLocalSearch() },
                onPickDocument = {
                    openLyricsDocLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                },
                onApplyCandidate = { candidate ->
                    if (resolvedLyrics.hasLyrics) {
                        pendingSaveSourceType = candidate.sourceType
                        pendingSaveRawContent = candidate.rawContent
                    } else {
                        coroutineScope.launch {
                            lyricsManager.saveCustomLyrics(track.id, candidate.rawContent, timingOffsetMs, isManual = true)
                            resolvedLyrics = ResolvedLyrics(
                                rawContent = candidate.rawContent,
                                offsetMs = timingOffsetMs,
                                sourceType = candidate.sourceType,
                                sourceDetail = candidate.title,
                                isManual = true
                            )
                            showSearchSheet = false
                            Toast.makeText(context, "Lyrics applied cleanly", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onClose = { showSearchSheet = false }
            )
        }
    }

    // Editor & Manual Sync Studio Sheet
    if (showEditorSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditorSheet = false },
            containerColor = Color(0xFF0E1015)
        ) {
            LyricsEditorAndSyncSheet(
                track = track,
                initialRawLyrics = resolvedLyrics.rawContent,
                currentOffsetMs = timingOffsetMs,
                isPlaying = isPlaying,
                getRealtimePositionMs = { playbackManager.getRealtimePositionMs() },
                onTogglePlayPause = { playbackManager.togglePlayPause() },
                onSeekRelative = { delta ->
                    val target = (playbackManager.getRealtimePositionMs() + delta).coerceIn(0L, safeDuration)
                    playbackManager.seekTo(target)
                    livePositionMs = target
                },
                onSaveLyrics = { updatedLrc ->
                    if (resolvedLyrics.hasLyrics) {
                        pendingSaveSourceType = LyricsSourceType.MANUAL_LOCAL
                        pendingSaveRawContent = updatedLrc
                    } else {
                        coroutineScope.launch {
                            lyricsManager.saveCustomLyrics(track.id, updatedLrc, timingOffsetMs, isManual = true)
                            resolvedLyrics = ResolvedLyrics(
                                rawContent = updatedLrc,
                                offsetMs = timingOffsetMs,
                                sourceType = LyricsSourceType.MANUAL_LOCAL,
                                sourceDetail = "Custom lyrics",
                                isManual = true
                            )
                            showEditorSheet = false
                            Toast.makeText(context, "Lyrics saved locally", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onExportLrc = { lrcContent ->
                    pendingExportContent = lrcContent
                    val safeName = track.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").ifBlank { "sangeet_lyrics" }
                    exportLyricsLauncher.launch("$safeName.lrc")
                },
                onResetToOriginalSource = if (resolvedLyrics.isManual) {
                    {
                        coroutineScope.launch {
                            lyricsManager.clearCustomLyrics(track.id)
                            val reResolved = lyricsManager.resolveLyricsDetailed(track)
                            resolvedLyrics = reResolved
                            timingOffsetMs = reResolved.offsetMs
                            showEditorSheet = false
                            Toast.makeText(context, "Restored original track lyrics", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else null,
                onClose = { showEditorSheet = false }
            )
        }
    }

    // Overwrite Confirmation Dialog
    val pendingText = pendingSaveRawContent
    if (pendingText != null) {
        AlertDialog(
            onDismissRequest = { pendingSaveRawContent = null },
            containerColor = Color(0xFF14161D),
            title = { Text("Replace Current Lyrics?", color = GoldBright, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will replace the lyrics currently displayed for \"${track.title}\" in SANGEET's local library. Your original audio file will not be modified.",
                    color = Color(0xFFD1D5DB)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            lyricsManager.saveCustomLyrics(track.id, pendingText, timingOffsetMs, isManual = true)
                            resolvedLyrics = ResolvedLyrics(
                                rawContent = pendingText,
                                offsetMs = timingOffsetMs,
                                sourceType = pendingSaveSourceType,
                                sourceDetail = "Saved locally",
                                isManual = true
                            )
                            pendingSaveRawContent = null
                            showSearchSheet = false
                            showEditorSheet = false
                            Toast.makeText(context, "Lyrics updated locally", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                    modifier = Modifier.testTag("lyrics_confirm_overwrite_btn")
                ) {
                    Text("Replace & Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSaveRawContent = null }) {
                    Text("Cancel", color = Color(0xFFD1D5DB))
                }
            }
        )
    }
}

@Composable
private fun LyricLineItem(
    line: EnhancedLyricsLine,
    isSynchronizedDoc: Boolean,
    isCurrent: Boolean,
    isPast: Boolean,
    prefs: LyricsDisplayPreferencesSnapshot,
    highlightColor: Color,
    fontFamily: FontFamily,
    reducedMotion: Boolean,
    wordFillProvider: ((LyricsWord) -> Float)?,
    onTapLine: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetAlpha = when {
        !isSynchronizedDoc -> 0.88f
        isCurrent -> 1.0f
        isPast -> (prefs.inactiveOpacity * 0.90f).coerceIn(0.15f, 0.85f)
        else -> prefs.inactiveOpacity
    }
    val targetScale = if (isCurrent && !reducedMotion) prefs.currentLineScale else 1.0f
    val targetColor = if (isCurrent) highlightColor else Color(0xFFF3F4F6).copy(alpha = targetAlpha)

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (reducedMotion) tween(0) else tween(220),
        label = "lyric_line_scale"
    )
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = if (reducedMotion) tween(0) else tween(200),
        label = "lyric_line_color"
    )

    val displayStyle = MaterialTheme.typography.titleLarge.copy(
        fontFamily = fontFamily,
        fontSize = prefs.fontSizeSp.sp,
        lineHeight = (prefs.fontSizeSp * 1.38f).sp,
        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
        shadow = if (isCurrent) {
            Shadow(color = highlightColor.copy(alpha = 0.45f), blurRadius = 18f)
        } else null
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTapLine)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isCurrent && wordFillProvider != null && line.words.isNotEmpty()) {
            val wordAnnotated = buildAnnotatedString {
                for (word in line.words) {
                    val fill = wordFillProvider(word)
                    val wordColor = when {
                        fill >= 0.65f -> highlightColor
                        fill > 0.0f -> highlightColor.copy(alpha = 0.72f + 0.28f * fill)
                        else -> highlightColor.copy(alpha = 0.44f)
                    }
                    withStyle(
                        SpanStyle(
                            color = wordColor,
                            fontWeight = if (fill > 0.25f) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    ) {
                        append(word.text)
                    }
                }
            }
            Text(
                text = wordAnnotated,
                style = displayStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
            )
        } else {
            Text(
                text = line.text,
                style = displayStyle,
                color = animatedColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
            )
        }
    }
}

/**
 * Loads artwork for [track] and applies a fast, API 26-compatible StackBlur on a downscaled copy.
 */
private suspend fun loadBlurredArtworkBitmap(
    context: Context,
    track: AudioTrack,
    blurRadius: Int
): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val artPath = track.artworkUri.takeIf { it.isNotBlank() }
            ?: ArtworkPathCache.get(track.id)
            ?: ArtworkManager(context).resolveArtwork(track)
        if (artPath.isNullOrBlank()) return@withContext null

        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val decoded: Bitmap? = if (artPath.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(artPath))?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } else {
            val file = File(artPath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath, opts) else null
        }

        if (decoded == null) return@withContext null
        val small = decoded.scale(140, 140, filter = true)
        if (small != decoded) decoded.recycle()
        val result = if (blurRadius > 0) fastBoxBlurBitmap(small, blurRadius.coerceIn(1, 30)) else small
        result.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

private fun fastBoxBlurBitmap(source: Bitmap, radius: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= 2 || h <= 2) return source
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    val temp = IntArray(w * h)

    // Two fast separable horizontal + vertical box passes produce smooth Gaussian-like blur on API 26+
    val r = radius.coerceIn(1, 24)
    val window = r * 2 + 1
    repeat(2) {
        for (y in 0 until h) {
            val rowOff = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -r..r) {
                val c = pixels[rowOff + k.coerceIn(0, w - 1)]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (x in 0 until w) {
                temp[rowOff + x] = (0xFF shl 24) or ((rSum / window) shl 16) or ((gSum / window) shl 8) or (bSum / window)
                val outC = pixels[rowOff + (x - r).coerceIn(0, w - 1)]
                val inC = pixels[rowOff + (x + r + 1).coerceIn(0, w - 1)]
                rSum += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
                gSum += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
                bSum += (inC and 0xFF) - (outC and 0xFF)
            }
        }
        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -r..r) {
                val c = temp[k.coerceIn(0, h - 1) * w + x]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (y in 0 until h) {
                pixels[y * w + x] = (0xFF shl 24) or ((rSum / window) shl 16) or ((gSum / window) shl 8) or (bSum / window)
                val outC = temp[(y - r).coerceIn(0, h - 1) * w + x]
                val inC = temp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rSum += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
                gSum += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
                bSum += (inC and 0xFF) - (outC and 0xFF)
            }
        }
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}
