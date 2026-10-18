package es.sonda.measurement

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/** Actual application-rate regressions, including default and long sweeps; all I/O is synthetic. */
class Measurement48kTest {
    @Test fun defaultAndTenSecondSweepsRecoverGainAtZeroAndTheDelayWindowLimit() {
        for (duration in listOf(5.0, 10.0)) for (delay in listOf(0, 72000)) {
            verify(MeasurementConfig(durationSeconds = duration), delay)
        }
    }

    @Test fun eightyMillisecondPulseRecoversGainAtZeroAndTheDelayWindowLimit() {
        for (delay in listOf(0, 72000)) {
            verify(MeasurementConfig(kind = MeasurementSignal.PULSE, durationSeconds = .08), delay)
        }
    }

    @Test fun sweepAndPulseRecoverKnownLowpassAndLateTailWithoutRemovingTheirFilterDelay() {
        val fir = DoubleArray(9601).also { it[0] = .25; it[1] = .5; it[2] = .25; it[9600] = .08 }
        for (kind in MeasurementSignal.entries) {
            verify(MeasurementConfig(kind = kind, durationSeconds = if (kind == MeasurementSignal.SWEEP) 5.0 else .08),
                delay = 2400, fir = fir, filterDelay = 1)
        }
    }

    private fun verify(config: MeasurementConfig, delay: Int, fir: DoubleArray = doubleArrayOf(1.0), filterDelay: Int = 0) {
        val stimulus = MeasurementDsp.create(config)
        val captured = FloatArray(stimulus.samples.size + delay + fir.size - 1)
        val taps = fir.indices.filter { fir[it] != 0.0 }
        for (i in stimulus.samples.indices) for (tap in taps) {
            captured[i + delay + tap] += (stimulus.samples[i] * .4 * fir[tap]).toFloat()
        }
        val result = MeasurementDsp.analyze(stimulus, captured, maxDelaySeconds = 1.5)
        assertTrue("$config delay=$delay ${result.warnings}", result.valid)
        assertEquals((delay + filterDelay).toDouble(), result.delaySamples, .1)
        if (delay == 0 && filterDelay == 0) assertEquals(0.0, result.delaySamples, 0.0)
        assertTrue(result.normalizedCorrelation > .9)
        assertEquals(0, result.impulseStartCaptureFrame)
        assertEquals(stimulus.excitationStartFrame + delay + filterDelay,
            result.impulse.indices.maxBy { abs(result.impulse[it]) })
        assertEquals(captured.size, result.impulse.size)
        assertTrue(result.impulse.all { it.isFinite() })
        assertTrue(result.magnitudeDb.all { it.isFinite() })
        var checked = 0
        for (i in result.frequenciesHz.indices) {
            val frequency = result.frequenciesHz[i]
            assertTrue(frequency in config.lowHz..config.highHz)
            if (frequency < 100 || frequency > 12000) continue
            // Independent direct evaluation of the known FIR; no measurement FFT is reused here.
            var real = 0.0
            var imaginary = 0.0
            for (tap in taps) {
                val phase = 2 * PI * frequency * tap / config.sampleRate
                real += fir[tap] * cos(phase)
                imaginary -= fir[tap] * sin(phase)
            }
            val expected = 20 * log10(.4 * hypot(real, imaginary))
            assertEquals("${config.kind} ${config.durationSeconds}s $frequency Hz", expected, result.magnitudeDb[i], .05)
            checked++
        }
        assertTrue("No useful curve bins checked", checked > 300)
        assertTrue(result.fftSize >= captured.size + stimulus.excitationEndFrame - stimulus.excitationStartFrame - 1)
    }
}
