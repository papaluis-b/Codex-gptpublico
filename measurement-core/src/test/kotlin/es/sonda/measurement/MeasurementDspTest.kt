package es.sonda.measurement

import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import kotlin.math.*

class MeasurementDspTest {
    private val sweep = MeasurementConfig(sampleRate = 8000, lowHz = 80.0, highHz = 3200.0,
        durationSeconds = .25, preSilenceSeconds = .05, tailSeconds = .2)

    @Test fun generatedSignalsAreDeterministicBandLimitedAndPreserveExactSilenceBoundaries() {
        for (kind in MeasurementSignal.entries) {
            val config = sweep.copy(kind = kind)
            val a = MeasurementDsp.create(config)
            val b = MeasurementDsp.create(config)
            assertArrayEquals(a.samples, b.samples, 0f)
            assertEquals(400, a.excitationStartFrame)
            assertEquals(2400, a.excitationEndFrame)
            assertEquals(4000, a.samples.size)
            assertTrue(a.samples.take(400).all { it == 0f })
            assertTrue(a.samples.drop(2400).all { it == 0f })
            assertEquals(0f, a.samples[a.excitationStartFrame], 0f)
            assertEquals(0f, a.samples[a.excitationEndFrame - 1], 1e-12f)
            assertEquals(10.0.pow(config.levelDbFs / 20), a.samples.maxOf { abs(it) }.toDouble(), 1e-7)
            assertTrue(a.samples.all { it.isFinite() })
            if (kind == MeasurementSignal.PULSE) {
                val center = amplitudeAt(a, 1000.0)
                assertTrue("Pulse must contain many samples", a.samples.count { abs(it) > 1e-5 } > 20)
                assertTrue(20 * log10(amplitudeAt(a, 10.0) / center) < -35)
                assertTrue(20 * log10(amplitudeAt(a, 3900.0) / center) < -35)
            } else {
                val firstCrossings = crossings(a.samples, 450, 950)
                val lastCrossings = crossings(a.samples, 1850, 2350)
                assertTrue("Sweep frequency must grow", lastCrossings > firstCrossings * 3)
            }
        }
    }

    @Test fun integerDelayAndGainAreRecoveredForSweepAndPulseWithoutChangingCaptureOrigin() {
        for (kind in MeasurementSignal.entries) {
            val stimulus = MeasurementDsp.create(sweep.copy(kind = kind))
            val captured = capture(stimulus, delay = 97, gain = .5)
            val original = captured.clone()
            val result = MeasurementDsp.analyze(stimulus, captured)
            assertTrue("$kind ${result.warnings}", result.valid)
            assertEquals(97.0, result.delaySamples, .1)
            assertEquals(1000 * 97.0 / 8000, result.delayMs, .02)
            assertEquals(0, result.impulseStartCaptureFrame)
            assertEquals(stimulus.excitationStartFrame + 97, result.impulse.indices.maxBy { abs(result.impulse[it]) })
            assertEquals(captured.size, result.impulse.size)
            assertTrue(result.normalizedCorrelation > .999)
            for (db in result.magnitudeDb) assertEquals(20 * log10(.5), db, .1)
            assertFalse(result.polarityInverted)
            assertArrayEquals(original, captured, 0f)
            assertTrue(result.fftSize >= captured.size + (stimulus.excitationEndFrame - stimulus.excitationStartFrame) - 1)
        }
    }

    @Test fun playbackMappingIsRemovedFromDelayButPreservedInImpulseIndex() {
        val stimulus = MeasurementDsp.create(sweep)
        val captured = capture(stimulus, delay = 61 + 97)
        val result = MeasurementDsp.analyze(stimulus, captured, playbackStartInCaptureFrames = 61.0)
        assertTrue(result.warnings.toString(), result.valid)
        assertEquals(97.0, result.delaySamples, .1)
        assertEquals(stimulus.excitationStartFrame + 61 + 97, result.impulse.indices.maxBy { abs(result.impulse[it]) })
    }

    @Test fun numericalFractionalFitHasMeasuredToleranceAndIsNotRoundedToAFrame() {
        val stimulus = MeasurementDsp.create(sweep)
        val captured = FloatArray(stimulus.samples.size + 98)
        for (i in stimulus.samples.indices) {
            captured[i + 97] += stimulus.samples[i] * .63f
            captured[i + 98] += stimulus.samples[i] * .37f
        }
        val result = MeasurementDsp.analyze(stimulus, captured)
        assertTrue(result.warnings.toString(), result.valid)
        // Linear interpolation is itself frequency dependent; no physical sub-sample claim follows.
        assertEquals(97.37, result.delaySamples, .2)
        assertTrue(abs(result.delaySamples - result.delaySamples.roundToInt()) > .05)
    }

