package com.example.sangeet.dsp

import kotlin.math.*

/**
 * High-performance Biquad IIR filter implementation using the Audio EQ Cookbook equations.
 * Operates in Direct Form II Transposed for optimal numerical stability and low CPU overhead.
 */
class BiquadFilter {
    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // Direct Form II Transposed delay states for Left and Right channels
    private var d1L = 0.0f
    private var d2L = 0.0f
    private var d1R = 0.0f
    private var d2R = 0.0f

    var isBypassed = true
        private set

    fun reset() {
        d1L = 0.0f
        d2L = 0.0f
        d1R = 0.0f
        d2R = 0.0f
    }

    /**
     * Configure as a Peaking EQ filter.
     * @param centerFreq Center frequency in Hz
     * @param sampleRate Audio sample rate in Hz
     * @param gainDb Gain in dB (-12 to +12)
     * @param q Quality factor (typically 1.414 for ~1 octave bandwidth)
     */
    fun setPeakingEq(centerFreq: Float, sampleRate: Float, gainDb: Float, q: Float = 1.414f) {
        if (abs(gainDb) < 0.05f || centerFreq >= sampleRate / 2f) {
            isBypassed = true
            return
        }
        isBypassed = false

        val a = 10.0.pow((gainDb / 40.0).toDouble()).toFloat()
        val omega = (2.0 * Math.PI * centerFreq / sampleRate).toFloat()
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0f * q)

        val b0Raw = 1.0f + alpha * a
        val b1Raw = -2.0f * cosOmega
        val b2Raw = 1.0f - alpha * a
        val a0Raw = 1.0f + alpha / a
        val a1Raw = -2.0f * cosOmega
        val a2Raw = 1.0f - alpha / a

        b0 = b0Raw / a0Raw
        b1 = b1Raw / a0Raw
        b2 = b2Raw / a0Raw
        a1 = a1Raw / a0Raw
        a2 = a2Raw / a0Raw
    }

    /**
     * Configure as a Low-Shelf filter (for Bass Boost).
     */
    fun setLowShelf(cutoffFreq: Float, sampleRate: Float, gainDb: Float, slope: Float = 0.9f) {
        if (abs(gainDb) < 0.05f) {
            isBypassed = true
            return
        }
        isBypassed = false

        val a = 10.0.pow((gainDb / 40.0).toDouble()).toFloat()
        val omega = (2.0 * Math.PI * cutoffFreq / sampleRate).toFloat()
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / 2.0f * sqrt((a + 1.0f / a) * (1.0f / slope - 1.0f) + 2.0f)
        val twoSqrtAAlpha = 2.0f * sqrt(a) * alpha

        val b0Raw = a * ((a + 1.0f) - (a - 1.0f) * cosOmega + twoSqrtAAlpha)
        val b1Raw = 2.0f * a * ((a - 1.0f) - (a + 1.0f) * cosOmega)
        val b2Raw = a * ((a + 1.0f) - (a - 1.0f) * cosOmega - twoSqrtAAlpha)
        val a0Raw = (a + 1.0f) + (a - 1.0f) * cosOmega + twoSqrtAAlpha
        val a1Raw = -2.0f * ((a - 1.0f) + (a + 1.0f) * cosOmega)
        val a2Raw = (a + 1.0f) + (a - 1.0f) * cosOmega - twoSqrtAAlpha

        b0 = b0Raw / a0Raw
        b1 = b1Raw / a0Raw
        b2 = b2Raw / a0Raw
        a1 = a1Raw / a0Raw
        a2 = a2Raw / a0Raw
    }

    /**
     * Configure as a High-Shelf filter (for Treble Boost).
     */
    fun setHighShelf(cutoffFreq: Float, sampleRate: Float, gainDb: Float, slope: Float = 0.9f) {
        if (abs(gainDb) < 0.05f) {
            isBypassed = true
            return
        }
        isBypassed = false

        val a = 10.0.pow((gainDb / 40.0).toDouble()).toFloat()
        val omega = (2.0 * Math.PI * cutoffFreq / sampleRate).toFloat()
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / 2.0f * sqrt((a + 1.0f / a) * (1.0f / slope - 1.0f) + 2.0f)
        val twoSqrtAAlpha = 2.0f * sqrt(a) * alpha

        val b0Raw = a * ((a + 1.0f) + (a - 1.0f) * cosOmega + twoSqrtAAlpha)
        val b1Raw = -2.0f * a * ((a - 1.0f) + (a + 1.0f) * cosOmega)
        val b2Raw = a * ((a + 1.0f) + (a - 1.0f) * cosOmega - twoSqrtAAlpha)
        val a0Raw = (a + 1.0f) - (a - 1.0f) * cosOmega + twoSqrtAAlpha
        val a1Raw = 2.0f * ((a - 1.0f) - (a + 1.0f) * cosOmega)
        val a2Raw = (a + 1.0f) - (a - 1.0f) * cosOmega - twoSqrtAAlpha

        b0 = b0Raw / a0Raw
        b1 = b1Raw / a0Raw
        b2 = b2Raw / a0Raw
        a1 = a1Raw / a0Raw
        a2 = a2Raw / a0Raw
    }

    fun processLeft(sample: Float): Float {
        if (isBypassed) return sample
        val out = b0 * sample + d1L
        d1L = b1 * sample - a1 * out + d2L
        d2L = b2 * sample - a2 * out
        return out
    }

    fun processRight(sample: Float): Float {
        if (isBypassed) return sample
        val out = b0 * sample + d1R
        d1R = b1 * sample - a1 * out + d2R
        d2R = b2 * sample - a2 * out
        return out
    }
}
