package com.example.sangeet.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.ArtworkAnimationIntensity
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.ArtworkAnimationStyle
import com.example.sangeet.ui.components.ArtworkMotionPlanner
import com.example.sangeet.ui.components.CassetteArtwork
import com.example.sangeet.ui.components.CassettePrintedArtImage
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import com.example.sangeet.ui.components.CassetteTopBar
import com.example.sangeet.ui.components.SpotlightArtworkTransitionHost
import com.example.sangeet.ui.components.SpotlightTransitionDirection
import com.example.sangeet.ui.components.SpotlightTransitionSpeed
import com.example.sangeet.ui.components.SpotlightTransitionStyle
import com.example.sangeet.ui.components.rememberEffectiveArtworkMotion
import com.example.sangeet.ui.components.rememberIsAppResumed
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkAnimationSettingsScreen(
    playbackManager: PlaybackManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    val prefs by settingsRepo.artworkAnimationPreferences.collectAsState(
        initial = ArtworkAnimationPreferencesSnapshot()
    )
    val currentTrack by playbackManager.currentTrack.collectAsState()
    val queueTracks by playbackManager.queue.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    // Pre-selection preview state for Featured Album Animation
    var previewStyleOverride by remember { mutableStateOf<SpotlightTransitionStyle?>(null) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var previewNavDirection by remember { mutableIntStateOf(1) }

    val previewTracks = remember(currentTrack, queueTracks) {
        val list = LinkedHashMap<Long, AudioTrack>()
        currentTrack?.let { list[it.id] = it }
        queueTracks.forEach { if (list.size < 4) list.putIfAbsent(it.id, it) }
        val defaults = listOf(
            AudioTrack(id = -101L, title = "Midnight Groove", artist = "Aura & Spatial Wave", album = "SANGEET Studio Vol. 1"),
            AudioTrack(id = -102L, title = "Golden Horizon", artist = "Binaural Strings", album = "Acoustic Unplugged"),
            AudioTrack(id = -103L, title = "Velvet Resonance", artist = "HyperBass Collective", album = "AMOLED Sessions")
        )
        defaults.forEach { if (list.size < 3) list.putIfAbsent(it.id, it) }
        list.values.toList()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("artwork_animation_settings_screen"),
        topBar = {
            CassetteTopBar(
                title = "Artwork Animations",
                subtitle = "Featured Spotlight transitions, Ken Burns, parallax & cover FX",
                badgeText = "FX",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.testTag("art_anim_reset_btn")
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "Reset Artwork Animation Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Interactive Featured Album Transition Preview
            item {
                val activePreviewStyle = previewStyleOverride ?: prefs.spotlightTransitionStyle
                FeaturedTransitionLivePreviewCard(
                    prefs = prefs,
                    previewStyle = activePreviewStyle,
                    isTemporaryPreview = previewStyleOverride != null && previewStyleOverride != prefs.spotlightTransitionStyle,
                    previewTracks = previewTracks,
                    previewIndex = previewIndex,
                    navigationDirection = previewNavDirection,
                    onPrevious = {
                        previewNavDirection = -1
                        previewIndex = (previewIndex - 1 + previewTracks.size) % previewTracks.size
                    },
                    onNext = {
                        previewNavDirection = 1
                        previewIndex = (previewIndex + 1) % previewTracks.size
                    }
                )
            }

            // 2. Featured Album Animation Selector (Slide, Fade, Page Turn, None) with pre-selection Preview
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CassetteSectionHeader(title = "Featured Album Animation")
                        Text(
                            text = "Select how Featured Spotlight artwork transitions between albums, or tap Preview first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SpotlightTransitionStyle.entries.forEach { transitionStyle ->
                            val selected = prefs.spotlightTransitionStyle == transitionStyle
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    Color.Transparent
                                },
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        previewStyleOverride = null
                                        previewNavDirection = 1
                                        previewIndex = (previewIndex + 1) % previewTracks.size
                                        coroutineScope.launch { settingsRepo.setSpotlightTransitionStyle(transitionStyle) }
                                    }
                                    .testTag("featured_anim_style_${transitionStyle.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            previewStyleOverride = null
                                            previewNavDirection = 1
                                            previewIndex = (previewIndex + 1) % previewTracks.size
                                            coroutineScope.launch { settingsRepo.setSpotlightTransitionStyle(transitionStyle) }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = transitionStyle.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = transitionStyle.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    TextButton(
                                        onClick = {
                                            previewStyleOverride = transitionStyle
                                            previewNavDirection = 1
                                            previewIndex = (previewIndex + 1) % previewTracks.size
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("featured_anim_preview_${transitionStyle.name.lowercase()}")
                                    ) {
                                        Text("Preview")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Featured Animation Speed & Direction
            item {
                val transitionEnabled = (previewStyleOverride ?: prefs.spotlightTransitionStyle) != SpotlightTransitionStyle.NONE
                CassetteSectionCard(
                    modifier = Modifier.alpha(if (transitionEnabled || prefs.spotlightTransitionStyle != SpotlightTransitionStyle.NONE) 1f else 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CassetteSectionHeader(title = "Animation Speed")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpotlightTransitionSpeed.entries.forEach { speed ->
                                FilterChip(
                                    selected = prefs.spotlightTransitionSpeed == speed,
                                    onClick = {
                                        previewNavDirection = 1
                                        previewIndex = (previewIndex + 1) % previewTracks.size
                                        coroutineScope.launch { settingsRepo.setSpotlightTransitionSpeed(speed) }
                                    },
                                    label = { Text(speed.label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("featured_anim_speed_${speed.name.lowercase()}")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        Text(
                            text = "ANIMATION DIRECTION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SpotlightTransitionDirection.entries.forEach { dir ->
                                val selected = prefs.spotlightTransitionDirection == dir
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        Color.Transparent
                                    },
                                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            previewNavDirection = 1
                                            previewIndex = (previewIndex + 1) % previewTracks.size
                                            coroutineScope.launch { settingsRepo.setSpotlightTransitionDirection(dir) }
                                        }
                                        .testTag("featured_anim_dir_${dir.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = {
                                                previewNavDirection = 1
                                                previewIndex = (previewIndex + 1) % previewTracks.size
                                                coroutineScope.launch { settingsRepo.setSpotlightTransitionDirection(dir) }
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = dir.label,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = dir.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Ambient & Cover FX Style Selector
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CassetteSectionHeader(title = "Home Cover Motion Style")

                        ArtworkAnimationStyle.entries.forEach { style ->
                            val selected = prefs.style == style
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    Color.Transparent
                                },
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        coroutineScope.launch { settingsRepo.setArtworkAnimationStyle(style) }
                                    }
                                    .testTag("art_anim_style_${style.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            coroutineScope.launch { settingsRepo.setArtworkAnimationStyle(style) }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = style.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = style.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Animation Intensity Control
            item {
                val styleEnabled = prefs.style != ArtworkAnimationStyle.NONE
                CassetteSectionCard(
                    modifier = Modifier.alpha(if (styleEnabled) 1f else 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CassetteSectionHeader(title = "Cover Motion Intensity")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArtworkAnimationIntensity.entries.forEach { level ->
                                FilterChip(
                                    selected = prefs.intensity == level,
                                    enabled = styleEnabled,
                                    onClick = {
                                        coroutineScope.launch { settingsRepo.setArtworkAnimationIntensity(level) }
                                    },
                                    label = { Text(level.label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("art_anim_intensity_${level.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // 6. Individual Effects Toggles
            item {
                val styleEnabled = prefs.style != ArtworkAnimationStyle.NONE
                CassetteSectionCard(
                    modifier = Modifier.alpha(if (styleEnabled) 1f else 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CassetteSectionHeader(title = "Individual Animation Effects")

                        AnimationToggleRow(
                            title = "Slow Zoom & Ken Burns Pan",
                            subtitle = "Gentle cinematic camera drift on Featured Spotlight artwork",
                            icon = Icons.Rounded.ZoomIn,
                            checked = prefs.enableKenBurns,
                            enabled = styleEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setArtworkKenBurnsEnabled(it) }
                            },
                            testTag = "art_anim_ken_burns_switch"
                        )

                        AnimationToggleRow(
                            title = "Scroll Parallax & Depth Layer",
                            subtitle = "Subtle background artwork depth and vertical scroll parallax",
                            icon = Icons.Rounded.Layers,
                            checked = prefs.enableParallax,
                            enabled = styleEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setArtworkParallaxEnabled(it) }
                            },
                            testTag = "art_anim_parallax_switch"
                        )

                        AnimationToggleRow(
                            title = "Animated Ambient Lighting",
                            subtitle = "Soft glowing gradient sweep on Spotlight artwork (Smooth/Cinematic)",
                            icon = Icons.Rounded.LightMode,
                            checked = prefs.enableAmbientLighting,
                            enabled = styleEnabled && prefs.style != ArtworkAnimationStyle.SUBTLE,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setArtworkAmbientLightingEnabled(it) }
                            },
                            testTag = "art_anim_lighting_switch"
                        )

                        AnimationToggleRow(
                            title = "Cover Breathing & Tap Scale",
                            subtitle = "Subtle floating rhythm and spring tap feedback on Quick Picks & Artists",
                            icon = Icons.Rounded.TouchApp,
                            checked = prefs.enableCardBreathing,
                            enabled = styleEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setArtworkCardBreathingEnabled(it) }
                            },
                            testTag = "art_anim_breathing_switch"
                        )

                        AnimationToggleRow(
                            title = "Track Change & Mini-Player Transitions",
                            subtitle = "Smooth scale and cross-fade when switching playing songs",
                            icon = Icons.Rounded.SwapHoriz,
                            checked = prefs.enableTrackTransitions,
                            enabled = styleEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setArtworkTrackTransitionsEnabled(it) }
                            },
                            testTag = "art_anim_transitions_switch"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        AnimationToggleRow(
                            title = "Respect System Reduced Motion",
                            subtitle = "Automatically disable motion if Android accessibility animations are off",
                            icon = Icons.Rounded.AccessibilityNew,
                            checked = prefs.respectReducedMotion,
                            enabled = true,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setArtworkRespectReducedMotion(it) }
                            },
                            testTag = "art_anim_reduced_motion_switch"
                        )
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Artwork Animations?") },
            text = { Text("Restores Slide Spotlight transition, Smooth cover style, Medium intensity, and default SANGEET Home effects.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            previewStyleOverride = null
                            settingsRepo.resetArtworkAnimationSettingsToDefault()
                            showResetDialog = false
                            Toast.makeText(context, "Animation defaults restored", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("art_anim_reset_confirm_btn")
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FeaturedTransitionLivePreviewCard(
    prefs: ArtworkAnimationPreferencesSnapshot,
    previewStyle: SpotlightTransitionStyle,
    isTemporaryPreview: Boolean,
    previewTracks: List<AudioTrack>,
    previewIndex: Int,
    navigationDirection: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val isAppResumed by rememberIsAppResumed()
    val motion = rememberEffectiveArtworkMotion(prefs.copy(spotlightTransitionStyle = previewStyle))
    val primaryColor = MaterialTheme.colorScheme.primary
    val safeIndex = previewIndex.coerceIn(0, previewTracks.lastIndex.coerceAtLeast(0))
    val activeTrack = previewTracks.getOrNull(safeIndex)
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 36.dp.toPx() }
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrev by rememberUpdatedState(onPrevious)

    val directionSign = ArtworkMotionPlanner.resolveSpotlightDirectionSign(
        direction = prefs.spotlightTransitionDirection,
        navigationDelta = navigationDirection
    )

    val previewTintPalettes = listOf(
        listOf(Color(0xFF3E2D0B), Color(0xFF1A1405)),
        listOf(Color(0xFF0E303D), Color(0xFF0B131E)),
        listOf(Color(0xFF38162B), Color(0xFF140A15)),
        listOf(Color(0xFF153429), Color(0xFF0A1612))
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("art_anim_live_preview"),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .pointerInput(previewTracks.size) {
                        var totalDrag = 0f
                        var triggered = false
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDrag = 0f
                                triggered = false
                            },
                            onDragEnd = {
                                if (!triggered) {
                                    when {
                                        totalDrag <= -swipeThresholdPx -> currentOnNext()
                                        totalDrag >= swipeThresholdPx -> currentOnPrev()
                                    }
                                }
                                totalDrag = 0f
                                triggered = false
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                                if (!triggered && abs(totalDrag) >= swipeThresholdPx * 1.4f) {
                                    triggered = true
                                    if (totalDrag < 0f) currentOnNext() else currentOnPrev()
                                }
                            }
                        )
                    }
            ) {
                SpotlightArtworkTransitionHost(
                    track = activeTrack,
                    transitionStyle = motion.spotlightTransitionStyle,
                    durationMs = motion.spotlightTransitionDurationMs,
                    directionSign = directionSign,
                    isAppResumed = isAppResumed,
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    modifier = Modifier.fillMaxSize(),
                    artworkLayer = { track ->
                        val paletteIdx = abs((track?.id ?: 0L).toInt()) % previewTintPalettes.size
                        val tintColors = previewTintPalettes[paletteIdx]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(tintColors))
                        ) {
                            CassettePrintedArtImage(
                                track = track,
                                crossfadeDurationMs = 0,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = 0.28f }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.56f),
                                                Color.Black.copy(alpha = 0.90f)
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 18.dp, end = 18.dp, top = 34.dp, bottom = 52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CassetteArtwork(
                                    track = track,
                                    isPlaying = false,
                                    showMetadataOnLabel = true,
                                    compact = false,
                                    crossfadeDurationMs = 0,
                                    modifier = Modifier
                                        .fillMaxHeight(0.94f)
                                        .aspectRatio(1.56f)
                                )
                            }
                        }
                    },
                    overlayChrome = { shownTrack, metaAlpha ->
                        val display = shownTrack ?: activeTrack
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = primaryColor
                                ) {
                                    Text(
                                        text = if (isTemporaryPreview) {
                                            "TRYING • ${previewStyle.label.uppercase()}"
                                        } else {
                                            "PREVIEW • ${previewStyle.label.uppercase()} (${prefs.spotlightTransitionSpeed.label.uppercase()})"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    previewTracks.forEachIndexed { idx, _ ->
                                        Box(
                                            modifier = Modifier
                                                .height(4.dp)
                                                .width(if (idx == safeIndex) 14.dp else 4.dp)
                                                .clip(CircleShape)
                                                .background(if (idx == safeIndex) primaryColor else Color.White.copy(alpha = 0.4f))
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.graphicsLayer { alpha = metaAlpha }) {
                                Text(
                                    text = display?.title ?: "Featured Spotlight Preview",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${display?.artist ?: "SANGEET"} • ${display?.album ?: "Preview"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onPrevious,
                    modifier = Modifier.testTag("featured_preview_prev_btn")
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Prev Album")
                }
                Text(
                    text = "Swipe or tap to test",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onNext,
                    modifier = Modifier.testTag("featured_preview_next_btn")
                ) {
                    Text("Next Album")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AnimationToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
