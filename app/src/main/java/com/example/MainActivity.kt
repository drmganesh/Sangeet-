package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.data.model.VideoTrack
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.playback.PlaybackService
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.LocalCassetteStyle
import com.example.sangeet.ui.components.MiniPlayer
import com.example.sangeet.ui.screens.*
import com.example.sangeet.ui.screens.player.PlayerContainer
import com.example.ui.theme.AccentColor
import com.example.ui.theme.SangeetTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

enum class MainNavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SEARCH("Search", Icons.Rounded.Search),
    LIBRARY("Library", Icons.Rounded.LibraryMusic),
    FILES("Files", Icons.Rounded.FolderOpen),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

sealed class LibraryRouteRequest {
    data object None : LibraryRouteRequest()
    data class OpenAlbum(val albumName: String, val nonce: Long = System.nanoTime()) : LibraryRouteRequest()
    data class OpenArtist(val artistName: String, val nonce: Long = System.nanoTime()) : LibraryRouteRequest()
    data class OpenPlaylist(val playlistId: Long, val playlistName: String, val nonce: Long = System.nanoTime()) : LibraryRouteRequest()
}

sealed class SubScreen {
    data object None : SubScreen()
    data object Equalizer : SubScreen()
    data object SpatialAudio : SubScreen()
    data object HomeNavSettings : SubScreen()
    data object NotificationSettings : SubScreen()
    data object ArtworkAnimationSettings : SubScreen()
    data class BlacklistWhitelist(val initialTab: Int = 0) : SubScreen()
    data class VideoPlayer(val video: VideoTrack) : SubScreen()
}

class MainActivity : ComponentActivity() {

    private lateinit var playbackManager: PlaybackManager
    private lateinit var mediaRepository: MediaRepository
    private var openPlayerSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        playbackManager = PlaybackManager.getInstance(applicationContext)
        mediaRepository = MediaRepository(applicationContext)
        val settingsRepository = SettingsRepository.getInstance(applicationContext)

        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerSignal++
        }

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.AMOLED)
            val accentColor by settingsRepository.accentColor.collectAsState(initial = AccentColor.GOLD)
            val activeCassetteStyle by playbackManager.cassetteStyle.collectAsState()
            val scope = rememberCoroutineScope()

            CompositionLocalProvider(LocalCassetteStyle provides activeCassetteStyle) {
                SangeetTheme(themeMode = themeMode, accentColor = accentColor) {
                    SangeetApp(
                        playbackManager = playbackManager,
                        repository = mediaRepository,
                        settingsRepository = settingsRepository,
                        themeMode = themeMode,
                        onThemeModeChanged = { newMode ->
                            scope.launch { settingsRepository.setThemeMode(newMode) }
                        },
                        accentColor = accentColor,
                        onAccentColorChanged = { newAccent ->
                            scope.launch { settingsRepository.setAccentColor(newAccent) }
                        },
                        openPlayerSignal = openPlayerSignal
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false)) {
            openPlayerSignal++
        }
    }
}

