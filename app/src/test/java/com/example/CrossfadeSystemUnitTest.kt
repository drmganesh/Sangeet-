package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.core.app.ApplicationProvider
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.dsp.DeckTransitionAudioProcessor
import com.example.sangeet.dsp.EqualizerAudioProcessor
import com.example.sangeet.playback.PlaybackManager
import com.example.sangeet.playback.crossfade.BeatAnalysisResult
import com.example.sangeet.playback.crossfade.CrossfadeCurves
import com.example.sangeet.playback.crossfade.CrossfadeMode
import com.example.sangeet.playback.crossfade.CrossfadePreferencesSnapshot
import com.example.sangeet.playback.crossfade.DjTransitionStyle
import com.example.sangeet.playback.crossfade.LocalAudioBeatAnalyzer
import com.example.sangeet.ui.screens.CrossfadeSettingsScreen
import com.example.sangeet.ui.screens.player.DjCrossfadeDeckSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class CrossfadeSystemUnitTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun equalPowerCurves_maintainConstantPerceivedPowerAcrossTransition() {
        for (step in 0..20) {
            val t = step / 20f
            val g = CrossfadeCurves.computeGains(
                progress = t,
                mode = CrossfadeMode.ADVANCED,
                style = DjTransitionStyle.EQUAL_POWER,
                equalPowerEnabled = true
            )
            val totalPower = (g.outgoingGain * g.outgoingGain) + (g.incomingGain * g.incomingGain)
            assertEquals("Expected constant equal-power sum at t=$t", 1.0f, totalPower, 0.01f)
            assertTrue(g.overlapHeadroomGain in 0.80f..1.0f)
        }
    }

    @Test
    fun allSixDjPresets_haveValidCurvesAndBassSwapAttenuatesLowEndWithoutTouchingEq() {
        DjTransitionStyle.entries.forEach { preset ->
            val start = CrossfadeCurves.computeGains(0f, CrossfadeMode.DJ_STYLE, preset, true)
            val mid = CrossfadeCurves.computeGains(0.5f, CrossfadeMode.DJ_STYLE, preset, true)
            val end = CrossfadeCurves.computeGains(1f, CrossfadeMode.DJ_STYLE, preset, true)

            assertTrue("Preset ${preset.name} start out should be high", start.outgoingGain >= 0.9f)
            assertEquals("Preset ${preset.name} start in", 0f, start.incomingGain, 0.01f)
            assertTrue("Preset ${preset.name} mid overlap active", mid.outgoingGain > 0.25f && mid.incomingGain > 0.35f)
            assertEquals("Preset ${preset.name} end out", 0f, end.outgoingGain, 0.01f)
            assertTrue("Preset ${preset.name} end in should be high", end.incomingGain >= 0.9f)
        }

        // Verify Bass Swap DSP curve prevents low-end buildup at midpoint
        val bassStart = CrossfadeCurves.computeBassSwapAttenuationDb(0.0f, CrossfadeMode.DJ_STYLE, DjTransitionStyle.BASS_SWAP)
        val bassMid = CrossfadeCurves.computeBassSwapAttenuationDb(0.5f, CrossfadeMode.DJ_STYLE, DjTransitionStyle.BASS_SWAP)
        val bassEnd = CrossfadeCurves.computeBassSwapAttenuationDb(1.0f, CrossfadeMode.DJ_STYLE, DjTransitionStyle.BASS_SWAP)

        assertEquals(0f, bassStart.outgoingBassCutDb, 0.05f)
        assertEquals(-18f, bassStart.incomingBassCutDb, 0.05f)
        assertTrue("Both channels dip sub-bass at midpoint", bassMid.outgoingBassCutDb < -5f && bassMid.incomingBassCutDb < -5f)
        assertEquals(-18f, bassEnd.outgoingBassCutDb, 0.05f)
        assertEquals(0f, bassEnd.incomingBassCutDb, 0.05f)

        // Verify actual DeckTransitionAudioProcessor Biquad DSP attenuates 75Hz bass while leaving EqualizerAudioProcessor untouched
        val eq = EqualizerAudioProcessor()
        eq.setBandGain(2, 6.0f)

        val dsp = DeckTransitionAudioProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        dsp.configure(format)
        dsp.flush()

        // Generate 4096 stereo frames of 75Hz sub-bass sine
        val frames = 4096
        val inputDry = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val s = (sin(2.0 * PI * 75.0 * i / 44100.0) * 16000.0).roundToInt().toShort()
            inputDry.putShort(s)
            inputDry.putShort(s)
        }
        inputDry.flip()

        // Passthrough check
        dsp.updateTransitionDsp(bassCutDb = 0f, headroomGain = 1.0f)
        dsp.queueInput(inputDry.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        val passOut = dsp.output
        assertEquals(frames * 4, passOut.remaining())

        // Attenuated Bass Swap check (-18 dB low shelf at 145 Hz)
        dsp.updateTransitionDsp(bassCutDb = -18f, headroomGain = 0.92f)
        dsp.queueInput(inputDry.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        val cutOut = dsp.output.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        while (cutOut.hasRemaining()) {
            val sample = cutOut.short.toDouble() / 32768.0
            sumSq += sample * sample
        }
        val attenuatedRms = sqrt(sumSq / (frames * 2))
        val dryRms = (16000.0 / 32768.0) / sqrt(2.0)
        assertTrue("Expected significant low-frequency attenuation ($attenuatedRms vs $dryRms)", attenuatedRms < dryRms * 0.45)
        assertEquals("Existing 15-band EQ gain must never be altered", 6.0f, eq.getBandGain(2), 0.001f)
    }

    @Test
    fun beatAnalyzer_detects120And136BpmAndRejectsFlatAmbientSignal() {
        val hopMs = LocalAudioBeatAnalyzer.HOP_MS
        val totalFrames = 2000 // 20 seconds at 100Hz hop

        fun generatePulseEnvelope(bpm: Float): Pair<FloatArray, FloatArray> {
            val beatPeriodMs = 60_000f / bpm
            val full = FloatArray(totalFrames)
            val low = FloatArray(totalFrames)
            for (i in 0 until totalFrames) {
                val timeMs = i * hopMs
                val phaseMs = timeMs % beatPeriodMs
                val kick = exp(-phaseMs / 45f)
                full[i] = 0.08f + kick * 0.65f
                low[i] = 0.04f + kick * 0.85f
            }
            return full to low
        }

        val (env120, low120) = generatePulseEnvelope(120f)
        val res120 = LocalAudioBeatAnalyzer.analyzeEnergyEnvelope(
            trackId = 1L,
            rmsEnvelope = env120,
            lowBandEnvelope = low120,
            hopMs = hopMs,
            windowOffsetMs = 10_000L,
            totalDurationMs = 180_000L
        )
        assertTrue("Expected reliable beat detection for 120 BPM", res120.isReliable)
        assertTrue("Expected BPM near 120, got ${res120.bpm}", res120.bpm != null && abs(res120.bpm!! - 120f) <= 2f)

        val (env136, low136) = generatePulseEnvelope(136f)
        val res136 = LocalAudioBeatAnalyzer.analyzeEnergyEnvelope(
            trackId = 2L,
            rmsEnvelope = env136,
            lowBandEnvelope = low136,
            hopMs = hopMs,
            windowOffsetMs = 10_000L,
            totalDurationMs = 180_000L
        )
        assertTrue("Expected reliable beat detection for 136 BPM", res136.isReliable)
        assertTrue("Expected BPM near 136, got ${res136.bpm}", res136.bpm != null && abs(res136.bpm!! - 136f) <= 2f)

        // Flat non-rhythmic steady signal must fall back honestly instead of faking BPM
        val flat = FloatArray(totalFrames) { 0.35f }
        val resFlat = LocalAudioBeatAnalyzer.analyzeEnergyEnvelope(
            trackId = 3L,
            rmsEnvelope = flat,
            lowBandEnvelope = flat,
            hopMs = hopMs
        )
        assertFalse(resFlat.isReliable)
        assertNull(resFlat.bpm)
    }

    @Test
    fun transitionPlanner_handlesShortSongsBeatSyncAndLowConfidenceFallback() {
        // 1. Very short track (<3.5s) skips overlap
        val tooShort = CrossfadeCurves.resolveEffectiveDurationMs(3_000L, 180_000L, 10)
        assertEquals(0L, tooShort)

        // 2. Song shorter than selected duration scales safely to <= 42%
        val capped = CrossfadeCurves.resolveEffectiveDurationMs(10_000L, 10_000L, 12)
        assertTrue("Expected duration <= 4200ms, got $capped", capped in 1000L..4200L)

        // 3. Reliable DJ beat analysis aligns duration & intro entry
        val prefs = CrossfadePreferencesSnapshot(
            mode = CrossfadeMode.DJ_STYLE,
            djDurationSeconds = 8,
            djBeatSyncEnabled = true,
            djAutoBpmDetectionEnabled = true,
            djTransitionStyle = DjTransitionStyle.BASS_SWAP
        )
        val outBeat = BeatAnalysisResult(
            trackId = 10L, bpm = 120f, confidence = 0.85f, isReliable = true,
            beatIntervalMs = 500L, firstDownbeatMs = 0L, outroPhraseStartMs = 170_000L
        )
        val inBeat = BeatAnalysisResult(
            trackId = 11L, bpm = 120f, confidence = 0.82f, isReliable = true,
            beatIntervalMs = 500L, firstDownbeatMs = 500L, introEntryMs = 1_200L
        )
        val beatPlan = CrossfadeCurves.planTransitionTiming(180_000L, 180_000L, prefs, outBeat, inBeat)
        assertTrue(beatPlan.isBeatSynced)
        assertEquals(1_200L, beatPlan.incomingStartPositionMs)
        assertEquals(0L, beatPlan.effectiveDurationMs % 500L)

        // 4. Low confidence drops cleanly to time-based fallback
        val lowBeat = BeatAnalysisResult(trackId = 12L, bpm = null, confidence = 0.1f, isReliable = false)
        val fallbackPlan = CrossfadeCurves.planTransitionTiming(180_000L, 180_000L, prefs, lowBeat, inBeat)
        assertFalse(fallbackPlan.isBeatSynced)
        assertEquals(8_000L, fallbackPlan.effectiveDurationMs)
    }

    @Test
    fun crossfadeSettings_persistAcrossReadsWithoutDisturbingEqualizerSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository.getInstance(context)

        repo.setEqualizerPresetName("Jazz")
        repo.setCrossfadeMode(CrossfadeMode.DJ_STYLE)
        repo.setCrossfadeSeconds(9)
        repo.setCrossfadeAutoEnabled(false)
        repo.setCrossfadeEqualPower(true)
        repo.setDjDurationSeconds(14)
        repo.setDjBeatSyncEnabled(false)
        repo.setDjAutoBpmDetectionEnabled(true)
        repo.setDjTransitionStyle(DjTransitionStyle.LONG_MIX)
        repo.setDjIncomingVolume(0.88f)
        repo.setDjOutgoingVolume(0.92f)

        val saved = repo.crossfadePreferences.first()
        assertEquals(CrossfadeMode.DJ_STYLE, saved.mode)
        assertEquals(9, saved.advancedDurationSeconds)
        assertFalse(saved.autoCrossfadeEnabled)
        assertTrue(saved.equalPowerCurvesEnabled)
        assertEquals(14, saved.djDurationSeconds)
        assertFalse(saved.djBeatSyncEnabled)
        assertTrue(saved.djAutoBpmDetectionEnabled)
        assertEquals(DjTransitionStyle.LONG_MIX, saved.djTransitionStyle)
        assertEquals(0.88f, saved.djIncomingVolumeCap, 0.001f)
        assertEquals(0.92f, saved.djOutgoingVolumeCap, 0.001f)
        assertEquals("Jazz", repo.eqPresetName.first())

        repo.resetCrossfadeSettingsToDefault()
        assertEquals(CrossfadeMode.OFF, repo.crossfadePreferences.first().mode)
        assertEquals("Jazz", repo.eqPresetName.first())
    }

    @Test
    fun crossfadeSettingsScreen_rendersModesPresetsAndControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val playbackManager = PlaybackManager.getInstance(context)

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CrossfadeSettingsScreen(
                    playbackManager = playbackManager,
                    onNavigateBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crossfade_settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("crossfade_mode_off").assertIsDisplayed()
        composeRule.onNodeWithTag("crossfade_mode_advanced").assertIsDisplayed()
        composeRule.onNodeWithTag("crossfade_mode_dj_style").assertIsDisplayed()

        composeRule.onNodeWithTag("crossfade_settings_list").performScrollToNode(hasTestTag("preset_card_bass_swap"))
        composeRule.onNodeWithTag("preset_card_bass_swap").assertIsDisplayed()
        composeRule.onNodeWithTag("preset_card_bass_swap").performClick()

        composeRule.onNodeWithTag("crossfade_settings_list").performScrollToNode(hasTestTag("dj_manual_trigger_settings_btn"))
        composeRule.onNodeWithTag("dj_manual_trigger_settings_btn").assertIsDisplayed()
        composeRule.onNodeWithText("Trigger Manual DJ Transition").assertIsDisplayed()
    }

    @Test
    fun djCrossfadeDeckSheet_rendersDualDecksMetersAndManualTrigger() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val playbackManager = PlaybackManager.getInstance(context)

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DjCrossfadeDeckSheet(
                    playbackManager = playbackManager,
                    onOpenCrossfadeSettings = {},
                    onClose = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dj_crossfade_deck_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("dj_deck_a_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dj_deck_b_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dj_outgoing_vol_meter").assertIsDisplayed()
        composeRule.onNodeWithTag("dj_incoming_vol_meter").assertIsDisplayed()
        composeRule.onNodeWithTag("dj_style_chip_bass_swap").assertIsDisplayed()
        composeRule.onNodeWithTag("dj_manual_trigger_btn").assertIsDisplayed()
    }
}
