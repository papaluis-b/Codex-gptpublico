package es.sonda.dsp

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Random
import kotlin.math.*

class DspTest {
    @Test fun binToneAmplitudeAndRms() {
        val n = 8192
        val amplitude = 10.0.pow(-20.0 / 20)
        val x = FloatArray(n) { (amplitude * sin(2 * PI * 128 * it / n)).toFloat() }
        val spectrum = SpectrumProcessor(n).analyze(x)
        assertEquals(-20.0, Spectrum.db(spectrum.toneAmplitude[128]), 0.001)
        assertEquals(-23.0103, Spectrum.db(spectrum.rms), 0.001)
        assertEquals(128, spectrum.peakBin())
    }

    @Test fun hannPsdParsevalIncludingDcAndNyquist() {
        val n = 8192
        val random = Random(9183)
        val x = FloatArray(n) { (random.nextGaussian() * 0.1).toFloat() }
        val variants = listOf(x, FloatArray(n) { 0.2f }, FloatArray(n) { if (it % 2 == 0) 0.2f else -0.2f })
        for (signal in variants) {
            val spectrum = SpectrumProcessor(n).analyze(signal)
            var timeEnergy = 0.0
            var sumWindowSquared = 0.0
            for (i in signal.indices) {
                val w = 0.5 - 0.5 * cos(2 * PI * i / n)
                timeEnergy += w * w * signal[i] * signal[i]
                sumWindowSquared += w * w
            }
            assertEquals(timeEnergy / sumWindowSquared, spectrum.psd.sum() * spectrum.binHz, 1e-10)
        }
    }

    @Test fun fftSizePreservesToneEnergy() {
        for (n in listOf(4096, 8192, 16384)) {
            val signal = FloatArray(n) { (0.25 * sin(2 * PI * 1500 * it / 48000)).toFloat() }
            val s = SpectrumProcessor(n).analyze(signal)
            assertEquals(0.25 * 0.25 / 2, s.psd.sum() * s.binHz, 1e-8)
        }
    }

    @Test fun betweenBinsIsWindowedRatherThanAnInventedExactPeak() {
        val n = 8192
        val s = SpectrumProcessor(n).analyze(FloatArray(n) { sin(2 * PI * 100.5 * it / n).toFloat() })
        assertTrue(Spectrum.db(s.toneAmplitude[s.peakBin()]) in -1.5..-1.3)
        assertEquals(0.5, s.psd.sum() * s.binHz, 1e-7)
    }

    @Test fun powerAveragingUsesLinearPower() {
        val average = PowerAverage(1.0)
        average.update(doubleArrayOf(1.0), 1.0)
        val value = average.update(doubleArrayOf(0.01), ln(2.0))[0]
        assertEquals(0.505, value, 1e-12)
        assertEquals(-2.9670862, Spectrum.db(value, power = true), 1e-6)
    }

    @Test fun silenceAndClippingAreExplicit() {
        val processor = SpectrumProcessor()
        val silent = processor.analyze(FloatArray(8192))
        assertTrue(silent.psd.all { it == 0.0 && Spectrum.db(it, true).isFinite() })
        assertEquals(-140.0, Spectrum.db(silent.rms), 0.0)
        assertEquals(8192, processor.analyze(FloatArray(8192) { 1f }).clippedSamples)
    }

    @Test fun oscillatorIsContinuousAcrossBuffersAndFrequencyChanges() {
        val generator = SignalGenerator(48000).apply { frequencyHz = 1000.0; levelDbFs = -20.0; enabled = true }
        val out = FloatArray(4800)
        generator.render(out, out.size)
        for (i in 1000 until out.size) {
            assertEquals(0.1 * sin(2 * PI * i * 1000 / 48000), out[i].toDouble(), 1e-6)
        }
        generator.frequencyHz = 2000.0
        val next = FloatArray(200)
        generator.render(next, next.size)
        // The old block ends at an integer number of cycles: the next phase remains zero.
        assertEquals(0.0, next[0].toDouble(), 1e-6)
        assertTrue(abs(next[1] - next[0]) < 0.03)
    }

    @Test fun rampDownReachesExactSilenceAndResetCannotRearm() {
        val g = SignalGenerator(48000).apply { enabled = true; levelDbFs = -12.0 }
        val out = FloatArray(2048)
        g.render(out, out.size)
        assertTrue(abs(out[1]) < 0.001)
        g.enabled = false
        g.render(out, out.size)
        assertTrue(out.drop(960).all { it == 0f })
        g.reset()
        g.render(out, out.size)
        assertTrue(out.all { it == 0f })
    }

    @Test fun ringPreservesFrameOriginAndReportsOverflowWithoutFillingGaps() {
        val ring = AudioRing(4, 2)
        assertTrue(ring.offer(floatArrayOf(1f, 2f), 2, 10))
        assertTrue(ring.offer(floatArrayOf(3f, 4f), 2, 12))
        assertFalse(ring.offer(floatArrayOf(5f, 6f), 2, 14))
        assertEquals(2L, ring.droppedFrames.get())
        val out = FloatArray(4)
        assertEquals(2, ring.poll(out)); assertEquals(10L, ring.lastFirstFrame)
        assertArrayEquals(floatArrayOf(1f, 2f, 0f, 0f), out, 0f)
        assertEquals(2, ring.poll(out)); assertEquals(12L, ring.lastFirstFrame)
        assertEquals(0, ring.poll(out))
    }

    @Test fun spscQueueIsOrderedUnderConcurrentUse() {
        val ring = AudioRing(1, 32)
        val count = 50000
        val producer = Thread {
            val data = FloatArray(1)
            for (i in 0 until count) {
                data[0] = i.toFloat()
                while (!ring.offer(data, 1, i.toLong())) Thread.yield()
            }
        }
        producer.start()
        val data = FloatArray(1)
        var index = 0
        while (index < count) {
            if (ring.poll(data) == 0) { Thread.yield(); continue }
            assertEquals(index.toLong(), ring.lastFirstFrame)
            assertEquals(index.toFloat(), data[0], 0f)
            index++
        }
        producer.join(5000)
        assertFalse(producer.isAlive)
    }

    @Test fun wavRoundTripPreservesRawFloatBitsAndMetadata() {
        val file = Files.createTempFile("sonda-", ".wav").toFile()
        try {
            val samples = floatArrayOf(0f, -0.5f, 0.125f, 0.9999f, -1f)
            FloatWavWriter(file, 48000).use { it.write(samples, samples.size) }
            val bytes = file.readBytes()
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(bytes, 0, 4))
            assertEquals(bytes.size - 8, b.getInt(4))
            assertEquals(3, b.getShort(20).toInt())
            assertEquals(48000, b.getInt(24))
            assertEquals(samples.size, b.getInt(44))
            assertEquals("data", String(bytes, 48, 4))
            assertEquals(samples.size * 4, b.getInt(52))
            for (i in samples.indices) assertEquals(samples[i].toRawBits(), b.getInt(56 + i * 4))
        } finally { file.delete() }
    }
}
