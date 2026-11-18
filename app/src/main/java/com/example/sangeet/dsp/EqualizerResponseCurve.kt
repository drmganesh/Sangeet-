package com.example.sangeet.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin frequency response evaluator that computes the exact combined magnitude response (in dB)
 * of SANGEET's Biquad IIR filter bank (15 peaking bands + 100Hz bass low-shelf + 6kHz treble high-shelf)
 * matching the Audio EQ Cookbook formulas in [BiquadFilter].
 */
object EqualizerResponseCurve {

    private data class Coeffs(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    )

    private const val MIN_FREQ_HZ = 20.0
    private const val MAX_FREQ_HZ = 20_000.0

    fun computeResponseCurveDb(
        bandGains: FloatArray,
        bassBoostDb: Float,
        trebleBoostDb: Float,
        isEnabled: Boolean,
        pointCount: Int = 96,
        sampleRateHz: Float = 44_100f
    ): List<Float> {
        val count = pointCount.coerceAtLeast(16)
        if (!isEnabled) {
            return List(count) { 0f }
        }

        val activeFilters = ArrayList<Coeffs>(EqualizerAudioProcessor.BAND_COUNT + 2)
        val fs = sampleRateHz.toDouble()

        if (abs(bassBoostDb) >= 0.05f) {
            activeFilters += lowShelfCoeffs(100.0, fs, bassBoostDb.coerceIn(0f, 12f).toDouble(), slope = 0.9)
        }
        if (abs(trebleBoostDb) >= 0.05f) {
            activeFilters += highShelfCoeffs(6000.0, fs, trebleBoostDb.coerceIn(0f, 12f).toDouble(), slope = 0.9)
        }

        val bandCount = minOf(bandGains.size, EqualizerAudioProcessor.BAND_COUNT)
        for (i in 0 until bandCount) {
            val gain = bandGains[i].coerceIn(-12f, 12f)
            val center = EqualizerAudioProcessor.FREQUENCIES[i].toDouble()
            if (abs(gain) >= 0.05f && center < fs / 2.0) {
                activeFilters += peakingCoeffs(center, fs, gain.toDouble(), q = 1.414)
            }
        }

        if (activeFilters.isEmpty()) {
            return List(count) { 0f }
        }

        val logMin = log10(MIN_FREQ_HZ)
        val logMax = log10(MAX_FREQ_HZ)

        return List(count) { idx ->
            val t = idx.toDouble() / (count - 1).toDouble()
            val freq = 10.0.pow(logMin + t * (logMax - logMin))
            val omega = 2.0 * PI * freq / fs
            val cosW = cos(omega)
            val sinW = sin(omega)
            val cos2W = cos(2.0 * omega)
            val sin2W = sin(2.0 * omega)

            var totalDb = 0.0
            for (c in activeFilters) {
                val nr = c.b0 + c.b1 * cosW + c.b2 * cos2W
                val ni = -c.b1 * sinW - c.b2 * sin2W
                val dr = 1.0 + c.a1 * cosW + c.a2 * cos2W
                val di = -c.a1 * sinW - c.a2 * sin2W
                val denomMagSq = max(1e-12, dr * dr + di * di)
                val magSq = max(1e-12, (nr * nr + ni * ni) / denomMagSq)
                totalDb += 10.0 * log10(magSq)
            }
            totalDb.toFloat().coerceIn(-18f, 18f)
        }
    }

    /**
     * Normalized horizontal position in [0f, 1f] on a 20 Hz .. 20 kHz logarithmic frequency scale.
     */
    fun normalizedFrequencyPosition(freqHz: Float): Float {
        val clamped = freqHz.toDouble().coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ)
        val logMin = log10(MIN_FREQ_HZ)
        val logMax = log10(MAX_FREQ_HZ)
        return ((log10(clamped) - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)
    }

    private fun peakingCoeffs(centerFreq: Double, sampleRate: Double, gainDb: Double, q: Double): Coeffs {
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * centerFreq / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        val b0Raw = 1.0 + alpha * a
        val b1Raw = -2.0 * cosOmega
        val b2Raw = 1.0 - alpha * a
        val a0Raw = 1.0 + alpha / a
        val a1Raw = -2.0 * cosOmega
        val a2Raw = 1.0 - alpha / a

        return Coeffs(
            b0 = b0Raw / a0Raw,
            b1 = b1Raw / a0Raw,
            b2 = b2Raw / a0Raw,
            a1 = a1Raw / a0Raw,
            a2 = a2Raw / a0Raw
        )
    }

    private fun lowShelfCoeffs(cutoffFreq: Double, sampleRate: Double, gainDb: Double, slope: Double): Coeffs {
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * cutoffFreq / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val b0Raw = a * ((a + 1.0) - (a - 1.0) * cosOmega + twoSqrtAAlpha)
        val b1Raw = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosOmega)
        val b2Raw = a * ((a + 1.0) - (a - 1.0) * cosOmega - twoSqrtAAlpha)
        val a0Raw = (a + 1.0) + (a - 1.0) * cosOmega + twoSqrtAAlpha
        val a1Raw = -2.0 * ((a - 1.0) + (a + 1.0) * cosOmega)
        val a2Raw = (a + 1.0) + (a - 1.0) * cosOmega - twoSqrtAAlpha

        return Coeffs(
            b0 = b0Raw / a0Raw,
            b1 = b1Raw / a0Raw,
            b2 = b2Raw / a0Raw,
            a1 = a1Raw / a0Raw,
            a2 = a2Raw / a0Raw
        )
    }

    private fun highShelfCoeffs(cutoffFreq: Double, sampleRate: Double, gainDb: Double, slope: Double): Coeffs {
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * cutoffFreq / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val b0Raw = a * ((a + 1.0) + (a - 1.0) * cosOmega + twoSqrtAAlpha)
        val b1Raw = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosOmega)
        val b2Raw = a * ((a + 1.0) + (a - 1.0) * cosOmega - twoSqrtAAlpha)
        val a0Raw = (a + 1.0) - (a - 1.0) * cosOmega + twoSqrtAAlpha
        val a1Raw = 2.0 * ((a - 1.0) - (a + 1.0) * cosOmega)
        val a2Raw = (a + 1.0) - (a - 1.0) * cosOmega - twoSqrtAAlpha

        return Coeffs(
            b0 = b0Raw / a0Raw,
            b1 = b1Raw / a0Raw,
            b2 = b2Raw / a0Raw,
            a1 = a1Raw / a0Raw,
            a2 = a2Raw / a0Raw
        )
    }
}
