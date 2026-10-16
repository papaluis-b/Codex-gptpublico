package es.sonda.dsp

import kotlin.math.*

data class Spectrum(
    val sampleRate: Int,
    val size: Int,
    val psd: DoubleArray,
    val toneAmplitude: DoubleArray,
    val rms: Double,
    val peak: Double,
    val clippedSamples: Int,
) {
    val binHz get() = sampleRate.toDouble() / size
    val durationMs get() = 1000.0 * size / sampleRate
    fun peakBin(): Int = (1 until psd.size).maxByOrNull { psd[it] } ?: 0
    companion object {
        fun db(value: Double, power: Boolean = false): Double =
            ((if (power) 10.0 else 20.0) * log10(value.coerceAtLeast(1e-30))).coerceAtLeast(-140.0)
    }
}

/** Periodic Hann, one-sided PSD in FS²/Hz. Analysis belongs off the audio worker. */
class SpectrumProcessor(val size: Int = 8192, val sampleRate: Int = 48000) {
    private val window = DoubleArray(size) { 0.5 - 0.5 * cos(2 * PI * it / size) }
    private val sumWindow = window.sum()
    private val sumWindowSquared = window.sumOf { it * it }
    private val real = DoubleArray(size)
    private val imaginary = DoubleArray(size)

    init { require(size >= 8 && size and (size - 1) == 0 && sampleRate > 0) }

    fun analyze(samples: FloatArray): Spectrum {
        require(samples.size == size)
        var energy = 0.0
        var peak = 0.0
        var clipped = 0
        for (i in samples.indices) {
            val value = samples[i].toDouble()
            energy += value * value
            peak = max(peak, abs(value))
            if (abs(value) >= 0.999) clipped++
            real[i] = value * window[i]
            imaginary[i] = 0.0
        }
        fft(real, imaginary)
        val psd = DoubleArray(size / 2 + 1)
        val amplitude = DoubleArray(psd.size)
        for (k in psd.indices) {
            val squared = real[k] * real[k] + imaginary[k] * imaginary[k]
            val sided = if (k == 0 || k == size / 2) 1.0 else 2.0
            psd[k] = sided * squared / (sampleRate * sumWindowSquared)
            amplitude[k] = sided * sqrt(squared) / sumWindow
        }
        return Spectrum(sampleRate, size, psd, amplitude, sqrt(energy / size), peak, clipped)
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = re[i]; re[i] = re[j]; re[j] = r
                val v = im[i]; im[i] = im[j]; im[j] = v
            }
        }
        var length = 2
        while (length <= size) {
            val angle = -2 * PI / length
            val stepRe = cos(angle)
            val stepIm = sin(angle)
            var start = 0
            while (start < size) {
                var twRe = 1.0
                var twIm = 0.0
                for (offset in 0 until length / 2) {
                    val a = start + offset
                    val b = a + length / 2
                    val vr = re[b] * twRe - im[b] * twIm
                    val vi = re[b] * twIm + im[b] * twRe
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                    val next = twRe * stepRe - twIm * stepIm
                    twIm = twRe * stepIm + twIm * stepRe
                    twRe = next
                }
                start += length
            }
            length = length shl 1
        }
    }
}

/** Exponential power averaging. dt is capture time, independent of UI refresh. */
class PowerAverage(private val tauSeconds: Double) {
    private var previous: DoubleArray? = null
    init { require(tauSeconds > 0) }
    fun update(power: DoubleArray, dtSeconds: Double): DoubleArray {
        require(dtSeconds > 0)
        val old = previous
        val result = if (old == null || old.size != power.size) power.copyOf() else {
            val alpha = exp(-dtSeconds / tauSeconds)
            DoubleArray(power.size) { old[it] * alpha + power[it] * (1 - alpha) }
        }
        previous = result
        return result
    }
}
