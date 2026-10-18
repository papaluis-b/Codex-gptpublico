package es.sonda.measurement

import kotlin.math.*

/** Generation and analysis are offline operations: never call these from an audio I/O worker. */
object MeasurementDsp {
    private const val MAX_FFT_SIZE = 1 shl 22
    private const val REGULARIZATION = 1e-8

    fun create(config: MeasurementConfig = MeasurementConfig()): MeasurementStimulus {
        validate(config)
        val rate = config.sampleRate
        val pre = (config.preSilenceSeconds * rate).roundToInt()
        val length = (config.durationSeconds * rate).roundToInt()
        val tail = (config.tailSeconds * rate).roundToInt()
        val samples = FloatArray(pre + length + tail)
        val raw = DoubleArray(length)
        if (config.kind == MeasurementSignal.SWEEP) {
            val seconds = (length - 1).toDouble() / rate
            val logarithm = ln(config.highHz / config.lowHz)
            val factor = 2 * PI * config.lowHz * seconds / logarithm
            val fade = minOf(rate / 100, length / 10).coerceAtLeast(1)
            for (i in raw.indices) {
                val envelope = sin(PI / 2 * minOf(1.0, i.toDouble() / fade, (length - 1 - i).toDouble() / fade)).pow(2)
                raw[i] = sin(factor * expm1(logarithm * i / (length - 1))) * envelope
            }
        } else {
            val center = (length - 1) / 2.0
            for (i in raw.indices) {
                val offset = i - center
                val pulse = if (abs(offset) < 1e-12) 2 * (config.highHz - config.lowHz) / rate
                    else (sin(2 * PI * config.highHz * offset / rate) - sin(2 * PI * config.lowHz * offset / rate)) / (PI * offset)
                raw[i] = pulse * (0.5 - 0.5 * cos(2 * PI * i / (length - 1)))
            }
        }
        val peak = raw.maxOf { abs(it) }
        require(peak > 0 && peak.isFinite()) { "Excitación sin energía" }
        val gain = 10.0.pow(config.levelDbFs / 20) / peak
        for (i in raw.indices) samples[pre + i] = (raw[i] * gain).toFloat()
        return MeasurementStimulus(config, samples, pre, pre + length)
    }

