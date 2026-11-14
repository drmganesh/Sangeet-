package com.example.sangeet.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sangeet.data.model.RuleListKind
import com.example.sangeet.data.model.RuleType
import com.example.sangeet.data.repository.MediaRepository
import com.example.sangeet.data.rules.RuleConflict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class QuickRuleProposal(
    val targetList: RuleListKind,
    val ruleType: RuleType,
    val identifier: String,
    val title: String,
    val displayPath: String = "",
    val documentId: String = "",
    val treeUri: String = "",
    val mediaArtist: String = "",
    val mediaAlbum: String = "",
    val affectedCount: Int = 1
)

suspend fun executeQuickRuleWithConflictCheck(
    repository: MediaRepository,
    proposal: QuickRuleProposal,
    context: Context,
    onConflictsFound: (QuickRuleProposal, List<RuleConflict>) -> Unit
) {
    val conflicts = repository.detectConflictsForNewRule(
        targetList = proposal.targetList,
        ruleType = proposal.ruleType,
        identifier = proposal.identifier,
        documentId = proposal.documentId,
        treeUri = proposal.treeUri,
        mediaPath = proposal.displayPath,
        mediaArtist = proposal.mediaArtist,
        mediaAlbum = proposal.mediaAlbum
    )
    if (conflicts.isNotEmpty()) {
        onConflictsFound(proposal, conflicts)
    } else {
        commitQuickRule(repository, proposal, emptyList(), removeConflicts = false, context = context)
    }
}

suspend fun commitQuickRule(
    repository: MediaRepository,
    proposal: QuickRuleProposal,
    conflicts: List<RuleConflict>,
    removeConflicts: Boolean,
    context: Context
) {
    if (removeConflicts) {
        for (c in conflicts) {
            repository.removeConflictingRule(c)
        }
    }

    val isFolderType = proposal.ruleType == RuleType.FOLDER ||
        proposal.ruleType == RuleType.MUSIC_FOLDER ||
        proposal.ruleType == RuleType.VIDEO_FOLDER

    if (isFolderType && (proposal.treeUri.isNotBlank() || proposal.documentId.isNotBlank())) {
        repository.addFolderRule(
            listKind = proposal.targetList,
            ruleType = proposal.ruleType,
            treeUri = proposal.treeUri,
            documentId = proposal.documentId,
            displayName = proposal.title,
            displayPath = proposal.displayPath.ifBlank { proposal.identifier },
            includeSubfolders = true,
            initialMediaCount = proposal.affectedCount
        )
    } else if (proposal.targetList == RuleListKind.BLACKLIST) {
        repository.addToBlacklist(
            type = proposal.ruleType.name,
            identifier = proposal.identifier,
            title = proposal.title,
            displayPath = proposal.displayPath,
            treeUri = proposal.treeUri,
            documentId = proposal.documentId,
            affectedCount = proposal.affectedCount
        )
    } else {
        repository.addToWhitelist(
            type = proposal.ruleType.name,
            identifier = proposal.identifier,
            title = proposal.title,
            displayPath = proposal.displayPath,
            treeUri = proposal.treeUri,
            documentId = proposal.documentId,
            affectedCount = proposal.affectedCount
        )
    }

    val listLabel = if (proposal.targetList == RuleListKind.BLACKLIST) "Blacklist" else "Whitelist"
    Toast.makeText(context, "Added \"${proposal.title}\" to $listLabel", Toast.LENGTH_SHORT).show()
}

@Composable
fun QuickRuleConflictHost(
    pendingConflict: Pair<QuickRuleProposal, List<RuleConflict>>?,
    repository: MediaRepository,
    coroutineScope: CoroutineScope,
    context: Context,
    onClear: () -> Unit
) {
    if (pendingConflict != null) {
        val (proposal, conflicts) = pendingConflict
        ConflictWarningDialog(
            title = proposal.title,
            targetList = proposal.targetList,
            conflicts = conflicts,
            onDismiss = onClear,
            onRemoveConflictsAndAdd = {
                coroutineScope.launch {
                    commitQuickRule(repository, proposal, conflicts, removeConflicts = true, context = context)
                    onClear()
                }
            },
            onAddAnyway = {
                coroutineScope.launch {
                    commitQuickRule(repository, proposal, conflicts, removeConflicts = false, context = context)
                    onClear()
                }
            }
        )
    }
}

@Composable
fun MultiSelectRuleActionBar(
    visible: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onBlacklistSelected: () -> Unit,
    onWhitelistSelected: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("multi_select_action_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel Selection")
                    }
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSelectAll) {
                        Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                    }
                    FilledTonalButton(
                        enabled = selectedCount > 0,
                        onClick = onBlacklistSelected,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("bulk_blacklist_btn")
                    ) {
                        Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Blacklist", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        enabled = selectedCount > 0,
                        onClick = onWhitelistSelected,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("bulk_whitelist_btn")
                    ) {
                        Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Whitelist", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
