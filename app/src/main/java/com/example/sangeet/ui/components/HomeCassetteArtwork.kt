package com.example.sangeet.ui.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.scanner.ArtworkManager
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val CassetteGold = Color(0xFFE8B429)
private val CassetteGoldBright = Color(0xFFFFD666)
private val CassetteGoldDark = Color(0xFF8C640F)
private val CassetteBlackTop = Color(0xFF212329)
private val CassetteBlackBottom = Color(0xFF0B0C0F)
private val CassetteWindowDark = Color(0xD908090C)
private val CassetteTapeBrown = Color(0xFF3E2617)
private val CassetteHubCream = Color(0xFFECE4D2)
private val CassetteScrewSilver = Color(0xFFA6ADB8)

/**
 * Battery-efficient reel angle provider: only creates an InfiniteTransition
 * when this cassette is both actively playing AND visible on screen.
 */
@Composable
fun rememberVisibleCassetteReelAngle(
    isActivelyPlayingAndVisible: Boolean,
    seedAngleDegrees: Float = 0f
): State<Float> {
    return if (isActivelyPlayingAndVisible) {
        val transition = rememberInfiniteTransition(label = "home_cassette_reel")
        transition.animateFloat(
            initialValue = seedAngleDegrees,
            targetValue = seedAngleDegrees + 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "homeReelSpin"
        )
    } else {
        remember(seedAngleDegrees) { mutableFloatStateOf(seedAngleDegrees) }
    }
}

/**
 * Resolves the track's actual local album artwork (embedded metadata, cached DB entry,
 * or sibling folder image) and renders it seamlessly into the cassette label surface,
 * preserving original artwork colors & composition with a deterministic per-song
 * artwork fallback when no image exists on disk.
 */
@Composable
fun CassettePrintedArtImage(
    track: AudioTrack?,
    modifier: Modifier = Modifier,
    crossfadeDurationMs: Int = 220
) {
    val context = LocalContext.current
    var resolvedPath by remember(track?.id, track?.artworkUri) {
        val direct = track?.artworkUri?.takeIf { it.isNotBlank() }
        val cached = track?.id?.let { ArtworkPathCache.get(it) }
        mutableStateOf(direct ?: cached)
    }

    LaunchedEffect(track?.id, track?.artworkUri) {
        if (track != null && resolvedPath.isNullOrBlank() && !ArtworkPathCache.isKnownMissing(track.id)) {
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

    val imageRequest = remember(track?.id, resolvedPath, crossfadeDurationMs) {
        if (resolvedPath.isNullOrBlank() || track == null) {
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
                .crossfade(crossfadeDurationMs.coerceAtLeast(0))
                .build()
        }
    }

    Box(modifier = modifier) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = track?.let { "${it.title} album cover" } ?: "Cassette Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Deterministic unique artwork palette & groove geometry seeded by track identity
            val seed = remember(track?.id, track?.title, track?.artist, track?.album) {
                val raw = "${track?.title.orEmpty()}|${track?.artist.orEmpty()}|${track?.album.orEmpty()}"
                abs(raw.hashCode())
            }
            val hue1 = (seed % 360).toFloat()
            val hue2 = ((seed + 58) % 360).toFloat()
            val c1 = Color.hsv(hue = hue1, saturation = 0.72f, value = 0.76f)
            val c2 = Color.hsv(hue = hue2, saturation = 0.64f, value = 0.42f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(c1, c2, Color(0xFF151310)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )
                val center = Offset(size.width * 0.76f, size.height * 0.55f)
                val rMax = min(size.width, size.height) * 0.46f
                for (ring in 1..3) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = rMax * (ring / 3f),
                        center = center,
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                }
                drawCircle(
                    color = CassetteGoldBright.copy(alpha = 0.85f),
                    radius = rMax * 0.24f,
                    center = center
                )
            }
        }
    }
}

/**
 * Vintage black and golden-yellow physical cassette shell with 3D plastic texture,
 * recessed label deck, Phillips screws, and bottom trapezoidal magnetic-head housing.
 */