    /**
     * Delay is observed against playback frame zero mapped to capture, plus known pre-silence.
     * Correlation uses the exact emitted excitation. Parabolic sub-sample fitting is numerical,
     * not a claim of physical time accuracy or compensation of platform/chain latencies.
     */
    fun analyze(
        stimulus: MeasurementStimulus,
        captured: FloatArray,
        playbackStartInCaptureFrames: Double = 0.0,
        maxDelaySeconds: Double = 1.5,
    ): MeasurementResult {
        try { validate(stimulus.config) } catch (_: IllegalArgumentException) { return invalid("Configuración de estímulo inválida") }
        val config = stimulus.config
        val rate = config.sampleRate
        val first = stimulus.excitationStartFrame
        val end = stimulus.excitationEndFrame
        if (first < 0 || end <= first || end > stimulus.samples.size ||
            stimulus.samples.any { !it.isFinite() }) return invalid("Excitación o índices inválidos")
        if (!playbackStartInCaptureFrames.isFinite() || playbackStartInCaptureFrames < 0 ||
            !maxDelaySeconds.isFinite() || maxDelaySeconds < 0 || maxDelaySeconds > 30)
            return invalid("Origen temporal o ventana de búsqueda inválidos")
        val length = end - first
        if (captured.size < length) return invalid("Captura incompleta: falta excitación")
        if (captured.any { !it.isFinite() }) return invalid("Captura con muestras no finitas")
        if (captured.any { abs(it) > 4f }) return invalid("Captura fuera de escala")
        val required = captured.size.toLong() + length - 1
        if (required > MAX_FFT_SIZE) return invalid("Captura demasiado larga para el límite de análisis")
        var size = 2
        while (size < required) size = size shl 1
        var excitationEnergy = 0.0
        for (i in first until end) excitationEnergy += stimulus.samples[i].toDouble().pow(2)
        val captureEnergy = captured.sumOf { it.toDouble().pow(2) }
        if (excitationEnergy <= 1e-20) return invalid("Excitación sin energía")
        if (captureEnergy / captured.size < 1e-18) return invalid("Captura silenciosa: no hay una llegada medible")

        val xReal = DoubleArray(size)
        val xImaginary = DoubleArray(size)
        val yReal = DoubleArray(size)
        val yImaginary = DoubleArray(size)
        for (i in 0 until length) xReal[i] = stimulus.samples[first + i].toDouble()
        for (i in captured.indices) yReal[i] = captured[i].toDouble()
        OfflineFft.transform(xReal, xImaginary)
        OfflineFft.transform(yReal, yImaginary)
        val correlation = DoubleArray(size)
        val correlationImaginary = DoubleArray(size)
        var maximumExcitationPower = 0.0
        for (k in 0 until size) {
            correlation[k] = yReal[k] * xReal[k] + yImaginary[k] * xImaginary[k]
            correlationImaginary[k] = yImaginary[k] * xReal[k] - yReal[k] * xImaginary[k]
            maximumExcitationPower = max(maximumExcitationPower, xReal[k].pow(2) + xImaginary[k].pow(2))
        }
        OfflineFft.transform(correlation, correlationImaginary, inverse = true)

        val expectedStart = playbackStartInCaptureFrames + first
        val lastFullLag = captured.size - length
        var peak = 0
        for (lag in 1..lastFullLag) if (abs(correlation[lag]) > abs(correlation[peak])) peak = lag
        val peakOffset = fittedOffset(correlation, peak, lastFullLag)
        val estimatedDelay = peak + peakOffset - expectedStart
        val delay = if (abs(estimatedDelay) <= 1e-9) 0.0 else estimatedDelay
        val warnings = mutableListOf<String>()
        var valid = true
        if (captured.any { abs(it) >= .999f }) { valid = false; warnings += "Clipping digital en la captura" }
        if (delay < 0 || delay > maxDelaySeconds * rate + .5) {
            valid = false; warnings += "Llegada fuera de la ventana de retardo solicitada"
        }
        val alignedEnergy = (peak until peak + length).sumOf { captured[it].toDouble().pow(2) }
        val normalized = (abs(correlation[peak]) / sqrt(excitationEnergy * alignedEnergy).coerceAtLeast(1e-30)).coerceIn(0.0, 1.0)
        if (normalized < .2) { valid = false; warnings += "Correlación insuficiente con la excitación conocida" }
        val noiseFrames = floor(expectedStart).toInt().coerceIn(0, captured.size)
        val noiseRms = if (noiseFrames >= 16) {
            val mean = (0 until noiseFrames).sumOf { captured[it].toDouble() } / noiseFrames
            sqrt((0 until noiseFrames).sumOf { (captured[it] - mean).pow(2) } / noiseFrames)
        } else { valid = false; warnings += "Pretrigger insuficiente para estimar el ruido"; 0.0 }
        val peakToNoise = (20 * log10(abs(correlation[peak]).coerceAtLeast(1e-30) /
            (sqrt(excitationEnergy) * noiseRms).coerceAtLeast(1e-15))).coerceIn(-140.0, 140.0)
        if (peakToNoise < 18) { valid = false; warnings += "Pico de correlación insuficiente sobre el ruido previo" }
        if (expectedStart + delay + length + config.tailSeconds * rate > captured.size + 2) {
            valid = false; warnings += "Captura incompleta: falta la cola solicitada tras la llegada"
        }
        val candidates = candidatePeaks(correlation, lastFullLag, peak, rate)
        if (candidates.size > 1) {
            warnings += "Llegadas alternativas o reflexiones: el pico principal no identifica por sí solo la llegada directa"
            if (candidates.drop(1).any { abs(correlation[it]) >= abs(correlation[peak]) * .45 }) {
                valid = false; warnings += "Retardo ambiguo: varias llegadas comparables"
            }
        }
        val inverted = correlation[peak] < 0
        if (inverted) warnings += "Polaridad invertida respecto a la excitación"

        // H = Y conj(X) / (|X|² + lambda), with linear padding and no division near spectral zeros.
        // Outside the excited band is masked; this necessarily makes the displayed IR band-limited.
        val regularizer = maximumExcitationPower * REGULARIZATION
        for (k in 0 until size) {
            val frequency = minOf(k, size - k).toDouble() * rate / size
            if (frequency < config.lowHz || frequency > config.highHz) {
                yReal[k] = 0.0; yImaginary[k] = 0.0
            } else {
                val denominator = xReal[k].pow(2) + xImaginary[k].pow(2) + regularizer
                val re = (yReal[k] * xReal[k] + yImaginary[k] * xImaginary[k]) / denominator
                val im = (yImaginary[k] * xReal[k] - yReal[k] * xImaginary[k]) / denominator
                yReal[k] = re; yImaginary[k] = im
            }
        }
        val requestedBins = frequencyBins(size, rate, config.lowHz, config.highHz)
        val bins = requestedBins.filter {
            xReal[it].pow(2) + xImaginary[it].pow(2) >= maximumExcitationPower * 1e-6
        }.toIntArray()
        if (bins.size < requestedBins.size) {
            warnings += "Bins con excitación insuficiente omitidos de la respuesta"
            if (bins.size < requestedBins.size * .75) valid = false
        }
        if (bins.isEmpty()) { valid = false; warnings += "Sin bins de frecuencia dentro de la banda solicitada" }
        val frequencies = DoubleArray(bins.size) { bins[it].toDouble() * rate / size }
        val magnitude = DoubleArray(bins.size) { k ->
            (20 * log10(hypot(yReal[bins[k]], yImaginary[bins[k]]).coerceAtLeast(1e-7))).coerceIn(-140.0, 140.0)
        }
        OfflineFft.transform(yReal, yImaginary, inverse = true)
        val impulse = FloatArray(captured.size) { yReal[it].toFloat() }
        if (impulse.any { !it.isFinite() }) return invalid("La deconvolución no produjo muestras finitas")
        return MeasurementResult(delay, 1000 * delay / rate, frequencies, magnitude, impulse, 0,
            peakToNoise, normalized, valid, warnings.toList(), candidates.map {
                val candidate = it + fittedOffset(correlation, it, lastFullLag) - expectedStart
                1000 * (if (abs(candidate) <= 1e-9) 0.0 else candidate) / rate
            }, inverted, size)
    }

