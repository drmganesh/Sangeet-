package com.example.sangeet.ui.screens.player

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.scanner.LyricsManager
import kotlinx.coroutines.launch

@Composable
fun LyricsSheetContent(
    track: AudioTrack,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lyricsManager = remember { LyricsManager(context) }
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    val persistedFontSize by settingsRepo.lyricsFontSize.collectAsState(initial = 18f)

    var rawLyricsText by remember { mutableStateOf("") }
    var timingOffsetMs by remember { mutableLongStateOf(0L) }
    var isLoaded by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    // Resolve lyrics from DB/embedded/sibling files
    LaunchedEffect(track.id) {
        val (lyrics, offset) = lyricsManager.resolveLyrics(track)
        rawLyricsText = lyrics
        timingOffsetMs = offset
        isLoaded = true
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val content = lyricsManager.readLyricsFromUri(uri)
                if (content.isNotBlank()) {
                    rawLyricsText = content
                    lyricsManager.saveCustomLyrics(track.id, content, timingOffsetMs)
                    Toast.makeText(context, "Lyrics file loaded", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not read lyrics file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val effectivePosition = currentPositionMs + timingOffsetMs
    val lyricsLines = remember(rawLyricsText, timingOffsetMs) {
        lyricsManager.parseLyrics(rawLyricsText, timingOffsetMs)
    }

    val currentLineIndex = remember(lyricsLines, effectivePosition) {
        val idx = lyricsLines.indexOfLast { it.timestampMs <= effectivePosition }
        if (idx == -1) 0 else idx
    }

    LaunchedEffect(currentLineIndex) {
        if (lyricsLines.isNotEmpty() && currentLineIndex in lyricsLines.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem((currentLineIndex - 2).coerceAtLeast(0))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OFFLINE LYRICS & SYNC",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    editText = rawLyricsText
                    showEditDialog = true
                }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Lyrics", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { filePicker.launch("*/*") }) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = "Import .LRC File", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
        }

        // Timing offset adjustment controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = {
                    timingOffsetMs -= 500L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, timingOffsetMs) }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Text("-0.5s", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = {
                    timingOffsetMs -= 100L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, timingOffsetMs) }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Text("-0.1s", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Sync: ${if (timingOffsetMs >= 0) "+" else ""}${timingOffsetMs / 1000f}s",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = {
                    timingOffsetMs += 100L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, timingOffsetMs) }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Text("+0.1s", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = {
                    timingOffsetMs += 500L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, timingOffsetMs) }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Text("+0.5s", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (lyricsLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.MusicOff,
                        contentDescription = "No lyrics",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Offline Lyrics Found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Place a matching .lrc or .txt file in the folder, or tap the upload icon above to select a file from device storage.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick .LRC File")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 32.dp)
            ) {
                itemsIndexed(lyricsLines) { index, line ->
                    val isCurrent = index == currentLineIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        label = "lyricColor"
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onSeek(line.timestampMs) },
                        color = Color.Transparent
                    ) {
                        Text(
                            text = line.text,
                            style = if (isCurrent) {
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (persistedFontSize + 4).sp
                                )
                            } else {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = persistedFontSize.sp
                                )
                            },
                            textAlign = TextAlign.Center,
                            color = textColor
                        )
                    }
                }
            }
        }
    }

    // Edit Lyrics Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Offline Lyrics") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("LRC or Plain Text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    placeholder = { Text("[00:01.00]Song lyrics line here...") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rawLyricsText = editText
                        coroutineScope.launch {
                            lyricsManager.saveCustomLyrics(track.id, editText, timingOffsetMs)
                        }
                        showEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun QueueSheetContent(
    queue: List<AudioTrack>,
    currentTrack: AudioTrack?,
    onSelectTrack: (AudioTrack) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PLAYING QUEUE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${queue.size} tracks in queue",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(queue) { index, track ->
                    val isPlayingThis = track.id == currentTrack?.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectTrack(track) },
                        color = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPlayingThis) {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    contentDescription = "Current",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRemoveTrack(index) }) {
                                Icon(
                                    Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
