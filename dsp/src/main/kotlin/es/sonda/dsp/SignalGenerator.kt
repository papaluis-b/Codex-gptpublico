package es.sonda.dsp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Continuous phase, 20 ms linear gain ramp. Owned by the output worker. */
class SignalGenerator(val sampleRate: Int) {
    @Volatile var frequencyHz: Double = 1000.0
    @Volatile var levelDbFs: Double = -40.0
    @Volatile var enabled: Boolean = false
    private var phase = 0.0
    private var gain = 0.0
    private var previousTarget = 0.0
    private var step = 0.0
    private var rampLeft = 0
    private val rampLength = (sampleRate * 0.020).toInt().coerceAtLeast(1)

    init { require(sampleRate > 0) }

    fun render(output: FloatArray, frames: Int, channels: Int = 1) {
        require(frames >= 0 && channels in 1..2 && output.size >= frames * channels)
        val target = if (enabled) 10.0.pow(levelDbFs.coerceIn(-80.0, -12.0) / 20.0) else 0.0
        if (target != previousTarget) {
            step = (target - gain) / rampLength
            rampLeft = rampLength
            previousTarget = target
        }
        val increment = 2.0 * PI * frequencyHz.coerceIn(20.0, minOf(20000.0, sampleRate * 0.45)) / sampleRate
        for (frame in 0 until frames) {
            if (rampLeft > 0) {
                gain += step
                if (--rampLeft == 0) gain = target
            }
            val value = (sin(phase) * gain).toFloat()
            for (channel in 0 until channels) output[frame * channels + channel] = value
            phase += increment
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
        }
    }

    /** Hard silence used for faults; no automatic rearming. */
    fun reset() {
        enabled = false
        gain = 0.0
        previousTarget = 0.0
        rampLeft = 0
    }
}
