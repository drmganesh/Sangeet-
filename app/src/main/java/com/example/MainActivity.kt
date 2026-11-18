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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.data.model.VideoTrack
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.playback.PlaybackService
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.ArtworkMotionPlanner
import com.example.sangeet.ui.components.LocalCassetteStyle
import com.example.sangeet.ui.components.MiniPlayer
import com.example.sangeet.ui.components.rememberIsAppResumed
import com.example.sangeet.ui.motion.GlobalMotionPlanner
import com.example.sangeet.ui.motion.LocalSangeetMotion
import com.example.sangeet.ui.motion.MotionPreferencesSnapshot
import com.example.sangeet.ui.screens.*
import com.example.sangeet.ui.screens.player.FullScreenLyricsScreen
import com.example.sangeet.ui.screens.player.PlayerContainer
import com.example.sangeet.ui.startup.StartupAnimationPreferencesSnapshot
import com.example.sangeet.ui.startup.StartupAnimationStyle
import com.example.sangeet.ui.startup.WalkmanStartupOverlay
import com.example.ui.theme.AccentColor
import com.example.ui.theme.SangeetTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
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
    data object Profile : SubScreen()
    data object FullScreenLyrics : SubScreen()
    data class LyricsSettings(val openedFromFullScreenLyrics: Boolean = false) : SubScreen()
    data object CrossfadeSettings : SubScreen()
    data object AboutSangeet : SubScreen()
    data class BlacklistWhitelist(val initialTab: Int = 0) : SubScreen()
    data class VideoPlayer(val video: VideoTrack) : SubScreen()
}

class MainActivity : ComponentActivity() {

    private lateinit var playbackManager: PlaybackManager
    private lateinit var mediaRepository: MediaRepository
    private var openPlayerSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        playbackManager = PlaybackManager.getInstance(applicationContext)
        mediaRepository = MediaRepository(applicationContext)
        val settingsRepository = SettingsRepository.getInstance(applicationContext)

        var startupPrefsLoaded by mutableStateOf(false)
        var initialStartupPrefs by mutableStateOf(StartupAnimationPreferencesSnapshot())

