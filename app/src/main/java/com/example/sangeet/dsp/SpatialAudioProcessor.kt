package com.example.sangeet.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * SANGEET 360° SPATIAL AUDIO & ROTATING SURROUND DSP
 *
 * Implements:
 * 1. 360° Rotating Soundstage with continuous azimuth movement
 * 2. Binaural ITD (Interaural Time Difference ~0.65ms max delay based on Woodworth head model)
 * 3. Binaural ILD (Interaural Level Difference with contralateral head shadow filter)
 * 4. Mid/Side Stereo Widening with acoustic decorrelation
 * 5. Headphone Crossfeed matrix to eliminate in-head acoustic fatigue
 * 6. Real-time azimuth angle reporting for UI synchronization
 */
class SpatialAudioProcessor : BaseAudioProcessor() {

    var isEnabled: Boolean = true
        set(value) {
            field = value
        }

    // Spatial intensity from 0.0 (subtle) to 1.0 (maximum immersive)
    var spatialIntensity: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    // Rotation speed in rotations per minute (e.g. 0.2 to 5.0 RPM, 0.0 = static)
    var rotationSpeed: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 5f)
        }

    // 0: Left-to-Right, 1: Right-to-Left, 2: Circular 360, 3: Static Manual Angle
    var rotationDirection: Int = 2
        set(value) {
            field = value.coerceIn(0, 3)
        }

    // Manual angle in degrees (0..360) used when rotationDirection == 3 or as base offset
    var manualAzimuthDegrees: Float = 0f
        set(value) {
            field = (value % 360f + 360f) % 360f
        }

    // Soundstage width factor: 1.0 = standard, 2.0 = ultra wide
    var soundstageWidth: Float = 1.3f
        set(value) {
            field = value.coerceIn(0.5f, 2.5f)
        }

    // Soundstage depth factor: 0.0 to 1.0
    var soundstageDepth: Float = 0.7f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    // Center level positioning factor
    var centerLevel: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
        }

    // Current real-time azimuth angle (radians) exposed to UI radar
    @Volatile
    var currentAzimuthDegrees: Float = 0f
        private set

    private var currentSampleRate: Float = 44100f

    // ITD Delay line (max 2ms delay buffer)
    private val maxDelaySamples = 192 // ~4ms at 48kHz
    private val delayBufferL = FloatArray(maxDelaySamples)
    private val delayBufferR = FloatArray(maxDelaySamples)
    private var delayWriteIndex = 0

    // ILD Head-shadow 1-pole filter state
    private var headShadowL = 0f
    private var headShadowR = 0f

    // Decorrelation buffer for stereo widening
    private val decorrBuffer = FloatArray(128)
    private var decorrIndex = 0

    // Azimuth internal phase
    private var azimuthPhase = 0.0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        currentSampleRate = inputAudioFormat.sampleRate.toFloat()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)

        if (!isEnabled || spatialIntensity <= 0.01f) {
            // Direct passthrough
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val channelCount = inputAudioFormat.channelCount
        val samplePairs = remaining / (2 * channelCount)

        // Delta angle per sample based on rotation speed (RPM to rad/sample)
        val radPerSample = (rotationSpeed * 2.0 * Math.PI) / (60.0 * currentSampleRate)

        // Woodworth ITD constants: head radius ~0.0875m, speed of sound ~343 m/s
        val maxItdSeconds = 0.0875f / 343.0f // ~0.000255s (~11 to 15 samples)
        val maxItdSamples = maxItdSeconds * currentSampleRate

        for (i in 0 until samplePairs) {
            // Update rotation azimuth
            val angleRad: Double = when (rotationDirection) {
                0 -> {
                    // Left to Right oscillation (-60 deg to +60 deg)
                    azimuthPhase += radPerSample
                    sin(azimuthPhase) * (Math.PI / 3.0)
                }
                1 -> {
                    // Right to Left oscillation
                    azimuthPhase += radPerSample
                    -sin(azimuthPhase) * (Math.PI / 3.0)
                }
                2 -> {
                    // Full 360 circular rotation
                    azimuthPhase += radPerSample
                    if (azimuthPhase > 2.0 * Math.PI) azimuthPhase -= 2.0 * Math.PI
                    azimuthPhase
                }
                else -> {
                    // Static manual angle
                    Math.toRadians(manualAzimuthDegrees.toDouble())
                }
            }

            // Expose for UI visualizer
            val currentDeg = (Math.toDegrees(angleRad) % 360.0 + 360.0) % 360.0
            currentAzimuthDegrees = currentDeg.toFloat()

            val rawL = inputBuffer.short.toFloat() / 32768.0f
            val rawR = if (channelCount >= 2) inputBuffer.short.toFloat() / 32768.0f else rawL

            // 1. Mid / Side Separation
            val mid = (rawL + rawR) * 0.5f
            var side = (rawL - rawR) * 0.5f

            // 2. Stereo Widening with subtle all-pass acoustic decorrelation
            decorrBuffer[decorrIndex] = side
            val delayedSide = decorrBuffer[(decorrIndex + 64) % decorrBuffer.size]
            decorrIndex = (decorrIndex + 1) % decorrBuffer.size

            val effectiveWidth = 1.0f + (soundstageWidth - 1.0f) * spatialIntensity
            side = side * effectiveWidth + delayedSide * 0.15f * spatialIntensity

            val wideL = mid + side
            val wideR = mid - side

            // 3. 360 Spatial Azimuth Positioning (Binaural ILD + ITD)
            val sinAz = sin(angleRad).toFloat() // -1 (left) to +1 (right)
            val cosAz = cos(angleRad).toFloat() // +1 (front) to -1 (back)

            // Equal power panning based on azimuth angle and depth
            val panAngle = (sinAz * 0.5f + 0.5f) * (Math.PI.toFloat() / 2f)
            val panL = cos(panAngle)
            val panR = sin(panAngle)

            // Interaural Level Difference (ILD): contralateral head shadow low-pass filter
            // Cutoff drops when sound is on the opposite side
            val ildAlphaL = (0.3f + 0.5f * (sinAz.coerceAtLeast(0f))).coerceIn(0.1f, 0.9f)
            val ildAlphaR = (0.3f + 0.5f * ((-sinAz).coerceAtLeast(0f))).coerceIn(0.1f, 0.9f)

            headShadowL += ildAlphaL * (wideL - headShadowL)
            headShadowR += ildAlphaR * (wideR - headShadowR)

            // Interaural Time Difference (ITD): delay to contralateral ear
            val itdSamples = (abs(sinAz) * maxItdSamples * spatialIntensity).toInt().coerceIn(0, maxDelaySamples - 1)

            delayBufferL[delayWriteIndex] = wideL
            delayBufferR[delayWriteIndex] = wideR

            val delayedL = delayBufferL[(delayWriteIndex - itdSamples + maxDelaySamples) % maxDelaySamples]
            val delayedR = delayBufferR[(delayWriteIndex - itdSamples + maxDelaySamples) % maxDelaySamples]
            delayWriteIndex = (delayWriteIndex + 1) % maxDelaySamples

            // Crossfeed matrix (Bauer binaural model)
            val crossfeedGain = 0.25f * spatialIntensity
            val spatializedL = if (sinAz >= 0f) {
                // Sound source is to the right: Left ear receives delayed, head-shadowed sound
                panL * delayedL + headShadowL * 0.2f
            } else {
                // Sound source is to the left: Left ear receives direct sound
                panL * wideL + delayedR * crossfeedGain
            }

            val spatializedR = if (sinAz >= 0f) {
                // Sound source is to the right: Right ear receives direct sound
                panR * wideR + delayedL * crossfeedGain
            } else {
                // Sound source is to the left: Right ear receives delayed, head-shadowed sound
                panR * delayedR + headShadowR * 0.2f
            }

            // Blend dry/wet based on spatial intensity
            var finalL = rawL * (1f - spatialIntensity) + spatializedL * spatialIntensity
            var finalR = rawR * (1f - spatialIntensity) + spatializedR * spatialIntensity

            // Depth acoustic reflection simulation
            val depthGain = 1.0f - (soundstageDepth * 0.15f)
            finalL *= depthGain
            finalR *= depthGain

            // Soft saturation limiter to prevent any digital distortion
            finalL = tanh(finalL.toDouble()).toFloat()
            finalR = tanh(finalR.toDouble()).toFloat()

            buffer.putShort((finalL * 32767.0f).toInt().coerceIn(-32768, 32767).toShort())
            if (channelCount >= 2) {
                buffer.putShort((finalR * 32767.0f).toInt().coerceIn(-32768, 32767).toShort())
            }

            // Pass through extra channels unchanged
            for (ch in 2 until channelCount) {
                buffer.putShort(inputBuffer.short)
            }
        }

        buffer.flip()
    }

    override fun onReset() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        decorrBuffer.fill(0f)
        headShadowL = 0f
        headShadowR = 0f
        azimuthPhase = 0.0
    }

    override fun onFlush() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        decorrBuffer.fill(0f)
        headShadowL = 0f
        headShadowR = 0f
    }
}
