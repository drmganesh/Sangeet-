package com.example.sangeet.ui.screens.player

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.scanner.LyricsManager
import com.example.sangeet.ui.components.CassetteEmptyStateCard
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.components.MiniCassetteArtwork
import com.example.sangeet.ui.components.rememberVisibleCassetteReelAngle
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
                CassetteHeaderBadge(text = "OFFLINE LYRICS & SYNC")
                Spacer(Modifier.height(4.dp))
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
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = {
                    timingOffsetMs -= 500L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, timingOffsetMs) }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Text("-0.5s", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = {
                    timingOffsetMs -= 100L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, timingOffsetMs) }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Text("-0.1s", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(10.dp))
            CassetteHeaderBadge(
                text = "SYNC ${if (timingOffsetMs >= 0) "+" else ""}${timingOffsetMs / 1000f}s"
            )
            Spacer(modifier = Modifier.width(10.dp))
            FilledTonalIconButton(
                onClick = {
                    timingOffsetMs += 100L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, timingOffsetMs) }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Text("+0.1s", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = {
                    timingOffsetMs += 500L
                    coroutineScope.launch { lyricsManager.updateLyricsOffset(track.id, timingOffsetMs) }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Text("+0.5s", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (lyricsLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CassetteEmptyStateCard(
                    icon = Icons.Rounded.MusicOff,
                    title = "No Offline Lyrics Found",
                    subtitle = "Place a matching .lrc or .txt file in the folder, or tap below to pick a local lyrics file.",
                    action = {
                        Button(onClick = { filePicker.launch("*/*") }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick .LRC File")
                        }
                    }
                )
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
    isPlaying: Boolean = false,
    onSelectTrack: (AudioTrack) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> },
    onMoveToNext: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val itemStepPx = with(density) { 68.dp.toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val currentQueueIdx = remember(queue, currentTrack?.id) {
        queue.indexOfFirst { it.id == currentTrack?.id }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("queue_sheet_content")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CassetteHeaderBadge(text = "TAPE QUEUE")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${queue.size} ${if (queue.size == 1) "cassette" else "cassettes"} in deck",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (queue.size > 1) {
                TextButton(
                    onClick = onClearQueue,
                    modifier = Modifier.testTag("queue_clear_btn")
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Queue")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CassetteEmptyStateCard(
                    icon = Icons.Rounded.QueueMusic,
                    title = "Deck Queue Is Empty",
                    subtitle = "Select any cassette from your library or search to start listening."
                )
            }
        } else {
            // Highlighted Currently Playing Cassette Deck Card
            if (currentTrack != null) {
                val activeReelAngle by rememberVisibleCassetteReelAngle(
                    isActivelyPlayingAndVisible = isPlaying,
                    seedAngleDegrees = ((currentTrack.id * 37L) % 360L).toFloat()
                )
                Text(
                    text = "CURRENTLY PLAYING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queue_now_playing_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniCassetteArtwork(
                            track = currentTrack,
                            isPlaying = isPlaying,
                            reelAngleDegrees = activeReelAngle,
                            modifier = Modifier.height(42.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentTrack.artist} • ${currentTrack.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "TAPE ORDER (DRAG HANDLE TO REORDER)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(queue, key = { index, t -> "${t.id}_$index" }) { index, track ->
                    val isPlayingThis = index == currentQueueIdx || (currentQueueIdx == -1 && track.id == currentTrack?.id)
                    val isDragging = draggingIndex == index
                    val spin = isPlayingThis && isPlaying
                    val reelAngle by rememberVisibleCassetteReelAngle(
                        isActivelyPlayingAndVisible = spin,
                        seedAngleDegrees = ((track.id * 43L) % 360L).toFloat()
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 2f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                scaleX = if (isDragging) 1.02f else 1f
                                scaleY = if (isDragging) 1.02f else 1f
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTrack(track) },
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isDragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            isPlayingThis -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                        border = BorderStroke(
                            width = if (isPlayingThis || isDragging) 1.2.dp else 0.8.dp,
                            color = if (isPlayingThis || isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Drag handle for live queue reordering
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .pointerInput(queue.size, index) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                val activeIdx = draggingIndex ?: return@detectVerticalDragGestures
                                                dragOffsetY += dragAmount
                                                if (dragOffsetY > itemStepPx && activeIdx < queue.lastIndex) {
                                                    onMoveTrack(activeIdx, activeIdx + 1)
                                                    draggingIndex = activeIdx + 1
                                                    dragOffsetY -= itemStepPx
                                                } else if (dragOffsetY < -itemStepPx && activeIdx > 0) {
                                                    onMoveTrack(activeIdx, activeIdx - 1)
                                                    draggingIndex = activeIdx - 1
                                                    dragOffsetY += itemStepPx
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Reorder Track",
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            MiniCassetteArtwork(
                                track = track,
                                isPlaying = spin,
                                reelAngleDegrees = reelAngle,
                                modifier = Modifier.height(36.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold
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

                            if (!isPlayingThis && currentQueueIdx >= 0 && index != currentQueueIdx + 1) {
                                IconButton(
                                    onClick = { onMoveToNext(index) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.VerticalAlignTop,
                                        contentDescription = "Play Next",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onRemoveTrack(index) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Remove from Queue",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
