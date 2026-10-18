package es.sonda.measurement

enum class MeasurementSignal { SWEEP, PULSE }

data class MeasurementConfig(
    val sampleRate: Int = 48000,
    val kind: MeasurementSignal = MeasurementSignal.SWEEP,
    val lowHz: Double = 40.0,
    val highHz: Double = 16000.0,
    val durationSeconds: Double = 5.0,
    val levelDbFs: Double = -30.0,
    val preSilenceSeconds: Double = 0.3,
    val tailSeconds: Double = 2.0,
)

/** Mono float32, including pre-silence and tail. [excitationEndFrame] is exclusive. */
data class MeasurementStimulus(
    val config: MeasurementConfig,
    val samples: FloatArray,
    val excitationStartFrame: Int,
    val excitationEndFrame: Int,
)

data class MeasurementResult(
    /** Matched-filter observed delay after playback start + the excitation's pre-silence. */
    val delaySamples: Double,
    val delayMs: Double,
    /** Actual FFT-bin frequencies in the excited band; no fabricated frequency resolution. */
    val frequenciesHz: DoubleArray,
    val magnitudeDb: DoubleArray,
    /** Full captured-time interval after regularized inversion, without moving its peak to zero. */
    val impulse: FloatArray,
    /** impulse[i] corresponds to original capture frame impulseStartCaptureFrame + i. */
    val impulseStartCaptureFrame: Int,
    /** Matched-filter peak over its expected white-noise deviation, using measured pretrigger noise. */
    val peakToNoiseDb: Double,
    /** Absolute normalized correlation; polarity is reported separately. Not an accuracy percentage. */
    val normalizedCorrelation: Double,
    val valid: Boolean,
    val warnings: List<String>,
    /** Principal and alternative matched-filter candidates, on the same observed-delay axis. */
    val candidateDelaysMs: List<Double>,
    val polarityInverted: Boolean = false,
    val fftSize: Int = 0,
)
