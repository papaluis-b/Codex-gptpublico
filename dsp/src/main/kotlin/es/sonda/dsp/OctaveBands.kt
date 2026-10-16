package es.sonda.dsp

import kotlin.math.pow

/** Binary fractions of an octave; exact ratios, not rounded IEC preferred numbers. */
object OctaveBands {
    val divisions = listOf(3, 24, 32, 48)
    fun ratio(division: Int): Double {
        require(division in divisions)
        return 2.0.pow(1.0 / division)
    }
    fun edges(centerHz: Double, division: Int): Pair<Double, Double> {
        require(centerHz.isFinite() && centerHz > 0)
        val half = kotlin.math.sqrt(ratio(division))
        return centerHz / half to centerHz * half
    }
    fun step(frequencyHz: Double, division: Int, direction: Int, maximumHz: Double = 20000.0): Double {
        require(frequencyHz.isFinite() && direction in listOf(-1, 1) && maximumHz >= 20)
        return (frequencyHz * ratio(division).pow(direction)).coerceIn(20.0, maximumHz)
    }
}

enum class SignalKind(val label: String) { SINE("Seno"), PINK_NOISE("Ruido rosa") }

data class GeneratorSettings(
    val kind: SignalKind = SignalKind.SINE,
    val frequencyHz: Double = 1000.0,
    val levelDbFs: Double = -40.0,
    val octaveDivision: Int = 3,
) {
    val maximumLevel get() = if (kind == SignalKind.SINE) -12.0 else -24.0
    val levelReference get() = if (kind == SignalKind.SINE) "peak" else "rms"
    fun constrained(sampleRate: Int): GeneratorSettings {
        require(sampleRate > 100 && frequencyHz.isFinite() && levelDbFs.isFinite())
        require(octaveDivision in OctaveBands.divisions)
        return copy(frequencyHz = frequencyHz.coerceIn(20.0, maximumFrequency(sampleRate, kind, octaveDivision)),
            levelDbFs = levelDbFs.coerceIn(-80.0, maximumLevel))
    }
    companion object {
        fun maximumFrequency(sampleRate: Int, kind: SignalKind, division: Int): Double = minOf(20000.0,
            if (kind == SignalKind.SINE) sampleRate * 0.45 else sampleRate * 0.49 / kotlin.math.sqrt(OctaveBands.ratio(division)))
    }
}