@Composable
fun CassetteShell(
    cornerRadius: Dp = 10.dp,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(CassetteBlackTop, Color(0xFF14161B), CassetteBlackBottom)
                )
            )
            .border(
                width = if (compact) 1.dp else 1.4.dp,
                brush = Brush.linearGradient(listOf(CassetteGoldBright, CassetteGoldDark, CassetteGold)),
                shape = shape
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle diagonal molded plastic reflection sheen
            drawPath(
                path = Path().apply {
                    moveTo(0f, h * 0.22f)
                    lineTo(w * 0.36f, 0f)
                    lineTo(w * 0.54f, 0f)
                    lineTo(0f, h * 0.38f)
                    close()
                },
                color = Color.White.copy(alpha = 0.055f)
            )

            // Recessed inner shell border
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.38f),
                topLeft = Offset(w * 0.03f, h * 0.04f),
                size = Size(w * 0.94f, h * 0.74f),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = Stroke(width = 0.9.dp.toPx())
            )

            if (!compact) {
                // 4 corner + 1 bottom center Phillips screws
                val screwR = 2.4.dp.toPx()
                val inset = 7.5.dp.toPx()
                val screws = listOf(
                    Offset(inset, inset),
                    Offset(w - inset, inset),
                    Offset(inset, h - inset),
                    Offset(w - inset, h - inset),
                    Offset(w * 0.5f, h - inset)
                )
                screws.forEach { s ->
                    drawCircle(color = CassetteScrewSilver, radius = screwR, center = s)
                    drawLine(
                        color = Color(0xFF1C1E22),
                        start = Offset(s.x - screwR * 0.6f, s.y),
                        end = Offset(s.x + screwR * 0.6f, s.y),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF1C1E22),
                        start = Offset(s.x, s.y - screwR * 0.6f),
                        end = Offset(s.x, s.y + screwR * 0.6f),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
            }

            // Bottom trapezoid head shield ridge + guide holes
            val trapPath = Path().apply {
                moveTo(w * 0.22f, h)
                lineTo(w * 0.27f, h * 0.81f)
                lineTo(w * 0.73f, h * 0.81f)
                lineTo(w * 0.78f, h)
                close()
            }
            drawPath(
                path = trapPath,
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.72f))
                )
            )
            drawPath(
                path = trapPath,
                color = CassetteGold.copy(alpha = 0.40f),
                style = Stroke(width = 0.8.dp.toPx())
            )
            if (!compact) {
                listOf(w * 0.33f, w * 0.42f, w * 0.58f, w * 0.67f).forEachIndexed { idx, x ->
                    val y = h * 0.90f
                    val r = if (idx == 0 || idx == 3) 2.3.dp.toPx() else 1.7.dp.toPx()
                    drawCircle(color = Color(0xFF040507), radius = r, center = Offset(x, y))
                }
            }
        }
        content()
    }
}

/**
 * Printed cassette paper sticker label where the track's actual album artwork is
 * printed directly into the label surface, preserving original artwork colors.
 */
@Composable
fun CassetteLabel(
    track: AudioTrack?,
    showMetadata: Boolean,
    compact: Boolean,
    crossfadeDurationMs: Int = 220,
    modifier: Modifier = Modifier
) {
    val labelShape = RoundedCornerShape(if (compact) 4.dp else 6.dp)
    val sideTag = remember(track?.id, track?.trackNumber) {
        val n = track?.trackNumber?.takeIf { it > 0 } ?: (track?.id?.toInt() ?: 1)
        if (n % 2 == 0) "SIDE B" else "SIDE A"
    }

    Box(
        modifier = modifier
            .clip(labelShape)
            .border(
                width = if (compact) 0.7.dp else 1.dp,
                color = CassetteGold.copy(alpha = 0.68f),
                shape = labelShape
            )
    ) {
        // 1. Real album artwork printed full-bleed into the cassette sticker label
        CassettePrintedArtImage(
            track = track,
            crossfadeDurationMs = crossfadeDurationMs,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Subtle matte printed paper grain & edge vignette preserving true artwork center colors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (showMetadata) 0.46f else 0.14f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (showMetadata) 0.62f else 0.22f)
                        )
                    )
                )
        )

        // 3. Vintage golden cassette header trim + optional track title/artist printed into label
        if (!compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(CassetteGold)
                            .padding(horizontal = 4.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = sideTag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color.Black
                        )
                    }
                    Text(
                        text = "SANGEET C-90",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }

                if (showMetadata && track != null) {
                    Column {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = CassetteGoldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            // Compact mini cassette stripe accent along top edge of printed label
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(CassetteGold.copy(alpha = 0.85f))
            )
        }
    }
}

