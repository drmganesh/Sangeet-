package com.example.sangeet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.RuleListKind
import com.example.sangeet.data.model.RuleType
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.rules.RuleConflict
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.ui.components.QuickRuleConflictHost
import com.example.sangeet.ui.components.QuickRuleProposal
import com.example.sangeet.ui.components.executeQuickRuleWithConflictCheck
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    playbackManager: PlaybackManager,
    repository: MediaRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val recentSearches by repository.recentSearches.collectAsState(initial = emptyList())
    val searchResults by repository.searchTracks(searchQuery).collectAsState(initial = emptyList())
    var pendingConflict by remember { mutableStateOf<Pair<QuickRuleProposal, List<RuleConflict>>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("search_screen")
    ) {
        Spacer(Modifier.height(16.dp))

        // Search Bar Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (it.length > 2) {
                    coroutineScope.launch { repository.recordSearch(it) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input_field"),
            placeholder = { Text("Search songs, artists, albums...") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            } else null,
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        // Recent searches chips
        if (searchQuery.isEmpty() && recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT SEARCHES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = {
                    coroutineScope.launch { repository.clearSearches() }
                }) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(recentSearches) { query ->
                    SuggestionChip(
                        onClick = { searchQuery = query },
                        label = { Text(query) },
                        icon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Search Results List
        if (searchQuery.isNotEmpty()) {
            Text(
                text = "RESULTS (${searchResults.size})",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matches found offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { track ->
                        var showMenu by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { playbackManager.playTrack(track, searchResults) },
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
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
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                                    }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Play Next") },
                                            onClick = {
                                                playbackManager.playNextInQueue(track)
                                                showMenu = false
                                            }
                                        )
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
        } else {
            // Suggestion hints
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "100% Offline Instant Search",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Search across local songs, artists, albums, and genres instantly with zero data usage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
