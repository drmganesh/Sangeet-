package com.example.sangeet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.data.model.RuleListKind
import com.example.sangeet.data.model.RuleStatus
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.repository.NotificationPreferencesSnapshot
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.ArtworkPathCache
import com.example.sangeet.ui.components.CassetteArtwork
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import com.example.sangeet.ui.components.CassetteTopBar
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
    onNavigateToNotificationSettings: () -> Unit,
    onNavigateToArtworkAnimationSettings: () -> Unit,
    onNavigateToBlacklistWhitelist: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    val totalPlayed by repository.totalSongsPlayed.collectAsState(initial = 0)
    val totalTimeMs by repository.totalListeningTimeMs.collectAsState(initial = 0L)
    val currentCassetteStyle by playbackManager.cassetteStyle.collectAsState()
    val mechanicalSounds by playbackManager.walkmanMechanicalSounds.collectAsState()
    val sleepTimerSec by playbackManager.sleepTimerSeconds.collectAsState()

    val blacklistEntries by repository.blacklist.collectAsState(initial = emptyList())
    val whitelistEntries by repository.whitelist.collectAsState(initial = emptyList())
    val folderRules by repository.folderRules.collectAsState(initial = emptyList())

    val lyricsFontSize by settingsRepo.lyricsFontSize.collectAsState(initial = 18f)
    val lyricsAutoSync by settingsRepo.lyricsAutoSync.collectAsState(initial = true)
    val crossfadeSec by settingsRepo.crossfadeSeconds.collectAsState(initial = 0)
    val gaplessEnabled by settingsRepo.gaplessPlayback.collectAsState(initial = true)
    val artworkPriority by settingsRepo.artworkPriority.collectAsState(initial = "EMBEDDED_FIRST")
    val notifPrefs by settingsRepo.notificationPreferences.collectAsState(initial = NotificationPreferencesSnapshot())
    val artAnimPrefs by settingsRepo.artworkAnimationPreferences.collectAsState(initial = ArtworkAnimationPreferencesSnapshot())

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showCassetteStyleDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        CassetteTopBar(
            title = "Settings",
            subtitle = "Offline cassette deck, audio DSP & appearance",
            badgeText = currentCassetteStyle.badgeText
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section: 100% Offline Guarantee Banner
            item {
                CassetteSectionCard(highlighted = true) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = "Offline",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Absolute Offline Security",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                CassetteHeaderBadge(text = "100% LOCAL")
                            }
                            Text(
                                text = "Zero internet permission. Zero cloud tracking. All media, playlists, cassette skins, DSP, and equalizer settings stay strictly on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section: Listening Statistics
            item {
                CassetteSectionCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CassetteSectionHeader(
                            title = "Listening Statistics",
                            badgeText = "DECK LOG"
                        )
                        Spacer(Modifier.height(14.dp))
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

            // Section: Cassette Style & Theme Appearance
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CassetteSectionHeader(
                            title = "Cassette & Theme Appearance",
                            subtitle = "Applied live across Home, Library, Search, Queue & Walkman",
                            badgeText = currentCassetteStyle.displayName.uppercase()
                        )

                        // Visual 5-Option Cassette Style Selector
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(CassetteStyle.entries) { style ->
                                val isSelected = currentCassetteStyle == style
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 1.6.dp else 0.9.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { playbackManager.setCassetteStyle(style) }
                                        .testTag("cassette_theme_card_${style.name.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CassetteArtwork(
                                            track = null,
                                            isPlaying = isSelected,
                                            showMetadataOnLabel = false,
                                            compact = true,
                                            cassetteStyle = style,
                                            crossfadeDurationMs = 0,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1.56f)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = style.displayName,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

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
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { onAccentColorChanged(accent) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = accent.displayName,
                                            tint = Color.Black,
                                            modifier = Modifier.size(22.dp)
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
            CassetteSectionCard {
                Column(modifier = Modifier.padding(8.dp)) {
                    CassetteSectionHeader(
                        title = "Home, Notifications & Motion",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Now Playing Notification") },
                        supportingContent = {
                            val summary = if (notifPrefs.customizationEnabled) {
                                "${notifPrefs.privacyMode.label} • ${notifPrefs.artworkSize.label} art • Lock screen: ${notifPrefs.lockScreenMode.label}"
                            } else {
                                "Standard notification layout active"
                            }
                            Text(summary)
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier
                            .clickable { onNavigateToNotificationSettings() }
                            .testTag("settings_notification_btn")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Featured Album Animation") },
                        supportingContent = {
                            Text("${artAnimPrefs.spotlightTransitionStyle.label} • ${artAnimPrefs.spotlightTransitionSpeed.label} • ${artAnimPrefs.spotlightTransitionDirection.label}")
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.AutoAwesomeMotion, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier
                            .clickable { onNavigateToArtworkAnimationSettings() }
                            .testTag("settings_featured_album_anim_btn")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Home Artwork Animations") },
                        supportingContent = {
                            Text("${artAnimPrefs.style.label} style • ${artAnimPrefs.intensity.label} intensity")
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier
                            .clickable { onNavigateToArtworkAnimationSettings() }
                            .testTag("settings_artwork_animations_btn")
                    )
                }
            }
        }

        // Section: Audio DSP Controls
        item {
            CassetteSectionCard {
                Column(modifier = Modifier.padding(8.dp)) {
                    CassetteSectionHeader(
                        title = "Audio DSP Engine",
                        badgeText = "BIQUAD",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
            CassetteSectionCard {
                Column(modifier = Modifier.padding(8.dp)) {
                    CassetteSectionHeader(
                        title = "Media Filtering & Rules",
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
            CassetteSectionCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CassetteSectionHeader(title = "Album Artwork & Lyrics")

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
                                    ArtworkPathCache.clear()
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

        // Section: Walkman Cassette Player & Sleep Timer
        item {
            CassetteSectionCard {
                Column(modifier = Modifier.padding(8.dp)) {
                    CassetteSectionHeader(
                        title = "Walkman Deck & Timer",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Cassette Design") },
                        supportingContent = { Text("${currentCassetteStyle.displayName} • ${currentCassetteStyle.badgeText}") },
                        leadingContent = {
                            Icon(Icons.Rounded.Album, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier
                            .clickable { showCassetteStyleDialog = true }
                            .testTag("settings_cassette_style_btn")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ListItem(
                        headlineContent = { Text("Walkman Mechanical Sounds") },
                        supportingContent = { Text("Subtle tactile button & cassette door latch clicks") },
                        leadingContent = {
                            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = mechanicalSounds,
                                onCheckedChange = { playbackManager.setWalkmanMechanicalSounds(it) },
                                modifier = Modifier.testTag("settings_walkman_sounds_switch")
                            )
                        }
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
            CassetteSectionCard {
                Column(modifier = Modifier.padding(8.dp)) {
                    CassetteSectionHeader(
                        title = "Library Maintenance & Backup",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
    }

    // Cassette Design Customization Dialog
    if (showCassetteStyleDialog) {
        AlertDialog(
            onDismissRequest = { showCassetteStyleDialog = false },
            title = { Text("Customize Cassette Design") },
            text = {
                Column {
                    CassetteStyle.entries.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackManager.setCassetteStyle(style)
                                    showCassetteStyleDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentCassetteStyle == style,
                                onClick = {
                                    playbackManager.setCassetteStyle(style)
                                    showCassetteStyleDialog = false
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
                TextButton(onClick = { showCassetteStyleDialog = false }) { Text("Close") }
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
                Text("This will restore default AMOLED theme, Golden-Yellow accent, Flat 15-band equalizer, Natural stereo spatial audio, and Black and Gold cassette appearance. Your media files and playlists will NOT be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            settingsRepo.resetAllSettingsToDefault()
                            onThemeModeChanged(ThemeMode.AMOLED)
                            onAccentColorChanged(AccentColor.GOLD)
                            playbackManager.setCassetteStyle(CassetteStyle.BLACK_AND_GOLD)
                            playbackManager.setWalkmanMechanicalSounds(false)
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
