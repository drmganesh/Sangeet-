package com.example

import com.example.sangeet.dsp.BiquadFilter
import com.example.sangeet.dsp.EqualizerAudioProcessor
import com.example.sangeet.dsp.SpatialAudioProcessor
import com.example.sangeet.scanner.LrcParser
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class ExampleUnitTest {

  @Test
  fun `verify 15-band biquad filter calculation and stability`() {
    val filter = BiquadFilter()
    filter.setPeakingEq(centerFreq = 1000f, sampleRate = 44100f, gainDb = 6f)
    assertFalse(filter.isBypassed)

    // Process sinusoidal test samples through filter
    var maxVal = 0f
    for (i in 0 until 100) {
      val sample = sin(i * 0.1).toFloat()
      val out = filter.processLeft(sample)
      assertFalse("Filter output must not be NaN", out.isNaN())
      assertFalse("Filter output must not be Infinite", out.isInfinite())
      maxVal = maxOf(maxVal, abs(out))
    }
    assertTrue("Filter should process audio with output", maxVal > 0f)
  }

  @Test
  fun `verify 15 frequency bands in equalizer processor`() {
    assertEquals(15, EqualizerAudioProcessor.FREQUENCIES.size)
    assertEquals(25f, EqualizerAudioProcessor.FREQUENCIES[0])
    assertEquals(16000f, EqualizerAudioProcessor.FREQUENCIES[14])

    val eq = EqualizerAudioProcessor()
    eq.setBandGain(0, 5f)
    assertEquals(5f, eq.getBandGain(0), 0.01f)
    eq.setBandGain(14, -6f)
    assertEquals(-6f, eq.getBandGain(14), 0.01f)
  }

  @Test
  fun `verify spatial audio processor parameters`() {
    val spatial = SpatialAudioProcessor()
    spatial.spatialIntensity = 0.9f
    assertEquals(0.9f, spatial.spatialIntensity, 0.01f)
    spatial.rotationSpeed = 2.5f
    assertEquals(2.5f, spatial.rotationSpeed, 0.01f)
    spatial.rotationDirection = 2
    assertEquals(2, spatial.rotationDirection)
  }

  @Test
  fun `verify synchronized LRC lyrics parser`() {
    val lrc = """
      [00:04.50]First line of lyrics
      [00:10.00]Second line of lyrics
    """.trimIndent()
    val parsed = LrcParser.parse(lrc)
    assertEquals(2, parsed.size)
    assertEquals(4500L, parsed[0].timestampMs)
    assertEquals("First line of lyrics", parsed[0].text)
    assertEquals(10000L, parsed[1].timestampMs)
    assertEquals("Second line of lyrics", parsed[1].text)
  }
}
