package com.example.sangeet.playback.crossfade

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure deterministic DSP curve and beat-phrase timing math for SANGEET's
 * Advanced Crossfade and DJ-Style Crossfade engines.
 */
object CrossfadeCurves {

    data class GainPair(
        val outgoingGain: Float,
        val incomingGain: Float,
        val overlapHeadroomGain: Float
    )

    data class BassSwapPair(
        val outgoingBassCutDb: Float,
        val incomingBassCutDb: Float
    )

    fun computeGains(
        progress: Float,
        mode: CrossfadeMode,
        style: DjTransitionStyle,
        equalPowerEnabled: Boolean,
        outgoingCap: Float = 1.0f,
        incomingCap: Float = 1.0f
    ): GainPair {
        val t = progress.coerceIn(0f, 1f)
        val outCap = outgoingCap.coerceIn(0.2f, 1.0f)
        val inCap = incomingCap.coerceIn(0.2f, 1.0f)

        if (mode == CrossfadeMode.OFF) {
            return GainPair(outgoingGain = 1.0f, incomingGain = 0.0f, overlapHeadroomGain = 1.0f)
        }

        val curveShape = when (mode) {
            CrossfadeMode.ADVANCED -> if (equalPowerEnabled) CrossfadeCurveShape.EQUAL_POWER else CrossfadeCurveShape.LINEAR
            CrossfadeMode.DJ_STYLE -> style.curveShape
            CrossfadeMode.OFF -> CrossfadeCurveShape.LINEAR
        }

        val (rawOut, rawIn) = when (curveShape) {
            CrossfadeCurveShape.LINEAR -> {
                (1.0f - t) to t
            }
            CrossfadeCurveShape.EQUAL_POWER -> {
                val angle = t * (PI / 2.0)
                cos(angle).toFloat() to sin(angle).toFloat()
            }
            CrossfadeCurveShape.S_CURVE -> {
                // Constant-power S-curve: smoothly tapered ends with preserved center energy
                val s = (0.5 * (1.0 - cos(t * PI))).coerceIn(0.0, 1.0)
                sqrt(1.0 - s).toFloat() to sqrt(s).toFloat()
            }
            CrossfadeCurveShape.DJ_CLASSIC -> {
                // Outgoing holds body slightly longer while incoming punchily enters cleanly
                val outG = (cos((t.pow(1.22f)) * (PI / 2.0))).toFloat().coerceIn(0f, 1f)
                val inG = (sin((t.pow(0.78f)) * (PI / 2.0))).toFloat().coerceIn(0f, 1f)
                outG to inG
            }
            CrossfadeCurveShape.QUICK_CUT -> {
                // Rapid club blend: quick incoming lift [0..0.42], tight outgoing drop [0.48..1.0]
                val inPhase = (t / 0.45f).coerceIn(0f, 1f)
                val outPhase = ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)
                val outG = cos(outPhase * (PI / 2.0)).toFloat()
                val inG = sin(inPhase * (PI / 2.0)).toFloat()
                outG to inG
            }
        }

        val scaledOut = (rawOut * outCap).coerceIn(0f, 1f)
        val scaledIn = (rawIn * inCap).coerceIn(0f, 1f)

        // Gentle headroom protection around the 50/50 overlap peak to prevent digital clipping
        // on heavily mastered tracks without audible level dips.
        val combinedSum = scaledOut + scaledIn
        val headroom = if (combinedSum > 1.15f) {
            (1.15f / combinedSum).coerceIn(0.84f, 1.0f)
        } else {
            1.0f
        }

