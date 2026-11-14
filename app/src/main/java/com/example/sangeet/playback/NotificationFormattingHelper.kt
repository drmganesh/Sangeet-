package com.example.sangeet.playback

import com.example.sangeet.data.repository.LockScreenMediaMode
import com.example.sangeet.data.repository.NotificationPreferencesSnapshot
import com.example.sangeet.data.repository.NotificationPrivacyMode

enum class NotificationActionSlot {
    PREVIOUS,
    SEEK_BACK,
    PLAY_PAUSE,
    SEEK_FORWARD,
    NEXT,
    STOP
}

data class FormattedNotificationMetadata(
    val title: String,
    val subtitle: String,
    val albumTitle: String?,
    val showArtwork: Boolean,
    val artworkSizePx: Int,
    val redactOnLockScreen: Boolean,
    val hideCompletelyOnLockScreen: Boolean
)

/**
 * Pure formatting and button planning logic for SANGEET's Now Playing notification,
 * lock-screen presentation, and notification preview UI.
 */
object NotificationFormattingHelper {

    fun formatMetadata(
        rawTitle: String?,
        rawArtist: String?,
        rawAlbum: String?,
        prefs: NotificationPreferencesSnapshot
    ): FormattedNotificationMetadata {
        val eff = prefs.effective()
        val safeTitle = rawTitle?.takeIf { it.isNotBlank() } ?: "SANGEET Audio"
        val safeArtist = rawArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val safeAlbum = rawAlbum?.takeIf { it.isNotBlank() }

        if (eff.privacyMode == NotificationPrivacyMode.PRIVATE) {
            return FormattedNotificationMetadata(
                title = "SANGEET Audio",
                subtitle = "Playing offline media",
                albumTitle = null,
                showArtwork = false,
                artworkSizePx = eff.artworkSize.pixelSize,
                redactOnLockScreen = true,
                hideCompletelyOnLockScreen = true
            )
        }

        if (eff.lockScreenMode == LockScreenMediaMode.BASIC) {
            return FormattedNotificationMetadata(
                title = safeTitle,
                subtitle = "",
                albumTitle = null,
                showArtwork = eff.showArtwork,
                artworkSizePx = eff.artworkSize.pixelSize,
                redactOnLockScreen = eff.privacyMode == NotificationPrivacyMode.HIDE_SENSITIVE,
                hideCompletelyOnLockScreen = false
            )
        }

        val subtitleParts = buildList {
            if (eff.showArtist) add(safeArtist)
            if (eff.showAlbum && safeAlbum != null) add(safeAlbum)
        }

        return FormattedNotificationMetadata(
            title = safeTitle,
            subtitle = subtitleParts.joinToString(" • "),
            albumTitle = if (eff.showAlbum) safeAlbum else null,
            showArtwork = eff.showArtwork,
            artworkSizePx = eff.artworkSize.pixelSize,
            redactOnLockScreen = eff.privacyMode == NotificationPrivacyMode.HIDE_SENSITIVE,
            hideCompletelyOnLockScreen = eff.lockScreenMode == LockScreenMediaMode.HIDDEN
        )
    }

    /**
     * Plans up to 5 expanded media notification actions ordered intuitively,
     * preserving Play/Pause unconditionally.
     */
    fun planActionSlots(prefs: NotificationPreferencesSnapshot): List<NotificationActionSlot> {
        val eff = prefs.effective()
        val slots = mutableListOf<NotificationActionSlot>()

        if (eff.showPrevNext) slots.add(NotificationActionSlot.PREVIOUS)
        if (eff.showSeekButtons) slots.add(NotificationActionSlot.SEEK_BACK)
        slots.add(NotificationActionSlot.PLAY_PAUSE)
        if (eff.showSeekButtons) slots.add(NotificationActionSlot.SEEK_FORWARD)
        if (eff.showPrevNext) slots.add(NotificationActionSlot.NEXT)
        if (eff.showStopButton) slots.add(NotificationActionSlot.STOP)

        // Android MediaStyle notification renders at most 5 action buttons cleanly.
        if (slots.size > 5) {
            // Keep Previous, Play/Pause, Next, Seek Forward (+10s), and Stop/Dismiss.
            return listOf(
                NotificationActionSlot.PREVIOUS,
                NotificationActionSlot.PLAY_PAUSE,
                NotificationActionSlot.NEXT,
                NotificationActionSlot.SEEK_FORWARD,
                NotificationActionSlot.STOP
            )
        }
        return slots
    }

    /**
     * Picks up to 3 compact-view action indices (always including Play/Pause).
     */
    fun compactIndices(slots: List<NotificationActionSlot>): IntArray {
        val playIndex = slots.indexOf(NotificationActionSlot.PLAY_PAUSE)
        if (playIndex < 0) return intArrayOf(0)

        val chosen = mutableListOf<Int>()
        val prevIdx = slots.indexOf(NotificationActionSlot.PREVIOUS)
        val seekBackIdx = slots.indexOf(NotificationActionSlot.SEEK_BACK)
        val nextIdx = slots.indexOf(NotificationActionSlot.NEXT)
        val seekFwdIdx = slots.indexOf(NotificationActionSlot.SEEK_FORWARD)
        val stopIdx = slots.indexOf(NotificationActionSlot.STOP)

        when {
            prevIdx >= 0 -> chosen.add(prevIdx)
            seekBackIdx >= 0 -> chosen.add(seekBackIdx)
        }
        chosen.add(playIndex)
        when {
            nextIdx >= 0 -> chosen.add(nextIdx)
            seekFwdIdx >= 0 -> chosen.add(seekFwdIdx)
            stopIdx >= 0 -> chosen.add(stopIdx)
        }
        return chosen.distinct().sorted().take(3).toIntArray()
    }
}
