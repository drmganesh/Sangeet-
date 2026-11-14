package com.example.sangeet.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Shared section-level breathing phase (single InfiniteTransition per visible carousel)
 * that pauses completely when scrolled off-screen or when the app is backgrounded.
 */
@Composable
fun rememberCarouselBreathingPhase(
    isSectionVisible: Boolean,
    isAppResumed: Boolean,
    isBreathingEnabled: Boolean
): State<Float> {
    val shouldRun = isSectionVisible && isAppResumed && isBreathingEnabled
    return if (shouldRun) {
        val infiniteTransition = rememberInfiniteTransition(label = "carousel_breathe")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "breathingPhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
}

/**
 * Reusable Featured Spotlight transition host implementing Slide, Crossfade Fade,
 * realistic 3D Page Turn (rotationY, cameraDistance, spine hinge, cast & fold shadows),
 * and instant None transitions with synchronized artwork and metadata.
 */
@Composable
fun SpotlightArtworkTransitionHost(
    track: AudioTrack?,
    transitionStyle: SpotlightTransitionStyle,
    durationMs: Int,
    directionSign: Int,
    isAppResumed: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    modifier: Modifier = Modifier,
    artworkLayer: @Composable (AudioTrack?) -> Unit,
    overlayChrome: @Composable (displayedTrack: AudioTrack?, metadataAlpha: Float) -> Unit
) {
    val effectiveDuration = if (transitionStyle == SpotlightTransitionStyle.NONE || !isAppResumed) {
        0
    } else {
        durationMs.coerceAtLeast(0)
    }

    if (transitionStyle == SpotlightTransitionStyle.PAGE_TURN && effectiveDuration > 0) {
        SpotlightPageTurnHost(
            track = track,
            durationMs = effectiveDuration,
            directionSign = directionSign,
            shape = shape,
            modifier = modifier,
            artworkLayer = artworkLayer,
            overlayChrome = overlayChrome
        )
        return
    }

    Box(modifier = modifier) {
        when {
            effectiveDuration <= 0 || transitionStyle == SpotlightTransitionStyle.NONE -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    artworkLayer(track)
                }
                overlayChrome(track, 1f)
            }

            transitionStyle == SpotlightTransitionStyle.FADE -> {
                Crossfade(
                    targetState = track,
                    animationSpec = tween(durationMillis = effectiveDuration, easing = FastOutSlowInEasing),
                    label = "spotlight_fade_artwork",
                    modifier = Modifier.fillMaxSize()
                ) { target ->
                    artworkLayer(target)
                }
                Crossfade(
                    targetState = track,
                    animationSpec = tween(durationMillis = effectiveDuration, easing = FastOutSlowInEasing),
                    label = "spotlight_fade_meta",
                    modifier = Modifier.fillMaxSize()
                ) { target ->
                    overlayChrome(target, 1f)
                }
            }

            else -> {
                // SLIDE (default) — synchronized horizontal Slide + smooth easing via AnimatedContent
                val currentSign by rememberUpdatedState(directionSign)
                AnimatedContent(
                    targetState = track,
                    contentKey = { it?.id ?: -1L },
                    transitionSpec = {
                        val moveForwardRtl = currentSign >= 0
                        (slideInHorizontally(
                            animationSpec = tween(effectiveDuration, easing = FastOutSlowInEasing)
                        ) { fullWidth -> if (moveForwardRtl) fullWidth else -fullWidth } +
                            fadeIn(
                                animationSpec = tween((effectiveDuration * 0.85f).toInt(), easing = FastOutSlowInEasing)
                            )
                        ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(effectiveDuration, easing = FastOutSlowInEasing)
                            ) { fullWidth -> if (moveForwardRtl) -fullWidth else fullWidth } +
                                fadeOut(
                                    animationSpec = tween((effectiveDuration * 0.75f).toInt(), easing = FastOutSlowInEasing)
                                )
                        ).using(SizeTransform(clip = true))
                    },
                    label = "spotlight_slide_transition",
                    modifier = Modifier.fillMaxSize()
                ) { target ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        artworkLayer(target)
                        overlayChrome(target, 1f)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightPageTurnHost(
    track: AudioTrack?,
    durationMs: Int,
    directionSign: Int,
    shape: Shape,
    modifier: Modifier = Modifier,
    artworkLayer: @Composable (AudioTrack?) -> Unit,
    overlayChrome: @Composable (displayedTrack: AudioTrack?, metadataAlpha: Float) -> Unit
) {
    val density = LocalDensity.current.density
    var settledTrack by remember { mutableStateOf(track) }
    var outgoingTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var activeSign by remember { mutableIntStateOf(directionSign) }
    val turnProgress = remember { Animatable(1f) }
    val latestDirectionSign by rememberUpdatedState(directionSign)
    val latestDurationMs by rememberUpdatedState(durationMs)

    LaunchedEffect(track?.id) {
        if (track?.id == settledTrack?.id) {
            settledTrack = track
            return@LaunchedEffect
        }
        val prev = settledTrack
        settledTrack = track
        if (prev == null || latestDurationMs <= 0) {
            outgoingTrack = null
            turnProgress.snapTo(1f)
            return@LaunchedEffect
        }
        outgoingTrack = prev
        activeSign = latestDirectionSign
        turnProgress.snapTo(0f)
        turnProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = latestDurationMs,
                easing = CubicBezierEasing(0.24f, 0.62f, 0.34f, 1f)
            )
        )
        outgoingTrack = null
    }

    val p = turnProgress.value.coerceIn(0f, 1f)
    val isTurning = outgoingTrack != null && p < 0.999f
    val turnRtl = activeSign >= 0

    Box(modifier = modifier.clip(shape)) {
        // 1. Underneath incoming album cover (revealed as the top cover turns open)
        val underneathScale = if (isTurning) 0.955f + 0.045f * p else 1f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = underneathScale
                    scaleY = underneathScale
                }
        ) {
            artworkLayer(settledTrack)

            // Realistic cast shadow from the turning cover page onto the revealed album underneath
            if (isTurning) {
                val shadowStrength = (0.64f * (1f - p)).coerceIn(0f, 0.64f)
                val shadowBrush = if (turnRtl) {
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = shadowStrength * 0.95f),
                        0.45f to Color.Black.copy(alpha = shadowStrength * 0.55f),
                        1.0f to Color.Transparent
                    )
                } else {
                    Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = shadowStrength * 0.55f),
                        1.0f to Color.Black.copy(alpha = shadowStrength * 0.95f)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(shadowBrush)
                )
            }
        }

        // 2. Top turning physical album cover page with 3D rotationY, perspective, and curl shading
        if (isTurning) {
            val angle = if (turnRtl) -108f * p else 108f * p
            val absAngle = abs(angle)
            val pageAlpha = if (absAngle <= 82f) {
                1f
            } else {
                (1f - ((absAngle - 82f) / 22f)).coerceIn(0f, 1f)
            }
            val spineOrigin = if (turnRtl) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
            val elevationPx = with(LocalDensity.current) { (18f * (1f - p)).dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = angle
                        cameraDistance = 14f * density
                        transformOrigin = spineOrigin
                        this.shape = shape
                        clip = true
                        shadowElevation = elevationPx
                        alpha = pageAlpha
                    }
            ) {
                artworkLayer(outgoingTrack)

                // Realistic paper/gatefold curl shading & spine highlight as the page bends
                val shadeStrength = (sin(p * PI).toFloat() * 0.42f).coerceIn(0f, 0.42f)
                val curlBrush = if (turnRtl) {
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = shadeStrength * 0.45f),
                        0.14f to Color.White.copy(alpha = shadeStrength * 0.22f),
                        0.68f to Color.Black.copy(alpha = shadeStrength * 0.25f),
                        1.0f to Color.Black.copy(alpha = shadeStrength * 0.9f)
                    )
                } else {
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = shadeStrength * 0.9f),
                        0.32f to Color.Black.copy(alpha = shadeStrength * 0.25f),
                        0.86f to Color.White.copy(alpha = shadeStrength * 0.22f),
                        1.0f to Color.Black.copy(alpha = shadeStrength * 0.45f)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(curlBrush)
                )
            }
        }

        // 3. Synchronized metadata overlay (cross-transitions neatly as the new cover reveals)
        val shownMetaTrack = if (isTurning && p < 0.38f) outgoingTrack else settledTrack
        val metaAlpha = if (!isTurning) {
            1f
        } else if (p < 0.38f) {
            (1f - (p / 0.38f)).coerceIn(0f, 1f)
        } else {
            ((p - 0.38f) / 0.42f).coerceIn(0f, 1f)
        }
        overlayChrome(shownMetaTrack, metaAlpha)
    }
}

