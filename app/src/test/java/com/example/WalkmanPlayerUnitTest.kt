package com.example

import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.ui.screens.player.CassetteReelAnglesState
import com.example.sangeet.ui.screens.player.CassetteSwapController
import com.example.sangeet.ui.screens.player.CassetteSwapPhase
import com.example.sangeet.ui.screens.player.WalkmanTransportState
import com.example.sangeet.ui.screens.player.formatMs
import com.example.sangeet.ui.screens.player.formatWalkmanTime
import com.example.sangeet.ui.screens.player.palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkmanPlayerUnitTest {

    @Test
    fun cassetteStyles_coverAllFiveEditionsWithValidPalettes() {
        val entries = CassetteStyle.entries
        assertEquals(5, entries.size)

        val names = entries.map { it.displayName }.toSet()
        assertEquals(5, names.size)

        entries.forEach { style ->
            val p = style.palette()
            assertTrue(style.displayName.isNotBlank())
            assertTrue(style.description.isNotBlank())
            assertTrue(style.badgeText.isNotBlank())
            assertNotEquals(p.labelPaper, p.labelInk)
        }

        assertTrue(CassetteStyle.TRANSPARENT.palette().shellAlpha < 0.85f)
        assertTrue(CassetteStyle.CLASSIC_BLACK.palette().shellAlpha > 0.95f)
        assertTrue(CassetteStyle.GOLDEN.palette().shellAlpha > 0.95f)
    }

    @Test
    fun formatWalkmanTime_handlesZeroNegativeAndMinutes() {
        assertEquals("00:00", formatWalkmanTime(-500L))
        assertEquals("00:00", formatWalkmanTime(0L))
        assertEquals("00:45", formatWalkmanTime(45_000L))
        assertEquals("03:09", formatWalkmanTime(189_000L))
        assertEquals("03:09", formatMs(189_000L))
    }

    @Test
    fun cassetteReelAnglesState_preservesAnglesOnPause() {
        val state = CassetteReelAnglesState()
        assertEquals(0f, state.leftAngleDegrees, 0.001f)
        assertEquals(0f, state.rightAngleDegrees, 0.001f)

        state.leftAngleDegrees = 142.5f
        state.rightAngleDegrees = 217.25f

        assertEquals(142.5f, state.leftAngleDegrees, 0.001f)
        assertEquals(217.25f, state.rightAngleDegrees, 0.001f)
    }

    @Test
    fun swapController_initialSyncSetsTrackWithoutEnteringSwap() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = CassetteSwapController(
            scope = scope,
            reducedMotion = true,
            onPlayMechanicalSound = {}
        )
        val t1 = AudioTrack(id = 10L, title = "Midnight Cassette", artist = "Sangeet Band", album = "Analog Dreams", durationMs = 180_000L)
        controller.syncExternalTrack(t1)

        assertEquals(CassetteSwapPhase.IDLE, controller.phase)
        assertTrue(controller.isCassetteSeated)
        assertEquals(t1, controller.settledTrack)

        // Updating artwork/metadata on the same track ID should stay IDLE
        val t1Updated = t1.copy(artworkUri = "file:///cache/art_10.jpg")
        controller.syncExternalTrack(t1Updated)
        assertEquals(CassetteSwapPhase.IDLE, controller.phase)
        assertEquals("file:///cache/art_10.jpg", controller.settledTrack?.artworkUri)
    }

    @Test
    fun walkmanTransportStates_includePlayPauseAndStop() {
        assertEquals(3, WalkmanTransportState.entries.size)
        assertTrue(WalkmanTransportState.entries.contains(WalkmanTransportState.PLAYING))
        assertTrue(WalkmanTransportState.entries.contains(WalkmanTransportState.PAUSED))
        assertTrue(WalkmanTransportState.entries.contains(WalkmanTransportState.STOPPED))
    }
}
