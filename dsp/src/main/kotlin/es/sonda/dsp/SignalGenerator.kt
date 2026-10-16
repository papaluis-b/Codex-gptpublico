package es.sonda.dsp

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/** Continuous-phase sine or pink noise through an octave-width Butterworth bell. */
class SignalGenerator(val sampleRate: Int) {
    private data class Prepared(val settings: GeneratorSettings, val band: NoiseBand?)
    @Volatile private var requested = Prepared(GeneratorSettings().constrained(sampleRate), null)
    private var active = requested
    val settings get() = requested.settings
    val noiseBand get() = requested.band
    var frequencyHz: Double
        get() = settings.frequencyHz
        set(value) { configure(settings.copy(frequencyHz = value)) }
    var levelDbFs: Double
        get() = settings.levelDbFs
        set(value) { configure(settings.copy(levelDbFs = value)) }
    @Volatile var enabled: Boolean = false
    @Volatile var clippedSamples: Long = 0
        private set
    private val resetSerial = AtomicLong()
    private var observedReset = 0L
    private val pink = PinkNoise()
    private val filter1 = Biquad()
    private val filter2 = Biquad()
    private var phase = 0.0
    private var gain = 0.0
    private var previousTarget = 0.0
    private var rampStep = 0.0
    private var rampLeft = 0
    private val rampLength = (sampleRate * 0.020).toInt().coerceAtLeast(1)

    init { require(sampleRate > 100) }

    /** Filter design and normalization run in UI/control threads, not the output worker. */
    fun configure(value: GeneratorSettings) {
        val next = value.constrained(sampleRate)
        val old = requested
        val band = if (next.kind == SignalKind.SINE) null
            else if (sameShape(next, old.settings)) old.band
            else NoiseBand(next.frequencyHz, next.octaveDivision, sampleRate)
        requested = Prepared(next, band)
    }

    fun render(output: FloatArray, frames: Int, channels: Int = 1) {
        require(frames >= 0 && channels in 1..2 && output.size >= frames * channels)
        val serial = resetSerial.get()
        if (serial != observedReset) {
            gain = 0.0; previousTarget = 0.0; rampLeft = 0
            pink.reset(); filter1.clear(); filter2.clear()
            observedReset = serial
        }
        val next = requested
        var changingShape = !sameShape(next.settings, active.settings)
        if (!changingShape) active = next
        else if (gain == 0.0) { apply(next); changingShape = false }
        target(if (enabled && !changingShape) 10.0.pow(active.settings.levelDbFs / 20) else 0.0)
        var increment = 2 * PI * active.settings.frequencyHz / sampleRate
        for (frame in 0 until frames) {
            if (rampLeft > 0) {
                gain += rampStep
                if (--rampLeft == 0) gain = previousTarget
            }
            if (changingShape && gain == 0.0) {
                apply(next); changingShape = false
                increment = 2 * PI * active.settings.frequencyHz / sampleRate
                target(if (enabled) 10.0.pow(active.settings.levelDbFs / 20) else 0.0)
            }
            val sample = if (active.settings.kind == SignalKind.SINE) sin(phase)
                else filter2.process(filter1.process(pink.next())) * active.band!!.rmsNormalization
            val raw = sample * gain
            if (abs(raw) > 1.0 || !raw.isFinite()) clippedSamples++
            val value = if (raw.isFinite()) raw.coerceIn(-1.0, 1.0).toFloat() else 0f
            for (channel in 0 until channels) output[frame * channels + channel] = value
            phase += increment
            if (phase >= 2 * PI) phase -= 2 * PI
        }
    }

    private fun apply(value: Prepared) {
        value.band?.let { filter1.configure(it.first); filter2.configure(it.second) }
        active = value
    }
    private fun target(value: Double) {
        if (value == previousTarget) return
        rampStep = (value - gain) / rampLength
        rampLeft = rampLength
        previousTarget = value
    }
    /** Only the output worker touches DSP history; a fault also pauses/flushes AudioTrack. */
    fun reset() { enabled = false; resetSerial.incrementAndGet() }
    private fun sameShape(a: GeneratorSettings, b: GeneratorSettings) = a.kind == b.kind &&
        (a.kind == SignalKind.SINE || (a.frequencyHz == b.frequencyHz && a.octaveDivision == b.octaveDivision))
}
