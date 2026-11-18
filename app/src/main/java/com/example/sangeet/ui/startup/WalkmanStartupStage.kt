package com.example.sangeet.ui.startup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

internal val BrandDeepNavy = Color(0xFF091124)
internal val BrandNavyMid = Color(0xFF101C3B)
internal val BrandGold = Color(0xFFE8B429)
internal val BrandGoldBright = Color(0xFFFFD96A)
internal val BrandGoldDark = Color(0xFF8F6711)
internal val BrandObsidian = Color(0xFF0E1015)

/**
 * Vintage SANGEET Walkman deck rendered in native Jetpack Compose for Scenes 1 through 6:
 * - Emergence from black with golden edge reflections
 * - Hinged front compartment door unlocking & swinging open in 3D perspective
 * - Descending SANGEET cassette aligning into spindle hubs
 * - Door latching shut with visible cassette inside window
 * - Mechanical Play switch depressing & twin reels spinning up
 */
@Composable
fun WalkmanStartupStage(
    state: StartupVisualState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    Box(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                alpha = state.walkmanAlpha
                scaleX = state.walkmanScale
                scaleY = state.walkmanScale
                translationY = state.walkmanOffsetYFraction * 260f * density
            }
            .testTag("startup_walkman_stage"),
        contentAlignment = Alignment.Center
    ) {
        // Warm golden floor shadow + back halo beneath the Walkman hardware
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
        ) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandGold.copy(alpha = (0.14f + 0.24f * state.playLedGlow) * state.walkmanShadowAlpha),
                        BrandNavyMid.copy(alpha = 0.16f * state.walkmanShadowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.55f),
                    radius = size.width * 0.65f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top mechanical piano-key buttons bar (Play button highlights and depresses in Scene 5)
            StartupTopButtonsDeck(
                playDepress = state.playSwitchDepress,
                latchFlash = state.latchUnlockFlash
            )

            // Main Walkman anodized deep-navy & obsidian chassis
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BrandNavyMid,
                                Color(0xFF0A0F1D),
                                Color(0xFF06080E)
                            )
                        )
                    )
                    .border(
                        width = 1.6.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                BrandGoldBright.copy(alpha = 0.78f),
                                BrandGoldDark.copy(alpha = 0.45f),
                                BrandGold.copy(alpha = 0.72f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Upper brushed aluminum & gold badge bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Power / Drive LED that ignites amber-gold when Play depresses
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.playLedGlow > 0.05f) {
                                            BrandGoldBright.copy(alpha = (0.4f + 0.6f * state.playLedGlow))
                                        } else {
                                            Color(0xFF2B2415)
                                        }
                                    )
                                    .border(
                                        0.7.dp,
                                        BrandGold.copy(alpha = 0.55f),
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "SANGEET WALKMAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.4.sp
                                ),
                                color = BrandGoldBright.copy(alpha = 0.92f)
                            )
                        }

                        // Metallic door latch tab that glows briefly on Scene 2 unlock
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    BrandGold.copy(alpha = 0.14f + 0.55f * state.latchUnlockFlash)
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = BrandGoldBright.copy(alpha = 0.5f + 0.5f * state.latchUnlockFlash),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (state.doorOpenProgress > 0.2f) "OPEN" else "LOCK",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 0.8.sp
                                ),
                                color = BrandGoldBright
                            )
                        }
                    }

                    // Recessed Cassette Compartment + Animated Cassette + 3D Hinged Door
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.48f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF04060A))
                            .border(
                                width = 1.4.dp,
                                color = BrandGold.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Layer 1: Compartment bay interior (spindles, brass capstan, tape head)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawWalkmanBayInterior(
                                doorOpenProgress = state.doorOpenProgress,
                                reelSpinDegrees = state.reelSpinDegrees
                            )
                        }

                        // Layer 2: SANGEET Black & Gold Cassette descending into compartment slots
                        if (state.cassetteVisible) {
                            val dropInv = 1f - state.cassetteDropProgress.coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.94f)
                                    .aspectRatio(1.58f)
                                    .graphicsLayer {
                                        translationY = -dropInv * 96f * density
                                        scaleX = 1f + 0.08f * dropInv
                                        scaleY = 1f + 0.08f * dropInv
                                        rotationX = dropInv * 18f
                                        cameraDistance = 14f * density
                                        alpha = (state.cassetteDropProgress / 0.25f).coerceIn(0f, 1f)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                StartupCassetteCard(
                                    reelSpinDegrees = state.reelSpinDegrees,
                                    tapeOffsetPx = state.tapeOffsetPx
                                )
                            }
                        }

                        // Layer 3: Hinged smoked-acrylic Walkman compartment door
                        val doorAngle = state.doorOpenProgress * 58f
                        val doorAlpha = (1f - state.doorOpenProgress * 0.62f).coerceIn(0.24f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    rotationX = doorAngle
                                    cameraDistance = 16f * density
                                    translationY = state.doorOpenProgress * 14f * density
                                    alpha = doorAlpha
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = 1.dp,
                                    color = BrandGoldBright.copy(alpha = 0.48f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                // Smoked glass top metallic frame
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0x99192238), Color(0x14080C16))
                                    ),
                                    size = Size(w, h * 0.13f)
                                )
                                // Crisp diagonal specular streaks on glass door
                                val streak = Path().apply {
                                    moveTo(w * 0.10f, h * 0.94f)
                                    lineTo(w * 0.36f, h * 0.05f)
                                    lineTo(w * 0.44f, h * 0.05f)
                                    lineTo(w * 0.18f, h * 0.94f)
                                    close()
                                }
                                drawPath(streak, color = Color.White.copy(alpha = 0.075f))

                                val thinStreak = Path().apply {
                                    moveTo(w * 0.66f, h * 0.94f)
                                    lineTo(w * 0.84f, h * 0.05f)
                                    lineTo(w * 0.87f, h * 0.05f)
                                    lineTo(w * 0.69f, h * 0.94f)
                                    close()
                                }
                                drawPath(thinStreak, color = Color.White.copy(alpha = 0.045f))
                            }

                            Text(
                                text = "HI-FI ANALOG DECK",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 7.sp,
                                    letterSpacing = 0.9.sp
                                ),
                                color = BrandGoldBright.copy(alpha = 0.68f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Bottom Walkman engraved serial / gold pinstripe plate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.activeScene.caption,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            ),
                            color = BrandGold.copy(alpha = 0.78f)
                        )
                        Text(
                            text = "OFFLINE AUDIO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                letterSpacing = 0.7.sp
                            ),
                            color = Color(0xFF94A3B8).copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartupTopButtonsDeck(
    playDepress: Float,
    latchFlash: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val labels = listOf("REW", "PREV", "PLAY", "PAUSE", "STOP", "NEXT")
        labels.forEach { key ->
            val isPlay = key == "PLAY"
            val offsetDp = if (isPlay) (playDepress * 5f).dp else 0.dp
            val isGlowingPlay = isPlay && playDepress > 0.08f
            val weightVal = if (isPlay) 1.35f else 1f

            Box(
                modifier = Modifier
                    .weight(weightVal)
                    .height(22.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .offset(y = offsetDp)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(
                            Brush.verticalGradient(
                                if (isPlay) {
                                    listOf(
                                        if (isGlowingPlay) BrandGoldBright else BrandGold,
                                        BrandGoldDark
                                    )
                                } else {
                                    listOf(
                                        if (latchFlash > 0f && key == "STOP") Color(0xFF474E5E) else Color(0xFF282E3D),
                                        Color(0xFF13161E)
                                    )
                                }
                            )
                        )
                        .border(
                            width = if (isGlowingPlay) 1.2.dp else 0.7.dp,
                            color = if (isPlay) BrandGoldBright else Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 6.5.sp
                        ),
                        color = if (isPlay) Color.Black else Color(0xFFDCE3F0)
                    )
                }
            }
        }
    }
}

