package es.sonda.generador.audio

import android.content.Context
import android.os.Process
import es.sonda.dsp.GeneratorSettings
import es.sonda.dsp.NoiseBand
import es.sonda.dsp.OctaveBands
import es.sonda.dsp.SignalGenerator
import es.sonda.dsp.SignalKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Foreground-only output. Setup/cleanup are serial; rendering owns all mutable DSP history. */
class GeneratorOutputEngine internal constructor(private val backend: GeneratorAudioBackend) {
    constructor(context: Context) : this(AndroidGeneratorAudioBackend(context))

    private class Session(
        val ticket: Long,
        val device: GeneratorOutputDevice,
        val sink: GeneratorAudioSink,
        val generator: SignalGenerator,
    ) {
        val active = AtomicBoolean(true)
        val verified = AtomicBoolean(false)
        val writtenSamples = AtomicLong()
        val fault = AtomicInteger()
        val block = FloatArray(BLOCK_FRAMES * sink.channels)
        @Volatile var failure: Exception? = null
        var worker: Thread? = null
    }

    private val control = Executors.newSingleThreadScheduledExecutor()
    private val gate = Any() // UI/control state only. The audio worker never acquires this lock.
    private val serial = AtomicLong()
    private val _state = MutableStateFlow(GeneratorOutputState())
    val state = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<GeneratorOutputDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    @Volatile private var session: Session? = null
    @Volatile private var closed = false
    private val monitor = control.scheduleWithFixedDelay({ monitorSession() }, 100, 100, TimeUnit.MILLISECONDS)

    init {
        refreshDevices()
        backend.observeDevices { refreshDevices() }
    }

    private fun refreshDevices() {
        if (closed) return
        val available = backend.outputs().filter { it.isUsb || it.isSpeaker }
        _devices.value = available
        val current = session
        if (current != null && available.none { it.id == current.device.id })
            stopForTicket(current.ticket, "Salida desconectada: emisión detenida")
    }

    fun start(outputId: Int) {
        val ticket: Long
        val old: Session?
        synchronized(gate) {
            if (closed) return
            ticket = serial.incrementAndGet()
            old = session
            disarm(old)
            val device = devices.value.firstOrNull { it.id == outputId }
            _state.update { it.copy(preparing = true, running = false, emitting = false, routingVerified = false,
                requestedOutput = device?.name ?: "Salida #$outputId", activeOutput = "Sin abrir", outputId = outputId,
                sampleRate = 0, channels = 0, bufferFrames = 0, underruns = 0, clippedSamples = 0,
                message = "Preparando salida en silencio…") }
            // Submission is covered by the same lock as close(), so no task reaches a closed executor.
            control.execute {
                shutdownCurrent()
                if (!valid(ticket)) return@execute
                try { prepare(outputId, ticket) }
                catch (failure: Exception) {
                    stopForTicket(ticket, "No se pudo iniciar: ${failure.message ?: failure.javaClass.simpleName}")
                    if (session?.ticket == ticket) shutdownCurrent() else backend.abandonFocus()
                }
            }
        }
        silence(old)
    }

    private fun prepare(outputId: Int, ticket: Long) {
        val device = backend.outputs().firstOrNull { it.id == outputId && (it.isUsb || it.isSpeaker) }
            ?: error("La salida seleccionada ya no está disponible")
        check(backend.requestFocus { stopForTicket(ticket, "Foco de audio perdido: emisión detenida") }) {
            "Foco de audio no concedido; mantén el generador en primer plano"
        }
        if (!valid(ticket)) { backend.abandonFocus(); return }
        val sink = backend.open(outputId) { routingChanged(ticket) }
        val current: Session
        try {
            check(sink.sampleRate > 100 && sink.channels == 2) { "Formato de salida inesperado" }
            synchronized(gate) {
                if (!valid(ticket)) { sink.release(); backend.abandonFocus(); return }
                val generator = SignalGenerator(sink.sampleRate).also { it.configure(state.value.settings) }
                current = Session(ticket, device, sink, generator)
                session = current
                _state.update { withSettings(it.copy(sampleRate = sink.sampleRate, channels = sink.channels,
                    bufferFrames = sink.bufferFrames, mediaVolume = backend.mediaVolume(), mediaVolumeMax = backend.mediaVolumeMax()),
                    generator.settings, generator.noiseBand) }
            }
        } catch (failure: Exception) {
            sink.release()
            throw failure
        }
        if (!valid(ticket)) return
        sink.play()
        current.worker = Thread({ outputLoop(current) }, "sonda-generator-output").apply { start() }
        val deadline = System.nanoTime() + 1_500_000_000L
        while (valid(ticket) && current.active.get() && System.nanoTime() < deadline &&
            (current.writtenSamples.get() == 0L || sink.routedDeviceId != outputId)) Thread.sleep(10)
        if (!valid(ticket)) return
        check(current.active.get() && current.writtenSamples.get() > 0L && sink.routedDeviceId == outputId) {
            "Ruta solicitada no confirmada; no se permite emitir"
        }
        synchronized(gate) {
            if (!valid(ticket) || !current.active.get()) return
            check(sink.routedDeviceId == outputId) { "La ruta cambió al verificar la salida" }
            current.verified.set(true)
            _state.update { it.copy(preparing = false, running = true, routingVerified = true,
                activeOutput = device.name, message = "Ruta confirmada · salida en silencio") }
        }
    }

