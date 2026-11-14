package com.example

import com.example.sangeet.scanner.EmbeddedArtworkProbe
import com.example.sangeet.ui.components.ArtworkPathCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmbeddedArtworkProbeUnitTest {

    @Before
    fun setUp() {
        ArtworkPathCache.clear()
    }

    @Test
    fun wavHeaderReturnsFalse() {
        val wav = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray(Charsets.US_ASCII))
        wav.putInt(36)
        wav.put("WAVE".toByteArray(Charsets.US_ASCII))
        wav.put("fmt ".toByteArray(Charsets.US_ASCII))
        wav.putInt(16)
        assertFalse(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(wav.array()))
    }

    @Test
    fun rawMp3FramesWithoutId3ReturnsFalse() {
        val raw = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00, 0x11, 0x22, 0x33, 0x44)
        assertFalse(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(raw))
    }

    @Test
    fun id3v23WithoutApicReturnsFalse() {
        val framePayload = "Test Title".toByteArray(Charsets.US_ASCII)
        val tagBody = ByteArrayOutputStream().apply {
            write("TIT2".toByteArray(Charsets.US_ASCII))
            write(bigEndian4(framePayload.size))
            write(byteArrayOf(0, 0))
            write(framePayload)
        }.toByteArray()

        val id3 = wrapId3v2(major = 3, body = tagBody)
        assertFalse(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(id3))
    }

    @Test
    fun id3v23WithApicReturnsTrue() {
        val tit2Payload = "Title".toByteArray(Charsets.US_ASCII)
        val apicPayload = byteArrayOf(0, 0x69, 0x6D, 0x61, 0x67, 0x65, 0, 3, 0, 0xFF.toByte(), 0xD8.toByte())
        val tagBody = ByteArrayOutputStream().apply {
            write("TIT2".toByteArray(Charsets.US_ASCII))
            write(bigEndian4(tit2Payload.size))
            write(byteArrayOf(0, 0))
            write(tit2Payload)

            write("APIC".toByteArray(Charsets.US_ASCII))
            write(bigEndian4(apicPayload.size))
            write(byteArrayOf(0, 0))
            write(apicPayload)
        }.toByteArray()

        val id3 = wrapId3v2(major = 3, body = tagBody)
        assertTrue(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(id3))
    }

    @Test
    fun id3v22WithPicReturnsTrue() {
        val picPayload = byteArrayOf(0, 0x50, 0x4E, 0x47, 3, 0, 0x89.toByte(), 0x50)
        val tagBody = ByteArrayOutputStream().apply {
            write("PIC".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(0, 0, picPayload.size.toByte()))
            write(picPayload)
        }.toByteArray()

        val id3 = wrapId3v2(major = 2, body = tagBody)
        assertTrue(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(id3))
    }

    @Test
    fun flacWithAndWithoutPictureBlock() {
        val noPic = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(Charsets.US_ASCII))
            // STREAMINFO (type 0, isLast = 1 -> 0x80), length = 4
            write(byteArrayOf(0x80.toByte(), 0, 0, 4))
            write(byteArrayOf(1, 2, 3, 4))
        }.toByteArray()
        assertFalse(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(noPic))

        val withPic = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(Charsets.US_ASCII))
            // STREAMINFO (type 0, isLast = 0), length = 4
            write(byteArrayOf(0x00, 0, 0, 4))
            write(byteArrayOf(1, 2, 3, 4))
            // PICTURE (type 6, isLast = 1 -> 0x86), length = 8
            write(byteArrayOf(0x86.toByte(), 0, 0, 8))
            write(ByteArray(8) { 1 })
        }.toByteArray()
        assertTrue(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(withPic))
    }

    @Test
    fun mp4WithAndWithoutCovrAtom() {
        val ftyp = mp4Box("ftyp", "M4A ".toByteArray(Charsets.US_ASCII) + ByteArray(8))
        val nameBox = mp4Box("\u00A9nam", ByteArray(8))
        val covrBox = mp4Box("covr", ByteArray(12))

        val ilstWithoutCovr = mp4Box("ilst", nameBox)
        val metaWithoutCovr = mp4Box("meta", ByteArray(4) + ilstWithoutCovr) // FullBox 4-byte flags
        val moovWithoutCovr = mp4Box("moov", mp4Box("udta", metaWithoutCovr))
        assertFalse(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(ftyp + moovWithoutCovr))

        val ilstWithCovr = mp4Box("ilst", nameBox + covrBox)
        val metaWithCovr = mp4Box("meta", ByteArray(4) + ilstWithCovr)
        val moovWithCovr = mp4Box("moov", mp4Box("udta", metaWithCovr))
        assertTrue(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(ftyp + moovWithCovr))

        val metaNonFullBoxWithCovr = mp4Box("meta", ilstWithCovr)
        val moovNonFullBox = mp4Box("moov", mp4Box("udta", metaNonFullBoxWithCovr))
        assertTrue(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(ftyp + moovNonFullBox))
    }

    @Test
    fun oggWithAndWithoutPictureComment() {
        val noPic = "OggS\u0000\u0002vorbis\u0000TITLE=Demo".toByteArray(Charsets.ISO_8859_1)
        val withPic = "OggS\u0000\u0002vorbis\u0000METADATA_BLOCK_PICTURE=AAAA".toByteArray(Charsets.ISO_8859_1)
        assertFalse(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(noPic))
        assertTrue(EmbeddedArtworkProbe.hasEmbeddedArtworkInBytes(withPic))
    }

    @Test
    fun artworkPathCacheTracksNegativeAndPositiveEntries() {
        assertFalse(ArtworkPathCache.isKnownMissing(10L))
        ArtworkPathCache.markNoArtwork(10L)
        assertTrue(ArtworkPathCache.isKnownMissing(10L))
        assertNull(ArtworkPathCache.get(10L))

        // Saving real artwork clears negative marker
        ArtworkPathCache.put(10L, "/tmp/cover.jpg")
        assertFalse(ArtworkPathCache.isKnownMissing(10L))
        assertEquals("/tmp/cover.jpg", ArtworkPathCache.get(10L))

        // markNoArtwork must not overwrite existing valid path
        ArtworkPathCache.markNoArtwork(10L)
        assertFalse(ArtworkPathCache.isKnownMissing(10L))
        assertEquals("/tmp/cover.jpg", ArtworkPathCache.get(10L))

        ArtworkPathCache.clear()
        assertFalse(ArtworkPathCache.isKnownMissing(10L))
        assertNull(ArtworkPathCache.get(10L))
    }

    private fun wrapId3v2(major: Int, body: ByteArray): ByteArray {
        val size = body.size
        val syncsafe = byteArrayOf(
            ((size shr 21) and 0x7F).toByte(),
            ((size shr 14) and 0x7F).toByte(),
            ((size shr 7) and 0x7F).toByte(),
            (size and 0x7F).toByte()
        )
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(Charsets.US_ASCII))
            write(major)
            write(0)
            write(0)
            write(syncsafe)
            write(body)
        }.toByteArray()
    }

    private fun bigEndian4(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val total = 8 + payload.size
        return ByteArrayOutputStream().apply {
            write(bigEndian4(total))
            write(type.toByteArray(Charsets.ISO_8859_1).copyOf(4))
            write(payload)
        }.toByteArray()
    }
}
