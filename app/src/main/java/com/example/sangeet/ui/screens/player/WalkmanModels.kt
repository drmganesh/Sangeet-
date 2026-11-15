package com.example.sangeet.ui.screens.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.ui.graphics.Color
import com.example.sangeet.data.model.CassetteStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class WalkmanTransportState {
    PLAYING,
    PAUSED,
    STOPPED
}

enum class CassetteSwapPhase {
    IDLE,
    OPENING,
    EJECTING,
    INSERTING,
    CLOSING
}

enum class CassetteSwapDirection {
    NEXT,
    PREVIOUS
}

enum class MechanicalSoundType {
    BUTTON_PRESS,
    LATCH_ENGAGE,
    DOOR_MECHANISM
}

data class CassettePalette(
    val shellPrimary: Color,
    val shellSecondary: Color,
    val shellBorder: Color,
    val shellAlpha: Float,
    val labelPaper: Color,
    val labelStripe: Color,
    val labelStripeSecondary: Color,
    val labelInk: Color,
    val labelSubInk: Color,
    val windowTint: Color,
    val reelFlange: Color,
    val reelHub: Color,
    val tapeRibbon: Color,
    val screwMetal: Color
)

fun CassetteStyle.palette(): CassettePalette {
    return when (this) {
        CassetteStyle.CLASSIC_BLACK -> CassettePalette(
            shellPrimary = Color(0xFF1A1B1E),
            shellSecondary = Color(0xFF0E0F12),
            shellBorder = Color(0xFF8C919C),
            shellAlpha = 0.98f,
            labelPaper = Color(0xFFF3EBDC),
            labelStripe = Color(0xFFE0A11B),
            labelStripeSecondary = Color(0xFFC84B24),
            labelInk = Color(0xFF171614),
            labelSubInk = Color(0xFF534E44),
            windowTint = Color(0xCC0D0E10),
            reelFlange = Color(0xFFE7DFD0),
            reelHub = Color(0xFF2B2D33),
            tapeRibbon = Color(0xFF3A2416),
            screwMetal = Color(0xFF9EA3AE)
        )
        CassetteStyle.BLACK_AND_GOLD -> CassettePalette(
            shellPrimary = Color(0xFF1C1B18),
            shellSecondary = Color(0xFF0A0907),
            shellBorder = Color(0xFFE8B429),
            shellAlpha = 0.99f,
            labelPaper = Color(0xFF191610),
            labelStripe = Color(0xFFFFD666),
            labelStripeSecondary = Color(0xFFE8B429),
            labelInk = Color(0xFFFFEAB5),
            labelSubInk = Color(0xFFE8B429),
            windowTint = Color(0xD908090C),
            reelFlange = Color(0xFFECE4D2),
            reelHub = Color(0xFF1F2229),
            tapeRibbon = Color(0xFF3E2617),
            screwMetal = Color(0xFFFFD666)
        )
        CassetteStyle.TRANSPARENT -> CassettePalette(
            shellPrimary = Color(0x992D3642),
            shellSecondary = Color(0xAA191F28),
            shellBorder = Color(0xFFE6C260),
            shellAlpha = 0.72f,
            labelPaper = Color(0xEBFAF7F0),
            labelStripe = Color(0xFF00B4D8),
            labelStripeSecondary = Color(0xFFE6A82E),
            labelInk = Color(0xFF101820),
            labelSubInk = Color(0xFF475569),
            windowTint = Color(0x660B0E14),
            reelFlange = Color(0xFFDDE6F0),
            reelHub = Color(0xFFF59E0B),
            tapeRibbon = Color(0xFF422819),
            screwMetal = Color(0xFFD2DCE6)
        )
        CassetteStyle.RETRO_BROWN -> CassettePalette(
            shellPrimary = Color(0xFF5C341E),
            shellSecondary = Color(0xFF361D10),
            shellBorder = Color(0xFFD69E66),
            shellAlpha = 0.98f,
            labelPaper = Color(0xFFF7E5C8),
            labelStripe = Color(0xFFB8481D),
            labelStripeSecondary = Color(0xFF7C4622),
            labelInk = Color(0xFF2C170B),
            labelSubInk = Color(0xFF6B4229),
            windowTint = Color(0xD8160C07),
            reelFlange = Color(0xFFF2DFC4),
            reelHub = Color(0xFF4A2A17),
            tapeRibbon = Color(0xFF3D2212),
            screwMetal = Color(0xFFDAB78B)
        )
        CassetteStyle.GOLDEN -> CassettePalette(
            shellPrimary = Color(0xFFC89626),
            shellSecondary = Color(0xFF7D580E),
            shellBorder = Color(0xFFFFE082),
            shellAlpha = 0.99f,
            labelPaper = Color(0xFF191610),
            labelStripe = Color(0xFFF5C451),
            labelStripeSecondary = Color(0xFFB37A12),
            labelInk = Color(0xFFFFEAB5),
            labelSubInk = Color(0xFFCDB479),
            windowTint = Color(0xDD14110B),
            reelFlange = Color(0xFFFFDF8A),
            reelHub = Color(0xFF3A2909),
            tapeRibbon = Color(0xFF331D0F),
            screwMetal = Color(0xFFFFE8A3)
        )
    }
}

/**
 * Offline procedural PCM sonification helper for optional Walkman mechanical clicks.
 * Uses USAGE_ASSISTANCE_SONIFICATION so ExoPlayer media playback and audio focus are unaffected.
 */
object WalkmanSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val SAMPLE_RATE = 22050

    private val pressSamples: ShortArray by lazy { synthesizeClick(durationMs = 16, baseHz = 980.0, decay = 260.0, amplitude = 0.20) }
    private val latchSamples: ShortArray by lazy { synthesizeClick(durationMs = 28, baseHz = 420.0, decay = 165.0, amplitude = 0.24) }
    private val doorSamples: ShortArray by lazy { synthesizeDoorLatch() }

    fun playIfEnabled(enabled: Boolean, type: MechanicalSoundType) {
        if (!enabled) return
        scope.launch {
            runCatching {
                val samples = when (type) {
                    MechanicalSoundType.BUTTON_PRESS -> pressSamples
                    MechanicalSoundType.LATCH_ENGAGE -> latchSamples
                    MechanicalSoundType.DOOR_MECHANISM -> doorSamples
                }
                val bufferBytes = samples.size * 2
                val audioTrack = AudioTrack.Builder()
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
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                Thread.sleep((samples.size * 1000L / SAMPLE_RATE) + 30L)
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }

    private fun synthesizeClick(
        durationMs: Int,
        baseHz: Double,
        decay: Double,
        amplitude: Double
    ): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-decay * t)
            val transient = sin(2.0 * PI * baseHz * t) + 0.45 * sin(2.0 * PI * (baseHz * 2.15) * t)
            val valNorm = (transient * env * amplitude).coerceIn(-0.95, 0.95)
            (valNorm * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizeDoorLatch(): ShortArray {
        val count = (SAMPLE_RATE * 44) / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val firstBurst = exp(-210.0 * t) * sin(2.0 * PI * 320.0 * t)
            val secondT = (t - 0.018).coerceAtLeast(0.0)
            val secondBurst = if (t >= 0.018) exp(-240.0 * secondT) * sin(2.0 * PI * 760.0 * secondT) else 0.0
            val sample = ((firstBurst * 0.20 + secondBurst * 0.16)).coerceIn(-0.95, 0.95)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