@Composable
internal fun StartupCassetteCard(
    reelSpinDegrees: Float,
    tapeOffsetPx: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF191C24), Color(0xFF0A0B0E), Color(0xFF171920))
                )
            )
            .border(
                width = 1.4.dp,
                brush = Brush.linearGradient(listOf(BrandGoldBright, BrandGoldDark, BrandGold)),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDetailedCassetteArtwork(
                reelSpinDegrees = reelSpinDegrees,
                tapeOffsetPx = tapeOffsetPx
            )
        }
        Text(
            text = "SANGEET",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.8.sp
            ),
            color = BrandGoldBright,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp, start = 24.dp)
        )
    }
}

private fun DrawScope.drawWalkmanBayInterior(
    doorOpenProgress: Float,
    reelSpinDegrees: Float
) {
    val w = size.width
    val h = size.height

    // Interior compartment warm golden lamp
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                BrandGold.copy(alpha = 0.08f + 0.24f * doorOpenProgress),
                Color(0xFF070910)
            ),
            center = Offset(w * 0.5f, h * 0.45f),
            radius = w * 0.65f
        ),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    )

    // Machined metallic frame rails inside bay
    drawRoundRect(
        color = Color(0xFF1E2432),
        topLeft = Offset(w * 0.06f, h * 0.08f),
        size = Size(w * 0.88f, h * 0.84f),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Twin precision drive spindles that turn with the cassette reels
    val spindleY = h * 0.55f
    listOf(w * 0.31f, w * 0.69f).forEach { sx ->
        drawCircle(color = Color(0xFF222836), radius = 14.dp.toPx(), center = Offset(sx, spindleY))
        drawCircle(
            color = BrandGold.copy(alpha = 0.55f),
            radius = 9.dp.toPx(),
            center = Offset(sx, spindleY),
            style = Stroke(width = 1.2.dp.toPx())
        )
        rotate(degrees = reelSpinDegrees, pivot = Offset(sx, spindleY)) {
            for (k in 0 until 3) {
                val angle = k * (2.0 * Math.PI / 3.0)
                val tx = sx + (5.dp.toPx() * cos(angle)).toFloat()
                val ty = spindleY + (5.dp.toPx() * sin(angle)).toFloat()
                drawCircle(color = BrandGoldBright, radius = 1.6.dp.toPx(), center = Offset(tx, ty))
            }
        }
        drawCircle(color = Color(0xFFD8DEE8), radius = 3.2.dp.toPx(), center = Offset(sx, spindleY))
    }

    // Bottom magnetic playback head & brass pinch roller
    drawRoundRect(
        color = Color(0xFF333B4D),
        topLeft = Offset(w * 0.40f, h * 0.78f),
        size = Size(w * 0.20f, h * 0.11f),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )
    drawCircle(color = BrandGoldDark, radius = 4.dp.toPx(), center = Offset(w * 0.34f, h * 0.835f))
    drawCircle(color = BrandGoldDark, radius = 4.dp.toPx(), center = Offset(w * 0.66f, h * 0.835f))
}

