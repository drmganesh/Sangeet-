package com.example.sangeet.ui.screens.player

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.scanner.ArtworkManager
import com.example.sangeet.ui.components.ArtworkPathCache
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Resolves cached/extracted/folder artwork for the track or renders a deterministic
 * vinyl-inspired fallback cover generated uniquely from track metadata when no image exists.
 */
@Composable
fun CassetteLabelArtwork(
    track: AudioTrack,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var resolvedPath by remember(track.id, track.artworkUri) {
        val direct = track.artworkUri.takeIf { it.isNotBlank() }
        val cached = ArtworkPathCache.get(track.id)
        mutableStateOf(direct ?: cached)
    }

    LaunchedEffect(track.id, track.artworkUri) {
        if (resolvedPath.isNullOrBlank() && !ArtworkPathCache.isKnownMissing(track.id)) {
            val artManager = ArtworkManager(context)
            val resolved = artManager.resolveArtwork(track)
            if (!resolved.isNullOrBlank()) {
                ArtworkPathCache.put(track.id, resolved)
                resolvedPath = resolved
            } else {
                ArtworkPathCache.markNoArtwork(track.id)
            }
        }
    }

    val imageRequest = remember(track.id, resolvedPath) {
        if (resolvedPath.isNullOrBlank()) {
            null
        } else {
            val modelData: Any = if (resolvedPath!!.startsWith("content://")) {
                Uri.parse(resolvedPath)
            } else {
                File(resolvedPath!!)
            }
            ImageRequest.Builder(context)
                .data(modelData)
                .memoryCacheKey("track_art_${track.id}")
                .diskCacheKey("track_art_${track.id}")
                .crossfade(160)
                .build()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF141210))
            .border(0.8.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "${track.title} album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Deterministic per-track procedural fallback cover artwork (unique per song/album)
            val seed = remember(track.id, track.title, track.artist, track.album) {
                abs((track.title + "|" + track.artist + "|" + track.album).hashCode())
            }
            val toneA = Color.hsv(hue = (seed % 360).toFloat(), saturation = 0.68f, value = 0.72f)
            val toneB = Color.hsv(hue = ((seed + 42) % 360).toFloat(), saturation = 0.55f, value = 0.34f)
            val monogram = remember(track.title) {
                track.title.trim().take(2).uppercase().ifEmpty { "SG" }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(listOf(toneB, toneA.copy(alpha = 0.85f), Color(0xFF14110E)))
                )
                val c = Offset(size.width * 0.5f, size.height * 0.5f)
                val maxR = min(size.width, size.height) * 0.44f
                for (ring in 1..3) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = maxR * (ring / 3f),
                        center = c,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                drawCircle(
                    color = accentColor.copy(alpha = 0.9f),
                    radius = maxR * 0.28f,
                    center = c
                )
                drawCircle(
                    color = Color(0xFF12100E),
                    radius = maxR * 0.08f,
                    center = c
                )
            }
            Text(
                text = monogram,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                ),
                color = Color.Black,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/**
 * Custom Canvas rotating cassette reel hub with dynamic tape pack thickness
 * representing physical tape winding from supply reel (left) to take-up reel (right).
 */
@Composable
fun CassetteReel(
    rotationDegrees: Float,
    tapeFillFraction: Float,
    palette: CassettePalette,
    modifier: Modifier = Modifier
) {
    val fill = tapeFillFraction.coerceIn(0.18f, 0.96f)
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = min(size.width, size.height) / 2f
        val hubRadius = outerRadius * 0.44f

        // 1. Magnetic tape wound pack around hub
        val packRadius = hubRadius + (outerRadius - hubRadius) * fill
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.tapeRibbon.copy(alpha = 0.95f),
                    Color(0xFF1A110B),
                    palette.tapeRibbon.copy(alpha = 0.85f)
                ),
                center = center,
                radius = packRadius
            ),
            radius = packRadius,
            center = center
        )

        // Concentric ferric tape winding grooves (subtle realism)
        val ringSteps = 4
        for (i in 1..ringSteps) {
            val r = hubRadius + (packRadius - hubRadius) * (i / (ringSteps + 1f))
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = r,
                center = center,
                style = Stroke(width = 0.7.dp.toPx())
            )
        }

        // 2. Rotating spool teeth & hub elements
        rotate(degrees = rotationDegrees, pivot = center) {
            // Flange index marker dot on outer spool rim
            val markerAngleRad = 0.35
            val markerR = (hubRadius + packRadius) * 0.5f
            drawCircle(
                color = palette.reelFlange.copy(alpha = 0.30f),
                radius = 1.6.dp.toPx(),
                center = Offset(
                    center.x + (cos(markerAngleRad) * markerR).toFloat(),
                    center.y + (sin(markerAngleRad) * markerR).toFloat()
                )
            )

            // White/metallic spool hub rim
            drawCircle(
                color = palette.reelFlange,
                radius = hubRadius,
                center = center
            )

            // Dark inner gear pit
            drawCircle(
                color = palette.reelHub,
                radius = hubRadius * 0.66f,
                center = center
            )

            // 6 mechanical drive splines projecting inward
            val toothCount = 6
            for (t in 0 until toothCount) {
                val angle = (t * (360f / toothCount)) * (PI / 180.0)
                val innerR = hubRadius * 0.36f
                val outerR = hubRadius * 0.68f
                val pStart = Offset(
                    center.x + (cos(angle) * innerR).toFloat(),
                    center.y + (sin(angle) * innerR).toFloat()
                )
                val pEnd = Offset(
                    center.x + (cos(angle) * outerR).toFloat(),
                    center.y + (sin(angle) * outerR).toFloat()
                )
                drawLine(
                    color = palette.reelFlange,
                    start = pStart,
                    end = pEnd,
                    strokeWidth = (hubRadius * 0.24f),
                    cap = StrokeCap.Butt
                )
            }

            // Inner metallic spindle collar + 3 radial ribs
            drawCircle(
                color = palette.shellBorder.copy(alpha = 0.65f),
                radius = hubRadius * 0.25f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Central Walkman tape transport mechanism drawn behind/below the cassette window:
 * shows tensioned oxide ribbon running across guide rollers, capstan pin, pinch roller, and read head.
 */
@Composable
fun TapeMechanism(
    isMoving: Boolean,
    tapeOffsetPx: Float,
    palette: CassettePalette,
    accentGlow: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Recessed mechanical bay
        drawRoundRect(
            color = Color(0xFF0A0A0C),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        // Subtle warm lamp illumination behind central counter window
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentGlow.copy(alpha = if (isMoving) 0.24f else 0.10f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.48f),
                radius = w * 0.45f
            )
        )

        // Twin metallic tape guide rollers (left & right)
        val rollerY = h * 0.68f
        val leftGuide = Offset(w * 0.14f, rollerY)
        val rightGuide = Offset(w * 0.86f, rollerY)
        val ribbonY = h * 0.58f

        // Moving magnetic oxide tape path across window bridge
        drawLine(
            color = palette.tapeRibbon,
            start = Offset(w * 0.06f, h * 0.42f),
            end = leftGuide,
            strokeWidth = 2.6.dp.toPx()
        )
        drawLine(
            color = palette.tapeRibbon,
            start = leftGuide,
            end = rightGuide,
            strokeWidth = 2.8.dp.toPx()
        )
        drawLine(
            color = palette.tapeRibbon,
            start = rightGuide,
            end = Offset(w * 0.94f, h * 0.42f),
            strokeWidth = 2.6.dp.toPx()
        )

        // Moving oxide specular shimmer segments along central tape ribbon when playing/seeking
        val segmentSpacing = 12.dp.toPx()
        val shift = tapeOffsetPx % segmentSpacing
        var xPos = leftGuide.x + shift
        while (xPos < rightGuide.x - 4.dp.toPx()) {
            drawLine(
                color = Color(0xFFB48554).copy(alpha = if (isMoving) 0.48f else 0.18f),
                start = Offset(xPos, rollerY),
                end = Offset((xPos + 4.dp.toPx()).coerceAtMost(rightGuide.x), rollerY),
                strokeWidth = 1.4.dp.toPx()
            )
            xPos += segmentSpacing
        }

        // Magnetic Read/Write Head assembly in center bottom
        val headW = w * 0.22f
        val headH = h * 0.22f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF9EA6B2), Color(0xFF474E59))),
            topLeft = Offset(w * 0.5f - headW / 2f, h - headH - 2.dp.toPx()),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // Rubber pinch roller + brushed steel capstan spindle
        drawCircle(
            color = Color(0xFF1F2126),
            radius = 5.dp.toPx(),
            center = Offset(w * 0.72f, ribbonY + 6.dp.toPx())
        )
        drawCircle(
            color = Color(0xFFD0D6DF),
            radius = 2.2.dp.toPx(),
            center = Offset(w * 0.72f, ribbonY + 6.dp.toPx())
        )

        // Guide pins
        listOf(leftGuide, rightGuide).forEach { g ->
            drawCircle(color = Color(0xFFBEC5D0), radius = 3.2.dp.toPx(), center = g)
            drawCircle(color = Color(0xFF2B2E35), radius = 1.4.dp.toPx(), center = g)
        }

        // Graduated transparent meter ticks (100 · 50 · 0)
        val tickY = h * 0.24f
        val tickCount = 9
        for (i in 0..tickCount) {
            val tx = w * 0.20f + (w * 0.60f) * (i.toFloat() / tickCount)
            val tickLen = if (i % 3 == 0) 5.dp.toPx() else 2.8.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.42f),
                start = Offset(tx, tickY),
                end = Offset(tx, tickY + tickLen),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

