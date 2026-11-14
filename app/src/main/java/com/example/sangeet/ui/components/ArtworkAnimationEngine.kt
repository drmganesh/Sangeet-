package com.example.sangeet.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ConcurrentHashMap

enum class ArtworkAnimationStyle(val label: String, val description: String) {
    NONE("None", "Disable all optional artwork animations"),
    SUBTLE("Subtle", "Minimal smooth transitions and gentle lift"),
    SMOOTH("Smooth", "Premium transitions, zoom, and gentle motion"),
    CINEMATIC("Cinematic", "Expressive parallax, Ken Burns, and ambient light")
}

enum class ArtworkAnimationIntensity(val label: String, val factor: Float) {
    LOW("Low", 0.55f),
    MEDIUM("Medium", 1.0f),
    HIGH("High", 1.45f)
}

enum class SpotlightTransitionStyle(val label: String, val description: String) {
    SLIDE("Slide", "Smooth horizontal slide between featured album covers"),
    FADE("Fade", "Cinematic crossfade of artwork, backdrop, and track info"),
    PAGE_TURN("Page Turn", "Realistic 3D album cover page turn with perspective & shadow"),
    NONE("None", "Switch featured album artwork instantly without transition")
}

enum class SpotlightTransitionSpeed(val label: String, val durationMs: Int) {
    SLOW("Slow", 820),
    NORMAL("Normal", 520),
    FAST("Fast", 300)
}

enum class SpotlightTransitionDirection(val label: String, val description: String) {
    LEFT_TO_RIGHT("Left to Right", "Always animate from left to right"),
    RIGHT_TO_LEFT("Right to Left", "Always animate from right to left"),
    AUTOMATIC("Automatic", "Match swipe or next/previous navigation direction")
}

data class ArtworkAnimationPreferencesSnapshot(
    val style: ArtworkAnimationStyle = ArtworkAnimationStyle.SMOOTH,
    val intensity: ArtworkAnimationIntensity = ArtworkAnimationIntensity.MEDIUM,
    val enableKenBurns: Boolean = true,
    val enableParallax: Boolean = true,
    val enableAmbientLighting: Boolean = true,
    val enableCardBreathing: Boolean = true,
    val enableTrackTransitions: Boolean = true,
    val respectReducedMotion: Boolean = true,
    val spotlightTransitionStyle: SpotlightTransitionStyle = SpotlightTransitionStyle.SLIDE,
    val spotlightTransitionSpeed: SpotlightTransitionSpeed = SpotlightTransitionSpeed.NORMAL,
    val spotlightTransitionDirection: SpotlightTransitionDirection = SpotlightTransitionDirection.AUTOMATIC
)

data class EffectiveArtworkMotion(
    val isEnabled: Boolean,
    val style: ArtworkAnimationStyle,
    val kenBurnsActive: Boolean,
    val parallaxActive: Boolean,
    val ambientLightingActive: Boolean,
    val cardBreathingActive: Boolean,
    val trackTransitionsActive: Boolean,
    val zoomMaxScale: Float,
    val kenBurnsPanPx: Float,
    val parallaxMaxShiftPx: Float,
    val ambientLightAlpha: Float,
    val breathingTranslationPx: Float,
    val tapPressScale: Float,
    val transitionDurationMs: Int,
    val spotlightTransitionStyle: SpotlightTransitionStyle = SpotlightTransitionStyle.SLIDE,
    val spotlightTransitionDurationMs: Int = SpotlightTransitionSpeed.NORMAL.durationMs,
    val spotlightTransitionDirection: SpotlightTransitionDirection = SpotlightTransitionDirection.AUTOMATIC
)

/**
 * Pure Kotlin motion planner transforming persisted animation preferences and
 * Android accessibility state into clamped, battery-conscious animation parameters.
 */
object ArtworkMotionPlanner {

    fun resolveSpotlightDirectionSign(
        direction: SpotlightTransitionDirection,
        navigationDelta: Int
    ): Int {
        return when (direction) {
            SpotlightTransitionDirection.RIGHT_TO_LEFT -> 1
            SpotlightTransitionDirection.LEFT_TO_RIGHT -> -1
            SpotlightTransitionDirection.AUTOMATIC -> if (navigationDelta >= 0) 1 else -1
        }
    }

