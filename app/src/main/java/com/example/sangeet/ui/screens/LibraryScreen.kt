package com.example.sangeet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.example.sangeet.data.model.*
import com.example.sangeet.data.repository.MediaFolderSummary
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.rules.RuleConflict
import com.example.sangeet.data.rules.StoragePathHelper
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.scanner.FolderInspectionResult
import com.example.sangeet.ui.components.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
    val mediaFolders by repository.mediaFolders.collectAsState(initial = emptyList())
    val blacklistEntries by repository.blacklist.collectAsState(initial = emptyList())
    val whitelistEntries by repository.whitelist.collectAsState(initial = emptyList())

    // Reactive Sub-view filter (Album / Artist / Genre / Playlist / Folder)
    var activeSubFilter by remember { mutableStateOf<LibrarySubFilter?>(null) }
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

    // Playlist & Rule dialogs
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var trackToAddToPlaylist by remember { mutableStateOf<AudioTrack?>(null) }
    var pendingConflict by remember { mutableStateOf<Pair<QuickRuleProposal, List<RuleConflict>>?>(null) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        TopAppBar(
            title = {
                Text(
                    text = activeSubFilter?.title ?: "Music Library",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = if (activeSubFilter != null) {
                {
                    IconButton(onClick = {
                        activeSubFilter = null
                        isSelectionMode = false
                        selectedTrackIds = emptySet()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            } else { {} },
            actions = {
                if (selectedTab == LibraryTab.SONGS && activeSubFilter == null) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort")
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
                    IconButton(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = "New Playlist")
                    }
                }
            }
        )

        if (activeSubFilter == null) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                divider = {}
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                            selectedFolderKeys = emptySet()
                        },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (activeSubFilter != null) {
                TrackList(
                    tracks = activeFilterTracks,
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
                    onAddToPlaylist = { trackToAddToPlaylist = it },
                    onToggleFavorite = { t -> coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) } },
                    onProposeRule = proposeRule,
                    onRemoveBlacklistId = { id -> coroutineScope.launch { repository.removeFromBlacklist(id) } },
                    onRemoveWhitelistId = { id -> coroutineScope.launch { repository.removeFromWhitelist(id) } }
                )
            } else {
                when (selectedTab) {
                    LibraryTab.SONGS -> {
                        TrackList(
                            tracks = sortedTracks,
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
                            onPlay = { playbackManager.playTrack(it, sortedTracks) },
                            onAddToQueue = { playbackManager.addToQueue(it) },
                            onPlayNext = { playbackManager.playNextInQueue(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onToggleFavorite = { t -> coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) } },
                            onProposeRule = proposeRule,
                            onRemoveBlacklistId = { id -> coroutineScope.launch { repository.removeFromBlacklist(id) } },
                            onRemoveWhitelistId = { id -> coroutineScope.launch { repository.removeFromWhitelist(id) } }
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
                                val blEntry = blacklistEntries.find { it.type.equals("ALBUM", true) && it.identifier.equals(album, true) }
                                val wlEntry = whitelistEntries.find { it.type.equals("ALBUM", true) && it.identifier.equals(album, true) }
                                var showMenu by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { activeSubFilter = LibrarySubFilter.Album(album) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(104.dp)
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
                                            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                                IconButton(onClick = { showMenu = true }) {
                                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Album Options")
                                                }
                                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (wlEntry != null) "Whitelisted Album" else "Album",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (wlEntry != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                                val blEntry = blacklistEntries.find { it.type.equals("ARTIST", true) && it.identifier.equals(artist, true) }
                                val wlEntry = whitelistEntries.find { it.type.equals("ARTIST", true) && it.identifier.equals(artist, true) }
                                var showMenu by remember { mutableStateOf(false) }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { activeSubFilter = LibrarySubFilter.Artist(artist) },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (wlEntry != null) {
                                                Text("Whitelisted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Box {
                                            IconButton(onClick = { showMenu = true }) {
                                                Icon(Icons.Rounded.MoreVert, contentDescription = "Artist Options")
                                            }
                                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                                }
                            }
                        }
                    }

                    LibraryTab.FOLDERS -> {
                        LazyColumn(
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

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
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
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    selectedFolderKeys = if (it) selectedFolderKeys + folder.canonicalKey else selectedFolderKeys - folder.canonicalKey
                                                }
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (folder.isBlacklisted) Icons.Rounded.FolderOff else Icons.Rounded.Folder,
                                                contentDescription = null,
                                                tint = if (folder.isBlacklisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folder.displayName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = folder.displayPath,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    append("${folder.audioCount} songs • ${folder.videoCount} videos")
                                                    if (folder.isBlacklisted) append(" • BLACKLISTED")
                                                    else if (folder.isWhitelisted) append(" • WHITELISTED")
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (folder.isBlacklisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Box {
                                            IconButton(onClick = { showMenu = true }) {
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
                                        .clickable { activeSubFilter = LibrarySubFilter.Genre(genre) },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.CenterStart) {
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
                                        .clickable { activeSubFilter = LibrarySubFilter.PlaylistFilter(playlist.id, playlist.name) },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { coroutineScope.launch { repository.deletePlaylist(playlist.id) } }) {
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
                            onPlay = { playbackManager.playTrack(it, favoriteTracks) },
                            onAddToQueue = { playbackManager.addToQueue(it) },
                            onPlayNext = { playbackManager.playNextInQueue(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onToggleFavorite = { t -> coroutineScope.launch { repository.setTrackFavorite(t.id, !t.isFavorite) } },
                            onProposeRule = proposeRule,
                            onRemoveBlacklistId = { id -> coroutineScope.launch { repository.removeFromBlacklist(id) } },
                            onRemoveWhitelistId = { id -> coroutineScope.launch { repository.removeFromWhitelist(id) } }
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
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
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
            dismissButton = { TextButton(onClick = { trackToAddToPlaylist = null }) { Text("Close") } }
        )
    }

    // Conflict Warning Dialog for Quick Rules
    QuickRuleConflictHost(
        pendingConflict = pendingConflict,
        repository = repository,
        coroutineScope = coroutineScope,
        context = context,
        onClear = { pendingConflict = null }
    )

    // SAF Folder Confirmation Dialog when picked from Library -> Folders
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackList(
    tracks: List<AudioTrack>,
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
    onProposeRule: (QuickRuleProposal) -> Unit = {},
    onRemoveBlacklistId: (Long) -> Unit = {},
    onRemoveWhitelistId: (Long) -> Unit = {},
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
        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            var showItemMenu by remember { mutableStateOf(false) }
            val isSelected = track.id in selectedTrackIds
            val trackIdentifier = track.path.ifBlank { track.id.toString() }
            val blSongEntry = blacklistEntries.find {
                it.type.equals("SONG", true) && (it.identifier.equals(trackIdentifier, true) || it.identifier.equals(track.title, true))
            }
            val wlSongEntry = whitelistEntries.find {
                it.type.equals("SONG", true) && (it.identifier.equals(trackIdentifier, true) || it.identifier.equals(track.title, true))
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) onToggleSelect(track.id) else onPlay(track)
                        },
                        onLongClick = { onEnterSelectionMode(track.id) }
                    ),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect(track.id) })
                        Spacer(Modifier.width(4.dp))
                    }
                    TrackArtwork(track = track, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(10.dp))
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
                        DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
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
                                    text = { Text("Add to Blacklist") },
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
                                    text = { Text("Add to Whitelist") },
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