    private fun validate(config: MeasurementConfig) {
        require(config.sampleRate in 4000..192000)
        require(config.lowHz.isFinite() && config.highHz.isFinite() && config.lowHz > 0 &&
            config.highHz > config.lowHz && config.highHz < config.sampleRate * .49)
        require(config.durationSeconds.isFinite() && config.durationSeconds in .01..20.0)
        require(config.levelDbFs.isFinite() && config.levelDbFs in -80.0..-12.0)
        require(config.preSilenceSeconds.isFinite() && config.preSilenceSeconds in 0.0..5.0)
        require(config.tailSeconds.isFinite() && config.tailSeconds in 0.0..10.0)
        require((config.preSilenceSeconds + config.durationSeconds + config.tailSeconds) * config.sampleRate <= 2_000_000)
    }

    private fun fittedOffset(values: DoubleArray, peak: Int, last: Int): Double {
        if (peak <= 0 || peak >= last) return 0.0
        val left = abs(values[peak - 1]); val center = abs(values[peak]); val right = abs(values[peak + 1])
        val denominator = left - 2 * center + right
        return if (denominator < -1e-30) (.5 * (left - right) / denominator).coerceIn(-.5, .5) else 0.0
    }

    private fun candidatePeaks(values: DoubleArray, last: Int, principal: Int, rate: Int): List<Int> {
        val threshold = abs(values[principal]) * .35
        val strongest = mutableListOf(principal)
        for (i in 1 until last) {
            val value = abs(values[i])
            if (i == principal || value < threshold || value < abs(values[i - 1]) || value <= abs(values[i + 1])) continue
            val position = strongest.indexOfFirst { abs(values[it]) < value }.let { if (it < 0) strongest.size else it }
            if (position < 64) { strongest.add(position, i); if (strongest.size > 64) strongest.removeAt(64) }
        }
        val selected = mutableListOf<Int>()
        val separation = (rate * .002).roundToInt().coerceAtLeast(3)
        for (peak in strongest) if (selected.none { abs(it - peak) < separation }) {
            selected += peak
            if (selected.size == 6) break
        }
        return selected
    }

    private fun frequencyBins(size: Int, rate: Int, low: Double, high: Double): IntArray {
        val first = ceil(low * size / rate).toInt().coerceAtLeast(1)
        val last = floor(high * size / rate).toInt().coerceAtMost(size / 2)
        if (last < first) return IntArray(0)
        val count = minOf(512, last - first + 1)
        return (0 until count).map { i ->
            val fraction = if (count == 1) 0.0 else i.toDouble() / (count - 1)
            (low * (high / low).pow(fraction) * size / rate).roundToInt().coerceIn(first, last)
        }.distinct().toIntArray()
    }

    private fun invalid(reason: String) = MeasurementResult(0.0, 0.0, DoubleArray(0), DoubleArray(0),
        FloatArray(0), 0, 0.0, 0.0, false, listOf(reason), emptyList())
}
