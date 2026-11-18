package com.example.sangeet.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.BlacklistEntry
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.data.model.FolderRule
import com.example.sangeet.data.model.PlaybackHistory
import com.example.sangeet.data.model.WhitelistEntry
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.data.rules.RuleFilterEngine
import com.example.sangeet.dsp.DeckTransitionAudioProcessor
import com.example.sangeet.dsp.EqualizerAudioProcessor
import com.example.sangeet.dsp.SpatialAudioProcessor
import com.example.sangeet.playback.crossfade.BeatAnalysisResult
import com.example.sangeet.playback.crossfade.CrossfadeCurves
import com.example.sangeet.playback.crossfade.CrossfadeMode
import com.example.sangeet.playback.crossfade.CrossfadePreferencesSnapshot
import com.example.sangeet.playback.crossfade.DjTransitionStyle
import com.example.sangeet.playback.crossfade.LocalAudioBeatAnalyzer
import com.example.sangeet.playback.crossfade.PlannedTransitionTiming
import com.example.sangeet.playback.crossfade.TransitionVisualState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackManager private constructor(private val context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db = AppDatabase.getInstance(context)
    val settingsRepository = SettingsRepository.getInstance(context)
    val beatAnalyzer = LocalAudioBeatAnalyzer(context)

    // Exposed primary Equalizer & Spatial DSP processors (always kept in sync with Deck B)
    val equalizerProcessor = EqualizerAudioProcessor()
    val spatialProcessor = SpatialAudioProcessor()

    // Dedicated per-deck DSP processors so Deck A and Deck B never corrupt each other's
    // filter state or apply global effects twice.
    private val equalizerProcessorB = EqualizerAudioProcessor()
    private val spatialProcessorB = SpatialAudioProcessor()
    private val transitionProcessorA = DeckTransitionAudioProcessor()
    private val transitionProcessorB = DeckTransitionAudioProcessor()

    private val deckA: ExoPlayer
    private val deckB: ExoPlayer
    @Volatile
    private var activeIsDeckA: Boolean = true

    @Volatile
    var onActivePlayerSwapped: ((ExoPlayer) -> Unit)? = null

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

    private val _cassetteStyle = MutableStateFlow(CassetteStyle.BLACK_AND_GOLD)
    val cassetteStyle: StateFlow<CassetteStyle> = _cassetteStyle.asStateFlow()

    private val _walkmanMechanicalSounds = MutableStateFlow(false)
    val walkmanMechanicalSounds: StateFlow<Boolean> = _walkmanMechanicalSounds.asStateFlow()

    private val _isStopped = MutableStateFlow(false)
    val isStopped: StateFlow<Boolean> = _isStopped.asStateFlow()

    // --- Crossfade & DJ Transition State ---
    private val _crossfadePrefs = MutableStateFlow(CrossfadePreferencesSnapshot())
    val crossfadePrefs: StateFlow<CrossfadePreferencesSnapshot> = _crossfadePrefs.asStateFlow()

    private val _transitionVisualState = MutableStateFlow(TransitionVisualState())
    val transitionVisualState: StateFlow<TransitionVisualState> = _transitionVisualState.asStateFlow()

    private val _currentTrackBeatAnalysis = MutableStateFlow<BeatAnalysisResult?>(null)
    val currentTrackBeatAnalysis: StateFlow<BeatAnalysisResult?> = _currentTrackBeatAnalysis.asStateFlow()

    private val _nextTrackBeatAnalysis = MutableStateFlow<BeatAnalysisResult?>(null)
    val nextTrackBeatAnalysis: StateFlow<BeatAnalysisResult?> = _nextTrackBeatAnalysis.asStateFlow()

    private val _nextQueueTrack = MutableStateFlow<AudioTrack?>(null)
    val nextQueueTrack: StateFlow<AudioTrack?> = _nextQueueTrack.asStateFlow()

    private data class ActiveTransitionSession(
        val outgoingTrack: AudioTrack,
        val incomingTrack: AudioTrack,
        val incomingQueueIndex: Int,
        val totalDurationMs: Long,
        val outgoingTriggerMs: Long,
        val isPreview: Boolean,
        val isBeatSynced: Boolean,
        val alignmentNote: String,
        var elapsedActiveMs: Long = 0L,
        var lastTickRealtimeMs: Long = 0L,
        var isIncomingReady: Boolean = false,
        var startRequestedRealtimeMs: Long = SystemClock.elapsedRealtime()
    )

    private var activeTransition: ActiveTransitionSession? = null
    private var lastAutoTriggeredTrackKey: String? = null
    private var preAnalysisJob: Job? = null

    private var sleepTimerJob: Job? = null
    private var progressUpdateJob: Job? = null

    @Volatile private var cachedBlacklist: List<BlacklistEntry> = emptyList()
    @Volatile private var cachedWhitelist: List<WhitelistEntry> = emptyList()
    @Volatile private var cachedFolderRules: List<FolderRule> = emptyList()

    private val musicAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    init {
        deckA = buildDeckPlayer(
            transitionProcessor = transitionProcessorA,
            eqProcessor = equalizerProcessor,
            spatialProc = spatialProcessor,
            handleAudioFocus = true
        )
        deckB = buildDeckPlayer(
            transitionProcessor = transitionProcessorB,
            eqProcessor = equalizerProcessorB,
            spatialProc = spatialProcessorB,
            handleAudioFocus = false
        )

        setupDeckListeners()
        observeCrossfadePreferences()
        startProgressUpdater()
        restorePersistedSettings()
        observeRuleChanges()
        restoreLastPlaybackSessionIfEmpty()
    }

    private fun buildDeckPlayer(
        transitionProcessor: DeckTransitionAudioProcessor,
        eqProcessor: EqualizerAudioProcessor,
        spatialProc: SpatialAudioProcessor,
        handleAudioFocus: Boolean
    ): ExoPlayer {
        val audioSink: AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(transitionProcessor, eqProcessor, spatialProc))
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = audioSink
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(musicAudioAttributes, handleAudioFocus)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private fun activeDeck(): ExoPlayer = if (activeIsDeckA) deckA else deckB
    private fun standbyDeck(): ExoPlayer = if (activeIsDeckA) deckB else deckA
    private fun activeTransitionDsp(): DeckTransitionAudioProcessor = if (activeIsDeckA) transitionProcessorA else transitionProcessorB
    private fun standbyTransitionDsp(): DeckTransitionAudioProcessor = if (activeIsDeckA) transitionProcessorB else transitionProcessorA

    fun getPlayer(): ExoPlayer = activeDeck()

    fun getRealtimePositionMs(): Long {
        val p = activeDeck()
        return if (_isStopped.value) _currentPositionMs.value else p.currentPosition.coerceAtLeast(0L)
    }

    fun getPlaybackDurationMs(): Long {
        val p = activeDeck()
        val exoDur = p.duration.takeIf { it > 0L }
        return exoDur ?: _durationMs.value.takeIf { it > 0L } ?: (_currentTrack.value?.durationMs ?: 0L)
    }

    private fun observeCrossfadePreferences() {
        applicationScope.launch {
            settingsRepository.crossfadePreferences.collect { prefs ->
                val clean = prefs.sanitized()
                _crossfadePrefs.value = clean
                if (!isTransitionActive()) {
                    _transitionVisualState.value = _transitionVisualState.value.copy(
                        mode = clean.mode,
                        style = clean.djTransitionStyle,
                        beatAlignmentNote = when (clean.mode) {
                            CrossfadeMode.OFF -> "Crossfade Off"
                            CrossfadeMode.ADVANCED -> "Advanced Blend (${clean.advancedDurationSeconds}s)"
                            CrossfadeMode.DJ_STYLE -> "${clean.djTransitionStyle.displayName} (${clean.djDurationSeconds}s)"
                        }
                    )
                }
                if (clean.mode == CrossfadeMode.OFF && isTransitionActive()) {
                    abortActiveTransition(restoreActiveVolume = true)
                }
            }
        }
    }

    private fun isTransitionActive(): Boolean = activeTransition != null

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

        val mutableQueue = currentQueue.toMutableList()
        var currentWasRemoved = false
        val currentPlayingId = _currentTrack.value?.id
        val incomingId = activeTransition?.incomingTrack?.id
        val player = activeDeck()

        for (i in mutableQueue.indices.reversed()) {
            val t = mutableQueue[i]
            if (t.id !in allowedIds) {
                if (t.id == currentPlayingId) currentWasRemoved = true
                if (t.id == incomingId) abortActiveTransition(restoreActiveVolume = true)
                mutableQueue.removeAt(i)
                if (i < player.mediaItemCount) {
                    player.removeMediaItem(i)
                }
            }
        }
        _queue.value = mutableQueue
        if (mutableQueue.isEmpty()) {
            abortActiveTransition(restoreActiveVolume = false)
            player.stop()
            _currentTrack.value = null
            _isPlaying.value = false
        } else if (currentWasRemoved) {
            val newIdx = player.currentMediaItemIndex.coerceIn(0, mutableQueue.lastIndex)
            _currentTrack.value = mutableQueue.getOrNull(newIdx)
        }
        refreshNextTrackAndBeatAnalysis()
    }

    private fun restorePersistedSettings() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val style = settingsRepository.cassetteStyle.first()
                val mechSounds = settingsRepository.walkmanMechanicalSounds.first()
                val shuffle = settingsRepository.shuffleEnabled.first()
                val repeat = settingsRepository.repeatMode.first()
                val speed = settingsRepository.playbackSpeed.first()
                val crossfade = settingsRepository.crossfadePreferences.first()

                withContext(Dispatchers.Main) {
                    _cassetteStyle.value = style
                    _walkmanMechanicalSounds.value = mechSounds
                    _isShuffleEnabled.value = shuffle
                    deckA.shuffleModeEnabled = shuffle
                    deckB.shuffleModeEnabled = shuffle
                    _repeatMode.value = repeat
                    deckA.repeatMode = repeat
                    deckB.repeatMode = repeat
                    _playbackSpeed.value = speed
                    deckA.setPlaybackSpeed(speed)
                    deckB.setPlaybackSpeed(speed)
                    _crossfadePrefs.value = crossfade
                }

                // Restore Equalizer settings onto both decks
                val eqActive = settingsRepository.eqEnabled.first()
                val eqBands = settingsRepository.eqBandGains.first()
                val eqPreampVal = settingsRepository.eqPreamp.first()
                val eqBass = settingsRepository.eqBassBoost.first()
                val eqTreble = settingsRepository.eqTrebleBoost.first()
                val eqBal = settingsRepository.eqBalance.first()

                listOf(equalizerProcessor, equalizerProcessorB).forEach { eq ->
                    eq.isEnabled = eqActive
                    eq.setAllBands(eqBands)
                    eq.preampGainDb = eqPreampVal
                    eq.bassBoostDb = eqBass
                    eq.trebleBoostDb = eqTreble
                    eq.balance = eqBal
                }

                // Restore Spatial Audio settings onto both decks
                val spActive = settingsRepository.spatialEnabled.first()
                val spIntensity = settingsRepository.spatialIntensity.first()
                val spSpeed = settingsRepository.spatialRotationSpeed.first()
                val spDirection = settingsRepository.spatialRotationDirection.first()
                val spWidth = settingsRepository.spatialWidth.first()
                val spDepth = settingsRepository.spatialDepth.first()
                val spAzimuth = settingsRepository.spatialManualAzimuth.first()

                listOf(spatialProcessor, spatialProcessorB).forEach { sp ->
                    sp.isEnabled = spActive
                    sp.spatialIntensity = spIntensity
                    sp.rotationSpeed = spSpeed
                    sp.rotationDirection = spDirection
                    sp.soundstageWidth = spWidth
                    sp.soundstageDepth = spDepth
                    sp.manualAzimuthDegrees = spAzimuth
                }
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Error restoring persisted settings", e)
            }
        }
    }

    fun findTrackById(trackId: Long): AudioTrack? {
        return _currentTrack.value?.takeIf { it.id == trackId }
            ?: _queue.value.find { it.id == trackId }
    }

    private fun setupDeckListeners() {
        val listenerForA = createDeckListener(isDeckA = true)
        val listenerForB = createDeckListener(isDeckA = false)
        deckA.addListener(listenerForA)
        deckB.addListener(listenerForB)
    }

    private fun createDeckListener(isDeckA: Boolean): Player.Listener {
        return object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (activeIsDeckA == isDeckA) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        _isStopped.value = false
                        startPlaybackService()
                        // Resume incoming standby deck if a transition was paused (e.g. pause or focus loss)
                        activeTransition?.let { session ->
                            val sb = standbyDeck()
                            if (!sb.isPlaying && session.isIncomingReady) {
                                sb.play()
                            }
                            session.lastTickRealtimeMs = SystemClock.elapsedRealtime()
                            updateVisualStateFromSession(session, paused = false)
                        }
                    } else {
                        // Active deck paused or lost audio focus -> pause incoming deck cleanly in sync
                        activeTransition?.let { session ->
                            val sb = standbyDeck()
                            if (sb.isPlaying || sb.playWhenReady) {
                                sb.pause()
                            }
                            updateVisualStateFromSession(session, paused = true)
                        }
                        persistCurrentPlaybackSnapshot()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (activeIsDeckA == isDeckA) {
                    val p = activeDeck()
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = p.duration.coerceAtLeast(0L)
                    } else if (playbackState == Player.STATE_ENDED) {
                        val session = activeTransition
                        if (session != null && session.isIncomingReady) {
                            completeActiveTransitionSwap()
                        } else {
                            if (session != null) abortActiveTransition(restoreActiveVolume = false)
                            handleTrackEnded()
                        }
                    }
                } else {
                    // Standby deck readiness or failure notification during crossfade
                    val session = activeTransition ?: return
                    if (playbackState == Player.STATE_READY && !session.isIncomingReady) {
                        session.isIncomingReady = true
                        session.lastTickRealtimeMs = SystemClock.elapsedRealtime()
                    } else if (playbackState == Player.STATE_ENDED && session.isIncomingReady) {
                        abortActiveTransition(restoreActiveVolume = true)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (activeIsDeckA != isDeckA) {
                    // Incoming track failed to prepare -> gracefully abort transition so outgoing finishes cleanly
                    Log.w("PlaybackManager", "Standby crossfade deck failed (${error.message}), restoring active deck")
                    abortActiveTransition(restoreActiveVolume = true)
                } else {
                    Log.w("PlaybackManager", "Active deck playback error: ${error.message}")
                    if (isTransitionActive()) {
                        completeActiveTransitionSwap()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (activeIsDeckA != isDeckA) return
                val p = activeDeck()
                if (mediaItem != null) {
                    val trackId = mediaItem.mediaId.toLongOrNull()
                    val track = _queue.value.find { it.id == trackId }
                        ?: _currentTrack.value?.takeIf { it.id == trackId }
                    _currentTrack.value = track
                    _currentPositionMs.value = p.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = p.duration.coerceAtLeast(0L)
                    lastAutoTriggeredTrackKey = null

                    if (track != null) {
                        recordPlayHistory(track)
                        persistCurrentPlaybackSnapshot()
                    }
                    refreshNextTrackAndBeatAnalysis()
                } else {
                    _currentTrack.value = null
                    _nextQueueTrack.value = null
                }
            }
        }
    }

    private fun startProgressUpdater() {
        progressUpdateJob?.cancel()
        progressUpdateJob = applicationScope.launch {
            var tickCount = 0
            while (isActive) {
                val p = activeDeck()
                val session = activeTransition

                if (session != null) {
                    val sb = standbyDeck()
                    if (!session.isIncomingReady && sb.playbackState == Player.STATE_READY) {
                        session.isIncomingReady = true
                        session.lastTickRealtimeMs = SystemClock.elapsedRealtime()
                    }
                    if (!session.isIncomingReady &&
                        SystemClock.elapsedRealtime() - session.startRequestedRealtimeMs > 4_000L
                    ) {
                        // Safeguard against stalled source preparation
                        abortActiveTransition(restoreActiveVolume = true)
                    } else if (p.isPlaying && session.isIncomingReady) {
                        val now = SystemClock.elapsedRealtime()
                        val dt = if (session.lastTickRealtimeMs > 0L) {
                            (now - session.lastTickRealtimeMs).coerceIn(0L, 120L)
                        } else 0L
                        session.lastTickRealtimeMs = now
                        session.elapsedActiveMs += dt

                        val progress = (session.elapsedActiveMs.toFloat() / session.totalDurationMs.toFloat())
                            .coerceIn(0f, 1f)
                        applyTransitionMix(session, progress)

                        if (progress >= 1.0f || p.playbackState == Player.STATE_ENDED) {
                            completeActiveTransitionSwap()
                        }
                    } else if (session.isIncomingReady) {
                        session.lastTickRealtimeMs = SystemClock.elapsedRealtime()
                    }
                    _currentPositionMs.value = p.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = p.duration.coerceAtLeast(0L)
                    delay(33L) // Smooth 30 Hz crossfade envelope update
                } else {
                    if (p.isPlaying) {
                        val pos = p.currentPosition.coerceAtLeast(0L)
                        val dur = p.duration.coerceAtLeast(0L)
                        _currentPositionMs.value = pos
                        _durationMs.value = dur

                        maybeTriggerAutomaticCrossfade(pos, dur)

                        tickCount++
                        if (tickCount % 20 == 0) {
                            persistCurrentPlaybackSnapshot()
                        }
                    }
                    val remaining = (_durationMs.value - _currentPositionMs.value).coerceAtLeast(0L)
                    val approachingEnd = p.isPlaying &&
                        _crossfadePrefs.value.mode != CrossfadeMode.OFF &&
                        remaining in 1L..22_000L
                    delay(if (approachingEnd) 100L else 250L)
                }
            }
        }
    }

    private fun maybeTriggerAutomaticCrossfade(posMs: Long, durMs: Long) {
        val prefs = _crossfadePrefs.value
        if (prefs.mode == CrossfadeMode.OFF || !prefs.autoCrossfadeEnabled) return
        if (durMs < 3_500L || posMs < 1_000L) return

        val current = _currentTrack.value ?: return
        val nextIdx = resolveNextMediaIndex()
        if (nextIdx == C.INDEX_UNSET) return

        val nextTrack = _queue.value.getOrNull(nextIdx) ?: return
        // Avoid auto-retriggering multiple times for the same playback pass
        val triggerKey = "${current.id}_${_queue.value.size}_$nextIdx"
        if (lastAutoTriggeredTrackKey == triggerKey) return

        val nextDur = nextTrack.durationMs.takeIf { it > 0L } ?: durMs
        val plan = CrossfadeCurves.planTransitionTiming(
            outgoingDurationMs = durMs,
            incomingDurationMs = nextDur,
            prefs = prefs,
            outgoingAnalysis = if (prefs.djAutoBpmDetectionEnabled) beatAnalyzer.getCached(current) else null,
            incomingAnalysis = if (prefs.djAutoBpmDetectionEnabled) beatAnalyzer.getCached(nextTrack) else null
        )

        if (plan.effectiveDurationMs < 1_000L) return
        if (posMs >= plan.outgoingTriggerPositionMs && (durMs - posMs) <= plan.effectiveDurationMs + 800L) {
            lastAutoTriggeredTrackKey = triggerKey
            startDualDeckTransition(
                outgoingTrack = current,
                incomingTrack = nextTrack,
                incomingQueueIndex = nextIdx,
                plan = plan,
                isPreview = false
            )
        }
    }

    fun triggerManualTransition(isPreview: Boolean = false): Boolean {
        val prefs = _crossfadePrefs.value
        val activePrefs = if (prefs.mode == CrossfadeMode.OFF) {
            prefs.copy(mode = if (isPreview) CrossfadeMode.DJ_STYLE else return false)
        } else {
            prefs
        }

        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return false

        val p = activeDeck()
        val current = _currentTrack.value ?: currentQueue.firstOrNull() ?: return false
        val nextIdx = resolveNextMediaIndex().takeIf { it != C.INDEX_UNSET }
            ?: if (currentQueue.size > 1) ((currentQueue.indexOfFirst { it.id == current.id } + 1) % currentQueue.size) else 0

        val nextTrack = currentQueue.getOrNull(nextIdx) ?: return false

        if (isTransitionActive()) {
            abortActiveTransition(restoreActiveVolume = true)
        }

        if (!p.isPlaying) {
            play()
        }

        val durMs = getPlaybackDurationMs().takeIf { it > 0L } ?: current.durationMs.coerceAtLeast(15_000L)
        val remainingMs = (durMs - p.currentPosition.coerceAtLeast(0L)).coerceAtLeast(2_500L)
        val desiredMs = if (isPreview) {
            minOf(5_000L, remainingMs)
        } else {
            val sec = when (activePrefs.mode) {
                CrossfadeMode.ADVANCED -> activePrefs.advancedDurationSeconds
                CrossfadeMode.DJ_STYLE -> activePrefs.djDurationSeconds
                CrossfadeMode.OFF -> 5
            }
            minOf(sec * 1000L, remainingMs)
        }.coerceAtLeast(1_500L)

        val outAnalysis = if (activePrefs.djAutoBpmDetectionEnabled) beatAnalyzer.getCached(current) else null
        val inAnalysis = if (activePrefs.djAutoBpmDetectionEnabled) beatAnalyzer.getCached(nextTrack) else null
        val useBeatSync = activePrefs.mode == CrossfadeMode.DJ_STYLE &&
            activePrefs.djBeatSyncEnabled &&
            activePrefs.djAutoBpmDetectionEnabled &&
            outAnalysis?.isReliable == true &&
            inAnalysis?.isReliable == true

        val plan = PlannedTransitionTiming(
            effectiveDurationMs = desiredMs,
            outgoingTriggerPositionMs = p.currentPosition.coerceAtLeast(0L),
            incomingStartPositionMs = if (useBeatSync) inAnalysis!!.introEntryMs.coerceIn(0L, 4_000L) else 0L,
            isBeatSynced = useBeatSync,
            alignmentSummary = if (isPreview) {
                "Live ${activePrefs.mode.displayName} Preview"
            } else if (useBeatSync) {
                "Manual Beat-Synced Mix • ${outAnalysis!!.bpm!!.toInt()} → ${inAnalysis!!.bpm!!.toInt()} BPM"
            } else {
                "Manual ${activePrefs.djTransitionStyle.displayName} Mix"
            }
        )

        startDualDeckTransition(
            outgoingTrack = current,
            incomingTrack = nextTrack,
            incomingQueueIndex = nextIdx,
            plan = plan,
            isPreview = isPreview
        )
        return true
    }

    private fun startDualDeckTransition(
        outgoingTrack: AudioTrack,
        incomingTrack: AudioTrack,
        incomingQueueIndex: Int,
        plan: PlannedTransitionTiming,
        isPreview: Boolean
    ) {
        val prefs = _crossfadePrefs.value
        val mode = if (prefs.mode == CrossfadeMode.OFF && isPreview) CrossfadeMode.DJ_STYLE else prefs.mode
        val sb = standbyDeck()
        val sbDsp = standbyTransitionDsp()
        val actDsp = activeTransitionDsp()

        val initialBass = CrossfadeCurves.computeBassSwapAttenuationDb(0f, mode, prefs.djTransitionStyle)
        val initialGains = CrossfadeCurves.computeGains(
            progress = 0f,
            mode = mode,
            style = prefs.djTransitionStyle,
            equalPowerEnabled = prefs.equalPowerCurvesEnabled,
            outgoingCap = prefs.djOutgoingVolumeCap,
            incomingCap = prefs.djIncomingVolumeCap
        )

        actDsp.updateTransitionDsp(initialBass.outgoingBassCutDb, initialGains.overlapHeadroomGain)
        sbDsp.updateTransitionDsp(initialBass.incomingBassCutDb, initialGains.overlapHeadroomGain)

        val currentQueue = _queue.value
        val items = currentQueue.map { buildMediaItem(it) }
        val safeIdx = incomingQueueIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

        sb.repeatMode = _repeatMode.value
        sb.shuffleModeEnabled = _isShuffleEnabled.value
        sb.setPlaybackSpeed(_playbackSpeed.value)
        sb.volume = 0f
        sb.setMediaItems(items, safeIdx, plan.incomingStartPositionMs.coerceAtLeast(0L))
        sb.prepare()
        sb.play()

        val session = ActiveTransitionSession(
            outgoingTrack = outgoingTrack,
            incomingTrack = incomingTrack,
            incomingQueueIndex = safeIdx,
            totalDurationMs = plan.effectiveDurationMs.coerceAtLeast(1_000L),
            outgoingTriggerMs = plan.outgoingTriggerPositionMs,
            isPreview = isPreview,
            isBeatSynced = plan.isBeatSynced,
            alignmentNote = plan.alignmentSummary,
            elapsedActiveMs = 0L,
            lastTickRealtimeMs = SystemClock.elapsedRealtime(),
            isIncomingReady = sb.playbackState == Player.STATE_READY
        )
        activeTransition = session
        updateVisualStateFromSession(session, paused = !activeDeck().isPlaying)
    }

    private fun applyTransitionMix(session: ActiveTransitionSession, progress: Float) {
        val prefs = _crossfadePrefs.value
        val effectiveMode = if (prefs.mode == CrossfadeMode.OFF && session.isPreview) CrossfadeMode.DJ_STYLE else prefs.mode

        val gains = CrossfadeCurves.computeGains(
            progress = progress,
            mode = effectiveMode,
            style = prefs.djTransitionStyle,
            equalPowerEnabled = prefs.equalPowerCurvesEnabled,
            outgoingCap = prefs.djOutgoingVolumeCap,
            incomingCap = prefs.djIncomingVolumeCap
        )
        val bass = CrossfadeCurves.computeBassSwapAttenuationDb(
            progress = progress,
            mode = effectiveMode,
            style = prefs.djTransitionStyle
        )

        activeDeck().volume = gains.outgoingGain
        standbyDeck().volume = gains.incomingGain
        activeTransitionDsp().updateTransitionDsp(bass.outgoingBassCutDb, gains.overlapHeadroomGain)
        standbyTransitionDsp().updateTransitionDsp(bass.incomingBassCutDb, gains.overlapHeadroomGain)

        updateVisualStateFromSession(
            session = session,
            progress = progress,
            gains = gains,
            bass = bass,
            paused = !activeDeck().isPlaying
        )
    }

    private fun updateVisualStateFromSession(
        session: ActiveTransitionSession,
        progress: Float = (session.elapsedActiveMs.toFloat() / session.totalDurationMs.toFloat()).coerceIn(0f, 1f),
        gains: CrossfadeCurves.GainPair? = null,
        bass: CrossfadeCurves.BassSwapPair? = null,
        paused: Boolean = false
    ) {
        val prefs = _crossfadePrefs.value
        val effectiveMode = if (prefs.mode == CrossfadeMode.OFF && session.isPreview) CrossfadeMode.DJ_STYLE else prefs.mode
        val resolvedGains = gains ?: CrossfadeCurves.computeGains(
            progress = progress,
            mode = effectiveMode,
            style = prefs.djTransitionStyle,
            equalPowerEnabled = prefs.equalPowerCurvesEnabled,
            outgoingCap = prefs.djOutgoingVolumeCap,
            incomingCap = prefs.djIncomingVolumeCap
        )
        val resolvedBass = bass ?: CrossfadeCurves.computeBassSwapAttenuationDb(
            progress = progress,
            mode = effectiveMode,
            style = prefs.djTransitionStyle
        )

        val outAnalysis = beatAnalyzer.getCached(session.outgoingTrack)
        val inAnalysis = beatAnalyzer.getCached(session.incomingTrack)

        _transitionVisualState.value = TransitionVisualState(
            isActive = true,
            isPreview = session.isPreview,
            mode = effectiveMode,
            style = prefs.djTransitionStyle,
            progress = progress,
            outgoingTrack = session.outgoingTrack,
            incomingTrack = session.incomingTrack,
            outgoingVolume = resolvedGains.outgoingGain,
            incomingVolume = resolvedGains.incomingGain,
            outgoingBassAttenuationDb = resolvedBass.outgoingBassCutDb,
            incomingBassAttenuationDb = resolvedBass.incomingBassCutDb,
            outgoingBpm = outAnalysis?.takeIf { it.isReliable }?.bpm,
            incomingBpm = inAnalysis?.takeIf { it.isReliable }?.bpm,
            beatSyncActive = session.isBeatSynced,
            beatAlignmentNote = session.alignmentNote,
            elapsedMs = session.elapsedActiveMs,
            totalDurationMs = session.totalDurationMs,
            isPausedDuringTransition = paused
        )
    }

    private fun completeActiveTransitionSwap() {
        val session = activeTransition ?: return
        activeTransition = null

        val oldActive = activeDeck()
        val oldDsp = activeTransitionDsp()
        val newActive = standbyDeck()
        val newDsp = standbyTransitionDsp()

        activeIsDeckA = !activeIsDeckA
        lastAutoTriggeredTrackKey = null

        // Ensure incoming deck reaches full clean gain & zero bass cut with ZERO audio gap
        newActive.volume = 1.0f
        newDsp.resetTransition()
        newActive.setAudioAttributes(musicAudioAttributes, true)

        // Silence & release media on outgoing deck after transferring active flag & audio focus
        oldDsp.resetTransition()
        oldActive.volume = 1.0f
        oldActive.setAudioAttributes(musicAudioAttributes, false)
        oldActive.pause()
        oldActive.stop()
        oldActive.clearMediaItems()

        val promotedTrack = session.incomingTrack
        _currentTrack.value = promotedTrack
        _currentPositionMs.value = newActive.currentPosition.coerceAtLeast(0L)
        _durationMs.value = newActive.duration.takeIf { it > 0L } ?: promotedTrack.durationMs
        _isPlaying.value = newActive.isPlaying || newActive.playWhenReady

        val prefs = _crossfadePrefs.value
        _transitionVisualState.value = TransitionVisualState(
            isActive = false,
            mode = prefs.mode,
            style = prefs.djTransitionStyle,
            beatAlignmentNote = "Ready"
        )

        onActivePlayerSwapped?.invoke(newActive)
        recordPlayHistory(promotedTrack)
        persistCurrentPlaybackSnapshot()
        refreshNextTrackAndBeatAnalysis()
    }

    fun abortActiveTransition(restoreActiveVolume: Boolean) {
        val hadTransition = activeTransition != null
        activeTransition = null

        val sb = standbyDeck()
        standbyTransitionDsp().resetTransition()
        sb.volume = 1.0f
        sb.pause()
        sb.stop()
        sb.clearMediaItems()

        activeTransitionDsp().resetTransition()
        if (restoreActiveVolume) {
            activeDeck().volume = 1.0f
        }

        if (hadTransition) {
            val prefs = _crossfadePrefs.value
            _transitionVisualState.value = TransitionVisualState(
                isActive = false,
                mode = prefs.mode,
                style = prefs.djTransitionStyle,
                beatAlignmentNote = "Ready"
            )
        }
    }

    private fun resolveNextMediaIndex(): Int {
        val player = activeDeck()
        val q = _queue.value
        if (q.isEmpty()) return C.INDEX_UNSET

        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            val cur = player.currentMediaItemIndex
            return if (cur in q.indices) cur else C.INDEX_UNSET
        }

        val timeline = player.currentTimeline
        if (!timeline.isEmpty && player.currentMediaItemIndex in 0 until timeline.windowCount) {
            val nextIdx = timeline.getNextWindowIndex(
                player.currentMediaItemIndex,
                _repeatMode.value,
                _isShuffleEnabled.value
            )
            if (nextIdx != C.INDEX_UNSET && nextIdx in q.indices) {
                return nextIdx
            }
        }

        val curIdx = player.currentMediaItemIndex
        return when {
            curIdx + 1 in q.indices -> curIdx + 1
            _repeatMode.value == Player.REPEAT_MODE_ALL && q.size > 1 -> 0
            else -> C.INDEX_UNSET
        }
    }

    private fun refreshNextTrackAndBeatAnalysis() {
        val current = _currentTrack.value
        val nextIdx = resolveNextMediaIndex()
        val next = if (nextIdx in _queue.value.indices) _queue.value[nextIdx] else null
        _nextQueueTrack.value = next

        preAnalysisJob?.cancel()
        if (!_crossfadePrefs.value.djAutoBpmDetectionEnabled) {
            _currentTrackBeatAnalysis.value = null
            _nextTrackBeatAnalysis.value = null
            return
        }

        _currentTrackBeatAnalysis.value = current?.let { beatAnalyzer.getCached(it) }
        _nextTrackBeatAnalysis.value = next?.let { beatAnalyzer.getCached(it) }

        preAnalysisJob = applicationScope.launch(Dispatchers.IO) {
            if (current != null) {
                val res = beatAnalyzer.analyzeTrack(current)
                withContext(Dispatchers.Main) {
                    if (_currentTrack.value?.id == current.id) {
                        _currentTrackBeatAnalysis.value = res
                    }
                }
            }
            if (next != null) {
                val res = beatAnalyzer.analyzeTrack(next)
                withContext(Dispatchers.Main) {
                    if (_nextQueueTrack.value?.id == next.id) {
                        _nextTrackBeatAnalysis.value = res
                    }
                }
            }
        }
    }

    private fun persistCurrentPlaybackSnapshot() {
        val current = _currentTrack.value ?: return
        val queueIds = _queue.value.map { it.id }
        val pos = activeDeck().currentPosition.coerceAtLeast(0L)
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
                    val p = activeDeck()
                    if (_currentTrack.value != null || p.mediaItemCount > 0) return@withContext
                    _queue.value = restoredQueue
                    _currentTrack.value = targetTrack
                    val items = restoredQueue.map { buildMediaItem(it) }
                    p.setMediaItems(items, startIndex, session.positionMs.coerceAtLeast(0L))
                    p.prepare()
                    p.pause()
                    _currentPositionMs.value = session.positionMs.coerceAtLeast(0L)
                    _durationMs.value = targetTrack.durationMs
                    refreshNextTrackAndBeatAnalysis()
                }
            } catch (e: Exception) {
                Log.d("PlaybackManager", "Skipped session restore: ${e.message}")
            }
        }
    }

    suspend fun loadResumableMediaItemsWithStartPosition(): MediaSession.MediaItemsWithStartPosition? {
        val currentQueue = _queue.value
        val p = activeDeck()
        if (currentQueue.isNotEmpty()) {
            val idx = p.currentMediaItemIndex.coerceIn(0, currentQueue.lastIndex)
            val pos = p.currentPosition.coerceAtLeast(0L)
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
            session.queueIds.mapNotNull { allowedById[it] }
                .ifEmpty { listOfNotNull(allowedById[session.trackId]) }
        }
        if (loaded.isEmpty()) return null

        val startIndex = loaded.indexOfFirst { it.id == session.trackId }.coerceAtLeast(0)
        _queue.value = loaded
        _currentTrack.value = loaded[startIndex]
        refreshNextTrackAndBeatAnalysis()
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

        abortActiveTransition(restoreActiveVolume = true)
        lastAutoTriggeredTrackKey = null

        val selected = allowedQueue.find { it.id == track.id } ?: allowedQueue.first()
        val index = allowedQueue.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        _queue.value = allowedQueue
        _currentTrack.value = selected
        _isStopped.value = false

        val p = activeDeck()
        p.volume = 1.0f
        p.clearMediaItems()
        val mediaItems = allowedQueue.map { buildMediaItem(it) }

        p.setMediaItems(mediaItems, index, 0L)
        p.prepare()
        p.play()
        persistCurrentPlaybackSnapshot()
        refreshNextTrackAndBeatAnalysis()
    }

    fun play() {
        val q = _queue.value
        if (q.isEmpty()) return
        _isStopped.value = false
        val p = activeDeck()
        if (p.mediaItemCount == 0) {
            val currentId = _currentTrack.value?.id
            val idx = q.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            p.setMediaItems(q.map { buildMediaItem(it) }, idx, _currentPositionMs.value.coerceAtLeast(0L))
        }
        if (p.playbackState == Player.STATE_IDLE) {
            p.prepare()
        } else if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0L)
        }
        p.play()

        activeTransition?.let { session ->
            if (session.isIncomingReady) {
                standbyDeck().play()
                session.lastTickRealtimeMs = SystemClock.elapsedRealtime()
                updateVisualStateFromSession(session, paused = false)
            }
        }
    }

    fun stopPlaybackPreservingQueue() {
        abortActiveTransition(restoreActiveVolume = true)
        val p = activeDeck()
        _isStopped.value = true
        _isPlaying.value = false
        p.playWhenReady = false
        p.pause()
        p.seekTo(0L)
        _currentPositionMs.value = 0L
        persistCurrentPlaybackSnapshot()
        p.stop()
    }

    fun togglePlayPause() {
        if (activeDeck().isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun pause() {
        val p = activeDeck()
        if (p.isPlaying || p.playWhenReady) {
            p.pause()
        }
        val sb = standbyDeck()
        if (sb.isPlaying || sb.playWhenReady) {
            sb.pause()
        }
        activeTransition?.let { session ->
            updateVisualStateFromSession(session, paused = true)
        }
        _isStopped.value = false
    }

    fun seekTo(positionMs: Long) {
        val p = activeDeck()
        val dur = _durationMs.value.takeIf { it > 0L } ?: Long.MAX_VALUE
        val clamped = positionMs.coerceIn(0L, dur)

        val session = activeTransition
        if (session != null) {
            if (clamped < session.outgoingTriggerMs - 800L) {
                abortActiveTransition(restoreActiveVolume = true)
                lastAutoTriggeredTrackKey = null
            } else if (clamped >= dur - 600L && session.isIncomingReady) {
                completeActiveTransitionSwap()
                return
            }
        }

        if (p.playbackState == Player.STATE_IDLE && _queue.value.isNotEmpty()) {
            p.prepare()
        }
        p.seekTo(clamped)
        _currentPositionMs.value = clamped
    }

    fun seekBackward(incrementMs: Long = 10_000L) {
        val current = if (_isStopped.value) _currentPositionMs.value else activeDeck().currentPosition.coerceAtLeast(0L)
        val target = (current - incrementMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun seekForward(incrementMs: Long = 10_000L) {
        val p = activeDeck()
        val current = if (_isStopped.value) _currentPositionMs.value else p.currentPosition.coerceAtLeast(0L)
        val dur = p.duration.takeIf { it > 0L } ?: _durationMs.value.takeIf { it > 0L } ?: Long.MAX_VALUE
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
        abortActiveTransition(restoreActiveVolume = true)
        val p = activeDeck()
        p.playWhenReady = false
        p.pause()
        p.stop()
        _isPlaying.value = false
        _isStopped.value = true
    }

    fun skipToNext() {
        _isStopped.value = false
        val session = activeTransition
        if (session != null && session.isIncomingReady) {
            // Seamlessly promote incoming track immediately with zero duplication
            completeActiveTransitionSwap()
            return
        } else if (session != null) {
            abortActiveTransition(restoreActiveVolume = true)
        }

        val p = activeDeck()
        if (p.playbackState == Player.STATE_IDLE && p.mediaItemCount > 0) {
            p.prepare()
        }
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            p.play()
        } else if (_queue.value.size > 1 || _repeatMode.value == Player.REPEAT_MODE_ALL) {
            if (_queue.value.isNotEmpty()) {
                p.seekTo(0, 0L)
                p.play()
            }
        }
        refreshNextTrackAndBeatAnalysis()
    }

    fun skipToPrevious(forcePreviousTrack: Boolean = false) {
        _isStopped.value = false
        if (isTransitionActive()) {
            abortActiveTransition(restoreActiveVolume = true)
            lastAutoTriggeredTrackKey = null
        }

        val p = activeDeck()
        if (p.playbackState == Player.STATE_IDLE && p.mediaItemCount > 0) {
            p.prepare()
        }
        if (!forcePreviousTrack && p.currentPosition > 3000L) {
            p.seekTo(0L)
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
            p.play()
        } else if (_queue.value.size > 1 && (_repeatMode.value == Player.REPEAT_MODE_ALL || forcePreviousTrack)) {
            p.seekTo(_queue.value.lastIndex, 0L)
            p.play()
        } else {
            p.seekTo(0L)
        }
        refreshNextTrackAndBeatAnalysis()
    }

    fun playNext() = skipToNext()
    fun playPrevious() = skipToPrevious(forcePreviousTrack = false)

    fun toggleShuffle() {
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        deckA.shuffleModeEnabled = newShuffle
        deckB.shuffleModeEnabled = newShuffle
        refreshNextTrackAndBeatAnalysis()
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
        deckA.repeatMode = nextMode
        deckB.repeatMode = nextMode
        refreshNextTrackAndBeatAnalysis()
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setRepeatMode(nextMode)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        _playbackSpeed.value = clamped
        deckA.setPlaybackSpeed(clamped)
        deckB.setPlaybackSpeed(clamped)
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

    // --- Crossfade & DJ Mixing Configuration ---

    fun setCrossfadeMode(mode: CrossfadeMode) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setCrossfadeMode(mode)
        }
    }

    fun setCrossfadeAutoEnabled(enabled: Boolean) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setCrossfadeAutoEnabled(enabled)
        }
    }

    fun setAdvancedCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(1, 15)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setCrossfadeSeconds(clamped)
        }
    }

    fun setCrossfadeEqualPower(enabled: Boolean) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setCrossfadeEqualPower(enabled)
        }
    }

    fun setDjDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(4, 20)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setDjDurationSeconds(clamped)
        }
    }

    fun setDjBeatSyncEnabled(enabled: Boolean) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setDjBeatSyncEnabled(enabled)
        }
    }

    fun setDjAutoBpmDetectionEnabled(enabled: Boolean) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setDjAutoBpmDetectionEnabled(enabled)
        }
        applicationScope.launch {
            refreshNextTrackAndBeatAnalysis()
        }
    }

    fun setDjTransitionStyle(style: DjTransitionStyle, applyRecommendedDuration: Boolean = false) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setDjTransitionStyle(style, applyRecommendedDuration)
        }
    }

    fun setDjIncomingVolume(volume: Float) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setDjIncomingVolume(volume)
        }
    }

    fun setDjOutgoingVolume(volume: Float) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setDjOutgoingVolume(volume)
        }
    }

    fun resetCrossfadeSettings() {
        abortActiveTransition(restoreActiveVolume = true)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.resetCrossfadeSettingsToDefault()
        }
    }

    // --- Equalizer Persistence Operations (Mirrored Cleanly to Deck B) ---

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        equalizerProcessor.setBandGain(bandIndex, gainDb)
        equalizerProcessorB.setBandGain(bandIndex, gainDb)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerBands(equalizerProcessor.getAllBandGains())
        }
    }

    fun setEqAllBands(gains: FloatArray) {
        equalizerProcessor.setAllBands(gains)
        equalizerProcessorB.setAllBands(gains)
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerBands(equalizerProcessor.getAllBandGains())
        }
    }

    fun setEqPreamp(gainDb: Float) {
        equalizerProcessor.preampGainDb = gainDb
        equalizerProcessorB.preampGainDb = gainDb
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerPreamp(gainDb)
        }
    }

    fun setEqBassBoost(boostDb: Float) {
        equalizerProcessor.bassBoostDb = boostDb
        equalizerProcessorB.bassBoostDb = boostDb
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerBassBoost(boostDb)
        }
    }

    fun setEqTrebleBoost(boostDb: Float) {
        equalizerProcessor.trebleBoostDb = boostDb
        equalizerProcessorB.trebleBoostDb = boostDb
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerTrebleBoost(boostDb)
        }
    }

    fun setEqBalance(balance: Float) {
        equalizerProcessor.balance = balance
        equalizerProcessorB.balance = balance
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerBalance(balance)
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        equalizerProcessor.isEnabled = enabled
        equalizerProcessorB.isEnabled = enabled
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerEnabled(enabled)
        }
    }

    fun setEqPresetName(name: String) {
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setEqualizerPresetName(name)
        }
    }

    // --- Spatial Audio Persistence Operations (Mirrored Cleanly to Deck B) ---

    fun setSpatialEnabled(enabled: Boolean) {
        spatialProcessor.isEnabled = enabled
        spatialProcessorB.isEnabled = enabled
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialEnabled(enabled)
        }
    }

    fun setSpatialIntensity(intensity: Float) {
        spatialProcessor.spatialIntensity = intensity
        spatialProcessorB.spatialIntensity = intensity
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialIntensity(intensity)
        }
    }

    fun setSpatialRotationSpeed(speed: Float) {
        spatialProcessor.rotationSpeed = speed
        spatialProcessorB.rotationSpeed = speed
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialRotationSpeed(speed)
        }
    }

    fun setSpatialRotationDirection(direction: Int) {
        spatialProcessor.rotationDirection = direction
        spatialProcessorB.rotationDirection = direction
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialRotationDirection(direction)
        }
    }

    fun setSpatialWidth(width: Float) {
        spatialProcessor.soundstageWidth = width
        spatialProcessorB.soundstageWidth = width
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialWidth(width)
        }
    }

    fun setSpatialDepth(depth: Float) {
        spatialProcessor.soundstageDepth = depth
        spatialProcessorB.soundstageDepth = depth
        applicationScope.launch(Dispatchers.IO) {
            settingsRepository.setSpatialDepth(depth)
        }
    }

    fun setSpatialManualAzimuth(azimuth: Float) {
        spatialProcessor.manualAzimuthDegrees = azimuth
        spatialProcessorB.manualAzimuthDegrees = azimuth
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
            pause()
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
        if (fromIndex in currentList.indices && toIndex in currentList.indices && fromIndex != toIndex) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _queue.value = currentList
            val p = activeDeck()
            if (fromIndex < p.mediaItemCount && toIndex < p.mediaItemCount) {
                p.moveMediaItem(fromIndex, toIndex)
            }
            refreshNextTrackAndBeatAnalysis()
            persistCurrentPlaybackSnapshot()
        }
    }

    fun moveQueueItemToPlayNext(fromIndex: Int) {
        val currentList = _queue.value
        val currentPlayingId = _currentTrack.value?.id
        val playingIdx = currentList.indexOfFirst { it.id == currentPlayingId }.coerceAtLeast(0)
        val targetIdx = if (fromIndex <= playingIdx) playingIdx else (playingIdx + 1).coerceAtMost(currentList.lastIndex)
        if (fromIndex in currentList.indices && targetIdx in currentList.indices && fromIndex != targetIdx) {
            reorderQueue(fromIndex, targetIdx)
        }
    }

    fun clearQueue(keepCurrent: Boolean = true) {
        abortActiveTransition(restoreActiveVolume = true)
        val current = _currentTrack.value
        val p = activeDeck()
        if (keepCurrent && current != null) {
            val curIdx = p.currentMediaItemIndex
            if (p.mediaItemCount > 1 && curIdx in 0 until p.mediaItemCount) {
                for (i in (p.mediaItemCount - 1) downTo 0) {
                    if (i != curIdx) {
                        p.removeMediaItem(i)
                    }
                }
            }
            _queue.value = listOf(current)
            refreshNextTrackAndBeatAnalysis()
            persistCurrentPlaybackSnapshot()
        } else {
            p.stop()
            p.clearMediaItems()
            _queue.value = emptyList()
            _currentTrack.value = null
            _nextQueueTrack.value = null
            _isPlaying.value = false
            _isStopped.value = true
        }
    }

    fun removeFromQueue(index: Int) {
        val currentList = _queue.value.toMutableList()
        if (index in currentList.indices) {
            val removed = currentList.removeAt(index)
            if (activeTransition?.incomingTrack?.id == removed.id) {
                abortActiveTransition(restoreActiveVolume = true)
            }
            _queue.value = currentList
            val p = activeDeck()
            if (index < p.mediaItemCount) {
                p.removeMediaItem(index)
            }
            if (currentList.isEmpty()) {
                _currentTrack.value = null
                _isPlaying.value = false
            }
            refreshNextTrackAndBeatAnalysis()
            persistCurrentPlaybackSnapshot()
        }
    }

    fun insertTrackInQueue(index: Int, track: AudioTrack) {
        if (RuleFilterEngine.filterAudioTracks(listOf(track), cachedBlacklist, cachedWhitelist, cachedFolderRules).isEmpty()) {
            return
        }
        val currentList = _queue.value.toMutableList()
        val safeIdx = index.coerceIn(0, currentList.size)
        currentList.add(safeIdx, track)
        _queue.value = currentList

        val p = activeDeck()
        val exoIdx = safeIdx.coerceIn(0, p.mediaItemCount)
        p.addMediaItem(exoIdx, buildMediaItem(track))
        if (_currentTrack.value == null && currentList.isNotEmpty()) {
            _currentTrack.value = currentList.first()
        }
        refreshNextTrackAndBeatAnalysis()
        persistCurrentPlaybackSnapshot()
    }

    fun addToQueue(track: AudioTrack) {
        if (RuleFilterEngine.filterAudioTracks(listOf(track), cachedBlacklist, cachedWhitelist, cachedFolderRules).isEmpty()) {
            return
        }
        val currentList = _queue.value.toMutableList()
        currentList.add(track)
        _queue.value = currentList

        activeDeck().addMediaItem(buildMediaItem(track))
        refreshNextTrackAndBeatAnalysis()
    }

    fun playNextInQueue(track: AudioTrack) {
        if (RuleFilterEngine.filterAudioTracks(listOf(track), cachedBlacklist, cachedWhitelist, cachedFolderRules).isEmpty()) {
            return
        }
        val p = activeDeck()
        val currentIdx = p.currentMediaItemIndex
        val targetIdx = (currentIdx + 1).coerceAtMost(_queue.value.size)

        val currentList = _queue.value.toMutableList()
        currentList.add(targetIdx, track)
        _queue.value = currentList

        p.addMediaItem(targetIdx, buildMediaItem(track))
        refreshNextTrackAndBeatAnalysis()
    }

    private fun handleTrackEnded() {
        val p = activeDeck()
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            p.seekTo(0L)
            p.play()
        } else if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            p.play()
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && _queue.value.isNotEmpty()) {
            p.seekTo(0, 0L)
            p.play()
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