    fun compute(
        prefs: ArtworkAnimationPreferencesSnapshot,
        systemAnimatorsEnabled: Boolean = true
    ): EffectiveArtworkMotion {
        val reducedMotionBlocked = prefs.respectReducedMotion && !systemAnimatorsEnabled
        val effectiveSpotlightStyle = if (reducedMotionBlocked) {
            SpotlightTransitionStyle.NONE
        } else {
            prefs.spotlightTransitionStyle
        }
        val effectiveSpotlightDuration = if (effectiveSpotlightStyle == SpotlightTransitionStyle.NONE) {
            0
        } else {
            prefs.spotlightTransitionSpeed.durationMs
        }

        if (prefs.style == ArtworkAnimationStyle.NONE || reducedMotionBlocked) {
            return EffectiveArtworkMotion(
                isEnabled = false,
                style = ArtworkAnimationStyle.NONE,
                kenBurnsActive = false,
                parallaxActive = false,
                ambientLightingActive = false,
                cardBreathingActive = false,
                trackTransitionsActive = false,
                zoomMaxScale = 1.0f,
                kenBurnsPanPx = 0f,
                parallaxMaxShiftPx = 0f,
                ambientLightAlpha = 0f,
                breathingTranslationPx = 0f,
                tapPressScale = 1.0f,
                transitionDurationMs = 0,
                spotlightTransitionStyle = effectiveSpotlightStyle,
                spotlightTransitionDurationMs = effectiveSpotlightDuration,
                spotlightTransitionDirection = prefs.spotlightTransitionDirection
            )
        }

        val styleWeight = when (prefs.style) {
            ArtworkAnimationStyle.NONE -> 0f
            ArtworkAnimationStyle.SUBTLE -> 0.55f
            ArtworkAnimationStyle.SMOOTH -> 1.0f
            ArtworkAnimationStyle.CINEMATIC -> 1.45f
        }
        val combined = (styleWeight * prefs.intensity.factor).coerceIn(0.25f, 2.0f)

        val kenBurnsActive = prefs.enableKenBurns
        val parallaxActive = prefs.enableParallax
        val ambientLightingActive = prefs.enableAmbientLighting && prefs.style != ArtworkAnimationStyle.SUBTLE
        val cardBreathingActive = prefs.enableCardBreathing
        val trackTransitionsActive = prefs.enableTrackTransitions

        return EffectiveArtworkMotion(
            isEnabled = true,
            style = prefs.style,
            kenBurnsActive = kenBurnsActive,
            parallaxActive = parallaxActive,
            ambientLightingActive = ambientLightingActive,
            cardBreathingActive = cardBreathingActive,
            trackTransitionsActive = trackTransitionsActive,
            zoomMaxScale = 1.0f + (0.065f * combined).coerceAtMost(0.16f),
            kenBurnsPanPx = (14f * combined).coerceAtMost(32f),
            parallaxMaxShiftPx = (16f * combined).coerceAtMost(36f),
            ambientLightAlpha = (0.18f * combined).coerceIn(0.06f, 0.34f),
            breathingTranslationPx = (3.5f * combined).coerceAtMost(8f),
            tapPressScale = (1.0f - 0.045f * combined.coerceAtMost(1.5f)).coerceIn(0.91f, 0.98f),
            transitionDurationMs = when (prefs.style) {
                ArtworkAnimationStyle.SUBTLE -> 260
                ArtworkAnimationStyle.SMOOTH -> 380
                ArtworkAnimationStyle.CINEMATIC -> 520
                ArtworkAnimationStyle.NONE -> 0
            },
            spotlightTransitionStyle = effectiveSpotlightStyle,
            spotlightTransitionDurationMs = effectiveSpotlightDuration,
            spotlightTransitionDirection = prefs.spotlightTransitionDirection
        )
    }

    fun isSystemAnimationEnabled(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            val apiEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ValueAnimator.areAnimatorsEnabled()
            } else {
                true
            }
            scale > 0f && apiEnabled
        } catch (_: Throwable) {
            true
        }
    }
}

/**
 * Shared in-memory artwork path cache across Home, MiniPlayer, and Fullscreen Player
 * so synchronized artwork switches never reload or flicker.
 */
object ArtworkPathCache {
    private val paths = ConcurrentHashMap<Long, String>()
    private val noArtworkTrackIds = ConcurrentHashMap.newKeySet<Long>()

    fun get(trackId: Long): String? = paths[trackId]

    fun isKnownMissing(trackId: Long): Boolean = noArtworkTrackIds.contains(trackId)

    fun put(trackId: Long, path: String?) {
        if (!path.isNullOrBlank()) {
            noArtworkTrackIds.remove(trackId)
            paths[trackId] = path
        }
    }

    fun markNoArtwork(trackId: Long) {
        if (!paths.containsKey(trackId)) {
            noArtworkTrackIds.add(trackId)
        }
    }

    fun clearMissing() {
        noArtworkTrackIds.clear()
    }

    fun clear() {
        paths.clear()
        noArtworkTrackIds.clear()
    }
}

@Composable
fun rememberIsAppResumed(): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val isResumed = remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed.value = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return isResumed
}

@Composable
fun rememberEffectiveArtworkMotion(
    prefs: ArtworkAnimationPreferencesSnapshot
): EffectiveArtworkMotion {
    val context = LocalContext.current
    val isAppResumed by rememberIsAppResumed()
    val systemEnabled = remember(context, isAppResumed) {
        ArtworkMotionPlanner.isSystemAnimationEnabled(context)
    }
    return remember(prefs, systemEnabled) {
        ArtworkMotionPlanner.compute(prefs, systemEnabled)
    }
}