@Composable
fun AnimatedFeaturedSpotlightCard(
    featuredTrack: AudioTrack,
    motion: EffectiveArtworkMotion,
    isSpotlightVisible: Boolean,
    isAppResumed: Boolean,
    listState: LazyListState,
    itemKey: Any,
    onPlayFeatured: () -> Unit,
    modifier: Modifier = Modifier,
    navigationDirection: Int = 1,
    isFeaturedPlaying: Boolean = false,
    featuredCount: Int = 1,
    featuredIndex: Int = 0,
    onSwipeNext: () -> Unit = {},
    onSwipePrevious: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isLaunching by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 42.dp.toPx() }
    val currentOnSwipeNext by rememberUpdatedState(onSwipeNext)
    val currentOnSwipePrevious by rememberUpdatedState(onSwipePrevious)
    val currentFeaturedCount by rememberUpdatedState(featuredCount)

    val cardScale by animateFloatAsState(
        targetValue = when {
            !motion.isEnabled -> 1f
            isPressed -> motion.tapPressScale
            isLaunching -> 1.015f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "spotlightCardScale"
    )

    // Scroll parallax offset computed only from visible layout info without extra recompositions
    val scrollParallaxNormalized by remember(listState, itemKey) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
            if (info == null || info.size <= 0) {
                0f
            } else {
                (-info.offset.toFloat() / info.size.toFloat()).coerceIn(-1f, 1f)
            }
        }
    }

    val runContinuous = isAppResumed && isSpotlightVisible && motion.isEnabled &&
        (motion.kenBurnsActive || motion.ambientLightingActive)

    val kenBurnsZoom: Float
    val kenBurnsPanX: Float
    val kenBurnsPanY: Float
    val lightSweep: Float

    if (runContinuous) {
        val infiniteTransition = rememberInfiniteTransition(label = "spotlight_ken_burns")
        if (motion.kenBurnsActive) {
            val z by infiniteTransition.animateFloat(
                initialValue = 1.01f,
                targetValue = motion.zoomMaxScale,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 11500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "kbZoom"
            )
            val px by infiniteTransition.animateFloat(
                initialValue = -motion.kenBurnsPanPx,
                targetValue = motion.kenBurnsPanPx,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 13700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "kbPanX"
            )
            val py by infiniteTransition.animateFloat(
                initialValue = -motion.kenBurnsPanPx * 0.6f,
                targetValue = motion.kenBurnsPanPx * 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 16100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "kbPanY"
            )
            kenBurnsZoom = z
            kenBurnsPanX = px
            kenBurnsPanY = py
        } else {
            kenBurnsZoom = 1f
            kenBurnsPanX = 0f
            kenBurnsPanY = 0f
        }

        if (motion.ambientLightingActive) {
            val sweep by infiniteTransition.animateFloat(
                initialValue = -0.3f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 7400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ambientSweep"
            )
            lightSweep = sweep
        } else {
            lightSweep = 0.5f
        }
    } else {
        kenBurnsZoom = 1f
        kenBurnsPanX = 0f
        kenBurnsPanY = 0f
        lightSweep = 0.5f
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val effectiveDirectionSign = ArtworkMotionPlanner.resolveSpotlightDirectionSign(
        direction = motion.spotlightTransitionDirection,
        navigationDelta = navigationDirection
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .pointerInput(currentFeaturedCount) {
                if (currentFeaturedCount <= 1) return@pointerInput
                var accumulatedDragX = 0f
                var handledThisGesture = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulatedDragX = 0f
                        handledThisGesture = false
                    },
                    onDragCancel = {
                        accumulatedDragX = 0f
                        handledThisGesture = false
                    },
                    onDragEnd = {
                        if (!handledThisGesture) {
                            when {
                                accumulatedDragX <= -swipeThresholdPx -> currentOnSwipeNext()
                                accumulatedDragX >= swipeThresholdPx -> currentOnSwipePrevious()
                            }
                        }
                        accumulatedDragX = 0f
                        handledThisGesture = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragX += dragAmount
                        if (!handledThisGesture && abs(accumulatedDragX) >= swipeThresholdPx * 1.45f) {
                            handledThisGesture = true
                            if (accumulatedDragX < 0f) {
                                currentOnSwipeNext()
                            } else {
                                currentOnSwipePrevious()
                            }
                        }
                    }
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) {
                if (motion.isEnabled) {
                    coroutineScope.launch {
                        isLaunching = true
                        delay(85L)
                        isLaunching = false
                        onPlayFeatured()
                    }
                } else {
                    onPlayFeatured()
                }
            }
            .testTag("spotlight_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            SpotlightArtworkTransitionHost(
                track = featuredTrack,
                transitionStyle = motion.spotlightTransitionStyle,
                durationMs = motion.spotlightTransitionDurationMs,
                directionSign = effectiveDirectionSign,
                isAppResumed = isAppResumed,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxSize(),
                artworkLayer = { track ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Subtle background depth layer with gentle counter-movement
                        if (motion.parallaxActive) {
                            TrackArtwork(
                                track = track,
                                shape = RoundedCornerShape(24.dp),
                                crossfadeDurationMs = 0,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val depthScale = 1.18f
                                        scaleX = depthScale
                                        scaleY = depthScale
                                        translationX = -kenBurnsPanX * 0.45f
                                        translationY = (-scrollParallaxNormalized * motion.parallaxMaxShiftPx * 0.5f) -
                                            (kenBurnsPanY * 0.45f)
                                        alpha = 0.45f
                                    }
                            )
                        }

                        // Primary hero artwork with Ken Burns slow zoom + pan + scroll parallax
                        TrackArtwork(
                            track = track,
                            shape = RoundedCornerShape(24.dp),
                            crossfadeDurationMs = 0,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val activeZoom = if (motion.kenBurnsActive) {
                                        kenBurnsZoom
                                    } else if (motion.parallaxActive) {
                                        1.06f
                                    } else {
                                        1.0f
                                    }
                                    scaleX = activeZoom
                                    scaleY = activeZoom
                                    translationX = if (motion.kenBurnsActive) kenBurnsPanX else 0f
                                    val kbY = if (motion.kenBurnsActive) kenBurnsPanY else 0f
                                    val paraY = if (motion.parallaxActive) {
                                        scrollParallaxNormalized * motion.parallaxMaxShiftPx
                                    } else {
                                        0f
                                    }
                                    translationY = kbY + paraY
                                }
                        )

                        // Subtle animated diagonal ambient lighting sheen
                        if (motion.ambientLightingActive && isSpotlightVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                primaryColor.copy(alpha = motion.ambientLightAlpha * 0.55f),
                                                Color.White.copy(alpha = motion.ambientLightAlpha * 0.25f),
                                                Color.Transparent
                                            ),
                                            start = Offset((lightSweep - 0.5f) * 900f, 0f),
                                            end = Offset((lightSweep + 0.4f) * 900f, 650f)
                                        )
                                    )
                            )
                        }

                        // Rich legibility scrim overlay (keeps title, artist, and Play CTA crisp)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.14f),
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Black.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )
                    }
                },
                overlayChrome = { shownTrack, metadataAlpha ->
                    val displayTrack = shownTrack ?: featuredTrack
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "FEATURED SPOTLIGHT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            if (featuredCount > 1) {
                                val visibleDots = featuredCount.coerceAtMost(6)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.testTag("spotlight_page_indicator")
                                ) {
                                    repeat(visibleDots) { dotIdx ->
                                        val activeDot = (featuredIndex % visibleDots) == dotIdx
                                        Box(
                                            modifier = Modifier
                                                .height(5.dp)
                                                .width(if (activeDot) 16.dp else 5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (activeDot) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        Color.White.copy(alpha = 0.42f)
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                                    .graphicsLayer { alpha = metadataAlpha }
                            ) {
                                Text(
                                    text = displayTrack.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("spotlight_title")
                                )
                                Text(
                                    text = "${displayTrack.artist} • ${displayTrack.album}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("spotlight_subtitle")
                                )
                            }

                            FilledIconButton(
                                onClick = onPlayFeatured,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("spotlight_play_button")
                            ) {
                                Icon(
                                    imageVector = if (isFeaturedPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isFeaturedPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun AnimatedQuickPickCard(
    track: AudioTrack,
    index: Int,
    isCurrentlyPlaying: Boolean,
    motion: EffectiveArtworkMotion,
    breathingPhaseState: State<Float>,
    rowState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isOpening by remember { mutableStateOf(false) }

    // One-shot entrance animation when appearing on screen (skipped if motion disabled)
    val entranceProgress = remember(track.id, motion.isEnabled) {
        Animatable(if (motion.isEnabled) 0f else 1f)
    }
    LaunchedEffect(track.id, motion.isEnabled) {
        if (motion.isEnabled && entranceProgress.value < 1f) {
            delay((index.coerceAtMost(6) * 32L))
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = motion.transitionDurationMs.coerceAtLeast(220), easing = FastOutSlowInEasing)
            )
        } else if (!motion.isEnabled) {
            entranceProgress.snapTo(1f)
        }
    }

    val tapScale by animateFloatAsState(
        targetValue = when {
            !motion.isEnabled -> 1f
            isPressed -> motion.tapPressScale
            isOpening -> 1.035f
            isCurrentlyPlaying && motion.cardBreathingActive -> 1.02f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "quickPickTapScale"
    )

    val cardElevation by animateDpAsState(
        targetValue = when {
            !motion.isEnabled -> 0.dp
            isPressed -> 2.dp
            isOpening || isCurrentlyPlaying -> 10.dp
            else -> 4.dp
        },
        animationSpec = tween(durationMillis = 200),
        label = "quickPickElevation"
    )

    // Smooth horizontal carousel edge focus factor (GPU-only inside graphicsLayer)
    val viewportCenterFocus by remember(rowState, track.id) {
        derivedStateOf {
            val layout = rowState.layoutInfo
            val itemInfo = layout.visibleItemsInfo.firstOrNull { it.key == track.id }
            if (itemInfo == null || layout.viewportEndOffset <= layout.viewportStartOffset) {
                1f
            } else {
                val viewportWidth = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat()
                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                val distFromEdge = minOf(itemCenter, viewportWidth - itemCenter)
                (distFromEdge / (itemInfo.size * 0.6f)).coerceIn(0f, 1f)
            }
        }
    }

    Surface(
        modifier = modifier
            .width(140.dp)
            .graphicsLayer {
                val entry = entranceProgress.value
                val edgeScale = if (motion.isEnabled) 0.94f + 0.06f * viewportCenterFocus else 1f
                val breatheOffset = if (motion.cardBreathingActive) {
                    sin(breathingPhaseState.value + index * 0.85f) * motion.breathingTranslationPx
                } else {
                    0f
                }
                alpha = if (motion.isEnabled) entry * (0.72f + 0.28f * viewportCenterFocus) else 1f
                val finalScale = tapScale * edgeScale * (0.94f + 0.06f * entry)
                scaleX = finalScale
                scaleY = finalScale
                translationY = ((1f - entry) * 14f) + breatheOffset
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) {
                if (motion.isEnabled) {
                    coroutineScope.launch {
                        isOpening = true
                        delay(90L)
                        isOpening = false
                        onClick()
                    }
                } else {
                    onClick()
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                shadowElevation = cardElevation,
                border = if (isCurrentlyPlaying) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                } else {
                    null
                },
                color = Color.Transparent
            ) {
                AnimatedContent(
                    targetState = track,
                    contentKey = { it.id },
                    transitionSpec = {
                        if (motion.trackTransitionsActive && motion.transitionDurationMs > 0) {
                            (fadeIn(tween(motion.transitionDurationMs)) + scaleIn(initialScale = 0.94f))
                                .togetherWith(fadeOut(tween((motion.transitionDurationMs * 0.7f).toInt())))
                        } else {
                            EnterTransition.None.togetherWith(ExitTransition.None)
                        }
                    },
                    label = "quick_pick_art"
                ) { targetTrack ->
                    TrackArtwork(
                        track = targetTrack,
                        modifier = Modifier.size(130.dp),
                        shape = RoundedCornerShape(18.dp),
                        crossfadeDurationMs = if (motion.isEnabled) motion.transitionDurationMs else 0
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnimatedArtistCard(
    artist: String,
    index: Int,
    representativeTrack: AudioTrack?,
    isSelected: Boolean,
    motion: EffectiveArtworkMotion,
    rowState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isOpening by remember { mutableStateOf(false) }

    val entranceProgress = remember(artist, motion.isEnabled) {
        Animatable(if (motion.isEnabled) 0f else 1f)
    }
    LaunchedEffect(artist, motion.isEnabled) {
        if (motion.isEnabled && entranceProgress.value < 1f) {
            delay((index.coerceAtMost(6) * 35L))
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = motion.transitionDurationMs.coerceAtLeast(220), easing = FastOutSlowInEasing)
            )
        } else if (!motion.isEnabled) {
            entranceProgress.snapTo(1f)
        }
    }

    val scale by animateFloatAsState(
        targetValue = when {
            !motion.isEnabled -> 1f
            isPressed -> motion.tapPressScale
            isOpening -> 1.05f
            isSelected -> 1.02f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "artistCardScale"
    )

    val viewportFocus by remember(rowState, artist) {
        derivedStateOf {
            val layout = rowState.layoutInfo
            val info = layout.visibleItemsInfo.firstOrNull { it.key == artist }
            if (info == null || layout.viewportEndOffset <= layout.viewportStartOffset) {
                1f
            } else {
                val viewportWidth = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat()
                val center = info.offset + info.size / 2f
                val distFromEdge = minOf(center, viewportWidth - center)
                (distFromEdge / (info.size * 0.55f)).coerceIn(0f, 1f)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isSelected || isOpening) 6.dp else 0.dp,
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) else null,
        modifier = modifier
            .width(110.dp)
            .graphicsLayer {
                val entry = entranceProgress.value
                val edgeScale = if (motion.isEnabled) 0.94f + 0.06f * viewportFocus else 1f
                alpha = if (motion.isEnabled) entry * (0.76f + 0.24f * viewportFocus) else 1f
                val finalScale = scale * edgeScale * (0.93f + 0.07f * entry)
                scaleX = finalScale
                scaleY = finalScale
                translationY = if (motion.isEnabled) (1f - entry) * 12f else 0f
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) {
                if (motion.isEnabled) {
                    coroutineScope.launch {
                        isOpening = true
                        delay(90L)
                        isOpening = false
                        onClick()
                    }
                } else {
                    onClick()
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (representativeTrack != null) {
                TrackArtwork(
                    track = representativeTrack,
                    shape = CircleShape,
                    contentDescription = artist,
                    crossfadeDurationMs = if (motion.isEnabled) motion.transitionDurationMs else 0,
                    modifier = Modifier.size(54.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = artist,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
