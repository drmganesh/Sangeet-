package com.example.sangeet.ui.startup

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Immutable

enum class StartupAnimationStyle(val label: String, val subtitle: String) {
    FULL_WALKMAN(
        label = "Full Walkman Intro",
        subtitle = "Complete 8-scene Walkman load, Play switch & SANGEET reveal"
    ),
    SHORT_WALKMAN(
        label = "Short Walkman Intro",
        subtitle = "Condensed cassette drop, spin & SANGEET emblem"
    ),
    LOGO_ONLY(
        label = "Logo Only",
        subtitle = "Circular golden cassette badge & SANGEET by Ganeswar credit"
    ),
    DISABLED(
        label = "Disabled",
        subtitle = "Enter the SANGEET Home screen immediately"
    )
}

enum class StartupAnimationSpeed(val label: String, val durationMultiplier: Float) {
    SLOW("Slow", 1.38f),
    NORMAL("Normal", 1.0f),
    FAST("Fast", 0.65f)
}

@Immutable
data class StartupAnimationPreferencesSnapshot(
    val style: StartupAnimationStyle = StartupAnimationStyle.FULL_WALKMAN,
    val speed: StartupAnimationSpeed = StartupAnimationSpeed.NORMAL,
    val mechanicalSoundsEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true
)

enum class StartupScene(val caption: String) {
    WALKMAN_APPEARS("WALKMAN DECK"),
    DOOR_OPENS("COMPARTMENT UNLOCKED"),
    CASSETTE_ENTERS("LOADING TAPE"),
    DOOR_CLOSES("CASSETTE SEATED"),
    PLAY_SWITCH("ENGAGING PLAY"),
    WALKMAN_TRANSITION("SIGNAL LOCKED"),
    BRAND_REVEAL("SANGEET"),
    ENTER_APP("READY")
}

data class StartupTimelinePlan(
    val shouldPlay: Boolean,
    val effectiveStyle: StartupAnimationStyle,
    val totalDurationMs: Int,
    val isReducedMotionFallback: Boolean
)

/**
 * Normalised [0f..1f] choreography state driven by the active timeline progress.
 */
@Immutable
data class StartupVisualState(
    val activeScene: StartupScene,
    val backdropGoldGlow: Float,
    val walkmanAlpha: Float,
    val walkmanScale: Float,
    val walkmanOffsetYFraction: Float,
    val walkmanShadowAlpha: Float,
    val doorOpenProgress: Float,
    val latchUnlockFlash: Float,
    val cassetteVisible: Boolean,
    val cassetteDropProgress: Float,
    val cassetteSeated: Boolean,
    val playSwitchDepress: Float,
    val playLedGlow: Float,
    val reelSpinDegrees: Float,
    val tapeOffsetPx: Float,
    val transitionGlowRadius: Float,
    val emblemAlpha: Float,
    val emblemScale: Float,
    val emblemRingSweep: Float,
    val emblemSweepSheen: Float,
    val brandTitleAlpha: Float,
    val brandCreditAlpha: Float,
    val exitZoomScale: Float,
    val overlayAlpha: Float
)

object StartupTimelinePlanner {

    private const val BASE_FULL_MS = 4100
    private const val BASE_SHORT_MS = 2150
    private const val BASE_LOGO_MS = 1350
    private const val REDUCED_MOTION_MS = 380

