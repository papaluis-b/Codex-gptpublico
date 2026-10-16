package es.sonda.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.os.Process
import es.sonda.data.CaptureStore
import es.sonda.dsp.AudioRing
import es.sonda.dsp.SignalGenerator
import es.sonda.dsp.SpectrumProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Foreground-only engine. Device I/O never touches disk, FFT or a UI dispatcher. */
class AudioEngine(context: Context, private val captures: CaptureStore) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val control = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val token = AtomicLong()
    private val sessionLock = Any() // Control/UI transitions only; never acquired by I/O workers.
    private val running = AtomicBoolean()
    private val routesArmed = AtomicBoolean()
    private val inputFrames = AtomicLong()
    private val outputFrames = AtomicLong()
    private val _state = MutableStateFlow(AudioState())
    val state = _state.asStateFlow()
    private val _analysis = MutableStateFlow<AnalysisFrame?>(null)
    val analysis = _analysis.asStateFlow()
    private val _devices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _savedRevision = MutableStateFlow(0L)
    val savedRevision = _savedRevision.asStateFlow()
    @Volatile private var record: AudioRecord? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var oscillator: SignalGenerator? = null
    @Volatile private var recordingRequested = false
    private var inputThread: Thread? = null
    private var outputThread: Thread? = null
    private var processingThread: Thread? = null
    private var ring = AudioRing()
    private var effects = mutableListOf<AudioEffect>()
    private var focus: AudioFocusRequest? = null
    @Volatile private var closed = false
    private var stopNote = ""

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refreshDevices() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val s = state.value
            if (removedDevices.any { it.id == s.inputId || it.id == s.outputId }) stop("Ruta desconectada: emisión detenida")
            refreshDevices()
        }
    }
    private val routeListener = AudioRouting.OnRoutingChangedListener {
        if (routesArmed.get() && !currentRoutesMatch()) stop("Ruta cambiada o sin verificar: emisión detenida")
    }

    init {
        refreshDevices()
        manager.registerAudioDeviceCallback(deviceCallback, main)
    }

    fun refreshDevices() {
        _devices.value = (manager.getDevices(AudioManager.GET_DEVICES_INPUTS) +
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).map { it.describe() }
    }

    fun start(outputDeviceId: Int?, simulation: Boolean) {
        if (closed) return
        val ticket = synchronized(sessionLock) { token.incrementAndGet() }
        emergencySilence()
        control.execute {
            shutdown()
            synchronized(sessionLock) {
                if (ticket != token.get() || closed) return@execute
                _analysis.value = null
                _state.value = AudioState(preparing = true, simulation = simulation, message = "Preparando audio en silencio…")
                ring = AudioRing()
                inputFrames.set(0); outputFrames.set(0); stopNote = ""
            }
            try {
                if (simulation) prepareSimulation(ticket) else prepareHardware(outputDeviceId, ticket)
            } catch (e: Exception) {
                stop("No se pudo iniciar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun prepareHardware(outputId: Int?, ticket: Long) {
        val input = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?: error("No se encontró micrófono integrado")
        val output = outputId?.let { id ->
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id && (it.isUsb() || it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) }
                ?: error("La salida seleccionada ya no está disponible")
        }
        val declared = manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (declared) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
        val inputFormat = AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build()
        val minimum = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minimum > 0) { "Captura float32 a 48 kHz no disponible" }
        val rec = AudioRecord.Builder().setAudioSource(source).setAudioFormat(inputFormat)
            .setBufferSizeInBytes(maxOf(minimum * 4, 8192 * 4)).build()
        record = rec
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "Micrófono ocupado o formato no disponible" }
        check(rec.setPreferredDevice(input)) { "Android rechazó la entrada integrada solicitada" }
        rec.addOnRoutingChangedListener(routeListener, main)
        val effectStatus = disableEffects(rec.audioSessionId)

        if (output != null) {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs).setOnAudioFocusChangeListener({ change ->
                    if (change != AudioManager.AUDIOFOCUS_GAIN) stop("Foco de audio perdido: emisión detenida")
                }, main).build()
            check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Foco de audio no concedido; mantén SONDA en primer plano" }
            focus = request
            val minimumOut = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            check(minimumOut > 0) { "Salida estéreo float32 no disponible" }
            track = AudioTrack.Builder().setAudioAttributes(attrs)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(maxOf(minimumOut * 2, 2048 * 8)).build().also { out ->
                    check(out.state == AudioTrack.STATE_INITIALIZED) { "Salida no inicializada" }
                    check(out.setPreferredDevice(output)) { "Android rechazó la salida solicitada" }
                    out.addOnRoutingChangedListener(routeListener, main)
                }
            oscillator = SignalGenerator(track!!.sampleRate)
        }
        _state.update { it.copy(
            requestedInput = input.routeName(), requestedOutput = output?.routeName() ?: "Sin salida · solo análisis",
            inputId = input.id, outputId = output?.id, inputRate = rec.sampleRate, outputRate = track?.sampleRate ?: 0,
            inputChannels = rec.channelCount, outputChannels = track?.channelCount ?: 0,
            inputBufferFrames = rec.bufferSizeInFrames, outputBufferFrames = track?.bufferSizeInFrames ?: 0,
            unprocessedDeclared = declared, source = if (declared) "UNPROCESSED solicitado" else "MIC (UNPROCESSED no declarado)",
            effects = effectStatus, mediaVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC),
            mediaVolumeMax = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        ) }
        if (ticket != token.get()) { shutdown(); return }
        synchronized(sessionLock) {
            if (ticket != token.get()) return
            running.set(true)
        }
        rec.startRecording()
        check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Captura no activa" }
        inputThread = Thread({ captureLoop(rec) }, "sonda-input").apply { start() }
        track?.let { out ->
            out.play() // Only zeros are rendered until routes are independently confirmed.
            outputThread = Thread({ outputLoop(out) }, "sonda-output").apply { start() }
        }
        val deadline = System.nanoTime() + 1_500_000_000L
        while (running.get() && ticket == token.get() && !currentRoutesMatch() && System.nanoTime() < deadline) Thread.sleep(10)
        if (ticket != token.get()) { shutdown(); return }
        check(currentRoutesMatch()) { "Ruta solicitada no confirmada. Entrada: ${rec.routedDevice?.routeName() ?: "sin verificar"}; salida: ${track?.routedDevice?.routeName() ?: "sin verificar"}" }
        synchronized(sessionLock) {
            if (ticket != token.get()) return
            routesArmed.set(true)
            _state.update { it.copy(preparing = false, running = true, routingVerified = true,
                activeInput = rec.routedDevice!!.routeName(), activeOutput = track?.routedDevice?.routeName() ?: "Sin salida",
                message = "Micrófono interno · ruta confirmada. Procesado del micrófono sin validar.") }
        }
        event("Streams abiertos en silencio; rutas confirmadas")
        startProcessor(false)
    }

    private fun prepareSimulation(ticket: Long) {
        synchronized(sessionLock) {
            if (ticket != token.get()) return
            running.set(true)
            _state.update { it.copy(preparing = false, running = true, simulation = true,
                inputRate = 48000, inputChannels = 1, activeInput = "Datos sintéticos · sin micrófono",
                activeOutput = "Sin salida física", requestedOutput = "Simulación", source = "Fixture determinista",
                message = "SIMULACIÓN: seno de 1 kHz a −20 dBFS pico. No es una medida real.") }
        }
        inputThread = Thread({
            val data = FloatArray(1024)
            var frame = 0L
            val start = System.nanoTime()
            while (running.get()) {
                for (i in data.indices) data[i] = (0.1 * sin(2 * PI * 1000 * (frame + i) / 48000)).toFloat()
                ring.offer(data, data.size, frame)
                frame += data.size
                inputFrames.set(frame)
                val wait = start + frame * 1_000_000_000L / 48000 - System.nanoTime()
                if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
            }
        }, "sonda-synthetic").apply { start() }
        event("Simulación determinista iniciada; ningún hardware validado")
        startProcessor(true)
    }

    private fun captureLoop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val block = FloatArray(1024)
        try {
            while (running.get()) {
                val count = rec.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) { if (running.get()) stop("Error de captura: $count"); break }
                val first = inputFrames.getAndAdd(count.toLong())
                ring.offer(block, count, first)
            }
        } catch (e: Exception) { if (running.get()) stop("Captura interrumpida: ${e.message}") }
    }

    private fun outputLoop(out: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val block = FloatArray(1024 * out.channelCount)
        val generator = oscillator ?: return
        try {
            while (running.get()) {
                generator.render(block, 1024, out.channelCount)
                var offset = 0
                while (offset < block.size && running.get()) {
                    val count = out.write(block, offset, block.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (count <= 0) { if (running.get()) stop("Error de salida: $count"); return }
                    offset += count
                    outputFrames.addAndGet((count / out.channelCount).toLong())
                }
            }
        } catch (e: Exception) { if (running.get()) stop("Salida interrumpida: ${e.message}") }
    }

    private fun startProcessor(synthetic: Boolean) {
        processingThread = Thread({ processLoop(synthetic) }, "sonda-analysis-disk").apply { start() }
    }

    private fun processLoop(synthetic: Boolean) {
        val processor = SpectrumProcessor(sampleRate = state.value.inputRate)
        val window = FloatArray(processor.size)
        val block = FloatArray(ring.blockSize)
        var fill = 0
        var expected = -1L
        var firstWindowFrame = 0L
        var discontinuities = 0L
        var clipped = 0L
        var recording: CaptureStore.Recording? = null
        var lastDiagnostic = 0L
        val inStamp = AudioTimestamp()
        val outStamp = AudioTimestamp()
        var previousStampFrame = -1L
        var previousStampNs = -1L
        var lastVolume = state.value.mediaVolume
        try {
            while (running.get() || !ring.isEmpty()) {
                val count = ring.poll(block)
                if (count == 0) { Thread.sleep(3); continue }
                val first = ring.lastFirstFrame
                if (expected >= 0 && first != expected) { discontinuities++; fill = 0; event("Hueco de cola: frames $expected a $first; sin rellenar") }
                expected = first + count
                var blockClipped = 0L
                for (i in 0 until count) if (abs(block[i]) >= 0.999f) blockClipped++
                clipped += blockClipped
                if (fill == 0 || recordingRequested != (recording != null)) {
                    _state.update { it.copy(captureFrames = inputFrames.get(), outputFramesWritten = outputFrames.get(),
                        droppedFrames = ring.droppedFrames.get(), discontinuities = discontinuities, clippedSamples = clipped) }
                }
                if (recordingRequested && recording == null && running.get()) {
                    recording = captures.begin(state.value.copy(clippedSamples = clipped - blockClipped,
                        droppedFrames = ring.droppedFrames.get(), discontinuities = discontinuities), first)
                    _state.update { it.copy(recording = true, message = "Grabando WAV original · límite 120 s") }
                    event("Inicio de grabación en frame $first")
                }
                recording?.let { saved ->
                    val remaining = state.value.inputRate * 120L - saved.frames
                    if (recordingRequested && remaining > 0) saved.write(block, minOf(count.toLong(), remaining).toInt())
                    else {
                        recordingRequested = false
                        saved.finish(state.value.copy(clippedSamples = clipped, discontinuities = discontinuities,
                            droppedFrames = ring.droppedFrames.get()), analysis.value, "")
                        recording = null
                        _state.update { it.copy(recording = false, message = "Captura guardada en el teléfono") }
                        _savedRevision.value += 1
                    }
                }
                for (i in 0 until count) {
                    if (fill == 0) firstWindowFrame = first + i
                    window[fill++] = block[i]
                    if (fill == window.size) {
                        _analysis.value = AnalysisFrame(processor.analyze(window), firstWindowFrame, System.nanoTime())
                        fill = 0
                    }
                }
                val now = System.nanoTime()
                if (now - lastDiagnostic > 500_000_000L) {
                    lastDiagnostic = now
                    if (!synthetic) {
                        if (routesArmed.get() && !currentRoutesMatch()) { stop("Ruta cambiada: captura cancelada"); continue }
                        val rec = record
                        val out = track
                        val inValid = rec?.getTimestamp(inStamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS
                        val outValid = out?.getTimestamp(outStamp) == true
                        if (inValid && previousStampFrame >= 0 && inStamp.nanoTime != previousStampNs) {
                            val df = inStamp.framePosition - previousStampFrame
                            val dt = inStamp.nanoTime - previousStampNs
                            if (df < 0 || dt < 0 || abs(df - dt * state.value.inputRate / 1e9) > state.value.inputRate * 0.1) {
                                discontinuities++; event("Discontinuidad de timestamp de entrada")
                            }
                        }
                        if (inValid) { previousStampFrame = inStamp.framePosition; previousStampNs = inStamp.nanoTime }
                        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        if (volume != lastVolume) { event("Volumen Android cambió: $lastVolume → $volume"); lastVolume = volume }
                        _state.update { it.copy(
                            activeInput = rec?.routedDevice?.routeName() ?: "Sin verificar", activeOutput = out?.routedDevice?.routeName() ?: "Sin salida",
                            underruns = out?.underrunCount ?: 0, mediaVolume = volume,
                            inputTimestamp = if (inValid) "Monotónico de plataforma · sin compensar" else "No disponible",
                            outputTimestamp = if (outValid) "Monotónico de plataforma · sin compensar" else "No disponible",
                            inputTimestampFrame = if (inValid) inStamp.framePosition else null, inputTimestampNs = if (inValid) inStamp.nanoTime else null,
                            outputTimestampFrame = if (outValid) outStamp.framePosition else null, outputTimestampNs = if (outValid) outStamp.nanoTime else null,
                        ) }
                    }
                }
            }
        } catch (e: Exception) {
            stopNote = "Error de procesamiento/archivo: ${e.message}"
            stop(stopNote)
        } finally {
            recordingRequested = false
            _state.update { it.copy(captureFrames = inputFrames.get(), outputFramesWritten = outputFrames.get(),
                droppedFrames = ring.droppedFrames.get(), discontinuities = discontinuities, clippedSamples = clipped) }
            recording?.let { saved ->
                runCatching { saved.finish(state.value, analysis.value, stopNote) }
                    .onFailure { _state.update { s -> s.copy(message = "No se pudo completar el archivo: ${it.message}") } }
                _savedRevision.value += 1
            }
            _state.update { it.copy(recording = false) }
        }
    }

    fun setTone(frequency: Double, level: Double) {
        val f = frequency.coerceIn(20.0, 20000.0)
        val l = level.coerceIn(-80.0, -12.0)
        oscillator?.let { it.frequencyHz = f; it.levelDbFs = l }
        _state.update { it.copy(frequencyHz = f, levelDbFs = l) }
        event("Generador configurado: $f Hz, $l dBFS pico")
    }

    fun emit(enabled: Boolean) = synchronized(sessionLock) {
        val generator = oscillator
        if (enabled && (!running.get() || !routesArmed.get() || !currentRoutesMatch() || generator == null)) {
            _state.update { it.copy(message = "Inicia audio con una salida confirmada antes de emitir") }; return
        }
        generator?.enabled = enabled
        _state.update { it.copy(emitting = enabled, message = if (enabled) "Seno activo · ${it.frequencyHz.toInt()} Hz" else "Emisión en silencio") }
        event(if (enabled) "Emisión activada por el usuario" else "Silencio solicitado · rampa 20 ms")
    }

    fun toggleRecording() {
        if (!state.value.running) return
        recordingRequested = !recordingRequested
    }

    fun stop(reason: String = "Audio detenido") {
        val stopTicket = synchronized(sessionLock) {
            val ticket = token.incrementAndGet()
            stopNote = if (reason == "Audio detenido") "" else reason
            running.set(false)
            _state.update { it.copy(running = false, preparing = false, emitting = false, routingVerified = false, message = reason) }
            ticket
        }
        emergencySilence()
        recordingRequested = false
        event(reason)
        if (!closed) control.execute { if (closed || token.get() == stopTicket) shutdown() }
    }

    private fun emergencySilence() {
        routesArmed.set(false)
        oscillator?.reset()
        // Fault/lifecycle silence is immediate, even if a disk or UI worker is busy.
        track?.let { out -> runCatching { out.pause(); out.flush() } }
    }

    private fun currentRoutesMatch(): Boolean {
        val s = state.value
        return record?.routedDevice?.id == s.inputId && s.inputId != null &&
            (s.outputId == null || track?.routedDevice?.id == s.outputId)
    }

    private fun shutdown() {
        running.set(false)
        emergencySilence()
        runCatching { record?.stop() }
        runCatching { track?.stop() }
        // I/O is now unblocked. Drain queued audio before closing its WAV.
        inputThread?.join(2000); outputThread?.join(2000); processingThread?.join(5000)
        inputThread = null; outputThread = null; processingThread = null
        record?.let { runCatching { it.removeOnRoutingChangedListener(routeListener); it.release() } }; record = null
        track?.let { runCatching { it.removeOnRoutingChangedListener(routeListener); it.release() } }; track = null
        effects.forEach { runCatching { it.release() } }; effects.clear()
        focus?.let { manager.abandonAudioFocusRequest(it) }; focus = null
        oscillator = null
    }

    private fun disableEffects(sessionId: Int): String {
        val results = mutableListOf<String>()
        val factories: List<Pair<String, () -> AudioEffect?>> = listOf(
            "AGC" to { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId) else null },
            "NS" to { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId) else null },
            "AEC" to { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId) else null },
        )
        for ((name, factory) in factories) {
            results += runCatching {
                val effect = factory()
                if (effect == null) "$name: no controlable" else {
                    effects.add(effect)
                    val status = effect.setEnabled(false)
                    "$name: ${if (status == AudioEffect.SUCCESS && !effect.enabled) "desactivado por API" else "no se pudo desactivar"}"
                }
            }.getOrElse { "$name: no controlable" }
        }
        return results.joinToString(" · ") + "; DSP del fabricante sin verificar"
    }

    private fun event(description: String) {
        val item = AudioEvent(System.nanoTime(), description)
        _state.update { it.copy(events = (it.events + item).takeLast(512)) }
    }

    fun close() {
        if (closed) return
        stop("Aplicación cerrada")
        closed = true
        manager.unregisterAudioDeviceCallback(deviceCallback)
        control.shutdown()
    }
}
