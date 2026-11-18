package com.example.sangeet.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.sangeet.data.model.AudioTrack
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.min

/**
 * Lightweight, 100% offline embedded lyrics extractor for local audio files.
 * Reads standard container tags without ever modifying the original audio file:
 *  - ID3v2 (MP3 / AIFF / AAC): SYLT (synchronized lyrics) & USLT (unsynchronized/LRC lyrics)
 *  - FLAC / OGG / OPUS VorbisComment: SYNCEDLYRICS / LYRICS / UNSYNCEDLYRICS
 *  - MP4 / M4A: `©lyr` atom inside `udta/meta/ilst`
 */
object EmbeddedLyricsReader {

    private const val TAG = "EmbeddedLyricsReader"

    fun extractEmbeddedLyrics(context: Context, track: AudioTrack): String {
        return try {
            openTrackStream(context, track.contentUri, track.path)?.use { raw ->
                BufferedInputStream(raw, 32 * 1024).use { stream ->
                    extractFromStream(stream)
                }
            }.orEmpty()
        } catch (t: Throwable) {
            Log.d(TAG, "Embedded lyrics read skipped for ${track.title}: ${t.message}")
            ""
        }
    }

    fun extractFromBytes(bytes: ByteArray): String {
        return ByteArrayInputStream(bytes).use { extractFromStream(it) }
    }

    fun extractFromStream(input: InputStream): String {
        val header = ByteArray(12)
        val readCount = readUpTo(input, header, 12)
        if (readCount < 4) return ""

        // ID3v2 header
        if (readCount >= 10 && matchesAscii(header, 0, "ID3")) {
            return readId3v2Lyrics(input, header, readCount)
        }

        // FLAC stream
        if (matchesAscii(header, 0, "fLaC")) {
            return readFlacLyrics(input, header, readCount)
        }

        // MP4 / M4A container
        if (readCount >= 8 && matchesAscii(header, 4, "ftyp")) {
            return readMp4Lyrics(input, header, readCount)
        }

        return ""
    }

