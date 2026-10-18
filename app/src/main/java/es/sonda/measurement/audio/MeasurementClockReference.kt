package es.sonda.measurement.audio

import kotlin.math.abs

/** Maps stream clocks, never UI time or the instant AudioTrack.write returns. */
internal object MeasurementClockReference {
    data class Result(val captureFrame: Double, val valid: Boolean, val warnings: List<String>)

    fun map(
        input: List<MeasurementClockStamp>,
        output: List<MeasurementClockStamp>,
        outputFrame: Long,
        sampleRate: Int,
    ): Result {
        val warnings = mutableListOf<String>()
        val inputFit = fit(input, sampleRate)
        val outputFit = fit(output, sampleRate)
        if (inputFit == null) warnings += "Timestamps de captura ausentes, discontinuos o insuficientes"
        if (outputFit == null) warnings += "Timestamps de salida ausentes, discontinuos o insuficientes"
        if (outputFrame < 0) warnings += "No se confirmó el primer frame de la señal emitida"
        if (inputFit == null || outputFit == null || outputFrame < 0) {
            return Result(Double.NaN, false, warnings)
        }
        val time = outputFit.originNs + outputFrame * outputFit.nsPerFrame
        val frame = (time - inputFit.originNs) / inputFit.nsPerFrame
        if (!frame.isFinite() || frame < 0) {
            return Result(Double.NaN, false, warnings + "Origen temporal fuera de la captura")
        }
        val driftPpm = abs(inputFit.nsPerFrame / outputFit.nsPerFrame - 1.0) * 1e6
        // No resampling/drift correction has been validated for the measurement deconvolution.
        if (driftPpm > 200) warnings += "Deriva entre relojes superior a 200 ppm; retardo no validado"
        return Result(frame, driftPpm <= 200, warnings)
    }

    private data class Fit(val originNs: Double, val nsPerFrame: Double)

    private fun fit(stamps: List<MeasurementClockStamp>, sampleRate: Int): Fit? {
        if (stamps.size < 2 || sampleRate <= 0) return null
        for (i in 1 until stamps.size) {
            if (stamps[i].framePosition == stamps[i - 1].framePosition &&
                stamps[i].monotonicNs != stamps[i - 1].monotonicNs) return null
        }
        val distinct = stamps.distinctBy { it.framePosition }
        if (distinct.size < 2) return null
        for (i in 1 until distinct.size) {
            if (distinct[i].framePosition <= distinct[i - 1].framePosition ||
                distinct[i].monotonicNs <= distinct[i - 1].monotonicNs) return null
        }
        val first = distinct.first()
        val last = distinct.last()
        // A tiny pair is too sensitive to platform timestamp quantisation/jitter.
        if (last.monotonicNs - first.monotonicNs < 100_000_000L) return null
        val step = (last.monotonicNs - first.monotonicNs).toDouble() /
            (last.framePosition - first.framePosition)
        val nominal = 1e9 / sampleRate
        if (abs(step / nominal - 1) > 0.002) return null
        val origin = first.monotonicNs.toDouble() - first.framePosition * step
        // A jump within the series is not repaired by fitting just its endpoints.
        if (distinct.any { abs(it.monotonicNs - (origin + it.framePosition * step)) > 2_000_000 }) return null
        return Fit(origin, step)
    }
}
