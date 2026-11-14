package com.example.sangeet.scanner

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.min

/**
 * Lightweight, zero-native-log container header probe to determine whether an audio file
 * contains embedded artwork BEFORE calling [android.media.MediaMetadataRetriever.getEmbeddedPicture].
 *
 * Calling MediaMetadataRetriever.getEmbeddedPicture() on tracks without embedded art causes
 * Android's native JNI layer to emit noisy:
 * `E/MediaMetadataRetrieverJNI: getEmbeddedPicture: Call to getEmbeddedPicture failed.`
 */
object EmbeddedArtworkProbe {

    private val UNSUPPORTED_NO_ART_MIME_TYPES = setOf(
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/vnd.wave",
        "audio/midi",
        "audio/mid",
        "audio/x-midi",
        "audio/amr",
        "audio/amr-wb",
        "audio/3gpp",
        "audio/L16",
        "audio/pcm"
    )

    private val UNSUPPORTED_NO_ART_EXTENSIONS = setOf(
        "wav", "pcm", "mid", "midi", "amr", "awb"
    )

    fun mightContainEmbeddedArtwork(
        context: Context,
        contentUri: String,
        filePath: String,
        mimeType: String = ""
    ): Boolean {
        val normalizedMime = mimeType.trim().lowercase()
        val ext = filePath.substringAfterLast('.', "").trim().lowercase()
        if (normalizedMime in UNSUPPORTED_NO_ART_MIME_TYPES || ext in UNSUPPORTED_NO_ART_EXTENSIONS) {
            return false
        }

        return try {
            val stream: InputStream? = when {
                contentUri.isNotBlank() && contentUri.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(contentUri))
                }
                contentUri.isNotBlank() && contentUri.startsWith("file://") -> {
                    val path = Uri.parse(contentUri).path
                    val file = path?.let { File(it) }
                    if (file != null && file.exists() && file.isFile) FileInputStream(file) else null
                }
                filePath.isNotBlank() -> {
                    val file = File(filePath)
                    if (file.exists() && file.isFile) FileInputStream(file) else null
                }
                else -> null
            }

