package com.example.sangeet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.*
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.rules.RuleConflict
import com.example.sangeet.data.rules.RuleFilterEngine
import com.example.sangeet.scanner.FolderInspectionResult
import com.example.sangeet.ui.components.ConflictWarningDialog
import com.example.sangeet.ui.components.FolderConfirmationDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class RuleCategoryFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    MUSIC_FOLDERS("Music folders"),
    VIDEOS("Videos"),
    VIDEO_FOLDERS("Video folders")
}

enum class RuleSortMode(val label: String) {
    DATE_DESC("Newest First"),
    NAME_ASC("Name (A-Z)"),
    COUNT_DESC("Most Media Files"),
    TYPE_ASC("Entry Type")
}

data class UnifiedRuleItem(
    val uniqueKey: String,
    val dbId: Long,
    val isFolderRule: Boolean,
    val listKind: RuleListKind,
    val ruleType: RuleType,
    val name: String,
    val locationPath: String,
    val affectedCount: Int,
    val subfolderCount: Int,
    val addedAt: Long,
    val status: RuleStatus,
    val lastError: String,
    val folderRule: FolderRule? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BlacklistWhitelistScreen(
    repository: MediaRepository,
    initialTab: Int = 0, // 0 = Blacklist, 1 = Whitelist
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val activeListKind = if (selectedTab == 0) RuleListKind.BLACKLIST else RuleListKind.WHITELIST

    var selectedCategory by remember { mutableStateOf(RuleCategoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(RuleSortMode.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Folder Details Navigation
    var activeFolderDetailId by remember { mutableStateOf<Long?>(null) }

    // Pending SAF Folder Picker operations
    var pickerTargetListKind by remember { mutableStateOf(RuleListKind.BLACKLIST) }
    var pickerReplaceRule by remember { mutableStateOf<FolderRule?>(null) }
    var pendingFolderInspection by remember { mutableStateOf<FolderInspectionResult.Success?>(null) }
    var pendingFolderConflicts by remember { mutableStateOf<List<RuleConflict>>(emptyList()) }
    var folderErrorBanner by remember { mutableStateOf<String?>(null) }

    // Add non-folder item dialog state
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemType by remember { mutableStateOf(RuleType.SONG) }
    var newItemQuery by remember { mutableStateOf("") }
    var pendingItemConflict by remember { mutableStateOf<Pair<PendingItemRule, List<RuleConflict>>?>(null) }

    // Clear confirmation
    var showClearConfirm by remember { mutableStateOf(false) }

    // Collect DB Flows
    val blacklistEntries by repository.blacklist.collectAsState(initial = emptyList())
    val whitelistEntries by repository.whitelist.collectAsState(initial = emptyList())
    val folderRules by repository.folderRules.collectAsState(initial = emptyList())
    val rawTracks by repository.rawAllTracks.collectAsState(initial = emptyList())
    val rawVideos by repository.rawAllVideos.collectAsState(initial = emptyList())

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // If viewing Folder Details sub-screen
    if (activeFolderDetailId != null) {
        FolderRuleDetailScreen(
            ruleId = activeFolderDetailId!!,
            repository = repository,
            onNavigateBack = { activeFolderDetailId = null }
        )
        return
    }

    // SAF OpenDocumentTree Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = repository.inspectPickedSafFolder(uri)
                when (res) {
                    is FolderInspectionResult.Success -> {
                        val replaceTarget = pickerReplaceRule
                        if (replaceTarget != null) {
                            repository.replaceFolderRuleSelection(
                                ruleId = replaceTarget.id,
                                newTreeUri = uri,
                                ruleType = replaceTarget.ruleType,
                                includeSubfolders = replaceTarget.includeSubfolders
                            )
                            pickerReplaceRule = null
                            Toast.makeText(context, "Folder selection updated", Toast.LENGTH_SHORT).show()
                        } else {
                            val defaultScope = when (selectedCategory) {
                                RuleCategoryFilter.MUSIC_FOLDERS -> RuleType.MUSIC_FOLDER
                                RuleCategoryFilter.VIDEO_FOLDERS -> RuleType.VIDEO_FOLDER
                                else -> RuleType.FOLDER
                            }
                            val conflicts = repository.detectConflictsForNewRule(
                                targetList = pickerTargetListKind,
                                ruleType = defaultScope,
                                identifier = res.displayPath,
                                documentId = res.documentId,
                                treeUri = res.treeUri
                            )
                            pendingFolderInspection = res
                            pendingFolderConflicts = conflicts
                        }
                    }
                    is FolderInspectionResult.PermissionRevoked -> {
                        folderErrorBanner = res.message
                    }
                    is FolderInspectionResult.FolderUnavailable -> {
                        folderErrorBanner = res.message
                    }
                }
            }
        } else {
            pickerReplaceRule = null
        }
    }

    // Build unified items list for the active tab
    val allItemsForTab = remember(
        activeListKind, blacklistEntries, whitelistEntries, folderRules, rawTracks, rawVideos
    ) {
        val list = mutableListOf<UnifiedRuleItem>()

        // 1. FolderRule entities
        folderRules.filter { it.listKind == activeListKind }.forEach { fr ->
            val liveCount = RuleFilterEngine.countAffectedMediaForFolderRule(fr, rawTracks, rawVideos)
            val count = maxOf(fr.affectedMediaCount, liveCount)
            list.add(
                UnifiedRuleItem(
                    uniqueKey = "fr_${fr.id}",
                    dbId = fr.id,
                    isFolderRule = true,
                    listKind = fr.listKind,
                    ruleType = fr.ruleType,
                    name = fr.displayName,
                    locationPath = fr.displayPath,
                    affectedCount = count,
                    subfolderCount = fr.subfolderCount,
                    addedAt = fr.creationDate.epochMillis,
                    status = fr.status,
                    lastError = fr.lastError,
                    folderRule = fr
                )
            )
        }

        // 2. Entry items (Song, Artist, Album, Video, plus legacy entries)
        if (activeListKind == RuleListKind.BLACKLIST) {
            blacklistEntries.forEach { b ->
                val rType = RuleType.fromString(b.type)
                val count = RuleFilterEngine.countAffectedMediaForEntry(
                    b.type, b.identifier, b.documentId, b.treeUri, rawTracks, rawVideos
                )
                list.add(
                    UnifiedRuleItem(
                        uniqueKey = "bl_${b.id}",
                        dbId = b.id,
                        isFolderRule = false,
                        listKind = RuleListKind.BLACKLIST,
                        ruleType = rType,
                        name = b.title,
                        locationPath = b.displayPath.ifBlank { b.identifier },
                        affectedCount = maxOf(b.affectedCount, count),
                        subfolderCount = 0,
                        addedAt = b.addedAt,
                        status = b.status,
                        lastError = ""
                    )
                )
            }
        } else {
            whitelistEntries.forEach { w ->
                val rType = RuleType.fromString(w.type)
                val count = RuleFilterEngine.countAffectedMediaForEntry(
                    w.type, w.identifier, w.documentId, w.treeUri, rawTracks, rawVideos
                )
                list.add(
                    UnifiedRuleItem(
                        uniqueKey = "wl_${w.id}",
                        dbId = w.id,
                        isFolderRule = false,
                        listKind = RuleListKind.WHITELIST,
                        ruleType = rType,
                        name = w.title,
                        locationPath = w.displayPath.ifBlank { w.identifier },
                        affectedCount = maxOf(w.affectedCount, count),
                        subfolderCount = 0,
                        addedAt = w.addedAt,
                        status = w.status,
                        lastError = ""
                    )
                )
            }
        }

        list
    }

    val filteredAndSorted = remember(allItemsForTab, selectedCategory, searchQuery, sortMode) {
        val byCategory = allItemsForTab.filter { item ->
            when (selectedCategory) {
                RuleCategoryFilter.ALL -> true
                RuleCategoryFilter.SONGS -> item.ruleType == RuleType.SONG
                RuleCategoryFilter.ARTISTS -> item.ruleType == RuleType.ARTIST
                RuleCategoryFilter.ALBUMS -> item.ruleType == RuleType.ALBUM
                RuleCategoryFilter.MUSIC_FOLDERS -> item.ruleType == RuleType.MUSIC_FOLDER || item.ruleType == RuleType.FOLDER
                RuleCategoryFilter.VIDEOS -> item.ruleType == RuleType.VIDEO
                RuleCategoryFilter.VIDEO_FOLDERS -> item.ruleType == RuleType.VIDEO_FOLDER || item.ruleType == RuleType.FOLDER
            }
        }

        val byQuery = if (searchQuery.isBlank()) {
            byCategory
        } else {
            val q = searchQuery.trim()
            byCategory.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.locationPath.contains(q, ignoreCase = true) ||
                    it.ruleType.displayName.contains(q, ignoreCase = true)
            }
        }

        when (sortMode) {
            RuleSortMode.DATE_DESC -> byQuery.sortedByDescending { it.addedAt }
            RuleSortMode.NAME_ASC -> byQuery.sortedBy { it.name.lowercase() }
            RuleSortMode.COUNT_DESC -> byQuery.sortedByDescending { it.affectedCount }
            RuleSortMode.TYPE_ASC -> byQuery.sortedBy { it.ruleType.displayName }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("blacklist_whitelist_screen"),
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedKeys.size} Selected")
                    } else {
                        Text(
                            text = "Blacklist & Whitelist",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedKeys = emptySet()
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                val allKeys = filteredAndSorted.map { it.uniqueKey }.toSet()
                                selectedKeys = if (selectedKeys.size == allKeys.size) emptySet() else allKeys
                            }
                        ) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(
                            enabled = selectedKeys.isNotEmpty(),
                            onClick = {
                                val itemsToDelete = allItemsForTab.filter { it.uniqueKey in selectedKeys }
                                val entryIds = itemsToDelete.filter { !it.isFolderRule }.map { it.dbId }
                                val folderIds = itemsToDelete.filter { it.isFolderRule }.map { it.dbId }
                                coroutineScope.launch {
                                    repository.bulkDeleteRules(activeListKind, entryIds, folderIds)
                                    isSelectionMode = false
                                    selectedKeys = emptySet()
                                    Toast.makeText(context, "Removed ${itemsToDelete.size} rule(s)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Rounded.Sort, contentDescription = "Sort Rules")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                RuleSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        leadingIcon = {
                                            if (sortMode == mode) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            sortMode = mode
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        if (allItemsForTab.isNotEmpty()) {
                            IconButton(
                                onClick = { isSelectionMode = true }
                            ) {
                                Icon(Icons.Rounded.Checklist, contentDescription = "Multi-select")
                            }
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear List", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Blacklist / Whitelist Primary Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                divider = {}
            ) {
                val blCount = blacklistEntries.size + folderRules.count { it.listKind == RuleListKind.BLACKLIST }
                val wlCount = whitelistEntries.size + folderRules.count { it.listKind == RuleListKind.WHITELIST }
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        isSelectionMode = false
                        selectedKeys = emptySet()
                    },
                    text = { Text("Blacklist ($blCount)", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("tab_blacklist")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        isSelectionMode = false
                        selectedKeys = emptySet()
                    },
                    text = { Text("Whitelist ($wlCount)", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("tab_whitelist")
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Error Banner if SAF picker or permission failed
                if (folderErrorBanner != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = folderErrorBanner!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { folderErrorBanner = null }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                                }
                            }
                        }
                    }
                }

                // Two Prominent Direct Folder Selection Options
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "DIRECT FOLDER SELECTION",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        pickerTargetListKind = RuleListKind.BLACKLIST
                                        pickerReplaceRule = null
                                        folderPickerLauncher.launch(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface,
                                        contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_add_folder_blacklist")
                                ) {
                                    Icon(Icons.Rounded.FolderOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Folder to Blacklist", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        pickerTargetListKind = RuleListKind.WHITELIST
                                        pickerReplaceRule = null
                                        folderPickerLauncher.launch(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_add_folder_whitelist")
                                ) {
                                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Folder to Whitelist", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { showAddItemDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_add_item_rule")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Song, Artist, Album, or Video to ${if (selectedTab == 0) "Blacklist" else "Whitelist"}")
                            }
                        }
                    }
                }

                // Rule Mode Summary Note for Whitelist
                if (selectedTab == 1) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (allItemsForTab.isEmpty()) {
                                    "Whitelist is empty — all non-blacklisted media is shown. Adding folders or items restricts Library/Home/Search to whitelisted media (and includes SAF folders skipped by MediaStore)."
                                } else {
                                    "Whitelist active — only matching media appears (except items blocked by Blacklist priority rules)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search rules by name, path, or type...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rules_search_input")
                    )
                }

                // Category Chips
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RuleCategoryFilter.entries) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }

                // Entries List
                if (filteredAndSorted.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Rounded.FolderOff else Icons.Rounded.RuleFolder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No ${selectedCategory.label} Rules Found",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Use the folder picker above to select internal or SD card folders directly, or long-press items in Library and Files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredAndSorted, key = { it.uniqueKey }) { item ->
                        val isSelected = item.uniqueKey in selectedKeys
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedKeys = if (isSelected) selectedKeys - item.uniqueKey else selectedKeys + item.uniqueKey
                                        } else if (item.isFolderRule) {
                                            activeFolderDetailId = item.dbId
                                        }
                                    },
                                    onLongClick = {
                                        isSelectionMode = true
                                        selectedKeys = selectedKeys + item.uniqueKey
                                    }
                                )
                                .testTag("rule_card_${item.uniqueKey}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedKeys = if (checked) selectedKeys + item.uniqueKey else selectedKeys - item.uniqueKey
                                            }
                                        )
                                    } else {
                                        Icon(
                                            imageVector = when (item.ruleType) {
                                                RuleType.SONG -> Icons.Rounded.MusicNote
                                                RuleType.ARTIST -> Icons.Rounded.Person
                                                RuleType.ALBUM -> Icons.Rounded.Album
                                                RuleType.VIDEO -> Icons.Rounded.Movie
                                                RuleType.VIDEO_FOLDER -> Icons.Rounded.VideoLibrary
                                                else -> Icons.Rounded.Folder
                                            },
                                            contentDescription = null,
                                            tint = if (item.status == RuleStatus.PERMISSION_REVOKED || item.status == RuleStatus.FOLDER_NOT_FOUND) {
                                                MaterialTheme.colorScheme.error
                                            } else if (item.listKind == RuleListKind.BLACKLIST) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.ruleType.displayName} • ${dateFormat.format(Date(item.addedAt))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (item.isFolderRule && item.folderRule != null) {
                                        IconButton(
                                            onClick = {
                                                pickerTargetListKind = item.listKind
                                                pickerReplaceRule = item.folderRule
                                                folderPickerLauncher.launch(null)
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Replace Folder", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                if (item.isFolderRule) {
                                                    repository.removeFolderRule(item.dbId)
                                                } else if (item.listKind == RuleListKind.BLACKLIST) {
                                                    repository.removeFromBlacklist(item.dbId)
                                                } else {
                                                    repository.removeFromWhitelist(item.dbId)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                // Location & Affected Count Row
                                Text(
                                    text = item.locationPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statsText = buildString {
                                        append("${item.affectedCount} affected file(s)")
                                        if (item.isFolderRule && item.subfolderCount > 0) {
                                            append(" • ${item.subfolderCount} subfolder(s)")
                                        }
                                    }
                                    Text(
                                        text = statsText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )

                                    if (item.status != RuleStatus.ACTIVE) {
                                        AssistChip(
                                            onClick = {
                                                if (item.isFolderRule && item.folderRule != null) {
                                                    pickerTargetListKind = item.listKind
                                                    pickerReplaceRule = item.folderRule
                                                    folderPickerLauncher.launch(null)
                                                }
                                            },
                                            label = { Text(item.status.label, fontSize = 11.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        )
                                    } else if (item.isFolderRule) {
                                        Text(
                                            text = "Tap for details →",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Folder Confirmation Dialog after SAF picker returns
    if (pendingFolderInspection != null) {
        val inspection = pendingFolderInspection!!
        val initialScope = when (selectedCategory) {
            RuleCategoryFilter.MUSIC_FOLDERS -> RuleType.MUSIC_FOLDER
            RuleCategoryFilter.VIDEO_FOLDERS -> RuleType.VIDEO_FOLDER
            else -> RuleType.FOLDER
        }
        FolderConfirmationDialog(
            inspection = inspection,
            targetList = pickerTargetListKind,
            initialRuleType = initialScope,
            conflicts = pendingFolderConflicts,
            onDismiss = {
                pendingFolderInspection = null
                pendingFolderConflicts = emptyList()
            },
            onConfirm = { chosenType, includeSubfolders, removeConflicts ->
                coroutineScope.launch {
                    if (removeConflicts) {
                        for (c in pendingFolderConflicts) {
                            repository.removeConflictingRule(c)
                        }
                    }
                    repository.addFolderRule(
                        listKind = pickerTargetListKind,
                        ruleType = chosenType,
                        treeUri = inspection.treeUri,
                        documentId = inspection.documentId,
                        displayName = inspection.displayName,
                        displayPath = inspection.displayPath,
                        includeSubfolders = includeSubfolders,
                        subfolderCount = inspection.subfolderCount,
                        initialMediaCount = when (chosenType) {
                            RuleType.MUSIC_FOLDER -> inspection.audioCount
                            RuleType.VIDEO_FOLDER -> inspection.videoCount
                            else -> inspection.audioCount + inspection.videoCount
                        }
                    )
                    selectedTab = if (pickerTargetListKind == RuleListKind.BLACKLIST) 0 else 1
                    pendingFolderInspection = null
                    pendingFolderConflicts = emptyList()
                    Toast.makeText(
                        context,
                        "Added '${inspection.displayName}' to ${if (pickerTargetListKind == RuleListKind.BLACKLIST) "Blacklist" else "Whitelist"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // Add Item Rule Dialog (Song, Artist, Album, Video with Library Picker + Search)
    if (showAddItemDialog) {
        AddItemRuleDialog(
            targetList = activeListKind,
            initialType = newItemType,
            rawTracks = rawTracks,
            rawVideos = rawVideos,
            onDismiss = { showAddItemDialog = false },
            onSelectCandidate = { candidate ->
                coroutineScope.launch {
                    val conflicts = repository.detectConflictsForNewRule(
                        targetList = activeListKind,
                        ruleType = candidate.ruleType,
                        identifier = candidate.identifier,
                        documentId = candidate.documentId,
                        treeUri = candidate.treeUri,
                        mediaPath = candidate.displayPath,
                        mediaArtist = candidate.artist,
                        mediaAlbum = candidate.album
                    )
                    if (conflicts.isNotEmpty()) {
                        pendingItemConflict = candidate to conflicts
                        showAddItemDialog = false
                    } else {
                        savePendingItemRule(repository, activeListKind, candidate)
                        showAddItemDialog = false
                        Toast.makeText(context, "Added '${candidate.title}'", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Conflict Warning Dialog for Non-Folder items
    if (pendingItemConflict != null) {
        val (candidate, conflicts) = pendingItemConflict!!
        ConflictWarningDialog(
            title = candidate.title,
            targetList = activeListKind,
            conflicts = conflicts,
            onDismiss = { pendingItemConflict = null },
            onRemoveConflictsAndAdd = {
                coroutineScope.launch {
                    for (c in conflicts) {
                        repository.removeConflictingRule(c)
                    }
                    savePendingItemRule(repository, activeListKind, candidate)
                    pendingItemConflict = null
                    Toast.makeText(context, "Resolved conflicts and added rule", Toast.LENGTH_SHORT).show()
                }
            },
            onAddAnyway = {
                coroutineScope.launch {
                    savePendingItemRule(repository, activeListKind, candidate)
                    pendingItemConflict = null
                }
            }
        )
    }

    // Clear Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear ${if (selectedTab == 0) "Blacklist" else "Whitelist"}?") },
            text = {
                Text(
                    if (selectedTab == 0) {
                        "This removes all Blacklist rules (songs, artists, albums, videos, and folders) and restores excluded items to your Library. Original files are never modified."
                    } else {
                        "This removes all Whitelist rules and restores full library visibility."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            if (selectedTab == 0) repository.clearBlacklist() else repository.clearWhitelist()
                            showClearConfirm = false
                        }
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private data class PendingItemRule(
    val ruleType: RuleType,
    val identifier: String,
    val title: String,
    val displayPath: String = "",
    val documentId: String = "",
    val treeUri: String = "",
    val artist: String = "",
    val album: String = ""
)

private suspend fun savePendingItemRule(
    repository: MediaRepository,
    targetList: RuleListKind,
    candidate: PendingItemRule
) {
    if (targetList == RuleListKind.BLACKLIST) {
        repository.addToBlacklist(
            type = candidate.ruleType.name,
            identifier = candidate.identifier,
            title = candidate.title,
            displayPath = candidate.displayPath,
            treeUri = candidate.treeUri,
            documentId = candidate.documentId
        )
    } else {
        repository.addToWhitelist(
            type = candidate.ruleType.name,
            identifier = candidate.identifier,
            title = candidate.title,
            displayPath = candidate.displayPath,
            treeUri = candidate.treeUri,
            documentId = candidate.documentId
        )
    }
}

@Composable
private fun AddItemRuleDialog(
    targetList: RuleListKind,
    initialType: RuleType,
    rawTracks: List<AudioTrack>,
    rawVideos: List<VideoTrack>,
    onDismiss: () -> Unit,
    onSelectCandidate: (PendingItemRule) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(
            if (initialType in listOf(RuleType.SONG, RuleType.ARTIST, RuleType.ALBUM, RuleType.VIDEO)) initialType else RuleType.SONG
        )
    }
    var query by remember { mutableStateOf("") }

    val suggestions = remember(selectedType, query, rawTracks, rawVideos) {
        val q = query.trim()
        when (selectedType) {
            RuleType.SONG -> rawTracks
                .filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) }
                .take(15)
                .map {
                    PendingItemRule(
                        ruleType = RuleType.SONG,
                        identifier = it.id.toString(),
                        title = it.title,
                        displayPath = it.path.ifBlank { "${it.artist} • ${it.album}" },
                        documentId = it.documentId,
                        treeUri = it.sourceTreeUri,
                        artist = it.artist,
                        album = it.album
                    )
                }
            RuleType.ARTIST -> rawTracks.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted()
                .filter { q.isBlank() || it.contains(q, true) }
                .take(15)
                .map { artist ->
                    PendingItemRule(RuleType.ARTIST, artist, artist, "$artist (Artist)", artist = artist)
                }
            RuleType.ALBUM -> rawTracks.map { it.album }.filter { it.isNotBlank() }.distinct().sorted()
                .filter { q.isBlank() || it.contains(q, true) }
                .take(15)
                .map { album ->
                    PendingItemRule(RuleType.ALBUM, album, album, "$album (Album)", album = album)
                }
            RuleType.VIDEO -> rawVideos
                .filter { q.isBlank() || it.title.contains(q, true) }
                .take(15)
                .map {
                    PendingItemRule(
                        ruleType = RuleType.VIDEO,
                        identifier = it.id.toString(),
                        title = it.title,
                        displayPath = it.path.ifBlank { it.resolution },
                        documentId = it.documentId,
                        treeUri = it.sourceTreeUri
                    )
                }
            else -> emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add to ${if (targetList == RuleListKind.BLACKLIST) "Blacklist" else "Whitelist"}")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(RuleType.SONG, RuleType.ARTIST, RuleType.ALBUM, RuleType.VIDEO)) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search library or type name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(suggestions) { item ->
                        Surface(
                            onClick = { onSelectCandidate(item) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.displayPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (query.isNotBlank()) {
                TextButton(
                    onClick = {
                        onSelectCandidate(
                            PendingItemRule(
                                ruleType = selectedType,
                                identifier = query.trim(),
                                title = query.trim(),
                                displayPath = query.trim()
                            )
                        )
                    }
                ) {
                    Text("Add \"${query.trim()}\"")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
