package com.example.sangeet.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.*
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.PlaybackHistory
import com.example.sangeet.data.model.PlayerStyle
import com.example.sangeet.dsp.EqualizerAudioProcessor
import com.example.sangeet.dsp.SpatialAudioProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

class PlaybackManager private constructor(private val context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db = AppDatabase.getInstance(context)

    val equalizerProcessor = EqualizerAudioProcessor()
    val spatialProcessor = SpatialAudioProcessor()

    private var exoPlayer: ExoPlayer

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val queue: StateFlow<List<AudioTrack>> = _queue.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    // 0: OFF, 1: ALL, 2: ONE
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _sleepTimerSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerSeconds: StateFlow<Int?> = _sleepTimerSeconds.asStateFlow()

    private val _playerStyle = MutableStateFlow(PlayerStyle.CLASSIC)
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var progressUpdateJob: Job? = null
    private var nativeEqualizer: Equalizer? = null

    init {
        val audioSink: AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(equalizerProcessor, spatialProcessor))
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return audioSink
            }
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        setupPlayerListeners()
        startProgressUpdater()
    }

    fun getPlayer(): ExoPlayer = exoPlayer

    private fun setupPlayerListeners() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPlaybackService()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId?.toLongOrNull()
                if (mediaId != null) {
                    val track = _queue.value.find { it.id == mediaId }
                    if (track != null) {
                        _currentTrack.value = track
                        recordTrackHistory(track)
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachNativeEffects(audioSessionId)
            }
        })
    }

    private fun attachNativeEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            nativeEqualizer?.release()
            nativeEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Native hardware Equalizer unavailable; custom 15-band DSP active.", e)
        }
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startForegroundService(intent)
        } catch (_: Exception) {}
    }

    private fun startProgressUpdater() {
        progressUpdateJob?.cancel()
        progressUpdateJob = applicationScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    _currentPositionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration
                    if (dur > 0) _durationMs.value = dur
                }
                delay(300L)
            }
        }
    }

    fun playTrack(track: AudioTrack, newQueue: List<AudioTrack>? = null) {
        val queueToSet = newQueue ?: if (_queue.value.isEmpty()) listOf(track) else _queue.value
        _queue.value = queueToSet
        _currentTrack.value = track

        val mediaItems = queueToSet.map { t ->
            val uri = if (t.contentUri.isNotBlank()) Uri.parse(t.contentUri) else Uri.parse(t.path)
            MediaItem.Builder()
                .setMediaId(t.id.toString())
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setAlbumTitle(t.album)
                        .build()
                )
                .build()
        }

        val startIndex = queueToSet.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        exoPlayer.setMediaItems(mediaItems, startIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
        _isPlaying.value = true

        recordTrackHistory(track)
    }

    private fun recordTrackHistory(track: AudioTrack) {
        applicationScope.launch(Dispatchers.IO) {
            db.audioTrackDao().incrementPlayCount(track.id)
            db.historyAndStatsDao().insertPlaybackHistory(
                PlaybackHistory(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs
                )
            )
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            _isPlaying.value = false
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE && _currentTrack.value != null) {
                playTrack(_currentTrack.value!!)
            } else {
                exoPlayer.play()
                _isPlaying.value = true
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        _currentPositionMs.value = positionMs
    }

    fun playNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && _queue.value.isNotEmpty()) {
            exoPlayer.seekTo(0, 0L)
            exoPlayer.play()
        }
    }

    fun playPrevious() {
        if (exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0L)
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.play()
        } else {
            exoPlayer.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        exoPlayer.shuffleModeEnabled = newShuffle
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = nextMode
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        _playbackSpeed.value = clamped
        exoPlayer.setPlaybackSpeed(clamped)
    }

    fun setPlayerStyle(style: PlayerStyle) {
        _playerStyle.value = style
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerSeconds.value = null
            return
        }

        var remaining = minutes * 60
        _sleepTimerSeconds.value = remaining

        sleepTimerJob = applicationScope.launch {
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _sleepTimerSeconds.value = remaining
            }
            // Timer expired -> pause playback
            exoPlayer.pause()
            _isPlaying.value = false
            _sleepTimerSeconds.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerSeconds.value = null
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val currentList = _queue.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            Collections.swap(currentList, fromIndex, toIndex)
            _queue.value = currentList
            exoPlayer.moveMediaItem(fromIndex, toIndex)
        }
    }

    fun removeFromQueue(index: Int) {
        val currentList = _queue.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _queue.value = currentList
            exoPlayer.removeMediaItem(index)
        }
    }

    fun addToQueue(track: AudioTrack) {
        val currentList = _queue.value.toMutableList()
        currentList.add(track)
        _queue.value = currentList

        val uri = if (track.contentUri.isNotBlank()) Uri.parse(track.contentUri) else Uri.parse(track.path)
        val item = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()
        exoPlayer.addMediaItem(item)
    }

    fun playNextInQueue(track: AudioTrack) {
        val currentIdx = exoPlayer.currentMediaItemIndex
        val targetIdx = (currentIdx + 1).coerceAtMost(_queue.value.size)

        val currentList = _queue.value.toMutableList()
        currentList.add(targetIdx, track)
        _queue.value = currentList

        val uri = if (track.contentUri.isNotBlank()) Uri.parse(track.contentUri) else Uri.parse(track.path)
        val item = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()
        exoPlayer.addMediaItem(targetIdx, item)
    }

    private fun handleTrackEnded() {
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
        } else if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && _queue.value.isNotEmpty()) {
            exoPlayer.seekTo(0, 0L)
            exoPlayer.play()
        } else {
            _isPlaying.value = false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaybackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
