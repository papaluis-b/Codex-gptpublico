package es.sonda.dsp

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/** Continuous-phase sine or pink noise through an octave-width Butterworth bell. */
class SignalGenerator(val sampleRate: Int) {
    private data class Prepared(val settings: GeneratorSettings, val band: NoiseBand?, val continuousFrequency: Boolean = false)
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
    private val firstFilters = arrayOf(Biquad(), Biquad())
    private val secondFilters = arrayOf(Biquad(), Biquad())
    private var activeBank = 0
    private var fading: Prepared? = null
    private var fadePosition = 0
    private var phase = 0.0
    private var gain = 0.0
    private var previousTarget = 0.0
    private var rampStep = 0.0
    private var rampLeft = 0
    private val rampLength = (sampleRate * 0.020).toInt().coerceAtLeast(1)

    init { require(sampleRate > 100) }

    /**
     * Filter design and normalization run in UI/control threads, not the output worker.
     * Continuous frequency opt-in crossfades pink banks; type, level and division changes stay discrete.
     */
    fun configure(value: GeneratorSettings, continuousFrequency: Boolean = false) {
        val next = value.constrained(sampleRate)
        val old = requested
        val band = if (next.kind == SignalKind.SINE) null
            else if (sameShape(next, old.settings)) old.band
            else NoiseBand(next.frequencyHz, next.octaveDivision, sampleRate)
        requested = Prepared(next, band, continuousFrequency)
    }

    fun render(output: FloatArray, frames: Int, channels: Int = 1) {
        require(frames >= 0 && channels in 1..2 && output.size >= frames * channels)
        val serial = resetSerial.get()
        if (serial != observedReset) {
            gain = 0.0; previousTarget = 0.0; rampLeft = 0
            pink.reset()
            for (filter in firstFilters) filter.clear()
            for (filter in secondFilters) filter.clear()
            fading = null; fadePosition = 0
            observedReset = serial
        }
        val next = requested
        var discrete: Prepared? = null
        if (fading != null) {
            // Keep the current fade intact. Ordinary configure() still requests a ramp to zero.
            if (!canRetuneContinuously(next)) discrete = next
        } else if (sameShape(next.settings, active.settings)) active = next
        else if (gain == 0.0) apply(next)
        else if (canRetuneContinuously(next)) beginFade(next)
        else discrete = next
        target(if (enabled && discrete == null) 10.0.pow(active.settings.levelDbFs / 20) else 0.0)
        var increment = 2 * PI * active.settings.frequencyHz / sampleRate
        for (frame in 0 until frames) {
            if (rampLeft > 0) {
                gain += rampStep
                if (--rampLeft == 0) gain = previousTarget
            }
            if (discrete != null && gain == 0.0) {
                apply(discrete); discrete = null
                increment = 2 * PI * active.settings.frequencyHz / sampleRate
                target(if (enabled) 10.0.pow(active.settings.levelDbFs / 20) else 0.0)
            }
            val sample = if (active.settings.kind == SignalKind.SINE) sin(phase) else {
                val noise = pink.next() // Both banks see exactly the same pink source and frame clock.
                val from = secondFilters[activeBank].process(firstFilters[activeBank].process(noise)) * active.band!!.rmsNormalization
                val destination = fading
                if (destination == null) from else {
                    val otherBank = 1 - activeBank
                    val to = secondFilters[otherBank].process(firstFilters[otherBank].process(noise)) * destination.band!!.rmsNormalization
                    val blend = (++fadePosition).toDouble() / rampLength
                    val mixed = from + (to - from) * blend
                    if (fadePosition == rampLength) {
                        activeBank = otherBank
                        active = destination
                        fading = null; fadePosition = 0
                        // Coalesce intermediate requests; a later fade always takes the newest prepared target.
                        val latest = requested
                        if (discrete == null && enabled && gain > 0.0 && canRetuneContinuously(latest) &&
                            !sameShape(latest.settings, active.settings)) beginFade(latest)
                    }
                    mixed
                }
            }
            val raw = sample * gain
            if (abs(raw) > 1.0 || !raw.isFinite()) clippedSamples++
            val value = if (raw.isFinite()) raw.coerceIn(-1.0, 1.0).toFloat() else 0f
            for (channel in 0 until channels) output[frame * channels + channel] = value
            phase += increment
            if (phase >= 2 * PI) phase -= 2 * PI
        }
    }

    private fun apply(value: Prepared) {
        value.band?.let { firstFilters[activeBank].configure(it.first); secondFilters[activeBank].configure(it.second) }
        active = value
        fading = null; fadePosition = 0
    }
    private fun beginFade(value: Prepared) {
        val band = value.band!!
        val otherBank = 1 - activeBank
        firstFilters[otherBank].configure(band.first)
        secondFilters[otherBank].configure(band.second)
        fading = value; fadePosition = 0
    }
    private fun canRetuneContinuously(value: Prepared) = value.continuousFrequency &&
        active.settings.kind == SignalKind.PINK_NOISE && value.settings.kind == SignalKind.PINK_NOISE &&
        active.settings.octaveDivision == value.settings.octaveDivision && active.settings.levelDbFs == value.settings.levelDbFs
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