    @Test fun lowpassFirMatchesIndependentTransferAndKeepsItsGroupDelayInObservedResult() {
        val stimulus = MeasurementDsp.create(sweep)
        val fir = doubleArrayOf(.25, .5, .25)
        val result = MeasurementDsp.analyze(stimulus, capture(stimulus, 123, .7, fir))
        assertTrue(result.warnings.toString(), result.valid)
        assertEquals(124.0, result.delaySamples, .2)
        for (i in result.frequenciesHz.indices) {
            val f = result.frequenciesHz[i]
            if (f < 120 || f > 2900) continue
            val expected = 20 * log10(.7 * cos(PI * f / sweep.sampleRate).pow(2))
            assertEquals("$f Hz", expected, result.magnitudeDb[i], .2)
        }
        assertEquals(stimulus.excitationStartFrame + 124, result.impulse.indices.maxBy { abs(result.impulse[it]) })
    }

    @Test fun reversedPolarityHasTheSameDelayAndMagnitudeAndAnExplicitWarning() {
        val stimulus = MeasurementDsp.create(sweep)
        val positive = MeasurementDsp.analyze(stimulus, capture(stimulus, 181, .3))
        val negative = MeasurementDsp.analyze(stimulus, capture(stimulus, 181, -.3))
        assertTrue(negative.warnings.toString(), negative.valid)
        assertEquals(positive.delaySamples, negative.delaySamples, 1e-6)
        assertArrayEquals(positive.magnitudeDb, negative.magnitudeDb, 1e-6)
        assertTrue(negative.polarityInverted)
        assertTrue(negative.warnings.any { it.contains("Polaridad") })
    }

    @Test fun strongerReflectionReportsBothCandidatesAndCannotClaimAnUnambiguousDirectArrival() {
        val stimulus = MeasurementDsp.create(sweep.copy(kind = MeasurementSignal.PULSE, durationSeconds = .08))
        val direct = capture(stimulus, 60, .3)
        val reflection = capture(stimulus, 420, .6)
        val captured = reflection.clone()
        for (i in direct.indices) captured[i] += direct[i]
        val result = MeasurementDsp.analyze(stimulus, captured)
        assertFalse(result.valid)
        assertEquals(420.0, result.delaySamples, .2)
        assertTrue(result.warnings.any { it.contains("directa") })
        assertTrue(result.candidateDelaysMs.any { abs(it - 60 * 1000.0 / 8000) < .1 })
        assertTrue(result.candidateDelaysMs.any { abs(it - 420 * 1000.0 / 8000) < .1 })
        assertTrue(abs(result.impulse[stimulus.excitationStartFrame + 420]) > abs(result.impulse[stimulus.excitationStartFrame + 60]))
    }

    @Test fun silenceNoiseOnlyNonFiniteAndClippingCannotProduceValidMeasurements() {
        val stimulus = MeasurementDsp.create(sweep)
        val silence = MeasurementDsp.analyze(stimulus, FloatArray(stimulus.samples.size + 100))
        assertFalse(silence.valid)
        assertTrue(silence.candidateDelaysMs.isEmpty())
        assertEquals(0.0, silence.delayMs, 0.0)
        val random = Random(9876)
        val noise = FloatArray(stimulus.samples.size + 100) { (random.nextGaussian() * .02).toFloat() }
        val noiseResult = MeasurementDsp.analyze(stimulus, noise)
        assertFalse(noiseResult.valid)
        assertTrue(noiseResult.warnings.any { it.contains("Correlación") || it.contains("ruido") })
        val nonFinite = capture(stimulus, 100).also { it[10] = Float.NaN }
        assertFalse(MeasurementDsp.analyze(stimulus, nonFinite).valid)
        val clipped = capture(stimulus, 100).also { it[600] = 1f }
        val clippingResult = MeasurementDsp.analyze(stimulus, clipped)
        assertFalse(clippingResult.valid)
        assertTrue(clippingResult.warnings.any { it.contains("Clipping") })
        assertTrue(noiseResult.magnitudeDb.all { it.isFinite() })
        assertTrue(noiseResult.impulse.all { it.isFinite() })
    }

    @Test fun lowLevelSignalOnMeasuredPretriggerNoiseIsRejectedOrAcceptedReproducibly() {
        val stimulus = MeasurementDsp.create(sweep)
        val random = Random(775)
        val good = capture(stimulus, 87)
        for (i in good.indices) good[i] += (random.nextGaussian() * .0002).toFloat()
        val result = MeasurementDsp.analyze(stimulus, good)
        assertTrue(result.warnings.toString(), result.valid)
        assertEquals(87.0, result.delaySamples, .2)
        assertTrue(result.peakToNoiseDb > 18)
        assertTrue(result.normalizedCorrelation > .9)
    }

