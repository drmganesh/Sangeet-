package com.example.sangeet.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.data.model.VideoTrack
import com.example.sangeet.ui.screens.player.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

val LocalCassetteStyle = compositionLocalOf { CassetteStyle.BLACK_AND_GOLD }

object CassetteThemeTokens {
    val Gold = Color(0xFFE8B429)
    val GoldBright = Color(0xFFFFD666)
    val GoldDark = Color(0xFF8C640F)
    val DeckBlack = Color(0xFF0A0B0E)
    val DeckElevated = Color(0xFF13151B)
}

@Composable
fun CassetteHeaderBadge(
    text: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = accentColor.copy(alpha = 0.16f),
        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.9.sp,
                fontSize = 10.sp
            ),
            color = accentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CassetteSectionHeader(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!badgeText.isNullOrBlank()) {
                    CassetteHeaderBadge(text = badgeText)
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 11.dp)
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun CassetteSectionCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalCassetteStyle.current.palette()
    val primary = MaterialTheme.colorScheme.primary
    val borderColor = if (highlighted) {
        primary.copy(alpha = 0.85f)
    } else {
        palette.shellBorder.copy(alpha = 0.28f)
    }
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val shape = RoundedCornerShape(20.dp)

    if (onClick != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(if (highlighted) 1.4.dp else 0.9.dp, borderColor),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(if (highlighted) 1.4.dp else 0.9.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
fun CassetteTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cassette_topbar_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badgeText.isNullOrBlank()) {
                        CassetteHeaderBadge(text = badgeText)
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
fun CassetteEmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    CassetteSectionCard(
        modifier = modifier,
        highlighted = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

/**
 * Vintage cassette storage case / tape box card for the Folders tab.
 */
@Composable
fun CassetteFolderBoxCard(
    folderName: String,
    folderPath: String,
    itemCountLabel: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit = {}
) {
    val palette = LocalCassetteStyle.current.palette()
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.9.dp,
            color = if (isSelected) primary else palette.shellBorder.copy(alpha = 0.30f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniature vintage cassette tape case spine/box icon
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.shellPrimary, palette.shellSecondary)
                        )
                    )
                    .border(1.dp, palette.shellBorder.copy(alpha = 0.65f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(primary.copy(alpha = 0.85f))
                )
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = folderName,
                    tint = primary,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    CassetteHeaderBadge(text = itemCountLabel)
                }
                if (folderPath.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = folderPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = trailingActions
            )
        }
    }
}

/**
 * Cassette-themed Artist Card keeping normal circular artist portrait artwork (NOT a cassette).
 */
@Composable
fun CassetteArtistCard(
    artistName: String,
    representativeTrack: AudioTrack?,
    trackCount: Int,
    isCurrentlyPlaying: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val palette = LocalCassetteStyle.current.palette()
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrentlyPlaying) {
            primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
        },
        border = BorderStroke(
            width = if (isCurrentlyPlaying) 1.4.dp else 0.9.dp,
            color = if (isCurrentlyPlaying) primary else palette.shellBorder.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isCurrentlyPlaying) 1.8.dp else 1.dp,
                        color = if (isCurrentlyPlaying) primary else palette.shellBorder.copy(alpha = 0.65f),
                        shape = CircleShape
                    )
                    .padding(2.dp)
            ) {
                if (representativeTrack != null) {
                    TrackArtwork(
                        track = representativeTrack,
                        shape = CircleShape,
                        contentDescription = artistName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = artistName,
                            tint = primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isCurrentlyPlaying) primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$trackCount ${if (trackCount == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}

/**
 * Cassette-themed Genre card.
 */
@Composable
fun CassetteGenreCard(
    genreName: String,
    trackCount: Int,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCassetteStyle.current.palette()
    val primary = MaterialTheme.colorScheme.primary
    val code = remember(index) { "CH-%02d".format(index + 1) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(0.9.dp, palette.shellBorder.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CassetteHeaderBadge(text = code)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = genreName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$trackCount ${if (trackCount == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "C-90",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = primary.copy(alpha = 0.7f)
            )
        }
    }
}

private object VideoThumbCache {
    private val paths = ConcurrentHashMap<Long, String>()
    private val failed = ConcurrentHashMap<Long, Boolean>()

    fun get(id: Long): String? = paths[id]
    fun put(id: Long, path: String) {
        paths[id] = path
        failed.remove(id)
    }

    fun isKnownFailed(id: Long): Boolean = failed[id] == true
    fun markFailed(id: Long) {
        failed[id] = true
    }
}

private suspend fun resolveOfflineVideoThumb(context: Context, video: VideoTrack): String? =
    withContext(Dispatchers.IO) {
        VideoThumbCache.get(video.id)?.let { existing ->
            if (File(existing).exists()) return@withContext existing
        }
        val thumbDir = File(context.cacheDir, "video_thumbs").apply { mkdirs() }
        val target = File(thumbDir, "vid_${video.id}.jpg")
        if (target.exists() && target.length() > 0L) {
            VideoThumbCache.put(video.id, target.absolutePath)
            return@withContext target.absolutePath
        }

        val retriever = MediaMetadataRetriever()
        val bitmap: Bitmap? = try {
            val loaded = when {
                video.contentUri.isNotBlank() -> {
                    runCatching {
                        retriever.setDataSource(context, Uri.parse(video.contentUri))
                        true
                    }.getOrDefault(false)
                }
                video.path.isNotBlank() && File(video.path).exists() -> {
                    runCatching {
                        retriever.setDataSource(video.path)
                        true
                    }.getOrDefault(false)
                }
                else -> false
            }
            if (!loaded) {
                null
            } else {
                retriever.getFrameAtTime(1_500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }

        if (bitmap != null) {
            runCatching {
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                bitmap.recycle()
                if (target.exists() && target.length() > 0L) {
                    VideoThumbCache.put(video.id, target.absolutePath)
                    return@withContext target.absolutePath
                }
            }
        }

        VideoThumbCache.markFailed(video.id)
        null
    }

/**
 * Retro cinema/VHS metallic film-frame thumbnail for Video Library items.
 * Preserves authentic local video frame thumbnails (never disguising video as an audio cassette).
 */
@Composable
fun VideoRetroThumbCard(
    video: VideoTrack,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbPath by remember(video.id) {
        mutableStateOf(VideoThumbCache.get(video.id))
    }

    LaunchedEffect(video.id, video.contentUri, video.path) {
        if (thumbPath == null && !VideoThumbCache.isKnownFailed(video.id)) {
            thumbPath = resolveOfflineVideoThumb(context, video)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val palette = LocalCassetteStyle.current.palette()
    val request = remember(video.id, thumbPath) {
        thumbPath?.let { path ->
            ImageRequest.Builder(context)
                .data(File(path))
                .memoryCacheKey("vid_thumb_${video.id}")
                .crossfade(180)
                .build()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0C0D10))
            .border(1.1.dp, palette.shellBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = "${video.title} video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Retro film-strip sprocket edge accent
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = 1.6.dp.toPx()
                val insetX = 4.dp.toPx()
                val steps = 3
                for (i in 0 until steps) {
                    val y = size.height * ((i + 0.5f) / steps)
                    drawCircle(Color.Black.copy(alpha = 0.65f), radius = r, center = Offset(insetX, y))
                    drawCircle(Color.Black.copy(alpha = 0.65f), radius = r, center = Offset(size.width - insetX, y))
                }
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = video.title,
                tint = primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
