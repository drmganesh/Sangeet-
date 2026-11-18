package com.example.sangeet.ui.startup

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class StartupMechanicalCue {
    DOOR_OPEN,
    CASSETTE_INSERT,
    DOOR_CLOSE,
    PLAY_SWITCH_CLICK,
    REEL_WHIRL_START
}

/**
 * 100% offline mechanical sonification & tactile vibration helper for the Walkman startup sequence.
 * Uses USAGE_ASSISTANCE_SONIFICATION so music playback & ExoPlayer audio focus are never disturbed.
 */
object StartupFeedbackEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val SAMPLE_RATE = 22050

    @Volatile
    private var activeJob: Job? = null

    private val doorOpenSamples: ShortArray by lazy { synthesizeLatchRelease() }
    private val cassetteInsertSamples: ShortArray by lazy { synthesizeCartridgeSeat() }
    private val doorCloseSamples: ShortArray by lazy { synthesizeDoorCloseThud() }
    private val playSwitchSamples: ShortArray by lazy { synthesizeMechanicalSwitchClick() }
    private val reelWhirlSamples: ShortArray by lazy { synthesizeSoftReelWhirl() }

    fun triggerCue(
        context: Context,
        cue: StartupMechanicalCue,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) {
        if (vibrationEnabled) {
            performHapticPulse(context, cue)
        }
        if (soundEnabled) {
            playSoundSample(cue)
        }
    }

    fun cancelPendingAudio() {
        activeJob?.cancel()
        activeJob = null
    }

    private fun playSoundSample(cue: StartupMechanicalCue) {
        activeJob = scope.launch {
            runCatching {
                val samples = when (cue) {
                    StartupMechanicalCue.DOOR_OPEN -> doorOpenSamples
                    StartupMechanicalCue.CASSETTE_INSERT -> cassetteInsertSamples
                    StartupMechanicalCue.DOOR_CLOSE -> doorCloseSamples
                    StartupMechanicalCue.PLAY_SWITCH_CLICK -> playSwitchSamples
                    StartupMechanicalCue.REEL_WHIRL_START -> reelWhirlSamples
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                try {
                    track.write(samples, 0, samples.size)
                    track.play()
                    Thread.sleep((samples.size * 1000L / SAMPLE_RATE) + 25L)
                    track.stop()
                } finally {
                    track.release()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun performHapticPulse(context: Context, cue: StartupMechanicalCue) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            val (durationMs, amplitude) = when (cue) {
                StartupMechanicalCue.DOOR_OPEN -> 16L to 70
                StartupMechanicalCue.CASSETTE_INSERT -> 22L to 110
                StartupMechanicalCue.DOOR_CLOSE -> 20L to 125
                StartupMechanicalCue.PLAY_SWITCH_CLICK -> 32L to 165
                StartupMechanicalCue.REEL_WHIRL_START -> 14L to 55
            }

            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }

    private fun synthesizeLatchRelease(): ShortArray {
        val count = (SAMPLE_RATE * 58) / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val springSnap = exp(-190.0 * t) * sin(2.0 * PI * 860.0 * t) * 0.16
            val hingeSlide = exp(-48.0 * t) * sin(2.0 * PI * 240.0 * t) * 0.12
            val s = (springSnap + hingeSlide).coerceIn(-0.9, 0.9)
            (s * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizeCartridgeSeat(): ShortArray {
        val count = (SAMPLE_RATE * 64) / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val knock = exp(-140.0 * t) * sin(2.0 * PI * 310.0 * t) * 0.22
            val seatT = (t - 0.022).coerceAtLeast(0.0)
            val seatSnap = if (t >= 0.022) exp(-230.0 * seatT) * sin(2.0 * PI * 680.0 * seatT) * 0.18 else 0.0
            val s = (knock + seatSnap).coerceIn(-0.9, 0.9)
            (s * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizeDoorCloseThud(): ShortArray {
        val count = (SAMPLE_RATE * 52) / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val thud = exp(-150.0 * t) * sin(2.0 * PI * 210.0 * t) * 0.24
            val lock = exp(-260.0 * t) * sin(2.0 * PI * 920.0 * t) * 0.14
            val s = (thud + lock).coerceIn(-0.9, 0.9)
            (s * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizeMechanicalSwitchClick(): ShortArray {
        val count = (SAMPLE_RATE * 38) / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val sharpClick = exp(-280.0 * t) * (sin(2.0 * PI * 1120.0 * t) + 0.5 * sin(2.0 * PI * 2240.0 * t)) * 0.24
            val chassisThump = exp(-120.0 * t) * sin(2.0 * PI * 185.0 * t) * 0.18
            val s = (sharpClick + chassisThump).coerceIn(-0.9, 0.9)
            (s * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizeSoftReelWhirl(): ShortArray {
        val count = (SAMPLE_RATE * 260) / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val fadeIn = (t / 0.06).coerceIn(0.0, 1.0)
            val fadeOut = ((0.26 - t) / 0.12).coerceIn(0.0, 1.0)
            val motorHum = sin(2.0 * PI * 145.0 * t) * 0.08 + sin(2.0 * PI * 290.0 * t) * 0.03
            val s = (motorHum * fadeIn * fadeOut).coerceIn(-0.9, 0.9)
            (s * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
