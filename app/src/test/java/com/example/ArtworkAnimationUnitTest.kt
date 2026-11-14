package com.example

import com.example.sangeet.ui.components.ArtworkAnimationIntensity
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.ArtworkAnimationStyle
import com.example.sangeet.ui.components.ArtworkMotionPlanner
import com.example.sangeet.ui.components.ArtworkPathCache
import com.example.sangeet.ui.components.SpotlightTransitionDirection
import com.example.sangeet.ui.components.SpotlightTransitionSpeed
import com.example.sangeet.ui.components.SpotlightTransitionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkAnimationUnitTest {

    @Test
    fun motionPlanner_noneStyleDisablesAllOptionalMotion() {
        val motion = ArtworkMotionPlanner.compute(
            prefs = ArtworkAnimationPreferencesSnapshot(style = ArtworkAnimationStyle.NONE),
            systemAnimatorsEnabled = true
        )
        assertFalse(motion.isEnabled)
        assertFalse(motion.kenBurnsActive)
        assertFalse(motion.parallaxActive)
        assertFalse(motion.ambientLightingActive)
        assertFalse(motion.cardBreathingActive)
        assertFalse(motion.trackTransitionsActive)
        assertEquals(1.0f, motion.zoomMaxScale, 0.0001f)
        assertEquals(0f, motion.kenBurnsPanPx, 0.0001f)
        assertEquals(0, motion.transitionDurationMs)
    }

    @Test
    fun motionPlanner_respectsAndroidReducedMotionWhenEnabled() {
        val respected = ArtworkMotionPlanner.compute(
            prefs = ArtworkAnimationPreferencesSnapshot(
                style = ArtworkAnimationStyle.CINEMATIC,
                intensity = ArtworkAnimationIntensity.HIGH,
                spotlightTransitionStyle = SpotlightTransitionStyle.PAGE_TURN,
                respectReducedMotion = true
            ),
            systemAnimatorsEnabled = false
        )
        assertFalse(respected.isEnabled)
        assertEquals(SpotlightTransitionStyle.NONE, respected.spotlightTransitionStyle)
        assertEquals(0, respected.spotlightTransitionDurationMs)

        val overridden = ArtworkMotionPlanner.compute(
            prefs = ArtworkAnimationPreferencesSnapshot(
                style = ArtworkAnimationStyle.CINEMATIC,
                intensity = ArtworkAnimationIntensity.HIGH,
                spotlightTransitionStyle = SpotlightTransitionStyle.PAGE_TURN,
                spotlightTransitionSpeed = SpotlightTransitionSpeed.SLOW,
                respectReducedMotion = false
            ),
            systemAnimatorsEnabled = false
        )
        assertTrue(overridden.isEnabled)
        assertTrue(overridden.kenBurnsActive)
        assertTrue(overridden.ambientLightingActive)
        assertEquals(SpotlightTransitionStyle.PAGE_TURN, overridden.spotlightTransitionStyle)
        assertEquals(SpotlightTransitionSpeed.SLOW.durationMs, overridden.spotlightTransitionDurationMs)
    }

    @Test
    fun motionPlanner_stylesAndIntensityScaleCleanlyWithinClamps() {
        val subtleLow = ArtworkMotionPlanner.compute(
            ArtworkAnimationPreferencesSnapshot(
                style = ArtworkAnimationStyle.SUBTLE,
                intensity = ArtworkAnimationIntensity.LOW
            )
        )
        val smoothMed = ArtworkMotionPlanner.compute(
            ArtworkAnimationPreferencesSnapshot(
                style = ArtworkAnimationStyle.SMOOTH,
                intensity = ArtworkAnimationIntensity.MEDIUM
            )
        )
        val cinematicHigh = ArtworkMotionPlanner.compute(
            ArtworkAnimationPreferencesSnapshot(
                style = ArtworkAnimationStyle.CINEMATIC,
                intensity = ArtworkAnimationIntensity.HIGH
            )
        )

        assertTrue(subtleLow.isEnabled)
        assertFalse(subtleLow.ambientLightingActive) // Subtle keeps ambient sheen off
        assertTrue(smoothMed.ambientLightingActive)
        assertTrue(cinematicHigh.zoomMaxScale > smoothMed.zoomMaxScale)
        assertTrue(smoothMed.zoomMaxScale > subtleLow.zoomMaxScale)
        assertTrue(cinematicHigh.zoomMaxScale <= 1.16f)
        assertTrue(cinematicHigh.transitionDurationMs > smoothMed.transitionDurationMs)
    }

    @Test
    fun motionPlanner_individualTogglesControlEachEffect() {
        val custom = ArtworkMotionPlanner.compute(
            ArtworkAnimationPreferencesSnapshot(
                style = ArtworkAnimationStyle.CINEMATIC,
                enableKenBurns = false,
                enableParallax = false,
                enableAmbientLighting = true,
                enableCardBreathing = false,
                enableTrackTransitions = true
            )
        )
        assertTrue(custom.isEnabled)
        assertFalse(custom.kenBurnsActive)
        assertFalse(custom.parallaxActive)
        assertTrue(custom.ambientLightingActive)
        assertFalse(custom.cardBreathingActive)
        assertTrue(custom.trackTransitionsActive)
    }

    @Test
    fun spotlightTransitions_speedAndDirectionResolveAccurately() {
        assertEquals(1, ArtworkMotionPlanner.resolveSpotlightDirectionSign(SpotlightTransitionDirection.AUTOMATIC, 1))
        assertEquals(-1, ArtworkMotionPlanner.resolveSpotlightDirectionSign(SpotlightTransitionDirection.AUTOMATIC, -1))
        assertEquals(-1, ArtworkMotionPlanner.resolveSpotlightDirectionSign(SpotlightTransitionDirection.LEFT_TO_RIGHT, 1))
        assertEquals(1, ArtworkMotionPlanner.resolveSpotlightDirectionSign(SpotlightTransitionDirection.RIGHT_TO_LEFT, -1))

        val slowPageTurn = ArtworkMotionPlanner.compute(
            ArtworkAnimationPreferencesSnapshot(
                spotlightTransitionStyle = SpotlightTransitionStyle.PAGE_TURN,
                spotlightTransitionSpeed = SpotlightTransitionSpeed.SLOW,
                spotlightTransitionDirection = SpotlightTransitionDirection.LEFT_TO_RIGHT
            )
        )
        assertEquals(SpotlightTransitionStyle.PAGE_TURN, slowPageTurn.spotlightTransitionStyle)
        assertEquals(820, slowPageTurn.spotlightTransitionDurationMs)
        assertEquals(SpotlightTransitionDirection.LEFT_TO_RIGHT, slowPageTurn.spotlightTransitionDirection)

        val fastFade = ArtworkMotionPlanner.compute(
            ArtworkAnimationPreferencesSnapshot(
                spotlightTransitionStyle = SpotlightTransitionStyle.FADE,
                spotlightTransitionSpeed = SpotlightTransitionSpeed.FAST
            )
        )
        assertEquals(SpotlightTransitionStyle.FADE, fastFade.spotlightTransitionStyle)
        assertEquals(300, fastFade.spotlightTransitionDurationMs)

        val none = ArtworkMotionPlanner.compute(
            ArtworkAnimationPreferencesSnapshot(
                spotlightTransitionStyle = SpotlightTransitionStyle.NONE,
                spotlightTransitionSpeed = SpotlightTransitionSpeed.SLOW
            )
        )
        assertEquals(0, none.spotlightTransitionDurationMs)
    }

    @Test
    fun artworkPathCache_storesAndClearsCleanly() {
        ArtworkPathCache.clear()
        assertNull(ArtworkPathCache.get(42L))
        ArtworkPathCache.put(42L, "/storage/emulated/0/Music/cover.jpg")
        assertEquals("/storage/emulated/0/Music/cover.jpg", ArtworkPathCache.get(42L))
        ArtworkPathCache.clear()
        assertNull(ArtworkPathCache.get(42L))
    }
}