    private fun readId3v2Lyrics(input: InputStream, header12: ByteArray, readCount: Int): String {
        val majorVersion = header12[3].toInt() and 0xFF
        val flags = header12[5].toInt() and 0xFF
        val tagSize = syncsafeInt(header12[6], header12[7], header12[8], header12[9])
        if (tagSize <= 0) return ""

        val stream: InputStream = if (readCount > 10) {
            java.io.SequenceInputStream(ByteArrayInputStream(header12.copyOfRange(10, readCount)), input)
        } else {
            input
        }

        var remaining = min(tagSize.toLong(), 16L * 1024L * 1024L)
        if ((majorVersion == 3 || majorVersion == 4) && (flags and 0x40) != 0 && remaining >= 4) {
            val ext = ByteArray(4)
            if (!readExact(stream, ext, 4)) return ""
            remaining -= 4
            val skip = if (majorVersion == 4) {
                (syncsafeInt(ext[0], ext[1], ext[2], ext[3]) - 4).coerceAtLeast(0)
            } else {
                bigEndianInt(ext[0], ext[1], ext[2], ext[3]).coerceAtLeast(0)
            }.toLong()
            if (skip > remaining || !skipExact(stream, skip)) return ""
            remaining -= skip
        }

        var usltResult = ""
        var syltResult = ""

        val frameHdrSize = if (majorVersion == 2) 6 else 10
        val frameHdr = ByteArray(frameHdrSize)
        var framesVisited = 0

        while (remaining >= frameHdrSize && framesVisited < 512) {
            framesVisited++
            if (!readExact(stream, frameHdr, frameHdrSize)) break
            remaining -= frameHdrSize
            if (frameHdr[0].toInt() == 0) break

            val frameId: String
            val frameSize: Int
            if (majorVersion == 2) {
                frameId = String(byteArrayOf(frameHdr[0], frameHdr[1], frameHdr[2]), Charsets.US_ASCII)
                frameSize = ((frameHdr[3].toInt() and 0xFF) shl 16) or
                    ((frameHdr[4].toInt() and 0xFF) shl 8) or
                    (frameHdr[5].toInt() and 0xFF)
            } else {
                frameId = String(byteArrayOf(frameHdr[0], frameHdr[1], frameHdr[2], frameHdr[3]), Charsets.US_ASCII)
                val syncsafe = syncsafeInt(frameHdr[4], frameHdr[5], frameHdr[6], frameHdr[7])
                val raw = bigEndianInt(frameHdr[4], frameHdr[5], frameHdr[6], frameHdr[7])
                frameSize = if (majorVersion == 4) {
                    val hasMsb = (frameHdr[4].toInt() and 0x80) != 0 ||
                        (frameHdr[5].toInt() and 0x80) != 0 ||
                        (frameHdr[6].toInt() and 0x80) != 0 ||
                        (frameHdr[7].toInt() and 0x80) != 0
                    if (hasMsb) raw else syncsafe
                } else {
                    raw
                }
            }

            if (frameSize <= 0 || frameSize > remaining || frameSize > 2 * 1024 * 1024) {
                if (frameSize > 0 && frameSize <= remaining) {
                    if (!skipExact(stream, frameSize.toLong())) break
                    remaining -= frameSize
                    continue
                }
                break
            }

            if (frameId == "USLT" || frameId == "ULT" || frameId == "SYLT" || frameId == "SLT") {
                val payload = ByteArray(frameSize)
                if (!readExact(stream, payload, frameSize)) break
                remaining -= frameSize

                if (frameId == "SYLT" || frameId == "SLT") {
                    val parsed = decodeSyltFrame(payload)
                    if (parsed.isNotBlank()) syltResult = parsed
                } else {
                    val parsed = decodeUsltFrame(payload)
                    if (parsed.isNotBlank()) usltResult = parsed
                }
            } else {
                if (!skipExact(stream, frameSize.toLong())) break
                remaining -= frameSize
            }
        }

        return syltResult.ifBlank { usltResult }
    }

    private fun decodeUsltFrame(payload: ByteArray): String {
        if (payload.size < 5) return ""
        val encoding = payload[0].toInt() and 0xFF
        // Bytes 1..3: language, then null-terminated content descriptor, then lyrics text
        var offset = 4
        offset = skipNullTerminatedString(payload, offset, encoding)
        if (offset >= payload.size) return ""
        return decodeId3String(payload, offset, payload.size, encoding).trim()
    }

    private fun decodeSyltFrame(payload: ByteArray): String {
        if (payload.size < 7) return ""
        val encoding = payload[0].toInt() and 0xFF
        // Bytes 1..3 language, byte 4 timestamp format (2 = ms), byte 5 content type
        val timestampFormat = payload[4].toInt() and 0xFF
        var offset = 6
        offset = skipNullTerminatedString(payload, offset, encoding)
        if (offset >= payload.size) return ""

        val sb = StringBuilder()
        while (offset < payload.size) {
            val endStr = findNullTerminator(payload, offset, encoding)
            val rawText = decodeId3String(payload, offset, endStr, encoding)
                .replace("\r", "")
                .replace("\n", " ")
                .trim()
            val nullBytes = if (encoding == 1 || encoding == 2) 2 else 1
            offset = endStr + nullBytes
            if (offset + 4 > payload.size) break
            val rawStamp = bigEndianInt(payload[offset], payload[offset + 1], payload[offset + 2], payload[offset + 3]).toLong()
            offset += 4
            val ms = if (timestampFormat == 2) rawStamp else rawStamp * 26L
            if (rawText.isNotBlank()) {
                sb.append('[').append(LrcParser.formatTimestamp(ms)).append(']').appendLine(rawText)
            }
        }
        return sb.toString().trim()
    }

