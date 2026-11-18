package com.example.sangeet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.data.repository.UserProfileSnapshot
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.AnimatedArtistCard
import com.example.sangeet.ui.components.AnimatedFeaturedSpotlightCard
import com.example.sangeet.ui.components.AnimatedQuickPickCard
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.ArtworkMotionPlanner
import com.example.sangeet.ui.components.CassetteArtworkCard
import com.example.sangeet.ui.components.CassetteEmptyStateCard
import com.example.sangeet.ui.components.MiniCassetteArtwork
import com.example.sangeet.ui.components.rememberCarouselBreathingPhase
import com.example.sangeet.ui.components.rememberIsAppResumed
import com.example.sangeet.ui.components.rememberVisibleCassetteReelAngle
import com.example.sangeet.ui.motion.GlobalMotionPlanner
import com.example.sangeet.ui.motion.MotionPreferencesSnapshot
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSpatial: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenPlaylist: (Long, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    val allTracks by repository.allTracks.collectAsState(initial = emptyList())
    val recentlyPlayed by repository.recentlyPlayed.collectAsState(initial = emptyList())
    val recentlyAdded by repository.recentlyAdded.collectAsState(initial = emptyList())
    val mostPlayed by repository.mostPlayed.collectAsState(initial = emptyList())
    val favoriteTracks by repository.favoriteTracks.collectAsState(initial = emptyList())
    val allArtists by repository.allArtists.collectAsState(initial = emptyList())
    val allAlbums by repository.allAlbums.collectAsState(initial = emptyList())
    val allPlaylists by repository.allPlaylists.collectAsState(initial = emptyList())

    val showGreeting by settingsRepo.showGreeting.collectAsState(initial = true)
    val spotlightHome by settingsRepo.spotlightHome.collectAsState(initial = true)
    val userProfile by settingsRepo.userProfile.collectAsState(initial = UserProfileSnapshot())
    val animPrefs by settingsRepo.artworkAnimationPreferences.collectAsState(
        initial = ArtworkAnimationPreferencesSnapshot()
    )
    val globalMotionPrefs by settingsRepo.motionPreferences.collectAsState(
        initial = MotionPreferencesSnapshot()
    )
    val isAppResumed by rememberIsAppResumed()
    val systemAnimatorsEnabled = remember(context, isAppResumed) {
        ArtworkMotionPlanner.isSystemAnimationEnabled(context)
    }
    val motion = remember(animPrefs, globalMotionPrefs, systemAnimatorsEnabled) {
        GlobalMotionPlanner.mergeWithArtworkPrefs(animPrefs, globalMotionPrefs, systemAnimatorsEnabled)
    }
    val currentlyPlayingTrack by playbackManager.currentTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()

    val featuredTracks = remember(favoriteTracks, allTracks) {
        val combined = LinkedHashMap<Long, AudioTrack>()
        favoriteTracks.forEach { combined[it.id] = it }
        // Prioritize distinct albums so swiping showcases distinct album covers when available
        val seenAlbums = mutableSetOf<String>()
        favoriteTracks.forEach { seenAlbums.add(it.album.trim().lowercase()) }
        allTracks.forEach { track ->
            val albumKey = track.album.trim().lowercase()
            if (albumKey.isNotBlank() && seenAlbums.add(albumKey)) {
                combined[track.id] = track
            }
        }
        allTracks.forEach { track ->
            if (combined.size < 12) {
                combined.putIfAbsent(track.id, track)
            }
        }
        combined.values.take(12).toList()
    }
    var featuredIndex by rememberSaveable { mutableIntStateOf(0) }
    var featuredNavDirection by remember { mutableIntStateOf(1) }

    val listState = rememberLazyListState()
    val isSpotlightVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "featured_spotlight" }
        }
    }
    val isQuickPicksVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "quick_picks_section" }
        }
    }
    val isLocalMixVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "local_mix_section" }
        }
    }
    val isRecentlyPlayedVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "recently_played_section" }
        }
    }
    val isAlbumsVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "featured_albums_section" }
        }
    }
    val isPlaylistsVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "home_playlists_section" }
        }
    }

    val quickPicksTracks = remember(allTracks) { allTracks.take(8) }
    val localMixTracks = remember(allTracks) { allTracks.shuffled().take(6) }
    val albumRepresentativeTracks = remember(allAlbums, allTracks) {
        val grouped = allTracks.groupBy { it.album.trim().lowercase() }
        allAlbums.mapNotNull { albumName ->
            val albumTracks = grouped[albumName.trim().lowercase()].orEmpty()
            albumTracks.firstOrNull { it.artworkUri.isNotBlank() } ?: albumTracks.firstOrNull()
        }.take(10)
    }
    val artistTrackMap = remember(allTracks) {
        allTracks.groupBy { it.artist.trim().lowercase() }
            .mapValues { (_, list) ->
                list.firstOrNull { it.artworkUri.isNotBlank() } ?: list.firstOrNull()
            }
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 120.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Greeting & Brand Bar with Circular Profile Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileAvatarCircle(
                    avatarPathOrUri = userProfile.avatarUriOrPath,
                    displayName = userProfile.displayName,
                    size = 42.dp,
                    onClick = onOpenProfile,
                    showGlow = true,
                    modifier = Modifier.testTag("home_profile_btn")
                )
                Spacer(Modifier.width(12.dp))
                if (showGreeting) {
                    Column(modifier = Modifier.testTag("home_greeting_badge")) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "SANGEET",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = "SANGEET",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Empty State: Prompt user to Scan or Generate High-Fidelity Demo Audio
        if (allTracks.isEmpty()) {
            item {
                CassetteEmptyStateCard(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "No Offline Music Found",
                    subtitle = "Scan your device's audio files or load built-in offline studio tracks to immediately experience cassette playback, 15-band EQ, and 360° spatial audio.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    coroutineScope.launch { repository.scanMusic() }
                                }
                            ) {
                                Text("Scan Storage")
                            }
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch { repository.generateDemoTracks() }
                                }
                            ) {
                                Text("Load Demo Music")
                            }
                        }
                    }
                )
            }
        }

        // -------------------------------------------------------------
        // SPOTLIGHT HOME LAYOUT (Featured Hero + Rich Discovery Carousels)
        // -------------------------------------------------------------
        if (spotlightHome && featuredTracks.isNotEmpty()) {
            val safeFeaturedIndex = featuredIndex.coerceIn(0, featuredTracks.lastIndex)
            val featuredTrack = featuredTracks[safeFeaturedIndex]
            val isFeaturedPlaying = currentlyPlayingTrack?.id == featuredTrack.id && isPlaying

            // 1. Featured Music Hero Section (Slide, Fade, 3D Page Turn, Ken Burns, Parallax, Swipe)
            item(key = "featured_spotlight") {
                AnimatedFeaturedSpotlightCard(
                    featuredTrack = featuredTrack,
                    motion = motion,
                    isSpotlightVisible = isSpotlightVisible,
                    isAppResumed = isAppResumed,
                    listState = listState,
                    itemKey = "featured_spotlight",
                    navigationDirection = featuredNavDirection,
                    isFeaturedPlaying = isFeaturedPlaying,
                    featuredCount = featuredTracks.size,
                    featuredIndex = safeFeaturedIndex,
                    onSwipeNext = {
                        if (featuredTracks.size > 1) {
                            featuredNavDirection = 1
                            featuredIndex = (safeFeaturedIndex + 1) % featuredTracks.size
                        }
                    },
                    onSwipePrevious = {
                        if (featuredTracks.size > 1) {
                            featuredNavDirection = -1
                            featuredIndex = (safeFeaturedIndex - 1 + featuredTracks.size) % featuredTracks.size
                        }
                    },
                    onPlayFeatured = {
                        if (currentlyPlayingTrack?.id == featuredTrack.id) {
                            playbackManager.togglePlayPause()
                        } else {
                            playbackManager.playTrack(featuredTrack, allTracks)
                        }
                    }
                )
            }

            // Carousel: Quick Picks
            item(key = "quick_picks_section") {
                val quickPicksRowState = rememberLazyListState()
                val quickPicksPhase = rememberCarouselBreathingPhase(
                    isSectionVisible = isQuickPicksVisible,
                    isAppResumed = isAppResumed,
                    isBreathingEnabled = motion.cardBreathingActive
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "QUICK PICKS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyRow(
                        state = quickPicksRowState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(quickPicksTracks, key = { _, t -> t.id }) { index, track ->
                            AnimatedQuickPickCard(
                                track = track,
                                index = index,
                                isCurrentlyPlaying = currentlyPlayingTrack?.id == track.id,
                                motion = motion,
                                breathingPhaseState = quickPicksPhase,
                                rowState = quickPicksRowState,
                                onClick = { playbackManager.playTrack(track, allTracks) },
                                isAudioPlaying = isPlaying && isAppResumed,
                                isSectionVisible = isQuickPicksVisible
                            )
                        }
                    }
                }
            }

            // Carousel: Spotlight Artists
            if (allArtists.isNotEmpty()) {
                item(key = "spotlight_artists_section") {
                    val artistRowState = rememberLazyListState()
                    val visibleArtists = remember(allArtists) { allArtists.take(8) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "SPOTLIGHT ARTISTS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            state = artistRowState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(visibleArtists, key = { _, a -> a }) { index, artist ->
                                AnimatedArtistCard(
                                    artist = artist,
                                    index = index,
                                    representativeTrack = artistTrackMap[artist.trim().lowercase()],
                                    isSelected = currentlyPlayingTrack?.artist?.equals(artist, ignoreCase = true) == true,
                                    motion = motion,
                                    rowState = artistRowState,
                                    onClick = { onOpenArtist(artist) }
                                )
                            }
                        }
                    }
                }
            }

            // Carousel: Your Local Mix
            item(key = "local_mix_section") {
                val localMixRowState = rememberLazyListState()
                val localMixPhase = rememberCarouselBreathingPhase(
                    isSectionVisible = isLocalMixVisible,
                    isAppResumed = isAppResumed,
                    isBreathingEnabled = motion.cardBreathingActive
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "YOUR LOCAL MIX",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyRow(
                        state = localMixRowState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(localMixTracks, key = { _, t -> t.id }) { index, track ->
                            AnimatedQuickPickCard(
                                track = track,
                                index = index,
                                isCurrentlyPlaying = currentlyPlayingTrack?.id == track.id,
                                motion = motion,
                                breathingPhaseState = localMixPhase,
                                rowState = localMixRowState,
                                onClick = { playbackManager.playTrack(track, allTracks) },
                                isAudioPlaying = isPlaying && isAppResumed,
                                isSectionVisible = isLocalMixVisible
                            )
                        }
                    }
                }
            }

            // Recently Added Tracks (Vertical List with Mini Cassette Artwork)
            if (recentlyAdded.isNotEmpty()) {
                item {
                    Text(
                        text = "RECENTLY ADDED",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                items(recentlyAdded.take(5), key = { "recent_added_${it.id}" }) { track ->
                    HomeTrackListItem(
                        track = track,
                        isCurrentTrack = currentlyPlayingTrack?.id == track.id,
                        isAudioPlaying = isPlaying && isAppResumed,
                        onPlay = { playbackManager.playTrack(track, recentlyAdded) },
                        onToggleFavorite = {
                            coroutineScope.launch {
                                repository.setTrackFavorite(track.id, !track.isFavorite)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // Carousel: Recently Played Cassettes
            if (recentlyPlayed.isNotEmpty()) {
                item(key = "recently_played_section") {
                    val recentRowState = rememberLazyListState()
                    val recentPhase = rememberCarouselBreathingPhase(
                        isSectionVisible = isRecentlyPlayedVisible,
                        isAppResumed = isAppResumed,
                        isBreathingEnabled = motion.cardBreathingActive
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "RECENTLY PLAYED",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            state = recentRowState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(recentlyPlayed.take(10), key = { _, t -> t.id }) { index, track ->
                                AnimatedQuickPickCard(
                                    track = track,
                                    index = index,
                                    isCurrentlyPlaying = currentlyPlayingTrack?.id == track.id,
                                    motion = motion,
                                    breathingPhaseState = recentPhase,
                                    rowState = recentRowState,
                                    onClick = { playbackManager.playTrack(track, recentlyPlayed) },
                                    isAudioPlaying = isPlaying && isAppResumed,
                                    isSectionVisible = isRecentlyPlayedVisible
                                )
                            }
                        }
                    }
                }
            }

            // Carousel: Featured Albums (Each Album Displayed as a Printed Cassette)
            if (albumRepresentativeTracks.isNotEmpty()) {
                item(key = "featured_albums_section") {
                    val albumsRowState = rememberLazyListState()
                    val albumsPhase = rememberCarouselBreathingPhase(
                        isSectionVisible = isAlbumsVisible,
                        isAppResumed = isAppResumed,
                        isBreathingEnabled = motion.cardBreathingActive
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "FEATURED ALBUMS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            state = albumsRowState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(albumRepresentativeTracks, key = { _, t -> t.id }) { index, albumTrack ->
                                val albumQueue = remember(albumTrack.album, allTracks) {
                                    allTracks.filter { it.album.equals(albumTrack.album, ignoreCase = true) }
                                        .ifEmpty { listOf(albumTrack) }
                                }
                                AnimatedQuickPickCard(
                                    track = albumTrack,
                                    index = index,
                                    isCurrentlyPlaying = currentlyPlayingTrack?.album?.equals(albumTrack.album, ignoreCase = true) == true,
                                    motion = motion,
                                    breathingPhaseState = albumsPhase,
                                    rowState = albumsRowState,
                                    onClick = { playbackManager.playTrack(albumQueue.first(), albumQueue) },
                                    isAudioPlaying = isPlaying && isAppResumed,
                                    isSectionVisible = isAlbumsVisible,
                                    titleOverride = albumTrack.album.ifBlank { albumTrack.title },
                                    subtitleOverride = albumTrack.artist
                                )
                            }
                        }
                    }
                }
            }

            // Carousel: Playlists (Displayed as Printed Cassettes)
            if (allPlaylists.isNotEmpty()) {
                item(key = "home_playlists_section") {
                    val playlistsRowState = rememberLazyListState()
                    val playlistsPhase = rememberCarouselBreathingPhase(
                        isSectionVisible = isPlaylistsVisible,
                        isAppResumed = isAppResumed,
                        isBreathingEnabled = motion.cardBreathingActive
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "YOUR PLAYLISTS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            state = playlistsRowState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(allPlaylists.take(10), key = { _, p -> -p.id - 1000L }) { index, playlist ->
                                val playlistTracks by repository.getPlaylistTracks(playlist.id)
                                    .collectAsState(initial = emptyList())
                                val coverTrack = remember(playlist.id, playlist.customArtworkUri, playlistTracks) {
                                    val firstTrackWithArt = playlistTracks.firstOrNull { it.artworkUri.isNotBlank() }
                                        ?: playlistTracks.firstOrNull()
                                    val artUri = playlist.customArtworkUri.ifBlank { firstTrackWithArt?.artworkUri.orEmpty() }
                                    AudioTrack(
                                        id = -playlist.id - 1000L,
                                        title = playlist.name,
                                        artist = "Playlist",
                                        album = "${playlistTracks.size} tracks",
                                        durationMs = 0L,
                                        path = firstTrackWithArt?.path.orEmpty(),
                                        artworkUri = artUri
                                    )
                                }
                                AnimatedQuickPickCard(
                                    track = coverTrack,
                                    index = index,
                                    isCurrentlyPlaying = false,
                                    motion = motion,
                                    breathingPhaseState = playlistsPhase,
                                    rowState = playlistsRowState,
                                    onClick = {
                                        if (playlistTracks.isNotEmpty()) {
                                            playbackManager.playTrack(playlistTracks.first(), playlistTracks)
                                        } else {
                                            onNavigateToLibrary()
                                        }
                                    },
                                    isAudioPlaying = false,
                                    isSectionVisible = isPlaylistsVisible,
                                    titleOverride = playlist.name,
                                    subtitleOverride = "${playlistTracks.size} tracks"
                                )
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // CLASSIC HOME LAYOUT (Compact Traditional Clean Lists)
        // -------------------------------------------------------------
        if (!spotlightHome && allTracks.isNotEmpty()) {
            // Compact Quick Picks
            item {
                Text(
                    text = "ALL TRACKS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            items(allTracks.take(12), key = { "classic_all_${it.id}" }) { track ->
                HomeTrackListItem(
                    track = track,
                    isCurrentTrack = currentlyPlayingTrack?.id == track.id,
                    isAudioPlaying = isPlaying && isAppResumed,
                    onPlay = { playbackManager.playTrack(track, allTracks) },
                    onToggleFavorite = {
                        coroutineScope.launch {
                            repository.setTrackFavorite(track.id, !track.isFavorite)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (favoriteTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "FAVORITES",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                items(favoriteTracks.take(6), key = { "classic_fav_${it.id}" }) { track ->
                    HomeTrackListItem(
                        track = track,
                        isCurrentTrack = currentlyPlayingTrack?.id == track.id,
                        isAudioPlaying = isPlaying && isAppResumed,
                        onPlay = { playbackManager.playTrack(track, favoriteTracks) },
                        onToggleFavorite = {
                            coroutineScope.launch {
                                repository.setTrackFavorite(track.id, !track.isFavorite)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTrackCard(
    track: AudioTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentlyPlaying: Boolean = false,
    isAudioPlaying: Boolean = false
) {
    Surface(
        modifier = modifier
            .width(152.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column {
            CassetteArtworkCard(
                track = track,
                isCurrentlyPlaying = isCurrentlyPlaying,
                isAudioPlaying = isAudioPlaying,
                isVisible = true,
                crossfadeDurationMs = 200,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
                    .testTag("home_track_cassette_${track.id}")
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrentlyPlaying) Color(0xFFFFD666) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HomeTrackListItem(
    track: AudioTrack,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isAudioPlaying: Boolean = false
) {
    val activeSpin = isCurrentTrack && isAudioPlaying
    val seedAngle = remember(track.id) { ((track.id * 41L) % 360L).toFloat() }
    val reelAngle by rememberVisibleCassetteReelAngle(
        isActivelyPlayingAndVisible = activeSpin,
        seedAngleDegrees = seedAngle
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPlay() },
        color = if (isCurrentTrack) Color(0xFFE8B429).copy(alpha = 0.08f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniCassetteArtwork(
                track = track,
                isPlaying = activeSpin,
                reelAngleDegrees = reelAngle,
                modifier = Modifier
                    .height(42.dp)
                    .testTag("home_list_cassette_${track.id}")
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentTrack) Color(0xFFFFD666) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (track.isFavorite) Color(0xFFFF2A6D) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
