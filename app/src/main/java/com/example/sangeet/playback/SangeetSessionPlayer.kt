package com.example.sangeet.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.sangeet.data.repository.NotificationPreferencesSnapshot
import java.util.concurrent.CopyOnWriteArraySet

/**
 * ForwardingPlayer wrapper exposed to MediaSession so notification customization
 * (artist/album/artwork visibility, progress scrubber, lock-screen/privacy modes, prev/next slots)
 * applies instantaneously without modifying ExoPlayer's active MediaItems or interrupting audio/DSP.
 */
@OptIn(UnstableApi::class)
class SangeetSessionPlayer(
    private val exoPlayer: ExoPlayer,
    initialPrefs: NotificationPreferencesSnapshot = NotificationPreferencesSnapshot()
) : ForwardingPlayer(exoPlayer) {

    @Volatile
    var currentPrefs: NotificationPreferencesSnapshot = initialPrefs
        private set

    private val sessionListeners = CopyOnWriteArraySet<Player.Listener>()

    fun updatePreferences(newPrefs: NotificationPreferencesSnapshot) {
        if (currentPrefs == newPrefs) return
        currentPrefs = newPrefs
        val updatedMetadata = mediaMetadata
        val updatedCommands = availableCommands
        sessionListeners.forEach { listener ->
            listener.onMediaMetadataChanged(updatedMetadata)
            listener.onAvailableCommandsChanged(updatedCommands)
        }
    }

    override fun addListener(listener: Player.Listener) {
        sessionListeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        sessionListeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val trackId = exoPlayer.currentMediaItem?.mediaId?.toLongOrNull()
        val formatted = NotificationFormattingHelper.formatMetadata(
            rawTitle = base.title?.toString(),
            rawArtist = base.artist?.toString(),
            rawAlbum = base.albumTitle?.toString(),
            prefs = currentPrefs
        )

        val artUri = if (formatted.showArtwork && trackId != null) {
            NotificationArtworkHelper.buildArtUri(trackId, formatted.artworkSizePx)
        } else {
            null
        }

        return base.buildUpon()
            .setTitle(formatted.title)
            .setDisplayTitle(formatted.title)
            .setArtist(formatted.subtitle.takeIf { it.isNotBlank() })
            .setSubtitle(formatted.subtitle.takeIf { it.isNotBlank() })
            .setAlbumTitle(formatted.albumTitle)
            .setArtworkUri(artUri)
            .build()
    }

    override fun getCurrentMediaItem(): MediaItem? {
        val item = super.getCurrentMediaItem() ?: return null
        return item.buildUpon()
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        val eff = currentPrefs.effective()
        val builder = base.buildUpon()
        if (!eff.showProgress) {
            builder.remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        }
        if (!eff.showPrevNext) {
            builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            builder.remove(Player.COMMAND_SEEK_TO_NEXT)
            builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return availableCommands.contains(command)
    }
}
