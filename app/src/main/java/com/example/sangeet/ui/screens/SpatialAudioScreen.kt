package com.example.sangeet.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.SpatialPreset
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import com.example.sangeet.ui.components.CassetteTopBar
import com.example.sangeet.ui.components.RadarSpatialView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialAudioScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val spatial = playbackManager.spatialProcessor

    var isEnabled by remember { mutableStateOf(spatial.isEnabled) }
    var intensity by remember { mutableFloatStateOf(spatial.spatialIntensity) }
    var speed by remember { mutableFloatStateOf(spatial.rotationSpeed) }
    var direction by remember { mutableIntStateOf(spatial.rotationDirection) }
    var width by remember { mutableFloatStateOf(spatial.soundstageWidth) }
    var depth by remember { mutableFloatStateOf(spatial.soundstageDepth) }
    var selectedPresetName by remember { mutableStateOf("360° Surround") }

    val presets by repository.allSpatialPresets.collectAsState(initial = emptyList())

    // Continuously read the real-time perceived azimuth angle from the DSP
    var liveAzimuthDegrees by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isEnabled) {
        while (true) {
            liveAzimuthDegrees = spatial.currentAzimuthDegrees
            delay(33L) // ~30 fps UI radar refresh
        }
    }

    fun applyPreset(preset: SpatialPreset) {
        selectedPresetName = preset.name
        isEnabled = preset.isEnabled
        playbackManager.setSpatialEnabled(preset.isEnabled)
        intensity = preset.spatialIntensity
        playbackManager.setSpatialIntensity(preset.spatialIntensity)
        speed = preset.rotationSpeed
        playbackManager.setSpatialRotationSpeed(preset.rotationSpeed)
        direction = preset.rotationDirection
        playbackManager.setSpatialRotationDirection(preset.rotationDirection)
        width = preset.soundstageWidth
        playbackManager.setSpatialWidth(preset.soundstageWidth)
        depth = preset.soundstageDepth
        playbackManager.setSpatialDepth(preset.soundstageDepth)
        playbackManager.setSpatialPresetName(preset.name)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("spatial_audio_screen"),
        topBar = {
            CassetteTopBar(
                title = "360° Spatial Audio",
                subtitle = if (isEnabled) "Rotating Binaural Surround Active" else "Spatial Audio Disabled",
                badgeText = "360°",
                onNavigateBack = onNavigateBack,
                actions = {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            playbackManager.setSpatialEnabled(it)
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("spatial_enable_switch")
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
            // Headphone Recommendation Banner
            CassetteSectionCard(highlighted = isEnabled) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = "Headphones",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Headphone Surround Recommended",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Real-time binaural crossfeed and ITD/ILD head-shadow synthesis are optimized for stereo headphones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Interactive 360° Radar Canvas
            CassetteSectionCard(highlighted = isEnabled) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CassetteSectionHeader(
                        title = "Soundstage Radar",
                        badgeText = "AZIMUTH ${liveAzimuthDegrees.toInt()}°"
                    )

                    RadarSpatialView(
                        azimuthDegrees = liveAzimuthDegrees,
                        intensity = intensity,
                        isRotating = speed > 0.05f && direction != 3,
                        onManualPositionChanged = { angleDeg ->
                            direction = 3
                            playbackManager.setSpatialRotationDirection(3)
                            playbackManager.setSpatialManualAzimuth(angleDeg)
                            liveAzimuthDegrees = angleDeg
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                    )

                    Text(
                        text = "Drag the sound dot to manually position source in 360° space",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Spatial Presets
            Column {
                Text(
                    text = "SPATIAL PRESETS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
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

            // Rotation & Trajectory Controls
            CassetteSectionCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CassetteSectionHeader(title = "Rotation & Trajectory")

                    // Direction Choice
                    Text("Sound Movement Pattern", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple(2, "360° Circular", Icons.Rounded.Autorenew),
                            Triple(0, "Left → Right", Icons.Rounded.ArrowForward),
                            Triple(1, "Right → Left", Icons.Rounded.ArrowBack),
                            Triple(3, "Static / Manual", Icons.Rounded.CenterFocusStrong)
                        ).forEach { (dir, label, icon) ->
                            ElevatedFilterChip(
                                selected = direction == dir,
                                onClick = {
                                    direction = dir
                                    playbackManager.setSpatialRotationDirection(dir)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Rotation Speed Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rotation Speed", style = MaterialTheme.typography.bodyMedium)
                            Text("${String.format("%.1f", speed)} RPM", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = speed,
                            onValueChange = {
                                speed = it
                                playbackManager.setSpatialRotationSpeed(it)
                            },
                            valueRange = 0f..4f,
                            enabled = isEnabled,
                            modifier = Modifier.testTag("spatial_speed_slider")
                        )
                    }
                }
            }

            // Soundstage Dimensions & Intensity
            CassetteSectionCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CassetteSectionHeader(title = "Acoustic Dimensions")

                    // Spatial Intensity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Spatial Immersion Intensity", style = MaterialTheme.typography.bodyMedium)
                            Text("${(intensity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = intensity,
                            onValueChange = {
                                intensity = it
                                playbackManager.setSpatialIntensity(it)
                            },
                            valueRange = 0f..1f,
                            enabled = isEnabled,
                            modifier = Modifier.testTag("spatial_intensity_slider")
                        )
                    }

                    // Soundstage Width Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Stereo Widening (Mid/Side)", style = MaterialTheme.typography.bodyMedium)
                            Text("${String.format("%.1f", width)}x", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = width,
                            onValueChange = {
                                width = it
                                playbackManager.setSpatialWidth(it)
                            },
                            valueRange = 0.8f..2.2f,
                            enabled = isEnabled,
                            modifier = Modifier.testTag("spatial_width_slider")
                        )
                    }

                    // Soundstage Depth Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Virtual Surround Depth", style = MaterialTheme.typography.bodyMedium)
                            Text("${(depth * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = depth,
                            onValueChange = {
                                depth = it
                                playbackManager.setSpatialDepth(it)
                            },
                            valueRange = 0f..1f,
                            enabled = isEnabled,
                            modifier = Modifier.testTag("spatial_depth_slider")
                        )
                    }
                }
            }
        }
    }
}