        splashScreen.setKeepOnScreenCondition { !startupPrefsLoaded }
        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.view.animate()
                .alpha(0f)
                .setDuration(150L)
                .withEndAction { splashProvider.remove() }
                .start()
        }

        lifecycleScope.launch {
            initialStartupPrefs = runCatching {
                settingsRepository.startupAnimationPreferences.first()
            }.getOrDefault(StartupAnimationPreferencesSnapshot())
            startupPrefsLoaded = true
        }

        val launchedFromNotification = intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true
        if (launchedFromNotification) {
            openPlayerSignal++
        }

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.AMOLED)
            val accentColor by settingsRepository.accentColor.collectAsState(initial = AccentColor.GOLD)
            val activeCassetteStyle by playbackManager.cassetteStyle.collectAsState()
            val motionPrefs by settingsRepository.motionPreferences.collectAsState(initial = MotionPreferencesSnapshot())
            val isAppResumed by rememberIsAppResumed()
            val ctx = LocalContext.current
            val systemAnimatorsEnabled = remember(ctx, isAppResumed) {
                ArtworkMotionPlanner.isSystemAnimationEnabled(ctx)
            }
            val effectiveMotion = remember(motionPrefs, systemAnimatorsEnabled) {
                GlobalMotionPlanner.compute(motionPrefs, systemAnimatorsEnabled)
            }
            val scope = rememberCoroutineScope()

            CompositionLocalProvider(
                LocalCassetteStyle provides activeCassetteStyle,
                LocalSangeetMotion provides effectiveMotion
            ) {
                SangeetTheme(themeMode = themeMode, accentColor = accentColor) {
                    if (!startupPrefsLoaded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                    } else {
                        val startupPrefs by settingsRepository.startupAnimationPreferences.collectAsState(
                            initial = initialStartupPrefs
                        )
                        var hasCompletedStartupIntro by rememberSaveable {
                            mutableStateOf(
                                initialStartupPrefs.style == StartupAnimationStyle.DISABLED ||
                                    launchedFromNotification
                            )
                        }
                        var isPreviewStartup by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxSize()) {
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
                                onPreviewStartupAnimation = {
                                    isPreviewStartup = true
                                },
                                openPlayerSignal = openPlayerSignal
                            )

                            val shouldShowStartup =
                                isPreviewStartup || (!hasCompletedStartupIntro && openPlayerSignal == 0)

                            if (shouldShowStartup) {
                                WalkmanStartupOverlay(
                                    preferences = startupPrefs,
                                    isPreview = isPreviewStartup,
                                    isNotificationPlayerLaunch = openPlayerSignal > 0 && !isPreviewStartup,
                                    onFinished = {
                                        if (isPreviewStartup) {
                                            isPreviewStartup = false
                                        } else {
                                            hasCompletedStartupIntro = true
                                        }
                                    }
                                )
                            }
                        }
                    }
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
    onPreviewStartupAnimation: () -> Unit = {},
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
    val sangeetMotion = LocalSangeetMotion.current
    val tabStateHolder = rememberSaveableStateHolder()

    var currentTab by rememberSaveable { mutableStateOf(MainNavTab.HOME) }
    var currentSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    var isMiniPlayerCollapsed by rememberSaveable { mutableStateOf(false) }
    var libraryRouteRequest by remember { mutableStateOf<LibraryRouteRequest>(LibraryRouteRequest.None) }

    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0 && playbackManager.currentTrack.value != null) {
            currentSubScreen = SubScreen.None
            isMiniPlayerCollapsed = false
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

    // Automatically re-surface mini-player when track changes cleanly
    LaunchedEffect(currentTrack?.id) {
        if (currentTrack != null) {
            isMiniPlayerCollapsed = false
        }
    }

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
        val activeSub = currentSubScreen
        when {
            activeSub is SubScreen.LyricsSettings && activeSub.openedFromFullScreenLyrics -> {
                currentSubScreen = SubScreen.FullScreenLyrics
            }
            activeSub != SubScreen.None -> {
                currentSubScreen = SubScreen.None
            }
            isPlayerExpanded -> {
                isPlayerExpanded = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        bottomBar = {
            if (!isPlayerExpanded && currentSubScreen == SubScreen.None) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Persistent Mini Player above bottom navigation (swipe left/right prev/next, up expand, down collapse)
                    AnimatedVisibility(
                        visible = currentTrack != null && !isMiniPlayerCollapsed,
                        enter = sangeetMotion.miniPlayerEnter(),
                        exit = sangeetMotion.miniPlayerExit()
                    ) {
                        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
                        MiniPlayer(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            progress = progress,
                            onTogglePlayPause = { playbackManager.togglePlayPause() },
                            onNext = { playbackManager.playNext() },
                            onPrevious = { playbackManager.skipToPrevious(forcePreviousTrack = true) },
                            onOpenLyrics = {
                                isMiniPlayerCollapsed = false
                                currentSubScreen = SubScreen.FullScreenLyrics
                            },
                            onSwipeDownCollapse = { isMiniPlayerCollapsed = true },
                            onClick = { isPlayerExpanded = true },
                            animationPrefs = artworkAnimPrefs
                        )
                    }

                    // Compact floating cassette pill when user swiped down to collapse Mini-Player (audio stays playing!)
                    AnimatedVisibility(
                        visible = currentTrack != null && isMiniPlayerCollapsed,
                        enter = sangeetMotion.miniPlayerEnter(),
                        exit = sangeetMotion.miniPlayerExit()
                    ) {
                        Surface(
                            onClick = { isMiniPlayerCollapsed = false },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .testTag("mini_player_restore_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "Restore Mini Player",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = currentTrack?.title ?: "Now Playing",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Bottom Navigation Bar (Animated for hide on scroll)
                    AnimatedVisibility(
                        visible = isNavVisible,
                        enter = slideInVertically(
                            animationSpec = sangeetMotion.intOffsetSpring(),
                            initialOffsetY = { it }
                        ) + fadeIn(sangeetMotion.durationTween(180)),
                        exit = slideOutVertically(
                            animationSpec = sangeetMotion.intOffsetSpring(),
                            targetOffsetY = { it }
                        ) + fadeOut(sangeetMotion.durationTween(160))
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
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (selected && sangeetMotion.isMotionEnabled) 1.12f else 1f,
                                            animationSpec = sangeetMotion.floatSpring(),
                                            label = "navIconScale_${tab.name}"
                                        )
                                        val pillColor by animateColorAsState(
                                            targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            animationSpec = sangeetMotion.durationTween(180),
                                            label = "navPillColor_${tab.name}"
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = pillColor,
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
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .graphicsLayer {
                                                            scaleX = iconScale
                                                            scaleY = iconScale
                                                        }
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
            // Main Tab Content with directional transition & SaveableStateHolder preserving scroll state
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    sangeetMotion.tabTransitionSpec<MainNavTab>(direction)
                },
                label = "main_nav_tab_content"
            ) { activeTab ->
                tabStateHolder.SaveableStateProvider(key = activeTab.name) {
                    when (activeTab) {
                MainNavTab.HOME -> HomeScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onNavigateToEqualizer = { currentSubScreen = SubScreen.Equalizer },
                    onNavigateToSpatial = { currentSubScreen = SubScreen.SpatialAudio },
                    onNavigateToLibrary = { currentTab = MainNavTab.LIBRARY },
                    onOpenProfile = { currentSubScreen = SubScreen.Profile },
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
                    onNavigateToBlacklistWhitelist = { tab -> currentSubScreen = SubScreen.BlacklistWhitelist(tab) },
                    onNavigateToLyricsSettings = { currentSubScreen = SubScreen.LyricsSettings(openedFromFullScreenLyrics = false) },
                    onNavigateToCrossfadeSettings = { currentSubScreen = SubScreen.CrossfadeSettings },
                    onNavigateToAbout = { currentSubScreen = SubScreen.AboutSangeet },
                    onPreviewStartupAnimation = onPreviewStartupAnimation
                )
                    }
                }
            }

            // Sub-screen Overlays (Equalizer, Spatial Audio, Home & Nav Settings, Video Player)
            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.Equalizer,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                EqualizerScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.Profile,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                ProfileScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.SpatialAudio,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                SpatialAudioScreen(
                    playbackManager = playbackManager,
                    repository = repository,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.HomeNavSettings,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                HomeNavSettingsScreen(
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.NotificationSettings,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                NotificationSettingsScreen(
                    playbackManager = playbackManager,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.ArtworkAnimationSettings,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                ArtworkAnimationSettingsScreen(
                    playbackManager = playbackManager,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.BlacklistWhitelist,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                val blTab = (currentSubScreen as? SubScreen.BlacklistWhitelist)?.initialTab ?: 0
                BlacklistWhitelistScreen(
                    repository = repository,
                    initialTab = blTab,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.CrossfadeSettings,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                CrossfadeSettingsScreen(
                    playbackManager = playbackManager,
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.AboutSangeet,
                enter = sangeetMotion.subScreenEnter(),
                exit = sangeetMotion.subScreenExit()
            ) {
                AboutSangeetScreen(
                    onNavigateBack = { currentSubScreen = SubScreen.None }
                )
            }

            AnimatedVisibility(
                visible = currentSubScreen is SubScreen.VideoPlayer,
                enter = sangeetMotion.playerEnter(),
                exit = sangeetMotion.playerExit()
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
                enter = sangeetMotion.playerEnter(),
                exit = sangeetMotion.playerExit()
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
                    },
                    onOpenFullScreenLyrics = {
                        currentSubScreen = SubScreen.FullScreenLyrics
                    },
                    onOpenCrossfadeSettings = {
                        isPlayerExpanded = false
                        currentSubScreen = SubScreen.CrossfadeSettings
                    }
                )
            }
        }
    }

    // Edge-to-Edge Full-Screen Synchronized Lyrics Player
    AnimatedVisibility(
        visible = currentSubScreen is SubScreen.FullScreenLyrics,
        enter = sangeetMotion.playerEnter(),
        exit = sangeetMotion.playerExit()
    ) {
        FullScreenLyricsScreen(
            playbackManager = playbackManager,
            onClose = { currentSubScreen = SubScreen.None },
            onOpenLyricsSettings = {
                currentSubScreen = SubScreen.LyricsSettings(openedFromFullScreenLyrics = true)
            }
        )
    }

    // Edge-to-Edge Lyrics Appearance Customization Screen
    AnimatedVisibility(
        visible = currentSubScreen is SubScreen.LyricsSettings,
        enter = sangeetMotion.subScreenEnter(),
        exit = sangeetMotion.subScreenExit()
    ) {
        val fromLyrics = (currentSubScreen as? SubScreen.LyricsSettings)?.openedFromFullScreenLyrics == true
        LyricsSettingsScreen(
            onNavigateBack = {
                currentSubScreen = if (fromLyrics) SubScreen.FullScreenLyrics else SubScreen.None
            }
        )
    }
    }
}
