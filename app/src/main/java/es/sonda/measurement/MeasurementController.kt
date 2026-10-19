package es.sonda.measurement

import android.content.Context
import android.os.Build
import es.sonda.BuildConfig
import es.sonda.measurement.audio.MeasurementAudioEngine
import es.sonda.measurement.audio.MeasurementAudioMetadata
import es.sonda.measurement.audio.MeasurementCapture
import es.sonda.measurement.data.SourceMatrixStore
import es.sonda.measurement.data.SourceMeasurement
import es.sonda.ui.MeasurementUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/** Coordinates finite foreground measurements. No sample processing runs on an audio worker. */
class MeasurementController(context: Context, private val releaseTimeoutMs: Long = 15_000) {
    val audio = MeasurementAudioEngine(context)
    private val store = SourceMatrixStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val storage = Mutex()
    private val generation = AtomicLong()
    private val comparisonSession = UUID.randomUUID().toString()
    private val _state = MutableStateFlow(MeasurementUiState())
    val state = _state.asStateFlow()
    @Volatile private var pending = emptyList<Outcome>()
    @Volatile private var closed = false
    private var job: Job? = null
    private var demoNumber = 0

    private data class Outcome(val stimulus: MeasurementStimulus, val capture: MeasurementCapture?,
        val samples: FloatArray, val result: MeasurementResult, val synthetic: Boolean)

    init { require(releaseTimeoutMs > 0); refresh() }

    private suspend fun awaitAnalysisStopped(ticket: Long, stopAnalysis: (() -> Unit) -> Unit) {
        currentCoroutineContext().ensureActive()
        try {
            withTimeout(releaseTimeoutMs) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    if (continuation.isActive && ticket == generation.get()) stopAnalysis {
                        if (continuation.isActive && ticket == generation.get()) continuation.resume(Unit)
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            throw IllegalStateException("No se pudo liberar el analizador a tiempo. Detén el audio y vuelve a medir.", error)
        }
    }

    private fun refresh() {
        scope.launch(Dispatchers.IO) {
            runCatching { storage.withLock { store.slots() } }
                .onSuccess { slots -> _state.update { it.copy(slots = slots) } }
                .onFailure { error -> notify("No se pudo leer la matriz: ${error.message}") }
        }
    }

    fun notify(message: String) { _state.update { it.copy(message = message) } }

    fun request(config: MeasurementConfig, input: Int?, output: Int, afterAnalysisStopped: (() -> Unit) -> Unit) {
        if (closed) return
        stop("Preparando nueva medida")
        val ticket = generation.get()
        pending = emptyList()
        _state.update { it.copy(busy = true, result = null, resultComparisonKey = null, canSave = false, syntheticResult = false,
            repeatDelaysMs = emptyList(), spreadMs = null, message = "Preparando señal y liberando el analizador…") }
        job = scope.launch {
            try {
                val stimulus = MeasurementDsp.create(config)
                awaitAnalysisStopped(ticket, afterAnalysisStopped)
                val count = if (config.kind == MeasurementSignal.PULSE) 5 else 1
                val outcomes = mutableListOf<Outcome>()
                repeat(count) { index ->
                    ensureActive()
                    check(ticket == generation.get())
                    _state.update { it.copy(message = "Medida ${index + 1}/$count · emisión conocida y captura") }
                    val capture = capture(stimulus, input, output)
                    ensureActive()
                    var result = MeasurementDsp.analyze(stimulus, capture.captured,
                        capture.playbackStartInCaptureFrames.takeIf { it.isFinite() } ?: 0.0)
                    val metadata = capture.metadata
                    val integrity = metadata.delayReferenceValid && metadata.clippedSamples == 0L &&
                        metadata.droppedFrames == 0L && metadata.discontinuities == 0L && metadata.underruns == 0
                    val warnings = result.warnings + metadata.warnings +
                        if (!metadata.delayReferenceValid) listOf("Sin referencia temporal fiable: retardo no válido") else emptyList()
                    result = result.copy(valid = result.valid && integrity, warnings = warnings.distinct())
                    outcomes += Outcome(stimulus, capture, capture.captured, result, false)
                    // Failed integrity cannot be improved by averaging or repeating its number.
                    if (!result.valid) { finish(ticket, outcomes); return@launch }
                    if (index + 1 < count) delay(350)
                }
                finish(ticket, outcomes)
            } catch (_: CancellationException) {
                // stop() owns the visible cancellation state and silences immediately.
            } catch (error: Exception) {
                if (ticket == generation.get()) {
                    audio.stop("Medida cancelada: ${error.message}")
                    _state.update { it.copy(busy = false, canSave = false, message = error.message ?: "No se pudo medir") }
                }
            }
        }
    }

    private suspend fun capture(stimulus: MeasurementStimulus, input: Int?, output: Int): MeasurementCapture =
        suspendCancellableCoroutine { continuation ->
            audio.run(stimulus, input, output, onComplete = {
                if (continuation.isActive) continuation.resume(it)
            }, onError = {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(it))
            })
            continuation.invokeOnCancellation { audio.stop("Medida interrumpida") }
        }

