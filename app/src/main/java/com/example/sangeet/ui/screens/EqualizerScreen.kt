package com.example.sangeet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.EqualizerPreset
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.dsp.EqualizerAudioProcessor
import com.example.sangeet.playback.PlaybackManager
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val eq = playbackManager.equalizerProcessor

    var isEnabled by remember { mutableStateOf(eq.isEnabled) }
    var selectedPresetName by remember { mutableStateOf("Flat") }
    val presets by repository.allEqPresets.collectAsState(initial = emptyList())

    // 15 band gain values
    var bandGains by remember {
        mutableStateOf(eq.getAllBandGains())
    }

    var bassBoost by remember { mutableFloatStateOf(eq.bassBoostDb) }
    var trebleBoost by remember { mutableFloatStateOf(eq.trebleBoostDb) }
    var preamp by remember { mutableFloatStateOf(eq.preampGainDb) }
    var balance by remember { mutableFloatStateOf(eq.balance) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    val bandFreqLabels = listOf(
        "25Hz", "40Hz", "63Hz", "100Hz", "160Hz", "250Hz", "400Hz", "630Hz",
        "1kHz", "1.6k", "2.5k", "4kHz", "6.3k", "10k", "16kHz"
    )

    fun applyPreset(preset: EqualizerPreset) {
        selectedPresetName = preset.name
        try {
            val jsonArray = JSONArray(preset.bandsGainJson)
            val newGains = FloatArray(15)
            for (i in 0 until minOf(jsonArray.length(), 15)) {
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
        } catch (_: Exception) {}
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("equalizer_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "15-BAND GRAPHIC EQUALIZER",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnabled) "Real-time DSP Active" else "Equalizer Bypassed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("eq_back_btn")
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Reset Button
                    IconButton(
                        onClick = {
                            val flatGains = FloatArray(15) { 0f }
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
                        modifier = Modifier.testTag("eq_reset_btn")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset to Flat")
                    }
                    // Master On/Off Switch
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            playbackManager.setEqEnabled(it)
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("eq_enable_switch")
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Preset Selection Chips (Horizontal Scroll)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRESETS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Preset", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = preset.name == selectedPresetName,
                            onClick = { applyPreset(preset) },
                            label = { Text(preset.name) },
                            leadingIcon = if (preset.name == selectedPresetName) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // 15 Vertical Graphic Equalizer Sliders
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREQUENCY BANDS (±12 dB)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Biquad IIR Filter Bank",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Horizontal scrollable container for the 15 bands
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in 0 until 15) {
                            val gain = bandGains[i]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(48.dp)
                            ) {
                                Text(
                                    text = "${if (gain > 0) "+" else ""}${gain.toInt()}dB",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (gain != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Vertical Slider
                                Box(
                                    modifier = Modifier
                                        .height(180.dp)
                                        .width(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Custom vertical slider representation
                                    Slider(
                                        value = gain,
                                        onValueChange = { newGain ->
                                            val updated = bandGains.clone()
                                            updated[i] = newGain
                                            bandGains = updated
                                            playbackManager.setEqBandGain(i, newGain)
                                            selectedPresetName = "Custom"
                                        },
                                        valueRange = -12f..12f,
                                        enabled = isEnabled,
                                        modifier = Modifier
                                            .width(180.dp)
                                            .rotate(-90f)
                                            .testTag("eq_band_slider_$i")
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = bandFreqLabels[i],
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Audio Enhancements & Controls Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "ENHANCEMENT & DYNAMICS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Bass Boost Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bass Enhancement (100Hz Low-Shelf)", style = MaterialTheme.typography.bodyMedium)
                            Text("+${bassBoost.toInt()} dB", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = bassBoost,
                            onValueChange = {
                                bassBoost = it
                                playbackManager.setEqBassBoost(it)
                            },
                            valueRange = 0f..12f,
                            enabled = isEnabled,
                            modifier = Modifier.testTag("eq_bass_boost_slider")
                        )
                    }

                    // Treble Boost Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Treble Enhancement (6kHz High-Shelf)", style = MaterialTheme.typography.bodyMedium)
                            Text("+${trebleBoost.toInt()} dB", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = trebleBoost,
                            onValueChange = {
                                trebleBoost = it
                                playbackManager.setEqTrebleBoost(it)
                            },
                            valueRange = 0f..12f,
                            enabled = isEnabled,
                            modifier = Modifier.testTag("eq_treble_boost_slider")
                        )
                    }

                    // Preamp Adjustment Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Preamp Master Gain", style = MaterialTheme.typography.bodyMedium)
                            Text("${if (preamp > 0) "+" else ""}${preamp.toInt()} dB", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = preamp,
                            onValueChange = {
                                preamp = it
                                playbackManager.setEqPreamp(it)
                            },
                            valueRange = -12f..12f,
                            enabled = isEnabled,
                            modifier = Modifier.testTag("eq_preamp_slider")
                        )
                    }

                    // Balance Control Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Stereo Balance", style = MaterialTheme.typography.bodyMedium)
                            val balText = when {
                                balance < -0.05f -> "L ${(-balance * 100).toInt()}%"
                                balance > 0.05f -> "R ${(balance * 100).toInt()}%"
                                else -> "Center"
                            }
                            Text(balText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = balance,
                            onValueChange = {
                                balance = it
                                playbackManager.setEqBalance(it)
                            },
                            valueRange = -1f..1f,
                            enabled = isEnabled,
                            modifier = Modifier.testTag("eq_balance_slider")
                        )
                    }
                }
            }
        }
    }

    // Save Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Equalizer Preset") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            coroutineScope.launch {
                                repository.saveCustomEqPreset(
                                    name = newPresetName.trim(),
                                    bandGains = bandGains,
                                    bassBoost = bassBoost,
                                    trebleBoost = trebleBoost,
                                    balance = balance,
                                    preamp = preamp
                                )
                                selectedPresetName = newPresetName.trim()
                                showSaveDialog = false
                                newPresetName = ""
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
