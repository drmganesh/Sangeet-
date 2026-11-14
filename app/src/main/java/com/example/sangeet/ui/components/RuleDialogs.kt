package com.example.sangeet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sangeet.data.model.RuleListKind
import com.example.sangeet.data.model.RuleType
import com.example.sangeet.data.rules.RuleConflict
import com.example.sangeet.scanner.FolderInspectionResult

@Composable
fun FolderConfirmationDialog(
    inspection: FolderInspectionResult.Success,
    targetList: RuleListKind,
    initialRuleType: RuleType = RuleType.FOLDER,
    conflicts: List<RuleConflict>,
    isReplacement: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (selectedRuleType: RuleType, includeSubfolders: Boolean, removeConflicts: Boolean) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(
            if (initialRuleType in listOf(RuleType.FOLDER, RuleType.MUSIC_FOLDER, RuleType.VIDEO_FOLDER)) {
                initialRuleType
            } else {
                RuleType.FOLDER
            }
        )
    }
    var includeSubfolders by remember { mutableStateOf(true) }

    val totalCount = when (selectedType) {
        RuleType.MUSIC_FOLDER -> inspection.audioCount
        RuleType.VIDEO_FOLDER -> inspection.videoCount
        else -> inspection.audioCount + inspection.videoCount
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (targetList == RuleListKind.BLACKLIST) Icons.Rounded.FolderOff else Icons.Rounded.CreateNewFolder,
                contentDescription = null,
                tint = if (targetList == RuleListKind.BLACKLIST) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = when {
                    isReplacement -> "Confirm Replacement Folder"
                    targetList == RuleListKind.BLACKLIST -> "Add Folder to Blacklist?"
                    else -> "Add Folder to Whitelist?"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Folder identity card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = inspection.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = inspection.displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "${inspection.audioCount} audio • ${inspection.videoCount} videos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${inspection.subfolderCount} subfolders",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Rule Scope Selector (Music Folder / Video Folder / Both)
                Text(
                    text = "Folder Rule Scope",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        RuleType.FOLDER to "All Media",
                        RuleType.MUSIC_FOLDER to "Music",
                        RuleType.VIDEO_FOLDER to "Videos"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Include subfolders switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include Subfolders Recursively", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Affects $totalCount media files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includeSubfolders,
                        onCheckedChange = { includeSubfolders = it }
                    )
                }

                // Conflict Warning Section (Priority Rules)
                if (conflicts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Rule Conflict Detected (${conflicts.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            conflicts.take(3).forEach { c ->
                                Text(
                                    text = "• ${c.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (conflicts.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        onClick = { onConfirm(selectedType, includeSubfolders, true) },
                        modifier = Modifier.testTag("folder_confirm_resolve_conflicts")
                    ) {
                        Text("Remove Conflicts & Add")
                    }
                    TextButton(
                        onClick = { onConfirm(selectedType, includeSubfolders, false) }
                    ) {
                        Text("Add Anyway (Keep Both)")
                    }
                }
            } else {
                Button(
                    onClick = { onConfirm(selectedType, includeSubfolders, false) },
                    modifier = Modifier.testTag("folder_confirm_add")
                ) {
                    Text(
                        when {
                            isReplacement -> "Update Folder"
                            targetList == RuleListKind.BLACKLIST -> "Add to Blacklist"
                            else -> "Add to Whitelist"
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConflictWarningDialog(
    title: String,
    targetList: RuleListKind,
    conflicts: List<RuleConflict>,
    onDismiss: () -> Unit,
    onRemoveConflictsAndAdd: () -> Unit,
    onAddAnyway: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = {
            Text(
                text = "Rule Conflict Warning",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Adding \"$title\" to ${if (targetList == RuleListKind.WHITELIST) "Whitelist" else "Blacklist"} conflicts with existing rule(s):",
                    style = MaterialTheme.typography.bodyMedium
                )
                conflicts.forEach { conflict ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "${conflict.existingListKind.name}: ${conflict.existingTitle} (${conflict.existingRuleType.displayName})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = conflict.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Text(
                    text = "Priority Rule: Blacklist always overrides Whitelist for the same item or descendant folders.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onRemoveConflictsAndAdd) {
                    Text("Remove Conflicting Rule(s) & Add")
                }
                TextButton(onClick = onAddAnyway) {
                    Text("Keep Both Rules")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