/**
 * Dual circular mechanical cassette reels and dark magnetic tape bridge drawn via Canvas.
 */
@Composable
fun CassetteReels(
    rotationDegrees: Float,
    isPlaying: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val reelRadius = (min(h * 0.44f, w * 0.21f)).coerceAtLeast(3.dp.toPx())
        val leftCenter = Offset(w * 0.22f, h * 0.52f)
        val rightCenter = Offset(w * 0.78f, h * 0.52f)

        // Visible oxide tape ribbon stretched between left and right reels
        val ribbonThickness = if (compact) 2.dp.toPx() else 3.dp.toPx()
        drawLine(
            color = CassetteTapeBrown,
            start = Offset(leftCenter.x, leftCenter.y + reelRadius * 0.55f),
            end = Offset(rightCenter.x, rightCenter.y + reelRadius * 0.55f),
            strokeWidth = ribbonThickness
        )
        drawLine(
            color = Color(0xFF7D5333).copy(alpha = 0.45f),
            start = Offset(leftCenter.x + reelRadius * 0.6f, leftCenter.y),
            end = Offset(rightCenter.x - reelRadius * 0.6f, rightCenter.y),
            strokeWidth = ribbonThickness * 0.55f
        )

        // Draw left & right reels independently
        listOf(leftCenter to 0.92f, rightCenter to 0.78f).forEach { (center, packFactor) ->
            // Outer wound tape pack
            val packR = reelRadius * (0.62f + 0.34f * packFactor)
            drawCircle(
                color = CassetteTapeBrown,
                radius = packR,
                center = center
            )

            // Rotating 6-tooth hub
            val hubR = reelRadius * 0.60f
            rotate(degrees = rotationDegrees, pivot = center) {
                drawCircle(color = CassetteHubCream, radius = hubR, center = center)
                drawCircle(color = Color(0xFF1F2229), radius = hubR * 0.64f, center = center)
                val teeth = 6
                for (i in 0 until teeth) {
                    val rad = (i * (360f / teeth)) * (PI / 180.0)
                    val p0 = Offset(
                        center.x + (cos(rad) * (hubR * 0.34f)).toFloat(),
                        center.y + (sin(rad) * (hubR * 0.34f)).toFloat()
                    )
                    val p1 = Offset(
                        center.x + (cos(rad) * (hubR * 0.68f)).toFloat(),
                        center.y + (sin(rad) * (hubR * 0.68f)).toFloat()
                    )
                    drawLine(
                        color = CassetteHubCream,
                        start = p0,
                        end = p1,
                        strokeWidth = (hubR * 0.25f).coerceAtLeast(1.dp.toPx()),
                        cap = StrokeCap.Butt
                    )
                }
            }

            if (isPlaying && !compact) {
                drawCircle(
                    color = CassetteGoldBright.copy(alpha = 0.45f),
                    radius = hubR * 0.22f,
                    center = center
                )
            }
        }
    }
}

/**
 * Transparent smoked-acrylic cassette window framing the dual rotating reels and tape bridge.
 */
