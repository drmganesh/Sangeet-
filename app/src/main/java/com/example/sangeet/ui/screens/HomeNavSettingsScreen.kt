package com.example.sangeet.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import com.example.sangeet.ui.components.CassetteTopBar
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeNavSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    // Collect persisted settings
    val startScreen by settingsRepo.startScreen.collectAsState(initial = "HOME")
    val startMode by settingsRepo.startMode.collectAsState(initial = "MUSIC")
    val showGreeting by settingsRepo.showGreeting.collectAsState(initial = true)
    val spotlightHome by settingsRepo.spotlightHome.collectAsState(initial = true)
    val nonExpressiveNavBar by settingsRepo.nonExpressiveNavBar.collectAsState(initial = false)
    val tabNamesMode by settingsRepo.tabNamesMode.collectAsState(initial = "AUTO")
    val hideNavWhileScrolling by settingsRepo.hideNavWhileScrolling.collectAsState(initial = false)
    val rotateWithDevice by settingsRepo.rotateWithDevice.collectAsState(initial = false)

    var showResetDialog by remember { mutableStateOf(false) }

    val currentGreetingPreview = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_nav_settings_screen"),
        topBar = {
            CassetteTopBar(
                title = "Home & Navigation",
                subtitle = "Customize layout, navigation bar, and launch behavior",
                badgeText = "DECK UI",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.testTag("home_nav_reset_btn")
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "Reset to Defaults",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ==========================================
            // 1. WHEN SANGEET OPENS
            // ==========================================
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CassetteSectionHeader(title = "When Sangeet Opens")

                        // Start Screen
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Start Screen",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Choose which section opens when launching SANGEET",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("LAST", "Last", Icons.Rounded.History),
                                    Triple("HOME", "Home", Icons.Rounded.Home),
                                    Triple("SEARCH", "Search", Icons.Rounded.Search),
                                    Triple("LIBRARY", "Library", Icons.Rounded.LibraryMusic)
                                ).forEach { (id, label, icon) ->
                                    val isSelected = startScreen == id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            coroutineScope.launch {
                                                settingsRepo.setStartScreen(id)
                                            }
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("start_screen_$id")
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Start Mode
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Start Mode",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Restore last-used mode or open directly in Music or Video",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("LAST", "Last Mode", Icons.Rounded.Restore),
                                    Triple("MUSIC", "Music", Icons.Rounded.MusicNote),
                                    Triple("VIDEO", "Video", Icons.Rounded.Videocam)
                                ).forEach { (id, label, icon) ->
                                    val isSelected = startMode == id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            coroutineScope.launch {
                                                settingsRepo.setStartMode(id)
                                            }
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("start_mode_$id")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. HOME TOP BAR
            // ==========================================
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CassetteSectionHeader(title = "Home Top Bar")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Greeting",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Show friendly time-sensitive greeting (Morning / Afternoon / Evening)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showGreeting,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        settingsRepo.setShowGreeting(it)
                                    }
                                },
                                modifier = Modifier.testTag("greeting_toggle")
                            )
                        }

                        AnimatedVisibility(visible = showGreeting) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.WbSunny,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Active Preview: \"$currentGreetingPreview, SANGEET Listener\"",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. HOME LAYOUT
            // ==========================================
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CassetteSectionHeader(title = "Home Layout Style")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Spotlight Home",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (spotlightHome) "Rich hero section with horizontal discovery carousels" else "Clean, compact traditional lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = spotlightHome,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        settingsRepo.setSpotlightHome(it)
                                    }
                                },
                                modifier = Modifier.testTag("spotlight_home_toggle")
                            )
                        }

                        // Selectable Visual Previews for Home Layout
                        Text(
                            text = "Layout Preview (Tap to select)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Classic Preview Card
                            LayoutPreviewCard(
                                title = "Classic",
                                subtitle = "Compact Lists",
                                isSelected = !spotlightHome,
                                onClick = {
                                    coroutineScope.launch { settingsRepo.setSpotlightHome(false) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_classic_home")
                            ) {
                                // Mini mockup for classic
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                    )
                                    repeat(4) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                            )
                                        }
                                    }
                                }
                            }

                            // Spotlight Preview Card
                            LayoutPreviewCard(
                                title = "Spotlight",
                                subtitle = "Hero & Carousels",
                                isSelected = spotlightHome,
                                onClick = {
                                    coroutineScope.launch { settingsRepo.setSpotlightHome(true) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_spotlight_home")
                            ) {
                                // Mini mockup for spotlight
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.secondary
                                                    )
                                                )
                                            )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        repeat(3) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Navigation Bar Style Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Non-expressive navigation bar",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (nonExpressiveNavBar) "Standard docked rectangular bar" else "Expressive floating pill bar with smooth animations",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = nonExpressiveNavBar,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        settingsRepo.setNonExpressiveNavBar(it)
                                    }
                                },
                                modifier = Modifier.testTag("non_expressive_nav_toggle")
                            )
                        }

                        // Selectable Visual Previews for Navigation Bar
                        Text(
                            text = "Navigation Style Preview (Tap to select)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Floating Preview Card
                            LayoutPreviewCard(
                                title = "Floating",
                                subtitle = "Expressive Pill",
                                isSelected = !nonExpressiveNavBar,
                                onClick = {
                                    coroutineScope.launch { settingsRepo.setNonExpressiveNavBar(false) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_floating_nav")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            repeat(4) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Standard Preview Card
                            LayoutPreviewCard(
                                title = "Standard",
                                subtitle = "Docked M3",
                                isSelected = nonExpressiveNavBar,
                                onClick = {
                                    coroutineScope.launch { settingsRepo.setNonExpressiveNavBar(true) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_standard_nav")
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            repeat(4) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
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

            // ==========================================
            // 4. NAVIGATION BAR SETTINGS
            // ==========================================
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CassetteSectionHeader(title = "Navigation Bar Behavior")

                        // Tab Names Mode
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Tab Names",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Control text labels displayed beside or beneath icons",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Pair("AUTO", "Auto"),
                                    Pair("ALWAYS", "Always"),
                                    Pair("OPEN_ONLY", "Open only"),
                                    Pair("NEVER", "Never")
                                ).forEach { (mode, label) ->
                                    val isSelected = tabNamesMode == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            coroutineScope.launch {
                                                settingsRepo.setTabNamesMode(mode)
                                            }
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tab_names_$mode")
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Hide While Scrolling
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hide While Scrolling",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Automatically slide bottom bar down when scrolling down and restore on scroll up",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = hideNavWhileScrolling,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        settingsRepo.setHideNavWhileScrolling(it)
                                    }
                                },
                                modifier = Modifier.testTag("hide_while_scrolling_toggle")
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 5. DISPLAY SETTINGS
            // ==========================================
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CassetteSectionHeader(title = "Display & Orientation")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Rotate With Device",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Allow app interface to dynamically adapt between portrait and landscape orientations",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rotateWithDevice,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        settingsRepo.setRotateWithDevice(it)
                                    }
                                },
                                modifier = Modifier.testTag("rotate_with_device_toggle")
                            )
                        }
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Home & Navigation Settings?") },
            text = {
                Text("This will restore default Home start screen, Music mode, time-sensitive greeting, Spotlight layout, and expressive floating navigation bar.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            settingsRepo.resetHomeNavSettingsToDefault()
                            showResetDialog = false
                            Toast.makeText(context, "Home & Navigation settings reset to defaults", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LayoutPreviewCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = if (isSelected) 6.dp else 2.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                content()
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
