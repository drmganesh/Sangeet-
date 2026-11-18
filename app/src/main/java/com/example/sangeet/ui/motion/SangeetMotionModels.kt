package com.example.sangeet.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.ArtworkAnimationStyle
import com.example.sangeet.ui.components.ArtworkMotionPlanner
import com.example.sangeet.ui.components.EffectiveArtworkMotion

enum class MotionQuality(val label: String, val description: String) {
    MINIMAL("Minimal", "Crisp, ultra-light transitions for maximum speed"),
    SMOOTH("Smooth", "Balanced springs and subtle card elevation"),
    FLUID("Fluid", "Silky momentum physics and natural spring curves"),
    EXPRESSIVE("Expressive", "Rich elastic springs, deck depth & cassette polish")
}

enum class MotionSpeed(
    val label: String,
    val durationFactor: Float,
    val stiffnessMultiplier: Float
) {
    SLOW("Slow", 1.35f, 0.72f),
    NORMAL("Normal", 1.0f, 1.0f),
    FAST("Fast", 0.68f, 1.42f)
}

@Immutable
data class MotionPreferencesSnapshot(
    val quality: MotionQuality = MotionQuality.FLUID,
    val speed: MotionSpeed = MotionSpeed.NORMAL,
    val horizontalSwipesEnabled: Boolean = true,
    val verticalSwipesEnabled: Boolean = true,
    val playerGesturesEnabled: Boolean = true,
    val cassetteTransitionsEnabled: Boolean = true,
    val swipeToDismissEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val disableDecorativeAnimations: Boolean = false
)

