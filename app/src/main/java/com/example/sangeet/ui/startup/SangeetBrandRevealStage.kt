package com.example.sangeet.ui.startup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scenes 6, 7 & 8 Brand Emblem Lockup:
 * - Deep navy blue circular medallion (#091124)
 * - Golden circular border animating cleanly from 0° to 360° sweep
 * - Centered SANGEET cassette logo with twin spinning golden reels & waveforms
 * - Diagonal metallic golden light sweep
 * - Bold "SANGEET" lettering and "by Ganeswar" developer signature underneath
 */
@Composable
fun SangeetBrandRevealStage(
    state: StartupVisualState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("startup_brand_reveal_stage"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Circular Medallion Emblem (matches the SANGEET launcher icon silhouette)
        Box(
            modifier = Modifier
                .size(216.dp)
                .graphicsLayer {
                    alpha = state.emblemAlpha
                    scaleX = state.emblemScale
                    scaleY = state.emblemScale
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)
                val ringRadius = w * 0.43f

                // Expanding warm radial golden glow during Walkman -> Logo transition (Scene 6)
                if (state.transitionGlowRadius > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BrandGoldBright.copy(alpha = 0.34f * state.transitionGlowRadius),
                                BrandGold.copy(alpha = 0.12f * state.transitionGlowRadius),
                                Color.Transparent
                            ),
                            center = center,
                            radius = w * (0.55f + 0.35f * state.transitionGlowRadius)
                        ),
                        radius = w * 0.90f,
                        center = center
                    )
                }

                // Deep navy blue medallion disk
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(BrandNavyMid, BrandDeepNavy, Color(0xFF050A17)),
                        center = Offset(w * 0.42f, h * 0.38f),
                        radius = ringRadius * 1.15f
                    ),
                    radius = ringRadius,
                    center = center
                )

                // Golden circular border drawing smoothly around 360 degrees
                val sweepDeg = 360f * state.emblemRingSweep.coerceIn(0f, 1f)
                if (sweepDeg > 0.5f) {
                    val strokePx = 6.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(BrandGoldDark, BrandGoldBright, BrandGold, BrandGoldBright, BrandGoldDark),
                            center = center
                        ),
                        startAngle = -90f,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Bright leading spark dot at tip of drawing arc
                    if (sweepDeg < 356f) {
                        val tipRad = Math.toRadians((-90f + sweepDeg).toDouble())
                        val tipOffset = Offset(
                            x = center.x + (ringRadius * cos(tipRad)).toFloat(),
                            y = center.y + (ringRadius * sin(tipRad)).toFloat()
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, BrandGoldBright, Color.Transparent),
                                center = tipOffset,
                                radius = 14.dp.toPx()
                            ),
                            radius = 14.dp.toPx(),
                            center = tipOffset
                        )
                    }
                }

                // Centered illustrated black & brushed metallic gold SANGEET cassette inside medallion
                val casW = w * 0.62f
                val casH = casW / 1.56f
                val casLeft = (w - casW) / 2f
                val casTop = (h - casH) / 2f

                // Cassette outer rounded shell
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1F232C), Color(0xFF0A0B0E))
                    ),
                    topLeft = Offset(casLeft, casTop),
                    size = Size(casW, casH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(BrandGoldBright, BrandGoldDark, BrandGoldBright)
                    ),
                    topLeft = Offset(casLeft, casTop),
                    size = Size(casW, casH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 1.6.dp.toPx())
                )

                // Brushed metallic gold body insert around tape window
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(BrandGoldDark, BrandGoldBright, BrandGold)
                    ),
                    topLeft = Offset(casLeft + casW * 0.06f, casTop + casH * 0.10f),
                    size = Size(casW * 0.88f, casH * 0.60f),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )

                // Dark navy cassette header band for waveforms + SANGEET text
                drawRoundRect(
                    color = BrandDeepNavy,
                    topLeft = Offset(casLeft + casW * 0.07f, casTop + casH * 0.12f),
                    size = Size(casW * 0.86f, casH * 0.22f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Small golden audio waveform bars on upper left of cassette label
                val waveScale = listOf(0.42f, 0.78f, 1.0f, 0.70f, 0.38f)
                val waveCenterY = casTop + casH * 0.23f
                waveScale.forEachIndexed { idx, s ->
                    val bx = casLeft + casW * (0.125f + idx * 0.025f)
                    val hh = casH * 0.055f * s
                    drawLine(
                        color = BrandGoldBright,
                        start = Offset(bx, waveCenterY - hh),
                        end = Offset(bx, waveCenterY + hh),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Transparent center tape window
                val winLeft = casLeft + casW * 0.15f
                val winTop = casTop + casH * 0.39f
                val winW = casW * 0.70f
                val winH = casH * 0.25f
                drawRoundRect(
                    color = Color(0xFF070A11),
                    topLeft = Offset(winLeft, winTop),
                    size = Size(winW, winH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(winLeft, winTop),
                    size = Size(winW, winH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Dark magnetic tape ribbon between hubs
                val reelCy = winTop + winH * 0.5f
                val leftHubX = casLeft + casW * 0.31f
                val rightHubX = casLeft + casW * 0.69f
                drawRect(
                    color = Color(0xFF3E2618),
                    topLeft = Offset(leftHubX, reelCy - winH * 0.22f),
                    size = Size(rightHubX - leftHubX, winH * 0.44f)
                )

                // Twin toothed golden cassette reels making smooth final spin
                val hubR = winH * 0.48f
                listOf(leftHubX, rightHubX).forEachIndexed { idx, rx ->
                    val spin = if (idx == 0) state.reelSpinDegrees else state.reelSpinDegrees * 0.9f + 22f
                    drawCircle(color = Color(0xFF0B0E17), radius = hubR * 1.1f, center = Offset(rx, reelCy))
                    drawCircle(
                        color = BrandGoldBright,
                        radius = hubR,
                        center = Offset(rx, reelCy),
                        style = Stroke(width = 2.6.dp.toPx())
                    )
                    rotate(degrees = spin, pivot = Offset(rx, reelCy)) {
                        for (t in 0 until 6) {
                            val a = t * (Math.PI / 3.0)
                            drawLine(
                                color = BrandGoldBright,
                                start = Offset(rx + (hubR * 0.35f * cos(a)).toFloat(), reelCy + (hubR * 0.35f * sin(a)).toFloat()),
                                end = Offset(rx + (hubR * 0.78f * cos(a)).toFloat(), reelCy + (hubR * 0.78f * sin(a)).toFloat()),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Bottom cassette trapezoid bridge & corner screws
                val trap = Path().apply {
                    moveTo(casLeft + casW * 0.25f, casTop + casH)
                    lineTo(casLeft + casW * 0.31f, casTop + casH * 0.77f)
                    lineTo(casLeft + casW * 0.69f, casTop + casH * 0.77f)
                    lineTo(casLeft + casW * 0.75f, casTop + casH)
                    close()
                }
                drawPath(trap, color = Color(0xFF171B24))
                drawPath(trap, color = BrandGold.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

                listOf(
                    Offset(casLeft + casW * 0.08f, casTop + casH * 0.12f),
                    Offset(casLeft + casW * 0.92f, casTop + casH * 0.12f),
                    Offset(casLeft + casW * 0.08f, casTop + casH * 0.89f),
                    Offset(casLeft + casW * 0.92f, casTop + casH * 0.89f),
                    Offset(casLeft + casW * 0.50f, casTop + casH * 0.87f)
                ).forEach { screw ->
                    drawCircle(color = BrandGoldBright, radius = 2.4.dp.toPx(), center = screw)
                    drawCircle(color = Color(0xFF281D07), radius = 1.dp.toPx(), center = screw)
                }

                // Subtle diagonal golden light sweep across the badge (Scene 7 finish)
                val sheenP = state.emblemSweepSheen
                if (sheenP in 0.01f..0.99f) {
                    val clipCircle = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                left = center.x - ringRadius,
                                top = center.y - ringRadius,
                                right = center.x + ringRadius,
                                bottom = center.y + ringRadius
                            )
                        )
                    }
                    clipPath(clipCircle) {
                        val sweepOffset = -w + (w * 2.2f * sheenP)
                        val beam = Path().apply {
                            moveTo(sweepOffset, h)
                            lineTo(sweepOffset + w * 0.22f, h)
                            lineTo(sweepOffset + w * 0.65f, 0f)
                            lineTo(sweepOffset + w * 0.43f, 0f)
                            close()
                        }
                        drawPath(
                            path = beam,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    BrandGoldBright.copy(alpha = 0.24f),
                                    Color.Transparent
                                ),
                                startX = sweepOffset,
                                endX = sweepOffset + w * 0.65f
                            )
                        )
                    }
                }
            }

            // Crisp live "SANGEET" lettering positioned cleanly on the cassette upper navy label
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.36f)
                    .aspectRatio(3.8f)
                    .graphicsLayer {
                        translationY = -23.dp.toPx()
                        translationX = 10.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SANGEET",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp
                    ),
                    color = BrandGoldBright,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Bold SANGEET wordmark below emblem
        Text(
            text = "SANGEET",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                letterSpacing = 6.sp
            ),
            color = BrandGoldBright,
            modifier = Modifier
                .graphicsLayer {
                    alpha = state.brandTitleAlpha
                    val s = 0.92f + 0.08f * state.brandTitleAlpha
                    scaleX = s
                    scaleY = s
                }
                .testTag("startup_brand_title")
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Developer credit cleanly below SANGEET
        Text(
            text = "by Ganeswar",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                letterSpacing = 1.5.sp
            ),
            color = Color(0xFFD6C38E),
            modifier = Modifier
                .graphicsLayer {
                    alpha = state.brandCreditAlpha
                    translationY = (1f - state.brandCreditAlpha) * 14f
                }
                .testTag("startup_developer_credit")
        )
    }
}
