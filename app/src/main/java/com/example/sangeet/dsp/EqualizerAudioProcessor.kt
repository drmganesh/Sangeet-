package com.example.sangeet.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Professional 15-Band Graphic Equalizer Media3 AudioProcessor.
 *
 * Implements real-time Biquad IIR filter bank with exact center frequencies:
 * 1: 25 Hz, 2: 40 Hz, 3: 63 Hz, 4: 100 Hz, 5: 160 Hz, 6: 250 Hz, 7: 400 Hz, 8: 630 Hz,
 * 9: 1 kHz, 10: 1.6 kHz, 11: 2.5 kHz, 12: 4 kHz, 13: 6.3 kHz, 14: 10 kHz, 15: 16 kHz.
 *
 * Includes Preamp, Bass Boost, Treble Boost, Stereo Balance, and Soft-clipping Limiter.
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        val FREQUENCIES = floatArrayOf(
            25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f,
            1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f
        )
        const val BAND_COUNT = 15
    }

    var isEnabled: Boolean = true
        set(value) {
            field = value
        }

    // Gains in dB (-12 dB to +12 dB) for each of the 15 bands
    private val bandGains = FloatArray(BAND_COUNT) { 0f }
    private val bandFilters = Array(BAND_COUNT) { BiquadFilter() }

    private val bassFilter = BiquadFilter()
    private val trebleFilter = BiquadFilter()

    var bassBoostDb: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 12f)
            updateShelfFilters()
        }

    var trebleBoostDb: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 12f)
            updateShelfFilters()
        }

    var preampGainDb: Float = 0f
        set(value) {
            field = value.coerceIn(-12f, 12f)
            preampLinear = 10.0.pow((value / 20.0).toDouble()).toFloat()
        }
    private var preampLinear: Float = 1.0f

    // Balance: -1.0 (full left) to +1.0 (full right)
    var balance: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            updateBalanceGains()
        }
    private var leftBalanceGain: Float = 1.0f
    private var rightBalanceGain: Float = 1.0f

    private var currentSampleRate: Float = 44100f

    init {
        updateBalanceGains()
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in 0 until BAND_COUNT) {
            val clamped = gainDb.coerceIn(-12f, 12f)
            bandGains[bandIndex] = clamped
            bandFilters[bandIndex].setPeakingEq(FREQUENCIES[bandIndex], currentSampleRate, clamped)
        }
    }

    fun getBandGain(bandIndex: Int): Float {
        return if (bandIndex in 0 until BAND_COUNT) bandGains[bandIndex] else 0f
    }

    fun getAllBandGains(): FloatArray = bandGains.clone()

    fun setAllBands(gains: FloatArray) {
        for (i in 0 until min(gains.size, BAND_COUNT)) {
            bandGains[i] = gains[i].coerceIn(-12f, 12f)
            bandFilters[i].setPeakingEq(FREQUENCIES[i], currentSampleRate, bandGains[i])
        }
    }

    fun resetToFlat() {
        for (i in 0 until BAND_COUNT) {
            bandGains[i] = 0f
            bandFilters[i].setPeakingEq(FREQUENCIES[i], currentSampleRate, 0f)
        }
        bassBoostDb = 0f
        trebleBoostDb = 0f
        preampGainDb = 0f
        balance = 0f
    }

    private fun updateBalanceGains() {
        // Equal-power balance law
        if (balance <= 0f) {
            leftBalanceGain = 1.0f
            rightBalanceGain = 1.0f + balance // drops to 0 at -1.0
        } else {
            leftBalanceGain = 1.0f - balance // drops to 0 at +1.0
            rightBalanceGain = 1.0f
        }
    }

    private fun updateShelfFilters() {
        bassFilter.setLowShelf(100f, currentSampleRate, bassBoostDb)
        trebleFilter.setHighShelf(6000f, currentSampleRate, trebleBoostDb)
    }

    private fun updateAllFilters() {
        for (i in 0 until BAND_COUNT) {
            bandFilters[i].setPeakingEq(FREQUENCIES[i], currentSampleRate, bandGains[i])
        }
        updateShelfFilters()
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        currentSampleRate = inputAudioFormat.sampleRate.toFloat()
        updateAllFilters()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)

        if (!isEnabled) {
            // Direct bit-transparent passthrough
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val channelCount = inputAudioFormat.channelCount
        val sampleCount = remaining / 2 // 16-bit PCM = 2 bytes per sample

        var sampleIndex = 0
        while (sampleIndex < sampleCount) {
            if (channelCount >= 2) {
                // Stereo processing
                val rawL = inputBuffer.short.toFloat() / 32768.0f
                val rawR = inputBuffer.short.toFloat() / 32768.0f
                sampleIndex += 2

                var l = rawL * preampLinear * leftBalanceGain
                var r = rawR * preampLinear * rightBalanceGain

                // Bass & Treble Shelf Filters
                l = bassFilter.processLeft(l)
                r = bassFilter.processRight(r)
                l = trebleFilter.processLeft(l)
                r = trebleFilter.processRight(r)

                // 15 Peaking EQ Bands
                for (i in 0 until BAND_COUNT) {
                    val filter = bandFilters[i]
                    if (!filter.isBypassed) {
                        l = filter.processLeft(l)
                        r = filter.processRight(r)
                    }
                }

                // Smooth tanh soft-limiting to eliminate harsh clipping
                l = tanh(l.toDouble()).toFloat()
                r = tanh(r.toDouble()).toFloat()

                val outShortL = (l * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                val outShortR = (r * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()

                buffer.putShort(outShortL)
                buffer.putShort(outShortR)

                // Skip any additional channels in multi-channel audio
                for (ch in 2 until channelCount) {
                    buffer.putShort(inputBuffer.short)
                    sampleIndex++
                }
            } else {
                // Mono processing
                val raw = inputBuffer.short.toFloat() / 32768.0f
                sampleIndex++

                var mono = raw * preampLinear
                mono = bassFilter.processLeft(mono)
                mono = trebleFilter.processLeft(mono)
                for (i in 0 until BAND_COUNT) {
                    if (!bandFilters[i].isBypassed) {
                        mono = bandFilters[i].processLeft(mono)
                    }
                }
                mono = tanh(mono.toDouble()).toFloat()
                val outShort = (mono * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(outShort)
            }
        }

        buffer.flip()
    }

    override fun onReset() {
        for (f in bandFilters) f.reset()
        bassFilter.reset()
        trebleFilter.reset()
    }

    override fun onFlush() {
        for (f in bandFilters) f.reset()
        bassFilter.reset()
        trebleFilter.reset()
    }
}