    private fun readFlacLyrics(input: InputStream, header12: ByteArray, readCount: Int): String {
        val stream: InputStream = if (readCount > 4) {
            java.io.SequenceInputStream(ByteArrayInputStream(header12.copyOfRange(4, readCount)), input)
        } else {
            input
        }

        val blockHdr = ByteArray(4)
        for (i in 0 until 128) {
            if (!readExact(stream, blockHdr, 4)) break
            val isLast = (blockHdr[0].toInt() and 0x80) != 0
            val blockType = blockHdr[0].toInt() and 0x7F
            val length = ((blockHdr[1].toInt() and 0xFF) shl 16) or
                ((blockHdr[2].toInt() and 0xFF) shl 8) or
                (blockHdr[3].toInt() and 0xFF)

            if (blockType == 4 && length in 8..(2 * 1024 * 1024)) {
                val data = ByteArray(length)
                if (!readExact(stream, data, length)) break
                val parsed = parseVorbisCommentLyrics(data)
                if (parsed.isNotBlank()) return parsed
            } else {
                if (length > 0 && !skipExact(stream, length.toLong())) break
            }
            if (isLast) break
        }
        return ""
    }

    private fun parseVorbisCommentLyrics(data: ByteArray): String {
        if (data.size < 8) return ""
        var pos = 0
        val vendorLen = littleEndianInt(data, pos)
        pos += 4
        if (vendorLen < 0 || pos + vendorLen + 4 > data.size) return ""
        pos += vendorLen
        val count = littleEndianInt(data, pos).coerceIn(0, 1024)
        pos += 4

        var synced = ""
        var plain = ""
        for (i in 0 until count) {
            if (pos + 4 > data.size) break
            val len = littleEndianInt(data, pos)
            pos += 4
            if (len < 0 || pos + len > data.size) break
            val comment = String(data, pos, len, StandardCharsets.UTF_8)
            pos += len
            val eqIdx = comment.indexOf('=')
            if (eqIdx > 0) {
                val key = comment.substring(0, eqIdx).trim().uppercase(Locale.ROOT)
                val value = comment.substring(eqIdx + 1).trim()
                if (value.isNotBlank()) {
                    when (key) {
                        "SYNCEDLYRICS", "LYRICS_SYNCED" -> synced = value
                        "LYRICS", "UNSYNCEDLYRICS" -> if (plain.isBlank()) plain = value
                    }
                }
            }
        }
        return synced.ifBlank { plain }
    }

    private fun readMp4Lyrics(input: InputStream, header12: ByteArray, readCount: Int): String {
        val ftypSize = uint32(header12, 0)
        if (ftypSize < 8L || ftypSize > 4096L) return ""
        val stream: InputStream = if (readCount > 8) {
            java.io.SequenceInputStream(ByteArrayInputStream(header12.copyOfRange(8, readCount)), input)
        } else {
            input
        }
        if (!skipExact(stream, ftypSize - 8L)) return ""
        return scanMp4ForLyrics(stream, Long.MAX_VALUE, depth = 0).orEmpty()
    }

    private fun scanMp4ForLyrics(input: InputStream, maxLimit: Long, depth: Int): String? {
        if (depth > 8) return null
        var remaining = maxLimit
        val boxHdr = ByteArray(8)
        var boxesVisited = 0

        while (remaining >= 8 && boxesVisited < 512) {
            boxesVisited++
            if (!readExact(input, boxHdr, 8)) return null
            var boxSize = uint32(boxHdr, 0)
            var headerBytes = 8L
            if (boxSize == 1L) {
                val ext = ByteArray(8)
                if (!readExact(input, ext, 8)) return null
                boxSize = uint64(ext, 0)
                headerBytes = 16L
            } else if (boxSize == 0L) {
                boxSize = remaining
            }
            if (boxSize < headerBytes) return null
            val payloadSize = (boxSize - headerBytes).coerceAtMost(remaining - headerBytes)
            val boxType = String(boxHdr, 4, 4, Charsets.ISO_8859_1)

            if (boxType == "\u00a9lyr" && payloadSize in 8..(1024 * 1024)) {
                val data = ByteArray(payloadSize.toInt())
                if (!readExact(input, data, data.size)) return null
                // Inside ilst -> ©lyr -> data atom (size[4] + "data"[4] + flags[8] + utf8 text)
                if (data.size > 16 && matchesAscii(data, 4, "data")) {
                    val str = String(data, 16, data.size - 16, StandardCharsets.UTF_8).trim()
                    if (str.isNotBlank()) return str
                }
                remaining -= boxSize
                continue
            }

            if (boxType == "moov" || boxType == "udta" || boxType == "meta" || boxType == "ilst") {
                val innerOffset = if (boxType == "meta") {
                    if (payloadSize < 4 || !skipExact(input, 4)) return null
                    4L
                } else 0L
                val found = scanMp4ForLyrics(input, payloadSize - innerOffset, depth + 1)
                if (!found.isNullOrBlank()) return found
            } else {
                if (payloadSize > 0 && !skipExact(input, payloadSize)) return null
            }
            remaining -= boxSize
        }
        return null
    }

