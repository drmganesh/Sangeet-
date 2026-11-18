package com.example.sangeet.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.RuleListKind
import com.example.sangeet.data.model.RuleType
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.rules.RuleConflict
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.*
import com.example.sangeet.ui.motion.LocalSangeetMotion
import kotlinx.coroutines.launch

private enum class SearchCategoryFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenPlaylist: (Long, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sangeetMotion = LocalSangeetMotion.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(SearchCategoryFilter.ALL) }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()

    val recentSearches by repository.recentSearches.collectAsState(initial = emptyList())
    val searchResults by repository.searchTracks(searchQuery).collectAsState(initial = emptyList())
    val allTracks by repository.allTracks.collectAsState(initial = emptyList())
    val allAlbums by repository.allAlbums.collectAsState(initial = emptyList())
    val allArtists by repository.allArtists.collectAsState(initial = emptyList())
    val allPlaylists by repository.allPlaylists.collectAsState(initial = emptyList())

    val currentTrack by playbackManager.currentTrack.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val isAppResumed by rememberIsAppResumed()
    var pendingConflict by remember { mutableStateOf<Pair<QuickRuleProposal, List<RuleConflict>>?>(null) }

    val hideKeyboardNestedScroll = remember(keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < -8f) {
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }

    val matchingAlbums = remember(searchQuery, allAlbums, allTracks) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim()
            val byAlbum = allTracks.groupBy { it.album.trim().lowercase() }
            allAlbums.filter { it.contains(q, ignoreCase = true) }.mapNotNull { album ->
                val list = byAlbum[album.trim().lowercase()].orEmpty()
                val rep = list.firstOrNull { it.artworkUri.isNotBlank() } ?: list.firstOrNull()
                if (rep != null) Triple(album, rep, list) else null
            }
        }
    }

    val matchingArtists = remember(searchQuery, allArtists, allTracks) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim()
            val byArtist = allTracks.groupBy { it.artist.trim().lowercase() }
            allArtists.filter { it.contains(q, ignoreCase = true) }.map { artist ->
                val list = byArtist[artist.trim().lowercase()].orEmpty()
                val rep = list.firstOrNull { it.artworkUri.isNotBlank() } ?: list.firstOrNull()
                Triple(artist, rep, list.size)
            }
        }
    }

    val matchingPlaylists = remember(searchQuery, allPlaylists) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim()
            allPlaylists.filter { it.name.contains(q, ignoreCase = true) }
        }
    }

    val totalMatches = searchResults.size + matchingAlbums.size + matchingArtists.size + matchingPlaylists.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        CassetteTopBar(
            title = "Search",
            subtitle = "100% Offline Music Index",
            badgeText = "TAPE FINDER"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // Animated Cassette Search Bar Input with Focus Elevation & Accent Highlight
            val barElevation by animateDpAsState(
                targetValue = if (isSearchFocused && sangeetMotion.isMotionEnabled) 10.dp else 2.dp,
                animationSpec = sangeetMotion.durationTween(180),
                label = "searchBarElevation"
            )
            val barBorderColor by animateColorAsState(
                targetValue = if (isSearchFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                },
                animationSpec = sangeetMotion.durationTween(180),
                label = "searchBarBorder"
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSearchFocused) 0.92f else 0.75f),
                border = BorderStroke(if (isSearchFocused) 1.4.dp else 1.dp, barBorderColor),
                shadowElevation = barElevation,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.length > 2) {
                            coroutineScope.launch { repository.recordSearch(it) }
                        }
                    },
                    interactionSource = searchInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input_field"),
                    placeholder = { Text("Search songs, albums, artists, playlists...") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear Search")
                            }
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            if (searchQuery.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SearchCategoryFilter.entries) { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("search_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Recent searches tape chips
            if (searchQuery.isEmpty() && recentSearches.isNotEmpty()) {
                CassetteSectionHeader(
                    title = "Recent Searches",
                    action = {
                        TextButton(onClick = {
                            coroutineScope.launch { repository.clearSearches() }
                        }) {
                            Text("Clear", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(recentSearches) { query ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                            onClick = { searchQuery = query }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = query,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Categorized Offline Results
            if (searchQuery.isNotEmpty()) {
                if (totalMatches == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CassetteEmptyStateCard(
                            icon = Icons.Rounded.SearchOff,
                            title = "No Offline Matches",
                            subtitle = "No songs, albums, artists, or playlists match \"$searchQuery\" on this device."
                        )
                    }
                } else {
                    AnimatedContent(
                        targetState = activeFilter,
                        transitionSpec = {
                            val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                            sangeetMotion.tabTransitionSpec<SearchCategoryFilter>(dir)
                        },
                        label = "search_category_content"
                    ) { filter ->
                    val showAlbums = matchingAlbums.isNotEmpty() && (filter == SearchCategoryFilter.ALL || filter == SearchCategoryFilter.ALBUMS)
                    val showArtists = matchingArtists.isNotEmpty() && (filter == SearchCategoryFilter.ALL || filter == SearchCategoryFilter.ARTISTS)
                    val showPlaylists = matchingPlaylists.isNotEmpty() && (filter == SearchCategoryFilter.ALL || filter == SearchCategoryFilter.PLAYLISTS)
                    val showSongs = searchResults.isNotEmpty() && (filter == SearchCategoryFilter.ALL || filter == SearchCategoryFilter.SONGS)

                    LazyColumn(
                        modifier = Modifier.nestedScroll(hideKeyboardNestedScroll),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Albums (Cassette Cards)
                        if (showAlbums) {
                            item {
                                CassetteSectionHeader(
                                    title = "Albums",
                                    badgeText = "${matchingAlbums.size}"
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(matchingAlbums, key = { "album_${it.first}" }) { (albumName, repTrack, albumQueue) ->
                                        val isAlbumPlaying = currentTrack?.album?.equals(albumName, ignoreCase = true) == true
                                        Column(
                                            modifier = Modifier
                                                .width(154.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onOpenAlbum(albumName) }
                                        ) {
                                            CassetteArtworkCard(
                                                track = repTrack,
                                                isCurrentlyPlaying = isAlbumPlaying,
                                                isAudioPlaying = isPlaying && isAppResumed,
                                                isVisible = true,
                                                crossfadeDurationMs = 180,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(106.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = albumName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isAlbumPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${albumQueue.size} tracks • ${repTrack.artist}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Artists (Normal Circular Artwork inside Cassette Cards — NOT cassettes)
                        if (showArtists) {
                            item {
                                CassetteSectionHeader(
                                    title = "Artists",
                                    badgeText = "${matchingArtists.size}"
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(matchingArtists, key = { "artist_${it.first}" }) { (artistName, repTrack, count) ->
                                        val isPlayingArtist = currentTrack?.artist?.equals(artistName, ignoreCase = true) == true
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                            border = BorderStroke(
                                                width = if (isPlayingArtist) 1.4.dp else 0.9.dp,
                                                color = if (isPlayingArtist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            ),
                                            modifier = Modifier
                                                .width(120.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onOpenArtist(artistName) }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                if (repTrack != null) {
                                                    TrackArtwork(
                                                        track = repTrack,
                                                        shape = CircleShape,
                                                        contentDescription = artistName,
                                                        modifier = Modifier.size(56.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Rounded.Person,
                                                        contentDescription = artistName,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(56.dp)
                                                    )
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = artistName,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$count songs",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Playlists (Cassette Collection Cards)
                        if (showPlaylists) {
                            item {
                                CassetteSectionHeader(
                                    title = "Playlists",
                                    badgeText = "${matchingPlaylists.size}"
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(matchingPlaylists, key = { "pl_${it.id}" }) { playlist ->
                                        val plTracks by repository.getPlaylistTracks(playlist.id).collectAsState(initial = emptyList())
                                        val coverTrack = remember(playlist.id, playlist.customArtworkUri, plTracks) {
                                            val firstWithArt = plTracks.firstOrNull { it.artworkUri.isNotBlank() } ?: plTracks.firstOrNull()
                                            AudioTrack(
                                                id = -playlist.id - 5000L,
                                                title = playlist.name,
                                                artist = "Playlist",
                                                album = "${plTracks.size} tracks",
                                                path = firstWithArt?.path.orEmpty(),
                                                artworkUri = playlist.customArtworkUri.ifBlank { firstWithArt?.artworkUri.orEmpty() }
                                            )
                                        }
                                        Column(
                                            modifier = Modifier
                                                .width(154.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onOpenPlaylist(playlist.id, playlist.name) }
                                        ) {
                                            CassetteArtworkCard(
                                                track = coverTrack,
                                                isCurrentlyPlaying = false,
                                                isAudioPlaying = false,
                                                isVisible = true,
                                                crossfadeDurationMs = 180,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(106.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${plTracks.size} tracks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Songs (Compact Miniature Cassette Rows)
                        if (showSongs) {
                            item {
                                CassetteSectionHeader(
                                    title = "Songs",
                                    badgeText = "${searchResults.size}"
                                )
                            }
                            items(searchResults, key = { "song_${it.id}" }) { track ->
                                var showMenu by remember { mutableStateOf(false) }
                                val isCurrent = currentTrack?.id == track.id
                                val activeSpin = isCurrent && isPlaying && isAppResumed
                                val reelAngle by rememberVisibleCassetteReelAngle(
                                    isActivelyPlayingAndVisible = activeSpin,
                                    seedAngleDegrees = ((track.id * 31L) % 360L).toFloat()
                                )

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(placementSpec = sangeetMotion.intOffsetSpring())
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { playbackManager.playTrack(track, searchResults) },
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                    },
                                    border = BorderStroke(
                                        width = if (isCurrent) 1.2.dp else 0.8.dp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
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
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${track.artist} • ${track.album}",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { showMenu = true },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Rounded.MoreVert, contentDescription = "Song Options")
                                            }
                                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Play") },
                                                    onClick = {
                                                        playbackManager.playTrack(track, searchResults)
                                                        showMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Play Next") },
                                                    onClick = {
                                                        playbackManager.playNextInQueue(track)
                                                        showMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Add to Queue") },
                                                    onClick = {
                                                        playbackManager.addToQueue(track)
                                                        showMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("View Album") },
                                                    onClick = {
                                                        showMenu = false
                                                        if (track.album.isNotBlank()) onOpenAlbum(track.album)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("View Artist") },
                                                    onClick = {
                                                        showMenu = false
                                                        if (track.artist.isNotBlank()) onOpenArtist(track.artist)
                                                    }
                                                )
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                    text = { Text("Add to Blacklist") },
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            executeQuickRuleWithConflictCheck(
                                                                repository = repository,
                                                                proposal = QuickRuleProposal(
                                                                    targetList = RuleListKind.BLACKLIST,
                                                                    ruleType = RuleType.SONG,
                                                                    identifier = track.path.ifBlank { track.id.toString() },
                                                                    title = track.title,
                                                                    displayPath = track.path.ifBlank { track.folderPath },
                                                                    documentId = track.documentId,
                                                                    treeUri = track.sourceTreeUri,
                                                                    mediaArtist = track.artist,
                                                                    mediaAlbum = track.album
                                                                ),
                                                                context = context,
                                                                onConflictsFound = { p, c -> pendingConflict = p to c }
                                                            )
                                                        }
                                                        showMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Add to Whitelist") },
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            executeQuickRuleWithConflictCheck(
                                                                repository = repository,
                                                                proposal = QuickRuleProposal(
                                                                    targetList = RuleListKind.WHITELIST,
                                                                    ruleType = RuleType.SONG,
                                                                    identifier = track.path.ifBlank { track.id.toString() },
                                                                    title = track.title,
                                                                    displayPath = track.path.ifBlank { track.folderPath },
                                                                    documentId = track.documentId,
                                                                    treeUri = track.sourceTreeUri,
                                                                    mediaArtist = track.artist,
                                                                    mediaAlbum = track.album
                                                                ),
                                                                context = context,
                                                                onConflictsFound = { p, c -> pendingConflict = p to c }
                                                            )
                                                        }
                                                        showMenu = false
                                                    }
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
            } else {
                // Offline cassette search prompt
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CassetteEmptyStateCard(
                        icon = Icons.Rounded.Search,
                        title = "100% Offline Instant Search",
                        subtitle = "Search across local songs, cassette albums, artists, and playlists instantly with zero internet usage."
                    )
                }
            }
        }
    }

    QuickRuleConflictHost(
        pendingConflict = pendingConflict,
        repository = repository,
        coroutineScope = coroutineScope,
        context = context,
        onClear = { pendingConflict = null }
    )
}