/**
 * Printed cassette sticker label displaying track's real album artwork, title,
 * artist, album, and Side A/B vintage cassette markings.
 */
@Composable
fun CassetteLabel(
    track: AudioTrack,
    style: CassetteStyle,
    palette: CassettePalette,
    modifier: Modifier = Modifier
) {
    val sideTag = remember(track.id, track.trackNumber) {
        val parity = if (track.trackNumber > 0) track.trackNumber else track.id.toInt()
        if (parity % 2 == 0) "SIDE B" else "SIDE A"
    }
    val albumClean = remember(track.album) {
        track.album.trim().takeIf {
            it.isNotBlank() &&
                !it.equals("Unknown Album", ignoreCase = true) &&
                !it.equals("<unknown>", ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.labelPaper)
            .border(1.dp, palette.shellBorder.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top vintage stripe bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.labelStripe)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = sideTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        ),
                        color = Color.Black
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(palette.labelStripe, palette.labelStripeSecondary)
                            )
                        )
                )

                Text(
                    text = style.badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = palette.labelSubInk,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Main Label Body: Album Artwork Thumbnail + Ruled Song Metadata
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CassetteLabelArtwork(
                    track = track,
                    accentColor = palette.labelStripe,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .testTag("cassette_label_artwork")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = palette.labelInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("cassette_label_title")
                    )

                    // Ruled line 1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .height(0.8.dp)
                            .background(palette.labelSubInk.copy(alpha = 0.35f))
                    )

                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = palette.labelSubInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("cassette_label_artist")
                    )

                    if (albumClean != null) {
                        Text(
                            text = albumClean,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = palette.labelSubInk.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full physical audio cassette cartridge featuring customizable shell design,
 * printed album-art label, dual animated reels, central window, and magnetic head shield.
 */
@Composable
fun CassetteBody(
    track: AudioTrack,
    style: CassetteStyle,
    reelAngles: CassetteReelAnglesState,
    progressFraction: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = remember(style) { style.palette() }
    val clampedProgress = progressFraction.coerceIn(0f, 1f)
    val leftFill = 0.88f - 0.56f * clampedProgress
    val rightFill = 0.32f + 0.56f * clampedProgress

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.56f)
            .shadow(10.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.shellPrimary.copy(alpha = palette.shellAlpha),
                        palette.shellSecondary.copy(alpha = palette.shellAlpha)
                    )
                )
            )
            .border(1.4.dp, palette.shellBorder.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .testTag("cassette_body_${style.name.lowercase()}")
    ) {
        val boxW = maxWidth
        val boxH = maxHeight

        // Subtle shell diagonal molded highlight & 4 corner Phillips screws
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Diagonal plastic/metal sheen
            drawPath(
                path = Path().apply {
                    moveTo(0f, h * 0.25f)
                    lineTo(w * 0.45f, 0f)
                    lineTo(w * 0.62f, 0f)
                    lineTo(0f, h * 0.42f)
                    close()
                },
                color = Color.White.copy(alpha = 0.055f)
            )

            // Recessed inner shell bevel
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.25f),
                topLeft = Offset(w * 0.03f, h * 0.04f),
                size = Size(w * 0.94f, h * 0.72f),
                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // 4 corner Phillips screws + bottom center screw
            val screwR = 3.2.dp.toPx()
            val screws = listOf(
                Offset(10.dp.toPx(), 10.dp.toPx()),
                Offset(w - 10.dp.toPx(), 10.dp.toPx()),
                Offset(10.dp.toPx(), h - 10.dp.toPx()),
                Offset(w - 10.dp.toPx(), h - 10.dp.toPx()),
                Offset(w * 0.5f, h - 11.dp.toPx())
            )
            screws.forEach { s ->
                drawCircle(color = palette.screwMetal, radius = screwR, center = s)
                drawLine(
                    color = Color(0xFF202226),
                    start = Offset(s.x - screwR * 0.6f, s.y),
                    end = Offset(s.x + screwR * 0.6f, s.y),
                    strokeWidth = 0.9.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF202226),
                    start = Offset(s.x, s.y - screwR * 0.6f),
                    end = Offset(s.x, s.y + screwR * 0.6f),
                    strokeWidth = 0.9.dp.toPx()
                )
            }

            // Bottom trapezoid head-shield housing + alignment holes
            val trapPath = Path().apply {
                moveTo(w * 0.22f, h)
                lineTo(w * 0.27f, h * 0.80f)
                lineTo(w * 0.73f, h * 0.80f)
                lineTo(w * 0.78f, h)
                close()
            }
            drawPath(
                path = trapPath,
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.32f), Color.Black.copy(alpha = 0.55f))
                )
            )
            drawPath(
                path = trapPath,
                color = palette.shellBorder.copy(alpha = 0.45f),
                style = Stroke(width = 1.dp.toPx())
            )

            // Alignment pin holes along bottom trapezoid
            listOf(w * 0.32f, w * 0.41f, w * 0.59f, w * 0.68f).forEachIndexed { idx, x ->
                val y = h * 0.90f
                val r = if (idx == 0 || idx == 3) 3.4.dp.toPx() else 2.5.dp.toPx()
                drawCircle(color = Color(0xFF070708), radius = r, center = Offset(x, y))
                drawCircle(
                    color = palette.shellBorder.copy(alpha = 0.35f),
                    radius = r,
                    center = Offset(x, y),
                    style = Stroke(width = 0.7.dp.toPx())
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = boxW * 0.065f, vertical = boxH * 0.075f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Printed Cassette Sticker Label (with real Album Artwork!)
            CassetteLabel(
                track = track,
                style = style,
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.46f)
            )

            Spacer(modifier = Modifier.height(5.dp))

            // Central Transparent Dual-Reel Window
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .weight(0.36f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.windowTint)
                    .border(1.2.dp, palette.shellBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Supply Reel
                    CassetteReel(
                        rotationDegrees = reelAngles.leftAngleDegrees,
                        tapeFillFraction = leftFill,
                        palette = palette,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .testTag("cassette_reel_left")
                    )

                    // Central Moving Tape Bridge & Transport
                    TapeMechanism(
                        isMoving = isPlaying,
                        tapeOffsetPx = reelAngles.tapeOffsetPx,
                        palette = palette,
                        accentGlow = palette.shellBorder,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.86f)
                            .padding(horizontal = 6.dp)
                            .testTag("cassette_tape_mechanism")
                    )

                    // Right Take-up Reel
                    CassetteReel(
                        rotationDegrees = reelAngles.rightAngleDegrees,
                        tapeFillFraction = rightFill,
                        palette = palette,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .testTag("cassette_reel_right")
                    )
                }
            }

            // Bottom spacing reserved for magnetic head shield trapezoid
            Spacer(modifier = Modifier.weight(0.18f))
        }
    }
}
