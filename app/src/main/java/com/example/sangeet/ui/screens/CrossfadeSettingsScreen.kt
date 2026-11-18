package com.example.sangeet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.playback.crossfade.CrossfadeMode
import com.example.sangeet.playback.crossfade.DjTransitionStyle
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import com.example.sangeet.ui.screens.player.CrossfadeCurveCanvas
import com.example.sangeet.ui.screens.player.DjCrossfadeDeckSheet
import kotlin.math.roundToInt

private val GoldAccent = Color(0xFFE8B429)
private val GoldBright = Color(0xFFFFD666)
private val CardDark = Color(0xFF101218)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeSettingsScreen(
    playbackManager: PlaybackManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by playbackManager.crossfadePrefs.collectAsState()
    val visualState by playbackManager.transitionVisualState.collectAsState()
    val currentAnalysis by playbackManager.currentTrackBeatAnalysis.collectAsState()
    val nextAnalysis by playbackManager.nextTrackBeatAnalysis.collectAsState()
    val queue by playbackManager.queue.collectAsState()

    var showDjDeckSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("crossfade_settings_screen"),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Playback • Crossfade",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = prefs.mode.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldBright
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("crossfade_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Settings"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            playbackManager.resetCrossfadeSettings()
                            Toast.makeText(context, "Crossfade settings reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("crossfade_reset_btn")
                    ) {
                        Text("Reset", color = GoldBright)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("crossfade_settings_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Mode Selection (Off / Advanced Crossfade / DJ-Style Crossfade)
            item {
                CassetteSectionCard(highlighted = prefs.mode != CrossfadeMode.OFF) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CassetteSectionHeader(
                            title = "Transition Mode",
                            badgeText = prefs.mode.name
                        )
                        CrossfadeMode.entries.forEach { mode ->
                            val selected = prefs.mode == mode
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) GoldAccent.copy(alpha = 0.14f) else CardDark,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) GoldBright else GoldAccent.copy(alpha = 0.18f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { playbackManager.setCrossfadeMode(mode) }
                                    .testTag("crossfade_mode_${mode.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { playbackManager.setCrossfadeMode(mode) },
                                        colors = RadioButtonDefaults.colors(selectedColor = GoldBright)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) GoldBright else Color(0xFFF9FAFB)
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF9CA3AF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Live Curve Visualizer & Audition Bar
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CassetteSectionHeader(
                                title = "Volume & Mix Curve",
                                badgeText = if (visualState.isActive) "LIVE" else "DSP",
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (visualState.isActive) {
                                    "${(visualState.progress * 100).roundToInt()}%"
                                } else {
                                    "Dual-Deck Engine"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = GoldBright
                            )
                        }

                        CrossfadeCurveCanvas(
                            mode = if (prefs.mode == CrossfadeMode.OFF) CrossfadeMode.ADVANCED else prefs.mode,
                            style = prefs.djTransitionStyle,
                            equalPowerEnabled = prefs.equalPowerCurvesEnabled,
                            activeProgress = if (visualState.isActive) visualState.progress else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (queue.isEmpty()) {
                                        Toast.makeText(context, "Load tracks to preview crossfade", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val started = playbackManager.triggerManualTransition(isPreview = true)
                                        if (started) {
                                            Toast.makeText(context, "Previewing audio crossfade", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("crossfade_preview_btn")
                            ) {
                                Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = GoldBright)
                                Spacer(Modifier.width(6.dp))
                                Text("Preview Fade", color = GoldBright)
                            }

                            Button(
                                onClick = { showDjDeckSheet = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldAccent,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("crossfade_open_dj_deck_btn")
                            ) {
                                Icon(Icons.Rounded.Album, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("DJ Cassette Deck", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Advanced Crossfade Configuration (1-15s, Default 5s)
            item {
                CassetteSectionCard(highlighted = prefs.mode == CrossfadeMode.ADVANCED) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CassetteSectionHeader(
                            title = "Advanced Crossfade",
                            badgeText = "${prefs.advancedDurationSeconds}s"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automatic Crossfade", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Automatically overlap at the end of tracks", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
                            }
                            Switch(
                                checked = prefs.autoCrossfadeEnabled,
                                onCheckedChange = { playbackManager.setCrossfadeAutoEnabled(it) },
                                modifier = Modifier.testTag("crossfade_auto_switch")
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Crossfade Duration", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${prefs.advancedDurationSeconds} sec",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = GoldBright,
                                    modifier = Modifier.testTag("advanced_crossfade_duration_label")
                                )
                            }
                            Slider(
                                value = prefs.advancedDurationSeconds.toFloat(),
                                onValueChange = { playbackManager.setAdvancedCrossfadeSeconds(it.roundToInt()) },
                                valueRange = 1f..15f,
                                steps = 13,
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldBright,
                                    activeTrackColor = GoldAccent
                                ),
                                modifier = Modifier.testTag("advanced_crossfade_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("1s Quick", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                                Text("5s Default", style = MaterialTheme.typography.labelSmall, color = GoldAccent)
                                Text("15s Long", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Equal-Power Volume Curves", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Constant perceived loudness (sin/cos curve)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
                            }
                            Switch(
                                checked = prefs.equalPowerCurvesEnabled,
                                onCheckedChange = { playbackManager.setCrossfadeEqualPower(it) },
                                modifier = Modifier.testTag("crossfade_equal_power_switch")
                            )
                        }
                    }
                }
            }

            // 4. Professional DJ-Style Mixing Controls (4-20s, Beat Sync, BPM Telemetry)
            item {
                CassetteSectionCard(highlighted = prefs.mode == CrossfadeMode.DJ_STYLE) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CassetteSectionHeader(
                            title = "DJ-Style Mixing Engine",
                            badgeText = "${prefs.djDurationSeconds}s"
                        )

                        // Local Offline Beat & BPM Status Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF13161E),
                            border = BorderStroke(0.8.dp, GoldAccent.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = GoldBright
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    val curStatus = currentAnalysis?.let {
                                        if (it.isReliable && it.bpm != null) "Current: ${it.bpm.roundToInt()} BPM" else it.statusReason
                                    } ?: "Awaiting local track analysis"
                                    val nxtStatus = nextAnalysis?.let {
                                        if (it.isReliable && it.bpm != null) "Next: ${it.bpm.roundToInt()} BPM" else it.statusReason
                                    } ?: "Next track queued"
                                    Text(
                                        text = "Local Beat Telemetry (Offline)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = GoldBright
                                    )
                                    Text(
                                        text = "$curStatus • $nxtStatus",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD1D5DB)
                                    )
                                }
                            }
                        }

                        // DJ Duration (4-20s)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("DJ Crossfade Duration", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${prefs.djDurationSeconds} sec",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = GoldBright,
                                    modifier = Modifier.testTag("dj_crossfade_duration_label")
                                )
                            }
                            Slider(
                                value = prefs.djDurationSeconds.toFloat(),
                                onValueChange = { playbackManager.setDjDurationSeconds(it.roundToInt()) },
                                valueRange = 4f..20f,
                                steps = 15,
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldBright,
                                    activeTrackColor = GoldAccent
                                ),
                                modifier = Modifier.testTag("dj_crossfade_slider")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Beat Sync & Phrase Alignment", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Align transition on musical bars when beat detection is reliable", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
                            }
                            Switch(
                                checked = prefs.djBeatSyncEnabled,
                                onCheckedChange = { playbackManager.setDjBeatSyncEnabled(it) },
                                modifier = Modifier.testTag("dj_beat_sync_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automatic BPM Detection", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Analyze local audio windows for tempo and intro drop-in", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
                            }
                            Switch(
                                checked = prefs.djAutoBpmDetectionEnabled,
                                onCheckedChange = { playbackManager.setDjAutoBpmDetectionEnabled(it) },
                                modifier = Modifier.testTag("dj_auto_bpm_switch")
                            )
                        }
                    }
                }
            }

            // 5. Transition Style Presets
            item {
                CassetteSectionCard(highlighted = prefs.mode == CrossfadeMode.DJ_STYLE) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CassetteSectionHeader(
                            title = "Transition Style Presets",
                            badgeText = prefs.djTransitionStyle.displayName.uppercase()
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DjTransitionStyle.entries.forEach { preset ->
                                val isSelected = prefs.djTransitionStyle == preset
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) GoldAccent.copy(alpha = 0.14f) else CardDark,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.3.dp else 0.8.dp,
                                        color = if (isSelected) GoldBright else GoldAccent.copy(alpha = 0.18f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { playbackManager.setDjTransitionStyle(preset) }
                                        .testTag("preset_card_${preset.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = preset.displayName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isSelected) GoldBright else Color(0xFFF9FAFB)
                                                )
                                                if (preset.bassSwapStrength > 0.05f) {
                                                    CassetteHeaderBadge(
                                                        text = if (preset == DjTransitionStyle.BASS_SWAP) "FULL BASS SWAP" else "LOW-END TUCK"
                                                    )
                                                }
                                            }
                                            Text(
                                                text = preset.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF9CA3AF)
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected Preset",
                                                tint = GoldBright
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Outgoing & Incoming Mix Trim Levels + Manual Trigger
            item {
                CassetteSectionCard(highlighted = prefs.mode == CrossfadeMode.DJ_STYLE) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CassetteSectionHeader(
                            title = "DJ Mix Levels & Control",
                            badgeText = "DECK A/B"
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Outgoing Track Mix Level", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${(prefs.djOutgoingVolumeCap * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                    color = GoldBright
                                )
                            }
                            Slider(
                                value = prefs.djOutgoingVolumeCap,
                                onValueChange = { playbackManager.setDjOutgoingVolume(it) },
                                valueRange = 0.25f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = GoldBright, activeTrackColor = GoldAccent),
                                modifier = Modifier.testTag("dj_outgoing_vol_slider")
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Incoming Track Mix Level", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${(prefs.djIncomingVolumeCap * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                    color = GoldBright
                                )
                            }
                            Slider(
                                value = prefs.djIncomingVolumeCap,
                                onValueChange = { playbackManager.setDjIncomingVolume(it) },
                                valueRange = 0.25f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = GoldBright, activeTrackColor = GoldAccent),
                                modifier = Modifier.testTag("dj_incoming_vol_slider")
                            )
                        }

                        Button(
                            onClick = {
                                if (prefs.mode == CrossfadeMode.OFF) {
                                    playbackManager.setCrossfadeMode(CrossfadeMode.DJ_STYLE)
                                }
                                val ok = playbackManager.triggerManualTransition(isPreview = false)
                                if (!ok) {
                                    Toast.makeText(context, "Load tracks to trigger a manual DJ transition", Toast.LENGTH_SHORT).show()
                                } else {
                                    showDjDeckSheet = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("dj_manual_trigger_settings_btn")
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Trigger Manual DJ Transition", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }

    if (showDjDeckSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDjDeckSheet = false },
            containerColor = Color.Black
        ) {
            DjCrossfadeDeckSheet(
                playbackManager = playbackManager,
                onOpenCrossfadeSettings = { showDjDeckSheet = false },
                onClose = { showDjDeckSheet = false }
            )
        }
    }
}
