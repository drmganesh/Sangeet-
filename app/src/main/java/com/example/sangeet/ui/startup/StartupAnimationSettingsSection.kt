package com.example.sangeet.ui.startup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartupAnimationSettingsSection(
    prefs: StartupAnimationPreferencesSnapshot,
    onStyleSelected: (StartupAnimationStyle) -> Unit,
    onSpeedSelected: (StartupAnimationSpeed) -> Unit,
    onSoundsToggled: (Boolean) -> Unit,
    onVibrationToggled: (Boolean) -> Unit,
    onPreviewClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    CassetteSectionCard(modifier = modifier.testTag("settings_startup_animation_section")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CassetteSectionHeader(
                title = "Startup Animation",
                subtitle = "Walkman cassette launch sequence & SANGEET reveal",
                badgeText = "BOOT"
            )

            // 1. Animation Style Selection (Full, Short, Logo Only, Disabled)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StartupAnimationStyle.entries.forEach { style ->
                    val selected = prefs.style == style
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                        border = BorderStroke(
                            width = if (selected) 1.2.dp else 0.7.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onStyleSelected(style) }
                            .testTag("startup_style_${style.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onStyleSelected(style) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = style.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            // 2. Animation Speed (Slow, Normal, Fast)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SlowMotionVideo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Animation Speed",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StartupAnimationSpeed.entries.forEach { speed ->
                        val selected = prefs.speed == speed
                        FilterChip(
                            selected = selected,
                            enabled = prefs.style != StartupAnimationStyle.DISABLED,
                            onClick = { onSpeedSelected(speed) },
                            label = { Text(speed.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("startup_speed_${speed.name.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            // 3. Mechanical Sound Effects (Disabled by default)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Mechanical Sound Effects",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Subtle offline latch, cassette & Play switch clicks (off by default)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = prefs.mechanicalSoundsEnabled,
                    onCheckedChange = onSoundsToggled,
                    modifier = Modifier.testTag("settings_startup_sounds_switch")
                )
            }

            // 4. Startup Vibration Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Vibration,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Startup Vibration",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Tactile pulse when cassette seats and Play engages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = prefs.vibrationEnabled,
                    onCheckedChange = onVibrationToggled,
                    modifier = Modifier.testTag("settings_startup_vibration_switch")
                )
            }

            // 5. Preview Startup Animation Action
            Button(
                onClick = onPreviewClicked,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_preview_startup_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Preview Startup Animation",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