            if (stream == null) return false
            BufferedInputStream(stream, 16 * 1024).use { buffered ->
                hasEmbeddedArtworkInStream(buffered)
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun hasEmbeddedArtworkInBytes(bytes: ByteArray): Boolean {
        return ByteArrayInputStream(bytes).use { hasEmbeddedArtworkInStream(it) }
    }

    fun hasEmbeddedArtworkInStream(input: InputStream): Boolean {
        val header = ByteArray(12)
        val readCount = readUpTo(input, header, 12)
        if (readCount < 4) return false

        // 1. RIFF / WAVE (WAVExtractor does not expose embedded art via MediaMetadataRetriever)
        if (matchesAscii(header, 0, "RIFF")) {
            return false
        }

        // 2. ID3v2 header (MP3, AAC, AIFF, etc.)
        if (readCount >= 10 && matchesAscii(header, 0, "ID3")) {
            return probeId3v2(input, header, readCount)
        }

        // 3. FLAC metadata blocks
        if (matchesAscii(header, 0, "fLaC")) {
            return probeFlac(input, header, readCount)
        }

        // 4. MP4 / M4A / 3GP ISOBMFF boxes
        if (readCount >= 8 && matchesAscii(header, 4, "ftyp")) {
            return probeMp4(input, header, readCount)
        }

        // 5. OGG / OPUS VorbisComment picture marker
        if (matchesAscii(header, 0, "OggS")) {
            return probeOgg(input, header, readCount)
        }

        // 6. ASF / WMA ("WM/Picture" attribute in header)
        if (readCount >= 4 &&
            (header[0].toInt() and 0xFF) == 0x30 &&
            (header[1].toInt() and 0xFF) == 0x26 &&
            (header[2].toInt() and 0xFF) == 0xB2 &&
            (header[3].toInt() and 0xFF) == 0x75
        ) {
            return scanPrefixForUtf16LeMarker(input, "WM/Picture", maxBytes = 256 * 1024)
        }

        // Raw MP3 frames (0xFF 0xFB / 0xF3 / 0xF2), ADTS AAC, MIDI, unknown headers
        return false
    }

    private fun probeId3v2(input: InputStream, header12: ByteArray, readCount: Int): Boolean {
        val majorVersion = header12[3].toInt() and 0xFF
        val flags = header12[5].toInt() and 0xFF
        val tagSize = syncsafeInt(header12[6], header12[7], header12[8], header12[9])
        if (tagSize <= 0) return false

        // Combine leftover bytes read beyond the 10-byte ID3 header (up to 2 bytes from our 12-byte initial read)
        val combinedStream: InputStream = if (readCount > 10) {
            val extra = header12.copyOfRange(10, readCount)
            java.io.SequenceInputStream(ByteArrayInputStream(extra), input)
        } else {
            input
        }

        var remaining = min(tagSize.toLong(), 32L * 1024L * 1024L)

        // Extended header in v2.3 / v2.4
        if ((majorVersion == 3 || majorVersion == 4) && (flags and 0x40) != 0 && remaining >= 4) {
            val extSizeBytes = ByteArray(4)
            if (!readExact(combinedStream, extSizeBytes, 4)) return false
            remaining -= 4
            val skipBytes = if (majorVersion == 4) {
                (syncsafeInt(extSizeBytes[0], extSizeBytes[1], extSizeBytes[2], extSizeBytes[3]) - 4).coerceAtLeast(0)
            } else {
                bigEndianInt(extSizeBytes[0], extSizeBytes[1], extSizeBytes[2], extSizeBytes[3]).coerceAtLeast(0)
            }.toLong()
            if (skipBytes > remaining || !skipExact(combinedStream, skipBytes)) return false
            remaining -= skipBytes
        }

        val frameHeaderSize = if (majorVersion == 2) 6 else 10
        val frameHdr = ByteArray(frameHeaderSize)
        var framesVisited = 0

        while (remaining >= frameHeaderSize && framesVisited < 512) {
            framesVisited++
            if (!readExact(combinedStream, frameHdr, frameHeaderSize)) return false
            remaining -= frameHeaderSize

            // Padding reached
            if (frameHdr[0].toInt() == 0) return false

            if (majorVersion == 2) {
                if (!isValidFrameChar(frameHdr[0]) || !isValidFrameChar(frameHdr[1]) || !isValidFrameChar(frameHdr[2])) {
                    return scanForAsciiToken(combinedStream, "PIC", min(remaining, 256L * 1024L).toInt())
                }
                val id = String(byteArrayOf(frameHdr[0], frameHdr[1], frameHdr[2]), Charsets.US_ASCII)
                val size = ((frameHdr[3].toInt() and 0xFF) shl 16) or
                    ((frameHdr[4].toInt() and 0xFF) shl 8) or
                    (frameHdr[5].toInt() and 0xFF)
                if (id == "PIC" && size > 0) return true
                if (size < 0 || size > remaining) return false
                if (!skipExact(combinedStream, size.toLong())) return false
                remaining -= size
            } else {
                if (!isValidFrameChar(frameHdr[0]) || !isValidFrameChar(frameHdr[1]) ||
                    !isValidFrameChar(frameHdr[2]) || !isValidFrameChar(frameHdr[3])
                ) {
                    return scanForAsciiToken(combinedStream, "APIC", min(remaining, 256L * 1024L).toInt())
                }
                val id = String(byteArrayOf(frameHdr[0], frameHdr[1], frameHdr[2], frameHdr[3]), Charsets.US_ASCII)
                val syncsafeSize = syncsafeInt(frameHdr[4], frameHdr[5], frameHdr[6], frameHdr[7])
                val rawSize = bigEndianInt(frameHdr[4], frameHdr[5], frameHdr[6], frameHdr[7])
                val size = if (majorVersion == 4) {
                    // Some encoders write non-syncsafe sizes in v2.4; prefer syncsafe unless MSB bits are set
                    val hasNonSyncsafeBit = (frameHdr[4].toInt() and 0x80) != 0 ||
                        (frameHdr[5].toInt() and 0x80) != 0 ||
                        (frameHdr[6].toInt() and 0x80) != 0 ||
                        (frameHdr[7].toInt() and 0x80) != 0
                    if (hasNonSyncsafeBit) rawSize else syncsafeSize
                } else {
                    rawSize
                }
                if (id == "APIC" && size > 0) return true
                if (size < 0 || size > remaining) return false
                if (!skipExact(combinedStream, size.toLong())) return false
                remaining -= size
            }
        }
        return false
    }

    private fun probeFlac(input: InputStream, header12: ByteArray, readCount: Int): Boolean {
        val combinedStream: InputStream = if (readCount > 4) {
            java.io.SequenceInputStream(ByteArrayInputStream(header12.copyOfRange(4, readCount)), input)
        } else {
            input
        }

        val blockHdr = ByteArray(4)
        for (i in 0 until 256) {
            if (!readExact(combinedStream, blockHdr, 4)) return false
            val isLast = (blockHdr[0].toInt() and 0x80) != 0
            val blockType = blockHdr[0].toInt() and 0x7F
            val length = ((blockHdr[1].toInt() and 0xFF) shl 16) or
                ((blockHdr[2].toInt() and 0xFF) shl 8) or
                (blockHdr[3].toInt() and 0xFF)

            if (blockType == 6 && length > 0) return true
            if (isLast) return false
            if (length > 0 && !skipExact(combinedStream, length.toLong())) return false
        }
        return false
    }

    private fun probeMp4(input: InputStream, header12: ByteArray, readCount: Int): Boolean {
        val ftypSize = uint32(header12, 0)
        if (ftypSize < 8L || ftypSize > 4096L) return false
        val combinedStream: InputStream = if (readCount > 8) {
            java.io.SequenceInputStream(ByteArrayInputStream(header12.copyOfRange(8, readCount)), input)
        } else {
            input
        }
        if (!skipExact(combinedStream, ftypSize - 8L)) return false
        return scanMp4Containers(combinedStream, maxLimit = Long.MAX_VALUE, depth = 0)
    }

    private fun scanMp4Containers(input: InputStream, maxLimit: Long, depth: Int): Boolean {
        if (depth > 8) return false
        var remaining = maxLimit
        val boxHdr = ByteArray(8)
        var boxesVisited = 0

        while (remaining >= 8 && boxesVisited < 1024) {
            boxesVisited++
            if (!readExact(input, boxHdr, 8)) return false
            remaining -= 8

            var boxSize = uint32(boxHdr, 0)
            val boxType = String(boxHdr, 4, 4, Charsets.ISO_8859_1)
            var headerLen = 8L

            if (boxSize == 1L) {
                if (remaining < 8) return false
                val extHdr = ByteArray(8)
                if (!readExact(input, extHdr, 8)) return false
                remaining -= 8
                headerLen = 16L
                boxSize = uint64(extHdr, 0)
            } else if (boxSize == 0L) {
                boxSize = if (maxLimit == Long.MAX_VALUE) Long.MAX_VALUE else remaining + headerLen
            }

            if (boxSize < headerLen) return false
            val payloadSize = if (boxSize == Long.MAX_VALUE) remaining else (boxSize - headerLen)
            if (payloadSize > remaining) return false

            if (boxType == "covr" && payloadSize > 0L) {
                return true
            }

            when (boxType) {
                "moov", "udta", "ilst" -> {
                    if (scanMp4Containers(input, payloadSize, depth + 1)) return true
                    remaining -= payloadSize
                }
                "meta" -> {
                    if (payloadSize >= 12L) {
                        // Standard ISO 'meta' has 4-byte FullBox version/flags before the first child box;
                        // peek 12 bytes to inspect both possible child box type positions (offset 4 vs offset 8).
                        val peek = ByteArray(12)
                        if (!readExact(input, peek, 12)) return false
                        val childStream: InputStream
                        val childLimit: Long
                        if (looksLikeBoxType(peek, 8) && !looksLikeBoxType(peek, 4)) {
                            // Standard FullBox: bytes 0..3 are version/flags, bytes 4..11 start first child box
                            childStream = java.io.SequenceInputStream(ByteArrayInputStream(peek.copyOfRange(4, 12)), input)
                            childLimit = payloadSize - 4L
                        } else {
                            // Non-FullBox meta variant: bytes 0..11 already start first child box
                            childStream = java.io.SequenceInputStream(ByteArrayInputStream(peek), input)
                            childLimit = payloadSize
                        }
                        if (scanMp4Containers(childStream, childLimit, depth + 1)) return true
                        remaining -= payloadSize
                    } else {
                        if (!skipExact(input, payloadSize)) return false
                        remaining -= payloadSize
                    }
                }
                else -> {
                    if (payloadSize == Long.MAX_VALUE) return false
                    if (!skipExact(input, payloadSize)) return false
                    remaining -= payloadSize
                }
            }
        }
        return false
    }

    private fun probeOgg(input: InputStream, header12: ByteArray, readCount: Int): Boolean {
        val combinedStream: InputStream = java.io.SequenceInputStream(
            ByteArrayInputStream(header12.copyOfRange(0, readCount)),
            input
        )
        return scanForAsciiTokensIgnoreCase(
            combinedStream,
            listOf("METADATA_BLOCK_PICTURE=", "COVERART="),
            maxBytes = 256 * 1024
        )
    }

    private fun scanPrefixForUtf16LeMarker(input: InputStream, asciiMarker: String, maxBytes: Int): Boolean {
        val target = ByteArray(asciiMarker.length * 2)
        for (i in asciiMarker.indices) {
            target[i * 2] = asciiMarker[i].code.toByte()
            target[i * 2 + 1] = 0
        }
        return scanStreamForBytes(input, target, maxBytes)
    }

    private fun scanForAsciiToken(input: InputStream, token: String, maxBytes: Int): Boolean {
        if (maxBytes <= 0) return false
        return scanStreamForBytes(input, token.toByteArray(Charsets.US_ASCII), maxBytes)
    }

    private fun scanStreamForBytes(input: InputStream, needle: ByteArray, maxBytes: Int): Boolean {
        if (needle.isEmpty() || maxBytes <= 0) return false
        val buffer = ByteArray(8192)
        var totalRead = 0
        var matched = 0
        while (totalRead < maxBytes) {
            val toRead = min(buffer.size, maxBytes - totalRead)
            val n = input.read(buffer, 0, toRead)
            if (n <= 0) break
            totalRead += n
            for (i in 0 until n) {
                if (buffer[i] == needle[matched]) {
                    matched++
                    if (matched == needle.size) return true
                } else {
                    matched = if (buffer[i] == needle[0]) 1 else 0
                }
            }
        }
        return false
    }

    private fun scanForAsciiTokensIgnoreCase(
        input: InputStream,
        tokens: List<String>,
        maxBytes: Int
    ): Boolean {
        val data = ByteArray(min(maxBytes, 256 * 1024))
        var offset = 0
        while (offset < data.size) {
            val n = input.read(data, offset, data.size - offset)
            if (n <= 0) break
            offset += n
        }
        if (offset == 0) return false
        val text = String(data, 0, offset, Charsets.ISO_8859_1)
        return tokens.any { text.contains(it, ignoreCase = true) }
    }

    private fun matchesAscii(bytes: ByteArray, offset: Int, ascii: String): Boolean {
        if (offset + ascii.length > bytes.size) return false
        for (i in ascii.indices) {
            if ((bytes[offset + i].toInt() and 0xFF) != ascii[i].code) return false
        }
        return true
    }

    private fun isValidFrameChar(b: Byte): Boolean {
        val c = b.toInt() and 0xFF
        return c in 'A'.code..'Z'.code || c in '0'.code..'9'.code
    }

    private fun looksLikeBoxType(bytes: ByteArray, offset: Int): Boolean {
        if (offset + 4 > bytes.size) return false
        for (i in 0 until 4) {
            val c = bytes[offset + i].toInt() and 0xFF
            val ok = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == ' '.code
            if (!ok) return false
        }
        return true
    }

    private fun syncsafeInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0x7F) shl 21) or
            ((b1.toInt() and 0x7F) shl 14) or
            ((b2.toInt() and 0x7F) shl 7) or
            (b3.toInt() and 0x7F)
    }

    private fun bigEndianInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0xFF) shl 24) or
            ((b1.toInt() and 0xFF) shl 16) or
            ((b2.toInt() and 0xFF) shl 8) or
            (b3.toInt() and 0xFF)
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 3].toLong() and 0xFFL)
    }

    private fun uint64(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFFL) shl 56) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 48) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 40) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 32) or
            ((bytes[offset + 4].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 5].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 6].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 7].toLong() and 0xFFL)
    }

    private fun readUpTo(input: InputStream, buffer: ByteArray, target: Int): Int {
        var total = 0
        while (total < target) {
            val n = input.read(buffer, total, target - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun readExact(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        return readUpTo(input, buffer, length) == length
    }

    private fun skipExact(input: InputStream, bytesToSkip: Long): Boolean {
        if (bytesToSkip <= 0L) return true
        var remaining = bytesToSkip
        val scratch = ByteArray(4096)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = input.read(scratch, 0, min(scratch.size.toLong(), remaining).toInt())
                if (read <= 0) return false
                remaining -= read
            }
        }
        return true
    }
}
