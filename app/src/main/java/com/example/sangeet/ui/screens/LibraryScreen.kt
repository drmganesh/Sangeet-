package com.example.sangeet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.LibraryRouteRequest
import com.example.sangeet.data.model.*
import com.example.sangeet.data.repository.MediaFolderSummary
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.rules.RuleConflict
import com.example.sangeet.data.rules.StoragePathHelper
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.scanner.FolderInspectionResult
import com.example.sangeet.ui.components.*
import com.example.sangeet.ui.motion.LocalSangeetMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class LibraryTab(val title: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
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

private sealed class LibrarySubFilter(val title: String) {
    class Album(name: String) : LibrarySubFilter(name)
    class Artist(name: String) : LibrarySubFilter(name)
    class Genre(name: String) : LibrarySubFilter(name)
    class PlaylistFilter(val id: Long, name: String) : LibrarySubFilter(name)
    class FolderFilter(val summary: MediaFolderSummary) : LibrarySubFilter(summary.displayName)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    routeRequest: LibraryRouteRequest = LibraryRouteRequest.None,
    onRouteRequestConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sangeetMotion = LocalSangeetMotion.current
    val haptic = LocalHapticFeedback.current

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { LibraryTab.entries.size }
    )
    val selectedTab by remember {
        derivedStateOf {
            LibraryTab.entries[pagerState.currentPage.coerceIn(0, LibraryTab.entries.lastIndex)]
        }
    }
    var currentSort by remember { mutableStateOf(SortOrder.TITLE) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Per-tab preserved scroll states
    val songsListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val artistsListState = rememberLazyListState()
    val foldersListState = rememberLazyListState()
    val genresGridState = rememberLazyGridState()
    val playlistsListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()

    val allTracks by repository.allTracks.collectAsState(initial = emptyList())
    val favoriteTracks by repository.favoriteTracks.collectAsState(initial = emptyList())
    val allArtists by repository.allArtists.collectAsState(initial = emptyList())
    val allAlbums by repository.allAlbums.collectAsState(initial = emptyList())
    val allGenres by repository.allGenres.collectAsState(initial = emptyList())
    val playlists by repository.allPlaylists.collectAsState(initial = emptyList())
    val mediaFolders by repository.mediaFolders.collectAsState(initial = emptyList())
    val blacklistEntries by repository.blacklist.collectAsState(initial = emptyList())
    val whitelistEntries by repository.whitelist.collectAsState(initial = emptyList())

    val currentPlayingTrack by playbackManager.currentTrack.collectAsState()
    val isAudioPlaying by playbackManager.isPlaying.collectAsState()
    val isAppResumed by rememberIsAppResumed()

    // Reactive Sub-view filter (Album / Artist / Genre / Playlist / Folder)
    var activeSubFilter by remember { mutableStateOf<LibrarySubFilter?>(null) }

    LaunchedEffect(routeRequest) {
        when (routeRequest) {
            is LibraryRouteRequest.OpenAlbum -> {
                pagerState.scrollToPage(LibraryTab.ALBUMS.ordinal)
                activeSubFilter = LibrarySubFilter.Album(routeRequest.albumName)
                onRouteRequestConsumed()
            }
            is LibraryRouteRequest.OpenArtist -> {
                pagerState.scrollToPage(LibraryTab.ARTISTS.ordinal)
                activeSubFilter = LibrarySubFilter.Artist(routeRequest.artistName)
                onRouteRequestConsumed()
            }
            is LibraryRouteRequest.OpenPlaylist -> {
                pagerState.scrollToPage(LibraryTab.PLAYLISTS.ordinal)
                activeSubFilter = LibrarySubFilter.PlaylistFilter(routeRequest.playlistId, routeRequest.playlistName)
                onRouteRequestConsumed()
            }
            LibraryRouteRequest.None -> Unit
        }
    }

    val activeFilterTracks by remember(activeSubFilter) {
        when (val f = activeSubFilter) {
            is LibrarySubFilter.Album -> repository.getTracksByAlbum(f.title)
            is LibrarySubFilter.Artist -> repository.getTracksByArtist(f.title)
            is LibrarySubFilter.Genre -> repository.getTracksByGenre(f.title)
            is LibrarySubFilter.PlaylistFilter -> repository.getPlaylistTracks(f.id)
            is LibrarySubFilter.FolderFilter -> repository.allTracks.map { tracks ->
                tracks.filter { t ->
                    StoragePathHelper.isMediaInsideFolder(
                        t.path, t.folderPath, t.documentId, t.sourceTreeUri,
                        f.summary.displayPath, f.summary.documentId, f.summary.treeUri,
                        includeSubfolders = true
                    )
                }
            }
            null -> flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Multi-select state (tracks or folders)
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedFolderKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Playlist dialogs
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }
    var playlistIdForCover by remember { mutableStateOf<Long?>(null) }
    var tracksToAddToPlaylist by remember { mutableStateOf<List<AudioTrack>?>(null) }
    var pendingConflict by remember { mutableStateOf<Pair<QuickRuleProposal, List<RuleConflict>>?>(null) }

    // Photo Picker for custom playlist cover (zero broad storage permissions)
    val coverPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val plId = playlistIdForCover
        playlistIdForCover = null
        if (uri != null && plId != null) {
            coroutineScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
                        val outFile = File(dir, "pl_$plId.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(outFile).use { output -> input.copyTo(output) }
                        }
                        if (outFile.exists() && outFile.length() > 0L) outFile.absolutePath else uri.toString()
                    }.getOrDefault(uri.toString())
                }
                repository.updatePlaylistArtwork(plId, savedPath)
                Toast.makeText(context, "Playlist cover updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SAF Direct Folder Picker state for Folders tab
    var pickerListKind by remember { mutableStateOf(RuleListKind.BLACKLIST) }
    var pendingSafInspection by remember { mutableStateOf<FolderInspectionResult.Success?>(null) }
    var pendingSafConflicts by remember { mutableStateOf<List<RuleConflict>>(emptyList()) }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = repository.inspectPickedSafFolder(uri)
                when (res) {
                    is FolderInspectionResult.Success -> {
                        val conflicts = repository.detectConflictsForNewRule(
                            targetList = pickerListKind,
                            ruleType = RuleType.FOLDER,
                            identifier = res.displayPath,
                            documentId = res.documentId,
                            treeUri = res.treeUri
                        )
                        pendingSafInspection = res
                        pendingSafConflicts = conflicts
                    }
                    is FolderInspectionResult.PermissionRevoked -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    is FolderInspectionResult.FolderUnavailable -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BackHandler(enabled = isSelectionMode || activeSubFilter != null) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedTrackIds = emptySet()
            selectedFolderKeys = emptySet()
        } else {
            activeSubFilter = null
        }
    }

    val sortedTracks = remember(allTracks, currentSort) {
        when (currentSort) {
            SortOrder.TITLE -> allTracks.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> allTracks.sortedBy { it.artist.lowercase() }
            SortOrder.DATE_ADDED -> allTracks.sortedByDescending { it.dateAdded }
            SortOrder.DURATION -> allTracks.sortedByDescending { it.durationMs }
        }
    }

    val tracksByAlbum = remember(allTracks) {
        allTracks.groupBy { it.album.trim().lowercase() }
    }
    val tracksByArtist = remember(allTracks) {
        allTracks.groupBy { it.artist.trim().lowercase() }
    }
    val tracksByGenre = remember(allTracks) {
        allTracks.groupBy { it.genre.trim().lowercase() }
    }

    val proposeRule: (QuickRuleProposal) -> Unit = { proposal ->
        coroutineScope.launch {
            executeQuickRuleWithConflictCheck(
                repository = repository,
                proposal = proposal,
                context = context,
                onConflictsFound = { p, c -> pendingConflict = p to c }
            )
        }
    }

    val applyBulkTrackRules: (List<AudioTrack>, RuleListKind) -> Unit = { list, targetKind ->
        coroutineScope.launch {
            val targets = list.filter { it.id in selectedTrackIds }
            var added = 0
            for (t in targets) {
                val id = t.path.ifBlank { t.id.toString() }
                if (targetKind == RuleListKind.BLACKLIST) {
                    repository.addToBlacklist("SONG", id, t.title, t.folderPath, t.sourceTreeUri, t.documentId, 1)
                } else {
                    repository.addToWhitelist("SONG", id, t.title, t.folderPath, t.sourceTreeUri, t.documentId, 1)
                }
                added++
            }
            isSelectionMode = false
            selectedTrackIds = emptySet()
            Toast.makeText(context, "Added $added track(s) to ${if (targetKind == RuleListKind.BLACKLIST) "Blacklist" else "Whitelist"}", Toast.LENGTH_SHORT).show()
        }
    }

    val activePlaylistObj = (activeSubFilter as? LibrarySubFilter.PlaylistFilter)?.let { pf ->
        playlists.find { it.id == pf.id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        CassetteTopBar(
            title = activePlaylistObj?.name ?: activeSubFilter?.title ?: "Music Library",
            subtitle = when (val f = activeSubFilter) {
                is LibrarySubFilter.Album -> "${activeFilterTracks.size} tracks • Album Deck"
                is LibrarySubFilter.Artist -> "${activeFilterTracks.size} tracks • Artist Discography"
                is LibrarySubFilter.PlaylistFilter -> "${activeFilterTracks.size} tracks • Tape Collection"
                is LibrarySubFilter.Genre -> "${activeFilterTracks.size} tracks • Genre Channel"
                is LibrarySubFilter.FolderFilter -> f.summary.displayPath
                null -> "${allTracks.size} Cassettes • ${allAlbums.size} Albums"
            },
            badgeText = if (activeSubFilter == null) "SANGEET" else null,
            onNavigateBack = if (activeSubFilter != null) {
                {
                    activeSubFilter = null
                    isSelectionMode = false
                    selectedTrackIds = emptySet()
                }
            } else null,
            actions = {
                if (selectedTab == LibraryTab.SONGS && activeSubFilter == null) {
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Rounded.Sort,
                                contentDescription = "Sort Tracks",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
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
                if (selectedTab == LibraryTab.PLAYLISTS && activeSubFilter == null) {
                    IconButton(
                        onClick = { showCreatePlaylistDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("library_create_playlist_btn")
                    ) {
                        Icon(
                            Icons.Rounded.PlaylistAdd,
                            contentDescription = "New Playlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )

        if (activeSubFilter == null) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (tabPositions.isNotEmpty()) {
                        val currentPage = pagerState.currentPage.coerceIn(0, tabPositions.lastIndex)
                        val offsetFraction = pagerState.currentPageOffsetFraction
                        val nextPage = (currentPage + if (offsetFraction >= 0f) 1 else -1)
                            .coerceIn(0, tabPositions.lastIndex)
                        val currentPos = tabPositions[currentPage]
                        val nextPos = tabPositions[nextPage]
                        val fractionAbs = kotlin.math.abs(offsetFraction).coerceIn(0f, 1f)
                        val indicatorLeft = lerp(currentPos.left, nextPos.left, fractionAbs)
                        val indicatorWidth = lerp(currentPos.width, nextPos.width, fractionAbs)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomStart)
                                .offset(x = indicatorLeft)
                                .width(indicatorWidth)
                                .padding(horizontal = 12.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                }
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                            selectedFolderKeys = emptySet()
                            coroutineScope.launch {
                                if (sangeetMotion.isReducedMotion) {
                                    pagerState.scrollToPage(tab.ordinal)
                                } else {
                                    pagerState.animateScrollToPage(
                                        page = tab.ordinal,
                                        animationSpec = sangeetMotion.floatSpring()
                                    )
                                }
                            }
                        },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == tab) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        modifier = Modifier.testTag("library_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        // Bulk Selection Bar (Tracks or Folders)
        val activeTracksForBulk = when {
            activeSubFilter != null -> activeFilterTracks
            selectedTab == LibraryTab.SONGS -> sortedTracks
            selectedTab == LibraryTab.FAVORITES -> favoriteTracks
            else -> emptyList()
        }
        val isFolderBulk = activeSubFilter == null && selectedTab == LibraryTab.FOLDERS
        MultiSelectRuleActionBar(
            visible = isSelectionMode,
            selectedCount = if (isFolderBulk) selectedFolderKeys.size else selectedTrackIds.size,
            onSelectAll = {
                if (isFolderBulk) {
                    val all = mediaFolders.map { it.canonicalKey }.toSet()
                    selectedFolderKeys = if (selectedFolderKeys.size == all.size) emptySet() else all
                } else {
                    val all = activeTracksForBulk.map { it.id }.toSet()
                    selectedTrackIds = if (selectedTrackIds.size == all.size) emptySet() else all
                }
            },
            onBlacklistSelected = {
                if (isFolderBulk) {
                    coroutineScope.launch {
                        val picked = mediaFolders.filter { it.canonicalKey in selectedFolderKeys }
                        for (f in picked) {
                            repository.addFolderRule(
                                listKind = RuleListKind.BLACKLIST,
                                ruleType = RuleType.FOLDER,
                                treeUri = f.treeUri,
                                documentId = f.documentId,
                                displayName = f.displayName,
                                displayPath = f.displayPath,
                                includeSubfolders = true,
                                initialMediaCount = f.audioCount + f.videoCount
                            )
                        }
                        isSelectionMode = false
                        selectedFolderKeys = emptySet()
                        Toast.makeText(context, "Blacklisted ${picked.size} folder(s)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    applyBulkTrackRules(activeTracksForBulk, RuleListKind.BLACKLIST)
                }
            },
            onWhitelistSelected = {
                if (isFolderBulk) {
                    coroutineScope.launch {
                        val picked = mediaFolders.filter { it.canonicalKey in selectedFolderKeys }
                        for (f in picked) {
                            repository.addFolderRule(
                                listKind = RuleListKind.WHITELIST,
                                ruleType = RuleType.FOLDER,
                                treeUri = f.treeUri,
                                documentId = f.documentId,
                                displayName = f.displayName,
                                displayPath = f.displayPath,
                                includeSubfolders = true,
                                initialMediaCount = f.audioCount + f.videoCount
                            )
                        }
                        isSelectionMode = false
                        selectedFolderKeys = emptySet()
                        Toast.makeText(context, "Whitelisted ${picked.size} folder(s)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    applyBulkTrackRules(activeTracksForBulk, RuleListKind.WHITELIST)
                }
            },
            onCancel = {
                isSelectionMode = false
                selectedTrackIds = emptySet()
                selectedFolderKeys = emptySet()
            }
        )

        var recentRemovedPlaylistItem by remember { mutableStateOf<Triple<Long, Int, AudioTrack>?>(null) }
        LaunchedEffect(activeSubFilter) {
            if (activeSubFilter !is LibrarySubFilter.PlaylistFilter) {
                recentRemovedPlaylistItem = null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            AnimatedContent(
                targetState = activeSubFilter,
                transitionSpec = {
                    val forward = targetState != null
                    sangeetMotion.tabTransitionSpec<LibrarySubFilter?>(if (forward) 1 else -1)
                },
                label = "library_subfilter_transition"
            ) { subFilter ->
            if (subFilter != null) {
                val isPlaylistSub = subFilter is LibrarySubFilter.PlaylistFilter
                TrackList(
                    tracks = activeFilterTracks,
                    currentTrackId = currentPlayingTrack?.id,
                    isAudioPlaying = isAudioPlaying && isAppResumed,
                    blacklistEntries = blacklistEntries,
                    whitelistEntries = whitelistEntries,
                    isSelectionMode = isSelectionMode,
                    selectedTrackIds = selectedTrackIds,
                    onToggleSelect = { id ->
                        selectedTrackIds = if (id in selectedTrackIds) selectedTrackIds - id else selectedTrackIds + id
                    },
                    onEnterSelectionMode = { id ->
                        isSelectionMode = true
                        selectedTrackIds = selectedTrackIds + id
                    },
                    onPlay = { playbackManager.playTrack(it, activeFilterTracks) },
                    onAddToQueue = { playbackManager.addToQueue(it) },
                    onPlayNext = { playbackManager.playNextInQueue(it) },
                    onAddToPlaylist = { tracksToAddToPlaylist = listOf(it) },
                    onToggleFavorite = { t -> coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) } },
                    onViewAlbum = { album -> if (album.isNotBlank()) activeSubFilter = LibrarySubFilter.Album(album) },
                    onViewArtist = { artist -> if (artist.isNotBlank()) activeSubFilter = LibrarySubFilter.Artist(artist) },
                    onProposeRule = proposeRule,
                    onRemoveBlacklistId = { id -> coroutineScope.launch { repository.removeFromBlacklist(id) } },
                    onRemoveWhitelistId = { id -> coroutineScope.launch { repository.removeFromWhitelist(id) } },
                    isPlaylistReorderMode = isPlaylistSub,
                    onMoveTrackInPlaylist = if (isPlaylistSub) {
                        { idx, delta ->
                            val targetIdx = idx + delta
                            if (idx in activeFilterTracks.indices && targetIdx in activeFilterTracks.indices) {
                                val reordered = activeFilterTracks.toMutableList()
                                val moved = reordered.removeAt(idx)
                                reordered.add(targetIdx, moved)
                                coroutineScope.launch {
                                    repository.reorderPlaylistTracks(subFilter.id, reordered.map { it.id })
                                }
                            }
                        }
                    } else null,
                    onRemoveTrackFromPlaylist = if (isPlaylistSub) {
                        { track ->
                            val idx = activeFilterTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                            recentRemovedPlaylistItem = Triple(subFilter.id, idx, track)
                            coroutineScope.launch {
                                repository.removeTrackFromPlaylist(subFilter.id, track.id)
                            }
                        }
                    } else null,
                    headerContent = {
                        when (subFilter) {
                            is LibrarySubFilter.Album -> {
                                val rep = activeFilterTracks.firstOrNull { it.artworkUri.isNotBlank() }
                                    ?: activeFilterTracks.firstOrNull()
                                if (rep != null) {
                                    AlbumDetailHeader(
                                        albumName = subFilter.title,
                                        artistName = rep.artist,
                                        trackCount = activeFilterTracks.size,
                                        representativeTrack = rep,
                                        isPlaying = currentPlayingTrack?.album?.equals(subFilter.title, ignoreCase = true) == true && isAudioPlaying && isAppResumed,
                                        onPlayAll = {
                                            if (activeFilterTracks.isNotEmpty()) {
                                                playbackManager.playTrack(activeFilterTracks.first(), activeFilterTracks)
                                            }
                                        },
                                        onShuffle = {
                                            if (activeFilterTracks.isNotEmpty()) {
                                                val shuffled = activeFilterTracks.shuffled()
                                                playbackManager.playTrack(shuffled.first(), shuffled)
                                            }
                                        },
                                        onAddToPlaylist = {
                                            if (activeFilterTracks.isNotEmpty()) {
                                                tracksToAddToPlaylist = activeFilterTracks
                                            }
                                        }
                                    )
                                }
                            }
                            is LibrarySubFilter.Artist -> {
                                val rep = activeFilterTracks.firstOrNull { it.artworkUri.isNotBlank() }
                                    ?: activeFilterTracks.firstOrNull()
                                val artistAlbums = remember(activeFilterTracks) {
                                    activeFilterTracks.groupBy { it.album.trim().lowercase() }.values.mapNotNull { list ->
                                        list.firstOrNull { it.artworkUri.isNotBlank() } ?: list.firstOrNull()
                                    }
                                }
                                ArtistDetailHeader(
                                    artistName = subFilter.title,
                                    representativeTrack = rep,
                                    albumTracks = artistAlbums,
                                    totalTracks = activeFilterTracks.size,
                                    currentTrack = currentPlayingTrack,
                                    isAudioPlaying = isAudioPlaying && isAppResumed,
                                    onPlayAll = {
                                        if (activeFilterTracks.isNotEmpty()) {
                                            playbackManager.playTrack(activeFilterTracks.first(), activeFilterTracks)
                                        }
                                    },
                                    onShuffle = {
                                        if (activeFilterTracks.isNotEmpty()) {
                                            val shuffled = activeFilterTracks.shuffled()
                                            playbackManager.playTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                    onOpenAlbum = { albumName ->
                                        activeSubFilter = LibrarySubFilter.Album(albumName)
                                    }
                                )
                            }
                            is LibrarySubFilter.PlaylistFilter -> {
                                val pl = activePlaylistObj
                                val firstWithArt = activeFilterTracks.firstOrNull { it.artworkUri.isNotBlank() }
                                    ?: activeFilterTracks.firstOrNull()
                                val coverTrack = AudioTrack(
                                    id = -subFilter.id - 2000L,
                                    title = pl?.name ?: subFilter.title,
                                    artist = "Playlist",
                                    album = "${activeFilterTracks.size} tracks",
                                    path = firstWithArt?.path.orEmpty(),
                                    artworkUri = pl?.customArtworkUri?.takeIf { it.isNotBlank() } ?: firstWithArt?.artworkUri.orEmpty()
                                )
                                PlaylistDetailHeader(
                                    playlistName = pl?.name ?: subFilter.title,
                                    coverTrack = coverTrack,
                                    trackCount = activeFilterTracks.size,
                                    onPlayAll = {
                                        if (activeFilterTracks.isNotEmpty()) {
                                            playbackManager.playTrack(activeFilterTracks.first(), activeFilterTracks)
                                        }
                                    },
                                    onShuffle = {
                                        if (activeFilterTracks.isNotEmpty()) {
                                            val shuffled = activeFilterTracks.shuffled()
                                            playbackManager.playTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                    onRename = {
                                        if (pl != null) {
                                            playlistToRename = pl
                                            renameText = pl.name
                                        }
                                    },
                                    onPickCover = {
                                        playlistIdForCover = subFilter.id
                                        coverPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                            }
                            else -> Unit
                        }
                    }
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = sangeetMotion.allowHorizontalSwipes && !isSelectionMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_horizontal_pager")
                ) { pageIndex ->
                when (LibraryTab.entries[pageIndex]) {
                    LibraryTab.SONGS -> {
                        TrackList(
                            tracks = sortedTracks,
                            listState = songsListState,
                            currentTrackId = currentPlayingTrack?.id,
                            isAudioPlaying = isAudioPlaying && isAppResumed,
                            blacklistEntries = blacklistEntries,
                            whitelistEntries = whitelistEntries,
                            isSelectionMode = isSelectionMode,
                            selectedTrackIds = selectedTrackIds,
                            onToggleSelect = { id ->
                                selectedTrackIds = if (id in selectedTrackIds) selectedTrackIds - id else selectedTrackIds + id
                            },
                            onEnterSelectionMode = { id ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSelectionMode = true
                                selectedTrackIds = selectedTrackIds + id
                            },
                            onPlay = { playbackManager.playTrack(it, sortedTracks) },
                            onAddToQueue = { playbackManager.addToQueue(it) },
                            onPlayNext = { playbackManager.playNextInQueue(it) },
                            onAddToPlaylist = { tracksToAddToPlaylist = listOf(it) },
                            onToggleFavorite = { t -> coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) } },
                            onViewAlbum = { album -> if (album.isNotBlank()) activeSubFilter = LibrarySubFilter.Album(album) },
                            onViewArtist = { artist -> if (artist.isNotBlank()) activeSubFilter = LibrarySubFilter.Artist(artist) },
                            onProposeRule = proposeRule,
                            onRemoveBlacklistId = { id -> coroutineScope.launch { repository.removeFromBlacklist(id) } },
                            onRemoveWhitelistId = { id -> coroutineScope.launch { repository.removeFromWhitelist(id) } }
                        )
                    }

                    LibraryTab.ALBUMS -> {
                        if (allAlbums.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CassetteEmptyStateCard(
                                    icon = Icons.Rounded.Album,
                                    title = "No Albums Indexed",
                                    subtitle = "Local albums will appear here as collectible cassette tapes."
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                state = albumsGridState,
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(top = 14.dp, bottom = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.testTag("library_albums_grid")
                            ) {
                                items(allAlbums, key = { it }) { album ->
                                    val albumTracks = tracksByAlbum[album.trim().lowercase()].orEmpty()
                                    val repTrack = remember(album, albumTracks) {
                                        albumTracks.firstOrNull { it.artworkUri.isNotBlank() }
                                            ?: albumTracks.firstOrNull()
                                            ?: AudioTrack(
                                                id = -album.hashCode().toLong(),
                                                title = album,
                                                artist = "SANGEET",
                                                album = album
                                            )
                                    }
                                    val blEntry = blacklistEntries.find {
                                        it.type.equals("ALBUM", true) && it.identifier.equals(album, true)
                                    }
                                    val wlEntry = whitelistEntries.find {
                                        it.type.equals("ALBUM", true) && it.identifier.equals(album, true)
                                    }
                                    val isAlbumPlaying = currentPlayingTrack?.album?.equals(album, ignoreCase = true) == true
                                    var showMenu by remember { mutableStateOf(false) }

                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val scale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.96f else 1f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        label = "albumCardScale"
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = ripple()
                                            ) { activeSubFilter = LibrarySubFilter.Album(album) }
                                            .testTag("album_cassette_card_${album.lowercase().replace(' ', '_')}")
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            CassetteArtworkCard(
                                                track = repTrack,
                                                isCurrentlyPlaying = isAlbumPlaying,
                                                isAudioPlaying = isAudioPlaying && isAppResumed,
                                                isVisible = true,
                                                crossfadeDurationMs = 200,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(116.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.Black.copy(alpha = 0.62f),
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { showMenu = true },
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.MoreVert,
                                                            contentDescription = "Album Options",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text("Play Album") },
                                                        onClick = {
                                                            if (albumTracks.isNotEmpty()) {
                                                                playbackManager.playTrack(albumTracks.first(), albumTracks)
                                                            }
                                                            showMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Shuffle Album") },
                                                        onClick = {
                                                            if (albumTracks.isNotEmpty()) {
                                                                val shuffled = albumTracks.shuffled()
                                                                playbackManager.playTrack(shuffled.first(), shuffled)
                                                            }
                                                            showMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Add to Playlist") },
                                                        onClick = {
                                                            if (albumTracks.isNotEmpty()) {
                                                                tracksToAddToPlaylist = albumTracks
                                                            }
                                                            showMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Album Details") },
                                                        onClick = {
                                                            activeSubFilter = LibrarySubFilter.Album(album)
                                                            showMenu = false
                                                        }
                                                    )
                                                    HorizontalDivider()
                                                    if (blEntry != null) {
                                                        DropdownMenuItem(
                                                            text = { Text("Remove from Blacklist") },
                                                            onClick = {
                                                                coroutineScope.launch { repository.removeFromBlacklist(blEntry.id) }
                                                                showMenu = false
                                                            }
                                                        )
                                                    } else {
                                                        DropdownMenuItem(
                                                            text = { Text("Add to Blacklist") },
                                                            onClick = {
                                                                proposeRule(QuickRuleProposal(RuleListKind.BLACKLIST, RuleType.ALBUM, album, album))
                                                                showMenu = false
                                                            }
                                                        )
                                                    }
                                                    if (wlEntry != null) {
                                                        DropdownMenuItem(
                                                            text = { Text("Remove from Whitelist") },
                                                            onClick = {
                                                                coroutineScope.launch { repository.removeFromWhitelist(wlEntry.id) }
                                                                showMenu = false
                                                            }
                                                        )
                                                    } else {
                                                        DropdownMenuItem(
                                                            text = { Text("Add to Whitelist") },
                                                            onClick = {
                                                                proposeRule(QuickRuleProposal(RuleListKind.WHITELIST, RuleType.ALBUM, album, album))
                                                                showMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = album,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isAlbumPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        Text(
                                            text = buildString {
                                                append(repTrack.artist)
                                                if (albumTracks.isNotEmpty()) append(" • ${albumTracks.size} tracks")
                                                if (wlEntry != null) append(" • Whitelisted")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (wlEntry != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.ARTISTS -> {
                        if (allArtists.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CassetteEmptyStateCard(
                                    icon = Icons.Rounded.Person,
                                    title = "No Artists Found",
                                    subtitle = "Artists from your offline collection will appear here."
                                )
                            }
                        } else {
                            LazyColumn(
                                state = artistsListState,
                                contentPadding = PaddingValues(top = 14.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allArtists, key = { it }) { artist ->
                                    val artistTracks = tracksByArtist[artist.trim().lowercase()].orEmpty()
                                    val repTrack = artistTracks.firstOrNull { it.artworkUri.isNotBlank() }
                                        ?: artistTracks.firstOrNull()
                                    val blEntry = blacklistEntries.find {
                                        it.type.equals("ARTIST", true) && it.identifier.equals(artist, true)
                                    }
                                    val wlEntry = whitelistEntries.find {
                                        it.type.equals("ARTIST", true) && it.identifier.equals(artist, true)
                                    }
                                    var showMenu by remember { mutableStateOf(false) }

                                    CassetteArtistCard(
                                        artistName = artist,
                                        representativeTrack = repTrack,
                                        trackCount = artistTracks.size,
                                        isCurrentlyPlaying = currentPlayingTrack?.artist?.equals(artist, ignoreCase = true) == true,
                                        onClick = { activeSubFilter = LibrarySubFilter.Artist(artist) },
                                        trailingAction = {
                                            Box {
                                                IconButton(
                                                    onClick = { showMenu = true },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Artist Options")
                                                }
                                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text("Play All") },
                                                        onClick = {
                                                            if (artistTracks.isNotEmpty()) {
                                                                playbackManager.playTrack(artistTracks.first(), artistTracks)
                                                            }
                                                            showMenu = false
                                                        }
                                                    )
                                                    if (blEntry != null) {
                                                        DropdownMenuItem(
                                                            text = { Text("Remove from Blacklist") },
                                                            onClick = {
                                                                coroutineScope.launch { repository.removeFromBlacklist(blEntry.id) }
                                                                showMenu = false
                                                            }
                                                        )
                                                    } else {
                                                        DropdownMenuItem(
                                                            text = { Text("Add to Blacklist") },
                                                            onClick = {
                                                                proposeRule(QuickRuleProposal(RuleListKind.BLACKLIST, RuleType.ARTIST, artist, artist))
                                                                showMenu = false
                                                            }
                                                        )
                                                    }
                                                    if (wlEntry != null) {
                                                        DropdownMenuItem(
                                                            text = { Text("Remove from Whitelist") },
                                                            onClick = {
                                                                coroutineScope.launch { repository.removeFromWhitelist(wlEntry.id) }
                                                                showMenu = false
                                                            }
                                                        )
                                                    } else {
                                                        DropdownMenuItem(
                                                            text = { Text("Add to Whitelist") },
                                                            onClick = {
                                                                proposeRule(QuickRuleProposal(RuleListKind.WHITELIST, RuleType.ARTIST, artist, artist))
                                                                showMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.FOLDERS -> {
                        LazyColumn(
                            state = foldersListState,
                            contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            pickerListKind = RuleListKind.BLACKLIST
                                            safFolderLauncher.launch(null)
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        modifier = Modifier.weight(1f).testTag("library_add_folder_blacklist")
                                    ) {
                                        Icon(Icons.Rounded.FolderOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Blacklist Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            pickerListKind = RuleListKind.WHITELIST
                                            safFolderLauncher.launch(null)
                                        },
                                        modifier = Modifier.weight(1f).testTag("library_add_folder_whitelist")
                                    ) {
                                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Whitelist Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }

                            items(mediaFolders, key = { it.canonicalKey }) { folder ->
                                val isSelected = folder.canonicalKey in selectedFolderKeys
                                var showMenu by remember { mutableStateOf(false) }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedFolderKeys = if (isSelected) selectedFolderKeys - folder.canonicalKey else selectedFolderKeys + folder.canonicalKey
                                                } else {
                                                    activeSubFilter = LibrarySubFilter.FolderFilter(folder)
                                                }
                                            },
                                            onLongClick = {
                                                isSelectionMode = true
                                                selectedFolderKeys = selectedFolderKeys + folder.canonicalKey
                                            }
                                        )
                                ) {
                                    CassetteFolderBoxCard(
                                        folderName = folder.displayName,
                                        folderPath = folder.displayPath,
                                        itemCountLabel = buildString {
                                            append("${folder.audioCount}S • ${folder.videoCount}V")
                                            if (folder.isBlacklisted) append(" • BL")
                                            else if (folder.isWhitelisted) append(" • WL")
                                        },
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedFolderKeys = if (isSelected) selectedFolderKeys - folder.canonicalKey else selectedFolderKeys + folder.canonicalKey
                                            } else {
                                                activeSubFilter = LibrarySubFilter.FolderFilter(folder)
                                            }
                                        },
                                        trailingActions = {
                                            if (isSelectionMode) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        selectedFolderKeys = if (it) selectedFolderKeys + folder.canonicalKey else selectedFolderKeys - folder.canonicalKey
                                                    }
                                                )
                                            } else {
                                                Box {
                                                    IconButton(
                                                        onClick = { showMenu = true },
                                                        modifier = Modifier.minimumInteractiveComponentSize()
                                                    ) {
                                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Folder Options")
                                                    }
                                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                        if (folder.isBlacklisted) {
                                                            DropdownMenuItem(
                                                                text = { Text("Remove from Blacklist") },
                                                                onClick = {
                                                                    coroutineScope.launch {
                                                                        folder.blacklistRuleId?.let { repository.removeFolderRule(it) }
                                                                        folder.blacklistEntryId?.let { repository.removeFromBlacklist(it) }
                                                                    }
                                                                    showMenu = false
                                                                }
                                                            )
                                                        } else {
                                                            DropdownMenuItem(
                                                                text = { Text("Add to Blacklist") },
                                                                onClick = {
                                                                    proposeRule(
                                                                        QuickRuleProposal(
                                                                            targetList = RuleListKind.BLACKLIST,
                                                                            ruleType = RuleType.FOLDER,
                                                                            identifier = folder.displayPath,
                                                                            title = folder.displayName,
                                                                            displayPath = folder.displayPath,
                                                                            documentId = folder.documentId,
                                                                            treeUri = folder.treeUri,
                                                                            affectedCount = folder.audioCount + folder.videoCount
                                                                        )
                                                                    )
                                                                    showMenu = false
                                                                }
                                                            )
                                                        }
                                                        if (folder.isWhitelisted) {
                                                            DropdownMenuItem(
                                                                text = { Text("Remove from Whitelist") },
                                                                onClick = {
                                                                    coroutineScope.launch {
                                                                        folder.whitelistRuleId?.let { repository.removeFolderRule(it) }
                                                                        folder.whitelistEntryId?.let { repository.removeFromWhitelist(it) }
                                                                    }
                                                                    showMenu = false
                                                                }
                                                            )
                                                        } else {
                                                            DropdownMenuItem(
                                                                text = { Text("Add to Whitelist") },
                                                                onClick = {
                                                                    proposeRule(
                                                                        QuickRuleProposal(
                                                                            targetList = RuleListKind.WHITELIST,
                                                                            ruleType = RuleType.FOLDER,
                                                                            identifier = folder.displayPath,
                                                                            title = folder.displayName,
                                                                            displayPath = folder.displayPath,
                                                                            documentId = folder.documentId,
                                                                            treeUri = folder.treeUri,
                                                                            affectedCount = folder.audioCount + folder.videoCount
                                                                        )
                                                                    )
                                                                    showMenu = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.GENRES -> {
                        if (allGenres.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CassetteEmptyStateCard(
                                    icon = Icons.Rounded.Category,
                                    title = "No Genres Indexed",
                                    subtitle = "Genre tape channels will populate automatically from your music library."
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                state = genresGridState,
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(top = 14.dp, bottom = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(allGenres.indices.toList()) { idx ->
                                    val genre = allGenres[idx]
                                    val count = tracksByGenre[genre.trim().lowercase()]?.size ?: 0
                                    CassetteGenreCard(
                                        genreName = genre,
                                        trackCount = count,
                                        index = idx,
                                        onClick = { activeSubFilter = LibrarySubFilter.Genre(genre) }
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        if (playlists.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CassetteEmptyStateCard(
                                    icon = Icons.Rounded.QueueMusic,
                                    title = "No Cassette Mixtapes Yet",
                                    subtitle = "Create an offline playlist to organize your favorite tracks into custom cassette collections.",
                                    action = {
                                        Button(onClick = { showCreatePlaylistDialog = true }) {
                                            Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Create Playlist")
                                        }
                                    }
                                )
                            }
                        } else {
                            LazyColumn(
                                state = playlistsListState,
                                contentPadding = PaddingValues(top = 14.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(playlists, key = { it.id }) { playlist ->
                                    val playlistTracks by repository.getPlaylistTracks(playlist.id).collectAsState(initial = emptyList())
                                    val firstWithArt = playlistTracks.firstOrNull { it.artworkUri.isNotBlank() } ?: playlistTracks.firstOrNull()
                                    val coverTrack = remember(playlist.id, playlist.customArtworkUri, playlistTracks) {
                                        AudioTrack(
                                            id = -playlist.id - 3000L,
                                            title = playlist.name,
                                            artist = "Mixtape",
                                            album = "${playlistTracks.size} tracks",
                                            path = firstWithArt?.path.orEmpty(),
                                            artworkUri = playlist.customArtworkUri.ifBlank { firstWithArt?.artworkUri.orEmpty() }
                                        )
                                    }
                                    var showMenu by remember { mutableStateOf(false) }

                                    CassetteSectionCard(
                                        onClick = { activeSubFilter = LibrarySubFilter.PlaylistFilter(playlist.id, playlist.name) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CassetteArtwork(
                                                track = coverTrack,
                                                isPlaying = false,
                                                showMetadataOnLabel = false,
                                                compact = false,
                                                modifier = Modifier
                                                    .width(96.dp)
                                                    .height(62.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(3.dp))
                                                CassetteHeaderBadge(
                                                    text = "${playlistTracks.size} ${if (playlistTracks.size == 1) "TRACK" else "TRACKS"}"
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (playlistTracks.isNotEmpty()) {
                                                        playbackManager.playTrack(playlistTracks.first(), playlistTracks)
                                                    } else {
                                                        activeSubFilter = LibrarySubFilter.PlaylistFilter(playlist.id, playlist.name)
                                                    }
                                                },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    Icons.Rounded.PlayArrow,
                                                    contentDescription = "Play Playlist",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Box {
                                                IconButton(
                                                    onClick = { showMenu = true },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist Options")
                                                }
                                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text("Play All") },
                                                        onClick = {
                                                            if (playlistTracks.isNotEmpty()) {
                                                                playbackManager.playTrack(playlistTracks.first(), playlistTracks)
                                                            }
                                                            showMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Shuffle Playlist") },
                                                        onClick = {
                                                            if (playlistTracks.isNotEmpty()) {
                                                                val shuffled = playlistTracks.shuffled()
                                                                playbackManager.playTrack(shuffled.first(), shuffled)
                                                            }
                                                            showMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Rename") },
                                                        onClick = {
                                                            playlistToRename = playlist
                                                            renameText = playlist.name
                                                            showMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Change Cover Photo") },
                                                        onClick = {
                                                            playlistIdForCover = playlist.id
                                                            showMenu = false
                                                            coverPhotoPicker.launch(
                                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                            )
                                                        }
                                                    )
                                                    HorizontalDivider()
                                                    DropdownMenuItem(
                                                        text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                                                        onClick = {
                                                            coroutineScope.launch { repository.deletePlaylist(playlist.id) }
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

                    LibraryTab.FAVORITES -> {
                        TrackList(
                            tracks = favoriteTracks,
                            listState = favoritesListState,
                            currentTrackId = currentPlayingTrack?.id,
                            isAudioPlaying = isAudioPlaying && isAppResumed,
                            blacklistEntries = blacklistEntries,
                            whitelistEntries = whitelistEntries,
                            isSelectionMode = isSelectionMode,
                            selectedTrackIds = selectedTrackIds,
                            onToggleSelect = { id ->
                                selectedTrackIds = if (id in selectedTrackIds) selectedTrackIds - id else selectedTrackIds + id
                            },
                            onEnterSelectionMode = { id ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSelectionMode = true
                                selectedTrackIds = selectedTrackIds + id
                            },
                            onPlay = { playbackManager.playTrack(it, favoriteTracks) },
                            onAddToQueue = { playbackManager.addToQueue(it) },
                            onPlayNext = { playbackManager.playNextInQueue(it) },
                            onAddToPlaylist = { tracksToAddToPlaylist = listOf(it) },
                            onToggleFavorite = { t -> coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) } },
                            onViewAlbum = { album -> if (album.isNotBlank()) activeSubFilter = LibrarySubFilter.Album(album) },
                            onViewArtist = { artist -> if (artist.isNotBlank()) activeSubFilter = LibrarySubFilter.Artist(artist) },
                            onProposeRule = proposeRule,
                            onRemoveBlacklistId = { id -> coroutineScope.launch { repository.removeFromBlacklist(id) } },
                            onRemoveWhitelistId = { id -> coroutineScope.launch { repository.removeFromWhitelist(id) } }
                        )
                    }
                }
                }
            }
            }

            // Floating Undo banner for playlist track swipe-to-remove
            androidx.compose.animation.AnimatedVisibility(
                visible = recentRemovedPlaylistItem != null && activeSubFilter is LibrarySubFilter.PlaylistFilter,
                enter = sangeetMotion.miniPlayerEnter(),
                exit = sangeetMotion.miniPlayerExit(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                val removed = recentRemovedPlaylistItem
                if (removed != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlist_undo_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Removed \"${removed.third.title}\" from playlist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val (plId, oldIdx, trackToRestore) = removed
                                    recentRemovedPlaylistItem = null
                                    coroutineScope.launch {
                                        val mutable = activeFilterTracks
                                            .map { it.id }
                                            .filterNot { it == trackToRestore.id }
                                            .toMutableList()
                                        val insertIdx = oldIdx.coerceIn(0, mutable.size)
                                        mutable.add(insertIdx, trackToRestore.id)
                                        repository.reorderPlaylistTracks(plId, mutable)
                                    }
                                },
                                modifier = Modifier.testTag("playlist_undo_btn")
                            ) {
                                Text("UNDO", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Mixtape Playlist") },
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
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename Playlist Dialog
    if (playlistToRename != null) {
        val target = playlistToRename!!
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        coroutineScope.launch {
                            repository.renamePlaylist(target.id, renameText.trim())
                            playlistToRename = null
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) { Text("Cancel") }
            }
        )
    }

    // Add Track(s) to Playlist Dialog
    if (tracksToAddToPlaylist != null) {
        val pendingTracks = tracksToAddToPlaylist!!
        AlertDialog(
            onDismissRequest = { tracksToAddToPlaylist = null },
            title = {
                Text(
                    if (pendingTracks.size > 1) "Add ${pendingTracks.size} Tracks to Playlist"
                    else "Add to Playlist"
                )
            },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists found. Create one first!")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(playlists) { p ->
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.addTracksToPlaylist(p.id, pendingTracks.map { it.id })
                                        tracksToAddToPlaylist = null
                                        Toast.makeText(context, "Added to ${p.name}", Toast.LENGTH_SHORT).show()
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
            dismissButton = { TextButton(onClick = { tracksToAddToPlaylist = null }) { Text("Close") } }
        )
    }

    QuickRuleConflictHost(
        pendingConflict = pendingConflict,
        repository = repository,
        coroutineScope = coroutineScope,
        context = context,
        onClear = { pendingConflict = null }
    )

    if (pendingSafInspection != null) {
        val insp = pendingSafInspection!!
        FolderConfirmationDialog(
            inspection = insp,
            targetList = pickerListKind,
            conflicts = pendingSafConflicts,
            onDismiss = {
                pendingSafInspection = null
                pendingSafConflicts = emptyList()
            },
            onConfirm = { chosenType, includeSubfolders, removeConflicts ->
                coroutineScope.launch {
                    if (removeConflicts) {
                        for (c in pendingSafConflicts) repository.removeConflictingRule(c)
                    }
                    repository.addFolderRule(
                        listKind = pickerListKind,
                        ruleType = chosenType,
                        treeUri = insp.treeUri,
                        documentId = insp.documentId,
                        displayName = insp.displayName,
                        displayPath = insp.displayPath,
                        includeSubfolders = includeSubfolders,
                        subfolderCount = insp.subfolderCount,
                        initialMediaCount = insp.audioCount + insp.videoCount
                    )
                    pendingSafInspection = null
                    pendingSafConflicts = emptyList()
                    Toast.makeText(
                        context,
                        "Added '${insp.displayName}' to ${if (pickerListKind == RuleListKind.BLACKLIST) "Blacklist" else "Whitelist"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun AlbumDetailHeader(
    albumName: String,
    artistName: String,
    trackCount: Int,
    representativeTrack: AudioTrack,
    isPlaying: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    CassetteSectionCard(
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        highlighted = isPlaying
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CassetteArtworkCard(
                track = representativeTrack,
                isCurrentlyPlaying = isPlaying,
                isAudioPlaying = isPlaying,
                isVisible = true,
                crossfadeDurationMs = 180,
                modifier = Modifier
                    .width(128.dp)
                    .height(86.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                CassetteHeaderBadge(text = "ALBUM CASSETTE")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$artistName • $trackCount tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play All")
            }
            FilledTonalButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Shuffle")
            }
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add Album to Playlist", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ArtistDetailHeader(
    artistName: String,
    representativeTrack: AudioTrack?,
    albumTracks: List<AudioTrack>,
    totalTracks: Int,
    currentTrack: AudioTrack?,
    isAudioPlaying: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CassetteSectionCard(
            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
            highlighted = currentTrack?.artist?.equals(artistName, ignoreCase = true) == true
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Normal circular artist portrait (NOT a cassette)
                if (representativeTrack != null) {
                    TrackArtwork(
                        track = representativeTrack,
                        shape = CircleShape,
                        contentDescription = artistName,
                        modifier = Modifier.size(68.dp)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Person, contentDescription = artistName, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    CassetteHeaderBadge(text = "ARTIST STUDIO")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${albumTracks.size} albums • $totalTracks songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPlayAll, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play All")
                }
                FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Shuffle")
                }
            }
        }

        if (albumTracks.isNotEmpty()) {
            CassetteSectionHeader(
                title = "Albums",
                badgeText = "${albumTracks.size}",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(albumTracks, key = { it.album }) { rep ->
                    val isAlbPlaying = currentTrack?.album?.equals(rep.album, ignoreCase = true) == true
                    Column(
                        modifier = Modifier
                            .width(142.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenAlbum(rep.album) }
                    ) {
                        CassetteArtworkCard(
                            track = rep,
                            isCurrentlyPlaying = isAlbPlaying,
                            isAudioPlaying = isAudioPlaying,
                            isVisible = true,
                            crossfadeDurationMs = 180,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = rep.album.ifBlank { rep.title },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    playlistName: String,
    coverTrack: AudioTrack,
    trackCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onPickCover: () -> Unit
) {
    CassetteSectionCard(
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CassetteArtworkCard(
                track = coverTrack,
                isCurrentlyPlaying = false,
                isAudioPlaying = false,
                isVisible = true,
                crossfadeDurationMs = 180,
                modifier = Modifier
                    .width(124.dp)
                    .height(84.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                CassetteHeaderBadge(text = "MIXTAPE CASSETTE")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = playlistName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$trackCount ${if (trackCount == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onPlayAll, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play")
            }
            FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Shuffle")
            }
            IconButton(onClick = onRename, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Rounded.Edit, contentDescription = "Rename Playlist", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onPickCover, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Rounded.Image, contentDescription = "Change Cover", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackList(
    tracks: List<AudioTrack>,
    currentTrackId: Long? = null,
    isAudioPlaying: Boolean = false,
    blacklistEntries: List<BlacklistEntry> = emptyList(),
    whitelistEntries: List<WhitelistEntry> = emptyList(),
    isSelectionMode: Boolean = false,
    selectedTrackIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onEnterSelectionMode: (Long) -> Unit = {},
    onPlay: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onViewAlbum: (String) -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    onProposeRule: (QuickRuleProposal) -> Unit = {},
    onRemoveBlacklistId: (Long) -> Unit = {},
    onRemoveWhitelistId: (Long) -> Unit = {},
    isPlaylistReorderMode: Boolean = false,
    onMoveTrackInPlaylist: ((Int, Int) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((AudioTrack) -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val sangeetMotion = LocalSangeetMotion.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val dragStepPx = with(density) { 64.dp.toPx() }
    var draggingPlaylistIdx by remember { mutableStateOf<Int?>(null) }
    var playlistDragOffsetY by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (headerContent != null) {
            item(key = "tracklist_header") {
                headerContent()
            }
        }

        if (tracks.isEmpty()) {
            item(key = "tracklist_empty") {
                CassetteEmptyStateCard(
                    icon = Icons.Rounded.MusicNote,
                    title = "No Tracks Available",
                    subtitle = "No audio cassettes match this view.",
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        } else {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                var showItemMenu by remember { mutableStateOf(false) }
                val isSelected = track.id in selectedTrackIds
                val isPlayingThis = currentTrackId == track.id
                val isDraggingRow = isPlaylistReorderMode && draggingPlaylistIdx == index
                val selectionScale by animateFloatAsState(
                    targetValue = when {
                        isDraggingRow -> 1.02f
                        isSelected && sangeetMotion.isMotionEnabled -> 0.98f
                        else -> 1f
                    },
                    animationSpec = sangeetMotion.floatSpring(),
                    label = "trackRowSelectionScale"
                )
                val activeSpin = isPlayingThis && isAudioPlaying
                val seedAngle = remember(track.id) { ((track.id * 41L) % 360L).toFloat() }
                val reelAngle by rememberVisibleCassetteReelAngle(
                    isActivelyPlayingAndVisible = activeSpin,
                    seedAngleDegrees = seedAngle
                )

                val trackIdentifier = track.path.ifBlank { track.id.toString() }
                val blSongEntry = blacklistEntries.find {
                    it.type.equals("SONG", true) && (it.identifier.equals(trackIdentifier, true) || it.identifier.equals(track.title, true))
                }
                val wlSongEntry = whitelistEntries.find {
                    it.type.equals("SONG", true) && (it.identifier.equals(trackIdentifier, true) || it.identifier.equals(track.title, true))
                }

                var dismissedPlaylistRow by remember(track.id) { mutableStateOf(false) }
                val canSwipePlaylistTrack = isPlaylistReorderMode &&
                    onRemoveTrackFromPlaylist != null &&
                    sangeetMotion.allowSwipeToDismiss &&
                    !isSelectionMode &&
                    draggingPlaylistIdx == null
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (canSwipePlaylistTrack && value == SwipeToDismissBoxValue.EndToStart && !dismissedPlaylistRow) {
                            dismissedPlaylistRow = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemoveTrackFromPlaylist?.invoke(track)
                            true
                        } else {
                            false
                        }
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = canSwipePlaylistTrack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(placementSpec = sangeetMotion.intOffsetSpring())
                        .zIndex(if (isDraggingRow) 2f else 0f),
                    backgroundContent = {
                        val bgColor by animateColorAsState(
                            targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            label = "playlistSwipeBg"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(bgColor)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlaylistRemove,
                                contentDescription = "Remove from Playlist",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = if (isDraggingRow) playlistDragOffsetY else 0f
                            scaleX = selectionScale
                            scaleY = selectionScale
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) onToggleSelect(track.id) else onPlay(track)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEnterSelectionMode(track.id)
                            }
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        isDraggingRow -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        isPlayingThis -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                    },
                    border = BorderStroke(
                        width = if (isPlayingThis || isSelected || isDraggingRow) 1.2.dp else 0.7.dp,
                        color = if (isPlayingThis || isSelected || isDraggingRow) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode) {
                            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect(track.id) })
                            Spacer(Modifier.width(4.dp))
                        } else if (isPlaylistReorderMode && onMoveTrackInPlaylist != null) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .pointerInput(tracks.size, index) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggingPlaylistIdx = index
                                                playlistDragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggingPlaylistIdx = null
                                                playlistDragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingPlaylistIdx = null
                                                playlistDragOffsetY = 0f
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                val activeIdx = draggingPlaylistIdx ?: return@detectVerticalDragGestures
                                                playlistDragOffsetY += dragAmount
                                                if (playlistDragOffsetY > dragStepPx && activeIdx < tracks.lastIndex) {
                                                    onMoveTrackInPlaylist(activeIdx, 1)
                                                    draggingPlaylistIdx = activeIdx + 1
                                                    playlistDragOffsetY -= dragStepPx
                                                } else if (playlistDragOffsetY < -dragStepPx && activeIdx > 0) {
                                                    onMoveTrackInPlaylist(activeIdx, -1)
                                                    draggingPlaylistIdx = activeIdx - 1
                                                    playlistDragOffsetY += dragStepPx
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to Reorder",
                                    tint = if (isDraggingRow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        MiniCassetteArtwork(
                            track = track,
                            isPlaying = activeSpin,
                            reelAngleDegrees = reelAngle,
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("library_mini_cassette_${track.id}")
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

                        if (isPlaylistReorderMode && onMoveTrackInPlaylist != null) {
                            IconButton(
                                onClick = { onMoveTrackInPlaylist(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "Move Track Up",
                                    tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(
                                onClick = { onMoveTrackInPlaylist(index, 1) },
                                enabled = index < tracks.lastIndex,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Move Track Down",
                                    tint = if (index < tracks.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { onToggleFavorite(track) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (track.isFavorite) Color(0xFFFF2A6D) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showItemMenu = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Play") },
                                    onClick = {
                                        onPlay(track)
                                        showItemMenu = false
                                    }
                                )
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
                                DropdownMenuItem(
                                    text = { Text(if (track.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                                    onClick = {
                                        onToggleFavorite(track)
                                        showItemMenu = false
                                    }
                                )
                                if (track.album.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("View Album") },
                                        onClick = {
                                            onViewAlbum(track.album)
                                            showItemMenu = false
                                        }
                                    )
                                }
                                if (track.artist.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("View Artist") },
                                        onClick = {
                                            onViewArtist(track.artist)
                                            showItemMenu = false
                                        }
                                    )
                                }
                                if (onRemoveTrackFromPlaylist != null) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from Playlist", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            onRemoveTrackFromPlaylist(track)
                                            showItemMenu = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                if (blSongEntry != null) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from Blacklist") },
                                        onClick = {
                                            onRemoveBlacklistId(blSongEntry.id)
                                            showItemMenu = false
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Blacklist Song") },
                                        onClick = {
                                            onProposeRule(
                                                QuickRuleProposal(
                                                    targetList = RuleListKind.BLACKLIST,
                                                    ruleType = RuleType.SONG,
                                                    identifier = trackIdentifier,
                                                    title = track.title,
                                                    displayPath = track.path.ifBlank { track.folderPath },
                                                    documentId = track.documentId,
                                                    treeUri = track.sourceTreeUri,
                                                    mediaArtist = track.artist,
                                                    mediaAlbum = track.album
                                                )
                                            )
                                            showItemMenu = false
                                        }
                                    )
                                }
                                if (wlSongEntry != null) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from Whitelist") },
                                        onClick = {
                                            onRemoveWhitelistId(wlSongEntry.id)
                                            showItemMenu = false
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Whitelist Song") },
                                        onClick = {
                                            onProposeRule(
                                                QuickRuleProposal(
                                                    targetList = RuleListKind.WHITELIST,
                                                    ruleType = RuleType.SONG,
                                                    identifier = trackIdentifier,
                                                    title = track.title,
                                                    displayPath = track.path.ifBlank { track.folderPath },
                                                    documentId = track.documentId,
                                                    treeUri = track.sourceTreeUri,
                                                    mediaArtist = track.artist,
                                                    mediaAlbum = track.album
                                                )
                                            )
                                            showItemMenu = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Blacklist Artist (${track.artist})") },
                                    onClick = {
                                        onProposeRule(
                                            QuickRuleProposal(
                                                targetList = RuleListKind.BLACKLIST,
                                                ruleType = RuleType.ARTIST,
                                                identifier = track.artist,
                                                title = track.artist
                                            )
                                        )
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
    }
}
