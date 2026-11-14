package com.example.sangeet.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.CassetteStyle

private val WalkmanGold = Color(0xFFE8B429)
private val WalkmanGoldBright = Color(0xFFFFD666)
private val WalkmanGoldDark = Color(0xFF9C7212)
private val WalkmanObsidian = Color(0xFF151619)
private val WalkmanChassisDark = Color(0xFF0C0D10)
private val WalkmanTrimSteel = Color(0xFF2B2E36)

/**
 * Physical mechanical piano-key Walkman button mounted on the top deck.
 * Animates physically downward on press or when latched (e.g. active PLAY or PAUSE).
 */
@Composable
fun WalkmanButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    isLatched: Boolean,
    isPrimaryPlayKey: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val depressDp by animateDpAsState(
        targetValue = when {
            isPressed -> 6.dp
            isLatched -> 3.5.dp
            else -> 0.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "walkmanBtnDepress"
    )

    val keyCapTop = when {
        isPrimaryPlayKey && isLatched -> WalkmanGoldBright
        isPrimaryPlayKey -> WalkmanGold
        isLatched -> Color(0xFF42361D)
        else -> Color(0xFF282B32)
    }
    val keyCapBottom = when {
        isPrimaryPlayKey -> WalkmanGoldDark
        isLatched -> Color(0xFF292112)
        else -> Color(0xFF14161A)
    }
    val iconTint by animateColorAsState(
        targetValue = when {
            isPrimaryPlayKey -> Color.Black
            isLatched -> WalkmanGoldBright
            enabled -> Color(0xFFE5E7EB)
            else -> Color(0xFF6B7280)
        },
        animationSpec = tween(160),
        label = "walkmanBtnTint"
    )

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTagName)
            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 4.dp, bottomEnd = 4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mechanical recess track showing travel depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF07080A), RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .offset(y = depressDp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(keyCapTop, keyCapBottom)))
                        .border(
                            width = if (isLatched || isPrimaryPlayKey) 1.dp else 0.7.dp,
                            color = if (isLatched || isPrimaryPlayKey) {
                                WalkmanGoldBright.copy(alpha = 0.85f)
                            } else {
                                Color.White.copy(alpha = 0.16f)
                            },
                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Metallic top bevel highlight
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.White.copy(alpha = if (isPrimaryPlayKey) 0.45f else 0.22f))
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.3.sp
                            ),
                            color = iconTint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Physical 7-key Walkman top deck transport bar: REW, PREV, PLAY, PAUSE, STOP, NEXT, FF.
 */
@Composable
fun WalkmanControls(
    transportState: WalkmanTransportState,
    isSwapping: Boolean,
    onRewind: () -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onFastForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        WalkmanButton(
            icon = Icons.Rounded.FastRewind,
            label = "REW",
            contentDescription = "Rewind 10 seconds",
            isLatched = false,
            onClick = onRewind,
            modifier = Modifier.weight(1f),
            testTagName = "walkman_btn_rew"
        )
        WalkmanButton(
            icon = Icons.Rounded.SkipPrevious,
            label = "PREV",
            contentDescription = "Previous track",
            isLatched = false,
            enabled = !isSwapping,
            onClick = onPrevious,
            modifier = Modifier.weight(1f),
            testTagName = "walkman_btn_prev"
        )
        WalkmanButton(
            icon = Icons.Rounded.PlayArrow,
            label = "PLAY",
            contentDescription = "Play",
            isLatched = transportState == WalkmanTransportState.PLAYING,
            isPrimaryPlayKey = true,
            onClick = onPlay,
            modifier = Modifier.weight(1.18f),
            testTagName = "walkman_btn_play"
        )
        WalkmanButton(
            icon = Icons.Rounded.Pause,
            label = "PAUSE",
            contentDescription = "Pause",
            isLatched = transportState == WalkmanTransportState.PAUSED,
            onClick = onPause,
            modifier = Modifier.weight(1f),
            testTagName = "walkman_btn_pause"
        )
        WalkmanButton(
            icon = Icons.Rounded.Stop,
            label = "STOP",
            contentDescription = "Stop playback",
            isLatched = transportState == WalkmanTransportState.STOPPED,
            onClick = onStop,
            modifier = Modifier.weight(1f),
            testTagName = "walkman_btn_stop"
        )
        WalkmanButton(
            icon = Icons.Rounded.SkipNext,
            label = "NEXT",
            contentDescription = "Next track",
            isLatched = false,
            enabled = !isSwapping,
            onClick = onNext,
            modifier = Modifier.weight(1f),
            testTagName = "walkman_btn_next"
        )
        WalkmanButton(
            icon = Icons.Rounded.FastForward,
            label = "FF",
            contentDescription = "Fast forward 10 seconds",
            isLatched = false,
            onClick = onFastForward,
            modifier = Modifier.weight(1f),
            testTagName = "walkman_btn_ff"
        )
    }
}

