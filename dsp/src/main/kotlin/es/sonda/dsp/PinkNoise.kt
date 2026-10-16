package es.sonda.dsp

import kotlin.math.*

/** Paul Kellet's seven-term pinking transfer, driven by a specified xorshift32. */
class PinkNoise(private val seed: Int = DEFAULT_SEED) {
    private val state = DoubleArray(6)
    private var random = if (seed == 0) DEFAULT_SEED else seed
    private var previousWhite = 0.0
    fun next(): Double {
        var x = random
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        random = x
        val white = x / 2147483648.0
        var sum = 0.5362 * white + 0.115926 * previousWhite
        for (i in state.indices) {
            state[i] = poles[i] * state[i] + feed[i] * white
            sum += state[i]
        }
        previousWhite = white
        return sum * 0.11
    }
    fun reset() { state.fill(0.0); previousWhite = 0.0; random = if (seed == 0) DEFAULT_SEED else seed }
    companion object {
        const val DEFAULT_SEED = 0x534F4E44
        const val ALGORITHM = "kellet7-xorshift32-butterworth4-v1"
        private val poles = doubleArrayOf(0.99886, 0.99332, 0.96900, 0.86650, 0.55000, -0.7616)
        private val feed = doubleArrayOf(0.0555179, 0.0750759, 0.1538520, 0.3104856, 0.5329522, -0.0168980)
        /** Exact squared transfer of this digital pinking filter, before band selection. */
        fun powerGain(frequencyHz: Double, sampleRate: Int): Double {
            val w = 2 * PI * frequencyHz / sampleRate
            val c = cos(w); val s = sin(w)
            var re = 0.5362 + 0.115926 * c
            var im = -0.115926 * s
            for (i in poles.indices) {
                val dr = 1 - poles[i] * c
                val di = poles[i] * s
                val denominator = dr * dr + di * di
                re += feed[i] * dr / denominator
                im -= feed[i] * di / denominator
            }
            return (re * re + im * im) * 0.11 * 0.11
        }
    }
}

data class BiquadCoefficients(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    fun powerGain(frequencyHz: Double, sampleRate: Int): Double {
        val w = 2 * PI * frequencyHz / sampleRate
        val nr = b0 + b1 * cos(w) + b2 * cos(2 * w)
        val ni = -b1 * sin(w) - b2 * sin(2 * w)
        val dr = 1 + a1 * cos(w) + a2 * cos(2 * w)
        val di = -a1 * sin(w) - a2 * sin(2 * w)
        return (nr * nr + ni * ni) / (dr * dr + di * di)
    }
}

/** Prepared on a control thread. Exact -3 dB edges; no FFT or coefficient design in render. */
class NoiseBand(val centerHz: Double, val division: Int, val sampleRate: Int) {
    val lowerHz: Double
    val upperHz: Double
    val first: BiquadCoefficients
    val second: BiquadCoefficients
    val rmsNormalization: Double
    val settlingSeconds: Double
    init {
        val edges = OctaveBands.edges(centerHz, division)
        lowerHz = edges.first; upperHz = edges.second
        require(lowerHz > 0 && upperHz < sampleRate * 0.5)
        val lo = tan(PI * lowerHz / sampleRate)
        val hi = tan(PI * upperHz / sampleRate)
        val bandwidth = hi - lo
        val centerSquared = lo * hi
        // Analog Butterworth lowpass poles -> bandpass poles -> bilinear transform.
        val halfRe = -bandwidth / (2 * sqrt(2.0))
        val halfIm = -halfRe
        val qr = halfRe * halfRe - halfIm * halfIm - centerSquared
        val qi = 2 * halfRe * halfIm
        val magnitude = hypot(qr, qi)
        val rootRe = sqrt(maxOf(0.0, (magnitude + qr) / 2))
        val rootIm = -sqrt(maxOf(0.0, (magnitude - qr) / 2))
        fun section(sr: Double, si: Double): BiquadCoefficients {
            val denominator = (1 - sr) * (1 - sr) + si * si
            val zr = (1 - sr * sr - si * si) / denominator
            val zi = 2 * si / denominator
            return BiquadCoefficients(1.0, 0.0, -1.0, -2 * zr, zr * zr + zi * zi)
        }
        val a = section(halfRe + rootRe, halfIm + rootIm)
        val b = section(halfRe - rootRe, halfIm - rootIm)
        val digitalCenter = sampleRate / PI * atan(sqrt(centerSquared))
        val gain = (a.powerGain(digitalCenter, sampleRate) * b.powerGain(digitalCenter, sampleRate)).pow(-0.25)
        first = a.copy(b0 = gain, b2 = -gain)
        second = b.copy(b0 = gain, b2 = -gain)
        val radius = sqrt(maxOf(first.a2, second.a2))
        settlingSeconds = ln(0.01) / ln(radius) / sampleRate

        // Band-coordinate quadrature resolves 1/48 at 20 Hz without a huge uniform-Hz grid.
        // White input variance = 1/3; prototype squared magnitude = 1/(1+t^4).
        val steps = 2048
        var integral = 0.0
        for (i in 1 until steps) {
            val theta = -PI / 2 + PI * i / steps
            val t = tan(theta)
            val bt = bandwidth * t
            val root = sqrt(bt * bt + 4 * centerSquared)
            val u = if (bt >= 0) (bt + root) / 2 else 2 * centerSquared / (root - bt)
            val frequency = sampleRate / PI * atan(u)
            val derivative = bandwidth * u / (root * (1 + u * u))
            val integrand = PinkNoise.powerGain(frequency, sampleRate) * derivative * (1 + t * t) / (1 + t.pow(4))
            integral += integrand * if (i % 2 == 0) 2 else 4
        }
        val variance = (2.0 / (3 * PI)) * integral * (PI / steps) / 3
        require(variance > 0 && variance.isFinite())
        rmsNormalization = 1 / sqrt(variance)
    }
    fun powerGain(frequencyHz: Double) = first.powerGain(frequencyHz, sampleRate) * second.powerGain(frequencyHz, sampleRate)
}

internal class Biquad {
    private var coefficients = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)
    private var z1 = 0.0
    private var z2 = 0.0
    fun configure(value: BiquadCoefficients) { coefficients = value; clear() }
    fun clear() { z1 = 0.0; z2 = 0.0 }
    fun process(value: Double): Double {
        val c = coefficients
        val out = c.b0 * value + z1
        z1 = c.b1 * value - c.a1 * out + z2
        z2 = c.b2 * value - c.a2 * out
        return out
    }
}