@Composable
fun CassetteWindow(
    rotationDegrees: Float,
    isPlaying: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val windowShape = RoundedCornerShape(if (compact) 4.dp else 7.dp)
    Box(
        modifier = modifier
            .clip(windowShape)
            .background(CassetteWindowDark)
            .border(
                width = if (compact) 0.7.dp else 1.dp,
                color = CassetteGold.copy(alpha = 0.55f),
                shape = windowShape
            )
    ) {
        CassetteReels(
            rotationDegrees = rotationDegrees,
            isPlaying = isPlaying,
            compact = compact,
            modifier = Modifier.fillMaxSize()
        )
        // Glass specular reflection & graduated tape counter ticks
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.14f, h)
                    lineTo(w * 0.32f, 0f)
                    lineTo(w * 0.40f, 0f)
                    lineTo(w * 0.22f, h)
                    close()
                },
                color = Color.White.copy(alpha = 0.065f)
            )
            if (!compact) {
                for (i in 0..6) {
                    val tx = w * 0.34f + w * 0.32f * (i / 6f)
                    val len = if (i % 3 == 0) 3.5.dp.toPx() else 2.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.38f),
                        start = Offset(tx, h * 0.12f),
                        end = Offset(tx, h * 0.12f + len),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
            }
        }
    }
}

/**
 * Complete reusable physical Cassette Tape graphic combining CassetteShell,
 * CassetteLabel (with real printed album art), and CassetteWindow (reels & tape).
 */
@Composable
fun CassetteArtwork(
    track: AudioTrack?,
    isPlaying: Boolean = false,
    reelAngleDegrees: Float = 0f,
    showMetadataOnLabel: Boolean = true,
    compact: Boolean = false,
    crossfadeDurationMs: Int = 220,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val padH = if (compact) 4.dp else (maxWidth * 0.065f).coerceIn(6.dp, 12.dp)
        val padV = if (compact) 3.dp else (maxHeight * 0.075f).coerceIn(5.dp, 10.dp)
        CassetteShell(
            cornerRadius = if (compact) 7.dp else 12.dp,
            compact = compact,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padH, vertical = padV),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CassetteLabel(
                    track = track,
                    showMetadata = showMetadataOnLabel,
                    compact = compact,
                    crossfadeDurationMs = crossfadeDurationMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (compact) 0.50f else 0.48f)
                )
                Spacer(modifier = Modifier.height(if (compact) 2.5.dp else 4.dp))
                CassetteWindow(
                    rotationDegrees = reelAngleDegrees,
                    isPlaying = isPlaying,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth(if (compact) 0.88f else 0.86f)
                        .weight(if (compact) 0.34f else 0.36f)
                )
                Spacer(modifier = Modifier.weight(0.16f))
            }
        }
    }
}

/**
 * Miniature cassette presentation for MiniPlayer and compact Home list rows.
 */
@Composable
fun MiniCassetteArtwork(
    track: AudioTrack?,
    isPlaying: Boolean = false,
    reelAngleDegrees: Float = 0f,
    crossfadeDurationMs: Int = 200,
    modifier: Modifier = Modifier
) {
    CassetteArtwork(
        track = track,
        isPlaying = isPlaying,
        reelAngleDegrees = reelAngleDegrees,
        showMetadataOnLabel = false,
        compact = true,
        crossfadeDurationMs = crossfadeDurationMs,
        modifier = modifier
            .aspectRatio(1.56f)
            .shadow(6.dp, RoundedCornerShape(7.dp))
    )
}

/**
 * Reusable cassette card presentation used for Quick Picks, Local Mix, and Home carousels.
 */
@Composable
fun CassetteArtworkCard(
    track: AudioTrack,
    isCurrentlyPlaying: Boolean,
    isAudioPlaying: Boolean,
    isVisible: Boolean,
    crossfadeDurationMs: Int,
    modifier: Modifier = Modifier
) {
    val activeSpin = isCurrentlyPlaying && isAudioPlaying && isVisible
    val seedAngle = remember(track.id) { ((track.id * 37L) % 360L).toFloat() }
    val reelAngle by rememberVisibleCassetteReelAngle(
        isActivelyPlayingAndVisible = activeSpin,
        seedAngleDegrees = seedAngle
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0A0B0E),
        border = BorderStroke(
            width = if (isCurrentlyPlaying) 1.5.dp else 0.9.dp,
            color = if (isCurrentlyPlaying) CassetteGoldBright else CassetteGold.copy(alpha = 0.25f)
        ),
        shadowElevation = if (isCurrentlyPlaying) 10.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            CassetteArtwork(
                track = track,
                isPlaying = activeSpin,
                reelAngleDegrees = reelAngle,
                showMetadataOnLabel = true,
                compact = false,
                crossfadeDurationMs = crossfadeDurationMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.54f)
            )
        }
    }
}

