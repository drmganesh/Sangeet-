package com.example

import com.example.sangeet.ui.startup.StartupAnimationPreferencesSnapshot
import com.example.sangeet.ui.startup.StartupAnimationSpeed
import com.example.sangeet.ui.startup.StartupAnimationStyle
import com.example.sangeet.ui.startup.StartupScene
import com.example.sangeet.ui.startup.StartupTimelinePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupAnimationUnitTest {

    @Test
    fun startupDefaults_matchSpecification() {
        val defaults = StartupAnimationPreferencesSnapshot()
        assertEquals(StartupAnimationStyle.FULL_WALKMAN, defaults.style)
        assertEquals(StartupAnimationSpeed.NORMAL, defaults.speed)
        assertFalse(defaults.mechanicalSoundsEnabled)
        assertTrue(defaults.vibrationEnabled)
    }

    @Test
    fun buildPlan_handlesModesSpeedsNotificationAndReducedMotion() {
        val defaults = StartupAnimationPreferencesSnapshot()

        val fullNormal = StartupTimelinePlanner.buildPlan(
            prefs = defaults,
            isReducedMotion = false,
            isNotificationPlayerLaunch = false,
            isPreview = false
        )
        assertTrue(fullNormal.shouldPlay)
        assertEquals(StartupAnimationStyle.FULL_WALKMAN, fullNormal.effectiveStyle)

        val fullSlow = StartupTimelinePlanner.buildPlan(
            prefs = defaults.copy(speed = StartupAnimationSpeed.SLOW),
            isReducedMotion = false,
            isNotificationPlayerLaunch = false,
            isPreview = false
        )
        val fullFast = StartupTimelinePlanner.buildPlan(
            prefs = defaults.copy(speed = StartupAnimationSpeed.FAST),
            isReducedMotion = false,
            isNotificationPlayerLaunch = false,
            isPreview = false
        )
        assertTrue(fullSlow.totalDurationMs > fullNormal.totalDurationMs)
        assertTrue(fullNormal.totalDurationMs > fullFast.totalDurationMs)

        val notifLaunch = StartupTimelinePlanner.buildPlan(
            prefs = defaults,
            isReducedMotion = false,
            isNotificationPlayerLaunch = true,
            isPreview = false
        )
        assertFalse(notifLaunch.shouldPlay)

        val disabledLaunch = StartupTimelinePlanner.buildPlan(
            prefs = defaults.copy(style = StartupAnimationStyle.DISABLED),
            isReducedMotion = false,
            isNotificationPlayerLaunch = false,
            isPreview = false
        )
        assertFalse(disabledLaunch.shouldPlay)

        val disabledPreview = StartupTimelinePlanner.buildPlan(
            prefs = defaults.copy(style = StartupAnimationStyle.DISABLED),
            isReducedMotion = false,
            isNotificationPlayerLaunch = false,
            isPreview = true
        )
        assertTrue(disabledPreview.shouldPlay)
        assertEquals(StartupAnimationStyle.FULL_WALKMAN, disabledPreview.effectiveStyle)

        val reduced = StartupTimelinePlanner.buildPlan(
            prefs = defaults,
            isReducedMotion = true,
            isNotificationPlayerLaunch = false,
            isPreview = false
        )
        assertTrue(reduced.shouldPlay)
        assertTrue(reduced.isReducedMotionFallback)
        assertTrue(reduced.totalDurationMs <= 500)
    }

    @Test
    fun fullWalkmanTimeline_progressesThroughAllEightScenesInExactOrder() {
        val samplePoints = listOf(0.05f, 0.23f, 0.38f, 0.53f, 0.66f, 0.78f, 0.90f, 0.98f)
        val expectedScenes = listOf(
            StartupScene.WALKMAN_APPEARS,
            StartupScene.DOOR_OPENS,
            StartupScene.CASSETTE_ENTERS,
            StartupScene.DOOR_CLOSES,
            StartupScene.PLAY_SWITCH,
            StartupScene.WALKMAN_TRANSITION,
            StartupScene.BRAND_REVEAL,
            StartupScene.ENTER_APP
        )

        val actualScenes = samplePoints.map { p ->
            StartupTimelinePlanner.evaluate(
                progress = p,
                style = StartupAnimationStyle.FULL_WALKMAN,
                reducedMotionFallback = false
            ).activeScene
        }
        assertEquals(expectedScenes, actualScenes)

        // Verify mechanical choreography physics invariants:
        val duringDoorOpen = StartupTimelinePlanner.evaluate(0.28f, StartupAnimationStyle.FULL_WALKMAN, false)
        assertTrue(duringDoorOpen.doorOpenProgress > 0.7f)
        assertFalse(duringDoorOpen.cassetteVisible)

        val duringCassetteInsert = StartupTimelinePlanner.evaluate(0.39f, StartupAnimationStyle.FULL_WALKMAN, false)
        assertTrue(duringCassetteInsert.cassetteVisible)
        assertTrue(duringCassetteInsert.doorOpenProgress > 0.95f)

        val duringPlaySwitch = StartupTimelinePlanner.evaluate(0.68f, StartupAnimationStyle.FULL_WALKMAN, false)
        assertTrue(duringPlaySwitch.playSwitchDepress > 0.9f)
        assertEquals(0f, duringPlaySwitch.doorOpenProgress, 0.001f)
        assertTrue(duringPlaySwitch.reelSpinDegrees > 0f)

        val duringBrandReveal = StartupTimelinePlanner.evaluate(0.93f, StartupAnimationStyle.FULL_WALKMAN, false)
        assertTrue(duringBrandReveal.emblemAlpha > 0.9f)
        assertTrue(duringBrandReveal.emblemRingSweep > 0.9f)
        assertTrue(duringBrandReveal.brandTitleAlpha > 0.9f)
        assertTrue(duringBrandReveal.brandCreditAlpha > 0.8f)

        val atFinish = StartupTimelinePlanner.evaluate(1.0f, StartupAnimationStyle.FULL_WALKMAN, false)
        assertEquals(0f, atFinish.overlayAlpha, 0.001f)
    }

    @Test
    fun shortAndLogoOnlyModes_reachCleanFinish() {
        val shortReveal = StartupTimelinePlanner.evaluate(0.90f, StartupAnimationStyle.SHORT_WALKMAN, false)
        assertEquals(StartupScene.BRAND_REVEAL, shortReveal.activeScene)
        assertTrue(shortReveal.emblemRingSweep > 0.9f)

        val logoMid = StartupTimelinePlanner.evaluate(0.50f, StartupAnimationStyle.LOGO_ONLY, false)
        assertEquals(StartupScene.BRAND_REVEAL, logoMid.activeScene)
        assertEquals(0f, logoMid.walkmanAlpha, 0.001f)
        assertTrue(logoMid.emblemAlpha > 0.9f)

        val logoEnd = StartupTimelinePlanner.evaluate(1.0f, StartupAnimationStyle.LOGO_ONLY, false)
        assertEquals(0f, logoEnd.overlayAlpha, 0.001f)
    }
}
