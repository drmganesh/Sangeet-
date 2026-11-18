package com.example.sangeet.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.sangeet.data.repository.NotificationPreferencesSnapshot
import com.example.sangeet.data.repository.SettingsRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private lateinit var playbackManager: PlaybackManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var artworkHelper: NotificationArtworkHelper
    private lateinit var notificationProvider: SangeetMediaNotificationProvider
    private lateinit var sessionPlayer: SangeetSessionPlayer

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        playbackManager = PlaybackManager.getInstance(applicationContext)
        settingsRepository = SettingsRepository.getInstance(applicationContext)
        artworkHelper = NotificationArtworkHelper(applicationContext)

        val initialPrefs = runCatching {
            runBlocking(Dispatchers.IO) { settingsRepository.notificationPreferences.first() }
        }.getOrDefault(NotificationPreferencesSnapshot())

        notificationProvider = SangeetMediaNotificationProvider(
            context = this,
            channelId = CHANNEL_ID,
            channelNameResId = R.string.playback_channel_name,
            initialPrefs = initialPrefs
        )
        setMediaNotificationProvider(notificationProvider)

        sessionPlayer = SangeetSessionPlayer(
            exoPlayer = playbackManager.getPlayer(),
            initialPrefs = initialPrefs,
            onPlayOverride = { playbackManager.play() },
            onPauseOverride = { playbackManager.pause() },
            onNextOverride = { playbackManager.skipToNext() },
            onPrevOverride = { playbackManager.skipToPrevious() },
            onSeekOverride = { posMs -> playbackManager.seekTo(posMs) }
        )

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setId(SESSION_ID)
            .setBitmapLoader(artworkHelper)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(SangeetSessionCallback())
            .setCustomLayout(notificationProvider.buildSessionCustomLayout(initialPrefs))
            .build()

        playbackManager.onActivePlayerSwapped = { newActiveExo ->
            val activeSession = mediaSession
            if (activeSession != null) {
                val updatedSessionPlayer = SangeetSessionPlayer(
                    exoPlayer = newActiveExo,
                    initialPrefs = sessionPlayer.currentPrefs,
                    onPlayOverride = { playbackManager.play() },
                    onPauseOverride = { playbackManager.pause() },
                    onNextOverride = { playbackManager.skipToNext() },
                    onPrevOverride = { playbackManager.skipToPrevious() },
                    onSeekOverride = { posMs -> playbackManager.seekTo(posMs) }
                )
                sessionPlayer = updatedSessionPlayer
                activeSession.player = updatedSessionPlayer
                onUpdateNotification(activeSession)
            }
        }

        activeServiceInstance = this
        observeNotificationPreferences()
    }

    private fun observeNotificationPreferences() {
        serviceScope.launch {
            settingsRepository.notificationPreferences.collect { prefs ->
                notificationProvider.updatePreferences(prefs)
                sessionPlayer.updatePreferences(prefs)
                mediaSession?.let { session ->
                    session.setCustomLayout(notificationProvider.buildSessionCustomLayout(prefs))
                    if (sessionPlayer.mediaItemCount > 0) {
                        onUpdateNotification(session)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.playback_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == SangeetMediaNotificationProvider.ACTION_STOP_DISMISS) {
            stopAndDismissNotification()
            return START_NOT_STICKY
        }

        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.getBooleanExtra(EXTRA_START_FOREGROUND, false) == true) {
            mediaSession?.let { session ->
                try {
                    onUpdateNotification(session, true)
                } catch (e: Exception) {
                    Log.w(TAG, "Deferred foreground notification promotion: ${e.message}")
                }
            }
        }
        return result
    }

    fun stopAndDismissNotification() {
        try {
            playbackManager.stopAndResetFromNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel(SangeetMediaNotificationProvider.NOTIFICATION_ID)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback service", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopAndDismissNotification()
        }
    }

    override fun onDestroy() {
        playbackManager.onActivePlayerSwapped = null
        if (activeServiceInstance === this) {
            activeServiceInstance = null
        }
        serviceScope.cancel()
        artworkHelper.clearCache()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class SangeetSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult {
            val sessionCommands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(SangeetMediaNotificationProvider.ACTION_SEEK_BACKWARD, Bundle.EMPTY))
                .add(SessionCommand(SangeetMediaNotificationProvider.ACTION_SEEK_FORWARD, Bundle.EMPTY))
                .add(SessionCommand(SangeetMediaNotificationProvider.ACTION_STOP_DISMISS, Bundle.EMPTY))
                .build()

            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(notificationProvider.buildSessionCustomLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                SangeetMediaNotificationProvider.ACTION_SEEK_BACKWARD -> {
                    playbackManager.seekBackward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                SangeetMediaNotificationProvider.ACTION_SEEK_FORWARD -> {
                    playbackManager.seekForward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                SangeetMediaNotificationProvider.ACTION_STOP_DISMISS -> {
                    stopAndDismissNotification()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_KEY_EVENT,
                KeyEvent::class.java
            )
            if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        playbackManager.skipToNext()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        playbackManager.skipToPrevious()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        playbackManager.seekForward()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        playbackManager.seekBackward()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        stopAndDismissNotification()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaItemsWithStartPosition>()
            serviceScope.launch(Dispatchers.Main) {
                try {
                    val resumed = playbackManager.loadResumableMediaItemsWithStartPosition()
                    if (resumed != null) {
                        future.set(resumed)
                    } else {
                        future.setException(IllegalStateException("No resumable offline tracks available"))
                    }
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val SESSION_ID = "sangeet_main_session"
        const val CHANNEL_ID = "sangeet_playback_channel"
        const val EXTRA_START_FOREGROUND = "extra_start_foreground"
        const val EXTRA_OPEN_PLAYER = "extra_open_player"

        @Volatile
        var activeServiceInstance: PlaybackService? = null
            private set
    }
}
