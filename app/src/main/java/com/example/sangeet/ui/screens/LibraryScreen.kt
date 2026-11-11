package com.example.sangeet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.Playlist
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.playback.PlaybackManager
import kotlinx.coroutines.launch

enum class LibraryTab(val title: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites")
}

enum class SortOrder(val displayName: String) {
    TITLE("Title (A-Z)"),
    ARTIST("Artist (A-Z)"),
    DATE_ADDED("Recently Added"),
    DURATION("Duration")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(LibraryTab.SONGS) }
    var currentSort by remember { mutableStateOf(SortOrder.TITLE) }
    var showSortMenu by remember { mutableStateOf(false) }

    val allTracks by repository.allTracks.collectAsState(initial = emptyList())
    val favoriteTracks by repository.favoriteTracks.collectAsState(initial = emptyList())
    val allArtists by repository.allArtists.collectAsState(initial = emptyList())
    val allAlbums by repository.allAlbums.collectAsState(initial = emptyList())
    val allGenres by repository.allGenres.collectAsState(initial = emptyList())
    val playlists by repository.allPlaylists.collectAsState(initial = emptyList())

    // Sub-view filters (e.g. user clicked an Album or Artist)
    var activeFilterTitle by remember { mutableStateOf<String?>(null) }
    var activeFilterTracks by remember { mutableStateOf<List<AudioTrack>?>(null) }

    // Playlist dialogs
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var trackToAddToPlaylist by remember { mutableStateOf<AudioTrack?>(null) }

    val sortedTracks = remember(allTracks, currentSort) {
        when (currentSort) {
            SortOrder.TITLE -> allTracks.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> allTracks.sortedBy { it.artist.lowercase() }
            SortOrder.DATE_ADDED -> allTracks.sortedByDescending { it.dateAdded }
            SortOrder.DURATION -> allTracks.sortedByDescending { it.durationMs }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Library Header
        TopAppBar(
            title = {
                Text(
                    text = activeFilterTitle ?: "Music Library",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            navigationIcon = if (activeFilterTitle != null) {
                {
                    IconButton(onClick = {
                        activeFilterTitle = null
                        activeFilterTracks = null
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            } else { {} },
            actions = {
                if (selectedTab == LibraryTab.SONGS && activeFilterTitle == null) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOrder.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.displayName) },
                                    onClick = {
                                        currentSort = sort
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (selectedTab == LibraryTab.PLAYLISTS && activeFilterTitle == null) {
                    IconButton(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = "New Playlist")
                    }
                }
            }
        )

        // Category Tabs (Only if not viewing filtered subcategory)
        if (activeFilterTitle == null) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                divider = {}
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (activeFilterTracks != null) {
                // Filtered tracks list
                TrackList(
                    tracks = activeFilterTracks!!,
                    onPlay = { playbackManager.playTrack(it, activeFilterTracks!!) },
                    onAddToQueue = { playbackManager.addToQueue(it) },
                    onPlayNext = { playbackManager.playNextInQueue(it) },
                    onAddToPlaylist = { trackToAddToPlaylist = it },
                    onToggleFavorite = { t ->
                        coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) }
                    }
                )
            } else {
                when (selectedTab) {
                    LibraryTab.SONGS -> {
                        TrackList(
                            tracks = sortedTracks,
                            onPlay = { playbackManager.playTrack(it, sortedTracks) },
                            onAddToQueue = { playbackManager.addToQueue(it) },
                            onPlayNext = { playbackManager.playNextInQueue(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onToggleFavorite = { t ->
                                coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) }
                            }
                        )
                    }
                    LibraryTab.ALBUMS -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(allAlbums) { album ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                repository.getTracksByAlbum(album).collect {
                                                    activeFilterTitle = album
                                                    activeFilterTracks = it
                                                }
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.Album,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = album,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Album",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LibraryTab.ARTISTS -> {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(allArtists) { artist ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                repository.getTracksByArtist(artist).collect {
                                                    activeFilterTitle = artist
                                                    activeFilterTracks = it
                                                }
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            text = artist,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LibraryTab.GENRES -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(allGenres) { genre ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                repository.getTracksByGenre(genre).collect {
                                                    activeFilterTitle = genre
                                                    activeFilterTracks = it
                                                }
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(
                                        modifier = Modifier.padding(16.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LibraryTab.PLAYLISTS -> {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlists) { playlist ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                repository.getPlaylistTracks(playlist.id).collect {
                                                    activeFilterTitle = playlist.name
                                                    activeFilterTracks = it
                                                }
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = {
                                            coroutineScope.launch { repository.deletePlaylist(playlist.id) }
                                        }) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LibraryTab.FAVORITES -> {
                        TrackList(
                            tracks = favoriteTracks,
                            onPlay = { playbackManager.playTrack(it, favoriteTracks) },
                            onAddToQueue = { playbackManager.addToQueue(it) },
                            onPlayNext = { playbackManager.playNextInQueue(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onToggleFavorite = { t ->
                                coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) }
                            }
                        )
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        coroutineScope.launch {
                            repository.createPlaylist(newPlaylistName.trim())
                            showCreatePlaylistDialog = false
                            newPlaylistName = ""
                        }
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Track to Playlist Dialog
    if (trackToAddToPlaylist != null) {
        AlertDialog(
            onDismissRequest = { trackToAddToPlaylist = null },
            title = { Text("Add to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists found. Create one first!")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(playlists) { p ->
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.addTrackToPlaylist(p.id, trackToAddToPlaylist!!.id)
                                        trackToAddToPlaylist = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(p.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { trackToAddToPlaylist = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun TrackList(
    tracks: List<AudioTrack>,
    onPlay: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No tracks available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(tracks) { track ->
            var showItemMenu by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPlay(track) },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
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
                    IconButton(onClick = { onToggleFavorite(track) }) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (track.isFavorite) Color(0xFFFF2A6D) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showItemMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showItemMenu,
                            onDismissRequest = { showItemMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play Next") },
                                onClick = {
                                    onPlayNext(track)
                                    showItemMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Queue") },
                                onClick = {
                                    onAddToQueue(track)
                                    showItemMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                onClick = {
                                    onAddToPlaylist(track)
                                    showItemMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