    private fun openTrackStream(context: Context, contentUri: String, filePath: String): InputStream? {
        return when {
            contentUri.isNotBlank() && contentUri.startsWith("content://") ->
                context.contentResolver.openInputStream(Uri.parse(contentUri))
            contentUri.isNotBlank() && contentUri.startsWith("file://") -> {
                val file = Uri.parse(contentUri).path?.let { File(it) }
                if (file != null && file.exists() && file.isFile) FileInputStream(file) else null
            }
            filePath.isNotBlank() -> {
                val file = File(filePath)
                if (file.exists() && file.isFile) FileInputStream(file) else null
            }
            else -> null
        }
    }

    private fun skipNullTerminatedString(data: ByteArray, start: Int, encoding: Int): Int {
        val end = findNullTerminator(data, start, encoding)
        val termSize = if (encoding == 1 || encoding == 2) 2 else 1
        return (end + termSize).coerceAtMost(data.size)
    }

    private fun findNullTerminator(data: ByteArray, start: Int, encoding: Int): Int {
        var i = start
        if (encoding == 1 || encoding == 2) {
            while (i + 1 < data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i
                i += 2
            }
            return data.size
        } else {
            while (i < data.size) {
                if (data[i].toInt() == 0) return i
                i++
            }
            return data.size
        }
    }

    private fun decodeId3String(data: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end) return ""
        val charset: Charset = when (encoding) {
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(data, start, end - start, charset).trim('\u0000')
    }

    private fun matchesAscii(buf: ByteArray, offset: Int, ascii: String): Boolean {
        if (offset + ascii.length > buf.size) return false
        for (i in ascii.indices) {
            if (buf[offset + i].toInt().toChar() != ascii[i]) return false
        }
        return true
    }

    private fun readUpTo(input: InputStream, buffer: ByteArray, maxBytes: Int): Int {
        var total = 0
        while (total < maxBytes) {
            val n = input.read(buffer, total, maxBytes - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun readExact(input: InputStream, buffer: ByteArray, count: Int): Boolean {
        return readUpTo(input, buffer, count) == count
    }

    private fun skipExact(input: InputStream, bytes: Long): Boolean {
        var rem = bytes
        val scratch = ByteArray(4096)
        while (rem > 0) {
            val skipped = input.skip(rem)
            if (skipped > 0) {
                rem -= skipped
            } else {
                val read = input.read(scratch, 0, min(rem, scratch.size.toLong()).toInt())
                if (read <= 0) return false
                rem -= read
            }
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

    private fun littleEndianInt(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun uint32(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFFL) shl 24) or
            ((buf[offset + 1].toLong() and 0xFFL) shl 16) or
            ((buf[offset + 2].toLong() and 0xFFL) shl 8) or
            (buf[offset + 3].toLong() and 0xFFL)
    }

    private fun uint64(buf: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (buf[offset + i].toLong() and 0xFFL)
        }
        return value
    }
}
