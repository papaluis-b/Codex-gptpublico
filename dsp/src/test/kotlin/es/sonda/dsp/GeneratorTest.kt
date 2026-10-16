package es.sonda.dsp

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GeneratorTest {
    @Test fun octaveStepsAndEdgesAreExactAndReversible() {
        for (n in OctaveBands.divisions) {
            var f = 1000.0
            repeat(n) { f = OctaveBands.step(f, n, 1) }
            assertEquals("1/$n", 2000.0, f, 1e-8)
            repeat(n) { f = OctaveBands.step(f, n, -1) }
            assertEquals(1000.0, f, 1e-8)
            val (lo, hi) = OctaveBands.edges(f, n)
            assertEquals(1.0 / n, ln(hi / lo) / ln(2.0), 1e-12)
            assertEquals(f, sqrt(lo * hi), 1e-9)
        }
        assertEquals(20.0, OctaveBands.step(20.0, 3, -1), 0.0)
        assertEquals(20000.0, OctaveBands.step(20000.0, 48, 1), 0.0)
    }

    @Test fun butterworthEdgesAndShapeMatchIndependentAnalogPrototype() {
        for (n in OctaveBands.divisions) for (fc in listOf(20.0, 63.0, 1000.0, 16000.0, 20000.0)) {
            val band = NoiseBand(fc, n, 48000)
            val lo = tan(PI * band.lowerHz / 48000)
            val hi = tan(PI * band.upperHz / 48000)
            for (offset in listOf(-4.0, -1.0, 0.0, 1.0, 4.0)) {
                val f = fc * 2.0.pow(offset / (2 * n))
                if (f >= 24000) continue
                val u = tan(PI * f / 48000)
                val omega = (u * u - lo * hi) / ((hi - lo) * u)
                val expected = 1.0 / (1 + omega.pow(4))
                assertEquals("$fc Hz, 1/$n, $f Hz", 10 * log10(expected), 10 * log10(band.powerGain(f)), 0.005)
            }
            assertEquals(0.5, band.powerGain(band.lowerHz), 1e-5)
            assertEquals(0.5, band.powerGain(band.upperHz), 1e-5)
            assertTrue(band.first.a2 in 0.0..<1.0 && band.second.a2 in 0.0..<1.0)
            assertTrue(band.rmsNormalization.isFinite() && band.settlingSeconds > 0)
        }
    }

    @Test fun pinkFilterHasOneOverFrequencySpectrumInTheDeclaredRange() {
        // Analytic shaping: f * |H(f)|² should be constant, not flat power per Hz.
        val products = (0..300).map { k ->
            val f = 20 * 1000.0.pow(k / 300.0)
            f * PinkNoise.powerGain(f, 48000)
        }
        val ripple = 10 * log10(products.max() / products.min())
        assertTrue("Pink transfer ripple $ripple dB", ripple < 0.35)
    }

    @Test fun renderedPinkNoiseHasEqualEnergyPerOctave() {
        val pink = PinkNoise()
        repeat(48000) { pink.next() }
        val processor = SpectrumProcessor(32768, 48000)
        val samples = FloatArray(32768)
        val powers = DoubleArray(8)
        repeat(96) {
            for (i in samples.indices) samples[i] = pink.next().toFloat()
            val spectrum = processor.analyze(samples)
            for (octave in powers.indices) {
                val lo = 40.0 * 2.0.pow(octave)
                val first = ceil(lo / spectrum.binHz).toInt()
                val last = floor(2 * lo / spectrum.binHz).toInt()
                for (k in first..last) powers[octave] += spectrum.psd[k] * spectrum.binHz
            }
        }
        val ripple = 10 * log10(powers.max() / powers.min())
        assertTrue("Rendered octave energy ripple $ripple dB", ripple < 0.8)
    }

    @Test fun filteredPinkRmsDoesNotFallWhenSelectingANarrowerBand() {
        for (n in OctaveBands.divisions) {
            val generator = SignalGenerator(48000)
            generator.configure(GeneratorSettings(SignalKind.PINK_NOISE, 1000.0, -40.0, n))
            generator.enabled = true
            val block = FloatArray(1024)
            repeat(48) { generator.render(block, block.size) }
            var sum = 0.0
            repeat(4096) {
                generator.render(block, block.size)
                for (x in block) sum += x.toDouble() * x
            }
            val rmsDb = 10 * log10(sum / (4096 * block.size))
            assertEquals("1/$n RMS", -40.0, rmsDb, 0.6)
            assertEquals(0L, generator.clippedSamples)
        }
    }

    @Test fun extremelyNarrowLowFrequencyBandRemainsStableAndFinite() {
        val generator = SignalGenerator(48000)
        generator.configure(GeneratorSettings(SignalKind.PINK_NOISE, 20.0, -24.0, 48))
        generator.enabled = true
        val block = FloatArray(1024)
        repeat(512) { generator.render(block, block.size) }
        var energy = 0.0
        repeat(4096) {
            generator.render(block, block.size)
            assertTrue(block.all { it.isFinite() && abs(it) <= 1 })
            for (x in block) energy += x.toDouble() * x
        }
        // ~87 s at a 0.289 Hz bandwidth: appreciable random RMS fluctuation is expected.
        assertEquals(-24.0, 10 * log10(energy / (4096 * block.size)), 3.0)
        assertEquals(0L, generator.clippedSamples)
    }

    @Test fun bandFilteredNoiseRejectsFrequenciesOutsideTheSelectedCampana() {
        val generator = SignalGenerator(48000)
        generator.configure(GeneratorSettings(SignalKind.PINK_NOISE, 1000.0, -40.0, 3))
        generator.enabled = true
        val block = FloatArray(32768)
        repeat(3) { generator.render(block, block.size) }
        val processor = SpectrumProcessor(block.size, 48000)
        var inside = 0.0
        var outside = 0.0
        repeat(32) {
            generator.render(block, block.size)
            val s = processor.analyze(block)
            for (k in s.psd.indices) {
                val f = k * s.binHz
                if (f in 900.0..1100.0) inside += s.psd[k]
                if (f in 2000.0..4000.0) outside += s.psd[k]
            }
        }
        assertTrue("Out-of-band leakage", 10 * log10(outside / inside) < -30)
    }

    @Test fun shapeTransitionsMuteCleanlyAndStereoUsesTheSameSignal() {
        val generator = SignalGenerator(48000)
        generator.configure(GeneratorSettings(SignalKind.PINK_NOISE, 1000.0, -24.0, 48))
        generator.enabled = true
        val block = FloatArray(4096)
        repeat(32) { generator.render(block, 2048, 2) }
        for (i in 0 until 2048) assertEquals(block[i * 2], block[i * 2 + 1], 0f)
        generator.configure(generator.settings.copy(frequencyHz = 2000.0, octaveDivision = 24))
        generator.render(block, 2048, 2)
        // The old filter is replaced at exactly zero gain after the 20 ms down-ramp.
        assertEquals(0f, block[959 * 2], 0f)
        assertTrue(block.all { it.isFinite() && abs(it) < 1 })
        generator.enabled = false
        generator.render(block, 2048, 2)
        assertTrue(block.drop(1920).all { it == 0f })
        generator.reset()
        generator.render(block, 2048, 2)
        assertTrue(block.all { it == 0f })
    }

    @Test fun noiseIsReproducibleAcrossBlockBoundariesAndCannotEnableItself() {
        val a = SignalGenerator(48000)
        val b = SignalGenerator(48000)
        val config = GeneratorSettings(SignalKind.PINK_NOISE, 2000.0, -30.0, 32)
        a.configure(config); b.configure(config)
        assertFalse(a.enabled)
        a.enabled = true; b.enabled = true
        val x = FloatArray(4096)
        val y = FloatArray(1024)
        a.render(x, x.size)
        repeat(4) { offset ->
            b.render(y, y.size)
            for (i in y.indices) assertEquals(x[offset * y.size + i], y[i], 0f)
        }
    }
}
