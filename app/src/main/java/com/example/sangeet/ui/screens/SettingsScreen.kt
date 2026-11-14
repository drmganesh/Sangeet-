package com.example.sangeet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.sangeet.data.model.RuleListKind
import com.example.sangeet.data.model.RuleStatus
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.repository.SettingsRepository
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
    onNavigateToHomeNavSettings: () -> Unit,
    onNavigateToBlacklistWhitelist: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    val totalPlayed by repository.totalSongsPlayed.collectAsState(initial = 0)
    val totalTimeMs by repository.totalListeningTimeMs.collectAsState(initial = 0L)
    val currentStyle by playbackManager.playerStyle.collectAsState()
    val sleepTimerSec by playbackManager.sleepTimerSeconds.collectAsState()

    val blacklistEntries by repository.blacklist.collectAsState(initial = emptyList())
    val whitelistEntries by repository.whitelist.collectAsState(initial = emptyList())
    val folderRules by repository.folderRules.collectAsState(initial = emptyList())

    val lyricsFontSize by settingsRepo.lyricsFontSize.collectAsState(initial = 18f)
    val lyricsAutoSync by settingsRepo.lyricsAutoSync.collectAsState(initial = true)
    val crossfadeSec by settingsRepo.crossfadeSeconds.collectAsState(initial = 0)
    val gaplessEnabled by settingsRepo.gaplessPlayback.collectAsState(initial = true)
    val artworkPriority by settingsRepo.artworkPriority.collectAsState(initial = "EMBEDDED_FIRST")

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlayerStyleDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }

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
                            text = "Zero internet permission. Zero cloud tracking. All media, playlists, audio DSP, and equalizer settings stay strictly on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Listening Statistics
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
                        text = "APPEARANCE & THEME (PERSISTED)",
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

        // Section: Home and Navigation Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Home and Navigation") },
                        supportingContent = { Text("Start screen & mode, Spotlight layout, navigation bar, and rotation") },
                        leadingContent = {
                            Icon(Icons.Rounded.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier
                            .clickable { onNavigateToHomeNavSettings() }
                            .testTag("settings_home_nav_btn")
                    )
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
                        supportingContent = { Text("Independent sliders, preamp, and bass boost (auto-saved)") },
                        leadingContent = {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onNavigateToEqualizer() }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("SANGEET 360° Spatial Audio") },
                        supportingContent = { Text("Interactive radar, rotating soundstage, binaural DSP (auto-saved)") },
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

        // Section: Media Filtering (Blacklist & Whitelist)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "MEDIA FILTERING & RULES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    val blFolderRules = folderRules.filter { it.listKind == RuleListKind.BLACKLIST }
                    val wlFolderRules = folderRules.filter { it.listKind == RuleListKind.WHITELIST }
                    val totalBlCount = blacklistEntries.size + blFolderRules.size
                    val totalWlCount = whitelistEntries.size + wlFolderRules.size
                    val blIssues = blFolderRules.count { it.status == RuleStatus.PERMISSION_REVOKED || it.status == RuleStatus.FOLDER_NOT_FOUND }
                    val wlIssues = wlFolderRules.count { it.status == RuleStatus.PERMISSION_REVOKED || it.status == RuleStatus.FOLDER_NOT_FOUND }

                    ListItem(
                        headlineContent = { Text("Blacklist Management") },
                        supportingContent = {
                            val base = "$totalBlCount rules (${blFolderRules.size} folders) excluded"
                            Text(if (blIssues > 0) "$base • $blIssues folder access issue(s)" else base)
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier
                            .clickable { onNavigateToBlacklistWhitelist(0) }
                            .testTag("settings_blacklist_btn")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Whitelist Inclusion Rules") },
                        supportingContent = {
                            val text = when {
                                totalWlCount == 0 -> "Disabled (all non-blacklisted media shown) • Tap to add folders"
                                wlIssues > 0 -> "$totalWlCount rules (${wlFolderRules.size} folders) • $wlIssues access issue(s)"
                                else -> "$totalWlCount strict rules (${wlFolderRules.size} folders) active"
                            }
                            Text(text)
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier
                            .clickable { onNavigateToBlacklistWhitelist(1) }
                            .testTag("settings_whitelist_btn")
                    )
                }
            }
        }

        // Section: Album Artwork & Lyrics Customization
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
                        text = "ALBUM ARTWORK & LYRICS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Lyrics Font Size
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lyrics Font Size", style = MaterialTheme.typography.bodyMedium)
                            Text("${lyricsFontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = lyricsFontSize,
                            onValueChange = {
                                coroutineScope.launch { settingsRepo.setLyricsFontSize(it) }
                            },
                            valueRange = 12f..28f,
                            steps = 7
                        )
                    }

                    // Auto-sync lyrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Scroll Synchronized Lyrics", style = MaterialTheme.typography.bodyMedium)
                            Text("Follow track progress in real-time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = lyricsAutoSync,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setLyricsAutoSync(it) }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // Clear Artwork Cache Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Artwork Cache", style = MaterialTheme.typography.bodyMedium)
                            Text("Embedded ID3 & folder artwork cache", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    repository.artworkManager.clearArtworkCache()
                                    Toast.makeText(context, "Artwork cache refreshed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Refresh Cache")
                        }
                    }
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
                        supportingContent = { Text("${currentStyle.displayName} (Tap to change from 9 designs)") },
                        leadingContent = {
                            Icon(Icons.Rounded.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showPlayerStyleDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Clear Listening History") },
                        supportingContent = { Text("Reset playback records and recent searches") },
                        leadingContent = {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.clickable { showClearHistoryDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Reset All Settings to Defaults", color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("Restores all appearance, audio, and equalizer settings") },
                        leadingContent = {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.clickable { showResetSettingsDialog = true }
                    )
                }
            }
        }
    }

    // Player Style Dialog
    if (showPlayerStyleDialog) {
        AlertDialog(
            onDismissRequest = { showPlayerStyleDialog = false },
            title = { Text("Choose Player Style") },
            text = {
                Column {
                    PlayerStyle.entries.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackManager.setPlayerStyle(style)
                                    showPlayerStyleDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentStyle == style,
                                onClick = {
                                    playbackManager.setPlayerStyle(style)
                                    showPlayerStyleDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(style.displayName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlayerStyleDialog = false }) { Text("Close") }
            }
        )
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

    // Reset All Settings Confirmation Dialog
    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = { Text("Reset All Settings to Defaults?") },
            text = {
                Text("This will restore default AMOLED theme, Cyan accent, Flat 15-band equalizer, Natural stereo spatial audio, and Classic player style. Your media files and playlists will NOT be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            settingsRepo.resetAllSettingsToDefault()
                            onThemeModeChanged(ThemeMode.AMOLED)
                            onAccentColorChanged(AccentColor.CYAN)
                            playbackManager.setPlayerStyle(PlayerStyle.CLASSIC)
                            playbackManager.setEqAllBands(FloatArray(15) { 0f })
                            playbackManager.setEqBassBoost(0f)
                            playbackManager.setEqTrebleBoost(0f)
                            playbackManager.setEqPreamp(0f)
                            playbackManager.setEqBalance(0f)
                            playbackManager.setEqPresetName("Flat")
                            playbackManager.setSpatialPresetName("360° Surround")
                            playbackManager.setSpatialIntensity(0.8f)
                            playbackManager.setSpatialRotationSpeed(1.0f)
                            playbackManager.setSpatialRotationDirection(2)
                            playbackManager.setSpatialWidth(1.3f)
                            playbackManager.setSpatialDepth(0.7f)
                            showResetSettingsDialog = false
                            Toast.makeText(context, "All settings restored to defaults", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Reset Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }
}
