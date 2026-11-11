package com.example.sangeet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.PlayerStyle
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.playback.PlaybackManager
import com.example.ui.theme.AccentColor
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    currentThemeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    currentAccentColor: AccentColor,
    onAccentColorChanged: (AccentColor) -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSpatial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val totalPlayed by repository.totalSongsPlayed.collectAsState(initial = 0)
    val totalTimeMs by repository.totalListeningTimeMs.collectAsState(initial = 0L)
    val currentStyle by playbackManager.playerStyle.collectAsState()
    val sleepTimerSec by playbackManager.sleepTimerSeconds.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Section: 100% Offline Guarantee Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = "Offline",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Absolute Offline Security",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zero internet permission. Zero cloud tracking. All media, playlists, and audio settings stay strictly on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Listening Insights
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LISTENING STATISTICS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalPlayed",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Songs Played",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val hours = (totalTimeMs ?: 0L) / 3600000L
                        val mins = ((totalTimeMs ?: 0L) % 3600000L) / 60000L
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${hours}h ${mins}m",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Listening Time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Section: Appearance & Themes
        item {
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
                        text = "APPEARANCE & THEME",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Theme selector chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = currentThemeMode == mode,
                                onClick = { onThemeModeChanged(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Accent Color selector
                    Text(text = "Accent Palette", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        AccentColor.entries.forEach { accent ->
                            val isSelected = currentAccentColor == accent
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(accent.color)
                                    .clickable { onAccentColorChanged(accent) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Audio DSP Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("15-Band Graphic Equalizer") },
                        supportingContent = { Text("Independent sliders, preamp, and bass boost") },
                        leadingContent = {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onNavigateToEqualizer() }
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("SANGEET 360° Spatial Audio") },
                        supportingContent = { Text("Interactive radar, rotating soundstage, binaural ITD/ILD") },
                        leadingContent = {
                            Icon(Icons.Rounded.SurroundSound, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onNavigateToSpatial() }
                    )
                }
            }
        }

        // Section: Playback & Sleep Timer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Default Player Style") },
                        supportingContent = { Text("${currentStyle.displayName} (9 Unique Designs Available)") },
                        leadingContent = {
                            Icon(Icons.Rounded.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Sleep Timer") },
                        supportingContent = {
                            Text(if (sleepTimerSec != null) "Stops in ${sleepTimerSec!! / 60}m ${sleepTimerSec!! % 60}s" else "Disabled")
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            if (sleepTimerSec != null) {
                                TextButton(onClick = { playbackManager.cancelSleepTimer() }) {
                                    Text("Cancel")
                                }
                            } else {
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable { showSleepTimerDialog = true }
                    )
                }
            }
        }

        // Section: Library Maintenance & Backup
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Rescan Device Media") },
                        supportingContent = { Text("Re-index all audio and video files from storage") },
                        leadingContent = {
                            Icon(Icons.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                val count = repository.scanMusic()
                                Toast.makeText(context, "Scanned $count music tracks", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Generate Demo Offline Music") },
                        supportingContent = { Text("Synthesize 3 lossless studio tracks locally") },
                        leadingContent = {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                repository.generateDemoTracks()
                                Toast.makeText(context, "Demo tracks loaded", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Export Local JSON Backup") },
                        supportingContent = { Text("Save presets and playlists to local storage") },
                        leadingContent = {
                            Icon(Icons.Rounded.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                repository.exportBackupJson()
                                Toast.makeText(context, "Backup exported locally", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Clear Listening History") },
                        supportingContent = { Text("Reset playback records and recent searches") },
                        leadingContent = {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.clickable { showClearHistoryDialog = true }
                    )
                }
            }
        }
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = {
                                playbackManager.setSleepTimer(mins)
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$mins Minutes", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear History Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Listening History?") },
            text = { Text("This will permanently remove all local playback history and recent searches. Your music files will NOT be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.clearHistory()
                            repository.clearSearches()
                            showClearHistoryDialog = false
                            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }
}