    private fun finish(ticket: Long, outcomes: List<Outcome>) = synchronized(generation) {
        if (ticket != generation.get() || closed || outcomes.isEmpty()) return@synchronized
        val delays = outcomes.map { it.result.delayMs }.sorted()
        val median = delays[delays.size / 2]
        val spread = delays.last() - delays.first()
        val representative = outcomes.minBy { abs(it.result.delayMs - median) }
        val repeatable = outcomes.size == 1 || spread <= 0.25
        val complete = representative.stimulus.config.kind != MeasurementSignal.PULSE || outcomes.size == 5
        val valid = outcomes.all { it.result.valid } && repeatable && complete
        val warnings = outcomes.flatMap { it.result.warnings } +
            if (!repeatable) listOf("Dispersión entre repeticiones superior a 0,25 ms: revisar ruta, ruido y reflexiones") else emptyList()
        val result = representative.result.copy(valid = valid, warnings = warnings.distinct())
        pending = if (valid) listOf(representative.copy(result = result)) + outcomes.filter { it !== representative } else emptyList()
        _state.update { it.copy(busy = false, result = result, canSave = valid,
            resultComparisonKey = if (valid) comparisonKey(representative) else null,
            syntheticResult = representative.synthetic, repeatDelaysMs = outcomes.map { o -> o.result.delayMs },
            spreadMs = if (outcomes.size > 1) spread else null,
            message = when {
                !valid -> "Medida no válida para registrar. Revisa los motivos y vuelve a medir."
                representative.synthetic -> "SIMULACIÓN · ejemplo generado, sin micrófono ni emisión física"
                else -> "Medida terminada · guarda la fuente y compara su llegada con la referencia"
            }) }
    }

    fun demo(config: MeasurementConfig, afterAnalysisStopped: (() -> Unit) -> Unit) {
        if (closed) return
        stop("Preparando simulación")
        val ticket = generation.get()
        pending = emptyList()
        _state.update { it.copy(busy = true, canSave = false, result = null, resultComparisonKey = null, syntheticResult = true,
            repeatDelaysMs = emptyList(), spreadMs = null, message = "SIMULACIÓN · calculando respuesta conocida") }
        job = scope.launch {
            try {
                val stimulus = MeasurementDsp.create(config)
                awaitAnalysisStopped(ticket, afterAnalysisStopped)
                val lag = config.sampleRate / 100 + (++demoNumber % 5) * config.sampleRate / 1000
                val reflected = config.sampleRate / 100
                val captured = FloatArray(stimulus.samples.size + lag + reflected)
                for (i in stimulus.samples.indices) {
                    captured[i + lag] += stimulus.samples[i] * 0.55f
                    captured[i + lag + reflected] += stimulus.samples[i] * 0.06f
                }
                val result = MeasurementDsp.analyze(stimulus, captured)
                val one = Outcome(stimulus, null, captured, result, true)
                finish(ticket, List(if (config.kind == MeasurementSignal.PULSE) 5 else 1) { one.copy() })
            } catch (_: CancellationException) { }
            catch (error: Exception) {
                if (ticket == generation.get()) _state.update { it.copy(busy = false, canSave = false,
                    message = "No se pudo simular: ${error.message}") }
            }
        }
    }