/**
 * Hinged Walkman Cassette Compartment housing the physical tape drive bay,
 * animated ejecting/inserting cassettes, and 3D smoked-acrylic transparent door.
 */
@Composable
fun CassetteCompartment(
    swapController: CassetteSwapController,
    currentTrack: AudioTrack,
    cassetteStyle: CassetteStyle,
    reelAngles: CassetteReelAnglesState,
    progressFraction: Float,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val doorOpen = swapController.doorOpenAnim.value.coerceIn(0f, 1f)
    val phase = swapController.phase

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.44f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070709))
            .border(1.8.dp, WalkmanGold.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .padding(10.dp)
            .testTag("walkman_cassette_compartment"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Internal Walkman Metallic Bay & Twin Drive Spindles (visible when door opens or through transparent tape)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Warm interior lamp glow that brightens as door opens
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        WalkmanGold.copy(alpha = 0.08f + 0.16f * doorOpen),
                        Color(0xFF0A0A0D)
                    ),
                    center = Offset(w * 0.5f, h * 0.45f),
                    radius = w * 0.65f
                ),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // Interior steel deck chassis rails
            drawRoundRect(
                color = Color(0xFF1D2026),
                topLeft = Offset(w * 0.06f, h * 0.10f),
                size = Size(w * 0.88f, h * 0.80f),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Twin Walkman drive spindle hubs inside compartment floor
            val spindleY = h * 0.57f
            listOf(w * 0.30f, w * 0.70f).forEach { sx ->
                drawCircle(color = Color(0xFF282C34), radius = 14.dp.toPx(), center = Offset(sx, spindleY))
                drawCircle(color = WalkmanGold.copy(alpha = 0.45f), radius = 9.dp.toPx(), center = Offset(sx, spindleY), style = Stroke(1.2.dp.toPx()))
                drawCircle(color = Color(0xFFD6DBE2), radius = 4.dp.toPx(), center = Offset(sx, spindleY))
            }
        }

        // 2. Active Cassette Stage (Ejecting old tape, Inserting new tape, or Seated steady tape)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            when (phase) {
                CassetteSwapPhase.IDLE -> {
                    val trackToShow = swapController.settledTrack ?: currentTrack
                    CassetteBody(
                        track = trackToShow,
                        style = cassetteStyle,
                        reelAngles = reelAngles,
                        progressFraction = progressFraction,
                        isPlaying = isPlaying
                    )
                }
                CassetteSwapPhase.OPENING -> {
                    val outgoing = swapController.outgoingTrack ?: currentTrack
                    CassetteBody(
                        track = outgoing,
                        style = cassetteStyle,
                        reelAngles = reelAngles,
                        progressFraction = progressFraction,
                        isPlaying = false
                    )
                }
                CassetteSwapPhase.EJECTING -> {
                    val outgoing = swapController.outgoingTrack ?: currentTrack
                    CassetteEjectAnimation(
                        progress = swapController.ejectAnim.value,
                        direction = swapController.direction,
                        reducedMotion = reducedMotion
                    ) {
                        CassetteBody(
                            track = outgoing,
                            style = cassetteStyle,
                            reelAngles = reelAngles,
                            progressFraction = progressFraction,
                            isPlaying = false
                        )
                    }
                }
                CassetteSwapPhase.INSERTING -> {
                    val incoming = swapController.incomingTrack ?: currentTrack
                    CassetteInsertAnimation(
                        progress = swapController.insertAnim.value,
                        direction = swapController.direction,
                        reducedMotion = reducedMotion
                    ) {
                        CassetteBody(
                            track = incoming,
                            style = cassetteStyle,
                            reelAngles = reelAngles,
                            progressFraction = 0f,
                            isPlaying = false
                        )
                    }
                }
                CassetteSwapPhase.CLOSING -> {
                    val seated = swapController.incomingTrack ?: swapController.settledTrack ?: currentTrack
                    CassetteBody(
                        track = seated,
                        style = cassetteStyle,
                        reelAngles = reelAngles,
                        progressFraction = progressFraction,
                        isPlaying = isPlaying
                    )
                }
            }
        }

        // 3. Hinged Smoked-Acrylic Walkman Door Window Overlay (swings open cleanly during cassette replacement)
        val doorAlpha = (1f - doorOpen * 0.72f).coerceIn(0.18f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1.0f)
                    if (!reducedMotion) {
                        rotationX = doorOpen * 54f
                        cameraDistance = 14f * density
                        translationY = doorOpen * 16f * density
                    }
                    alpha = doorAlpha
                }
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = WalkmanGoldBright.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Smoked glass glossy reflection streaks + framing lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Upper metallic visor strip along Walkman door top edge
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xAA1F2229), Color(0x220D0E12))
                    ),
                    size = Size(w, h * 0.14f)
                )

                // Diagonal glass specular reflection streak across acrylic window
                val streak = Path().apply {
                    moveTo(w * 0.08f, h * 0.92f)
                    lineTo(w * 0.36f, h * 0.04f)
                    lineTo(w * 0.44f, h * 0.04f)
                    lineTo(w * 0.16f, h * 0.92f)
                    close()
                }
                drawPath(path = streak, color = Color.White.copy(alpha = 0.055f))

                val streakThin = Path().apply {
                    moveTo(w * 0.62f, h * 0.92f)
                    lineTo(w * 0.82f, h * 0.04f)
                    lineTo(w * 0.85f, h * 0.04f)
                    lineTo(w * 0.65f, h * 0.92f)
                    close()
                }
                drawPath(path = streakThin, color = Color.White.copy(alpha = 0.035f))
            }

            // Bottom right etched door badge
            Text(
                text = "AUTO REVERSE • HIGH FIDELITY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                ),
                color = WalkmanGoldBright.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Vintage Sony Walkman-inspired portable chassis with brushed golden-yellow trim,
 * matte black anodized body, tactile top control keys, glowing LED, and hinged cassette bay.
 */
@Composable
fun WalkmanBody(
    track: AudioTrack,
    cassetteStyle: CassetteStyle,
    transportState: WalkmanTransportState,
    swapController: CassetteSwapController,
    reelAngles: CassetteReelAnglesState,
    progressFraction: Float,
    reducedMotion: Boolean,
    onRewind: () -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onFastForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSwapping = swapController.phase != CassetteSwapPhase.IDLE
    val ledColor by animateColorAsState(
        targetValue = when {
            isSwapping -> Color(0xFFFF9800)
            transportState == WalkmanTransportState.PLAYING -> Color(0xFFFFD54F)
            transportState == WalkmanTransportState.PAUSED -> Color(0xFFB3861B)
            else -> Color(0xFF332914)
        },
        animationSpec = tween(200),
        label = "walkmanLed"
    )

    val statusText = when {
        isSwapping -> when (swapController.phase) {
            CassetteSwapPhase.OPENING -> "DOOR OPENING"
            CassetteSwapPhase.EJECTING -> "EJECTING TAPE"
            CassetteSwapPhase.INSERTING -> "LOADING CASSETTE"
            CassetteSwapPhase.CLOSING -> "LATCHING"
            CassetteSwapPhase.IDLE -> ""
        }
        transportState == WalkmanTransportState.PLAYING -> "PLAYING"
        transportState == WalkmanTransportState.PAUSED -> "PAUSED"
        else -> "STOPPED"
    }

    Box(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .testTag("walkman_body"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Subtle warm golden aura beneath hardware on AMOLED black backdrop
        Canvas(
            modifier = Modifier
                .matchParentSize()
        ) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        WalkmanGold.copy(alpha = if (transportState == WalkmanTransportState.PLAYING) 0.19f else 0.09f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.58f),
                    radius = size.width * 0.62f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Physical Transport Buttons mounted atop the Walkman Crown
            WalkmanControls(
                transportState = transportState,
                isSwapping = isSwapping,
                onRewind = onRewind,
                onPrevious = onPrevious,
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
                onNext = onNext,
                onFastForward = onFastForward
            )

            // Main Walkman Metal & Leatherette Chassis
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(22.dp, RoundedCornerShape(24.dp))
                    .border(
                        width = 1.6.dp,
                        brush = Brush.verticalGradient(
                            listOf(WalkmanGoldBright, WalkmanGoldDark, WalkmanGold)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                color = WalkmanObsidian
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1F2127),
                                    WalkmanObsidian,
                                    WalkmanChassisDark
                                )
                            )
                        )
                        .drawBehind {
                            val railWidth = 7.dp.toPx()
                            val topY = size.height * 0.08f
                            val railH = size.height * 0.84f
                            val railBrush = Brush.verticalGradient(
                                colors = listOf(WalkmanGoldDark, WalkmanGoldBright, WalkmanGold, WalkmanGoldDark),
                                startY = topY,
                                endY = topY + railH
                            )
                            drawRoundRect(
                                brush = railBrush,
                                topLeft = Offset(0f, topY),
                                size = Size(railWidth, railH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawRoundRect(
                                brush = railBrush,
                                topLeft = Offset(size.width - railWidth, topY),
                                size = Size(railWidth, railH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Chrome/Gold Faceplate Silkscreen Bar (Brand + LED + Status)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(WalkmanTrimSteel, Color(0xFF1C1E24), WalkmanTrimSteel)
                                    )
                                )
                                .border(0.8.dp, WalkmanGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "SANGEET",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp,
                                            fontSize = 13.sp
                                        ),
                                        color = WalkmanGoldBright
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(WalkmanGold)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "WM-90",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = Color.Black
                                        )
                                    }
                                }
                                Text(
                                    text = "STEREO CASSETTE WALKMAN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        letterSpacing = 1.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = Color(0xFFD1D5DB),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Glowing Status + BATT LED indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = WalkmanGoldBright,
                                    modifier = Modifier.testTag("walkman_status_badge")
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ledColor)
                                        .border(1.dp, Color.Black, CircleShape)
                                        .testTag("walkman_power_led")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large Hinged Cassette Compartment
                        CassetteCompartment(
                            swapController = swapController,
                            currentTrack = track,
                            cassetteStyle = cassetteStyle,
                            reelAngles = reelAngles,
                            progressFraction = progressFraction,
                            isPlaying = transportState == WalkmanTransportState.PLAYING,
                            reducedMotion = reducedMotion
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Lower Chassis Engraving Strip (Cassette Style Badge & Open Clasp Accent)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(WalkmanGold)
                                )
                                Text(
                                    text = "DOLBY B-C NR",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                            Text(
                                text = cassetteStyle.displayName.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = WalkmanGold
                            )
                        }
                    }
                }
            }
        }
    }
}
