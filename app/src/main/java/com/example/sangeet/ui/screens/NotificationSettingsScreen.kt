package com.example.sangeet.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.repository.LockScreenMediaMode
import com.example.sangeet.data.repository.NotificationArtworkSize
import com.example.sangeet.data.repository.NotificationPreferencesSnapshot
import com.example.sangeet.data.repository.NotificationPrivacyMode
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.playback.NotificationActionSlot
import com.example.sangeet.playback.NotificationFormattingHelper
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.playback.PlaybackService
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import com.example.sangeet.ui.components.CassetteTopBar
import com.example.sangeet.ui.components.MiniCassetteArtwork
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    playbackManager: PlaybackManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    val prefs by settingsRepo.notificationPreferences.collectAsState(initial = NotificationPreferencesSnapshot())
    val currentTrack by playbackManager.currentTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()

    var hasNotifPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        val msg = if (granted) "Notification permission granted" else "Notification permission denied"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("notification_settings_screen"),
        topBar = {
            CassetteTopBar(
                title = "Now Playing Notification",
                subtitle = "Media controls, artwork, and lock-screen privacy",
                badgeText = "SYS",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.testTag("notif_settings_reset_btn")
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "Reset Notification Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Notification Preview
            item {
                NotificationPreviewCard(
                    prefs = prefs,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying
                )
            }

            // 2. Android 13+ Permission Warning & Android 8+ System Channel Shortcut
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!hasNotifPermission) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasNotifPermission) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = if (hasNotifPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasNotifPermission) "Notification Access Active" else "Notification Permission Required",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasNotifPermission) {
                                        "Compatible with Android 8.0+ media controls & Bluetooth audio"
                                    } else {
                                        "Android 13+ requires notification permission to show Now Playing controls"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Button(
                                    onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.testTag("notif_grant_permission_btn")
                                ) {
                                    Text("Grant Permission")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            putExtra(Settings.EXTRA_CHANNEL_ID, PlaybackService.CHANNEL_ID)
                                        }
                                    } else {
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    }
                                    runCatching { context.startActivity(intent) }
                                },
                                modifier = Modifier.testTag("notif_system_channel_btn")
                            ) {
                                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("System Channel")
                            }
                        }
                    }
                }
            }

            // 3. Master Customization Toggle
            item {
                CassetteSectionCard(highlighted = prefs.customizationEnabled) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        NotificationToggleRow(
                            title = "Enable Notification Customization",
                            subtitle = if (prefs.customizationEnabled) {
                                "Custom rules active for shade & lock screen"
                            } else {
                                "Using SANGEET default balanced notification layout"
                            },
                            icon = Icons.Rounded.AutoFixHigh,
                            checked = prefs.customizationEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch { settingsRepo.setNotificationCustomizationEnabled(enabled) }
                            },
                            testTag = "notif_customization_switch"
                        )
                    }
                }
            }

            // 4. Metadata & Artwork Options
            item {
                val controlsEnabled = prefs.customizationEnabled
                CassetteSectionCard(
                    modifier = Modifier.alpha(if (controlsEnabled) 1f else 0.52f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CassetteSectionHeader(title = "Artwork & Metadata")

                        NotificationToggleRow(
                            title = "Show Album Artwork",
                            subtitle = "Display embedded ID3 art or offline fallback cover",
                            icon = Icons.Rounded.Album,
                            checked = prefs.showArtwork,
                            enabled = controlsEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setNotificationShowArtwork(it) }
                            },
                            testTag = "notif_show_artwork_switch"
                        )

                        AnimatedVisibility(visible = prefs.showArtwork) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Notification Artwork Resolution",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    NotificationArtworkSize.entries.forEach { size ->
                                        val selected = prefs.artworkSize == size
                                        FilterChip(
                                            selected = selected,
                                            enabled = controlsEnabled,
                                            onClick = {
                                                coroutineScope.launch { settingsRepo.setNotificationArtworkSize(size) }
                                            },
                                            label = { Text("${size.label} (${size.pixelSize}px)", fontSize = 12.sp) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("notif_art_size_${size.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        NotificationToggleRow(
                            title = "Show Artist Name",
                            subtitle = "Include performing artist below the song title",
                            icon = Icons.Rounded.Person,
                            checked = prefs.showArtist,
                            enabled = controlsEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setNotificationShowArtist(it) }
                            },
                            testTag = "notif_show_artist_switch"
                        )

                        NotificationToggleRow(
                            title = "Show Album Name",
                            subtitle = "Append album title to subtitle & metadata",
                            icon = Icons.Rounded.LibraryMusic,
                            checked = prefs.showAlbum,
                            enabled = controlsEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setNotificationShowAlbum(it) }
                            },
                            testTag = "notif_show_album_switch"
                        )

                        NotificationToggleRow(
                            title = "Enable Playback Progress",
                            subtitle = "Show seekable progress bar, elapsed position, and duration",
                            icon = Icons.Rounded.LinearScale,
                            checked = prefs.showProgress,
                            enabled = controlsEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setNotificationShowProgress(it) }
                            },
                            testTag = "notif_show_progress_switch"
                        )
                    }
                }
            }

            // 5. Playback Action Buttons
            item {
                val controlsEnabled = prefs.customizationEnabled
                CassetteSectionCard(
                    modifier = Modifier.alpha(if (controlsEnabled) 1f else 0.52f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CassetteSectionHeader(title = "Playback Controls")

                        NotificationToggleRow(
                            title = "Previous & Next Controls",
                            subtitle = "Queue track skip buttons (Bluetooth keys always stay active)",
                            icon = Icons.Rounded.SkipNext,
                            checked = prefs.showPrevNext,
                            enabled = controlsEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setNotificationShowPrevNext(it) }
                            },
                            testTag = "notif_show_prev_next_switch"
                        )

                        NotificationToggleRow(
                            title = "Seek Backward / Forward (10s)",
                            subtitle = "Quick 10-second jump controls inside expanded notification",
                            icon = Icons.Rounded.Forward10,
                            checked = prefs.showSeekButtons,
                            enabled = controlsEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setNotificationShowSeek(it) }
                            },
                            testTag = "notif_show_seek_switch"
                        )

                        NotificationToggleRow(
                            title = "Stop / Dismiss Control",
                            subtitle = "Stop playback and cleanly dismiss notification & foreground service",
                            icon = Icons.Rounded.Close,
                            checked = prefs.showStopButton,
                            enabled = controlsEnabled,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setNotificationShowStop(it) }
                            },
                            testTag = "notif_show_stop_switch"
                        )
                    }
                }
            }

            // 6. Lock Screen & Privacy
            item {
                val controlsEnabled = prefs.customizationEnabled
                CassetteSectionCard(
                    modifier = Modifier.alpha(if (controlsEnabled) 1f else 0.52f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CassetteSectionHeader(title = "Lock Screen & Privacy")

                        Text(
                            text = "Lock Screen Media Information",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LockScreenMediaMode.entries.forEach { mode ->
                                val selected = prefs.lockScreenMode == mode
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
                                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(enabled = controlsEnabled) {
                                            coroutineScope.launch { settingsRepo.setNotificationLockScreenMode(mode) }
                                        }
                                        .testTag("notif_lockscreen_${mode.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            enabled = controlsEnabled,
                                            onClick = {
                                                coroutineScope.launch { settingsRepo.setNotificationLockScreenMode(mode) }
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(mode.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        Text(
                            text = "Notification Privacy Mode",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            NotificationPrivacyMode.entries.forEach { mode ->
                                val selected = prefs.privacyMode == mode
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
                                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(enabled = controlsEnabled) {
                                            coroutineScope.launch { settingsRepo.setNotificationPrivacyMode(mode) }
                                        }
                                        .testTag("notif_privacy_${mode.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            enabled = controlsEnabled,
                                            onClick = {
                                                coroutineScope.launch { settingsRepo.setNotificationPrivacyMode(mode) }
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(mode.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Notification Settings?") },
            text = { Text("Restores default artwork, controls, progress bar, and public lock-screen settings for SANGEET notifications.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            settingsRepo.resetNotificationSettingsToDefault()
                            showResetDialog = false
                            Toast.makeText(context, "Notification defaults restored", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("notif_reset_confirm_btn")
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NotificationPreviewCard(
    prefs: NotificationPreferencesSnapshot,
    currentTrack: AudioTrack?,
    isPlaying: Boolean
) {
    val eff = prefs.effective()
    val formatted = remember(currentTrack?.id, prefs) {
        NotificationFormattingHelper.formatMetadata(
            rawTitle = currentTrack?.title ?: "Midnight Raag (360° Master)",
            rawArtist = currentTrack?.artist ?: "SANGEET Studio",
            rawAlbum = currentTrack?.album ?: "Offline Spatial Sessions",
            prefs = prefs
        )
    }
    val slots = remember(prefs) { NotificationFormattingHelper.planActionSlots(prefs) }
    val artSizeDp = when (eff.artworkSize) {
        NotificationArtworkSize.SMALL -> 48.dp
        NotificationArtworkSize.MEDIUM -> 58.dp
        NotificationArtworkSize.LARGE -> 68.dp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notif_live_preview_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "SANGEET • LIVE PREVIEW",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "${eff.privacyMode.label} • ${eff.lockScreenMode.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (formatted.showArtwork) {
                        MiniCassetteArtwork(
                            track = currentTrack,
                            isPlaying = isPlaying,
                            modifier = Modifier.size(width = artSizeDp * 1.45f, height = artSizeDp)
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatted.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (formatted.subtitle.isNotBlank()) {
                            Text(
                                text = formatted.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (eff.showProgress) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { 0.42f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("01:48", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("04:15", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    slots.forEach { slot ->
                        val icon = when (slot) {
                            NotificationActionSlot.PREVIOUS -> Icons.Rounded.SkipPrevious
                            NotificationActionSlot.SEEK_BACK -> Icons.Rounded.Replay10
                            NotificationActionSlot.PLAY_PAUSE -> if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled
                            NotificationActionSlot.SEEK_FORWARD -> Icons.Rounded.Forward10
                            NotificationActionSlot.NEXT -> Icons.Rounded.SkipNext
                            NotificationActionSlot.STOP -> Icons.Rounded.Close
                        }
                        val isPrimary = slot == NotificationActionSlot.PLAY_PAUSE
                        Icon(
                            imageVector = icon,
                            contentDescription = slot.name,
                            tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(if (isPrimary) 38.dp else 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
