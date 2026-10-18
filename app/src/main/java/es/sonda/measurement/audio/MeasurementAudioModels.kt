package es.sonda.measurement.audio

/** Positions use each new stream's own frame zero and Android's MONOTONIC timebase. */
data class MeasurementClockStamp(val framePosition: Long, val monotonicNs: Long)

data class MeasurementAudioState(
    val preparing: Boolean = false,
    val running: Boolean = false,
    val emitting: Boolean = false,
    val message: String = "Listo para medir con una señal conocida",
    val progress: Float = 0f,
    val requestedInput: String = "Micrófono integrado",
    val requestedOutput: String = "Selecciona una salida",
    val activeInput: String = "Sin abrir",
    val activeOutput: String = "Sin abrir",
    val inputId: Int? = null,
    val outputId: Int? = null,
    val inputRate: Int = 0,
    val outputRate: Int = 0,
    val routingVerified: Boolean = false,
    val bluetooth: Boolean = false,
    val mediaVolume: Int = 0,
    val mediaVolumeMax: Int = 0,
    val capturedFrames: Long = 0,
    val targetFrames: Long = 0,
)

data class MeasurementCapture(
    val captured: FloatArray,
    /** Beginning of the complete emitted mono stimulus, including its pre-silence. */
    val playbackStartInCaptureFrames: Double,
    val metadata: MeasurementAudioMetadata,
)

data class MeasurementAudioMetadata(
    val requestedInputId: Int,
    val requestedOutputId: Int,
    val actualInputId: Int?,
    val actualOutputId: Int?,
    val requestedInputName: String,
    val requestedOutputName: String,
    val actualInputName: String,
    val actualOutputName: String,
    val inputKind: Int?,
    val outputKind: Int?,
    val inputRate: Int,
    val outputRate: Int,
    val inputChannels: Int,
    val outputChannels: Int,
    val inputBufferFrames: Int,
    val outputBufferFrames: Int,
    val mediaVolume: Int,
    val mediaVolumeMax: Int,
    val source: String,
    val effects: String,
    val unprocessedDeclared: Boolean,
    val bluetooth: Boolean,
    val clippedSamples: Long,
    val droppedFrames: Long,
    val discontinuities: Long,
    val underruns: Int,
    val inputTimestamps: List<MeasurementClockStamp>,
    val outputTimestamps: List<MeasurementClockStamp>,
    val playbackStartOutputFrame: Long,
    val delayReferenceValid: Boolean,
    val warnings: List<String>,
)
