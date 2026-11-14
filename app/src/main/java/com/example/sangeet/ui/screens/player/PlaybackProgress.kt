package com.example.sangeet.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.CassetteStyle

private val AmberAccent = Color(0xFFE8B429)
private val AmberBright = Color(0xFFFFD666)

fun formatWalkmanTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format("%02d:%02d", minutes, seconds)
}

fun formatMs(ms: Long): String = formatWalkmanTime(ms)

/**
 * Song title, artist, album metadata, seek slider with smooth scrubbing,
 * and elapsed/total time readout positioned below the Walkman body.
 */
@Composable
fun PlaybackProgress(
    track: AudioTrack,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(0L)
    val actualFraction = if (safeDuration > 0L) (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f

    var isScrubbing by remember(track.id) { mutableStateOf(false) }
    var scrubFraction by remember(track.id) { mutableFloatStateOf(actualFraction) }

    LaunchedEffect(actualFraction, isScrubbing) {
        if (!isScrubbing) {
            scrubFraction = actualFraction
        }
    }

    val shownPositionMs = if (isScrubbing) {
        (scrubFraction * safeDuration).toLong()
    } else {
        positionMs
    }

    val albumLine = remember(track.artist, track.album) {
        val cleanAlbum = track.album.trim().takeIf {
            it.isNotBlank() &&
                !it.equals("Unknown Album", ignoreCase = true) &&
                !it.equals("<unknown>", ignoreCase = true)
        }
        if (cleanAlbum != null) "${track.artist} • $cleanAlbum" else track.artist
    }

    Column(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp
            ),
            color = Color(0xFFF9FAFB),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("walkman_track_title")
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = albumLine,
            style = MaterialTheme.typography.bodyMedium,
            color = AmberBright.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("walkman_track_subtitle")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Slider(
            value = scrubFraction.coerceIn(0f, 1f),
            onValueChange = { fraction ->
                isScrubbing = true
                scrubFraction = fraction
            },
            onValueChangeFinished = {
                val target = (scrubFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                isScrubbing = false
                onSeek(target)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_seek_slider"),
            colors = SliderDefaults.colors(
                thumbColor = AmberBright,
                activeTrackColor = AmberAccent,
                inactiveTrackColor = Color(0xFF2B2D33)
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatWalkmanTime(shownPositionMs),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = AmberBright,
                modifier = Modifier.testTag("walkman_elapsed_time")
            )
            Text(
                text = formatWalkmanTime(safeDuration),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = Color(0xFF9CA3AF),
                modifier = Modifier.testTag("walkman_total_time")
            )
        }
    }
}

/**
 * Clean secondary utility controls bar below progress:
 * Shuffle, Queue, 15-Band Equalizer, Offline Lyrics, and Repeat.
 */
@Composable
fun WalkmanUtilityBar(
    isShuffle: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101114),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24262D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("walkman_shuffle_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Toggle Shuffle",
                    tint = if (isShuffle) AmberBright else Color(0xFF9CA3AF)
                )
            }

            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("player_queue_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = "Playing Queue",
                    tint = Color(0xFFE5E7EB)
                )
            }

            IconButton(
                onClick = onOpenEqualizer,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("player_eq_shortcut")
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "15-Band Equalizer",
                    tint = AmberAccent
                )
            }

            IconButton(
                onClick = onOpenLyrics,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("player_lyrics_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = "Lyrics",
                    tint = Color(0xFFE5E7EB)
                )
            }

            IconButton(
                onClick = onCycleRepeat,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("walkman_repeat_btn")
            ) {
                val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat One"
                        Player.REPEAT_MODE_ALL -> "Repeat All"
                        else -> "Repeat Off"
                    },
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) AmberBright else Color(0xFF9CA3AF)
                )
            }
        }
    }
}

/**
 * Modal bottom sheet allowing instant customization of the physical Cassette Shell appearance.
 */
@Composable
fun CassetteStyleSheetContent(
    selectedStyle: CassetteStyle,
    onSelectStyle: (CassetteStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "CASSETTE SHELL EDITION",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            ),
            color = AmberAccent
        )
        Text(
            text = "Customize Cassette Appearance",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        CassetteStyle.entries.forEach { style ->
            val palette = remember(style) { style.palette() }
            val isSelected = style == selectedStyle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectStyle(style) }
                    .testTag("cassette_style_option_${style.name.lowercase()}"),
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) AmberAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(1.4.dp, AmberAccent)
                } else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini visual cassette swatch
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        palette.shellPrimary.copy(alpha = palette.shellAlpha),
                                        palette.shellSecondary.copy(alpha = palette.shellAlpha)
                                    )
                                )
                            )
                            .border(1.dp, palette.shellBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(palette.reelFlange)
                            )
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(palette.reelFlange)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.displayName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = style.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = AmberAccent
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
