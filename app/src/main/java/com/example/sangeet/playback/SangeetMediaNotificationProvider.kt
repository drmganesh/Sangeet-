package com.example.sangeet.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.example.R
import com.example.sangeet.data.repository.LockScreenMediaMode
import com.example.sangeet.data.repository.NotificationPreferencesSnapshot
import com.example.sangeet.data.repository.NotificationPrivacyMode
import com.google.common.collect.ImmutableList

/**
 * Custom Media3 MediaNotification.Provider built atop DefaultMediaNotificationProvider
 * to apply SANGEET's persisted notification settings:
 * - Dynamic button planning (Prev/Next, Seek -10s/+10s, Play/Pause, Stop/Dismiss)
 * - Artist & album line visibility
 * - Lock-screen visibility & HIDE_SENSITIVE publicVersion redaction
 * - Non-flickering FLAG_ONLY_ALERT_ONCE & swipe-to-stop deleteIntent
 */
@OptIn(UnstableApi::class)
class SangeetMediaNotificationProvider(
    private val context: Context,
    private val channelId: String,
    private val channelNameResId: Int,
    initialPrefs: NotificationPreferencesSnapshot = NotificationPreferencesSnapshot()
) : MediaNotification.Provider {

    @Volatile
    var currentPrefs: NotificationPreferencesSnapshot = initialPrefs
        private set

    fun updatePreferences(newPrefs: NotificationPreferencesSnapshot) {
        currentPrefs = newPrefs
    }

    private val seekBackButton: CommandButton by lazy {
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setSessionCommand(SessionCommand(ACTION_SEEK_BACKWARD, Bundle.EMPTY))
            .setCustomIconResId(R.drawable.ic_notif_seek_back)
            .setDisplayName("Rewind 10s")
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private val seekForwardButton: CommandButton by lazy {
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setSessionCommand(SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY))
            .setCustomIconResId(R.drawable.ic_notif_seek_forward)
            .setDisplayName("Forward 10s")
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private val stopDismissButton: CommandButton by lazy {
        CommandButton.Builder(CommandButton.ICON_STOP)
            .setSessionCommand(SessionCommand(ACTION_STOP_DISMISS, Bundle.EMPTY))
            .setCustomIconResId(R.drawable.ic_notif_stop)
            .setDisplayName("Stop")
            .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD_SECONDARY)
            .build()
    }

    fun buildSessionCustomLayout(prefs: NotificationPreferencesSnapshot = currentPrefs): ImmutableList<CommandButton> {
        val eff = prefs.effective()
        val list = ImmutableList.builder<CommandButton>()
        if (eff.showSeekButtons) {
            list.add(seekBackButton)
            list.add(seekForwardButton)
        }
        if (eff.showStopButton) {
            list.add(stopDismissButton)
        }
        return list.build()
    }

    private val delegate = object : DefaultMediaNotificationProvider(
        context,
        NotificationIdProvider { NOTIFICATION_ID },
        channelId,
        channelNameResId
    ) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            // Let DefaultMediaNotificationProvider construct canonical Play/Pause, Prev, Next buttons
            val standardButtons = super.getMediaButtons(
                session,
                playerCommands,
                ImmutableList.of(),
                showPauseButton
            )

            val prevBtn = standardButtons.firstOrNull {
                it.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    it.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            }
            val playPauseBtn = standardButtons.firstOrNull {
                it.playerCommand == Player.COMMAND_PLAY_PAUSE
            }
            val nextBtn = standardButtons.firstOrNull {
                it.playerCommand == Player.COMMAND_SEEK_TO_NEXT ||
                    it.playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
            }

            val slots = NotificationFormattingHelper.planActionSlots(currentPrefs)
            val result = ImmutableList.builder<CommandButton>()
            for (slot in slots) {
                when (slot) {
                    NotificationActionSlot.PREVIOUS -> prevBtn?.let { result.add(it) }
                    NotificationActionSlot.SEEK_BACK -> result.add(seekBackButton)
                    NotificationActionSlot.PLAY_PAUSE -> playPauseBtn?.let { result.add(it) }
                    NotificationActionSlot.SEEK_FORWARD -> result.add(seekForwardButton)
                    NotificationActionSlot.NEXT -> nextBtn?.let { result.add(it) }
                    NotificationActionSlot.STOP -> result.add(stopDismissButton)
                }
            }
            return result.build()
        }

        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: ImmutableList<CommandButton>,
            builder: NotificationCompat.Builder,
            actionFactory: MediaNotification.ActionFactory
        ): IntArray {
            super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
            val slots = NotificationFormattingHelper.planActionSlots(currentPrefs)
            return NotificationFormattingHelper.compactIndices(slots)
        }

        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
            return metadata.title ?: "SANGEET Audio"
        }

        override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
            return metadata.artist ?: metadata.subtitle
        }
    }.apply {
        setSmallIcon(R.drawable.ic_notif_sangeet)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val wrappedCallback = MediaNotification.Provider.Callback { updated ->
            val processed = postProcessNotification(updated)
            onNotificationChangedCallback.onNotificationChanged(processed)
        }
        val initial = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            wrappedCallback
        )
        return postProcessNotification(initial)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        return delegate.handleCustomCommand(session, action, extras)
    }

    private fun postProcessNotification(mediaNotification: MediaNotification): MediaNotification {
        val notif = mediaNotification.notification
        val eff = currentPrefs.effective()

        notif.flags = notif.flags or Notification.FLAG_ONLY_ALERT_ONCE
        notif.deleteIntent = createStopDismissPendingIntent()

        val visibility = when {
            eff.privacyMode == NotificationPrivacyMode.PRIVATE ||
                eff.lockScreenMode == LockScreenMediaMode.HIDDEN -> NotificationCompat.VISIBILITY_SECRET
            eff.privacyMode == NotificationPrivacyMode.HIDE_SENSITIVE -> NotificationCompat.VISIBILITY_PRIVATE
            else -> NotificationCompat.VISIBILITY_PUBLIC
        }
        notif.visibility = visibility

        if (visibility == NotificationCompat.VISIBILITY_PRIVATE) {
            notif.publicVersion = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notif_sangeet)
                .setContentTitle("SANGEET")
                .setContentText("Playing offline audio")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .build()
        } else {
            notif.publicVersion = null
        }

        return mediaNotification
    }

    private fun createStopDismissPendingIntent(): PendingIntent {
        val stopIntent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_STOP_DISMISS
        }
        return PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_SEEK_BACKWARD = "com.example.sangeet.ACTION_SEEK_BACKWARD"
        const val ACTION_SEEK_FORWARD = "com.example.sangeet.ACTION_SEEK_FORWARD"
        const val ACTION_STOP_DISMISS = "com.example.sangeet.ACTION_STOP_DISMISS"
        private const val REQUEST_CODE_STOP = 4102
    }
}