    @Test fun delaySearchLimitsAndMissingTailAreExplicitlyInvalid() {
        val stimulus = MeasurementDsp.create(sweep)
        val boundary = MeasurementDsp.analyze(stimulus, capture(stimulus, 100), maxDelaySeconds = 100.0 / 8000)
        assertTrue(boundary.warnings.toString(), boundary.valid)
        val beyond = MeasurementDsp.analyze(stimulus, capture(stimulus, 104), maxDelaySeconds = 100.0 / 8000)
        assertFalse(beyond.valid)
        assertTrue(beyond.warnings.any { it.contains("ventana") })
        assertEquals(104.0, beyond.delaySamples, .1)
        val negative = MeasurementDsp.analyze(stimulus, capture(stimulus, 40), playbackStartInCaptureFrames = 80.0)
        assertFalse(negative.valid)
        assertEquals(-40.0, negative.delaySamples, .1)
        val negativeFraction = MeasurementDsp.analyze(stimulus, capture(stimulus, 0), playbackStartInCaptureFrames = .25)
        assertFalse(negativeFraction.valid)
        assertEquals(-.25, negativeFraction.delaySamples, 1e-6)
        val zero = MeasurementDsp.analyze(stimulus, capture(stimulus, 0))
        assertTrue(zero.warnings.toString(), zero.valid)
        assertEquals(0.0, zero.delaySamples, 0.0)
        assertEquals(0.0, zero.candidateDelaysMs.first(), 0.0)
        val truncated = capture(stimulus, 100).dropLast(40).toFloatArray()
        val truncatedResult = MeasurementDsp.analyze(stimulus, truncated)
        assertFalse(truncatedResult.valid)
        assertTrue(truncatedResult.warnings.any { it.contains("cola") })
        val tooShort = MeasurementDsp.analyze(stimulus, FloatArray(100))
        assertFalse(tooShort.valid)
    }

    @Test fun delayedTailDoesNotWrapToTheStartAndCaptureArraysRemainOriginal() {
        val stimulus = MeasurementDsp.create(sweep)
        val fir = DoubleArray(1101).also { it[0] = 1.0; it[1100] = .2 }
        val captured = capture(stimulus, 130, .5, fir)
        val result = MeasurementDsp.analyze(stimulus, captured)
        assertTrue(result.warnings.toString(), result.valid)
        val direct = stimulus.excitationStartFrame + 130
        val late = direct + 1100
        assertTrue(abs(result.impulse[late]) > .1 * abs(result.impulse[direct]))
        val earlyPeak = result.impulse.take(100).maxOf { abs(it) }
        assertTrue(earlyPeak < abs(result.impulse[late]) * .1)
        assertEquals(0, result.impulseStartCaptureFrame)
        assertEquals(captured.size, result.impulse.size)
    }

    @Test fun spectralZerosAreRegularizedAndUnsupportedBinsDoNotBecomeAResponseCurve() {
        val original = MeasurementDsp.create(sweep)
        val narrow = original.samples.clone()
        val length = original.excitationEndFrame - original.excitationStartFrame
        for (i in 0 until length) narrow[original.excitationStartFrame + i] =
            (.02 * sin(2 * PI * 1000 * i / 8000) * (0.5 - 0.5 * cos(2 * PI * i / (length - 1)))).toFloat()
        val stimulus = original.copy(samples = narrow)
        val result = MeasurementDsp.analyze(stimulus, capture(stimulus, 100))
        assertFalse(result.valid)
        assertTrue(result.warnings.any { it.contains("excitación insuficiente") })
        assertTrue(result.magnitudeDb.all { it.isFinite() })
        assertTrue(result.impulse.all { it.isFinite() })
        assertTrue(result.frequenciesHz.all { it in sweep.lowHz..sweep.highHz })
    }

    @Test fun invalidConfigurationFailsBeforeAllocationAndBadMetadataCannotShiftTheOrigin() {
        for (config in listOf(sweep.copy(highHz = 4000.0), sweep.copy(lowHz = Double.NaN),
            sweep.copy(levelDbFs = -1.0), sweep.copy(durationSeconds = 0.0), sweep.copy(tailSeconds = -1.0))) {
            try { MeasurementDsp.create(config); fail("Configuration accepted: $config") } catch (_: IllegalArgumentException) { }
        }
        val stimulus = MeasurementDsp.create(sweep)
        assertFalse(MeasurementDsp.analyze(stimulus.copy(excitationStartFrame = -1), capture(stimulus, 100)).valid)
        assertFalse(MeasurementDsp.analyze(stimulus, capture(stimulus, 100), Double.NaN).valid)
    }

    private fun capture(stimulus: MeasurementStimulus, delay: Int, gain: Double = 1.0,
        fir: DoubleArray = doubleArrayOf(1.0)): FloatArray {
        val data = FloatArray(stimulus.samples.size + delay + fir.size - 1)
        for (i in stimulus.samples.indices) for (tap in fir.indices) if (fir[tap] != 0.0)
            data[i + delay + tap] += (stimulus.samples[i] * gain * fir[tap]).toFloat()
        return data
    }

    private fun amplitudeAt(stimulus: MeasurementStimulus, frequency: Double): Double {
        var real = 0.0; var imaginary = 0.0
        for (i in stimulus.samples.indices) {
            val phase = 2 * PI * frequency * i / stimulus.config.sampleRate
            real += stimulus.samples[i] * cos(phase); imaginary += stimulus.samples[i] * sin(phase)
        }
        return hypot(real, imaginary)
    }

    private fun crossings(data: FloatArray, first: Int, end: Int) = (first until end - 1).count { data[it] <= 0 && data[it + 1] > 0 }
}
