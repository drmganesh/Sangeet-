package com.example.sangeet.playback.crossfade

import com.example.sangeet.data.model.AudioTrack

enum class CrossfadeMode(
    val displayName: String,
    val description: String
) {
    OFF(
        displayName = "Off",
        description = "Standard sequential playback with no overlap between tracks"
    ),
    ADVANCED(
        displayName = "Advanced Crossfade",
        description = "Smooth equal-power loudness blending with zero audible gaps"
    ),
    DJ_STYLE(
        displayName = "DJ-Style Crossfade",
        description = "Beat-aware mixing, phrase alignment, Bass Swap DSP, and DJ deck visuals"
    );

    companion object {
        fun fromString(value: String?): CrossfadeMode {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OFF
        }
    }
}

enum class CrossfadeCurveShape(val label: String) {
    LINEAR("Linear"),
    EQUAL_POWER("Equal Power"),
    S_CURVE("S-Curve Blend"),
    DJ_CLASSIC("DJ Classic"),
    QUICK_CUT("Quick Cut")
}

enum class DjTransitionStyle(
    val displayName: String,
    val subtitle: String,
    val recommendedDurationSeconds: Int,
    val bassSwapStrength: Float,
    val curveShape: CrossfadeCurveShape
) {
    SMOOTH_BLEND(
        displayName = "Smooth Blend",
        subtitle = "Silky S-curve overlap with gentle low-end taper",
        recommendedDurationSeconds = 10,
        bassSwapStrength = 0.30f,
        curveShape = CrossfadeCurveShape.S_CURVE
    ),
    EQUAL_POWER(
        displayName = "Equal Power",
        subtitle = "Constant-loudness sin/cos studio crossfade",
        recommendedDurationSeconds = 8,
        bassSwapStrength = 0.0f,
        curveShape = CrossfadeCurveShape.EQUAL_POWER
    ),
    BASS_SWAP(
        displayName = "Bass Swap",
        subtitle = "Low-shelf bass handoff prevents sub-frequency buildup",
        recommendedDurationSeconds = 10,
        bassSwapStrength = 1.0f,
        curveShape = CrossfadeCurveShape.EQUAL_POWER
    ),
    DJ_CLASSIC(
        displayName = "DJ Classic",
        subtitle = "Punchy incoming drop with balanced mid transition",
        recommendedDurationSeconds = 8,
        bassSwapStrength = 0.65f,
        curveShape = CrossfadeCurveShape.DJ_CLASSIC
    ),
    LONG_MIX(
        displayName = "Long Mix",
        subtitle = "Extended harmonic mix across musical phrases",
        recommendedDurationSeconds = 16,
        bassSwapStrength = 0.70f,
        curveShape = CrossfadeCurveShape.S_CURVE
    ),
    QUICK_MIX(
        displayName = "Quick Mix",
        subtitle = "Tight club cut with brisk bass handover",
        recommendedDurationSeconds = 4,
        bassSwapStrength = 0.50f,
        curveShape = CrossfadeCurveShape.QUICK_CUT
    );

    companion object {
        fun fromString(value: String?): DjTransitionStyle {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: BASS_SWAP
        }
    }
}

data class CrossfadePreferencesSnapshot(
    val mode: CrossfadeMode = CrossfadeMode.OFF,
    val autoCrossfadeEnabled: Boolean = true,
    val advancedDurationSeconds: Int = 5,
    val equalPowerCurvesEnabled: Boolean = true,
    val djDurationSeconds: Int = 8,
    val djBeatSyncEnabled: Boolean = true,
    val djAutoBpmDetectionEnabled: Boolean = true,
    val djTransitionStyle: DjTransitionStyle = DjTransitionStyle.BASS_SWAP,
    val djIncomingVolumeCap: Float = 1.0f,
    val djOutgoingVolumeCap: Float = 1.0f
) {
    fun sanitized(): CrossfadePreferencesSnapshot = copy(
        advancedDurationSeconds = advancedDurationSeconds.coerceIn(1, 15),
        djDurationSeconds = djDurationSeconds.coerceIn(4, 20),
        djIncomingVolumeCap = djIncomingVolumeCap.coerceIn(0.2f, 1.0f),
        djOutgoingVolumeCap = djOutgoingVolumeCap.coerceIn(0.2f, 1.0f)
    )
}

data class BeatAnalysisResult(
    val trackId: Long,
    val bpm: Float? = null,
    val confidence: Float = 0f,
    val isReliable: Boolean = false,
    val beatIntervalMs: Long = 0L,
    val firstDownbeatMs: Long = 0L,
    val outroPhraseStartMs: Long = 0L,
    val introEntryMs: Long = 0L,
    val analyzedAtEpochMs: Long = System.currentTimeMillis(),
    val statusReason: String = "Not analyzed"
)

data class PlannedTransitionTiming(
    val effectiveDurationMs: Long,
    val outgoingTriggerPositionMs: Long,
    val incomingStartPositionMs: Long,
    val isBeatSynced: Boolean,
    val alignmentSummary: String
)

data class TransitionVisualState(
    val isActive: Boolean = false,
    val isPreview: Boolean = false,
    val mode: CrossfadeMode = CrossfadeMode.OFF,
    val style: DjTransitionStyle = DjTransitionStyle.BASS_SWAP,
    val progress: Float = 0f,
    val outgoingTrack: AudioTrack? = null,
    val incomingTrack: AudioTrack? = null,
    val outgoingVolume: Float = 1.0f,
    val incomingVolume: Float = 0.0f,
    val outgoingBassAttenuationDb: Float = 0f,
    val incomingBassAttenuationDb: Float = 0f,
    val outgoingBpm: Float? = null,
    val incomingBpm: Float? = null,
    val beatSyncActive: Boolean = false,
    val beatAlignmentNote: String = "Ready",
    val elapsedMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isPausedDuringTransition: Boolean = false
)
