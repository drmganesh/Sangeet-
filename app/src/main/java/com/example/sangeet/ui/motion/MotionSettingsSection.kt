package com.example.sangeet.ui.motion

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MotionSettingsSection(
    prefs: MotionPreferencesSnapshot,
    onQualitySelected: (MotionQuality) -> Unit,
    onSpeedSelected: (MotionSpeed) -> Unit,
    onHorizontalSwipesChanged: (Boolean) -> Unit,
    onVerticalSwipesChanged: (Boolean) -> Unit,
    onPlayerGesturesChanged: (Boolean) -> Unit,
    onCassetteTransitionsChanged: (Boolean) -> Unit,
    onSwipeToDismissChanged: (Boolean) -> Unit,
    onReducedMotionChanged: (Boolean) -> Unit,
    onDisableDecorativeChanged: (Boolean) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    CassetteSectionCard(modifier = modifier.testTag("settings_motion_section")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CassetteSectionHeader(
                title = "Motion & Gestures",
                subtitle = "Global swipe physics, screen transitions & accessibility",
                badgeText = prefs.quality.label.uppercase()
            )

            Text(
                text = "Animation Quality",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MotionQuality.entries.forEach { quality ->
                    val selected = prefs.quality == quality
                    FilterChip(
                        selected = selected,
                        onClick = { onQualitySelected(quality) },
                        label = { Text(quality.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("motion_quality_${quality.name.lowercase()}")
                    )
                }
            }
            Text(
                text = prefs.quality.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            Text(
                text = "Animation Speed",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MotionSpeed.entries.forEach { speed ->
                    val selected = prefs.speed == speed
                    FilterChip(
                        selected = selected,
                        onClick = { onSpeedSelected(speed) },
                        label = { Text(speed.label) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("motion_speed_${speed.name.lowercase()}")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            Text(
                text = "Gesture Controls",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            MotionSwitchRow(
                icon = Icons.Rounded.Swipe,
                title = "Enable Horizontal Swipes",
                subtitle = "Swipe tabs in Library, Spotlight cassettes & tracks",
                checked = prefs.horizontalSwipesEnabled,
                onCheckedChange = onHorizontalSwipesChanged,
                tag = "motion_toggle_horizontal_swipes"
            )
            MotionSwitchRow(
                icon = Icons.Rounded.SwipeVertical,
                title = "Enable Vertical Swipes",
                subtitle = "Swipe up or down on Mini-Player & Walkman sheets",
                checked = prefs.verticalSwipesEnabled,
                onCheckedChange = onVerticalSwipesChanged,
                tag = "motion_toggle_vertical_swipes"
            )
            MotionSwitchRow(
                icon = Icons.Rounded.TouchApp,
                title = "Enable Player Gestures",
                subtitle = "Swipe left/right on cassette deck for Next / Previous",
                checked = prefs.playerGesturesEnabled,
                onCheckedChange = onPlayerGesturesChanged,
                tag = "motion_toggle_player_gestures"
            )
            MotionSwitchRow(
                icon = Icons.Rounded.Album,
                title = "Enable Cassette Transitions",
                subtitle = "Flip, slide & spring artwork changes across decks",
                checked = prefs.cassetteTransitionsEnabled,
                onCheckedChange = onCassetteTransitionsChanged,
                tag = "motion_toggle_cassette_transitions"
            )
            MotionSwitchRow(
                icon = Icons.Rounded.SwipeLeft,
                title = "Enable Swipe-to-Dismiss",
                subtitle = "Swipe queue or playlist rows left to remove with Undo",
                checked = prefs.swipeToDismissEnabled,
                onCheckedChange = onSwipeToDismissChanged,
                tag = "motion_toggle_swipe_dismiss"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            Text(
                text = "Accessibility",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            MotionSwitchRow(
                icon = Icons.Rounded.AccessibilityNew,
                title = "Reduced Motion",
                subtitle = "Minimize screen transitions & replace slides with soft fades",
                checked = prefs.reducedMotion,
                onCheckedChange = onReducedMotionChanged,
                tag = "motion_toggle_reduced_motion"
            )
            MotionSwitchRow(
                icon = Icons.Rounded.Animation,
                title = "Disable Decorative Animations",
                subtitle = "Pause Ken Burns drift, ambient sheen & card breathing",
                checked = prefs.disableDecorativeAnimations,
                onCheckedChange = onDisableDecorativeChanged,
                tag = "motion_toggle_disable_decorative"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.testTag("motion_reset_defaults_btn")
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset Motion Defaults")
                }
            }
        }
    }
}

@Composable
private fun MotionSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black),
            modifier = Modifier.testTag(tag)
        )
    }
}