    private fun outputLoop(current: Session) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val sink = current.sink
        val generator = current.generator
        val block = current.block
        val channels = block.size / BLOCK_FRAMES
        try {
            while (current.active.get()) {
                generator.render(block, BLOCK_FRAMES, channels)
                if (generator.clippedSamples > 0) { failWorker(current, FAULT_CLIPPING); return }
                var offset = 0
                while (offset < block.size && current.active.get()) {
                    val count = sink.write(block, offset, block.size - offset)
                    if (count <= 0 || count > block.size - offset || count % channels != 0) {
                        if (current.active.get()) failWorker(current, FAULT_WRITE)
                        return
                    }
                    offset += count
                    current.writtenSamples.addAndGet(count.toLong())
                }
            }
        } catch (failure: Exception) {
            if (current.active.get()) {
                current.failure = failure
                failWorker(current, FAULT_WRITE)
            }
        }
    }

    /** Only atomics/DSP reset and emergency AudioTrack silence on the fault path, no UI work. */
    private fun failWorker(current: Session, fault: Int) {
        current.active.set(false)
        current.generator.reset()
        current.fault.compareAndSet(0, fault)
        silence(current)
    }

    private fun monitorSession() {
        if (closed) return
        val current = session ?: return
        if (!valid(current.ticket)) return
        try {
            when (current.fault.get()) {
                FAULT_CLIPPING -> { stopForTicket(current.ticket, "Clipping del generador: emisión detenida"); return }
                FAULT_WRITE -> { stopForTicket(current.ticket, "Error de salida: emisión detenida"); return }
            }
            if (current.verified.get() && current.sink.routedDeviceId != current.device.id) {
                stopForTicket(current.ticket, "Ruta cambiada o sin verificar: emisión detenida")
                return
            }
            _state.update {
                if (!valid(current.ticket)) it else it.copy(underruns = current.sink.underruns,
                    clippedSamples = current.generator.clippedSamples,
                    mediaVolume = backend.mediaVolume(), mediaVolumeMax = backend.mediaVolumeMax())
            }
        } catch (_: Exception) { stopForTicket(current.ticket, "Salida interrumpida: emisión detenida") }
    }

    private fun routingChanged(ticket: Long) {
        val current = session ?: return
        if (current.ticket == ticket && current.verified.get() && current.sink.routedDeviceId != current.device.id)
            stopForTicket(ticket, "Ruta cambiada o sin verificar: emisión detenida")
    }

    fun emit(enabled: Boolean) {
        synchronized(gate) {
            if (closed) return
            val current = session
            if (enabled && (current == null || !valid(current.ticket) || !current.active.get() ||
                !current.verified.get() || !state.value.running || current.sink.routedDeviceId != current.device.id)) {
                if (current?.verified?.get() == true && current.sink.routedDeviceId != current.device.id)
                    stopForTicket(current.ticket, "Ruta cambiada o sin verificar: emisión detenida")
                else _state.update { it.copy(message = "Prepara una salida y confirma su ruta antes de emitir") }
                return
            }
            current?.generator?.enabled = enabled
            _state.update { it.copy(emitting = enabled,
                message = if (enabled) "${it.settings.kind.label} activo" else "Emisión en silencio · rampa de 20 ms") }
        }
    }

    fun setTone(frequency: Double, level: Double) = configure { it.copy(frequencyHz = frequency, levelDbFs = level) }
    fun setContinuousFrequency(frequency: Double) = configure(continuousFrequency = true) { it.copy(frequencyHz = frequency) }
    fun setSignalKind(kind: SignalKind) = configure { it.copy(kind = kind) }
    fun setOctaveDivision(division: Int) = configure { it.copy(octaveDivision = division) }
    fun stepFrequency(direction: Int) = configure {
        val maximum = GeneratorSettings.maximumFrequency(state.value.sampleRate.takeIf { rate -> rate > 0 } ?: 48000,
            it.kind, it.octaveDivision)
        it.copy(frequencyHz = OctaveBands.step(it.frequencyHz, it.octaveDivision, direction, maximum))
    }

    private fun configure(continuousFrequency: Boolean = false, change: (GeneratorSettings) -> GeneratorSettings) {
        synchronized(gate) {
            if (closed) return
            val previous = state.value
            val generator = session?.generator
            val rate = generator?.sampleRate ?: previous.sampleRate.takeIf { it > 0 } ?: 48000
            val settings = change(previous.settings).constrained(rate)
            val changedKind = previous.settings.kind != settings.kind
            if (changedKind) generator?.enabled = false
            generator?.configure(settings, continuousFrequency)
            val band = generator?.noiseBand ?: if (settings.kind == SignalKind.PINK_NOISE)
                NoiseBand(settings.frequencyHz, settings.octaveDivision, rate) else null
            _state.update { withSettings(it, settings, band).copy(
                emitting = if (changedKind) false else it.emitting,
                message = if (changedKind) "${settings.kind.label} seleccionado · emisión en silencio" else it.message) }
        }
    }

    fun stop(reason: String = "Audio detenido") { stopInternal(null, reason) }
    private fun stopForTicket(ticket: Long, reason: String) { stopInternal(ticket, reason) }

    private fun stopInternal(expectedTicket: Long?, reason: String) {
        val old: Session?
        synchronized(gate) {
            if (closed || (expectedTicket != null && !valid(expectedTicket))) return
            serial.incrementAndGet()
            old = session
            disarm(old)
            _state.update { it.copy(preparing = false, running = false, emitting = false, routingVerified = false,
                clippedSamples = old?.generator?.clippedSamples ?: it.clippedSamples, message = reason) }
            control.execute { if (session === old) shutdownCurrent() }
        }
        silence(old)
    }

    private fun disarm(current: Session?) {
        current?.verified?.set(false)
        current?.active?.set(false)
        current?.generator?.reset()
    }

    private fun silence(current: Session?) { if (current != null) runCatching { current.sink.pauseAndFlush() } }

    /** Serial control only. Stop/flush first unblocks a pending blocking write before join. */
    private fun shutdownCurrent() {
        val current = session
        if (current != null) {
            disarm(current)
            silence(current)
            runCatching { current.sink.stop() }
            current.worker?.join(2000)
            runCatching { current.sink.release() }
            synchronized(gate) { if (session === current) session = null }
        }
        backend.abandonFocus()
    }

    private fun valid(ticket: Long) = !closed && serial.get() == ticket

    fun close() {
        val old: Session?
        synchronized(gate) {
            if (closed) return
            closed = true
            serial.incrementAndGet()
            old = session
            disarm(old)
            _state.update { it.copy(preparing = false, running = false, emitting = false, routingVerified = false,
                message = "Aplicación cerrada") }
            monitor.cancel(false)
            control.execute { shutdownCurrent(); backend.close() }
            control.shutdown()
        }
        silence(old)
    }

    private fun withSettings(state: GeneratorOutputState, settings: GeneratorSettings, band: NoiseBand?) = state.copy(
        settings = settings, noiseLowerHz = band?.lowerHz, noiseUpperHz = band?.upperHz,
        noiseSettlingSeconds = band?.settlingSeconds)

    private companion object {
        const val BLOCK_FRAMES = 1024
        const val FAULT_CLIPPING = 1
        const val FAULT_WRITE = 2
    }
}
