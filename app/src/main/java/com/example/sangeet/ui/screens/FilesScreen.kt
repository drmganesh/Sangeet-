package com.example.sangeet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.*
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.rules.RuleConflict
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.scanner.FolderInspectionResult
import com.example.sangeet.ui.components.*
import kotlinx.coroutines.launch

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb > 1024) {
        String.format("%.2f GB", mb / 1024)
    } else {
        String.format("%.1f MB", mb)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    onPlayVideo: (VideoTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Audio, 1: Videos, 2: Folders

    val allTracks by repository.allTracks.collectAsState(initial = emptyList())
    val allVideos by repository.allVideos.collectAsState(initial = emptyList())
    val mediaFolders by repository.mediaFolders.collectAsState(initial = emptyList())
    val blacklistEntries by repository.blacklist.collectAsState(initial = emptyList())
    val whitelistEntries by repository.whitelist.collectAsState(initial = emptyList())
    val currentPlayingTrack by playbackManager.currentTrack.collectAsState()
    val isAudioPlaying by playbackManager.isPlaying.collectAsState()
    val isAppResumed by rememberIsAppResumed()

    val totalAudioBytes = remember(allTracks) { allTracks.sumOf { it.fileSize } }
    val totalVideoBytes = remember(allVideos) { allVideos.sumOf { it.fileSize } }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedAudioIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedVideoIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedFolderKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Conflict warning & SAF folder picker states
    var pendingConflict by remember { mutableStateOf<Pair<QuickRuleProposal, List<RuleConflict>>?>(null) }
    var pickerListKind by remember { mutableStateOf(RuleListKind.BLACKLIST) }
    var pendingSafInspection by remember { mutableStateOf<FolderInspectionResult.Success?>(null) }
    var pendingSafConflicts by remember { mutableStateOf<List<RuleConflict>>(emptyList()) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedAudioIds = emptySet()
        selectedVideoIds = emptySet()
        selectedFolderKeys = emptySet()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val imported = repository.importAudioUri(uri)
                if (imported != null) {
                    playbackManager.playTrack(imported)
                }
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("files_screen")
    ) {
        CassetteTopBar(
            title = "Offline Files",
            subtitle = "Local Audio, Videos & Tape Folders",
            actions = {
                FilledTonalButton(
                    onClick = { filePickerLauncher.launch(arrayOf("audio/*", "video/*")) },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = "Import", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
            }
        )

        // Storage Usage Summary Card
        CassetteSectionCard(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            highlighted = false
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                CassetteSectionHeader(
                    title = "Local Storage Usage",
                    badgeText = "100% OFFLINE"
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = formatBytes(totalAudioBytes + totalVideoBytes),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${allTracks.size} Audio Cassettes • ${allVideos.size} Videos • ${mediaFolders.size} Folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Sub tabs: Audio / Video / Folders
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(horizontal = 16.dp),
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    isSelectionMode = false
                },
                text = { Text("Music (${allTracks.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    isSelectionMode = false
                },
                text = { Text("Videos (${allVideos.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = {
                    selectedTab = 2
                    isSelectionMode = false
                },
                text = { Text("Folders (${mediaFolders.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("files_tab_folders")
            )
        }

        MultiSelectRuleActionBar(
            visible = isSelectionMode,
            selectedCount = when (selectedTab) {
                0 -> selectedAudioIds.size
                1 -> selectedVideoIds.size
                else -> selectedFolderKeys.size
            },
            onSelectAll = {
                when (selectedTab) {
                    0 -> {
                        val all = allTracks.map { it.id }.toSet()
                        selectedAudioIds = if (selectedAudioIds.size == all.size) emptySet() else all
                    }
                    1 -> {
                        val all = allVideos.map { it.id }.toSet()
                        selectedVideoIds = if (selectedVideoIds.size == all.size) emptySet() else all
                    }
                    else -> {
                        val all = mediaFolders.map { it.canonicalKey }.toSet()
                        selectedFolderKeys = if (selectedFolderKeys.size == all.size) emptySet() else all
                    }
                }
            },
            onBlacklistSelected = {
                coroutineScope.launch {
                    when (selectedTab) {
                        0 -> {
                            val list = allTracks.filter { it.id in selectedAudioIds }
                            list.forEach { t ->
                                repository.addToBlacklist("SONG", t.path.ifBlank { t.id.toString() }, t.title, t.folderPath, t.sourceTreeUri, t.documentId, 1)
                            }
                            selectedAudioIds = emptySet()
                            Toast.makeText(context, "Blacklisted ${list.size} audio file(s)", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            val list = allVideos.filter { it.id in selectedVideoIds }
                            list.forEach { v ->
                                repository.addToBlacklist("VIDEO", v.path.ifBlank { v.id.toString() }, v.title, v.folderPath, v.sourceTreeUri, v.documentId, 1)
                            }
                            selectedVideoIds = emptySet()
                            Toast.makeText(context, "Blacklisted ${list.size} video file(s)", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            val list = mediaFolders.filter { it.canonicalKey in selectedFolderKeys }
                            list.forEach { f ->
                                repository.addFolderRule(RuleListKind.BLACKLIST, RuleType.FOLDER, f.treeUri, f.documentId, f.displayName, f.displayPath, true, initialMediaCount = f.audioCount + f.videoCount)
                            }
                            selectedFolderKeys = emptySet()
                            Toast.makeText(context, "Blacklisted ${list.size} folder(s)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isSelectionMode = false
                }
            },
            onWhitelistSelected = {
                coroutineScope.launch {
                    when (selectedTab) {
                        0 -> {
                            val list = allTracks.filter { it.id in selectedAudioIds }
                            list.forEach { t ->
                                repository.addToWhitelist("SONG", t.path.ifBlank { t.id.toString() }, t.title, t.folderPath, t.sourceTreeUri, t.documentId, 1)
                            }
                            selectedAudioIds = emptySet()
                            Toast.makeText(context, "Whitelisted ${list.size} audio file(s)", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            val list = allVideos.filter { it.id in selectedVideoIds }
                            list.forEach { v ->
                                repository.addToWhitelist("VIDEO", v.path.ifBlank { v.id.toString() }, v.title, v.folderPath, v.sourceTreeUri, v.documentId, 1)
                            }
                            selectedVideoIds = emptySet()
                            Toast.makeText(context, "Whitelisted ${list.size} video file(s)", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            val list = mediaFolders.filter { it.canonicalKey in selectedFolderKeys }
                            list.forEach { f ->
                                repository.addFolderRule(RuleListKind.WHITELIST, RuleType.FOLDER, f.treeUri, f.documentId, f.displayName, f.displayPath, true, initialMediaCount = f.audioCount + f.videoCount)
                            }
                            selectedFolderKeys = emptySet()
                            Toast.makeText(context, "Whitelisted ${list.size} folder(s)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isSelectionMode = false
                }
            },
            onCancel = {
                isSelectionMode = false
                selectedAudioIds = emptySet()
                selectedVideoIds = emptySet()
                selectedFolderKeys = emptySet()
            }
        )

        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                if (allTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No local music files indexed yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allTracks, key = { it.id }) { track ->
                            val isSelected = track.id in selectedAudioIds
                            val isPlayingThis = currentPlayingTrack?.id == track.id
                            val activeSpin = isPlayingThis && isAudioPlaying && isAppResumed
                            val reelAngle by rememberVisibleCassetteReelAngle(
                                isActivelyPlayingAndVisible = activeSpin,
                                seedAngleDegrees = ((track.id * 29L) % 360L).toFloat()
                            )
                            val identifier = track.path.ifBlank { track.id.toString() }
                            val blEntry = blacklistEntries.find {
                                it.type.equals("SONG", true) && (it.identifier.equals(identifier, true) || it.identifier.equals(track.title, true))
                            }
                            val wlEntry = whitelistEntries.find {
                                it.type.equals("SONG", true) && (it.identifier.equals(identifier, true) || it.identifier.equals(track.title, true))
                            }
                            var showMenu by remember { mutableStateOf(false) }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedAudioIds = if (isSelected) selectedAudioIds - track.id else selectedAudioIds + track.id
                                            } else {
                                                playbackManager.playTrack(track, allTracks)
                                            }
                                        },
                                        onLongClick = {
                                            isSelectionMode = true
                                            selectedAudioIds = selectedAudioIds + track.id
                                        }
                                    ),
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    isPlayingThis -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                },
                                border = BorderStroke(
                                    width = if (isPlayingThis || isSelected) 1.2.dp else 0.8.dp,
                                    color = if (isPlayingThis || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedAudioIds = if (it) selectedAudioIds + track.id else selectedAudioIds - track.id
                                            }
                                        )
                                    } else {
                                        MiniCassetteArtwork(
                                            track = track,
                                            isPlaying = activeSpin,
                                            reelAngleDegrees = reelAngle,
                                            modifier = Modifier.height(38.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.mimeType.substringAfterLast("/").uppercase().take(4)} • ${formatBytes(track.fileSize)} • ${track.path.ifBlank { "Local Storage" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Rounded.MoreVert, contentDescription = "File Options")
                                        }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Play Next") },
                                                onClick = {
                                                    playbackManager.playNextInQueue(track)
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
                                                        proposeRule(
                                                            QuickRuleProposal(
                                                                targetList = RuleListKind.BLACKLIST,
                                                                ruleType = RuleType.SONG,
                                                                identifier = identifier,
                                                                title = track.title,
                                                                displayPath = track.path.ifBlank { track.folderPath },
                                                                documentId = track.documentId,
                                                                treeUri = track.sourceTreeUri,
                                                                mediaArtist = track.artist,
                                                                mediaAlbum = track.album
                                                            )
                                                        )
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
                                                        proposeRule(
                                                            QuickRuleProposal(
                                                                targetList = RuleListKind.WHITELIST,
                                                                ruleType = RuleType.SONG,
                                                                identifier = identifier,
                                                                title = track.title,
                                                                displayPath = track.path.ifBlank { track.folderPath },
                                                                documentId = track.documentId,
                                                                treeUri = track.sourceTreeUri,
                                                                mediaArtist = track.artist,
                                                                mediaAlbum = track.album
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
            }

            1 -> {
                if (allVideos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No local videos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { coroutineScope.launch { repository.scanVideos() } }) {
                                Text("Scan Device Videos")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allVideos, key = { it.id }) { video ->
                            val isSelected = video.id in selectedVideoIds
                            val identifier = video.path.ifBlank { video.id.toString() }
                            val blEntry = blacklistEntries.find {
                                it.type.equals("VIDEO", true) && (it.identifier.equals(identifier, true) || it.identifier.equals(video.title, true))
                            }
                            val wlEntry = whitelistEntries.find {
                                it.type.equals("VIDEO", true) && (it.identifier.equals(identifier, true) || it.identifier.equals(video.title, true))
                            }
                            var showMenu by remember { mutableStateOf(false) }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedVideoIds = if (isSelected) selectedVideoIds - video.id else selectedVideoIds + video.id
                                            } else {
                                                onPlayVideo(video)
                                            }
                                        },
                                        onLongClick = {
                                            isSelectionMode = true
                                            selectedVideoIds = selectedVideoIds + video.id
                                        }
                                    ),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.2.dp else 0.8.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    selectedVideoIds = if (it) selectedVideoIds + video.id else selectedVideoIds - video.id
                                                }
                                            )
                                        } else {
                                            VideoRetroThumbCard(
                                                video = video,
                                                modifier = Modifier.size(width = 64.dp, height = 42.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = video.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${video.resolution} • ${formatBytes(video.fileSize)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    repository.setVideoWatchLater(video.id, !video.inWatchLater)
                                                }
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = if (video.inWatchLater) Icons.Rounded.WatchLater else Icons.Rounded.Schedule,
                                                contentDescription = "Watch Later",
                                                tint = if (video.inWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Box {
                                            IconButton(onClick = { showMenu = true }) {
                                                Icon(Icons.Rounded.MoreVert, contentDescription = "Video Options")
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
                                                        proposeRule(
                                                            QuickRuleProposal(
                                                                targetList = RuleListKind.BLACKLIST,
                                                                ruleType = RuleType.VIDEO,
                                                                identifier = identifier,
                                                                title = video.title,
                                                                displayPath = video.path.ifBlank { video.folderPath },
                                                                documentId = video.documentId,
                                                                treeUri = video.sourceTreeUri
                                                            )
                                                        )
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
                                                        proposeRule(
                                                            QuickRuleProposal(
                                                                targetList = RuleListKind.WHITELIST,
                                                                ruleType = RuleType.VIDEO,
                                                                identifier = identifier,
                                                                title = video.title,
                                                                displayPath = video.path.ifBlank { video.folderPath },
                                                                documentId = video.documentId,
                                                                treeUri = video.sourceTreeUri
                                                            )
                                                        )
                                                        showMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                    if (video.durationMs > 0 && video.watchProgressMs > 0) {
                                        LinearProgressIndicator(
                                            progress = { (video.watchProgressMs.toFloat() / video.durationMs).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.5.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // Folders tab (SAF direct folder picker + discovered folders with contextual actions)
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
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
                                    folderPickerLauncher.launch(null)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.weight(1f).testTag("files_add_folder_blacklist")
                            ) {
                                Icon(Icons.Rounded.FolderOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Folder to Blacklist", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    pickerListKind = RuleListKind.WHITELIST
                                    folderPickerLauncher.launch(null)
                                },
                                modifier = Modifier.weight(1f).testTag("files_add_folder_whitelist")
                            ) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Folder to Whitelist", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
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
                                    append("${folder.audioCount}A • ${folder.videoCount}V")
                                    if (folder.isBlacklisted) append(" • BL")
                                    else if (folder.isWhitelisted) append(" • WL")
                                },
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedFolderKeys = if (isSelected) selectedFolderKeys - folder.canonicalKey else selectedFolderKeys + folder.canonicalKey
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
                            )
                        }
                    }
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
