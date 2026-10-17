package es.sonda.dsp

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ContinuousFrequencyTest {
    @Test fun continuousSineKeepsItsPhaseAndCannotEnableOutput() {
        val generator = SignalGenerator(48000)
        generator.configure(GeneratorSettings(frequencyHz = 1000.0, levelDbFs = -40.0), continuousFrequency = true)
        val block = FloatArray(1024)
        generator.render(block, block.size)
        assertTrue(block.all { it == 0f })
        assertFalse(generator.enabled)
        generator.enabled = true
        generator.render(block, block.size)
        var phase = 2 * PI * 1000 * 2048 / 48000 % (2 * PI)
        for (frequency in listOf(20.0, 123.4, 1765.43, 20000.0)) {
            generator.configure(generator.settings.copy(frequencyHz = frequency), continuousFrequency = true)
            generator.render(block, block.size)
            for (sample in block) {
                assertEquals((sin(phase) * .01).toFloat(), sample, 1e-7f)
                phase += 2 * PI * frequency / 48000
                if (phase >= 2 * PI) phase -= 2 * PI
            }
        }
    }

    @Test fun rapidPinkRetunesKeepAudibleEnergyInsteadOfRepeatedlyRampingToZero() {
        val continuous = pink()
        val discrete = pink()
        val a = FloatArray(2048)
        val b = FloatArray(2048)
        repeat(100) { continuous.render(a, 1024, 2); discrete.render(b, 1024, 2) }
        var liveEnergy = 0.0
        var discreteEnergy = 0.0
        repeat(140) { step ->
            val settings = continuous.settings.copy(frequencyHz = 1000 + .1 * step)
            continuous.configure(settings, continuousFrequency = true)
            discrete.configure(settings)
            continuous.render(a, 1024, 2); discrete.render(b, 1024, 2)
            assertStereoAndFinite(a)
            if (step >= 20) for (i in 0 until 1024) {
                liveEnergy += a[2 * i].toDouble().pow(2)
                discreteEnergy += b[2 * i].toDouble().pow(2)
            }
        }
        val liveDb = 10 * log10(liveEnergy / (120 * 1024))
        val discreteDb = 10 * log10(discreteEnergy / (120 * 1024))
        // A moving narrow filter is not at stationary RMS, but must avoid the old ~44 dB dropout.
        assertTrue("Continuous $liveDb dBFS versus discrete $discreteDb dBFS", liveDb > -65 && liveDb > discreteDb + 15)
        assertEquals(0L, continuous.clippedSamples)
    }

    @Test fun requestsInsideAFadeDoNotRestartItAndOnlyTheLastOneFollows() {
        val moving = pink()
        val reference = pink()
        val a = FloatArray(128)
        val b = FloatArray(128)
        repeat(800) { moving.render(a, 64, 2); reference.render(b, 64, 2) }
        // One 20 ms fade = 960 frames. New requests every 64 frames must not alter that fade.
        repeat(15) { request ->
            moving.configure(moving.settings.copy(frequencyHz = 1100.0 + 100 * request), continuousFrequency = true)
            if (request == 0) reference.configure(reference.settings.copy(frequencyHz = 1100.0), continuousFrequency = true)
            moving.render(a, 64, 2); reference.render(b, 64, 2)
            assertArrayEquals("Unfinished fade changed at request $request", b, a, 0f)
            assertStereoAndFinite(a)
        }
        // All intermediate targets were coalesced: the next fade goes straight to the last target.
        reference.configure(reference.settings.copy(frequencyHz = 2500.0), continuousFrequency = true)
        repeat(40) {
            moving.render(a, 64, 2); reference.render(b, 64, 2)
            assertArrayEquals(b, a, 0f)
        }
        assertEquals(2500.0, moving.settings.frequencyHz, 0.0)
        assertEquals(0L, moving.clippedSamples)
    }

    @Test fun muteResetAndSignalChangesDuringAFadeCannotRearm() {
        val generator = pink()
        val block = FloatArray(4096)
        repeat(32) { generator.render(block, 2048, 2) }
        generator.configure(generator.settings.copy(frequencyHz = 1100.0), continuousFrequency = true)
        generator.render(block, 128, 2)
        generator.enabled = false
        generator.configure(generator.settings.copy(frequencyHz = 1200.0), continuousFrequency = true)
        generator.render(block, 2048, 2)
        for (i in 960 * 2 until block.size) assertEquals(0f, block[i], 0f)
        assertFalse(generator.enabled)
        generator.enabled = true
        generator.render(block, 2048, 2)
        generator.configure(generator.settings.copy(frequencyHz = 1300.0), continuousFrequency = true)
        generator.render(block, 128, 2)
        generator.reset()
        generator.configure(generator.settings.copy(frequencyHz = 1400.0), continuousFrequency = true)
        generator.render(block, 2048, 2)
        assertTrue(block.all { it == 0f })
        generator.enabled = true
        generator.render(block, 2048, 2)
        generator.configure(generator.settings.copy(frequencyHz = 1500.0), continuousFrequency = true)
        generator.render(block, 128, 2)
        // The caller deliberately silences on signal selection, as both Android engines do.
        generator.enabled = false
        generator.configure(generator.settings.copy(kind = SignalKind.SINE, frequencyHz = 2000.0))
        generator.render(block, 2048, 2)
        for (i in 960 * 2 until block.size) assertEquals(0f, block[i], 0f)
        assertFalse(generator.enabled)
    }

    @Test fun discreteChangesStillReachExactZeroEvenWhenTheyInterruptAFade() {
        val generator = pink()
        val block = FloatArray(4096)
        repeat(32) { generator.render(block, 2048, 2) }
        generator.configure(generator.settings.copy(frequencyHz = 1100.0), continuousFrequency = true)
        generator.render(block, 128, 2)
        generator.configure(generator.settings.copy(frequencyHz = 2200.0, octaveDivision = 24))
        generator.render(block, 2048, 2)
        assertEquals(0f, block[959 * 2], 0f)
        assertStereoAndFinite(block)
        assertEquals(0L, generator.clippedSamples)
    }

    @Test fun stoppedMovementConvergesToTheLastStaticFilterAndRequestedLevel() {
        val moving = pink(level = -32.0)
        val reference = pink(frequency = 2000.0, level = -32.0)
        val a = FloatArray(1024)
        val b = FloatArray(1024)
        repeat(100) { step ->
            moving.configure(moving.settings.copy(frequencyHz = 1000 * 2.0.pow(step / 99.0)), continuousFrequency = true)
            moving.render(a, a.size); reference.render(b, b.size)
        }
        repeat(100) { moving.render(a, a.size); reference.render(b, b.size) }
        var energy = 0.0
        repeat(256) {
            moving.render(a, a.size); reference.render(b, b.size)
            assertArrayEquals("Final static filter/source differs", b, a, 1e-7f)
            for (sample in a) energy += sample.toDouble().pow(2)
        }
        assertEquals(2000.0, moving.settings.frequencyHz, 1e-8)
        assertEquals(-32.0, 10 * log10(energy / (256 * a.size)), 1.5)
        assertEquals(0L, moving.clippedSamples)
    }

    @Test fun crossfadesRemainFiniteAndStereoAtEveryWidthAcrossTheAudibleRange() {
        val block = FloatArray(512)
        for (division in OctaveBands.divisions) {
            val generator = pink(frequency = 20.0, level = -24.0, division = division)
            repeat(400) { step ->
                generator.configure(generator.settings.copy(frequencyHz = 20 * 1000.0.pow(step / 399.0)), continuousFrequency = true)
                generator.render(block, 256, 2)
                assertStereoAndFinite(block)
            }
            assertEquals("1/$division clipping", 0L, generator.clippedSamples)
        }
    }

    private fun pink(frequency: Double = 1000.0, level: Double = -40.0, division: Int = 48) = SignalGenerator(48000).also {
        it.configure(GeneratorSettings(SignalKind.PINK_NOISE, frequency, level, division))
        it.enabled = true
    }

    private fun assertStereoAndFinite(block: FloatArray) {
        for (i in block.indices step 2) {
            assertEquals(block[i], block[i + 1], 0f)
            assertTrue(block[i].isFinite() && abs(block[i]) <= 1)
        }
    }
}