@Immutable
data class EffectiveSangeetMotion(
    val quality: MotionQuality,
    val speed: MotionSpeed,
    val isReducedMotion: Boolean,
    val isMotionEnabled: Boolean,
    val decorativeAnimationsEnabled: Boolean,
    val allowHorizontalSwipes: Boolean,
    val allowVerticalSwipes: Boolean,
    val allowPlayerGestures: Boolean,
    val allowCassetteTransitions: Boolean,
    val allowSwipeToDismiss: Boolean,
    val pageTransitionMs: Int,
    val sheetTransitionMs: Int,
    val cassetteTransitionMs: Int,
    val springDampingRatio: Float,
    val springStiffness: Float,
    val cardPressScale: Float,
    val subtleElevationDp: Float
) {
    fun floatSpring(): SpringSpec<Float> = spring(
        dampingRatio = springDampingRatio,
        stiffness = springStiffness
    )

    fun intOffsetSpring(): SpringSpec<IntOffset> = spring(
        dampingRatio = springDampingRatio,
        stiffness = springStiffness
    )

    fun <T> durationTween(durationMs: Int = pageTransitionMs): TweenSpec<T> = tween(
        durationMillis = durationMs.coerceAtLeast(1),
        easing = FastOutSlowInEasing
    )

    fun <S> tabTransitionSpec(directionSign: Int): ContentTransform {
        if (!isMotionEnabled || isReducedMotion || quality == MotionQuality.MINIMAL) {
            val fadeMs = if (isReducedMotion) 110 else 150
            return fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs))
        }
        val sign = if (directionSign >= 0) 1 else -1
        val slideOffsetFraction = 0.22f
        return (slideInHorizontally(
            animationSpec = tween(pageTransitionMs, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> (fullWidth * slideOffsetFraction * sign).toInt() }
        ) + fadeIn(tween(pageTransitionMs, easing = FastOutSlowInEasing)))
            .togetherWith(
                slideOutHorizontally(
                    animationSpec = tween(pageTransitionMs, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> (-fullWidth * slideOffsetFraction * sign).toInt() }
                ) + fadeOut(tween((pageTransitionMs * 0.85f).toInt()))
            )
    }

    fun subScreenEnter(): EnterTransition {
        if (isReducedMotion) return fadeIn(tween(120))
        if (quality == MotionQuality.MINIMAL) {
            return fadeIn(tween(150))
        }
        return slideInHorizontally(
            animationSpec = tween(pageTransitionMs, easing = FastOutSlowInEasing),
            initialOffsetX = { (it * 0.35f).toInt() }
        ) + fadeIn(tween(pageTransitionMs))
    }

    fun subScreenExit(): ExitTransition {
        if (isReducedMotion) return fadeOut(tween(120))
        if (quality == MotionQuality.MINIMAL) {
            return fadeOut(tween(150))
        }
        return slideOutHorizontally(
            animationSpec = tween(pageTransitionMs, easing = FastOutSlowInEasing),
            targetOffsetX = { (it * 0.35f).toInt() }
        ) + fadeOut(tween((pageTransitionMs * 0.85f).toInt()))
    }

    fun playerEnter(): EnterTransition {
        if (isReducedMotion) return fadeIn(tween(140))
        return slideInVertically(
            animationSpec = tween(sheetTransitionMs, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + scaleIn(
            initialScale = if (quality == MotionQuality.EXPRESSIVE) 0.94f else 0.97f,
            animationSpec = tween(sheetTransitionMs, easing = FastOutSlowInEasing)
        ) + fadeIn(tween((sheetTransitionMs * 0.8f).toInt()))
    }

    fun playerExit(): ExitTransition {
        if (isReducedMotion) return fadeOut(tween(120))
        return slideOutVertically(
            animationSpec = tween(sheetTransitionMs, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + scaleOut(
            targetScale = 0.96f,
            animationSpec = tween((sheetTransitionMs * 0.85f).toInt())
        ) + fadeOut(tween((sheetTransitionMs * 0.75f).toInt()))
    }

    fun miniPlayerEnter(): EnterTransition {
        if (isReducedMotion) return fadeIn(tween(120))
        return slideInVertically(
            animationSpec = tween(cassetteTransitionMs, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 2 }
        ) + fadeIn(tween(cassetteTransitionMs))
    }

    fun miniPlayerExit(): ExitTransition {
        if (isReducedMotion) return fadeOut(tween(110))
        return slideOutVertically(
            animationSpec = tween(cassetteTransitionMs, easing = FastOutSlowInEasing),
            targetOffsetY = { it / 2 }
        ) + fadeOut(tween(cassetteTransitionMs))
    }
}

object GlobalMotionPlanner {
    fun compute(
        prefs: MotionPreferencesSnapshot,
        systemAnimatorsEnabled: Boolean = true
    ): EffectiveSangeetMotion {
        val effectiveReduced = prefs.reducedMotion || !systemAnimatorsEnabled
        val motionEnabled = !effectiveReduced && prefs.quality != MotionQuality.MINIMAL

        val basePageMs = when (prefs.quality) {
            MotionQuality.MINIMAL -> 140
            MotionQuality.SMOOTH -> 240
            MotionQuality.FLUID -> 290
            MotionQuality.EXPRESSIVE -> 340
        }
        val baseSheetMs = when (prefs.quality) {
            MotionQuality.MINIMAL -> 160
            MotionQuality.SMOOTH -> 280
            MotionQuality.FLUID -> 330
            MotionQuality.EXPRESSIVE -> 380
        }
        val baseCassetteMs = when (prefs.quality) {
            MotionQuality.MINIMAL -> 120
            MotionQuality.SMOOTH -> 220
            MotionQuality.FLUID -> 270
            MotionQuality.EXPRESSIVE -> 330
        }

        val scaledPageMs = if (effectiveReduced) 110 else (basePageMs * prefs.speed.durationFactor).toInt().coerceIn(90, 560)
        val scaledSheetMs = if (effectiveReduced) 120 else (baseSheetMs * prefs.speed.durationFactor).toInt().coerceIn(100, 620)
        val scaledCassetteMs = if (effectiveReduced) 100 else (baseCassetteMs * prefs.speed.durationFactor).toInt().coerceIn(80, 520)

        val baseDamping = when (prefs.quality) {
            MotionQuality.MINIMAL -> Spring.DampingRatioNoBouncy
            MotionQuality.SMOOTH -> 0.86f
            MotionQuality.FLUID -> 0.78f
            MotionQuality.EXPRESSIVE -> Spring.DampingRatioMediumBouncy
        }
        val baseStiffness = when (prefs.quality) {
            MotionQuality.MINIMAL -> Spring.StiffnessHigh
            MotionQuality.SMOOTH -> Spring.StiffnessMedium
            MotionQuality.FLUID -> Spring.StiffnessMediumLow
            MotionQuality.EXPRESSIVE -> 380f
        } * prefs.speed.stiffnessMultiplier

        val pressScale = when {
            effectiveReduced || prefs.quality == MotionQuality.MINIMAL -> 1f
            prefs.quality == MotionQuality.SMOOTH -> 0.975f
            prefs.quality == MotionQuality.FLUID -> 0.965f
            else -> 0.95f
        }

        val decorativeEnabled = !effectiveReduced &&
            !prefs.disableDecorativeAnimations &&
            prefs.quality != MotionQuality.MINIMAL

        return EffectiveSangeetMotion(
            quality = prefs.quality,
            speed = prefs.speed,
            isReducedMotion = effectiveReduced,
            isMotionEnabled = !effectiveReduced,
            decorativeAnimationsEnabled = decorativeEnabled,
            allowHorizontalSwipes = prefs.horizontalSwipesEnabled,
            allowVerticalSwipes = prefs.verticalSwipesEnabled,
            allowPlayerGestures = prefs.playerGesturesEnabled,
            allowCassetteTransitions = !effectiveReduced && prefs.cassetteTransitionsEnabled,
            allowSwipeToDismiss = prefs.swipeToDismissEnabled,
            pageTransitionMs = scaledPageMs,
            sheetTransitionMs = scaledSheetMs,
            cassetteTransitionMs = scaledCassetteMs,
            springDampingRatio = if (effectiveReduced) Spring.DampingRatioNoBouncy else baseDamping,
            springStiffness = if (effectiveReduced) Spring.StiffnessHigh else baseStiffness.coerceIn(180f, 2500f),
            cardPressScale = pressScale,
            subtleElevationDp = when {
                effectiveReduced -> 0f
                prefs.quality == MotionQuality.EXPRESSIVE -> 10f
                motionEnabled -> 6f
                else -> 2f
            }
        )
    }

    /**
     * Bridges Global Motion preferences into the Home & Cassette Artwork motion planner so
     * global accessibility toggles (Reduced motion / Disable decorative animations / Cassette transitions)
     * apply consistently everywhere.
     */
    fun mergeWithArtworkPrefs(
        artworkPrefs: ArtworkAnimationPreferencesSnapshot,
        globalPrefs: MotionPreferencesSnapshot,
        systemAnimatorsEnabled: Boolean
    ): EffectiveArtworkMotion {
        val effectiveSystemEnabled = systemAnimatorsEnabled && !globalPrefs.reducedMotion
        val adjustedPrefs = artworkPrefs.copy(
            style = when {
                globalPrefs.reducedMotion || globalPrefs.disableDecorativeAnimations -> {
                    if (globalPrefs.reducedMotion) ArtworkAnimationStyle.NONE else ArtworkAnimationStyle.SUBTLE
                }
                else -> artworkPrefs.style
            },
            enableKenBurns = artworkPrefs.enableKenBurns && !globalPrefs.disableDecorativeAnimations,
            enableAmbientLighting = artworkPrefs.enableAmbientLighting && !globalPrefs.disableDecorativeAnimations,
            enableCardBreathing = artworkPrefs.enableCardBreathing && !globalPrefs.disableDecorativeAnimations,
            enableTrackTransitions = artworkPrefs.enableTrackTransitions && globalPrefs.cassetteTransitionsEnabled
        )
        val computed = ArtworkMotionPlanner.compute(adjustedPrefs, effectiveSystemEnabled)
        return if (globalPrefs.speed != MotionSpeed.NORMAL && computed.transitionDurationMs > 0) {
            computed.copy(
                transitionDurationMs = (computed.transitionDurationMs * globalPrefs.speed.durationFactor)
                    .toInt().coerceIn(80, 750),
                spotlightTransitionDurationMs = (computed.spotlightTransitionDurationMs * globalPrefs.speed.durationFactor)
                    .toInt().coerceIn(80, 1100)
            )
        } else {
            computed
        }
    }
}

val LocalSangeetMotion = staticCompositionLocalOf {
    GlobalMotionPlanner.compute(MotionPreferencesSnapshot(), systemAnimatorsEnabled = true)
}
