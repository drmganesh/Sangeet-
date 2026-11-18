package com.example.sangeet.ui.screens

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.R
import com.example.sangeet.ui.components.CassetteHeaderBadge
import com.example.sangeet.ui.components.CassetteSectionCard
import com.example.sangeet.ui.components.CassetteSectionHeader
import com.example.sangeet.ui.motion.LocalSangeetMotion

private val GoldAccent = Color(0xFFE8B429)
private val GoldBright = Color(0xFFFFD666)
private val ObsidianCardTop = Color(0xFF14151B)
private val ObsidianCardBottom = Color(0xFF0A0B0F)

data class AppMetadataInfo(
    val appName: String,
    val versionName: String,
    val versionCode: Long
)

fun readActualAppMetadata(context: Context): AppMetadataInfo {
    val fallbackName = context.getString(R.string.app_name)
    return try {
        val pkgManager = context.packageManager
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pkgManager.getPackageInfo(context.packageName, 0)
        }
        val verName = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
        val verCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        AppMetadataInfo(
            appName = fallbackName,
            versionName = verName,
            versionCode = verCode
        )
    } catch (_: Exception) {
        AppMetadataInfo(
            appName = fallbackName,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong()
        )
    }
}

private data class OpenSourceLibraryNotice(
    val name: String,
    val license: String,
    val purpose: String
)

