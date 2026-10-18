package es.sonda.measurement.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import es.sonda.audio.AudioDevice
import es.sonda.audio.BluetoothOutputTypes
import es.sonda.audio.describe
import es.sonda.audio.isUsb
import es.sonda.audio.routeName
import es.sonda.measurement.MeasurementStimulus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** One foreground measurement. I/O workers only transfer bounded, preallocated buffers. */
class MeasurementAudioEngine(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val control = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    private val transition = Any() // Never acquired by an I/O worker.
    private val _state = MutableStateFlow(MeasurementAudioState())
    val state = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    @Volatile private var active: Session? = null
    @Volatile private var closed = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refreshDevices() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            active?.let { s ->
                if (removedDevices.any { it.id == s.input.id || it.id == s.output.id }) {
                    abort("Ruta desconectada: medida cancelada")
                }
            }
            refreshDevices()
        }
    }
    private val routeListener = AudioRouting.OnRoutingChangedListener {
        active?.let { s -> if (s.armed.get() && !routesMatch(s)) abort("Ruta cambiada: medida cancelada") }
    }

    init {
        refreshDevices()
        manager.registerAudioDeviceCallback(deviceCallback, main)
    }

    fun refreshDevices() {
        _devices.value = runCatching {
            (manager.getDevices(AudioManager.GET_DEVICES_INPUTS) + manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                .map { it.describe() }
        }.getOrDefault(emptyList())
    }

    /** The UI must explicitly select the output; null input means the built-in mic only. */
    fun run(stimulus: MeasurementStimulus, inputDeviceId: Int?, outputDeviceId: Int,
        onComplete: (MeasurementCapture) -> Unit, onError: (String) -> Unit = {}) {
        val ticket = synchronized(transition) {
            if (closed) return
            generation.incrementAndGet().also { active?.let(::silence) }
        }
        execute {
            active?.let(::shutdown)
            if (ticket != generation.get() || closed) return@execute
            _state.value = MeasurementAudioState(preparing = true, message = "Abriendo captura y salida en silencio…")
            var session: Session? = null
            var completion: MeasurementCapture? = null
            var failure: String? = null
            try {
                check(appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    "Concede el permiso de micrófono para medir"
                }
                require(stimulus.config.sampleRate == RATE) { "Esta versión de medida requiere 48 kHz" }
                require(stimulus.samples.isNotEmpty() && stimulus.samples.size <= RATE * 40) { "Duración de medida fuera del límite de 40 s" }
                require(stimulus.samples.all { it.isFinite() && abs(it) < 1f }) { "La señal contiene clipping o valores no finitos" }
                val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val input = if (inputDeviceId == null) inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                    else inputs.firstOrNull { it.id == inputDeviceId && (it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC || it.isUsb()) }
                check(input != null) { "Entrada integrada o USB seleccionada no disponible" }
                val output = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                    it.id == outputDeviceId && (it.isUsb() || it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || isBluetooth(it.type))
                }
                check(output != null) { "Salida USB, Bluetooth o altavoz seleccionada no disponible" }
                if (isBluetooth(output.type) && Build.VERSION.SDK_INT >= 31) {
                    check(appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        "Concede permiso de dispositivos cercanos para la salida Bluetooth"
                    }
                }
                val created = Session(ticket, input, output, stimulus)
                session = created
                synchronized(transition) {
                    if (ticket != generation.get() || closed) return@execute
                    active = created
                }
                prepare(created)
                if (!isCurrent(created)) return@execute
                measure(created)
                if (!isCurrent(created)) return@execute
                completion = finishCapture(created)
            } catch (e: Exception) {
                failure = "Medida cancelada: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                session?.let(::shutdown)
            }
            if (ticket == generation.get() && !closed) {
                val error = failure
                val result = completion
                if (error != null) {
                    _state.update { it.copy(preparing = false, running = false, emitting = false, routingVerified = false, message = error) }
                    onError(error)
                } else if (result != null) {
                    _state.update { it.copy(preparing = false, running = false, emitting = false, progress = 1f,
                        message = if (result.metadata.delayReferenceValid) "Captura terminada; procesando medida…"
                        else "Captura terminada con referencia temporal no válida; consulta diagnóstico") }
                    onComplete(result)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun prepare(s: Session) {
        val declared = manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        s.unprocessed = declared
        val source = if (declared) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
        s.source = if (declared) "UNPROCESSED solicitado" else "MIC (UNPROCESSED no declarado)"
        val minimumIn = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minimumIn > 0) { "Captura mono float a 48 kHz no disponible" }
        val rec = AudioRecord.Builder().setAudioSource(source).setAudioFormat(AudioFormat.Builder()
            .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(maxOf(minimumIn * 4, 8192 * 4)).build()
        s.record = rec
        check(rec.state == AudioRecord.STATE_INITIALIZED && rec.sampleRate == RATE && rec.channelCount == 1) {
            "Formato real de entrada incompatible con la señal"
        }
        check(rec.setPreferredDevice(s.input)) { "Android rechazó la entrada seleccionada" }
        rec.addOnRoutingChangedListener(routeListener, main)
        s.effectsDescription = disableEffects(rec.audioSessionId, s)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener({ change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN && isCurrent(s)) abort("Foco perdido: medida cancelada")
            }, main).build()
        check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Foco de audio no concedido" }
        s.focus = request
        val minimumOut = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minimumOut > 0) { "Salida estéreo float a 48 kHz no disponible" }
        val out = AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(AudioFormat.Builder()
            .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(minimumOut * 2, 2048 * 8)).build()
        s.track = out
        check(out.state == AudioTrack.STATE_INITIALIZED && out.sampleRate == RATE && out.channelCount == 2) {
            "Formato real de salida incompatible con la señal"
        }
        check(out.setPreferredDevice(s.output)) { "Android rechazó la salida seleccionada" }
        out.addOnRoutingChangedListener(routeListener, main)
        s.volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        s.volumeMax = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        _state.update { it.copy(inputId = s.input.id, outputId = s.output.id,
            requestedInput = s.input.routeName(), requestedOutput = s.output.routeName(),
            inputRate = rec.sampleRate, outputRate = out.sampleRate, bluetooth = isBluetooth(s.output.type),
            mediaVolume = s.volume, mediaVolumeMax = s.volumeMax, targetFrames = s.stimulus.samples.size.toLong()) }
        synchronized(transition) {
            if (!isCurrent(s)) return
            check(s.failureReason == null) { s.failureReason!! }
            // Prefill only zeros while stopped, avoiding an intentional startup underrun.
            var remaining = out.bufferSizeInFrames * 2
            while (remaining > 0) {
                val count = out.write(s.outputBlock, 0, minOf(remaining, s.outputBlock.size), AudioTrack.WRITE_NON_BLOCKING)
                if (count <= 0) break
                check(count % 2 == 0) { "Salida devolvió un frame estéreo parcial" }
                s.writtenFrames.addAndGet(count / 2L)
                remaining -= count
            }
            s.running.set(true)
            rec.startRecording()
            check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Captura no activa" }
            // Only a preallocated zero block can be written before the independent route checks.
            out.play()
            s.inputThread = Thread({ captureLoop(s, rec) }, "sonda-measure-input").apply { start() }
            s.outputThread = Thread({ outputLoop(s, out) }, "sonda-measure-output").apply { start() }
        }
        val deadline = System.nanoTime() + 1_500_000_000L
        while (isCurrent(s) && s.failureReason == null && !routesMatch(s) && System.nanoTime() < deadline) Thread.sleep(10)
        if (!isCurrent(s)) return
        check(s.failureReason == null) { s.failureReason!! }
        check(routesMatch(s)) { "Ruta exacta no confirmada; no se ha emitido señal" }
        synchronized(transition) {
            if (!isCurrent(s)) return
            check(s.failureReason == null) { s.failureReason!! }
            s.armed.set(true)
            _state.update { it.copy(preparing = false, running = true, routingVerified = true,
                activeInput = rec.routedDevice!!.routeName(), activeOutput = out.routedDevice!!.routeName(),
                message = if (isBluetooth(s.output.type)) "Bluetooth experimental: latencia variable, sin compensación validada"
                else "Rutas confirmadas; capturando señal conocida") }
        }
    }

    private fun measure(s: Session) {
        val rec = s.record ?: error("Captura cerrada")
        val out = s.track ?: error("Salida cerrada")
        val inStamp = AudioTimestamp()
        val outStamp = AudioTimestamp()
        val captureAtArm = s.capturedFrames.get()
        val captureTarget = captureAtArm + s.stimulus.samples.size + RATE * 5L / 2
        val deadline = System.nanoTime() + (s.stimulus.samples.size.toLong() * 1_000_000_000L / RATE) + 7_000_000_000L
        s.emit.set(true)
        while (isCurrent(s)) {
            check(s.failureReason == null) { s.failureReason!! }
            check(s.ioError.get() == 0) { ioMessage(s.ioError.get()) }
            check(routesMatch(s)) { "Ruta cambiada durante la medida" }
            check(manager.getStreamVolume(AudioManager.STREAM_MUSIC) == s.volume) { "Volumen Android cambiado durante la medida" }
            check(System.nanoTime() < deadline) { "Audio sin avance o captura incompleta" }
            if (rec.getTimestamp(inStamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                appendStamp(s.inputStamps, inStamp)
                if (inStamp.framePosition - s.capturedFrames.get() > rec.bufferSizeInFrames * 2L + BLOCK * 2) {
                    s.overrunSuspected = true
                }
            }
            if (out.getTimestamp(outStamp)) appendStamp(s.outputStamps, outStamp)
            val start = s.stimulusStart.get()
            val head = out.playbackHeadPosition.toLong() and 0xffffffffL
            val excitationStart = start + s.stimulus.excitationStartFrame
            val excitationEnd = start + s.stimulus.excitationEndFrame
            _state.update { it.copy(capturedFrames = s.capturedFrames.get(),
                emitting = start >= 0 && head in excitationStart until excitationEnd,
                progress = if (start < 0) 0f else ((head - start).toFloat() / (s.stimulus.samples.size + RATE * 2)).coerceIn(0f, 0.99f)) }
            // Two extra seconds beyond the complete stimulus allow positive delays to retain the tail.
            if (start >= 0 && head >= start + s.stimulus.samples.size + RATE * 2L && s.capturedFrames.get() >= captureTarget) break
            Thread.sleep(20)
        }
    }

    private fun appendStamp(target: MutableList<MeasurementClockStamp>, stamp: AudioTimestamp) {
        if (target.lastOrNull()?.let { it.framePosition == stamp.framePosition && it.monotonicNs == stamp.nanoTime } == true) return
        if (target.size < 3000) target += MeasurementClockStamp(stamp.framePosition, stamp.nanoTime)
    }

    private fun captureLoop(s: Session, rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val block = s.inputBlock
        try {
            while (s.running.get() && s.ticket == generation.get()) {
                val count = rec.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) { if (s.running.get()) failIo(s, 1); break }
                val position = s.capturedFrames.get()
                if (position + count > s.capture.size) { failIo(s, 3); break }
                var clipped = 0L
                for (i in 0 until count) if (!block[i].isFinite() || abs(block[i]) >= 0.9999f) clipped++
                if (clipped > 0) s.clippedSamples.addAndGet(clipped)
                System.arraycopy(block, 0, s.capture, position.toInt(), count)
                s.capturedFrames.addAndGet(count.toLong())
            }
        } catch (_: Exception) { if (s.running.get()) failIo(s, 1) }
    }

    private fun outputLoop(s: Session, out: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val block = s.outputBlock
        val samples = s.stimulus.samples
        var cursor = 0
        try {
            while (s.running.get() && s.ticket == generation.get()) {
                java.util.Arrays.fill(block, 0f)
                if (s.emit.get() && cursor < samples.size) {
                    if (cursor == 0) s.stimulusStart.compareAndSet(-1, s.writtenFrames.get())
                    val length = minOf(BLOCK, samples.size - cursor)
                    for (i in 0 until length) {
                        val value = samples[cursor + i]
                        block[i * 2] = value
                        block[i * 2 + 1] = value
                    }
                    cursor += length
                }
                var offset = 0
                while (offset < block.size && s.running.get() && s.ticket == generation.get()) {
                    val count = out.write(block, offset, block.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (count <= 0 || count % 2 != 0) { if (s.running.get()) failIo(s, 2); return }
                    offset += count
                    s.writtenFrames.addAndGet(count / 2L)
                }
            }
        } catch (_: Exception) { if (s.running.get()) failIo(s, 2) }
    }

    private fun finishCapture(s: Session): MeasurementCapture {
        val rec = s.record!!
        val out = s.track!!
        val actualInput = checkNotNull(rec.routedDevice) { "Entrada final sin verificar" }
        val actualOutput = checkNotNull(out.routedDevice) { "Salida final sin verificar" }
        check(actualInput.id == s.input.id && actualOutput.id == s.output.id) { "Ruta final sin verificar" }
        // Stop first, then copy the immutable timeline off the I/O threads.
        s.running.set(false)
        silence(s)
        runCatching { s.record?.stop() }
        s.inputThread?.join(2000)
        s.outputThread?.join(2000)
        check(s.inputThread?.isAlive != true && s.outputThread?.isAlive != true) { "No se pudo cerrar el audio con seguridad" }
        check(s.ioError.get() == 0) { ioMessage(s.ioError.get()) }
        val clock = MeasurementClockReference.map(s.inputStamps, s.outputStamps, s.stimulusStart.get(), RATE)
        val warnings = clock.warnings.toMutableList()
        warnings += "Timestamps Android sin validación física ni compensación del instrumento; no es delay puro del altavoz"
        warnings += "AudioRecord Java no informa todos los posibles frames perdidos; requiere validación de continuidad física"
        if (isBluetooth(s.output.type)) warnings += "Bluetooth experimental: códec, búfer y latencia pueden variar entre medidas"
        if (s.overrunSuspected) warnings += "Posible pérdida de captura: timestamp adelantado respecto a los frames leídos"
        val underruns = out.underrunCount
        if (underruns > 0) warnings += "Underruns de salida detectados: $underruns"
        if (s.clippedSamples.get() > 0) warnings += "Clipping o muestras no finitas en captura"
        val valid = clock.valid && !s.overrunSuspected && underruns == 0 && s.clippedSamples.get() == 0L &&
            clock.captureFrame + s.stimulus.samples.size + RATE * 2 <= s.capturedFrames.get()
        if (clock.valid && clock.captureFrame + s.stimulus.samples.size + RATE * 2 > s.capturedFrames.get()) {
            warnings += "La captura no conserva toda la cola para el intervalo de retardo de 2 s"
        }
        val metadata = MeasurementAudioMetadata(s.input.id, s.output.id, actualInput.id, actualOutput.id,
            s.input.routeName(), s.output.routeName(), actualInput.routeName(), actualOutput.routeName(),
            actualInput.type, actualOutput.type, rec.sampleRate, out.sampleRate, rec.channelCount, out.channelCount,
            rec.bufferSizeInFrames, out.bufferSizeInFrames, s.volume, s.volumeMax, s.source, s.effectsDescription, s.unprocessed,
            isBluetooth(s.output.type), s.clippedSamples.get(), 0, if (s.overrunSuspected) 1 else 0, underruns,
            s.inputStamps.toList(), s.outputStamps.toList(), s.stimulusStart.get(), valid, warnings)
        return MeasurementCapture(s.capture.copyOf(s.capturedFrames.get().toInt()), clock.captureFrame, metadata)
    }

    fun stop(reason: String = "Medida detenida") {
        synchronized(transition) {
            generation.incrementAndGet()
            active?.let(::silence)
            _state.update { it.copy(preparing = false, running = false, emitting = false, routingVerified = false, message = reason) }
        }
        if (!closed) execute { active?.let(::shutdown) }
    }

    /** Faults must finish the active request with onError, rather than strand its caller. */
    private fun abort(reason: String) {
        synchronized(transition) {
            val s = active ?: return
            if (!isCurrent(s)) return
            s.failureReason = reason
            silence(s)
            _state.update { it.copy(preparing = false, running = false, emitting = false, routingVerified = false, message = reason) }
        }
    }

    /** Runs only after pending audio cleanup; useful when handing the microphone to the RTA. */
    fun afterStopped(action: () -> Unit) {
        if (!closed) execute { active?.let(::shutdown); action() }
    }

    private fun silence(s: Session) {
        s.emit.set(false)
        s.armed.set(false)
        s.running.set(false)
        s.track?.let { out -> runCatching { out.setVolume(0f); out.pause(); out.flush() } }
        runCatching { s.record?.stop() }
    }

    private fun failIo(s: Session, error: Int) {
        s.ioError.compareAndSet(0, error)
        silence(s)
    }

    private fun shutdown(s: Session) {
        if (s.released) return
        silence(s)
        runCatching { s.track?.stop() }
        s.inputThread?.join(2000)
        s.outputThread?.join(2000)
        s.record?.let { runCatching { it.removeOnRoutingChangedListener(routeListener); it.release() } }
        s.track?.let { runCatching { it.removeOnRoutingChangedListener(routeListener); it.release() } }
        s.effects.forEach { runCatching { it.release() } }
        s.focus?.let { runCatching { manager.abandonAudioFocusRequest(it) } }
        s.released = true
        synchronized(transition) { if (active === s) active = null }
    }

    private fun routesMatch(s: Session) = s.record?.routedDevice?.id == s.input.id && s.track?.routedDevice?.id == s.output.id
    private fun isCurrent(s: Session) = s.ticket == generation.get() && !closed

    private fun disableEffects(sessionId: Int, s: Session): String {
        val factories: List<Pair<String, () -> AudioEffect?>> = listOf(
            "AGC" to { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId) else null },
            "NS" to { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId) else null },
            "AEC" to { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId) else null },
        )
        return factories.joinToString(" · ") { (name, factory) ->
            runCatching {
                val effect = factory()
                if (effect == null) "$name: no controlable" else {
                    s.effects += effect
                    val result = effect.setEnabled(false)
                    "$name: ${if (result == AudioEffect.SUCCESS && !effect.enabled) "desactivado por API" else "no desactivado"}"
                }
            }.getOrDefault("$name: no controlable")
        } + "; DSP del fabricante sin verificar"
    }

    fun close() {
        synchronized(transition) {
            if (closed) return
            generation.incrementAndGet()
            closed = true
            active?.let(::silence)
            _state.update { it.copy(preparing = false, running = false, emitting = false, message = "Motor cerrado") }
        }
        manager.unregisterAudioDeviceCallback(deviceCallback)
        execute { active?.let(::shutdown); control.shutdown() }
    }

    private fun execute(action: () -> Unit) {
        try { control.execute(action) }
        catch (e: RejectedExecutionException) { if (!closed) throw e }
    }

    private class Session(val ticket: Long, val input: AudioDeviceInfo, val output: AudioDeviceInfo, val stimulus: MeasurementStimulus) {
        val running = AtomicBoolean()
        val armed = AtomicBoolean()
        val emit = AtomicBoolean()
        val capture = FloatArray(stimulus.samples.size + RATE * 8)
        val inputBlock = FloatArray(BLOCK)
        val outputBlock = FloatArray(BLOCK * 2)
        val capturedFrames = AtomicLong()
        val writtenFrames = AtomicLong()
        val stimulusStart = AtomicLong(-1)
        val clippedSamples = AtomicLong()
        val ioError = AtomicInteger()
        val inputStamps = mutableListOf<MeasurementClockStamp>()
        val outputStamps = mutableListOf<MeasurementClockStamp>()
        val effects = mutableListOf<AudioEffect>()
        @Volatile var record: AudioRecord? = null
        @Volatile var track: AudioTrack? = null
        @Volatile var failureReason: String? = null
        var inputThread: Thread? = null
        var outputThread: Thread? = null
        var focus: AudioFocusRequest? = null
        var volume = 0
        var volumeMax = 0
        var source = "Sin abrir"
        var effectsDescription = "Sin comprobar"
        var unprocessed = false
        var overrunSuspected = false
        var released = false
    }

    companion object {
        private const val RATE = 48000
        private const val BLOCK = 512
        private fun isBluetooth(type: Int) = type in BluetoothOutputTypes
        private fun ioMessage(error: Int) = when (error) {
            1 -> "Lectura de micrófono interrumpida"
            2 -> "Escritura de salida interrumpida"
            3 -> "Captura excedió el búfer acotado; no se han ocultado muestras"
            else -> "Error de continuidad de audio"
        }
    }
}
