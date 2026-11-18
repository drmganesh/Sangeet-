package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.ArtworkAnimationStyle
import com.example.sangeet.ui.components.SpotlightTransitionStyle
import com.example.sangeet.ui.motion.EdgeSafeSwipeEvaluator
import com.example.sangeet.ui.motion.GlobalMotionPlanner
import com.example.sangeet.ui.motion.MotionPreferencesSnapshot
import com.example.sangeet.ui.motion.MotionQuality
import com.example.sangeet.ui.motion.MotionSpeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlobalMotionSystemUnitTest {

    @Test
    fun motionPreferencesDefaults_matchSmoothFluidSpec() {
        val defaults = MotionPreferencesSnapshot()
        assertEquals(MotionQuality.FLUID, defaults.quality)
        assertEquals(MotionSpeed.NORMAL, defaults.speed)
        assertTrue(defaults.horizontalSwipesEnabled)
        assertTrue(defaults.verticalSwipesEnabled)
        assertTrue(defaults.playerGesturesEnabled)
        assertTrue(defaults.cassetteTransitionsEnabled)
        assertTrue(defaults.swipeToDismissEnabled)
        assertFalse(defaults.reducedMotion)
        assertFalse(defaults.disableDecorativeAnimations)
    }

    @Test
    fun globalMotionPlanner_scalesQualitiesAndSpeedsProperly() {
        val minimal = GlobalMotionPlanner.compute(MotionPreferencesSnapshot(quality = MotionQuality.MINIMAL))
        val smooth = GlobalMotionPlanner.compute(MotionPreferencesSnapshot(quality = MotionQuality.SMOOTH))
        val fluid = GlobalMotionPlanner.compute(MotionPreferencesSnapshot(quality = MotionQuality.FLUID))
        val expressive = GlobalMotionPlanner.compute(MotionPreferencesSnapshot(quality = MotionQuality.EXPRESSIVE))

        assertTrue(minimal.pageTransitionMs < smooth.pageTransitionMs)
        assertTrue(smooth.pageTransitionMs < fluid.pageTransitionMs)
        assertTrue(fluid.pageTransitionMs < expressive.pageTransitionMs)

        assertFalse(minimal.decorativeAnimationsEnabled)
        assertTrue(smooth.decorativeAnimationsEnabled)
        assertTrue(fluid.decorativeAnimationsEnabled)
        assertTrue(expressive.decorativeAnimationsEnabled)
        assertTrue(expressive.subtleElevationDp > fluid.subtleElevationDp)

        val slow = GlobalMotionPlanner.compute(MotionPreferencesSnapshot(speed = MotionSpeed.SLOW))
        val fast = GlobalMotionPlanner.compute(MotionPreferencesSnapshot(speed = MotionSpeed.FAST))
        assertTrue(slow.pageTransitionMs > fluid.pageTransitionMs)
        assertTrue(fluid.pageTransitionMs > fast.pageTransitionMs)
        assertTrue(slow.sheetTransitionMs > fast.sheetTransitionMs)
        assertTrue(slow.cassetteTransitionMs > fast.cassetteTransitionMs)
    }

    @Test
    fun globalMotionPlanner_enforcesReducedMotionAndDecorativeDisables() {
        val userReduced = GlobalMotionPlanner.compute(
            prefs = MotionPreferencesSnapshot(
                quality = MotionQuality.EXPRESSIVE,
                reducedMotion = true
            ),
            systemAnimatorsEnabled = true
        )
        assertTrue(userReduced.isReducedMotion)
        assertFalse(userReduced.isMotionEnabled)
        assertFalse(userReduced.decorativeAnimationsEnabled)
        assertFalse(userReduced.allowCassetteTransitions)
        assertEquals(1.0f, userReduced.cardPressScale, 0.0001f)
        assertEquals(0.0f, userReduced.subtleElevationDp, 0.0001f)

        val systemReduced = GlobalMotionPlanner.compute(
            prefs = MotionPreferencesSnapshot(quality = MotionQuality.FLUID, reducedMotion = false),
            systemAnimatorsEnabled = false
        )
        assertTrue(systemReduced.isReducedMotion)
        assertFalse(systemReduced.decorativeAnimationsEnabled)

        val noDecorative = GlobalMotionPlanner.compute(
            prefs = MotionPreferencesSnapshot(
                quality = MotionQuality.FLUID,
                disableDecorativeAnimations = true
            ),
            systemAnimatorsEnabled = true
        )
        assertFalse(noDecorative.isReducedMotion)
        assertTrue(noDecorative.isMotionEnabled)
        assertFalse(noDecorative.decorativeAnimationsEnabled)
        assertTrue(noDecorative.allowCassetteTransitions)
    }

    @Test
    fun mergeWithArtworkPrefs_respectsGlobalAccessibilityAndSpeed() {
        val baseArtwork = ArtworkAnimationPreferencesSnapshot(
            style = ArtworkAnimationStyle.CINEMATIC,
            enableKenBurns = true,
            enableAmbientLighting = true,
            enableCardBreathing = true,
            enableTrackTransitions = true,
            spotlightTransitionStyle = SpotlightTransitionStyle.PAGE_TURN
        )

        val noDecor = GlobalMotionPlanner.mergeWithArtworkPrefs(
            artworkPrefs = baseArtwork,
            globalPrefs = MotionPreferencesSnapshot(
                disableDecorativeAnimations = true,
                cassetteTransitionsEnabled = true
            ),
            systemAnimatorsEnabled = true
        )
        assertTrue(noDecor.isEnabled)
        assertFalse(noDecor.kenBurnsActive)
        assertFalse(noDecor.ambientLightingActive)
        assertFalse(noDecor.cardBreathingActive)
        assertTrue(noDecor.trackTransitionsActive)

        val reduced = GlobalMotionPlanner.mergeWithArtworkPrefs(
            artworkPrefs = baseArtwork,
            globalPrefs = MotionPreferencesSnapshot(reducedMotion = true),
            systemAnimatorsEnabled = true
        )
        assertFalse(reduced.isEnabled)
        assertEquals(SpotlightTransitionStyle.NONE, reduced.spotlightTransitionStyle)

        val slowMerged = GlobalMotionPlanner.mergeWithArtworkPrefs(
            artworkPrefs = baseArtwork,
            globalPrefs = MotionPreferencesSnapshot(speed = MotionSpeed.SLOW),
            systemAnimatorsEnabled = true
        )
        val fastMerged = GlobalMotionPlanner.mergeWithArtworkPrefs(
            artworkPrefs = baseArtwork,
            globalPrefs = MotionPreferencesSnapshot(speed = MotionSpeed.FAST),
            systemAnimatorsEnabled = true
        )
        assertTrue(slowMerged.transitionDurationMs > fastMerged.transitionDurationMs)
        assertTrue(slowMerged.spotlightTransitionDurationMs > fastMerged.spotlightTransitionDurationMs)
    }

    @Test
    fun edgeSafeSwipeEvaluator_guardsSystemEdgesAndLocksDirectionalAxis() {
        val guardPx = 48f
        val widthPx = 1080f

        assertTrue(EdgeSafeSwipeEvaluator.isInsideEdgeGuard(12f, widthPx, guardPx))
        assertTrue(EdgeSafeSwipeEvaluator.isInsideEdgeGuard(1060f, widthPx, guardPx))
        assertFalse(EdgeSafeSwipeEvaluator.isInsideEdgeGuard(540f, widthPx, guardPx))
        assertFalse(EdgeSafeSwipeEvaluator.isInsideEdgeGuard(8f, 90f, guardPx))

        val slop = 24f
        assertEquals(
            EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED,
            EdgeSafeSwipeEvaluator.resolveDragAxis(10f, 10f, slop)
        )
        assertEquals(
            EdgeSafeSwipeEvaluator.DragAxis.HORIZONTAL,
            EdgeSafeSwipeEvaluator.resolveDragAxis(-40f, 12f, slop)
        )
        assertEquals(
            EdgeSafeSwipeEvaluator.DragAxis.VERTICAL,
            EdgeSafeSwipeEvaluator.resolveDragAxis(14f, 48f, slop)
        )
    }

    @Test
    fun edgeSafeSwipeEvaluator_handlesVelocityDistanceAndCooldown() {
        val distThreshold = 120f

        // High flick velocity commits even with shorter distance (> 35% of threshold)
        assertEquals(
            EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_NEGATIVE,
            EdgeSafeSwipeEvaluator.evaluateSwipeCommit(
                totalOffset = -55f,
                velocityPerSec = -800f,
                distanceThresholdPx = distThreshold
            )
        )
        assertEquals(
            EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_POSITIVE,
            EdgeSafeSwipeEvaluator.evaluateSwipeCommit(
                totalOffset = 55f,
                velocityPerSec = 780f,
                distanceThresholdPx = distThreshold
            )
        )

        // Slow drag past threshold still commits
        assertEquals(
            EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_NEGATIVE,
            EdgeSafeSwipeEvaluator.evaluateSwipeCommit(
                totalOffset = -135f,
                velocityPerSec = -100f,
                distanceThresholdPx = distThreshold
            )
        )

        // Short slow drag cancels back cleanly
        assertEquals(
            EdgeSafeSwipeEvaluator.SwipeDecision.CANCEL,
            EdgeSafeSwipeEvaluator.evaluateSwipeCommit(
                totalOffset = 40f,
                velocityPerSec = 150f,
                distanceThresholdPx = distThreshold
            )
        )

        // Debounce cooldown prevents duplicate skip/dismiss commits
        assertTrue(EdgeSafeSwipeEvaluator.canAcceptSwipeCommit(nowMs = 1000L, lastCommitMs = 0L))
        assertFalse(EdgeSafeSwipeEvaluator.canAcceptSwipeCommit(nowMs = 1150L, lastCommitMs = 1000L))
        assertTrue(EdgeSafeSwipeEvaluator.canAcceptSwipeCommit(nowMs = 1320L, lastCommitMs = 1000L))
    }

    @Test
    fun settingsRepository_persistsAndResetsGlobalMotionPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)

        repo.resetMotionSettingsToDefault()
        val initial = repo.motionPreferences.first()
        assertEquals(MotionQuality.FLUID, initial.quality)
        assertEquals(MotionSpeed.NORMAL, initial.speed)
        assertTrue(initial.horizontalSwipesEnabled)
        assertFalse(initial.reducedMotion)

        repo.setMotionQuality(MotionQuality.EXPRESSIVE)
        repo.setMotionSpeed(MotionSpeed.FAST)
        repo.setMotionHorizontalSwipesEnabled(false)
        repo.setMotionVerticalSwipesEnabled(false)
        repo.setMotionPlayerGesturesEnabled(false)
        repo.setMotionCassetteTransitionsEnabled(false)
        repo.setMotionSwipeToDismissEnabled(false)
        repo.setMotionReducedMotion(true)
        repo.setMotionDisableDecorativeAnimations(true)

        val updated = repo.motionPreferences.first()
        assertEquals(MotionQuality.EXPRESSIVE, updated.quality)
        assertEquals(MotionSpeed.FAST, updated.speed)
        assertFalse(updated.horizontalSwipesEnabled)
        assertFalse(updated.verticalSwipesEnabled)
        assertFalse(updated.playerGesturesEnabled)
        assertFalse(updated.cassetteTransitionsEnabled)
        assertFalse(updated.swipeToDismissEnabled)
        assertTrue(updated.reducedMotion)
        assertTrue(updated.disableDecorativeAnimations)

        repo.resetMotionSettingsToDefault()
        val reset = repo.motionPreferences.first()
        assertEquals(MotionPreferencesSnapshot(), reset)
    }
}
