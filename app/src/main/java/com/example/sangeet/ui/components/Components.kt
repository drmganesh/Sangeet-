package com.example.sangeet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sangeet.scanner.ArtworkManager
import java.io.File
import kotlin.math.*

@Composable
fun TrackArtwork(
    track: AudioTrack?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    contentDescription: String? = "Album Artwork"
) {
    val context = LocalContext.current
    var resolvedPath by remember(track?.id, track?.artworkUri) {
        mutableStateOf<String?>(track?.artworkUri?.takeIf { it.isNotBlank() })
    }

    LaunchedEffect(track?.id) {
        if (track != null && (resolvedPath == null || resolvedPath!!.isBlank())) {
            val artManager = ArtworkManager(context)
            resolvedPath = artManager.resolveArtwork(track)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!resolvedPath.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(if (resolvedPath!!.startsWith("content://")) Uri.parse(resolvedPath) else File(resolvedPath!!))
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun MiniPlayer(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .testTag("mini_player"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle top progress bar
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                TrackArtwork(
                    track = currentTrack,
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${currentTrack.artist} • ${currentTrack.album}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Controls
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * Interactive 360° Radar Canvas for SANGEET Spatial Audio
 * Shows circular rotating soundstage, head/listener center, and real-time sound source position.
 */
@Composable
fun RadarSpatialView(
    azimuthDegrees: Float,
    intensity: Float,
    isRotating: Boolean,
    onManualPositionChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(16.dp)
            .pointerInput(Unit) {
                if (onManualPositionChanged != null) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val touch = change.position
                        val dx = touch.x - center.x
                        val dy = touch.y - center.y
                        var angleDeg = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                        if (angleDeg < 0) angleDeg += 360f
                        onManualPositionChanged(angleDeg)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = min(size.width, size.height) / 2f - 24.dp.toPx()

            // 1. Concentric distance rings
            val ringCount = 4
            for (r in 1..ringCount) {
                val radius = (maxRadius / ringCount) * r
                drawCircle(
                    color = outlineColor.copy(alpha = 0.15f * r / ringCount),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2. Crosshair grid lines
            drawLine(
                color = outlineColor.copy(alpha = 0.2f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = outlineColor.copy(alpha = 0.2f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // 3. Center Listener (Headphone user symbol)
            drawCircle(
                color = primaryColor.copy(alpha = 0.3f),
                radius = 18.dp.toPx(),
                center = center
            )
            drawCircle(
                color = primaryColor,
                radius = 7.dp.toPx(),
                center = center
            )

            // 4. Rotating Sound Source Position
            val rad = Math.toRadians(azimuthDegrees.toDouble())
            val soundDistance = maxRadius * (0.4f + 0.6f * intensity) * (if (isRotating) pulseRadius else 1f)
            val soundX = center.x + (sin(rad) * soundDistance).toFloat()
            val soundY = center.y - (cos(rad) * soundDistance).toFloat()
            val soundPos = Offset(soundX, soundY)

            // Connection ray from listener to sound source
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.6f), secondaryColor),
                    start = center,
                    end = soundPos
                ),
                start = center,
                end = soundPos,
                strokeWidth = 2.5.dp.toPx()
            )

            // Aura ring around sound source
            drawCircle(
                color = secondaryColor.copy(alpha = 0.35f),
                radius = 20.dp.toPx() * (if (isRotating) pulseRadius else 1f),
                center = soundPos
            )
            // Solid sound source core
            drawCircle(
                color = secondaryColor,
                radius = 9.dp.toPx(),
                center = soundPos
            )
        }
    }
}

/**
 * Animated dynamic audio waveform visualization
 */
@Composable
fun WaveformVisualizer(
    isPlaying: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 24,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = width / (barCount * 1.6f)
        val spacing = (width - (barCount * barWidth)) / (barCount - 1)

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount
            val amp = if (isPlaying) {
                0.3f + 0.65f * abs(sin(phase + normalizedX * 4f))
            } else {
                0.15f
            }
            val barHeight = height * amp
            val x = i * (barWidth + spacing)
            val y = (height - barHeight) / 2f

            drawRoundRect(
                color = color.copy(alpha = if (isPlaying) 0.85f else 0.4f),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