internal fun DrawScope.drawDetailedCassetteArtwork(
    reelSpinDegrees: Float,
    tapeOffsetPx: Float
) {
    val w = size.width
    val h = size.height

    // Inner metallic brushed gold face panel
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF141B2D), Color(0xFF0A0D14))
        ),
        topLeft = Offset(w * 0.04f, h * 0.06f),
        size = Size(w * 0.92f, h * 0.72f),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )

    // Gold top label band
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(BrandGoldDark, BrandGoldBright, BrandGold)
        ),
        topLeft = Offset(w * 0.08f, h * 0.11f),
        size = Size(w * 0.84f, h * 0.20f),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )

    // Navy inset stripe inside label holding SANGEET + waveform bars
    drawRoundRect(
        color = BrandDeepNavy,
        topLeft = Offset(w * 0.10f, h * 0.135f),
        size = Size(w * 0.80f, h * 0.15f),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Small gold audio waveform decorations on left of label
    val waveHeights = listOf(0.35f, 0.75f, 1.0f, 0.65f, 0.40f)
    val labelMidY = h * 0.21f
    waveHeights.forEachIndexed { idx, factor ->
        val barX = w * (0.145f + idx * 0.022f)
        val halfH = h * 0.042f * factor
        drawLine(
            color = BrandGoldBright,
            start = Offset(barX, labelMidY - halfH),
            end = Offset(barX, labelMidY + halfH),
            strokeWidth = (w * 0.009f).coerceAtLeast(1.6f),
            cap = StrokeCap.Round
        )
    }

    // Subtle golden highlight underline beside waveform bars where SANGEET text renders
    drawLine(
        color = BrandGold.copy(alpha = 0.22f),
        start = Offset(w * 0.30f, labelMidY + h * 0.045f),
        end = Offset(w * 0.85f, labelMidY + h * 0.045f),
        strokeWidth = 1.dp.toPx()
    )

    // Transparent smoked center tape window
    val winLeft = w * 0.16f
    val winTop = h * 0.37f
    val winW = w * 0.68f
    val winH = h * 0.34f
    drawRoundRect(
        color = Color(0xFF05070B),
        topLeft = Offset(winLeft, winTop),
        size = Size(winW, winH),
        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
    )
    drawRoundRect(
        color = BrandGold.copy(alpha = 0.65f),
        topLeft = Offset(winLeft, winTop),
        size = Size(winW, winH),
        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )

    // Magnetic tape spool bands & moving tape sheen inside transparent window
    val reelY = winTop + winH * 0.5f
    val leftReelX = w * 0.31f
    val rightReelX = w * 0.69f
    drawLine(
        color = Color(0xFF3E2618),
        start = Offset(leftReelX, reelY + winH * 0.26f),
        end = Offset(rightReelX, reelY + winH * 0.26f),
        strokeWidth = (winH * 0.14f).coerceAtLeast(2f)
    )
    val pulseX = winLeft + ((tapeOffsetPx / 48f) * winW * 0.5f) + winW * 0.22f
    drawCircle(
        color = BrandGoldBright.copy(alpha = 0.32f),
        radius = winH * 0.08f,
        center = Offset(pulseX.coerceIn(leftReelX, rightReelX), reelY + winH * 0.26f)
    )

    // Twin golden toothed cassette reels
    val reelRadius = winH * 0.36f
    listOf(leftReelX, rightReelX).forEachIndexed { idx, cx ->
        val angle = if (idx == 0) reelSpinDegrees else reelSpinDegrees * 0.88f + 18f
        drawCircle(
            color = Color(0xFF2E1E14),
            radius = reelRadius * 1.18f,
            center = Offset(cx, reelY)
        )
        drawCircle(
            color = BrandGold,
            radius = reelRadius,
            center = Offset(cx, reelY),
            style = Stroke(width = (reelRadius * 0.26f).coerceAtLeast(2f))
        )
        drawCircle(
            color = Color(0xFF0A0D14),
            radius = reelRadius * 0.58f,
            center = Offset(cx, reelY)
        )
        rotate(degrees = angle, pivot = Offset(cx, reelY)) {
            for (t in 0 until 6) {
                val rad = t * (Math.PI / 3.0)
                val sx = cx + (reelRadius * 0.35f * cos(rad)).toFloat()
                val sy = reelY + (reelRadius * 0.35f * sin(rad)).toFloat()
                val ex = cx + (reelRadius * 0.72f * cos(rad)).toFloat()
                val ey = reelY + (reelRadius * 0.72f * sin(rad)).toFloat()
                drawLine(
                    color = BrandGoldBright,
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = (reelRadius * 0.22f).coerceAtLeast(1.8f),
                    cap = StrokeCap.Round
                )
            }
        }
    }

    // Bottom trapezoid tape bridge + corner screws
    val bridge = Path().apply {
        moveTo(w * 0.25f, h * 0.96f)
        lineTo(w * 0.31f, h * 0.79f)
        lineTo(w * 0.69f, h * 0.79f)
        lineTo(w * 0.75f, h * 0.96f)
        close()
    }
    drawPath(bridge, color = Color(0xFF151923))
    drawPath(bridge, color = BrandGold.copy(alpha = 0.45f), style = Stroke(width = 1.dp.toPx()))

    listOf(
        Offset(w * 0.085f, h * 0.11f),
        Offset(w * 0.915f, h * 0.11f),
        Offset(w * 0.085f, h * 0.89f),
        Offset(w * 0.915f, h * 0.89f),
        Offset(w * 0.50f, h * 0.87f)
    ).forEach { center ->
        drawCircle(color = BrandGold, radius = (w * 0.018f).coerceAtLeast(2.2f), center = center)
        drawCircle(color = Color(0xFF2B2009), radius = (w * 0.008f).coerceAtLeast(1f), center = center)
    }
}
