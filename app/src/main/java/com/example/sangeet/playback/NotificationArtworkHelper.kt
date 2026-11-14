package com.example.sangeet.playback

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.scanner.ArtworkManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Offline LRU-cached artwork loader and procedural fallback generator for SANGEET
 * media notifications, lock screens, and MediaSession BitmapLoader integration.
 */
@OptIn(UnstableApi::class)
class NotificationArtworkHelper(private val context: Context) : BitmapLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = AppDatabase.getInstance(context)
    private val artworkManager = ArtworkManager(context)

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024L).toInt() / 8).coerceIn(4 * 1024, 16 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/", ignoreCase = true)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val decoded = decodeSampledByteArray(data, DEFAULT_ARTWORK_PX)
                if (decoded != null) {
                    future.set(decoded)
                } else {
                    future.set(generateFallbackArtwork("SANGEET", "Offline Audio", DEFAULT_ARTWORK_PX))
                }
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val cacheKey = uri.toString()
        memoryCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) {
                future.set(cached)
                return future
            }
        }

        scope.launch {
            try {
                val bitmap = loadBitmapSync(uri)
                memoryCache.put(cacheKey, bitmap)
                future.set(bitmap)
            } catch (t: Throwable) {
                val fallback = generateFallbackArtwork("SANGEET", "Offline Audio", DEFAULT_ARTWORK_PX)
                future.set(fallback)
            }
        }
        return future
    }

    private suspend fun loadBitmapSync(uri: Uri): Bitmap {
        val targetPx = uri.getQueryParameter("size")?.toIntOrNull()?.coerceIn(128, 768)
            ?: DEFAULT_ARTWORK_PX

        if (uri.scheme == ART_SCHEME) {
            val trackId = uri.lastPathSegment?.toLongOrNull()
            val track = trackId?.let {
                PlaybackManager.getInstance(context).findTrackById(it) ?: db.audioTrackDao().getTrackById(it)
            }
            if (track != null) {
                return resolveTrackBitmapSync(track, targetPx)
            }
            return generateFallbackArtwork("SANGEET", "Offline Audio", targetPx)
        }

        val resolved = decodeFromUriOrPath(uri.toString(), targetPx)
        return resolved ?: generateFallbackArtwork("SANGEET", "Offline Audio", targetPx)
    }

    private suspend fun resolveTrackBitmapSync(track: AudioTrack, targetPx: Int): Bitmap {
        val resolvedArtPath = try {
            artworkManager.resolveArtwork(track)
        } catch (_: Exception) {
            null
        }

        if (!resolvedArtPath.isNullOrBlank()) {
            val decoded = decodeFromUriOrPath(resolvedArtPath, targetPx)
            if (decoded != null) {
                return decoded
            }
        }

        return generateFallbackArtwork(
            title = track.title.ifBlank { "Unknown Track" },
            artist = track.artist.ifBlank { track.album },
            sizePx = targetPx
        )
    }

    private fun decodeFromUriOrPath(uriOrPath: String, targetPx: Int): Bitmap? {
        return try {
            val parsed = Uri.parse(uriOrPath)
            when (parsed.scheme) {
                "content" -> {
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(parsed)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, boundsOpts)
                    }
                    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetPx)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val raw = context.contentResolver.openInputStream(parsed)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOpts)
                    } ?: return null
                    centerCropAndScale(raw, targetPx)
                }
                "file" -> {
                    val path = parsed.path ?: return null
                    decodeFile(path, targetPx)
                }
                else -> decodeFile(uriOrPath, targetPx)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeFile(path: String, targetPx: Int): Bitmap? {
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        return centerCropAndScale(raw, targetPx)
    }

    private fun decodeSampledByteArray(data: ByteArray, targetPx: Int): Bitmap? {
        if (data.isEmpty()) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOpts) ?: return null
        return centerCropAndScale(raw, targetPx)
    }

    private fun centerCropAndScale(source: Bitmap, targetPx: Int): Bitmap {
        val w = source.width
        val h = source.height
        val side = min(w, h)
        val xOffset = (w - side) / 2
        val yOffset = (h - side) / 2

        val cropped = if (w == h) {
            source
        } else {
            Bitmap.createBitmap(source, xOffset, yOffset, side, side).also {
                if (it !== source) source.recycle()
            }
        }

        return if (cropped.width == targetPx && cropped.height == targetPx) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetPx, targetPx, true).also {
                if (it !== cropped) cropped.recycle()
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetPx: Int): Int {
        var inSampleSize = 1
        if (height > targetPx || width > targetPx) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= targetPx && (halfWidth / inSampleSize) >= targetPx) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    fun clearCache() {
        memoryCache.evictAll()
    }

    fun generateFallbackArtwork(title: String, artist: String, sizePx: Int): Bitmap {
        val safeSize = sizePx.coerceIn(128, 768)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val seed = abs((title + "|" + artist).hashCode())
        val palette = FALLBACK_PALETTES[seed % FALLBACK_PALETTES.size]

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, safeSize.toFloat(), safeSize.toFloat(),
                intArrayOf(palette.first, palette.second, Color.rgb(12, 16, 28)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, safeSize.toFloat(), safeSize.toFloat(), bgPaint)

        // Subtle vinyl concentric rings for rich offline cover aesthetic
        val cx = safeSize * 0.5f
        val cy = safeSize * 0.5f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, safeSize * 0.008f)
            color = Color.argb(46, 255, 255, 255)
        }
        listOf(0.41f, 0.33f, 0.24f).forEach { factor ->
            canvas.drawCircle(cx, cy, safeSize * factor, ringPaint)
        }

        // Translucent center medallion
        val medallionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 0, 0, 0)
        }
        canvas.drawCircle(cx, cy, safeSize * 0.18f, medallionPaint)

        // Crisp monogram / musical mark
        val glyph = title.trim().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetterOrDigit() }?.toString() ?: "♪"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = safeSize * 0.18f
        }
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(glyph, cx, textY, textPaint)

        // Subtle SANGEET bottom label
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = safeSize * 0.055f
        }
        canvas.drawText("SANGEET", cx, safeSize * 0.89f, brandPaint)

        return bitmap
    }

    companion object {
        const val ART_SCHEME = "sangeet-art"
        const val DEFAULT_ARTWORK_PX = 512

        fun buildArtUri(trackId: Long, sizePx: Int = DEFAULT_ARTWORK_PX): Uri {
            return Uri.parse("$ART_SCHEME://track/$trackId?size=$sizePx")
        }

        private val FALLBACK_PALETTES = listOf(
            Color.rgb(0, 172, 193) to Color.rgb(21, 101, 192),
            Color.rgb(124, 77, 255) to Color.rgb(49, 27, 146),
            Color.rgb(233, 30, 99) to Color.rgb(136, 14, 79),
            Color.rgb(0, 191, 165) to Color.rgb(0, 105, 92),
            Color.rgb(255, 112, 67) to Color.rgb(191, 54, 12),
            Color.rgb(92, 107, 192) to Color.rgb(40, 53, 147)
        )
    }
}
