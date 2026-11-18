package com.example.sangeet.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.data.repository.UserProfileSnapshot
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import com.example.sangeet.ui.components.CassetteTopBar
import com.example.sangeet.ui.components.MiniCassetteArtwork
import com.example.sangeet.ui.components.rememberIsAppResumed
import com.example.sangeet.ui.components.rememberVisibleCassetteReelAngle
import com.example.sangeet.ui.motion.LocalSangeetMotion
import com.example.sangeet.ui.motion.sangeetPressScale
import com.example.ui.theme.SangeetGold
import com.example.ui.theme.SangeetGoldBright
import com.example.ui.theme.SangeetGoldDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ProfileAmoledBlack = Color(0xFF000000)
private val ProfileGold = SangeetGold
private val ProfileGoldBright = SangeetGoldBright
private val ProfileGoldDark = SangeetGoldDark

/**
 * Reusable black-and-gold circular profile avatar shared by the Home top-left button
 * and the Profile header. Smoothly crossfades when photo updates or resets.
 */
@Composable
fun ProfileAvatarCircle(
    avatarPathOrUri: String,
    displayName: String,
    size: Dp,
    onClick: (() -> Unit)? = null,
    showGlow: Boolean = true,
    badgeIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val motion = LocalSangeetMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    var imageLoadFailed by remember(avatarPathOrUri) { mutableStateOf(false) }

    val imageModel: Any? = remember(avatarPathOrUri) {
        when {
            avatarPathOrUri.isBlank() -> null
            avatarPathOrUri.startsWith("/") -> {
                val f = File(avatarPathOrUri)
                if (f.exists() && f.length() > 0L) f else null
            }
            else -> Uri.parse(avatarPathOrUri)
        }
    }

    val clickableModifier = if (onClick != null) {
        modifier
            .minimumInteractiveComponentSize()
            .sangeetPressScale(interactionSource)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, color = ProfileGoldBright),
                onClick = onClick
            )
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center
    ) {
        if (showGlow && !motion.isReducedMotion && motion.decorativeAnimationsEnabled) {
            Canvas(modifier = Modifier.size(size + 10.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            ProfileGoldBright.copy(alpha = 0.28f),
                            ProfileGold.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(
                width = if (size >= 72.dp) 2.2.dp else 1.5.dp,
                brush = Brush.linearGradient(listOf(ProfileGoldBright, ProfileGold, ProfileGoldDark))
            ),
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = if (showGlow) 8.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = ProfileGold,
                    spotColor = ProfileGoldBright
                )
        ) {
            AnimatedContent(
                targetState = if (imageModel != null && !imageLoadFailed) imageModel else "default_$displayName",
                transitionSpec = {
                    val ms = if (motion.isReducedMotion) 110 else motion.cassetteTransitionMs
                    (fadeIn(motion.durationTween(ms)) + scaleIn(initialScale = 0.9f, animationSpec = motion.durationTween(ms)))
                        .togetherWith(fadeOut(motion.durationTween(ms)) + scaleOut(targetScale = 0.92f, animationSpec = motion.durationTween(ms)))
                },
                label = "profile_avatar_transition"
            ) { state ->
                if (state is File || state is Uri) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(state)
                            .crossfade(!motion.isReducedMotion)
                            .build(),
                        contentDescription = "$displayName profile picture",
                        contentScale = ContentScale.Crop,
                        onError = { imageLoadFailed = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        ProfileGoldBright,
                                        ProfileGold,
                                        Color(0xFFC48E18)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val trimmed = displayName.trim()
                        val showInitial = trimmed.isNotEmpty() && !trimmed.equals("SANGEET Listener", ignoreCase = true)
                        if (showInitial) {
                            Text(
                                text = trimmed.first().uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = (size.value * 0.44f).sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = Color(0xFF0A0905)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "Profile",
                                tint = Color(0xFF0A0905),
                                modifier = Modifier.size(size * 0.54f)
                            )
                        }
                    }
                }
            }
        }

        if (badgeIcon != null) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF0E0E12),
                border = BorderStroke(1.2.dp, ProfileGoldBright),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.30f).coerceAtLeast(24.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = ProfileGoldBright,
                        modifier = Modifier.size((size * 0.16f).coerceAtLeast(13.dp))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sangeetMotion = LocalSangeetMotion.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    val profile by settingsRepo.userProfile.collectAsState(initial = UserProfileSnapshot())
    val allTracks by repository.allTracks.collectAsState(initial = emptyList())
    val favoriteTracks by repository.favoriteTracks.collectAsState(initial = emptyList())
    val mostPlayed by repository.mostPlayed.collectAsState(initial = emptyList())
    val recentlyPlayed by repository.recentlyPlayed.collectAsState(initial = emptyList())
    val totalListeningTimeMs by repository.totalListeningTimeMs.collectAsState(initial = 0L)
    val totalSongsPlayed by repository.totalSongsPlayed.collectAsState(initial = 0)

    val currentPlayingTrack by playbackManager.currentTrack.collectAsState()
    val isAudioPlaying by playbackManager.isPlaying.collectAsState()
    val isAppResumed by rememberIsAppResumed()

    LaunchedEffect(Unit) {
        settingsRepo.ensureProfileInitialized()
    }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showManageProfileDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    val handlePickedImageUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val resultPath = persistProfileAvatarLocally(
                    context = context,
                    uri = uri,
                    previousPath = profile.avatarUriOrPath
                )
                if (resultPath != null) {
                    settingsRepo.setProfileAvatarPath(resultPath)
                    Toast.makeText(context, "Profile picture updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not load selected image", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Native Photo Picker on Android 13+, automatically falls back to SAF OpenDocument on Android 8–12
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> handlePickedImageUri(uri) }

    // Fallback gallery/content picker if a custom Android 8 ROM lacks Photo Picker / OpenDocument support
    val fallbackGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> handlePickedImageUri(uri) }

    val launchGalleryPicker: () -> Unit = {
        runCatching {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            runCatching {
                fallbackGalleryLauncher.launch("image/*")
            }.onFailure {
                Toast.makeText(context, "No gallery app found to pick an image", Toast.LENGTH_LONG).show()
            }
        }
    }

    val resetProfileAvatar: () -> Unit = {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                deleteLocalProfileAvatarIfOwned(context, profile.avatarUriOrPath)
            }
            settingsRepo.setProfileAvatarPath("")
            Toast.makeText(context, "Profile picture reset", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        containerColor = ProfileAmoledBlack,
        topBar = {
            CassetteTopBar(
                title = "Listener Profile",
                subtitle = "100% Local Offline Identity",
                badgeText = "OFFLINE",
                onNavigateBack = onNavigateBack,
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0A0804), ProfileAmoledBlack, Color(0xFF060608))
                    )
                )
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Profile Header Card
            item {
                CassetteSectionCard(highlighted = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProfileAvatarCircle(
                            avatarPathOrUri = profile.avatarUriOrPath,
                            displayName = profile.displayName,
                            size = 104.dp,
                            onClick = launchGalleryPicker,
                            showGlow = true,
                            badgeIcon = Icons.Rounded.PhotoCamera,
                            modifier = Modifier.testTag("profile_avatar_large")
                        )

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp
                            ),
                            color = Color(0xFFF6F1E6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("profile_display_name")
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = profile.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB4AB96),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(10.dp))

                        val createdFormatted = remember(profile.createdAtEpochMs) {
                            if (profile.createdAtEpochMs > 0L) {
                                SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                    .format(Date(profile.createdAtEpochMs))
                            } else {
                                "Today"
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CassetteHeaderBadge(text = "SINCE $createdFormatted".uppercase(), accentColor = ProfileGold)
                            CassetteHeaderBadge(text = "DEVICE ONLY", accentColor = ProfileGoldBright)
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showEditNameDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProfileGold,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_edit_btn")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Profile", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = launchGalleryPicker,
                                border = BorderStroke(1.dp, ProfileGold.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProfileGoldBright),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_pick_photo_header_btn")
                            ) {
                                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Photo", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Listening Statistics Section (100% real local Room data)
            if (profile.showListeningStats) {
                item {
                    CassetteSectionCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CassetteSectionHeader(
                                title = "Listening Statistics",
                                subtitle = "Computed live from your local library",
                                badgeText = "LOCAL DATA"
                            )

                            val listeningFormatted = remember(totalListeningTimeMs) {
                                formatListeningDuration(totalListeningTimeMs ?: 0L)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProfileStatBox(
                                    icon = Icons.Rounded.LibraryMusic,
                                    label = "Total Songs",
                                    value = allTracks.size.toString(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_stat_total_songs")
                                )
                                ProfileStatBox(
                                    icon = Icons.Rounded.Favorite,
                                    label = "Favorites",
                                    value = favoriteTracks.size.toString(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_stat_favorites")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProfileStatBox(
                                    icon = Icons.Rounded.Schedule,
                                    label = "Listening Time",
                                    value = listeningFormatted,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_stat_listening_time")
                                )
                                ProfileStatBox(
                                    icon = Icons.Rounded.GraphicEq,
                                    label = "Total Plays",
                                    value = totalSongsPlayed.toString(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_stat_total_plays")
                                )
                            }
                        }
                    }
                }

                // Most Played Songs (actual local playCount > 0)
                item {
                    CassetteSectionCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CassetteSectionHeader(
                                title = "Most Played Songs",
                                badgeText = "${mostPlayed.size} TRACKED"
                            )

                            if (mostPlayed.isEmpty()) {
                                Text(
                                    text = "Play songs in SANGEET to record your genuine top rotation here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF9C9484)
                                )
                            } else {
                                mostPlayed.take(5).forEach { track ->
                                    ProfileTrackStatRow(
                                        track = track,
                                        trailingLabel = "${track.playCount}x",
                                        isCurrentTrack = currentPlayingTrack?.id == track.id,
                                        isAudioPlaying = isAudioPlaying && isAppResumed,
                                        onClick = { playbackManager.playTrack(track, mostPlayed) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Recently Played Music (actual local lastPlayedTimestamp > 0)
                item {
                    CassetteSectionCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CassetteSectionHeader(
                                title = "Recently Played",
                                badgeText = "HISTORY"
                            )

                            if (recentlyPlayed.isEmpty()) {
                                Text(
                                    text = "No playback history yet. Start a cassette to see your recent tracks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF9C9484)
                                )
                            } else {
                                recentlyPlayed.take(5).forEach { track ->
                                    ProfileTrackStatRow(
                                        track = track,
                                        trailingLabel = formatCompactDuration(track.durationMs),
                                        isCurrentTrack = currentPlayingTrack?.id == track.id,
                                        isAudioPlaying = isAudioPlaying && isAppResumed,
                                        onClick = { playbackManager.playTrack(track, recentlyPlayed) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Profile Options Section
            item {
                CassetteSectionCard {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            CassetteSectionHeader(
                                title = "Profile Options",
                                badgeText = "LOCAL"
                            )
                        }

                        ProfileOptionRow(
                            icon = Icons.Rounded.AddPhotoAlternate,
                            title = "Change Profile Picture",
                            subtitle = "Pick a photo from your device gallery",
                            onClick = launchGalleryPicker,
                            testTag = "profile_change_photo_btn"
                        )

                        ProfileOptionRow(
                            icon = Icons.Rounded.Badge,
                            title = "Edit Display Name",
                            subtitle = profile.displayName,
                            onClick = { showEditNameDialog = true },
                            testTag = "profile_edit_name_btn"
                        )

                        ProfileOptionRow(
                            icon = Icons.Rounded.HideImage,
                            title = "Reset Profile Picture",
                            subtitle = if (profile.avatarUriOrPath.isNotBlank()) "Restore default golden avatar" else "Using default golden avatar",
                            enabled = profile.avatarUriOrPath.isNotBlank(),
                            onClick = resetProfileAvatar,
                            testTag = "profile_reset_photo_btn"
                        )

                        ProfileOptionRow(
                            icon = Icons.Rounded.Tune,
                            title = "Manage Local Profile Info",
                            subtitle = if (profile.showListeningStats) "Stats visible • Offline preferences" else "Stats hidden • Offline preferences",
                            onClick = { showManageProfileDialog = true },
                            testTag = "profile_manage_info_btn"
                        )

                        ProfileOptionRow(
                            icon = Icons.Rounded.VerifiedUser,
                            title = "Privacy Information",
                            subtitle = "100% offline • Zero cloud uploads or tracking",
                            onClick = { showPrivacyDialog = true },
                            testTag = "profile_privacy_btn"
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Edit Profile Dialog (Display Name & Tagline)
    if (showEditNameDialog) {
        var nameInput by remember(profile.displayName) { mutableStateOf(profile.displayName) }
        var taglineInput by remember(profile.tagline) { mutableStateOf(profile.tagline) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = Color(0xFF14120D),
            titleContentColor = ProfileGoldBright,
            textContentColor = Color(0xFFF6F1E6),
            title = { Text("Edit Local Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 48) nameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ProfileGold,
                            unfocusedBorderColor = ProfileGold.copy(alpha = 0.4f),
                            focusedLabelColor = ProfileGoldBright,
                            cursorColor = ProfileGoldBright
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )
                    OutlinedTextField(
                        value = taglineInput,
                        onValueChange = { if (it.length <= 80) taglineInput = it },
                        label = { Text("Profile Bio / Tagline") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ProfileGold,
                            unfocusedBorderColor = ProfileGold.copy(alpha = 0.4f),
                            focusedLabelColor = ProfileGoldBright,
                            cursorColor = ProfileGoldBright
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_tagline_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            settingsRepo.setProfileDisplayName(nameInput)
                            settingsRepo.setProfileTagline(taglineInput)
                            showEditNameDialog = false
                            Toast.makeText(context, "Profile saved locally", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfileGold,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("profile_save_name_btn")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = Color(0xFF9C9484))
                }
            }
        )
    }

    // Manage Local Profile Info & Stats Dialog
    if (showManageProfileDialog) {
        AlertDialog(
            onDismissRequest = { showManageProfileDialog = false },
            containerColor = Color(0xFF14120D),
            titleContentColor = ProfileGoldBright,
            textContentColor = Color(0xFFF6F1E6),
            title = { Text("Manage Local Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                coroutineScope.launch {
                                    settingsRepo.setProfileShowListeningStats(!profile.showListeningStats)
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Listening Statistics", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Display library counts and local playback stats",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA8A08E)
                            )
                        }
                        Switch(
                            checked = profile.showListeningStats,
                            onCheckedChange = {
                                coroutineScope.launch { settingsRepo.setProfileShowListeningStats(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ProfileGoldBright
                            )
                        )
                    }

                    HorizontalDivider(color = ProfileGold.copy(alpha = 0.2f))

                    OutlinedButton(
                        onClick = {
                            showManageProfileDialog = false
                            showClearHistoryConfirm = true
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Listening Time History")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageProfileDialog = false }) {
                    Text("Done", color = ProfileGoldBright, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            containerColor = Color(0xFF14120D),
            titleContentColor = ProfileGoldBright,
            textContentColor = Color(0xFFF6F1E6),
            title = { Text("Clear Listening History?") },
            text = {
                Text("This clears your local listening time log without deleting any music files, playlists, or equalizer settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.clearHistory()
                            showClearHistoryConfirm = false
                            Toast.makeText(context, "Listening history cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", color = Color(0xFF9C9484))
                }
            }
        )
    }

    // Privacy Information Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = Color(0xFF14120D),
            titleContentColor = ProfileGoldBright,
            textContentColor = Color(0xFFF6F1E6),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = ProfileGoldBright,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Offline Privacy Guarantee", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "• Your display name, tagline, and profile picture are stored strictly in private on-device storage.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• SANGEET never connects to the internet, never uses analytics or ads, and never uploads images or listening statistics anywhere.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Images are picked using Android's privacy-preserving Photo Picker with zero broad storage permissions.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfileGold,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ProfileStatBox(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0E0F13),
        border = BorderStroke(0.9.dp, ProfileGold.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ProfileGold.copy(alpha = 0.14f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ProfileGoldBright,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    ),
                    color = Color(0xFFF6F1E6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA8A08E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProfileTrackStatRow(
    track: AudioTrack,
    trailingLabel: String,
    isCurrentTrack: Boolean,
    isAudioPlaying: Boolean,
    onClick: () -> Unit
) {
    val activeSpin = isCurrentTrack && isAudioPlaying
    val reelAngle by rememberVisibleCassetteReelAngle(
        isActivelyPlayingAndVisible = activeSpin,
        seedAngleDegrees = ((track.id * 37L) % 360L).toFloat()
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrentTrack) ProfileGold.copy(alpha = 0.12f) else Color(0xFF0D0E12),
        border = BorderStroke(
            width = 0.8.dp,
            color = if (isCurrentTrack) ProfileGoldBright.copy(alpha = 0.65f) else ProfileGold.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniCassetteArtwork(
                track = track,
                isPlaying = activeSpin,
                reelAngleDegrees = reelAngle,
                modifier = Modifier.height(38.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isCurrentTrack) ProfileGoldBright else Color(0xFFF5F2EA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA8A08E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CassetteHeaderBadge(text = trailingLabel, accentColor = ProfileGoldBright)
        }
    }
}

@Composable
private fun ProfileOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = ProfileGold.copy(alpha = 0.14f * contentAlpha),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProfileGoldBright.copy(alpha = contentAlpha),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF5F2EA).copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA8A08E).copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF8C8472).copy(alpha = contentAlpha)
        )
    }
}

private suspend fun persistProfileAvatarLocally(
    context: Context,
    uri: Uri,
    previousPath: String
): String? = withContext(Dispatchers.IO) {
    runCatching {
        // Validate that the URI resolves to a readable bitmap header
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return@withContext null

        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            return@withContext null
        }

        val profileDir = File(context.filesDir, "profile").apply { mkdirs() }
        val ext = when {
            boundsOpts.outMimeType?.contains("png", ignoreCase = true) == true -> "png"
            boundsOpts.outMimeType?.contains("webp", ignoreCase = true) == true -> "webp"
            else -> "jpg"
        }
        val destFile = File(profileDir, "avatar_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext null

        if (!destFile.exists() || destFile.length() == 0L) {
            destFile.delete()
            return@withContext null
        }

        // Remove previous owned avatar file cleanly
        deleteLocalProfileAvatarIfOwned(context, previousPath, preservePath = destFile.absolutePath)

        // Best-effort persistable URI permission if supported by provider
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        destFile.absolutePath
    }.getOrNull()
}

private fun deleteLocalProfileAvatarIfOwned(
    context: Context,
    path: String,
    preservePath: String? = null
) {
    if (path.isBlank() || path == preservePath || !path.startsWith("/")) return
    runCatching {
        val profileDir = File(context.filesDir, "profile").canonicalPath
        val target = File(path)
        if (target.canonicalPath.startsWith(profileDir) && target.exists()) {
            target.delete()
        }
    }
}

internal fun formatListeningDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0m"
    val totalMinutes = durationMs / 60_000L
    if (totalMinutes <= 0L) return "< 1m"
    val hours = totalMinutes / 60L
    val mins = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"
}

private fun formatCompactDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSec = durationMs / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    return "%d:%02d".format(m, s)
}
