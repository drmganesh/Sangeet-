package com.example.sangeet.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.playback.crossfade.CrossfadeCurves
import com.example.sangeet.playback.crossfade.CrossfadeMode
import com.example.sangeet.playback.crossfade.DjTransitionStyle
import com.example.sangeet.ui.components.CassetteArtwork
import com.example.sangeet.ui.components.CassetteHeaderBadge
import kotlin.math.abs
import kotlin.math.roundToInt

private val DjGold = Color(0xFFE8B429)
private val DjGoldBright = Color(0xFFFFD666)
private val DjPanelBg = Color(0xFF0E0F14)

@Composable
fun DjCrossfadeDeckSheet(
    playbackManager: PlaybackManager,
    onOpenCrossfadeSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by playbackManager.currentTrack.collectAsState()
    val nextQueueTrack by playbackManager.nextQueueTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val cassetteStyle by playbackManager.cassetteStyle.collectAsState()
    val crossfadePrefs by playbackManager.crossfadePrefs.collectAsState()
    val visualState by playbackManager.transitionVisualState.collectAsState()
    val currentAnalysis by playbackManager.currentTrackBeatAnalysis.collectAsState()
    val nextAnalysis by playbackManager.nextTrackBeatAnalysis.collectAsState()

    val outgoingTrack = visualState.outgoingTrack ?: currentTrack
    val incomingTrack = visualState.incomingTrack ?: nextQueueTrack

    val outBpm = visualState.outgoingBpm ?: currentAnalysis?.takeIf { it.isReliable }?.bpm
    val inBpm = visualState.incomingBpm ?: nextAnalysis?.takeIf { it.isReliable }?.bpm

    val transitionActive = visualState.isActive
    val progress = if (transitionActive) visualState.progress.coerceIn(0f, 1f) else 0f
    val outVol = if (transitionActive) visualState.outgoingVolume else crossfadePrefs.djOutgoingVolumeCap
    val inVol = if (transitionActive) visualState.incomingVolume else 0f

    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(90),
        label = "djDeckProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("dj_crossfade_deck_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SANGEET DJ DECK",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        ),
                        color = DjGoldBright
                    )
                    CassetteHeaderBadge(
                        text = if (transitionActive) "MIXING" else crossfadePrefs.mode.displayName.uppercase()
                    )
                }
                Text(
                    text = visualState.beatAlignmentNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (visualState.beatSyncActive) DjGold else Color(0xFF9CA3AF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("dj_deck_status_note")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenCrossfadeSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dj_deck_settings_btn")
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Crossfade Settings", tint = DjGoldBright)
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close DJ Deck", tint = Color(0xFFD1D5DB))
                }
            }
        }

        // Dual Black-and-Gold Cassette Decks (Current Deck A & Incoming Deck B)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DjCassetteDeckCard(
                deckLabel = "DECK A • OUT",
                track = outgoingTrack,
                bpm = outBpm,
                volumeFraction = outVol,
                bassCutDb = if (transitionActive) visualState.outgoingBassAttenuationDb else 0f,
                isSpinning = isPlaying && (!transitionActive || outVol > 0.04f),
                isHighlighted = !transitionActive || progress < 0.55f,
                scaleBoost = if (transitionActive) 1.0f - (animProgress * 0.04f) else 1.0f,
                cassetteStyle = cassetteStyle,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dj_deck_a_card")
            )

            DjCassetteDeckCard(
                deckLabel = "DECK B • IN",
                track = incomingTrack,
                bpm = inBpm,
                volumeFraction = inVol,
                bassCutDb = if (transitionActive) visualState.incomingBassAttenuationDb else 0f,
                isSpinning = transitionActive && isPlaying && inVol > 0.02f,
                isHighlighted = transitionActive && progress >= 0.35f,
                scaleBoost = if (transitionActive) 0.96f + (animProgress * 0.04f) else 0.97f,
                cassetteStyle = cassetteStyle,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dj_deck_b_card")
            )
        }

        // Real-Time Transition Progress, Curve & Fader Console
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DjPanelBg,
            border = BorderStroke(
                width = 1.dp,
                color = if (transitionActive) DjGoldBright else DjGold.copy(alpha = 0.28f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (crossfadePrefs.mode == CrossfadeMode.DJ_STYLE) {
                            "${crossfadePrefs.djTransitionStyle.displayName} • ${crossfadePrefs.djDurationSeconds}s"
                        } else if (crossfadePrefs.mode == CrossfadeMode.ADVANCED) {
                            "Advanced Blend • ${crossfadePrefs.advancedDurationSeconds}s"
                        } else {
                            "Crossfade Off (Tap Mix Now to audition)"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = DjGoldBright
                    )
                    val totalSec = if (transitionActive) visualState.totalDurationMs / 1000f else crossfadePrefs.djDurationSeconds.toFloat()
                    val elapsedSec = if (transitionActive) visualState.elapsedMs / 1000f else 0f
                    Text(
                        text = String.format("%.1fs / %.1fs", elapsedSec, totalSec),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFFD1D5DB),
                        modifier = Modifier.testTag("dj_transition_timer")
                    )
                }

                // Visual Crossfader Track Bar
                LinearProgressIndicator(
                    progress = { animProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("dj_transition_progress"),
                    color = DjGoldBright,
                    trackColor = Color(0xFF1F222A)
                )

                // Live Mixing Curves Canvas with moving playhead marker
                CrossfadeCurveCanvas(
                    mode = if (crossfadePrefs.mode == CrossfadeMode.OFF) CrossfadeMode.DJ_STYLE else crossfadePrefs.mode,
                    style = crossfadePrefs.djTransitionStyle,
                    equalPowerEnabled = crossfadePrefs.equalPowerCurvesEnabled,
                    activeProgress = if (transitionActive) animProgress else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                )

                // Outgoing vs Incoming Volume Bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VolumeMeterBar(
                        label = "OUT A",
                        fraction = outVol,
                        bassCutDb = if (transitionActive) visualState.outgoingBassAttenuationDb else 0f,
                        tint = DjGold,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dj_outgoing_vol_meter")
                    )
                    VolumeMeterBar(
                        label = "IN B",
                        fraction = inVol,
                        bassCutDb = if (transitionActive) visualState.incomingBassAttenuationDb else 0f,
                        tint = DjGoldBright,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dj_incoming_vol_meter")
                    )
                }
            }
        }

        // Transition Style Quick-Preset Strip
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TRANSITION STYLE PRESET",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFF9CA3AF)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DjTransitionStyle.entries.forEach { preset ->
                    val selected = crossfadePrefs.djTransitionStyle == preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (crossfadePrefs.mode == CrossfadeMode.OFF) {
                                playbackManager.setCrossfadeMode(CrossfadeMode.DJ_STYLE)
                            }
                            playbackManager.setDjTransitionStyle(preset)
                        },
                        label = {
                            Text(
                                text = preset.displayName,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DjGold.copy(alpha = 0.22f),
                            selectedLabelColor = DjGoldBright
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = DjGoldBright,
                            borderColor = Color(0xFF2D313B)
                        ),
                        modifier = Modifier.testTag("dj_style_chip_${preset.name.lowercase()}")
                    )
                }
            }
        }

        // Action Controls: Manual Transition Trigger + Play/Pause
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (transitionActive) {
                        playbackManager.abortActiveTransition(restoreActiveVolume = true)
                    } else {
                        if (crossfadePrefs.mode == CrossfadeMode.OFF) {
                            playbackManager.setCrossfadeMode(CrossfadeMode.DJ_STYLE)
                        }
                        playbackManager.triggerManualTransition(isPreview = false)
                    }
                },
                enabled = transitionActive || incomingTrack != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transitionActive) Color(0xFF7F1D1D) else DjGold,
                    contentColor = if (transitionActive) Color.White else Color.Black
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("dj_manual_trigger_btn")
            ) {
                Icon(
                    imageVector = if (transitionActive) Icons.Rounded.StopCircle else Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (transitionActive) "Abort Mix" else "Mix Now",
                    fontWeight = FontWeight.ExtraBold
                )
            }

            OutlinedButton(
                onClick = { playbackManager.togglePlayPause() },
                border = BorderStroke(1.dp, DjGold.copy(alpha = 0.6f)),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("dj_play_pause_btn")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = DjGoldBright
                )
            }
        }
    }
}