    fun save(slot: Int) {
        val copy = pending
        if (!state.value.canSave || state.value.busy || copy.isEmpty() || copy.any { !it.result.valid }) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val primary = copy.first()
                val c = primary.stimulus.config
                val a = primary.capture?.metadata
                val source = SourceMeasurement(UUID.randomUUID().toString(), Instant.now().toString(), c.kind.name,
                    c.sampleRate, primary.result.delayMs, comparisonKey(primary),
                    a?.actualInputName ?: "SIMULACIÓN", a?.actualOutputName ?: "Sin salida física", a?.bluetooth ?: false,
                    primary.result.frequenciesHz, primary.result.magnitudeDb, primary.result.warnings, primary.synthetic)
                val metadata = metadata(primary).put("repetitions", JSONArray().apply {
                    copy.forEachIndexed { index, outcome -> put(metadata(outcome)
                        .put("captureFile", if (index == 0) "capture.wav" else "capture-repeat-$index.wav")) }
                }).put("spreadMs", state.value.spreadMs ?: JSONObject.NULL)
                storage.withLock {
                    store.save(slot, source, primary.stimulus.samples, primary.samples, primary.result.impulse,
                        metadata, copy.drop(1).map { it.samples })
                    store.slots()
                }
            }.onSuccess { slots -> _state.update { it.copy(slots = slots, message = "Guardado en Fuente $slot · original y diagnóstico conservados") } }
                .onFailure { error -> notify("No se pudo guardar: ${error.message}") }
        }
    }

    fun clear(slot: Int) {
        scope.launch(Dispatchers.IO) {
            runCatching { storage.withLock { store.clear(slot); store.slots() } }
                .onSuccess { slots -> _state.update { it.copy(slots = slots, message = "Fuente $slot vaciada; los originales permanecen en el archivo") } }
                .onFailure { error -> notify("No se pudo vaciar: ${error.message}") }
        }
    }

    fun export(ready: (File) -> Unit) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { storage.withLock { store.export() } } }
                .onSuccess { file -> withContext(Dispatchers.Main) { ready(file) } }
                .onFailure { error -> notify("No se pudo exportar: ${error.message}") }
        }
    }

    fun setReference(number: Int) { require(number in 1..10); _state.update { it.copy(reference = number) } }
    fun setSpeed(speed: Double) {
        require(speed.isFinite() && speed in 300.0..380.0)
        _state.update { it.copy(speedMetersPerSecond = speed) }
    }

    private fun comparisonKey(outcome: Outcome): String {
        val c = outcome.stimulus.config
        val a = outcome.capture?.metadata
        val profile = listOf(comparisonSession, outcome.synthetic, c.kind, c.sampleRate, c.lowHz, c.highHz,
            c.durationSeconds, c.levelDbFs, c.preSilenceSeconds, c.tailSeconds, a?.actualInputId, a?.actualOutputId,
            a?.inputKind, a?.outputKind, a?.inputRate, a?.outputRate, a?.inputBufferFrames, a?.outputBufferFrames,
            a?.mediaVolume, a?.source, a?.effects).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(profile.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun metadata(outcome: Outcome): JSONObject {
        val c = outcome.stimulus.config
        val r = outcome.result
        return JSONObject().apply {
            put("schemaVersion", 1); put("appVersion", BuildConfig.VERSION_NAME)
            put("androidSdk", Build.VERSION.SDK_INT); put("androidRelease", Build.VERSION.RELEASE)
            put("deviceManufacturer", Build.MANUFACTURER); put("deviceModel", Build.MODEL)
            put("synthetic", outcome.synthetic); put("comparisonSession", comparisonSession)
            put("signal", JSONObject().put("kind", c.kind.name).put("sampleRate", c.sampleRate)
                .put("lowHz", c.lowHz).put("highHz", c.highHz).put("durationSeconds", c.durationSeconds)
                .put("levelDbFsPeak", c.levelDbFs).put("preSilenceSeconds", c.preSilenceSeconds)
                .put("tailSeconds", c.tailSeconds).put("excitationStartFrame", outcome.stimulus.excitationStartFrame)
                .put("excitationEndFrame", outcome.stimulus.excitationEndFrame))
            put("observedDelayMs", r.delayMs); put("delaySamples", r.delaySamples)
            put("compensationMs", JSONObject.NULL); put("compensatedDelayMs", JSONObject.NULL)
            put("impulseStartCaptureFrame", r.impulseStartCaptureFrame); put("fftSize", r.fftSize)
            put("normalizedCorrelation", r.normalizedCorrelation); put("peakToNoiseDb", r.peakToNoiseDb)
            put("polarityInverted", r.polarityInverted); put("candidateDelaysMs", JSONArray(r.candidateDelaysMs))
            put("valid", r.valid); put("warnings", JSONArray(r.warnings))
            put("absoluteDelayCalibrationValidated", false)
            outcome.capture?.let { capture ->
                put("playbackStartInCaptureFrames", capture.playbackStartInCaptureFrames.takeIf { it.isFinite() } ?: JSONObject.NULL)
                put("audio", audioMetadata(capture.metadata))
            }
        }
    }

    private fun audioMetadata(a: MeasurementAudioMetadata) = JSONObject().apply {
        put("requestedInputId", a.requestedInputId); put("requestedOutputId", a.requestedOutputId)
        put("actualInputId", a.actualInputId ?: JSONObject.NULL); put("actualOutputId", a.actualOutputId ?: JSONObject.NULL)
        put("requestedInputName", a.requestedInputName); put("requestedOutputName", a.requestedOutputName)
        put("actualInputName", a.actualInputName); put("actualOutputName", a.actualOutputName)
        put("inputKind", a.inputKind ?: JSONObject.NULL); put("outputKind", a.outputKind ?: JSONObject.NULL)
        put("inputRate", a.inputRate); put("outputRate", a.outputRate)
        put("inputChannels", a.inputChannels); put("outputChannels", a.outputChannels)
        put("inputBufferFrames", a.inputBufferFrames); put("outputBufferFrames", a.outputBufferFrames)
        put("mediaVolume", a.mediaVolume); put("mediaVolumeMax", a.mediaVolumeMax)
        put("source", a.source); put("effects", a.effects); put("unprocessedDeclared", a.unprocessedDeclared)
        put("processingVerifiedAbsent", false); put("bluetooth", a.bluetooth)
        put("clippedSamples", a.clippedSamples); put("droppedFrames", a.droppedFrames)
        put("discontinuities", a.discontinuities); put("underruns", a.underruns)
        put("playbackStartOutputFrame", a.playbackStartOutputFrame); put("delayReferenceValid", a.delayReferenceValid)
        put("inputTimestamps", JSONArray().apply { a.inputTimestamps.forEach {
            put(JSONObject().put("framePosition", it.framePosition).put("monotonicNs", it.monotonicNs)) } })
        put("outputTimestamps", JSONArray().apply { a.outputTimestamps.forEach {
            put(JSONObject().put("framePosition", it.framePosition).put("monotonicNs", it.monotonicNs)) } })
        put("warnings", JSONArray(a.warnings))
    }

    fun stop(reason: String = "Medición detenida") = synchronized(generation) {
        generation.incrementAndGet()
        job?.cancel(); job = null
        audio.stop(reason)
        _state.update { it.copy(busy = false, message = if (it.busy) reason else it.message) }
    }

    fun close() {
        if (closed) return
        closed = true
        stop("Aplicación cerrada")
        audio.close()
        scope.cancel()
    }
}