@Composable
fun SangeetApp(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    settingsRepository: SettingsRepository,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    accentColor: AccentColor,
    onAccentColorChanged: (AccentColor) -> Unit,
    openPlayerSignal: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Persistent Home & Navigation settings
    val startScreenPref by settingsRepository.startScreen.collectAsState(initial = "HOME")
    val lastVisitedPref by settingsRepository.lastVisitedScreen.collectAsState(initial = "HOME")
    val nonExpressiveNavBar by settingsRepository.nonExpressiveNavBar.collectAsState(initial = false)
    val tabNamesMode by settingsRepository.tabNamesMode.collectAsState(initial = "AUTO")
    val hideNavWhileScrolling by settingsRepository.hideNavWhileScrolling.collectAsState(initial = false)
    val rotateWithDevice by settingsRepository.rotateWithDevice.collectAsState(initial = false)
    val artworkAnimPrefs by settingsRepository.artworkAnimationPreferences.collectAsState(
        initial = ArtworkAnimationPreferencesSnapshot()
    )

    var currentTab by remember { mutableStateOf(MainNavTab.HOME) }
    var currentSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var libraryRouteRequest by remember { mutableStateOf<LibraryRouteRequest>(LibraryRouteRequest.None) }

    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0 && playbackManager.currentTrack.value != null) {
            currentSubScreen = SubScreen.None
            isPlayerExpanded = true
        }
    }

    // Start Screen Restoration on launch
    var hasAppliedStartScreen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startScreenPref, lastVisitedPref) {
        if (!hasAppliedStartScreen) {
            val initialTab = when (startScreenPref) {
                "LAST" -> {
                    try {
                        MainNavTab.valueOf(lastVisitedPref)
                    } catch (_: Exception) {
                        MainNavTab.HOME
                    }
                }
                "SEARCH" -> MainNavTab.SEARCH
                "LIBRARY" -> MainNavTab.LIBRARY
                else -> MainNavTab.HOME
            }
            currentTab = initialTab
            hasAppliedStartScreen = true
        }
    }

    // Dynamic Device Rotation
    LaunchedEffect(rotateWithDevice) {
        val activity = context as? ComponentActivity
        activity?.requestedOrientation = if (rotateWithDevice) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val currentTrack by playbackManager.currentTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val positionMs by playbackManager.currentPositionMs.collectAsState()
    val durationMs by playbackManager.durationMs.collectAsState()

    // Hide while scrolling nested scroll listener
    var isNavVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember(hideNavWhileScrolling) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (hideNavWhileScrolling) {
                    if (available.y < -12f) {
                        isNavVisible = false
                    } else if (available.y > 12f) {
                        isNavVisible = true
                    }
                } else {
                    isNavVisible = true
                }
                return Offset.Zero
            }
        }
    }

    // Request Storage and Notification Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            coroutineScope.launch {
                repository.scanMusic()
                repository.scanVideos()
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())

        // Ensure database has initial demo music loaded if library is empty
        coroutineScope.launch {
            val count = repository.scanMusic()
            if (count == 0) {
                repository.generateDemoTracks()
            }
        }
    }

    // Back handling for sub-screens and full player
    BackHandler(enabled = isPlayerExpanded || currentSubScreen != SubScreen.None) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (currentSubScreen != SubScreen.None) {
            currentSubScreen = SubScreen.None
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        bottomBar = {
            if (!isPlayerExpanded && currentSubScreen == SubScreen.None) {
                Column {
                    // Persistent Mini Player above bottom navigation
                    if (currentTrack != null) {
                        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
                        MiniPlayer(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            progress = progress,
                            onTogglePlayPause = { playbackManager.togglePlayPause() },
                            onNext = { playbackManager.playNext() },
                            onClick = { isPlayerExpanded = true },
                            animationPrefs = artworkAnimPrefs
                        )
                    }

                    // Bottom Navigation Bar (Animated for hide on scroll)
                    AnimatedVisibility(
                        visible = isNavVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        if (nonExpressiveNavBar) {
                            // Standard M3 Docked Navigation Bar
                            NavigationBar(
                                windowInsets = WindowInsets.navigationBars,
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                MainNavTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    val showLabel = when (tabNamesMode) {
                                        "ALWAYS" -> true
                                        "OPEN_ONLY" -> selected
                                        "NEVER" -> false
                                        else -> false // AUTO (standard M3 responsive)
                                    }

                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            currentTab = tab
                                            coroutineScope.launch {
                                                settingsRepository.setLastVisitedScreen(tab.name)
                                            }
                                        },
                                        icon = {
                                            Icon(imageVector = tab.icon, contentDescription = tab.title)
                                        },
                                        label = if (showLabel || tabNamesMode == "AUTO") {
                                            { Text(tab.title) }
                                        } else null,
                                        alwaysShowLabel = tabNamesMode == "ALWAYS",
                                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        } else {
                            // Expressive Floating Pill Cassette Navigation Bar
                            Surface(
                                shape = RoundedCornerShape(26.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                border = BorderStroke(
                                    0.9.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                ),
                                tonalElevation = 8.dp,
                                shadowElevation = 12.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .clip(RoundedCornerShape(26.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MainNavTab.entries.forEach { tab ->
                                        val selected = currentTab == tab
                                        val showLabel = when (tabNamesMode) {
                                            "ALWAYS" -> true
                                            "OPEN_ONLY" -> selected
                                            "NEVER" -> false
                                            else -> true // AUTO
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(18.dp))
                                                .clickable {
                                                    currentTab = tab
                                                    coroutineScope.launch {
                                                        settingsRepository.setLastVisitedScreen(tab.name)
                                                    }
                                                }
                                                .padding(vertical = 6.dp)
                                                .testTag("nav_tab_${tab.name.lowercase()}")
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.title,
                                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                if (showLabel) {
                                                    Text(
                                                        text = tab.title,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 11.sp
                                                        ),
                                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Tab Content
            when (currentTab) {
                MainNavTab.HOME -> HomeScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onNavigateToEqualizer = { currentSubScreen = SubScreen.Equalizer },
                    onNavigateToSpatial = { currentSubScreen = SubScreen.SpatialAudio },
                    onNavigateToLibrary = { currentTab = MainNavTab.LIBRARY },
                    onOpenArtist = { artistName ->
                        libraryRouteRequest = LibraryRouteRequest.OpenArtist(artistName)
                        currentTab = MainNavTab.LIBRARY
                    },
                    onOpenAlbum = { albumName ->
                        libraryRouteRequest = LibraryRouteRequest.OpenAlbum(albumName)
                        currentTab = MainNavTab.LIBRARY
                    },
                    onOpenPlaylist = { playlistId, playlistName ->
                        libraryRouteRequest = LibraryRouteRequest.OpenPlaylist(playlistId, playlistName)
                        currentTab = MainNavTab.LIBRARY
                    }
                )
                MainNavTab.SEARCH -> SearchScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onOpenAlbum = { albumName ->
                        libraryRouteRequest = LibraryRouteRequest.OpenAlbum(albumName)
                        currentTab = MainNavTab.LIBRARY
                    },
                    onOpenArtist = { artistName ->
                        libraryRouteRequest = LibraryRouteRequest.OpenArtist(artistName)
                        currentTab = MainNavTab.LIBRARY
                    },
                    onOpenPlaylist = { playlistId, playlistName ->
                        libraryRouteRequest = LibraryRouteRequest.OpenPlaylist(playlistId, playlistName)
                        currentTab = MainNavTab.LIBRARY
                    }
                )
                MainNavTab.LIBRARY -> LibraryScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    routeRequest = libraryRouteRequest,
                    onRouteRequestConsumed = { libraryRouteRequest = LibraryRouteRequest.None }
                )
                MainNavTab.FILES -> FilesScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onPlayVideo = { currentSubScreen = SubScreen.VideoPlayer(it) }
                )
                MainNavTab.SETTINGS -> SettingsScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    currentThemeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    currentAccentColor = accentColor,
                    onAccentColorChanged = onAccentColorChanged,
                    onNavigateToEqualizer = { currentSubScreen = SubScreen.Equalizer },
                    onNavigateToSpatial = { currentSubScreen = SubScreen.SpatialAudio },
                    onNavigateToHomeNavSettings = { currentSubScreen = SubScreen.HomeNavSettings },
                    onNavigateToNotificationSettings = { currentSubScreen = SubScreen.NotificationSettings },
                    onNavigateToArtworkAnimationSettings = { currentSubScreen = SubScreen.ArtworkAnimationSettings },
                    onNavigateToBlacklistWhitelist = { tab -> currentSubScreen = SubScreen.BlacklistWhitelist(tab) }
                )
            }

            // Sub-screen Overlays (Equalizer, Spatial Audio, Home & Nav Settings, Video Player)
            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.Equalizer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                EqualizerScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.SpatialAudio,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                SpatialAudioScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.HomeNavSettings,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                HomeNavSettingsScreen(
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.NotificationSettings,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                NotificationSettingsScreen(
                    playbackManager = playbackManager,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.ArtworkAnimationSettings,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                ArtworkAnimationSettingsScreen(
                    playbackManager = playbackManager,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.BlacklistWhitelist,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                val blTab = (currentSubScreen as? SubScreen.BlacklistWhitelist)?.initialTab ?: 0
                BlacklistWhitelistScreen(
                    repository = repository,
                    initialTab = blTab,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.VideoPlayer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                val video = (currentSubScreen as? SubScreen.VideoPlayer)?.video
                if (video != null) {
                    VideoPlayerScreen(
                        video = video,
                        repository = repository,
                        onClose = { currentSubScreen = SubScreen.None }
                    )
                }
            }

            // Fullscreen Audio Player Modal
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                PlayerContainer(
                    playbackManager = playbackManager,
                    onDismiss = { isPlayerExpanded = false },
                    onNavigateToEqualizer = {
                        isPlayerExpanded = false
                        currentSubScreen = SubScreen.Equalizer
                    },
                    onNavigateToSpatial = {
                        isPlayerExpanded = false
                        currentSubScreen = SubScreen.SpatialAudio
                    }
                )
            }
        }
    }
}
