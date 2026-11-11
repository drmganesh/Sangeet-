package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.VideoTrack
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.playback.PlaybackManager
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

sealed class SubScreen {
    data object None : SubScreen()
    data object Equalizer : SubScreen()
    data object SpatialAudio : SubScreen()
    data class VideoPlayer(val video: VideoTrack) : SubScreen()
}

class MainActivity : ComponentActivity() {

    private lateinit var playbackManager: PlaybackManager
    private lateinit var mediaRepository: MediaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        playbackManager = PlaybackManager.getInstance(applicationContext)
        mediaRepository = MediaRepository(applicationContext)

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.AMOLED) }
            var accentColor by remember { mutableStateOf(AccentColor.CYAN) }

            SangeetTheme(themeMode = themeMode, accentColor = accentColor) {
                SangeetApp(
                    playbackManager = playbackManager,
                    repository = mediaRepository,
                    themeMode = themeMode,
                    onThemeModeChanged = { themeMode = it },
                    accentColor = accentColor,
                    onAccentColorChanged = { accentColor = it }
                )
            }
        }
    }
}

@Composable
fun SangeetApp(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    accentColor: AccentColor,
    onAccentColorChanged: (AccentColor) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(MainNavTab.HOME) }
    var currentSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val currentTrack by playbackManager.currentTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val positionMs by playbackManager.currentPositionMs.collectAsState()
    val durationMs by playbackManager.durationMs.collectAsState()

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
        modifier = Modifier.fillMaxSize(),
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
                            onClick = { isPlayerExpanded = true }
                        )
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(imageVector = tab.icon, contentDescription = tab.title)
                                },
                                label = { Text(tab.title) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
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
                    onNavigateToLibrary = { currentTab = MainNavTab.LIBRARY }
                )
                MainNavTab.SEARCH -> SearchScreen(
                    playbackManager = playbackManager,
                    repository = repository
                )
                MainNavTab.LIBRARY -> LibraryScreen(
                    playbackManager = playbackManager,
                    repository = repository
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
                    onNavigateToSpatial = { currentSubScreen = SubScreen.SpatialAudio }
                )
            }

            // Sub-screen Overlays (Equalizer, Spatial Audio, Video Player)
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
