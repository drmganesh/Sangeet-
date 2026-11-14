package com.example.sangeet.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.*
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.data.rules.RuleFilterEngine
import com.example.sangeet.dsp.EqualizerAudioProcessor
import com.example.sangeet.dsp.SpatialAudioProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Collections

class PlaybackManager private constructor(private val context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db = AppDatabase.getInstance(context)
    val settingsRepository = SettingsRepository.getInstance(context)

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

    private val _cassetteStyle = MutableStateFlow(CassetteStyle.CLASSIC_BLACK)
    val cassetteStyle: StateFlow<CassetteStyle> = _cassetteStyle.asStateFlow()

    private val _walkmanMechanicalSounds = MutableStateFlow(false)
    val walkmanMechanicalSounds: StateFlow<Boolean> = _walkmanMechanicalSounds.asStateFlow()

    private val _isStopped = MutableStateFlow(false)
    val isStopped: StateFlow<Boolean> = _isStopped.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var progressUpdateJob: Job? = null
    private var nativeEqualizer: Equalizer? = null

    @Volatile private var cachedBlacklist: List<BlacklistEntry> = emptyList()
    @Volatile private var cachedWhitelist: List<WhitelistEntry> = emptyList()
    @Volatile private var cachedFolderRules: List<FolderRule> = emptyList()

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
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        setupPlayerListeners()
        startProgressUpdater()
        restorePersistedSettings()
        observeRuleChanges()
        restoreLastPlaybackSessionIfEmpty()
    }

    private fun observeRuleChanges() {
        applicationScope.launch {
            combine(
                db.blacklistDao().getAllBlacklist(),
                db.whitelistDao().getAllWhitelist(),
                db.folderRuleDao().getAllFolderRules()
            ) { bl, wl, fr -> Triple(bl, wl, fr) }
                .collect { (bl, wl, fr) ->
                    cachedBlacklist = bl
                    cachedWhitelist = wl
                    cachedFolderRules = fr
                    scrubExcludedFromQueue()
                }
        }
    }

    private fun scrubExcludedFromQueue() {
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        val allowedIds = RuleFilterEngine.filterAudioTracks(
            currentQueue, cachedBlacklist, cachedWhitelist, cachedFolderRules
        ).map { it.id }.toSet()

        if (allowedIds.size == currentQueue.size) return

        // Remove disallowed items from back to front so exoPlayer indices stay valid
        val mutableQueue = currentQueue.toMutableList()
        var currentWasRemoved = false
        val currentPlayingId = _currentTrack.value?.id

        for (i in mutableQueue.indices.reversed()) {
            val t = mutableQueue[i]
            if (t.id !in allowedIds) {
                if (t.id == currentPlayingId) {
                    currentWasRemoved = true
                }
                mutableQueue.removeAt(i)
                if (i < exoPlayer.mediaItemCount) {
                    exoPlayer.removeMediaItem(i)
                }
            }
        }
        _queue.value = mutableQueue
        if (mutableQueue.isEmpty()) {
            exoPlayer.stop()
            _currentTrack.value = null
            _isPlaying.value = false
        } else if (currentWasRemoved) {
            val newIdx = exoPlayer.currentMediaItemIndex.coerceIn(0, mutableQueue.lastIndex)
            _currentTrack.value = mutableQueue.getOrNull(newIdx)
        }
    }

    private fun restorePersistedSettings() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Restore Walkman & Playback settings
                val style = settingsRepository.cassetteStyle.first()
                val mechSounds = settingsRepository.walkmanMechanicalSounds.first()
                val shuffle = settingsRepository.shuffleEnabled.first()
                val repeat = settingsRepository.repeatMode.first()
                val speed = settingsRepository.playbackSpeed.first()

                withContext(Dispatchers.Main) {
                    _cassetteStyle.value = style
                    _walkmanMechanicalSounds.value = mechSounds
                    _isShuffleEnabled.value = shuffle
                    exoPlayer.shuffleModeEnabled = shuffle
                    _repeatMode.value = repeat
                    exoPlayer.repeatMode = repeat
                    _playbackSpeed.value = speed
                    exoPlayer.setPlaybackSpeed(speed)
                }

                // Restore Equalizer settings
                val eqActive = settingsRepository.eqEnabled.first()
                val eqBands = settingsRepository.eqBandGains.first()
                val eqPreampVal = settingsRepository.eqPreamp.first()
                val eqBass = settingsRepository.eqBassBoost.first()
                val eqTreble = settingsRepository.eqTrebleBoost.first()
                val eqBal = settingsRepository.eqBalance.first()

                equalizerProcessor.isEnabled = eqActive
                equalizerProcessor.setAllBands(eqBands)
                equalizerProcessor.preampGainDb = eqPreampVal
                equalizerProcessor.bassBoostDb = eqBass
                equalizerProcessor.trebleBoostDb = eqTreble
                equalizerProcessor.balance = eqBal

                // Restore Spatial Audio settings
                val spActive = settingsRepository.spatialEnabled.first()
                val spIntensity = settingsRepository.spatialIntensity.first()
                val spSpeed = settingsRepository.spatialRotationSpeed.first()
                val spDirection = settingsRepository.spatialRotationDirection.first()
                val spWidth = settingsRepository.spatialWidth.first()
                val spDepth = settingsRepository.spatialDepth.first()
                val spAzimuth = settingsRepository.spatialManualAzimuth.first()

                spatialProcessor.isEnabled = spActive
                spatialProcessor.spatialIntensity = spIntensity
                spatialProcessor.rotationSpeed = spSpeed
                spatialProcessor.rotationDirection = spDirection
                spatialProcessor.soundstageWidth = spWidth
                spatialProcessor.soundstageDepth = spDepth
                spatialProcessor.manualAzimuthDegrees = spAzimuth
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Error restoring persisted settings", e)
            }
        }
    }

    fun findTrackById(trackId: Long): AudioTrack? {
        return _currentTrack.value?.takeIf { it.id == trackId }
            ?: _queue.value.find { it.id == trackId }
    }

    fun getPlayer(): ExoPlayer = exoPlayer

    private fun setupPlayerListeners() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    _isStopped.value = false
                    startPlaybackService()
                } else {
                    persistCurrentPlaybackSnapshot()
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
                if (mediaItem != null) {
                    val trackId = mediaItem.mediaId.toLongOrNull()
                    val track = _queue.value.find { it.id == trackId }
                        ?: _currentTrack.value?.takeIf { it.id == trackId }
                    _currentTrack.value = track
                    _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)

                    if (track != null) {
                        recordPlayHistory(track)
                        persistCurrentPlaybackSnapshot()
                    }
                } else {
                    _currentTrack.value = null
                }
            }
        })
    }

    private fun startProgressUpdater() {
        progressUpdateJob?.cancel()
        progressUpdateJob = applicationScope.launch {
            var tickCount = 0
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    _currentPositionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                    tickCount++
                    if (tickCount % 20 == 0) { // Every ~5 seconds persist position for resumption
                        persistCurrentPlaybackSnapshot()
                    }
                }
                delay(250L)
            }
        }
    }

    private fun persistCurrentPlaybackSnapshot() {
        val current = _currentTrack.value ?: return
        val queueIds = _queue.value.map { it.id }
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.saveLastPlaybackSession(current.id, queueIds, pos)
        }
    }

    private fun restoreLastPlaybackSessionIfEmpty() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val session = settingsRepository.getLastPlaybackSession() ?: return@launch
                val bl = db.blacklistDao().getAllBlacklist().first()
                val wl = db.whitelistDao().getAllWhitelist().first()
                val fr = db.folderRuleDao().getAllFolderRules().first()
                cachedBlacklist = bl
                cachedWhitelist = wl
                cachedFolderRules = fr

                val allTracks = db.audioTrackDao().getAllTracksSync()
                val allowedById = RuleFilterEngine.filterAudioTracks(allTracks, bl, wl, fr)
                    .associateBy { it.id }

                val restoredQueue = session.queueIds.mapNotNull { allowedById[it] }
                    .ifEmpty { listOfNotNull(allowedById[session.trackId]) }
                if (restoredQueue.isEmpty()) return@launch

                val startIndex = restoredQueue.indexOfFirst { it.id == session.trackId }.coerceAtLeast(0)
                val targetTrack = restoredQueue[startIndex]

                withContext(Dispatchers.Main) {
                    if (_currentTrack.value != null || exoPlayer.mediaItemCount > 0) return@withContext
                    _queue.value = restoredQueue
                    _currentTrack.value = targetTrack
                    val items = restoredQueue.map { buildMediaItem(it) }
                    exoPlayer.setMediaItems(items, startIndex, session.positionMs.coerceAtLeast(0L))
                    exoPlayer.prepare()
                    exoPlayer.pause()
                    _currentPositionMs.value = session.positionMs.coerceAtLeast(0L)
                    _durationMs.value = targetTrack.durationMs
                }
            } catch (e: Exception) {
                Log.d("PlaybackManager", "Skipped session restore: ${e.message}")
            }
        }
    }

    suspend fun loadResumableMediaItemsWithStartPosition(): MediaSession.MediaItemsWithStartPosition? {
        val currentQueue = _queue.value
        if (currentQueue.isNotEmpty()) {
            val idx = exoPlayer.currentMediaItemIndex.coerceIn(0, currentQueue.lastIndex)
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            return MediaSession.MediaItemsWithStartPosition(
                currentQueue.map { buildMediaItem(it) },
                idx,
                pos
            )
        }

        val session = withContext(Dispatchers.IO) { settingsRepository.getLastPlaybackSession() } ?: return null
        val loaded = withContext(Dispatchers.IO) {
            val bl = db.blacklistDao().getAllBlacklist().first()
            val wl = db.whitelistDao().getAllWhitelist().first()
            val fr = db.folderRuleDao().getAllFolderRules().first()
            val allTracks = db.audioTrackDao().getAllTracksSync()
            val allowedById = RuleFilterEngine.filterAudioTracks(allTracks, bl, wl, fr)
                .associateBy { it.id }
            val restoredQueue = session.queueIds.mapNotNull { allowedById[it] }
                .ifEmpty { listOfNotNull(allowedById[session.trackId]) }
            restoredQueue
        }
        if (loaded.isEmpty()) return null

        val startIndex = loaded.indexOfFirst { it.id == session.trackId }.coerceAtLeast(0)
        _queue.value = loaded
        _currentTrack.value = loaded[startIndex]
        return MediaSession.MediaItemsWithStartPosition(
            loaded.map { buildMediaItem(it) },
            startIndex,
            session.positionMs.coerceAtLeast(0L)
        )
    }

    private fun recordPlayHistory(track: AudioTrack) {
        applicationScope.launch(Dispatchers.IO) {
            db.historyAndStatsDao().insertPlaybackHistory(
                PlaybackHistory(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs
                )
            )
            db.audioTrackDao().incrementPlayCount(track.id, System.currentTimeMillis())
        }
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java).apply {
                putExtra(PlaybackService.EXTRA_START_FOREGROUND, true)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Foreground service start fallback: ${e.message}")
            try {
                context.startService(Intent(context, PlaybackService::class.java))
            } catch (_: Exception) {}
        }
    }

    private fun buildMediaItem(t: AudioTrack): MediaItem {
        val uri = if (t.contentUri.isNotBlank()) Uri.parse(t.contentUri) else Uri.parse(t.path)
        return MediaItem.Builder()
            .setMediaId(t.id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setDisplayTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(NotificationArtworkHelper.buildArtUri(t.id))
                    .build()
            )
            .build()
    }

    fun playTrack(track: AudioTrack, trackList: List<AudioTrack> = listOf(track)) {
        val allowedQueue = RuleFilterEngine.filterAudioTracks(
            trackList, cachedBlacklist, cachedWhitelist, cachedFolderRules
        )
        if (allowedQueue.isEmpty()) return

        val selected = allowedQueue.find { it.id == track.id } ?: allowedQueue.first()
        val index = allowedQueue.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        _queue.value = allowedQueue
        _currentTrack.value = selected
        _isStopped.value = false

        exoPlayer.clearMediaItems()
        val mediaItems = allowedQueue.map { buildMediaItem(it) }

        exoPlayer.setMediaItems(mediaItems, index, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
        persistCurrentPlaybackSnapshot()
    }

    fun play() {
        val q = _queue.value
        if (q.isEmpty()) return
        _isStopped.value = false
        if (exoPlayer.mediaItemCount == 0) {
            val currentId = _currentTrack.value?.id
            val idx = q.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            exoPlayer.setMediaItems(q.map { buildMediaItem(it) }, idx, _currentPositionMs.value.coerceAtLeast(0L))
        }
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
        }
        exoPlayer.play()
    }

    fun stopPlaybackPreservingQueue() {
        _isStopped.value = true
        _isPlaying.value = false
        exoPlayer.playWhenReady = false
        exoPlayer.pause()
        exoPlayer.seekTo(0L)
        _currentPositionMs.value = 0L
        persistCurrentPlaybackSnapshot()
        exoPlayer.stop()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun pause() {
        if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
            exoPlayer.pause()
        }
        _isStopped.value = false
    }

    fun seekTo(positionMs: Long) {
        val dur = _durationMs.value.takeIf { it > 0L } ?: Long.MAX_VALUE
        val clamped = positionMs.coerceIn(0L, dur)
        if (exoPlayer.playbackState == Player.STATE_IDLE && _queue.value.isNotEmpty()) {
            exoPlayer.prepare()
        }
        exoPlayer.seekTo(clamped)
        _currentPositionMs.value = clamped
    }

    fun seekBackward(incrementMs: Long = 10_000L) {
        val current = if (_isStopped.value) _currentPositionMs.value else exoPlayer.currentPosition.coerceAtLeast(0L)
        val target = (current - incrementMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun seekForward(incrementMs: Long = 10_000L) {
        val current = if (_isStopped.value) _currentPositionMs.value else exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.takeIf { it > 0L } ?: _durationMs.value.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (current + incrementMs).coerceAtMost(dur)
        seekTo(target)
    }

    fun stopPlaybackAndDismiss() {
        val service = PlaybackService.activeServiceInstance
        if (service != null) {
            service.stopAndDismissNotification()
        } else {
            stopAndResetFromNotification()
        }
    }

    fun stopAndResetFromNotification() {
        persistCurrentPlaybackSnapshot()
        cancelSleepTimer()
        exoPlayer.playWhenReady = false
        exoPlayer.pause()
        exoPlayer.stop()
        _isPlaying.value = false
        _isStopped.value = true
    }

    fun skipToNext() {
        _isStopped.value = false
        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
            exoPlayer.prepare()
        }
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (_queue.value.size > 1 || _repeatMode.value == Player.REPEAT_MODE_ALL) {
            if (_queue.value.isNotEmpty()) {
                exoPlayer.seekTo(0, 0L)
                exoPlayer.play()
            }
        }
    }

    fun skipToPrevious(forcePreviousTrack: Boolean = false) {
        _isStopped.value = false
        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
            exoPlayer.prepare()
        }
        if (!forcePreviousTrack && exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0L)
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.play()
        } else if (_queue.value.size > 1 && (_repeatMode.value == Player.REPEAT_MODE_ALL || forcePreviousTrack)) {
            exoPlayer.seekTo(_queue.value.lastIndex, 0L)
            exoPlayer.play()
        } else {
            exoPlayer.seekTo(0L)
        }
    }

    fun playNext() = skipToNext()
    fun playPrevious() = skipToPrevious(forcePreviousTrack = false)

    fun toggleShuffle() {
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        exoPlayer.shuffleModeEnabled = newShuffle
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setShuffle(newShuffle)
        }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = nextMode
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setRepeatMode(nextMode)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        _playbackSpeed.value = clamped
        exoPlayer.setPlaybackSpeed(clamped)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setPlaybackSpeed(clamped)
        }
    }

    fun setCassetteStyle(style: CassetteStyle) {
        _cassetteStyle.value = style
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setCassetteStyle(style)
        }
    }

    fun setWalkmanMechanicalSounds(enabled: Boolean) {
        _walkmanMechanicalSounds.value = enabled
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setWalkmanMechanicalSounds(enabled)
        }
    }

    // --- Equalizer Persistence Operations ---

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        equalizerProcessor.setBandGain(bandIndex, gainDb)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerBands(equalizerProcessor.getAllBandGains())
        }
    }

    fun setEqAllBands(gains: FloatArray) {
        equalizerProcessor.setAllBands(gains)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerBands(equalizerProcessor.getAllBandGains())
        }
    }

    fun setEqPreamp(gainDb: Float) {
        equalizerProcessor.preampGainDb = gainDb
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerPreamp(gainDb)
        }
    }

    fun setEqBassBoost(boostDb: Float) {
        equalizerProcessor.bassBoostDb = boostDb
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerBassBoost(boostDb)
        }
    }

    fun setEqTrebleBoost(boostDb: Float) {
        equalizerProcessor.trebleBoostDb = boostDb
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerTrebleBoost(boostDb)
        }
    }

    fun setEqBalance(balance: Float) {
        equalizerProcessor.balance = balance
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerBalance(balance)
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        equalizerProcessor.isEnabled = enabled
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerEnabled(enabled)
        }
    }

    fun setEqPresetName(name: String) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerPresetName(name)
        }
    }

    // --- Spatial Audio Persistence Operations ---

    fun setSpatialEnabled(enabled: Boolean) {
        spatialProcessor.isEnabled = enabled
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialEnabled(enabled)
        }
    }

    fun setSpatialIntensity(intensity: Float) {
        spatialProcessor.spatialIntensity = intensity
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialIntensity(intensity)
        }
    }

    fun setSpatialRotationSpeed(speed: Float) {
        spatialProcessor.rotationSpeed = speed
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialRotationSpeed(speed)
        }
    }

    fun setSpatialRotationDirection(direction: Int) {
        spatialProcessor.rotationDirection = direction
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialRotationDirection(direction)
        }
    }

    fun setSpatialWidth(width: Float) {
        spatialProcessor.soundstageWidth = width
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialWidth(width)
        }
    }

    fun setSpatialDepth(depth: Float) {
        spatialProcessor.soundstageDepth = depth
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialDepth(depth)
        }
    }

    fun setSpatialManualAzimuth(azimuth: Float) {
        spatialProcessor.manualAzimuthDegrees = azimuth
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialManualAzimuth(azimuth)
        }
    }

    fun setSpatialPresetName(name: String) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialPresetName(name)
        }
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
        if (RuleFilterEngine.filterAudioTracks(listOf(track), cachedBlacklist, cachedWhitelist, cachedFolderRules).isEmpty()) {
            return
        }
        val currentList = _queue.value.toMutableList()
        currentList.add(track)
        _queue.value = currentList

        exoPlayer.addMediaItem(buildMediaItem(track))
    }

    fun playNextInQueue(track: AudioTrack) {
        if (RuleFilterEngine.filterAudioTracks(listOf(track), cachedBlacklist, cachedWhitelist, cachedFolderRules).isEmpty()) {
            return
        }
        val currentIdx = exoPlayer.currentMediaItemIndex
        val targetIdx = (currentIdx + 1).coerceAtMost(_queue.value.size)

        val currentList = _queue.value.toMutableList()
        currentList.add(targetIdx, track)
        _queue.value = currentList

        exoPlayer.addMediaItem(targetIdx, buildMediaItem(track))
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