@Composable
private fun DjCassetteDeckCard(
    deckLabel: String,
    track: AudioTrack?,
    bpm: Float?,
    volumeFraction: Float,
    bassCutDb: Float,
    isSpinning: Boolean,
    isHighlighted: Boolean,
    scaleBoost: Float,
    cassetteStyle: CassetteStyle,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) DjGoldBright else DjGold.copy(alpha = 0.25f),
        animationSpec = tween(220),
        label = "djDeckBorder"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DjPanelBg,
        border = BorderStroke(if (isHighlighted) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.graphicsLayer {
            scaleX = scaleBoost
            scaleY = scaleBoost
        }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deckLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    ),
                    color = if (isHighlighted) DjGoldBright else Color(0xFF9CA3AF)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (bpm != null) DjGold.copy(alpha = 0.16f) else Color(0xFF1A1C23),
                    border = BorderStroke(0.6.dp, if (bpm != null) DjGold else Color(0xFF323642))
                ) {
                    Text(
                        text = if (bpm != null) "${bpm.roundToInt()} BPM" else "BPM --",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = if (bpm != null) DjGoldBright else Color(0xFF9CA3AF),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            CassetteArtwork(
                track = track,
                isPlaying = isSpinning,
                showMetadataOnLabel = true,
                compact = true,
                cassetteStyle = cassetteStyle,
                crossfadeDurationMs = 150,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.56f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track?.title ?: "Queue End",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF9FAFB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "No upcoming track",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOL ${(volumeFraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    ),
                    color = DjGoldBright
                )
                if (abs(bassCutDb) >= 0.5f) {
                    Text(
                        text = "BASS ${bassCutDb.roundToInt()}dB",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        ),
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeMeterBar(
    label: String,
    fraction: Float,
    bassCutDb: Float,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFE5E7EB)
            )
            val bassNote = if (abs(bassCutDb) >= 0.5f) " • ${bassCutDb.roundToInt()}dB" else ""
            Text(
                text = "${(fraction * 100).roundToInt()}%$bassNote",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = tint
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = tint,
            trackColor = Color(0xFF1D2029)
        )
    }
}

@Composable
fun CrossfadeCurveCanvas(
    mode: CrossfadeMode,
    style: DjTransitionStyle,
    equalPowerEnabled: Boolean,
    activeProgress: Float? = null,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF07080B))
            .border(0.7.dp, DjGold.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
    ) {
        val padX = 8.dp.toPx()
        val padY = 8.dp.toPx()
        val w = (size.width - padX * 2).coerceAtLeast(1f)
        val h = (size.height - padY * 2).coerceAtLeast(1f)

        // Mid-grid guide line
        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(padX, padY + h * 0.5f),
            end = Offset(padX + w, padY + h * 0.5f),
            strokeWidth = 1.dp.toPx()
        )

        val steps = 40
        val outPath = Path()
        val inPath = Path()

        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val g = CrossfadeCurves.computeGains(
                progress = t,
                mode = mode,
                style = style,
                equalPowerEnabled = equalPowerEnabled
            )
            val x = padX + t * w
            val yOut = padY + (1f - g.outgoingGain) * h
            val yIn = padY + (1f - g.incomingGain) * h

            if (i == 0) {
                outPath.moveTo(x, yOut)
                inPath.moveTo(x, yIn)
            } else {
                outPath.lineTo(x, yOut)
                inPath.lineTo(x, yIn)
            }
        }

        drawPath(
            path = outPath,
            color = Color(0xFFD97706),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = inPath,
            color = DjGoldBright,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )

        if (activeProgress != null) {
            val markerX = padX + activeProgress.coerceIn(0f, 1f) * w
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(markerX, 2.dp.toPx()),
                end = Offset(markerX, size.height - 2.dp.toPx()),
                strokeWidth = 1.6.dp.toPx()
            )
        }
    }
}