/**
 * Oversized Featured Spotlight Cassette Card keeping the Featured Spotlight badge,
 * track info, page indicator dots, and golden Play button while presenting the
 * track's album artwork printed directly onto an oversized 3D-lit cassette centerpiece.
 */
@Composable
fun FeaturedCassetteCard(
    displayTrack: AudioTrack,
    isFeaturedTrackPlaying: Boolean,
    isSpotlightVisible: Boolean,
    isAppResumed: Boolean,
    motion: EffectiveArtworkMotion,
    kenBurnsZoom: Float,
    kenBurnsPanX: Float,
    kenBurnsPanY: Float,
    scrollParallaxNormalized: Float,
    lightSweep: Float,
    featuredCount: Int,
    featuredIndex: Int,
    metadataAlpha: Float,
    onPlayFeatured: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldSpinReels = isFeaturedTrackPlaying && isSpotlightVisible && isAppResumed
    val seedAngle = remember(displayTrack.id) { ((displayTrack.id * 53L) % 360L).toFloat() }
    val reelAngle by rememberVisibleCassetteReelAngle(
        isActivelyPlayingAndVisible = shouldSpinReels,
        seedAngleDegrees = seedAngle
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Soft blurred/tinted album artwork backdrop aura inside AMOLED frame
        CassettePrintedArtImage(
            track = displayTrack,
            crossfadeDurationMs = 0,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val activeZoom = if (motion.kenBurnsActive) kenBurnsZoom else 1.08f
                    scaleX = activeZoom
                    scaleY = activeZoom
                    translationX = if (motion.kenBurnsActive) kenBurnsPanX else 0f
                    translationY = (if (motion.kenBurnsActive) kenBurnsPanY else 0f) +
                        (if (motion.parallaxActive) scrollParallaxNormalized * motion.parallaxMaxShiftPx else 0f)
                    alpha = 0.28f
                }
        )

        // Warm AMOLED & golden-yellow studio spotlight vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A0906).copy(alpha = 0.72f),
                            Color(0xFF050506).copy(alpha = 0.62f),
                            Color.Black.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        if (motion.ambientLightingActive && isSpotlightVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                CassetteGold.copy(alpha = motion.ambientLightAlpha * 0.45f),
                                Color.White.copy(alpha = motion.ambientLightAlpha * 0.16f),
                                Color.Transparent
                            ),
                            start = Offset((lightSweep - 0.5f) * 900f, 0f),
                            end = Offset((lightSweep + 0.4f) * 900f, 650f)
                        )
                    )
            )
        }

        // 2 & 3. Clean Non-Overlapping Column: Badge/Dots + Oversized 3D Cassette + Track Info/Play CTA
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CassetteGold
                ) {
                    Text(
                        text = "FEATURED SPOTLIGHT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color.Black,
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
                                        if (activeDot) CassetteGoldBright else Color.White.copy(alpha = 0.42f)
                                    )
                            )
                        }
                    }
                }
            }

            // Oversized 3D Cassette Tape Centerpiece
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CassetteArtwork(
                    track = displayTrack,
                    isPlaying = shouldSpinReels,
                    reelAngleDegrees = reelAngle,
                    showMetadataOnLabel = true,
                    compact = false,
                    crossfadeDurationMs = 0,
                    modifier = Modifier
                        .fillMaxHeight(0.96f)
                        .aspectRatio(1.56f)
                        .graphicsLayer {
                            rotationZ = if (motion.isEnabled) -1.8f else 0f
                            translationY = if (motion.parallaxActive) scrollParallaxNormalized * 4f else 0f
                        }
                        .shadow(16.dp, RoundedCornerShape(12.dp))
                        .testTag("featured_cassette_artwork")
                )
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
                        color = CassetteGoldBright.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("spotlight_subtitle")
                    )
                }

                FilledIconButton(
                    onClick = onPlayFeatured,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CassetteGold
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("spotlight_play_button")
                ) {
                    Icon(
                        imageVector = if (isFeaturedTrackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isFeaturedTrackPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
