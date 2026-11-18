package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sangeet.data.repository.SettingsRepository
import com.example.sangeet.data.repository.UserProfileSnapshot
import com.example.sangeet.dsp.EqualizerAudioProcessor
import com.example.sangeet.dsp.EqualizerResponseCurve
import com.example.sangeet.ui.screens.formatListeningDuration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EqualizerAndProfileUnitTest {

    @Test
    fun equalizerProcessor_preservesExact15FrequenciesAndGainClamps() {
        assertEquals(15, EqualizerAudioProcessor.BAND_COUNT)
        assertArrayEquals(
            floatArrayOf(
                25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f,
                1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f
            ),
            EqualizerAudioProcessor.FREQUENCIES,
            0.001f
        )

        val eq = EqualizerAudioProcessor()
        eq.setBandGain(0, 18f)
        eq.setBandGain(14, -20f)
        assertEquals(12f, eq.getBandGain(0), 0.001f)
        assertEquals(-12f, eq.getBandGain(14), 0.001f)
    }

    @Test
    fun equalizerResponseCurve_reflectsActualDspGainsAndBypass() {
        val flat = EqualizerResponseCurve.computeResponseCurveDb(
            bandGains = FloatArray(15) { 0f },
            bassBoostDb = 0f,
            trebleBoostDb = 0f,
            isEnabled = true,
            pointCount = 64
        )
        assertEquals(64, flat.size)
        assertTrue(flat.all { kotlin.math.abs(it) < 0.001f })

        val boosted = FloatArray(15) { 0f }.apply {
            this[8] = 8.0f // +8 dB at 1 kHz
        }
        val midBoostCurve = EqualizerResponseCurve.computeResponseCurveDb(
            bandGains = boosted,
            bassBoostDb = 0f,
            trebleBoostDb = 0f,
            isEnabled = true,
            pointCount = 64
        )
        val centerIdx = (EqualizerResponseCurve.normalizedFrequencyPosition(1000f) * 63).toInt()
        assertTrue("Expected positive peak near 1kHz", midBoostCurve[centerIdx] > 6.5f)
        assertTrue("Expected minimal change far at 20Hz", kotlin.math.abs(midBoostCurve.first()) < 1.5f)

        val bypassed = EqualizerResponseCurve.computeResponseCurveDb(
            bandGains = boosted,
            bassBoostDb = 8f,
            trebleBoostDb = 6f,
            isEnabled = false,
            pointCount = 64
        )
        assertTrue(bypassed.all { it == 0f })
    }

    @Test
    fun userProfile_persistsDisplayNameTaglineAvatarAndStatsToggleAcrossReads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository.getInstance(context)

        // Ensure untouched existing settings remain unaffected when updating profile
        repo.setEqualizerPresetName("Rock")

        val initialDefaults = UserProfileSnapshot()
        assertEquals("SANGEET Listener", initialDefaults.displayName)
        assertTrue(initialDefaults.showListeningStats)

        repo.ensureProfileInitialized(1_700_000_000_000L)
        repo.setProfileDisplayName("Ganeswar Studio")
        repo.setProfileTagline("Golden Walkman Edition")
        repo.setProfileAvatarPath("/data/user/0/com.aistudio.sangeet.mzpka/files/profile/avatar_1.jpg")
        repo.setProfileShowListeningStats(false)

        val saved = repo.userProfile.first()
        assertEquals("Ganeswar Studio", saved.displayName)
        assertEquals("Golden Walkman Edition", saved.tagline)
        assertEquals("/data/user/0/com.aistudio.sangeet.mzpka/files/profile/avatar_1.jpg", saved.avatarUriOrPath)
        assertFalse(saved.showListeningStats)
        assertTrue(saved.createdAtEpochMs > 0L)

        // Verify Equalizer setting survived profile edits untouched
        assertEquals("Rock", repo.eqPresetName.first())

        // Reset picture clears avatar while keeping display name & tagline intact
        repo.setProfileAvatarPath("")
        val afterResetPhoto = repo.userProfile.first()
        assertEquals("", afterResetPhoto.avatarUriOrPath)
        assertEquals("Ganeswar Studio", afterResetPhoto.displayName)
    }

    @Test
    fun listeningDurationFormatter_formatsRealDurationsCleanly() {
        assertEquals("0m", formatListeningDuration(0L))
        assertEquals("< 1m", formatListeningDuration(35_000L))
        assertEquals("14m", formatListeningDuration(14 * 60_000L))
        assertEquals("2h 15m", formatListeningDuration((2 * 60 + 15) * 60_000L))
    }
}