    fun isSystemReducedMotion(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    fun buildPlan(
        prefs: StartupAnimationPreferencesSnapshot,
        isReducedMotion: Boolean,
        isNotificationPlayerLaunch: Boolean,
        isPreview: Boolean
    ): StartupTimelinePlan {
        if (!isPreview && isNotificationPlayerLaunch) {
            return StartupTimelinePlan(
                shouldPlay = false,
                effectiveStyle = StartupAnimationStyle.DISABLED,
                totalDurationMs = 0,
                isReducedMotionFallback = false
            )
        }

        val chosenStyle = if (isPreview && prefs.style == StartupAnimationStyle.DISABLED) {
            StartupAnimationStyle.FULL_WALKMAN
        } else {
            prefs.style
        }

        if (chosenStyle == StartupAnimationStyle.DISABLED) {
            return StartupTimelinePlan(
                shouldPlay = false,
                effectiveStyle = StartupAnimationStyle.DISABLED,
                totalDurationMs = 0,
                isReducedMotionFallback = false
            )
        }

        if (isReducedMotion) {
            return StartupTimelinePlan(
                shouldPlay = true,
                effectiveStyle = StartupAnimationStyle.LOGO_ONLY,
                totalDurationMs = REDUCED_MOTION_MS,
                isReducedMotionFallback = true
            )
        }

        val baseMs = when (chosenStyle) {
            StartupAnimationStyle.FULL_WALKMAN -> BASE_FULL_MS
            StartupAnimationStyle.SHORT_WALKMAN -> BASE_SHORT_MS
            StartupAnimationStyle.LOGO_ONLY -> BASE_LOGO_MS
            StartupAnimationStyle.DISABLED -> 0
        }

        val totalMs = (baseMs * prefs.speed.durationMultiplier).toInt().coerceAtLeast(320)
        return StartupTimelinePlan(
            shouldPlay = true,
            effectiveStyle = chosenStyle,
            totalDurationMs = totalMs,
            isReducedMotionFallback = false
        )
    }

    fun evaluate(
        progress: Float,
        style: StartupAnimationStyle,
        reducedMotionFallback: Boolean
    ): StartupVisualState {
        val p = progress.coerceIn(0f, 1f)
        if (reducedMotionFallback || style == StartupAnimationStyle.LOGO_ONLY) {
            return evaluateLogoOnly(p, reducedMotionFallback)
        }
        return when (style) {
            StartupAnimationStyle.FULL_WALKMAN -> evaluateFullWalkman(p)
            StartupAnimationStyle.SHORT_WALKMAN -> evaluateShortWalkman(p)
            StartupAnimationStyle.LOGO_ONLY -> evaluateLogoOnly(p, false)
            StartupAnimationStyle.DISABLED -> evaluateLogoOnly(1f, true)
        }
    }

    private fun evaluateFullWalkman(p: Float): StartupVisualState {
        // Scene windows in exact chronological order:
        // 1. WALKMAN_APPEARS     : 0.00 -> 0.17
        // 2. DOOR_OPENS          : 0.17 -> 0.30
        // 3. CASSETTE_ENTERS     : 0.30 -> 0.47
        // 4. DOOR_CLOSES         : 0.47 -> 0.59
        // 5. PLAY_SWITCH         : 0.59 -> 0.72
        // 6. WALKMAN_TRANSITION  : 0.72 -> 0.83
        // 7. BRAND_REVEAL        : 0.83 -> 0.95
        // 8. ENTER_APP           : 0.95 -> 1.00

        val scene = when {
            p < 0.17f -> StartupScene.WALKMAN_APPEARS
            p < 0.30f -> StartupScene.DOOR_OPENS
            p < 0.47f -> StartupScene.CASSETTE_ENTERS
            p < 0.59f -> StartupScene.DOOR_CLOSES
            p < 0.72f -> StartupScene.PLAY_SWITCH
            p < 0.83f -> StartupScene.WALKMAN_TRANSITION
            p < 0.95f -> StartupScene.BRAND_REVEAL
            else -> StartupScene.ENTER_APP
        }

        val s1 = easeOutCubic(band(p, 0.00f, 0.17f))
        val s2 = easeInOutCubic(band(p, 0.17f, 0.30f))
        val s3 = easeOutCubic(band(p, 0.30f, 0.47f))
        val s4 = easeInOutCubic(band(p, 0.47f, 0.59f))
        val s5 = easeOutCubic(band(p, 0.59f, 0.72f))
        val s6 = easeInOutCubic(band(p, 0.72f, 0.83f))
        val s7 = easeOutCubic(band(p, 0.83f, 0.95f))
        val s8 = easeInOutCubic(band(p, 0.95f, 1.00f))

        val walkmanEnterAlpha = band(p, 0.02f, 0.15f)
        val walkmanExitAlpha = 1f - band(p, 0.72f, 0.82f)
        val walkmanAlpha = (walkmanEnterAlpha * walkmanExitAlpha).coerceIn(0f, 1f)

        val walkmanScale = (0.85f + 0.15f * s1) - (0.34f * s6)
        val walkmanOffsetYFraction = (1f - s1) * 0.14f

        val doorOpen = when {
            p < 0.17f -> 0f
            p < 0.30f -> s2
            p < 0.47f -> 1f
            p < 0.59f -> 1f - s4
            else -> 0f
        }

        val cassetteVisible = p >= 0.30f && p < 0.82f
        val cassetteDrop = when {
            p < 0.30f -> 0f
            p < 0.47f -> s3
            else -> 1f
        }

        val playDepress = when {
            p < 0.60f -> 0f
            p < 0.72f -> band(p, 0.60f, 0.66f)
            else -> 1f
        }

        val spinningTime = (p - 0.62f).coerceAtLeast(0f)
        val reelSpin = spinningTime * 980f
        val tapeOffset = (spinningTime * 160f) % 48f

        val emblemIn = band(p, 0.75f, 0.85f)
        val emblemRingSweep = easeOutCubic(band(p, 0.82f, 0.91f))
        val sheen = band(p, 0.87f, 0.945f)

        val titleAlpha = easeOutCubic(band(p, 0.86f, 0.915f))
        val creditAlpha = easeOutCubic(band(p, 0.89f, 0.94f))

        val backdropGlow = (0.28f * band(p, 0.01f, 0.16f) +
            0.22f * s5 +
            0.40f * s6) * (1f - s8)

        return StartupVisualState(
            activeScene = scene,
            backdropGoldGlow = backdropGlow.coerceIn(0f, 1f),
            walkmanAlpha = walkmanAlpha,
            walkmanScale = walkmanScale.coerceAtLeast(0.55f),
            walkmanOffsetYFraction = walkmanOffsetYFraction,
            walkmanShadowAlpha = walkmanAlpha * (1f - s6 * 0.7f),
            doorOpenProgress = doorOpen,
            latchUnlockFlash = if (p in 0.17f..0.24f) 1f - band(p, 0.17f, 0.24f) else 0f,
            cassetteVisible = cassetteVisible,
            cassetteDropProgress = cassetteDrop,
            cassetteSeated = p >= 0.46f,
            playSwitchDepress = playDepress,
            playLedGlow = band(p, 0.61f, 0.69f),
            reelSpinDegrees = reelSpin % 360f,
            tapeOffsetPx = tapeOffset,
            transitionGlowRadius = if (p < 0.72f) 0f else s6 * (1f - 0.65f * s7),
            emblemAlpha = emblemIn * (1f - s8),
            emblemScale = (0.76f + 0.24f * easeOutCubic(emblemIn)) * (1f - 0.16f * s8),
            emblemRingSweep = emblemRingSweep,
            emblemSweepSheen = sheen,
            brandTitleAlpha = titleAlpha * (1f - s8),
            brandCreditAlpha = creditAlpha * (1f - s8),
            exitZoomScale = 1f + 0.08f * s8,
            overlayAlpha = 1f - s8
        )
    }

    private fun evaluateShortWalkman(p: Float): StartupVisualState {
        // Quick mechanical load -> play -> morph -> SANGEET brand reveal -> enter
        val scene = when {
            p < 0.14f -> StartupScene.WALKMAN_APPEARS
            p < 0.24f -> StartupScene.DOOR_OPENS
            p < 0.42f -> StartupScene.CASSETTE_ENTERS
            p < 0.54f -> StartupScene.DOOR_CLOSES
            p < 0.68f -> StartupScene.PLAY_SWITCH
            p < 0.79f -> StartupScene.WALKMAN_TRANSITION
            p < 0.93f -> StartupScene.BRAND_REVEAL
            else -> StartupScene.ENTER_APP
        }

        val s1 = easeOutCubic(band(p, 0.00f, 0.14f))
        val s2 = easeOutCubic(band(p, 0.14f, 0.24f))
        val s3 = easeOutCubic(band(p, 0.24f, 0.42f))
        val s4 = easeInOutCubic(band(p, 0.42f, 0.54f))
        val s6 = easeInOutCubic(band(p, 0.68f, 0.79f))
        val s7 = easeOutCubic(band(p, 0.79f, 0.93f))
        val s8 = easeInOutCubic(band(p, 0.93f, 1.00f))

        val walkmanAlpha = (band(p, 0.00f, 0.11f) * (1f - band(p, 0.68f, 0.79f))).coerceIn(0f, 1f)
        val doorOpen = when {
            p < 0.14f -> 0f
            p < 0.24f -> s2
            p < 0.42f -> 1f
            p < 0.54f -> 1f - s4
            else -> 0f
        }
        val reelSpin = ((p - 0.56f).coerceAtLeast(0f) * 1100f) % 360f
        val emblemIn = band(p, 0.71f, 0.82f)

        return StartupVisualState(
            activeScene = scene,
            backdropGoldGlow = (0.35f + 0.45f * s6) * (1f - s8),
            walkmanAlpha = walkmanAlpha,
            walkmanScale = (0.88f + 0.12f * s1) - 0.30f * s6,
            walkmanOffsetYFraction = (1f - s1) * 0.10f,
            walkmanShadowAlpha = walkmanAlpha * 0.9f,
            doorOpenProgress = doorOpen,
            latchUnlockFlash = if (p in 0.14f..0.22f) 1f - band(p, 0.14f, 0.22f) else 0f,
            cassetteVisible = p >= 0.24f && p < 0.78f,
            cassetteDropProgress = if (p < 0.42f) s3 else 1f,
            cassetteSeated = p >= 0.41f,
            playSwitchDepress = band(p, 0.54f, 0.62f),
            playLedGlow = band(p, 0.55f, 0.64f),
            reelSpinDegrees = reelSpin,
            tapeOffsetPx = ((p - 0.56f).coerceAtLeast(0f) * 180f) % 48f,
            transitionGlowRadius = if (p < 0.68f) 0f else s6 * (1f - 0.6f * s7),
            emblemAlpha = emblemIn * (1f - s8),
            emblemScale = (0.80f + 0.20f * easeOutCubic(emblemIn)) * (1f - 0.14f * s8),
            emblemRingSweep = easeOutCubic(band(p, 0.77f, 0.88f)),
            emblemSweepSheen = band(p, 0.84f, 0.925f),
            brandTitleAlpha = easeOutCubic(band(p, 0.82f, 0.89f)) * (1f - s8),
            brandCreditAlpha = easeOutCubic(band(p, 0.85f, 0.92f)) * (1f - s8),
            exitZoomScale = 1f + 0.08f * s8,
            overlayAlpha = 1f - s8
        )
    }

    private fun evaluateLogoOnly(p: Float, reducedMotionFallback: Boolean): StartupVisualState {
        val scene = if (p < 0.84f) StartupScene.BRAND_REVEAL else StartupScene.ENTER_APP
        val enter = if (reducedMotionFallback) 1f else easeOutCubic(band(p, 0.00f, 0.30f))
        val ringSweep = if (reducedMotionFallback) 1f else easeOutCubic(band(p, 0.08f, 0.62f))
        val titleAlpha = if (reducedMotionFallback) 1f else easeOutCubic(band(p, 0.28f, 0.58f))
        val creditAlpha = if (reducedMotionFallback) 1f else easeOutCubic(band(p, 0.42f, 0.72f))
        val sheen = if (reducedMotionFallback) 0f else band(p, 0.55f, 0.84f)
        val exit = easeInOutCubic(band(p, 0.84f, 1.00f))

        return StartupVisualState(
            activeScene = scene,
            backdropGoldGlow = (0.45f * enter) * (1f - exit),
            walkmanAlpha = 0f,
            walkmanScale = 1f,
            walkmanOffsetYFraction = 0f,
            walkmanShadowAlpha = 0f,
            doorOpenProgress = 0f,
            latchUnlockFlash = 0f,
            cassetteVisible = false,
            cassetteDropProgress = 1f,
            cassetteSeated = true,
            playSwitchDepress = 0f,
            playLedGlow = 0f,
            reelSpinDegrees = if (reducedMotionFallback) 0f else (p * 320f) % 360f,
            tapeOffsetPx = 0f,
            transitionGlowRadius = if (reducedMotionFallback) 0f else enter * (1f - 0.5f * exit),
            emblemAlpha = enter * (1f - exit),
            emblemScale = (if (reducedMotionFallback) 1f else 0.84f + 0.16f * enter) * (1f - 0.12f * exit),
            emblemRingSweep = ringSweep,
            emblemSweepSheen = sheen,
            brandTitleAlpha = titleAlpha * (1f - exit),
            brandCreditAlpha = creditAlpha * (1f - exit),
            exitZoomScale = 1f + 0.06f * exit,
            overlayAlpha = 1f - exit
        )
    }

    private fun band(p: Float, start: Float, end: Float): Float {
        if (end <= start) return if (p >= end) 1f else 0f
        return ((p - start) / (end - start)).coerceIn(0f, 1f)
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - inv * inv * inv
    }

    private fun easeInOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) {
            4f * x * x * x
        } else {
            val inv = -2f * x + 2f
            1f - (inv * inv * inv) / 2f
        }
    }
}
