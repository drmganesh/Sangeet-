package com.example.sangeet.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.scanner.*
import com.example.sangeet.ui.components.CassetteHeaderBadge
import kotlin.math.roundToLong

private val GoldPrimary = Color(0xFFE8B429)
private val GoldBright = Color(0xFFFFD666)
private val SurfaceDark = Color(0xFF101116)
private val SurfaceElevated = Color(0xFF171920)

/**
 * Per-song timing offset adjustment sheet (-10,000 ms .. +10,000 ms).
 */
@Composable
fun LyricsTimingAdjustmentSheet(
    track: AudioTrack,
    currentOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clamped = LyricsSyncEngine.clampOffset(currentOffsetMs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CassetteHeaderBadge(text = "LYRICS TIMING CALIBRATION")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Timing Controls")
            }
        }

        // Active status banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = LyricsSyncEngine.formatOffsetDescription(clamped),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = GoldBright,
                    modifier = Modifier.testTag("lyrics_timing_readout")
                )
                Text(
                    text = "If lyrics appear late, tap Advance (-). If lyrics appear early, tap Delay (+). Saved per song.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB3B8C4)
                )
            }
        }

        // Fine & Medium step buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimingStepButton(
                label = "-500ms",
                onClick = { onOffsetChange(LyricsSyncEngine.clampOffset(clamped - 500L)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("lyrics_offset_minus_500")
            )
            TimingStepButton(
                label = "-250ms",
                onClick = { onOffsetChange(LyricsSyncEngine.clampOffset(clamped - 250L)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("lyrics_offset_minus_250")
            )
            TimingStepButton(
                label = "-50ms",
                onClick = { onOffsetChange(LyricsSyncEngine.clampOffset(clamped - 50L)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("lyrics_offset_minus_50")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimingStepButton(
                label = "+50ms",
                onClick = { onOffsetChange(LyricsSyncEngine.clampOffset(clamped + 50L)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("lyrics_offset_plus_50")
            )
            TimingStepButton(
                label = "+250ms",
                onClick = { onOffsetChange(LyricsSyncEngine.clampOffset(clamped + 250L)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("lyrics_offset_plus_250")
            )
            TimingStepButton(
                label = "+500ms",
                onClick = { onOffsetChange(LyricsSyncEngine.clampOffset(clamped + 500L)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("lyrics_offset_plus_500")
            )
        }

        // Continuous slider (-10s to +10s)
        Column {
            Slider(
                value = clamped.toFloat(),
                onValueChange = { value ->
                    val snapped = ((value / 50f).roundToLong() * 50L)
                        .coerceIn(LyricsSyncEngine.MIN_OFFSET_MS, LyricsSyncEngine.MAX_OFFSET_MS)
                    onOffsetChange(snapped)
                },
                valueRange = LyricsSyncEngine.MIN_OFFSET_MS.toFloat()..LyricsSyncEngine.MAX_OFFSET_MS.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = GoldBright,
                    activeTrackColor = GoldPrimary,
                    inactiveTrackColor = Color(0xFF2B2E38)
                ),
                modifier = Modifier.testTag("lyrics_offset_slider")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-10.0s (Advance)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                Text("0 ms", style = MaterialTheme.typography.labelSmall, color = GoldPrimary)
                Text("+10.0s (Delay)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onResetOffset,
                enabled = clamped != 0L,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("lyrics_offset_reset_btn"),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset (0 ms)")
            }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TimingStepButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceDark,
            contentColor = GoldBright
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            maxLines = 1
        )
    }
}

/**
 * 100% Offline manual lyrics search & local file picker with preview before applying.
 */
@Composable
fun LyricsOfflineSearchSheet(
    track: AudioTrack,
    searchTitle: String,
    searchArtist: String,
    isSearching: Boolean,
    candidates: List<LocalLyricsCandidate>,
    onSearchQueryChange: (String, String) -> Unit,
    onRunLocalSearch: () -> Unit,
    onPickDocument: () -> Unit,
    onApplyCandidate: (LocalLyricsCandidate) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var previewCandidate by remember(candidates) { mutableStateOf<LocalLyricsCandidate?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CassetteHeaderBadge(text = "100% OFFLINE LYRICS SEARCH")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (previewCandidate == null) "Find Local .LRC or Embedded Lyrics" else "Preview Matched Lyrics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Search")
            }
        }

        if (previewCandidate != null) {
            val selected = previewCandidate!!
            val previewDoc = remember(selected.rawContent) { LrcParser.parseDocument(selected.rawContent) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selected.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = GoldBright)
                        Text("${selected.subtitle} • ${selected.syncType.badgeLabel}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
                    }
                    CassetteHeaderBadge(text = "${previewDoc.lines.size} LINES")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF232630), RoundedCornerShape(14.dp)),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(previewDoc.lines) { _, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (previewDoc.isSynchronized) {
                            Text(
                                text = "[${LrcParser.formatTimestamp(line.timestampMs)}]",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = GoldPrimary
                            )
                        }
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF3F4F6)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { previewCandidate = null },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Back to Results")
                }
                Button(
                    onClick = { onApplyCandidate(selected) },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("lyrics_apply_candidate_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply Lyrics", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            OutlinedTextField(
                value = searchTitle,
                onValueChange = { onSearchQueryChange(it, searchArtist) },
                label = { Text("Song Title or Filename") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = GoldPrimary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lyrics_search_title_input")
            )

            OutlinedTextField(
                value = searchArtist,
                onValueChange = { onSearchQueryChange(searchTitle, it) },
                label = { Text("Artist (Optional)") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = GoldPrimary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lyrics_search_artist_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunLocalSearch,
                    enabled = !isSearching,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("lyrics_run_local_search_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSearching) "Scanning..." else "Scan Device", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onPickDocument,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("lyrics_pick_file_btn"),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = GoldBright)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick .LRC/.TXT", color = GoldBright)
                }
            }

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GoldPrimary)
                }
            } else if (candidates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceDark)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = GoldPrimary.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
                        Text(
                            text = "No matching local .lrc or embedded files found for \"${searchTitle.ifBlank { track.title }}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD1D5DB)
                        )
                        Text(
                            text = "Tap 'Pick .LRC/.TXT' above to browse device folders safely offline without altering the original audio file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
            } else {
                Text(
                    text = "${candidates.size} LOCAL MATCH(ES) • TAP TO PREVIEW",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = GoldPrimary
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(candidates, key = { _, c -> c.id }) { idx, candidate ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, if (idx == 0) GoldPrimary.copy(alpha = 0.6f) else Color(0xFF282B36)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { previewCandidate = candidate }
                                .testTag("lyrics_search_candidate_$idx")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (candidate.syncType != LyricsSyncType.PLAIN_TEXT) Icons.Rounded.Timer else Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = GoldBright
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFF9FAFB),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${candidate.subtitle} • ${candidate.sourceType.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9CA3AF),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                CassetteHeaderBadge(text = candidate.syncType.badgeLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class EditorTab {
    LINES_AND_TEXT,
    MANUAL_TAP_SYNC
}

/**
 * Full offline Lyrics Editor + Tap-to-Sync Studio + .LRC Exporter.
 */
@Composable
fun LyricsEditorAndSyncSheet(
    track: AudioTrack,
    initialRawLyrics: String,
    currentOffsetMs: Long,
    isPlaying: Boolean,
    getRealtimePositionMs: () -> Long,
    onTogglePlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSaveLyrics: (String) -> Unit,
    onExportLrc: (String) -> Unit,
    onResetToOriginalSource: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(EditorTab.LINES_AND_TEXT) }
    var rawText by remember(track.id, initialRawLyrics) { mutableStateOf(initialRawLyrics) }

    // Line-by-line editable state kept synced when switching to Studio / Line Mode
    var editableLines by remember(track.id, initialRawLyrics) {
        mutableStateOf(
            LrcParser.parseDocument(initialRawLyrics).lines.ifEmpty {
                listOf(EnhancedLyricsLine(0L, 4000L, "", emptyList()))
            }
        )
    }
    var useRawTextEditor by remember { mutableStateOf(false) }

    // Manual Tap-to-Sync active index & live readout
    var syncCursorIndex by remember { mutableIntStateOf(0) }
    var liveClockMs by remember { mutableLongStateOf(getRealtimePositionMs()) }
    val studioListState = rememberLazyListState()

    LaunchedEffect(activeTab, isPlaying) {
        if (activeTab == EditorTab.MANUAL_TAP_SYNC) {
            while (true) {
                liveClockMs = getRealtimePositionMs()
                kotlinx.coroutines.delay(50L)
            }
        }
    }

    LaunchedEffect(syncCursorIndex, activeTab) {
        if (activeTab == EditorTab.MANUAL_TAP_SYNC && syncCursorIndex in editableLines.indices) {
            studioListState.animateScrollToItem((syncCursorIndex - 2).coerceAtLeast(0))
        }
    }

    fun buildCombinedLrcFromLines(): String {
        return LrcParser.exportToLrc(
            lines = editableLines,
            title = track.title,
            artist = track.artist,
            album = track.album,
            offsetMs = currentOffsetMs
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CassetteHeaderBadge(text = "LYRICS EDITOR & SYNC STUDIO")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onResetToOriginalSource != null) {
                IconButton(
                    onClick = onResetToOriginalSource,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = "Reset to Embedded/Folder Lyrics", tint = Color(0xFFFCA5A5))
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Editor")
            }
        }

        // Mode Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .border(1.dp, Color(0xFF242731), RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isTextTab = activeTab == EditorTab.LINES_AND_TEXT
            Surface(
                onClick = { activeTab = EditorTab.LINES_AND_TEXT },
                shape = RoundedCornerShape(10.dp),
                color = if (isTextTab) GoldPrimary else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("editor_tab_lines")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Edit Lines & Text",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isTextTab) Color.Black else Color(0xFFD1D5DB)
                    )
                }
            }
            Surface(
                onClick = {
                    if (useRawTextEditor && rawText.isNotBlank()) {
                        val parsed = LrcParser.parseDocument(rawText).lines
                        if (parsed.isNotEmpty()) editableLines = parsed
                    }
                    syncCursorIndex = 0
                    activeTab = EditorTab.MANUAL_TAP_SYNC
                },
                shape = RoundedCornerShape(10.dp),
                color = if (!isTextTab) GoldPrimary else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("editor_tab_tap_sync")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Tap-to-Sync Studio",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (!isTextTab) Color.Black else Color(0xFFD1D5DB)
                    )
                }
            }
        }

        if (activeTab == EditorTab.LINES_AND_TEXT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (useRawTextEditor) {
                            editableLines = LrcParser.parseDocument(rawText).lines.ifEmpty {
                                listOf(EnhancedLyricsLine(0L, 4000L, "", emptyList()))
                            }
                            useRawTextEditor = false
                        } else {
                            rawText = buildCombinedLrcFromLines()
                            useRawTextEditor = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (useRawTextEditor) Icons.Rounded.ViewList else Icons.Rounded.Code,
                        contentDescription = null,
                        tint = GoldBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (useRawTextEditor) "Switch to Line Editor" else "Switch to Raw .LRC",
                        color = GoldBright
                    )
                }

                if (!useRawTextEditor) {
                    FilledTonalButton(
                        onClick = {
                            val nextTs = (editableLines.lastOrNull()?.timestampMs ?: -3000L) + 3000L
                            editableLines = editableLines + EnhancedLyricsLine(nextTs, nextTs + 3000L, "", emptyList())
                        },
                        modifier = Modifier.testTag("editor_add_line_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Line")
                    }
                }
            }

            if (useRawTextEditor) {
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("LRC or Plain Text Lyrics") },
                    placeholder = { Text("[00:12.50]Your lyric line\n[00:16.00]Next line...") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("editor_raw_lrc_field")
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(editableLines) { idx, line ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, Color(0xFF282B36)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Quick -/+ 0.5s timestamp nudge per line
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = LrcParser.formatTimestamp(line.timestampMs),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = GoldBright
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = {
                                                editableLines = editableLines.toMutableList().also {
                                                    it[idx] = line.copy(timestampMs = (line.timestampMs - 250L).coerceAtLeast(0L))
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Rounded.Remove, contentDescription = "-250ms", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                editableLines = editableLines.toMutableList().also {
                                                    it[idx] = line.copy(timestampMs = line.timestampMs + 250L)
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = "+250ms", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = line.text,
                                    onValueChange = { updated ->
                                        editableLines = editableLines.toMutableList().also {
                                            it[idx] = line.copy(text = updated, words = emptyList())
                                        }
                                    },
                                    placeholder = { Text("Lyric text...") },
                                    singleLine = false,
                                    maxLines = 3,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        if (editableLines.size > 1) {
                                            editableLines = editableLines.toMutableList().also { it.removeAt(idx) }
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Line", tint = Color(0xFFFCA5A5))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Manual Tap-to-Sync Studio
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CLOCK: [${LrcParser.formatTimestamp(liveClockMs)}]",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = GoldBright
                            )
                            Text(
                                text = "Play song & tap Stamp as each line begins",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onSeekRelative(-3000L) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Replay5, contentDescription = "Back 3s", tint = Color(0xFFE5E7EB))
                            }
                            FilledIconButton(
                                onClick = onTogglePlayPause,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play"
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (syncCursorIndex in editableLines.indices) {
                                val stampMs = getRealtimePositionMs()
                                editableLines = editableLines.toMutableList().also { list ->
                                    val cur = list[syncCursorIndex]
                                    list[syncCursorIndex] = cur.copy(timestampMs = stampMs, words = emptyList())
                                }
                                if (syncCursorIndex < editableLines.lastIndex) {
                                    syncCursorIndex++
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("lyrics_stamp_sync_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val linePreview = editableLines.getOrNull(syncCursorIndex)?.text?.take(26).orEmpty()
                        Text(
                            text = if (linePreview.isNotBlank()) "STAMP LINE ${syncCursorIndex + 1}: \"$linePreview\"" else "STAMP LINE ${syncCursorIndex + 1}",
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            LazyColumn(
                state = studioListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(editableLines) { idx, line ->
                    val isSelected = idx == syncCursorIndex
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) GoldPrimary.copy(alpha = 0.16f) else SurfaceDark,
                        border = BorderStroke(
                            width = if (isSelected) 1.4.dp else 0.8.dp,
                            color = if (isSelected) GoldBright else Color(0xFF262934)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { syncCursorIndex = idx }
                            .testTag("studio_line_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "[${LrcParser.formatTimestamp(line.timestampMs)}]",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isSelected) GoldBright else GoldPrimary
                            )
                            Text(
                                text = line.text.ifBlank { "(Instrumental / Empty)" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                CassetteHeaderBadge(text = "NEXT")
                            }
                        }
                    }
                }
            }
        }

        // Bottom Export & Save Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val out = if (activeTab == EditorTab.LINES_AND_TEXT && useRawTextEditor) {
                        rawText
                    } else {
                        buildCombinedLrcFromLines()
                    }
                    onExportLrc(out)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("lyrics_export_lrc_btn"),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.65f))
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = GoldBright, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export .LRC", color = GoldBright)
            }

            Button(
                onClick = {
                    val out = if (activeTab == EditorTab.LINES_AND_TEXT && useRawTextEditor) {
                        rawText
                    } else {
                        buildCombinedLrcFromLines()
                    }
                    onSaveLyrics(out)
                },
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("lyrics_save_local_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Locally", fontWeight = FontWeight.Bold)
            }
        }
    }
}
