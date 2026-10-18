package es.sonda.measurement

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place radix-2 transform for offline analysis. Inverse includes 1/N normalization. */
internal object OfflineFft {
    fun transform(real: DoubleArray, imaginary: DoubleArray, inverse: Boolean = false) {
        val size = real.size
        require(size >= 2 && size and (size - 1) == 0 && imaginary.size == size)
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val v = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = v
            }
        }
        var length = 2
        while (length <= size) {
            val angle = (if (inverse) 2.0 else -2.0) * PI / length
            val stepRe = cos(angle)
            val stepIm = sin(angle)
            var start = 0
            while (start < size) {
                var twRe = 1.0
                var twIm = 0.0
                for (offset in 0 until length / 2) {
                    val a = start + offset
                    val b = a + length / 2
                    val vr = real[b] * twRe - imaginary[b] * twIm
                    val vi = real[b] * twIm + imaginary[b] * twRe
                    real[b] = real[a] - vr; imaginary[b] = imaginary[a] - vi
                    real[a] += vr; imaginary[a] += vi
                    val nextRe = twRe * stepRe - twIm * stepIm
                    twIm = twRe * stepIm + twIm * stepRe
                    twRe = nextRe
                }
                start += length
            }
            length = length shl 1
        }
        if (inverse) for (i in real.indices) { real[i] /= size; imaginary[i] /= size }
    }
}
