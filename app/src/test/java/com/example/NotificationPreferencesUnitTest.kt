package com.example

import com.example.sangeet.data.repository.LockScreenMediaMode
import com.example.sangeet.data.repository.NotificationArtworkSize
import com.example.sangeet.data.repository.NotificationPreferencesSnapshot
import com.example.sangeet.data.repository.NotificationPrivacyMode
import com.example.sangeet.playback.NotificationActionSlot
import com.example.sangeet.playback.NotificationFormattingHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferencesUnitTest {

    @Test
    fun formatMetadata_defaultPublicFullShowsAllFields() {
        val prefs = NotificationPreferencesSnapshot()
        val formatted = NotificationFormattingHelper.formatMetadata(
            rawTitle = "Naina",
            rawArtist = "Arijit Singh",
            rawAlbum = "SANGEET Acoustic",
            prefs = prefs
        )

        assertEquals("Naina", formatted.title)
        assertEquals("Arijit Singh • SANGEET Acoustic", formatted.subtitle)
        assertEquals("SANGEET Acoustic", formatted.albumTitle)
        assertTrue(formatted.showArtwork)
        assertEquals(512, formatted.artworkSizePx)
        assertFalse(formatted.redactOnLockScreen)
        assertFalse(formatted.hideCompletelyOnLockScreen)
    }

    @Test
    fun formatMetadata_respectsArtistAndAlbumToggles() {
        val noAlbum = NotificationFormattingHelper.formatMetadata(
            rawTitle = "Naina",
            rawArtist = "Arijit Singh",
            rawAlbum = "SANGEET Acoustic",
            prefs = NotificationPreferencesSnapshot(showAlbum = false)
        )
        assertEquals("Arijit Singh", noAlbum.subtitle)
        assertNull(noAlbum.albumTitle)

        val noArtistOrAlbum = NotificationFormattingHelper.formatMetadata(
            rawTitle = "Naina",
            rawArtist = "Arijit Singh",
            rawAlbum = "SANGEET Acoustic",
            prefs = NotificationPreferencesSnapshot(showArtist = false, showAlbum = false)
        )
        assertEquals("", noArtistOrAlbum.subtitle)
    }

    @Test
    fun formatMetadata_privateModeMasksPersonalDetails() {
        val prefs = NotificationPreferencesSnapshot(
            privacyMode = NotificationPrivacyMode.PRIVATE,
            showArtwork = true
        )
        val formatted = NotificationFormattingHelper.formatMetadata(
            rawTitle = "Secret Demo",
            rawArtist = "Private Artist",
            rawAlbum = "Unreleased",
            prefs = prefs
        )

        assertEquals("SANGEET Audio", formatted.title)
        assertEquals("Playing offline media", formatted.subtitle)
        assertNull(formatted.albumTitle)
        assertFalse(formatted.showArtwork)
        assertTrue(formatted.redactOnLockScreen)
        assertTrue(formatted.hideCompletelyOnLockScreen)
    }

    @Test
    fun formatMetadata_hideSensitiveAndLockScreenBasicWorkProperly() {
        val hideSensitive = NotificationFormattingHelper.formatMetadata(
            rawTitle = "Midnight",
            rawArtist = "Artist",
            rawAlbum = "Album",
            prefs = NotificationPreferencesSnapshot(privacyMode = NotificationPrivacyMode.HIDE_SENSITIVE)
        )
        assertTrue(hideSensitive.redactOnLockScreen)
        assertFalse(hideSensitive.hideCompletelyOnLockScreen)

        val basicLock = NotificationFormattingHelper.formatMetadata(
            rawTitle = "Midnight",
            rawArtist = "Artist",
            rawAlbum = "Album",
            prefs = NotificationPreferencesSnapshot(lockScreenMode = LockScreenMediaMode.BASIC)
        )
        assertEquals("Midnight", basicLock.title)
        assertEquals("", basicLock.subtitle)
        assertNull(basicLock.albumTitle)
    }

    @Test
    fun customizationDisabledFallsBackToDefaultsPreservingSavedToggles() {
        val savedCustom = NotificationPreferencesSnapshot(
            customizationEnabled = false,
            showArtwork = false,
            showArtist = false,
            privacyMode = NotificationPrivacyMode.PRIVATE,
            artworkSize = NotificationArtworkSize.SMALL
        )
        val formatted = NotificationFormattingHelper.formatMetadata(
            rawTitle = "Song",
            rawArtist = "Artist",
            rawAlbum = "Album",
            prefs = savedCustom
        )
        assertEquals("Song", formatted.title)
        assertEquals("Artist • Album", formatted.subtitle)
        assertTrue(formatted.showArtwork)
        assertEquals(512, formatted.artworkSizePx)
    }

    @Test
    fun planActionSlots_neverExceedsFiveAndIncludesPlayPause() {
        val allOn = NotificationFormattingHelper.planActionSlots(
            NotificationPreferencesSnapshot(
                showPrevNext = true,
                showSeekButtons = true,
                showStopButton = true
            )
        )
        assertTrue(allOn.size <= 5)
        assertTrue(allOn.contains(NotificationActionSlot.PLAY_PAUSE))
        assertTrue(allOn.contains(NotificationActionSlot.STOP))

        val noStop = NotificationFormattingHelper.planActionSlots(
            NotificationPreferencesSnapshot(
                showPrevNext = true,
                showSeekButtons = true,
                showStopButton = false
            )
        )
        assertEquals(
            listOf(
                NotificationActionSlot.PREVIOUS,
                NotificationActionSlot.SEEK_BACK,
                NotificationActionSlot.PLAY_PAUSE,
                NotificationActionSlot.SEEK_FORWARD,
                NotificationActionSlot.NEXT
            ),
            noStop
        )

        val compact = NotificationFormattingHelper.compactIndices(noStop)
        assertEquals(listOf(0, 2, 4), compact.toList())
    }
}
