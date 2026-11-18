package com.example.sangeet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.repository.LyricsDisplayPreferencesSnapshot
import com.example.sangeet.data.repository.LyricsFontStyle
import com.example.sangeet.data.repository.LyricsHighlightPreset
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val prefs by settingsRepo.lyricsPreferences.collectAsState(initial = LyricsDisplayPreferencesSnapshot())

    val highlightColor = Color(prefs.highlightColorHex)
    val fontFamily = remember(prefs.fontStyle) {
        when (prefs.fontStyle) {
            LyricsFontStyle.SANS -> FontFamily.SansSerif
            LyricsFontStyle.SERIF -> FontFamily.Serif
            LyricsFontStyle.MONO -> FontFamily.Monospace
            LyricsFontStyle.DISPLAY -> FontFamily.Default
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_settings_screen"),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Lyrics Appearance",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Full-Screen Synced Lyrics Customization",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lyrics_settings_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                settingsRepo.resetLyricsSettingsToDefault()
                                Toast.makeText(context, "Lyrics settings reset to default", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("lyrics_settings_reset_btn")
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Preview Card
            item {
                CassetteSectionCard(highlighted = true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        highlightColor.copy(alpha = (1f - prefs.backgroundDimming) * 0.45f),
                                        Color.Black.copy(alpha = prefs.backgroundDimming)
                                    )
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(prefs.lineSpacingDp.dp)
                        ) {
                            CassetteHeaderBadge(text = "LIVE LYRICS PREVIEW")
                            Text(
                                text = "Previous tape line fading softly behind",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = fontFamily,
                                    fontSize = prefs.fontSizeSp.sp
                                ),
                                color = Color.White.copy(alpha = prefs.inactiveOpacity),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (prefs.wordByWordEnabled) {
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.ExtraBold)) {
                                            append("Golden analog reels ")
                                        }
                                        withStyle(SpanStyle(color = highlightColor.copy(alpha = 0.52f), fontWeight = FontWeight.SemiBold)) {
                                            append("spin in rhythm")
                                        }
                                    }
                                } else {
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.ExtraBold)) {
                                            append("Golden analog reels spin in rhythm")
                                        }
                                    }
                                },
                                fontFamily = fontFamily,
                                fontSize = prefs.fontSizeSp.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = prefs.currentLineScale
                                    scaleY = prefs.currentLineScale
                                }
                            )
                            Text(
                                text = "Upcoming verse waiting below",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = fontFamily,
                                    fontSize = prefs.fontSizeSp.sp
                                ),
                                color = Color.White.copy(alpha = prefs.inactiveOpacity),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Typography & Layout
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CassetteSectionHeader(title = "Typography & Spacing")

                        // Font Size
                        SliderSettingRow(
                            label = "Font Size",
                            valueText = "${prefs.fontSizeSp.roundToInt()} sp",
                            value = prefs.fontSizeSp,
                            valueRange = 14f..30f,
                            onValueChange = { coroutineScope.launch { settingsRepo.setLyricsFontSize(it) } }
                        )

                        // Font Style
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Font Style", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(LyricsFontStyle.entries) { style ->
                                    val selected = prefs.fontStyle == style
                                    FilterChip(
                                        selected = selected,
                                        onClick = { coroutineScope.launch { settingsRepo.setLyricsFontStyle(style) } },
                                        label = { Text(style.label) },
                                        modifier = Modifier.testTag("lyrics_font_style_${style.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        // Line Spacing
                        SliderSettingRow(
                            label = "Line Spacing",
                            valueText = "${prefs.lineSpacingDp.roundToInt()} dp",
                            value = prefs.lineSpacingDp,
                            valueRange = 8f..36f,
                            onValueChange = { coroutineScope.launch { settingsRepo.setLyricsLineSpacing(it) } }
                        )

                        // Current Line Scale
                        SliderSettingRow(
                            label = "Active Line Highlight Scale",
                            valueText = String.format("%.2fx", prefs.currentLineScale),
                            value = prefs.currentLineScale,
                            valueRange = 1.0f..1.30f,
                            onValueChange = { coroutineScope.launch { settingsRepo.setLyricsCurrentLineScale(it) } }
                        )
                    }
                }
            }

            // Colors & Backdrop
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CassetteSectionHeader(title = "Colors & Backdrop")

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Active Line Highlight Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(LyricsHighlightPreset.entries) { preset ->
                                    val isSelected = prefs.highlightColorHex == preset.argbHex
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clickable {
                                                coroutineScope.launch { settingsRepo.setLyricsHighlightColor(preset.argbHex) }
                                            }
                                            .padding(vertical = 4.dp)
                                            .testTag("lyrics_color_${preset.name.lowercase()}")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color(preset.argbHex))
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) Color.White else Color.Gray.copy(alpha = 0.4f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = preset.label,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = preset.label.substringBefore(' '),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) Color(preset.argbHex) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        SliderSettingRow(
                            label = "Inactive Lyric Opacity",
                            valueText = "${(prefs.inactiveOpacity * 100).roundToInt()}%",
                            value = prefs.inactiveOpacity,
                            valueRange = 0.15f..0.85f,
                            onValueChange = { coroutineScope.launch { settingsRepo.setLyricsInactiveOpacity(it) } }
                        )

                        SliderSettingRow(
                            label = "Artwork Background Blur",
                            valueText = "${prefs.artworkBlurRadius.roundToInt()} px",
                            value = prefs.artworkBlurRadius,
                            valueRange = 0f..30f,
                            onValueChange = { coroutineScope.launch { settingsRepo.setLyricsArtworkBlur(it) } }
                        )

                        SliderSettingRow(
                            label = "Background Dimming Overlay",
                            valueText = "${(prefs.backgroundDimming * 100).roundToInt()}%",
                            value = prefs.backgroundDimming,
                            valueRange = 0.30f..0.95f,
                            onValueChange = { coroutineScope.launch { settingsRepo.setLyricsBackgroundDimming(it) } }
                        )
                    }
                }
            }

            // Behavior & Motion
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CassetteSectionHeader(title = "Sync & Motion Behavior")

                        SwitchSettingRow(
                            title = "Automatic Smooth Scrolling",
                            subtitle = "Keep the active lyric centered while playing",
                            checked = prefs.autoScrollEnabled,
                            onCheckedChange = { coroutineScope.launch { settingsRepo.setLyricsAutoSync(it) } },
                            testTag = "lyrics_setting_autoscroll"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        SwitchSettingRow(
                            title = "Word-by-Word Highlighting",
                            subtitle = "Animate individual words when Enhanced .LRC tags exist",
                            checked = prefs.wordByWordEnabled,
                            onCheckedChange = { coroutineScope.launch { settingsRepo.setLyricsWordByWordEnabled(it) } },
                            testTag = "lyrics_setting_word_by_word"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        SwitchSettingRow(
                            title = "Reduced Lyrics Motion",
                            subtitle = "Minimize line scaling and smooth jump transitions",
                            checked = prefs.reducedMotion,
                            onCheckedChange = { coroutineScope.launch { settingsRepo.setLyricsReducedMotion(it) } },
                            testTag = "lyrics_setting_reduced_motion"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
