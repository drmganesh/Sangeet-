package com.example.sangeet.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Per-deck transition AudioProcessor placed cleanly ahead of the 15-band Equalizer and
 * 360 Spatial Audio processors in each ExoPlayer AudioSink.
 *
 * Implements real-time Bass Swap low-shelf attenuation (~140 Hz) and overlap peak
 * headroom protection during DJ/Advanced crossfades WITHOUT altering or mutating the
 * user's saved 15-band equalizer settings.
 */
class DeckTransitionAudioProcessor : BaseAudioProcessor() {

    private val filterLock = Any()
    private val bassSwapShelf = BiquadFilter()
    private var currentSampleRate = 44100f

    @Volatile
    var bassSwapAttenuationDb: Float = 0f
        private set

    @Volatile
    var overlapHeadroomGain: Float = 1.0f
        private set

    fun updateTransitionDsp(bassCutDb: Float, headroomGain: Float) {
        val clampedBass = bassCutDb.coerceIn(-20f, 0f)
        val clampedHeadroom = headroomGain.coerceIn(0.75f, 1.0f)

        val changedBass = abs(clampedBass - bassSwapAttenuationDb) >= 0.05f
        bassSwapAttenuationDb = clampedBass
        overlapHeadroomGain = clampedHeadroom

        if (changedBass) {
            synchronized(filterLock) {
                bassSwapShelf.setLowShelf(
                    cutoffFreq = 145f,
                    sampleRate = currentSampleRate,
                    gainDb = clampedBass,
                    slope = 0.85f
                )
            }
        }
    }

    fun resetTransition() {
        bassSwapAttenuationDb = 0f
        overlapHeadroomGain = 1.0f
        synchronized(filterLock) {
            bassSwapShelf.setLowShelf(cutoffFreq = 145f, sampleRate = currentSampleRate, gainDb = 0f, slope = 0.85f)
            bassSwapShelf.reset()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        currentSampleRate = inputAudioFormat.sampleRate.toFloat()
        synchronized(filterLock) {
            bassSwapShelf.setLowShelf(
                cutoffFreq = 145f,
                sampleRate = currentSampleRate,
                gainDb = bassSwapAttenuationDb,
                slope = 0.85f
            )
            bassSwapShelf.reset()
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)
        val bassCut = bassSwapAttenuationDb
        val headroom = overlapHeadroomGain

        if (abs(bassCut) < 0.05f && headroom >= 0.999f) {
            // Bit-transparent passthrough when not actively crossfading
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val channelCount = inputAudioFormat.channelCount
        val sampleCount = remaining / 2

        synchronized(filterLock) {
            var idx = 0
            while (idx < sampleCount) {
                if (channelCount >= 2) {
                    var l = (inputBuffer.short.toFloat() / 32768.0f) * headroom
                    var r = (inputBuffer.short.toFloat() / 32768.0f) * headroom
                    idx += 2

                    if (!bassSwapShelf.isBypassed) {
                        l = bassSwapShelf.processLeft(l)
                        r = bassSwapShelf.processRight(r)
                    }

                    if (l > 0.96f || l < -0.96f) l = tanh(l.toDouble()).toFloat()
                    if (r > 0.96f || r < -0.96f) r = tanh(r.toDouble()).toFloat()

                    buffer.putShort((l * 32767.0f).toInt().coerceIn(-32768, 32767).toShort())
                    buffer.putShort((r * 32767.0f).toInt().coerceIn(-32768, 32767).toShort())

                    for (ch in 2 until channelCount) {
                        buffer.putShort(inputBuffer.short)
                        idx++
                    }
                } else {
                    var m = (inputBuffer.short.toFloat() / 32768.0f) * headroom
                    idx++
                    if (!bassSwapShelf.isBypassed) {
                        m = bassSwapShelf.processLeft(m)
                    }
                    if (m > 0.96f || m < -0.96f) m = tanh(m.toDouble()).toFloat()
                    buffer.putShort((m * 32767.0f).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }

        buffer.flip()
    }

    override fun onReset() {
        synchronized(filterLock) {
            bassSwapShelf.reset()
        }
    }

    override fun onFlush() {
        synchronized(filterLock) {
            bassSwapShelf.reset()
        }
    }
}