        return GainPair(
            outgoingGain = scaledOut,
            incomingGain = scaledIn,
            overlapHeadroomGain = headroom
        )
    }

    /**
     * Returns negative dB attenuation values in [-18f, 0f] for the low-shelf Bass Swap filters.
     * Prevents low-frequency muddiness when two tracks overlap concurrently.
     */
    fun computeBassSwapAttenuationDb(
        progress: Float,
        mode: CrossfadeMode,
        style: DjTransitionStyle
    ): BassSwapPair {
        if (mode != CrossfadeMode.DJ_STYLE || style.bassSwapStrength <= 0.01f) {
            return BassSwapPair(0f, 0f)
        }
        val t = progress.coerceIn(0f, 1f)
        val maxCut = -18.0f * style.bassSwapStrength.coerceIn(0f, 1f)

        // Outgoing holds full bass until 15%, smoothly rolls bass out across 15%..72%
        val outFactor = ((t - 0.15f) / 0.57f).coerceIn(0f, 1f)
        // Incoming starts with bass tucked down, rises cleanly across 28%..85%
        val inRemainingCut = 1.0f - ((t - 0.28f) / 0.57f).coerceIn(0f, 1f)

        return BassSwapPair(
            outgoingBassCutDb = maxCut * outFactor,
            incomingBassCutDb = maxCut * inRemainingCut
        )
    }

    /**
     * Resolves an edge-case-safe crossfade duration for tracks of arbitrary length.
     * Ensures songs shorter than the configured fade never get swallowed or clipped.
     */
    fun resolveEffectiveDurationMs(
        outgoingDurationMs: Long,
        incomingDurationMs: Long,
        requestedDurationSeconds: Int,
        minPlayableFadeMs: Long = 1000L
    ): Long {
        if (outgoingDurationMs < 3_500L) return 0L
        val requestedMs = (requestedDurationSeconds.coerceIn(1, 20) * 1000L)
        val outCap = (outgoingDurationMs * 0.42f).roundToLong()
        val inCap = if (incomingDurationMs > 3_500L) {
            (incomingDurationMs * 0.45f).roundToLong()
        } else {
            requestedMs
        }
        val maxSafe = min(outCap, inCap)
        val effective = min(requestedMs, maxSafe)
        return if (effective < minPlayableFadeMs) 0L else effective
    }

    /**
     * Computes exact transition trigger timestamp on the outgoing track and entry offset
     * on the incoming track. Uses real beat & phrase boundaries only when both tracks have
     * high-confidence beat analyses; otherwise uses clean time-based crossfade fallback.
     */
    fun planTransitionTiming(
        outgoingDurationMs: Long,
        incomingDurationMs: Long,
        prefs: CrossfadePreferencesSnapshot,
        outgoingAnalysis: BeatAnalysisResult?,
        incomingAnalysis: BeatAnalysisResult?
    ): PlannedTransitionTiming {
        val cleanPrefs = prefs.sanitized()
        if (cleanPrefs.mode == CrossfadeMode.OFF) {
            return PlannedTransitionTiming(
                effectiveDurationMs = 0L,
                outgoingTriggerPositionMs = outgoingDurationMs,
                incomingStartPositionMs = 0L,
                isBeatSynced = false,
                alignmentSummary = "Crossfade disabled"
            )
        }

        val desiredSec = when (cleanPrefs.mode) {
            CrossfadeMode.ADVANCED -> cleanPrefs.advancedDurationSeconds
            CrossfadeMode.DJ_STYLE -> cleanPrefs.djDurationSeconds
            CrossfadeMode.OFF -> 0
        }
        val baseDurationMs = resolveEffectiveDurationMs(outgoingDurationMs, incomingDurationMs, desiredSec)
        if (baseDurationMs <= 0L) {
            return PlannedTransitionTiming(
                effectiveDurationMs = 0L,
                outgoingTriggerPositionMs = outgoingDurationMs,
                incomingStartPositionMs = 0L,
                isBeatSynced = false,
                alignmentSummary = "Track too short for overlap"
            )
        }

        // Standard time-based plan for Advanced mode or when Beat Sync / BPM is disabled/unreliable
        val useBeatSync = cleanPrefs.mode == CrossfadeMode.DJ_STYLE &&
            cleanPrefs.djBeatSyncEnabled &&
            cleanPrefs.djAutoBpmDetectionEnabled &&
            outgoingAnalysis?.isReliable == true &&
            incomingAnalysis?.isReliable == true &&
            outgoingAnalysis.bpm != null &&
            incomingAnalysis.bpm != null &&
            outgoingAnalysis.beatIntervalMs > 0L

        if (!useBeatSync) {
            val triggerMs = (outgoingDurationMs - baseDurationMs).coerceAtLeast(1000L)
            val fallbackEntry = if (cleanPrefs.mode == CrossfadeMode.DJ_STYLE &&
                incomingAnalysis != null &&
                incomingAnalysis.introEntryMs in 1L..min(4000L, incomingDurationMs / 5)
            ) {
                incomingAnalysis.introEntryMs
            } else {
                0L
            }
            val reason = when {
                cleanPrefs.mode == CrossfadeMode.ADVANCED -> "Equal-power time blend"
                !cleanPrefs.djBeatSyncEnabled || !cleanPrefs.djAutoBpmDetectionEnabled -> "Time-based DJ mix (Beat Sync off)"
                else -> "Time-based fallback (low beat confidence)"
            }
            return PlannedTransitionTiming(
                effectiveDurationMs = baseDurationMs,
                outgoingTriggerPositionMs = triggerMs,
                incomingStartPositionMs = fallbackEntry,
                isBeatSynced = false,
                alignmentSummary = reason
            )
        }

        val outBeat = outgoingAnalysis!!
        val inBeat = incomingAnalysis!!
        val outBeatMs = outBeat.beatIntervalMs.coerceIn(280L, 1200L)

        // Align crossfade duration to an integer number of 4-beat bars (minimum 4 beats)
        val rawBeats = (baseDurationMs.toFloat() / outBeatMs.toFloat()).coerceAtLeast(4f)
        val alignedBars = ((rawBeats / 4f).roundToLong().coerceAtLeast(1L))
        val alignedDurationMs = (alignedBars * 4L * outBeatMs)
            .coerceAtMost((outgoingDurationMs * 0.45f).roundToLong())
            .coerceAtLeast(min(baseDurationMs, outBeatMs * 4L))

        val idealTrigger = (outgoingDurationMs - alignedDurationMs).coerceAtLeast(1000L)
        // Snap trigger to nearest beat phase relative to detected outro phrase/downbeat
        val anchor = if (outBeat.outroPhraseStartMs in 1000L until outgoingDurationMs - 1500L) {
            outBeat.outroPhraseStartMs
        } else {
            outBeat.firstDownbeatMs
        }
        val offsetFromAnchor = idealTrigger - anchor
        val nearestBeatSteps = (offsetFromAnchor.toDouble() / outBeatMs.toDouble()).roundToLong()
        val snappedTrigger = (anchor + nearestBeatSteps * outBeatMs)
            .coerceIn(1000L, (outgoingDurationMs - 1500L).coerceAtLeast(1000L))

        val finalDuration = min(alignedDurationMs, (outgoingDurationMs - snappedTrigger).coerceAtLeast(1500L))
        val entryMs = inBeat.introEntryMs.coerceIn(0L, (incomingDurationMs * 0.18f).roundToLong())

        val outBpmStr = "${outBeat.bpm!!.toInt()}"
        val inBpmStr = "${inBeat.bpm!!.toInt()}"
        val bpmCompatible = abs(outBeat.bpm - inBeat.bpm) <= 12f ||
            abs(outBeat.bpm - inBeat.bpm * 2f) <= 12f ||
            abs(outBeat.bpm * 2f - inBeat.bpm) <= 12f

        val note = if (bpmCompatible) {
            "Beat-Synced Phrase • $outBpmStr → $inBpmStr BPM"
        } else {
            "Beat Grid Phrase ($outBpmStr BPM) • Entry Aligned"
        }

        return PlannedTransitionTiming(
            effectiveDurationMs = finalDuration,
            outgoingTriggerPositionMs = snappedTrigger,
            incomingStartPositionMs = entryMs,
            isBeatSynced = true,
            alignmentSummary = note
        )
    }
}
