package com.example.sangeet.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.EqualizerPreset
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.dsp.EqualizerAudioProcessor
import com.example.sangeet.dsp.EqualizerResponseCurve
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.motion.LocalSangeetMotion
import com.example.sangeet.ui.motion.sangeetPressScale
import com.example.ui.theme.SangeetGold
import com.example.ui.theme.SangeetGoldBright
import com.example.ui.theme.SangeetGoldDark
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val EqAmoledBlack = Color(0xFF000000)
private val EqCharcoalTop = Color(0xFF17140D)
private val EqCharcoalBottom = Color(0xFF090A0E)
private val EqGold = SangeetGold
private val EqGoldBright = SangeetGoldBright
private val EqAmber = Color(0xFFFFB300)
private val EqGoldMuted = SangeetGoldDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sangeetMotion = LocalSangeetMotion.current
    val eq = playbackManager.equalizerProcessor
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    var isEnabled by remember { mutableStateOf(eq.isEnabled) }
    val persistedPresetName by settingsRepo.eqPresetName.collectAsState(initial = null)
    var selectedPresetName by remember { mutableStateOf("Flat") }
    var presetSyncedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(persistedPresetName) {
        if (!presetSyncedOnce && persistedPresetName != null) {
            selectedPresetName = persistedPresetName!!
            presetSyncedOnce = true
        }
    }

    val presets by repository.allEqPresets.collectAsState(initial = emptyList())

    // 15 band gain values directly synchronized with real EqualizerAudioProcessor
    var bandGains by remember { mutableStateOf(eq.getAllBandGains()) }
    var bassBoost by remember { mutableFloatStateOf(eq.bassBoostDb) }
    var trebleBoost by remember { mutableFloatStateOf(eq.trebleBoostDb) }
    var preamp by remember { mutableFloatStateOf(eq.preampGainDb) }
    var balance by remember { mutableFloatStateOf(eq.balance) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    // Exact 15 frequency labels matching EqualizerAudioProcessor.FREQUENCIES
    val bandFreqLabels = remember {
        listOf(
            "25Hz", "40Hz", "63Hz", "100Hz", "160Hz", "250Hz", "400Hz", "630Hz",
            "1kHz", "1.6k", "2.5k", "4kHz", "6.3k", "10k", "16kHz"
        )
    }

    val responseCurvePoints = remember(bandGains, bassBoost, trebleBoost, isEnabled) {
        EqualizerResponseCurve.computeResponseCurveDb(
            bandGains = bandGains,
            bassBoostDb = bassBoost,
            trebleBoostDb = trebleBoost,
            isEnabled = isEnabled,
            pointCount = 96
        )
    }

    fun applyPreset(preset: EqualizerPreset) {
        selectedPresetName = preset.name
        try {
            val jsonArray = JSONArray(preset.bandsGainJson)
            val newGains = FloatArray(EqualizerAudioProcessor.BAND_COUNT)
            for (i in 0 until minOf(jsonArray.length(), EqualizerAudioProcessor.BAND_COUNT)) {
                newGains[i] = jsonArray.getDouble(i).toFloat()
            }
            bandGains = newGains
            playbackManager.setEqAllBands(newGains)
            bassBoost = preset.bassBoost
            playbackManager.setEqBassBoost(preset.bassBoost)
            trebleBoost = preset.trebleBoost
            playbackManager.setEqTrebleBoost(preset.trebleBoost)
            balance = preset.balance
            playbackManager.setEqBalance(preset.balance)
            preamp = preset.preampGainDb
            playbackManager.setEqPreamp(preset.preampGainDb)
            playbackManager.setEqPresetName(preset.name)
        } catch (_: Exception) {
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("equalizer_screen"),
        containerColor = EqAmoledBlack,
        topBar = {
            StudioEqualizerTopBar(
                isEnabled = isEnabled,
                onNavigateBack = onNavigateBack,
                onReset = {
                    val flatGains = FloatArray(EqualizerAudioProcessor.BAND_COUNT) { 0f }
                    bandGains = flatGains
                    playbackManager.setEqAllBands(flatGains)
                    bassBoost = 0f
                    playbackManager.setEqBassBoost(0f)
                    trebleBoost = 0f
                    playbackManager.setEqTrebleBoost(0f)
                    preamp = 0f
                    playbackManager.setEqPreamp(0f)
                    balance = 0f
                    playbackManager.setEqBalance(0f)
                    selectedPresetName = "Flat"
                    playbackManager.setEqPresetName("Flat")
                },
                onToggleEnabled = {
                    isEnabled = it
                    playbackManager.setEqEnabled(it)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A0804),
                            EqAmoledBlack,
                            Color(0xFF060503),
                            EqAmoledBlack
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Top decorative subtle golden audio waveform
                EqualizerStudioWaveform(
                    isActive = isEnabled,
                    animateWave = isEnabled && sangeetMotion.decorativeAnimationsEnabled,
                    phaseSeed = 0.25f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                )

                // Studio Presets Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(EqGold)
                            )
                            Text(
                                text = "PRESETS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.4.sp
                                ),
                                color = EqGoldBright
                            )
                            CassetteHeaderBadge(
                                text = selectedPresetName.uppercase(),
                                accentColor = if (isEnabled) EqGold else Color(0xFF7B7360)
                            )
                        }
                        FilledTonalButton(
                            onClick = { showSaveDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = EqGold.copy(alpha = 0.14f),
                                contentColor = EqGoldBright
                            ),
                            border = BorderStroke(0.9.dp, EqGold.copy(alpha = 0.45f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("eq_save_preset_btn")
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Save Preset",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(presets, key = { "${it.id}_${it.name}" }) { preset ->
                            StudioPresetCard(
                                preset = preset,
                                isSelected = preset.name == selectedPresetName,
                                onSelect = { applyPreset(preset) },
                                onDeleteCustom = if (preset.isCustom) {
                                    {
                                        coroutineScope.launch {
                                            repository.deleteEqPreset(preset)
                                            if (selectedPresetName == preset.name) {
                                                selectedPresetName = "Custom"
                                                playbackManager.setEqPresetName("Custom")
                                            }
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }

                // 15-Band Graphic Equalizer Studio Card
                StudioGoldPanelCard(highlighted = isEnabled) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(EqGoldBright)
                                    )
                                    Text(
                                        text = "15-BAND SPECTRUM (±12 dB)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.1.sp
                                        ),
                                        color = EqGoldBright
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "25 Hz – 16 kHz Peaking Filter Bank",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA8A08E),
                                    modifier = Modifier.padding(start = 11.dp)
                                )
                            }
                            CassetteHeaderBadge(
                                text = "BIQUAD IIR",
                                accentColor = if (isEnabled) EqGoldBright else Color(0xFF7B7360)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Real-time Biquad IIR Frequency Response Curve Canvas
                        StudioFrequencyResponseGraph(
                            curveDb = responseCurvePoints,
                            bandGains = bandGains,
                            preampDb = preamp,
                            isEnabled = isEnabled,
                            showGlow = sangeetMotion.decorativeAnimationsEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                                .testTag("eq_response_curve")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Decibel reference scale + 15 horizontal scrollable vertical golden sliders
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Visual Decibel Reference Scale (+15 / +12 / +6 / 0 / -6 / -12 dB)
                            StudioDecibelScaleColumn(
                                isEnabled = isEnabled,
                                modifier = Modifier
                                    .width(42.dp)
                                    .padding(top = 20.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (i in 0 until EqualizerAudioProcessor.BAND_COUNT) {
                                    val gain = bandGains[i]
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(48.dp)
                                    ) {
                                        val formattedGain = if (abs(gain) < 0.05f) {
                                            "0dB"
                                        } else {
                                            val sign = if (gain > 0f) "+" else ""
                                            "$sign${String.format("%.1f", gain).removeSuffix(".0")}"
                                        }
                                        Text(
                                            text = formattedGain,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = when {
                                                !isEnabled -> Color(0xFF6B6559)
                                                abs(gain) >= 0.05f -> EqGoldBright
                                                else -> Color(0xFF9C9484)
                                            },
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        GoldenVerticalEqSlider(
                                            value = gain,
                                            onValueChange = { newGain ->
                                                val updated = bandGains.clone()
                                                updated[i] = newGain
                                                bandGains = updated
                                                playbackManager.setEqBandGain(i, newGain)
                                                selectedPresetName = "Custom"
                                                playbackManager.setEqPresetName("Custom")
                                            },
                                            valueRange = -12f..12f,
                                            enabled = isEnabled,
                                            bandLabel = bandFreqLabels[i],
                                            modifier = Modifier
                                                .height(186.dp)
                                                .width(48.dp)
                                                .testTag("eq_band_slider_$i")
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = bandFreqLabels[i],
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            ),
                                            color = if (isEnabled) Color(0xFFEBE4D5) else Color(0xFF7B7360),
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Separate Card: Enhancement & Dynamics (preserving all exact controls & ranges)
                StudioGoldPanelCard(highlighted = isEnabled && (bassBoost > 0f || trebleBoost > 0f || preamp != 0f || balance != 0f)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(EqGold)
                                )
                                Text(
                                    text = "ENHANCEMENT & DYNAMICS",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.1.sp
                                    ),
                                    color = EqGoldBright
                                )
                            }
                            CassetteHeaderBadge(
                                text = "ANALOG STAGE",
                                accentColor = if (isEnabled) EqAmber else Color(0xFF7B7360)
                            )
                        }

                        // Bass Enhancement (100Hz Low-Shelf) — preserved range 0..12
                        StudioHorizontalGainControl(
                            title = "Bass Enhancement (100Hz Low-Shelf)",
                            valueText = "+${bassBoost.toInt()} dB",
                            value = bassBoost,
                            onValueChange = {
                                bassBoost = it
                                playbackManager.setEqBassBoost(it)
                            },
                            valueRange = 0f..12f,
                            enabled = isEnabled,
                            sliderTag = "eq_bass_boost_slider"
                        )

                        // Treble Enhancement (6kHz High-Shelf) — preserved range 0..12
                        StudioHorizontalGainControl(
                            title = "Treble Enhancement (6kHz High-Shelf)",
                            valueText = "+${trebleBoost.toInt()} dB",
                            value = trebleBoost,
                            onValueChange = {
                                trebleBoost = it
                                playbackManager.setEqTrebleBoost(it)
                            },
                            valueRange = 0f..12f,
                            enabled = isEnabled,
                            sliderTag = "eq_treble_boost_slider"
                        )

                        // Preamp Master Gain — preserved range -12..12
                        StudioHorizontalGainControl(
                            title = "Preamp Master Gain",
                            valueText = "${if (preamp > 0) "+" else ""}${preamp.toInt()} dB",
                            value = preamp,
                            onValueChange = {
                                preamp = it
                                playbackManager.setEqPreamp(it)
                            },
                            valueRange = -12f..12f,
                            enabled = isEnabled,
                            sliderTag = "eq_preamp_slider"
                        )

                        // Stereo Balance — preserved range -1..1
                        val balText = when {
                            balance < -0.05f -> "L ${(-balance * 100).toInt()}%"
                            balance > 0.05f -> "R ${(balance * 100).toInt()}%"
                            else -> "Center"
                        }
                        StudioHorizontalGainControl(
                            title = "Stereo Balance",
                            valueText = balText,
                            value = balance,
                            onValueChange = {
                                balance = it
                                playbackManager.setEqBalance(it)
                            },
                            valueRange = -1f..1f,
                            enabled = isEnabled,
                            sliderTag = "eq_balance_slider"
                        )
                    }
                }

                // Bottom decorative subtle golden audio waveform
                EqualizerStudioWaveform(
                    isActive = isEnabled,
                    animateWave = isEnabled && sangeetMotion.decorativeAnimationsEnabled,
                    phaseSeed = 1.6f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Save Preset Dialog (preserves custom preset DB saving)
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = Color(0xFF14120D),
            titleContentColor = EqGoldBright,
            textContentColor = Color(0xFFF5F2EA),
            title = {
                Text(
                    "Save Equalizer Preset",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EqGold,
                        unfocusedBorderColor = EqGold.copy(alpha = 0.4f),
                        focusedLabelColor = EqGoldBright,
                        cursorColor = EqGoldBright
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eq_save_preset_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            val trimmed = newPresetName.trim()
                            coroutineScope.launch {
                                repository.saveCustomEqPreset(
                                    name = trimmed,
                                    bandGains = bandGains,
                                    bassBoost = bassBoost,
                                    trebleBoost = trebleBoost,
                                    balance = balance,
                                    preamp = preamp
                                )
                                selectedPresetName = trimmed
                                playbackManager.setEqPresetName(trimmed)
                                showSaveDialog = false
                                newPresetName = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EqGold,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = Color(0xFFA8A08E))
                }
            }
        )
    }
}

@Composable
private fun StudioEqualizerTopBar(
    isEnabled: Boolean,
    onNavigateBack: () -> Unit,
    onReset: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0906),
        border = BorderStroke(0.9.dp, EqGold.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("eq_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = EqGoldBright
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "15-Band Equalizer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp
                    ),
                    color = Color(0xFFF6F1E6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isEnabled) EqGoldBright else Color(0xFF6B6559))
                    )
                    Text(
                        text = if (isEnabled) "Real-time DSP Active" else "Real-time DSP Bypassed",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isEnabled) EqGold else Color(0xFF9C9484),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("eq_reset_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reset Equalizer",
                    tint = EqGoldBright
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = EqGoldBright,
                    checkedBorderColor = EqGold,
                    uncheckedThumbColor = Color(0xFF9C9484),
                    uncheckedTrackColor = Color(0xFF1A1B20),
                    uncheckedBorderColor = EqGold.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .testTag("eq_enable_switch")
            )
        }
    }
}

