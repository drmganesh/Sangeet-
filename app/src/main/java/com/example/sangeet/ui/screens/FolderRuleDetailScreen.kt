package com.example.sangeet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.*
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.rules.StoragePathHelper
import com.example.sangeet.scanner.FolderInspectionResult
import com.example.sangeet.ui.components.FolderConfirmationDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderRuleDetailScreen(
    ruleId: Long,
    repository: MediaRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val rule by repository.getFolderRuleFlow(ruleId).collectAsState(initial = null)
    val rawTracks by repository.rawAllTracks.collectAsState(initial = emptyList())
    val rawVideos by repository.rawAllVideos.collectAsState(initial = emptyList())
    val permissions by repository.folderPermissions.collectAsState(initial = emptyList())

    var subfolderNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var pendingReplacementInspection by remember { mutableStateOf<FolderInspectionResult.Success?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

    val currentRule = rule
    val permissionRecord = remember(currentRule, permissions) {
        if (currentRule == null) null else permissions.find { it.treeUri == currentRule.treeUri }
    }

    // Load live subfolder names via SAF when rule opens
    LaunchedEffect(currentRule?.id, currentRule?.treeUri) {
        val r = currentRule ?: return@LaunchedEffect
        if (r.treeUri.startsWith("content://")) {
            val result = repository.safScanner.inspectFolder(
                treeUriString = r.treeUri,
                extractDeepMetadata = false,
                isWhitelistSource = false
            )
            if (result is FolderInspectionResult.Success) {
                subfolderNames = result.subfolderNames
            }
        }
    }

    // SAF Picker for replacing folder or re-granting permission
    val replaceFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val r = currentRule ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            coroutineScope.launch {
                val res = repository.inspectPickedSafFolder(uri)
                when (res) {
                    is FolderInspectionResult.Success -> pendingReplacementInspection = res
                    is FolderInspectionResult.PermissionRevoked -> {
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                    is FolderInspectionResult.FolderUnavailable -> {
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val regrantLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val r = currentRule ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            coroutineScope.launch {
                isRefreshing = true
                val res = repository.regrantFolderPermission(r.id, uri)
                isRefreshing = false
                if (res is FolderInspectionResult.Success) {
                    subfolderNames = res.subfolderNames
                    Toast.makeText(context, "Permission restored & folder rescanned", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not verify folder access", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (currentRule == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Folder Details") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Folder rule not found or removed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val matchingAudio = remember(currentRule, rawTracks) {
        if (currentRule.ruleType == RuleType.VIDEO_FOLDER) emptyList()
        else rawTracks.filter { t ->
            StoragePathHelper.isMediaInsideFolder(
                t.path, t.folderPath, t.documentId, t.sourceTreeUri,
                currentRule.displayPath, currentRule.documentId, currentRule.treeUri,
                currentRule.includeSubfolders
            )
        }
    }

    val matchingVideos = remember(currentRule, rawVideos) {
        if (currentRule.ruleType == RuleType.MUSIC_FOLDER) emptyList()
        else rawVideos.filter { v ->
            StoragePathHelper.isMediaInsideFolder(
                v.path, v.folderPath, v.documentId, v.sourceTreeUri,
                currentRule.displayPath, currentRule.documentId, currentRule.treeUri,
                currentRule.includeSubfolders
            )
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("folder_detail_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentRule.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isRefreshing = true
                                repository.rescanFolderRule(currentRule.id)
                                isRefreshing = false
                                Toast.makeText(context, "Folder rescanned", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Rescan Folder")
                    }
                    IconButton(onClick = { showRemoveConfirm = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove Rule", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission / Error Alert Banner if revoked or unavailable
            if (currentRule.status == RuleStatus.PERMISSION_REVOKED || currentRule.status == RuleStatus.FOLDER_NOT_FOUND) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FolderOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = currentRule.status.label,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = currentRule.lastError.ifBlank { "Access permission to this folder was revoked or the folder was moved." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = { regrantLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Grant Access Again")
                            }
                        }
                    }
                }
            }

            // Main Summary Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        if (currentRule.listKind == RuleListKind.BLACKLIST) "BLACKLISTED FOLDER" else "WHITELISTED FOLDER",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (currentRule.listKind == RuleListKind.BLACKLIST) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = if (currentRule.listKind == RuleListKind.BLACKLIST) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            Text(
                                text = currentRule.ruleType.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text("Location Path", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = currentRule.displayPath,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        if (currentRule.documentId.isNotBlank()) {
                            Text("SAF Document ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = currentRule.documentId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${matchingAudio.size + matchingVideos.size}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Matching Media", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${currentRule.subfolderCount}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text("Subfolders", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (permissionRecord?.isPersisted == true) "Persisted" else "Local Path",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text("Access Type", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Include Subfolders Recursively", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Created ${dateFormatter.format(Date(currentRule.creationDate.epochMillis))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = currentRule.includeSubfolders,
                                onCheckedChange = { checked ->
                                    coroutineScope.launch {
                                        repository.toggleFolderRuleIncludeSubfolders(currentRule.id, checked)
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { replaceFolderLauncher.launch(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Replace Folder")
                            }
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isRefreshing = true
                                        repository.rescanFolderRule(currentRule.id)
                                        isRefreshing = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Rescan Now")
                            }
                        }
                    }
                }
            }

            // Detected Subfolders
            if (subfolderNames.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DETECTED SUBFOLDERS (${subfolderNames.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(subfolderNames) { sub ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(sub) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Matching Audio Files Preview
            item {
                Text(
                    text = "MATCHING MEDIA FILES (${matchingAudio.size + matchingVideos.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (matchingAudio.isEmpty() && matchingVideos.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No indexed media files currently matched inside this folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(matchingAudio.take(50), key = { "a_${it.id}" }) { track ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${track.artist} • ${track.album}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                items(matchingVideos.take(50), key = { "v_${it.id}" }) { video ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Video • ${video.resolution}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingReplacementInspection != null) {
        val insp = pendingReplacementInspection!!
        FolderConfirmationDialog(
            inspection = insp,
            targetList = currentRule.listKind,
            initialRuleType = currentRule.ruleType,
            conflicts = emptyList(),
            isReplacement = true,
            onDismiss = { pendingReplacementInspection = null },
            onConfirm = { newType, includeSubs, _ ->
                coroutineScope.launch {
                    repository.replaceFolderRuleSelection(
                        ruleId = currentRule.id,
                        newTreeUri = Uri.parse(insp.treeUri),
                        ruleType = newType,
                        includeSubfolders = includeSubs
                    )
                    subfolderNames = insp.subfolderNames
                    pendingReplacementInspection = null
                    Toast.makeText(context, "Folder updated", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Folder Rule?") },
            text = { Text("Removing '${currentRule.displayName}' will restore normal library visibility without touching any files on disk.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.removeFolderRule(currentRule.id)
                            showRemoveConfirm = false
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
