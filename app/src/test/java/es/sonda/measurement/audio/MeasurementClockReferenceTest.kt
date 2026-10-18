package es.sonda.measurement.audio

import org.junit.Assert.*
import org.junit.Test

class MeasurementClockReferenceTest {
    private val rate = 48000
    private fun clock(originNs: Long, nsPerFrame: Double = 1e9 / rate) = listOf(0L, 4800L, 48000L).map {
        MeasurementClockStamp(it, originNs + (it * nsPerFrame).toLong())
    }

    @Test fun independentStreamStartTimesMapThroughMonotonicClock() {
        val result = MeasurementClockReference.map(clock(1_000_000_000), clock(1_100_000_000), 10000, rate)
        assertTrue(result.valid)
        assertEquals(14800.0, result.captureFrame, 1e-5)
        // Merely equating capture and playback frame indices would lose these 4800 frames.
        assertNotEquals(10000.0, result.captureFrame, 1.0)
    }

    @Test fun missingTimestampCannotBecomeAnApparentlyValidZeroDelay() {
        val result = MeasurementClockReference.map(clock(1_000_000_000), emptyList(), 10000, rate)
        assertFalse(result.valid)
        assertTrue(result.captureFrame.isNaN())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test fun oneTimestampPerStreamIsInsufficientToCheckProgress() {
        val result = MeasurementClockReference.map(clock(1_000_000_000).take(1), clock(1_100_000_000), 10000, rate)
        assertFalse(result.valid)
        assertTrue(result.captureFrame.isNaN())
    }

    @Test fun uncorrectedClockDriftInvalidatesQuantitativeDelayButKeepsObservedMapping() {
        val result = MeasurementClockReference.map(clock(1_000_000_000),
            clock(1_100_000_000, 1e9 / rate * 1.0005), 10000, rate)
        assertFalse(result.valid)
        assertTrue(result.captureFrame.isFinite())
        assertTrue(result.warnings.any { "Deriva" in it })
    }

    @Test fun interiorTimestampDiscontinuityIsNotHiddenByGoodEndpoints() {
        val broken = clock(1_000_000_000).toMutableList()
        broken[1] = broken[1].copy(monotonicNs = broken[1].monotonicNs + 5_000_000)
        val result = MeasurementClockReference.map(broken, clock(1_100_000_000), 10000, rate)
        assertFalse(result.valid)
        assertTrue(result.captureFrame.isNaN())
    }

    @Test fun repeatedOrReversedPositionsDoNotFabricateAClockFit() {
        val stable = clock(1_000_000_000)
        val duplicateWithDifferentTime = listOf(stable[0], stable[0].copy(monotonicNs = stable[0].monotonicNs + 1), stable.last())
        assertFalse(MeasurementClockReference.map(duplicateWithDifferentTime, clock(1_100_000_000), 10000, rate).valid)
        assertFalse(MeasurementClockReference.map(stable.reversed(), clock(1_100_000_000), 10000, rate).valid)
    }
}