@Composable
private fun StudioGoldPanelCard(
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) EqGold.copy(alpha = 0.72f) else EqGold.copy(alpha = 0.25f),
        label = "studio_card_border"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(EqCharcoalTop, Color(0xFF101116), EqCharcoalBottom)
                )
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(if (highlighted) 1.25.dp else 0.9.dp, borderColor),
            content = { Column(modifier = Modifier.fillMaxWidth(), content = content) }
        )
    }
}

@Composable
private fun StudioPresetCard(
    preset: EqualizerPreset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDeleteCustom: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val previewGains = remember(preset.bandsGainJson) {
        runCatching {
            val arr = JSONArray(preset.bandsGainJson)
            FloatArray(15) { idx ->
                if (idx < arr.length()) arr.getDouble(idx).toFloat().coerceIn(-12f, 12f) else 0f
            }
        }.getOrElse { FloatArray(15) }
    }
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) EqGoldBright else EqGold.copy(alpha = 0.28f),
        label = "preset_card_border"
    )

    Surface(
        modifier = Modifier
            .width(126.dp)
            .sangeetPressScale(interactionSource)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = EqGold),
                onClick = onSelect
            )
            .testTag("eq_preset_card_${preset.name.lowercase().replace(' ', '_')}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF1F190B) else Color(0xFF111216),
        border = BorderStroke(if (isSelected) 1.4.dp else 0.9.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                    ),
                    color = if (isSelected) EqGoldBright else Color(0xFFEBE4D5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onDeleteCustom != null) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Delete Custom Preset",
                        tint = Color(0xFFA8A08E),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onDeleteCustom)
                    )
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Active Preset",
                        tint = EqGoldBright,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Miniature 15-bar EQ silhouette reflecting the preset's actual bands
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            ) {
                val barCount = 15
                val gap = 2.dp.toPx()
                val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1.5f)
                val midY = size.height / 2f
                drawLine(
                    color = EqGold.copy(alpha = 0.25f),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 0.8.dp.toPx()
                )
                for (i in 0 until barCount) {
                    val norm = (previewGains[i] / 12f).coerceIn(-1f, 1f)
                    val x = i * (barWidth + gap)
                    val barHeight = (abs(norm) * (size.height * 0.46f)).coerceAtLeast(1.5.dp.toPx())
                    val topY = if (norm >= 0f) midY - barHeight else midY
                    drawRoundRect(
                        color = if (isSelected) EqGoldBright else EqGold.copy(alpha = 0.6f),
                        topLeft = Offset(x, topY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioFrequencyResponseGraph(
    curveDb: List<Float>,
    bandGains: FloatArray,
    preampDb: Float,
    isEnabled: Boolean,
    showGlow: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF060709),
        border = BorderStroke(0.9.dp, EqGold.copy(alpha = if (isEnabled) 0.38f else 0.18f))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            val w = size.width
            val h = size.height
            val maxDbRange = 15f
            fun dbToY(db: Float): Float {
                val clamped = db.coerceIn(-maxDbRange, maxDbRange)
                return h * (0.5f - clamped / (2f * maxDbRange))
            }

            // Reference guide lines (+12, +6, 0, -6, -12 dB)
            val gridDbs = floatArrayOf(12f, 6f, 0f, -6f, -12f)
            gridDbs.forEach { db ->
                val y = dbToY(db)
                drawLine(
                    color = if (db == 0f) EqGold.copy(alpha = 0.34f) else EqGold.copy(alpha = 0.12f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (db == 0f) 1.dp.toPx() else 0.6.dp.toPx(),
                    pathEffect = if (db == 0f) null else PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                )
            }

            // Subtle dashed preamp baseline when non-zero
            if (isEnabled && abs(preampDb) >= 0.3f) {
                val py = dbToY(preampDb)
                drawLine(
                    color = EqAmber.copy(alpha = 0.45f),
                    start = Offset(0f, py),
                    end = Offset(w, py),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )
            }

            if (curveDb.size >= 2) {
                val strokePath = Path()
                val fillPath = Path()
                val zeroY = dbToY(0f)

                curveDb.forEachIndexed { index, db ->
                    val x = (index.toFloat() / (curveDb.lastIndex).toFloat()) * w
                    val y = dbToY(db)
                    if (index == 0) {
                        strokePath.moveTo(x, y)
                        fillPath.moveTo(x, zeroY)
                        fillPath.lineTo(x, y)
                    } else {
                        strokePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, zeroY)
                fillPath.close()

                val curveAlpha = if (isEnabled) 1f else 0.35f
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(
                            EqGoldBright.copy(alpha = 0.22f * curveAlpha),
                            EqGold.copy(alpha = 0.04f * curveAlpha),
                            Color.Transparent
                        )
                    )
                )

                if (showGlow && isEnabled) {
                    drawPath(
                        path = strokePath,
                        color = EqGold.copy(alpha = 0.28f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                drawPath(
                    path = strokePath,
                    brush = Brush.horizontalGradient(
                        listOf(EqAmber.copy(alpha = curveAlpha), EqGoldBright.copy(alpha = curveAlpha), EqGold.copy(alpha = curveAlpha))
                    ),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Plot subtle golden dots for non-zero band centers at their log frequency
                if (isEnabled) {
                    for (i in 0 until minOf(bandGains.size, EqualizerAudioProcessor.FREQUENCIES.size)) {
                        val g = bandGains[i]
                        if (abs(g) >= 0.4f) {
                            val normX = EqualizerResponseCurve.normalizedFrequencyPosition(EqualizerAudioProcessor.FREQUENCIES[i])
                            val sampleIdx = (normX * curveDb.lastIndex).toInt().coerceIn(0, curveDb.lastIndex)
                            val cx = normX * w
                            val cy = dbToY(curveDb[sampleIdx])
                            drawCircle(
                                color = EqGoldBright,
                                radius = 2.6.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioDecibelScaleColumn(
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isEnabled) Color(0xFFB5AA92) else Color(0xFF5D574C)
    Column(
        modifier = modifier.height(194.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("+15 dB", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = EqAmber.copy(alpha = if (isEnabled) 0.78f else 0.4f))
        Text("+12 dB", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color)
        Text("+6 dB", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color)
        Text("0 dB", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = EqGoldBright.copy(alpha = if (isEnabled) 0.9f else 0.45f))
        Text("-6 dB", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color)
        Text("-12 dB", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color)
    }
}

@Composable
private fun GoldenVerticalEqSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    bandLabel: String,
    modifier: Modifier = Modifier
) {
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val animatedVal by animateFloatAsState(
        targetValue = value.coerceIn(valueRange.start, valueRange.endInclusive),
        animationSpec = tween(durationMillis = 70),
        label = "eq_band_visual_$bandLabel"
    )

    val stateLabel = remember(value) {
        val sign = if (value > 0.05f) "+" else ""
        "$sign${String.format("%.1f", value)} dB"
    }

    Box(
        modifier = modifier
            .semantics {
                contentDescription = "$bandLabel equalizer band"
                stateDescription = stateLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange
                )
                if (enabled) {
                    setProgress { target ->
                        val clamped = target.coerceIn(valueRange.start, valueRange.endInclusive)
                        latestOnChange(clamped)
                        true
                    }
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { latestOnChange(0f) },
                    onTap = { offset ->
                        val topPad = 14.dp.toPx()
                        val usable = (size.height - topPad * 2f).coerceAtLeast(1f)
                        val fraction = (1f - ((offset.y - topPad) / usable)).coerceIn(0f, 1f)
                        latestOnChange(valueRange.start + fraction * span)
                    }
                )
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var current = latestValue
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val dy = change.positionChange().y
                        if (abs(dy) > 0.1f) {
                            change.consume()
                            val topPad = 14.dp.toPx()
                            val usable = (size.height - topPad * 2f).coerceAtLeast(1f)
                            val deltaVal = (-dy / usable) * span
                            current = (current + deltaVal).coerceIn(valueRange.start, valueRange.endInclusive)
                            latestOnChange(current)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val topPad = 14.dp.toPx()
            val bottomPad = size.height - topPad
            val usableH = (bottomPad - topPad).coerceAtLeast(1f)
            val centerX = size.width / 2f
            val centerY = topPad + usableH * 0.5f
            val trackW = 5.dp.toPx()

            // Horizontal subtle studio tick marks
            val tickFractions = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            tickFractions.forEach { frac ->
                val ty = topPad + usableH * frac
                val halfSpan = if (frac == 0.5f) 14.dp.toPx() else 9.dp.toPx()
                drawLine(
                    color = if (frac == 0.5f) EqGold.copy(alpha = 0.5f) else EqGold.copy(alpha = 0.18f),
                    start = Offset(centerX - halfSpan, ty),
                    end = Offset(centerX + halfSpan, ty),
                    strokeWidth = if (frac == 0.5f) 1.2.dp.toPx() else 0.8.dp.toPx()
                )
            }

            // Recessed dark fader channel
            drawRoundRect(
                color = Color(0xFF14151B),
                topLeft = Offset(centerX - trackW / 2f, topPad),
                size = Size(trackW, usableH),
                cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
            )
            drawRoundRect(
                color = EqGold.copy(alpha = 0.24f),
                topLeft = Offset(centerX - trackW / 2f, topPad),
                size = Size(trackW, usableH),
                cornerRadius = CornerRadius(trackW / 2f, trackW / 2f),
                style = Stroke(width = 0.8.dp.toPx())
            )

            // Normalized knob Y position
            val norm = ((animatedVal - valueRange.start) / span).coerceIn(0f, 1f)
            val thumbY = topPad + usableH * (1f - norm)

            // Glowing golden active fill from 0 dB center line
            val activeTop = minOf(thumbY, centerY)
            val activeBottom = maxOf(thumbY, centerY)
            val activeColor = if (enabled) EqGoldBright else Color(0xFF6B6559)
            if (activeBottom - activeTop > 1f) {
                if (enabled) {
                    drawRoundRect(
                        color = EqGold.copy(alpha = 0.28f),
                        topLeft = Offset(centerX - trackW * 0.9f, activeTop),
                        size = Size(trackW * 1.8f, activeBottom - activeTop),
                        cornerRadius = CornerRadius(trackW, trackW)
                    )
                }
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(activeColor, EqGold)
                    ),
                    topLeft = Offset(centerX - trackW / 2f, activeTop),
                    size = Size(trackW, activeBottom - activeTop),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                )
            }

            // Metallic studio fader cap
            val capW = 22.dp.toPx()
            val capH = 14.dp.toPx()
            if (enabled) {
                drawCircle(
                    color = EqGold.copy(alpha = 0.25f),
                    radius = 14.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
            }
            drawRoundRect(
                brush = Brush.verticalGradient(
                    if (enabled) {
                        listOf(Color(0xFF332A16), Color(0xFF18140C), Color(0xFF0E0D0A))
                    } else {
                        listOf(Color(0xFF242428), Color(0xFF18181B))
                    }
                ),
                topLeft = Offset(centerX - capW / 2f, thumbY - capH / 2f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = if (enabled) EqGoldBright else Color(0xFF6B6559),
                topLeft = Offset(centerX - capW / 2f, thumbY - capH / 2f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 1.1.dp.toPx())
            )
            // Center indicator line on fader cap
            drawLine(
                color = if (enabled) EqGoldBright else Color(0xFF8B8579),
                start = Offset(centerX - capW * 0.34f, thumbY),
                end = Offset(centerX + capW * 0.34f, thumbY),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun StudioHorizontalGainControl(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    sliderTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) Color(0xFFF3EEE2) else Color(0xFF7B7360),
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EqGold.copy(alpha = if (enabled) 0.14f else 0.06f),
                border = BorderStroke(0.8.dp, EqGold.copy(alpha = if (enabled) 0.42f else 0.18f))
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (enabled) EqGoldBright else Color(0xFF7B7360),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = EqGoldBright,
                activeTrackColor = EqGold,
                inactiveTrackColor = EqGold.copy(alpha = 0.22f),
                disabledThumbColor = Color(0xFF6B6559),
                disabledActiveTrackColor = Color(0xFF47433A),
                disabledInactiveTrackColor = Color(0xFF202024)
            ),
            modifier = Modifier.testTag(sliderTag)
        )
    }
}

@Composable
private fun EqualizerStudioWaveform(
    isActive: Boolean,
    animateWave: Boolean,
    phaseSeed: Float,
    modifier: Modifier = Modifier
) {
    val phase = if (animateWave) {
        val transition = rememberInfiniteTransition(label = "eq_studio_wave")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "eq_wave_phase"
        )
        animated + phaseSeed
    } else {
        phaseSeed
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val amp = h * 0.34f
        val alpha = if (isActive) 0.55f else 0.2f

        val primaryWave = Path()
        val secondaryWave = Path()
        val samples = 64
        for (i in 0..samples) {
            val t = i.toFloat() / samples.toFloat()
            val x = t * w
            val envelope = sin(t * PI).toFloat()
            val y1 = midY + sin(t * 10.0 + phase).toFloat() * amp * envelope
            val y2 = midY + cos(t * 14.0 - phase * 0.8).toFloat() * (amp * 0.6f) * envelope
            if (i == 0) {
                primaryWave.moveTo(x, y1)
                secondaryWave.moveTo(x, y2)
            } else {
                primaryWave.lineTo(x, y1)
                secondaryWave.lineTo(x, y2)
            }
        }

        drawPath(
            path = secondaryWave,
            color = EqGold.copy(alpha = alpha * 0.45f),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = primaryWave,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, EqGoldBright.copy(alpha = alpha), EqGold.copy(alpha = alpha), Color.Transparent)
            ),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