private val BUNDLED_OPEN_SOURCE_NOTICES = listOf(
    OpenSourceLibraryNotice(
        name = "AndroidX Jetpack Compose & Material 3",
        license = "Apache License 2.0",
        purpose = "Declarative UI toolkit and Material Design 3 components"
    ),
    OpenSourceLibraryNotice(
        name = "AndroidX Media3 (ExoPlayer & MediaSession)",
        license = "Apache License 2.0",
        purpose = "Offline audio/video playback engine and media controls"
    ),
    OpenSourceLibraryNotice(
        name = "AndroidX Room & Preferences DataStore",
        license = "Apache License 2.0",
        purpose = "Local library database and user preferences storage"
    ),
    OpenSourceLibraryNotice(
        name = "Kotlin & Kotlinx Coroutines",
        license = "Apache License 2.0",
        purpose = "Asynchronous processing and structured concurrency"
    ),
    OpenSourceLibraryNotice(
        name = "Coil (Coroutine Image Loader)",
        license = "Apache License 2.0",
        purpose = "Local album artwork decoding and caching"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSangeetScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sangeetMotion = LocalSangeetMotion.current
    val appMetadata = remember(context) { readActualAppMetadata(context) }
    val appDisplayName = stringResource(id = R.string.app_name)

    var showCardEntrance by remember { mutableStateOf(sangeetMotion.isReducedMotion) }
    LaunchedEffect(Unit) {
        showCardEntrance = true
    }

    var licensesExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("about_sangeet_screen"),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "About $appDisplayName",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Application & Developer Information",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldBright
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("about_sangeet_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("about_sangeet_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. SANGEET Application Identity Card (Logo, Name, Version Name & Version Code)
            item {
                AnimatedVisibility(
                    visible = showCardEntrance,
                    enter = if (sangeetMotion.isReducedMotion) {
                        fadeIn(tween(0))
                    } else {
                        fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it / 8 }
                    }
                ) {
                    CassetteSectionCard(highlighted = true) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(listOf(ObsidianCardTop, ObsidianCardBottom))
                                )
                                .padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFF0A1024))
                                    .border(1.6.dp, GoldAccent.copy(alpha = 0.85f), RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_sangeet_logo),
                                    contentDescription = "$appDisplayName Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(22.dp))
                                        .testTag("about_app_logo")
                                )
                            }

                            Text(
                                text = appDisplayName,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                ),
                                color = GoldBright,
                                modifier = Modifier.testTag("about_app_name")
                            )

                            Text(
                                text = "Retro Cassette Offline Music & Video Player",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD1D5DB),
                                textAlign = TextAlign.Center
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CassetteHeaderBadge(text = "VERSION ${appMetadata.versionName}")
                                CassetteHeaderBadge(text = "BUILD ${appMetadata.versionCode}")
                            }

                            HorizontalDivider(
                                color = GoldAccent.copy(alpha = 0.16f),
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Version Name",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9CA3AF)
                                    )
                                    Text(
                                        text = appMetadata.versionName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = Color(0xFFF9FAFB),
                                        modifier = Modifier.testTag("about_version_name")
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Version Code",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9CA3AF)
                                    )
                                    Text(
                                        text = "${appMetadata.versionCode}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = GoldBright,
                                        modifier = Modifier.testTag("about_version_code")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Premium Cassette-Themed Developer Profile Card
            item {
                AnimatedVisibility(
                    visible = showCardEntrance,
                    enter = if (sangeetMotion.isReducedMotion) {
                        fadeIn(tween(0))
                    } else {
                        fadeIn(tween(420, delayMillis = 90, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(420, delayMillis = 90, easing = FastOutSlowInEasing))
                    }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("about_developer_card"),
                        shape = RoundedCornerShape(22.dp),
                        color = ObsidianCardBottom,
                        border = BorderStroke(
                            width = 1.4.dp,
                            brush = Brush.linearGradient(
                                listOf(GoldBright, GoldAccent, Color(0xFF8F6B14), GoldBright)
                            )
                        ),
                        shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            GoldAccent.copy(alpha = 0.12f),
                                            ObsidianCardTop,
                                            ObsidianCardBottom
                                        )
                                    )
                                )
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Cassette Top Label Strip with Corner Screws
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CassetteScrewDot()
                                    CassetteHeaderBadge(text = "DEVELOPER PROFILE")
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "CREATOR EDITION",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = GoldAccent.copy(alpha = 0.8f)
                                    )
                                    CassetteScrewDot()
                                }
                            }

                            HorizontalDivider(color = GoldAccent.copy(alpha = 0.22f))

                            // Developer Portrait + Identity Details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black)
                                        .border(2.2.dp, GoldBright, CircleShape)
                                        .padding(3.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_developer_profile),
                                        contentDescription = "Developer Profile Artwork",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .testTag("about_developer_artwork")
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Developer Name:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9CA3AF)
                                    )
                                    Text(
                                        text = "🩺 Dr. M. Ganeswar",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = GoldBright,
                                        modifier = Modifier.testTag("about_developer_name")
                                    )
                                    Text(
                                        text = "drmganeswar",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color(0xFFE5E7EB),
                                        modifier = Modifier.testTag("about_developer_handle")
                                    )
                                }
                            }

                            // Qualification Cassette Plate
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF14161D),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.School,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Qualification:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF9CA3AF)
                                        )
                                        Text(
                                            text = "(BAMS final year)",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color(0xFFF9FAFB),
                                            modifier = Modifier.testTag("about_developer_qualification")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Privacy Information
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CassetteSectionHeader(
                            title = "Privacy Information",
                            badgeText = "100% OFFLINE"
                        )
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "SANGEET operates 100% offline with zero internet permissions, zero analytics, and zero external data collection. All audio files, artwork caches, synchronized lyrics, playlists, profiles, and DSP settings remain locally on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD1D5DB)
                            )
                        }
                    }
                }
            }

            // 4. Open-Source Licenses
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { licensesExpanded = !licensesExpanded }
                                .padding(vertical = 4.dp)
                                .testTag("about_licenses_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CassetteSectionHeader(
                                title = "Open-Source Licenses",
                                subtitle = "${BUNDLED_OPEN_SOURCE_NOTICES.size} bundled libraries • Tap to ${if (licensesExpanded) "collapse" else "view"}",
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (licensesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (licensesExpanded) "Collapse Licenses" else "Expand Licenses",
                                tint = GoldBright
                            )
                        }

                        AnimatedVisibility(visible = licensesExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                HorizontalDivider(color = GoldAccent.copy(alpha = 0.15f))
                                BUNDLED_OPEN_SOURCE_NOTICES.forEach { lib ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = lib.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFF9FAFB),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = lib.license,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                color = GoldAccent
                                            )
                                        }
                                        Text(
                                            text = lib.purpose,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF9CA3AF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Copyright Information
            item {
                CassetteSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CassetteSectionHeader(title = "Copyright Information")
                        Text(
                            text = "© $appDisplayName • Developed by 🩺 Dr. M. Ganeswar (drmganeswar).",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFE5E7EB)
                        )
                        Text(
                            text = "Built natively for offline Android playback (${appMetadata.appName} v${appMetadata.versionName}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CassetteScrewDot() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(0xFF23252E))
            .border(0.8.dp, GoldAccent.copy(alpha = 0.6f), CircleShape)
    )
}
