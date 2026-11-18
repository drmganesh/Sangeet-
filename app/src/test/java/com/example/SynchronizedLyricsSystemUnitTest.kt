package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.repository.LyricsDisplayPreferencesSnapshot
import com.example.sangeet.data.repository.LyricsFontStyle
import com.example.sangeet.data.repository.LyricsHighlightPreset
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.scanner.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SynchronizedLyricsSystemUnitTest {

    @Test
    fun parseDocument_handlesEnhancedLrcWordsAndMultiTimestamps() {
        val raw = """
            [ti:Golden Walkman]
            [ar:SANGEET Band]
            [al:AMOLED Tape]
            [00:05.00]<00:05.00>Golden <00:05.50>tape <00:06.10>reels <00:06.80>spin
            [00:10.00][00:40.00]<00:10.00>Chorus <00:10.60>glowing <00:11.20>bright
        """.trimIndent()

        val doc = LrcParser.parseDocument(raw)
        assertEquals(LyricsSyncType.ENHANCED_LRC, doc.syncType)
        assertTrue(doc.isSynchronized)
        assertTrue(doc.isWordSynced)
        assertEquals("Golden Walkman", doc.titleTag)
        assertEquals("SANGEET Band", doc.artistTag)
        assertEquals("AMOLED Tape", doc.albumTag)
        assertEquals(3, doc.lines.size)

        // Sorted chronologically: 5s, 10s, 40s
        assertEquals(5000L, doc.lines[0].timestampMs)
        assertEquals("Golden tape reels spin", doc.lines[0].text)
        assertEquals(4, doc.lines[0].words.size)
        assertEquals(5500L, doc.lines[0].words[1].startMs)

        // Second chorus occurrence rebased at 40s
        assertEquals(40000L, doc.lines[2].timestampMs)
        assertEquals(40600L, doc.lines[2].words[1].startMs)

        // Legacy LrcParser.parse returns clean stripped lines
        val legacyLines = LrcParser.parse(raw)
        assertEquals(3, legacyLines.size)
        assertEquals("Golden tape reels spin", legacyLines[0].text)
    }

    @Test
    fun parseDocument_handlesPlainTextWithoutFakingSync() {
        val plain = """
            First plain verse line
            Second plain verse line
            Third plain verse line
        """.trimIndent()

        val doc = LrcParser.parseDocument(plain)
        assertEquals(LyricsSyncType.PLAIN_TEXT, doc.syncType)
        assertFalse(doc.isSynchronized)
        assertFalse(doc.isWordSynced)
        assertEquals(3, doc.lines.size)
        assertEquals("Second plain verse line", doc.lines[1].text)
    }

    @Test
    fun syncEngine_binarySearchHandlesSeekAndOffsetClamping() {
        val doc = LrcParser.parseDocument(
            """
            [00:05.00]Line Zero
            [00:10.00]<00:10.00>WordOne <00:11.00>WordTwo <00:12.00>WordThree
            [00:20.00]Line Two
            """.trimIndent()
        )

        // Zero offset seeks
        assertEquals(-1, LyricsSyncEngine.findActiveLineIndex(doc.lines, 2000L, 0L))
        assertEquals(0, LyricsSyncEngine.findActiveLineIndex(doc.lines, 5000L, 0L))
        assertEquals(1, LyricsSyncEngine.findActiveLineIndex(doc.lines, 11500L, 0L))
        assertEquals(2, LyricsSyncEngine.findActiveLineIndex(doc.lines, 25000L, 0L))

        // Word-by-word highlighting on line 1 at 11.5s -> WordTwo active
        val line1 = doc.lines[1]
        assertEquals(1, LyricsSyncEngine.findActiveWordIndex(line1, 11500L, 0L))
        assertEquals(1.0f, LyricsSyncEngine.wordFillFraction(line1.words[0], 11500L, 0L), 0.01f)
        assertEquals(0.5f, LyricsSyncEngine.wordFillFraction(line1.words[1], 11500L, 0L), 0.05f)
        assertEquals(0.0f, LyricsSyncEngine.wordFillFraction(line1.words[2], 11500L, 0L), 0.01f)

        // Advance (-1500 ms) makes 10s line activate at 8.5s
        assertEquals(1, LyricsSyncEngine.findActiveLineIndex(doc.lines, 8600L, -1500L))
        // Delay (+2000 ms) makes 10s line wait until 12.0s
        assertEquals(0, LyricsSyncEngine.findActiveLineIndex(doc.lines, 11500L, +2000L))
        assertEquals(1, LyricsSyncEngine.findActiveLineIndex(doc.lines, 12100L, +2000L))

        // Clamping range -10,000 .. +10,000 ms
        assertEquals(-10_000L, LyricsSyncEngine.clampOffset(-99_999L))
        assertEquals(10_000L, LyricsSyncEngine.clampOffset(99_999L))
    }

    @Test
    fun exportToLrc_roundTripsTimestampsAndWords() {
        val original = """
            [00:02.50]<00:02.50>Hello <00:03.20>Cassette
            [00:07.80]Pure Offline Sound
        """.trimIndent()

        val parsed = LrcParser.parseDocument(original)
        val exported = LrcParser.exportToLrc(
            lines = parsed.lines,
            title = "Cassette Song",
            artist = "Ganeswar",
            album = "Walkman",
            offsetMs = -250L
        )

        val reparsed = LrcParser.parseDocument(exported)
        assertEquals(LyricsSyncType.ENHANCED_LRC, reparsed.syncType)
        assertEquals("Cassette Song", reparsed.titleTag)
        assertEquals("Ganeswar", reparsed.artistTag)
        assertEquals(-250L, reparsed.embeddedOffsetMs)
        assertEquals(2, reparsed.lines.size)
        assertEquals(2500L, reparsed.lines[0].timestampMs)
        assertEquals("Hello Cassette", reparsed.lines[0].text)
        assertEquals(7800L, reparsed.lines[1].timestampMs)
    }

    @Test
    fun lyricsManager_persistsLyricsAndOffsetsLocallyPerSong() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = LyricsManager(context)
        val track = AudioTrack(
            id = 88001122L,
            title = "Neon Tape",
            artist = "Retro Artist",
            album = "Midnight Gold",
            embeddedLyrics = "[00:01.00]Embedded initial line"
        )

        // Initial resolution picks embedded lyrics cleanly
        val initial = manager.resolveLyricsDetailed(track)
        assertTrue(initial.hasLyrics)
        assertEquals(LyricsSourceType.EMBEDDED_TAGS, initial.sourceType)
        assertEquals(0L, initial.offsetMs)

        // Save custom edited lyrics & per-song offset
        manager.saveCustomLyrics(track.id, "[00:02.00]Custom synced line", -350L, isManual = true)
        val updated = manager.resolveLyricsDetailed(track)
        assertEquals("[00:02.00]Custom synced line", updated.rawContent)
        assertEquals(-350L, updated.offsetMs)
        assertTrue(updated.isManual)

        // Update offset beyond limit -> clamps to +10000 ms
        manager.updateLyricsOffset(track.id, 15000L)
        val clampedRes = manager.resolveLyricsDetailed(track)
        assertEquals(10_000L, clampedRes.offsetMs)

        // Clear custom override -> cleanly reverts to embedded track lyrics
        manager.clearCustomLyrics(track.id)
        val reverted = manager.resolveLyricsDetailed(track)
        assertEquals("[00:01.00]Embedded initial line", reverted.rawContent)
        assertEquals(0L, reverted.offsetMs)
    }

    @Test
    fun lyricsDisplayPreferences_defaultToSangeetGoldAndPersistAllOptions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository.getInstance(context)
        repo.resetLyricsSettingsToDefault()

        val defaults = LyricsDisplayPreferencesSnapshot()
        assertEquals(LyricsHighlightPreset.SANGEET_GOLD.argbHex, defaults.highlightColorHex)
        assertTrue(defaults.autoScrollEnabled)
        assertTrue(defaults.wordByWordEnabled)

        repo.setLyricsFontSize(24f)
        repo.setLyricsFontStyle(LyricsFontStyle.MONO)
        repo.setLyricsLineSpacing(26f)
        repo.setLyricsHighlightColor(LyricsHighlightPreset.BRIGHT_AMBER.argbHex)
        repo.setLyricsInactiveOpacity(0.55f)
        repo.setLyricsArtworkBlur(22f)
        repo.setLyricsBackgroundDimming(0.80f)
        repo.setLyricsCurrentLineScale(1.22f)
        repo.setLyricsAutoSync(false)
        repo.setLyricsWordByWordEnabled(false)
        repo.setLyricsReducedMotion(true)

        val saved = repo.lyricsPreferences.first()
        assertEquals(24f, saved.fontSizeSp, 0.01f)
        assertEquals(LyricsFontStyle.MONO, saved.fontStyle)
        assertEquals(26f, saved.lineSpacingDp, 0.01f)
        assertEquals(LyricsHighlightPreset.BRIGHT_AMBER.argbHex, saved.highlightColorHex)
        assertEquals(0.55f, saved.inactiveOpacity, 0.01f)
        assertEquals(22f, saved.artworkBlurRadius, 0.01f)
        assertEquals(0.80f, saved.backgroundDimming, 0.01f)
        assertEquals(1.22f, saved.currentLineScale, 0.01f)
        assertFalse(saved.autoScrollEnabled)
        assertFalse(saved.wordByWordEnabled)
        assertTrue(saved.reducedMotion)
    }
}
