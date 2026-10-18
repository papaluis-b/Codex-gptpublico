package es.sonda.generador.audio

import es.sonda.dsp.GeneratorSettings
import es.sonda.dsp.SignalKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GeneratorOutputEngineTest {
    private lateinit var backend: FakeBackend
    private lateinit var engine: GeneratorOutputEngine

    @Before fun prepare() {
        backend = FakeBackend()
        engine = GeneratorOutputEngine(backend)
    }

    @After fun finish() { engine.close(); backend.openGate?.countDown() }

    @Test fun outputStartsWithZerosAndNeedsItsExactRouteBeforeUserCanEmit() {
        backend.initialRoute = null
        engine.start(USB)
        await { backend.sinks.isNotEmpty() && backend.sinks[0].writes.get() > 1 }
        val sink = backend.sinks.single()
        engine.emit(true)
        assertTrue(engine.state.value.preparing)
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.routingVerified)
        assertFalse(engine.state.value.emitting)
        assertEquals(0, sink.nonzeroWrites.get())
        sink.changeRoute(USB)
        await { engine.state.value.running }
        assertTrue(engine.state.value.routingVerified)
        assertEquals(0, sink.nonzeroWrites.get())
        engine.emit(true)
        await { sink.nonzeroWrites.get() > 0 }
        assertTrue(engine.state.value.emitting)
        engine.emit(false)
        await { sink.lastBlockWasSilent }
        assertFalse(engine.state.value.emitting)
    }

    @Test fun aDifferentDefaultRouteCannotBeAcceptedAsTheSelectedUsbOutput() {
        backend.initialRoute = SPEAKER
        engine.start(USB)
        await { !engine.state.value.preparing && engine.state.value.message.startsWith("No se pudo iniciar") }
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        assertFalse(engine.state.value.routingVerified)
        await { backend.sinks.single().released }
        assertEquals(0, backend.sinks.single().nonzeroWrites.get())
        engine.emit(true)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun losingFocusFlushesAndStopsWithoutRearmingAfterFocusReturns() {
        startAndEmit()
        val sink = backend.sinks.single()
        backend.focusLoss!!.invoke()
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        assertFalse(engine.state.value.routingVerified)
        assertEquals("Foco de audio perdido: emisión detenida", engine.state.value.message)
        assertTrue(sink.flushes.get() > 0)
        await { sink.released }
        engine.emit(true)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun routeLossAndRestorationNeverAutomaticallyRearm() {
        startAndEmit()
        val sink = backend.sinks.single()
        sink.changeRoute(SPEAKER)
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        assertTrue(sink.flushes.get() > 0)
        sink.changeRoute(USB)
        Thread.sleep(150)
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        engine.emit(true)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun disconnectedUsbStopsAndUnsupportedOutputsAreNeverOpened() {
        startAndEmit()
        backend.available = listOf(backend.speaker)
        backend.devicesChanged!!.invoke()
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        await { backend.sinks.single().released }
        engine.start(999)
        await { !engine.state.value.preparing && engine.state.value.message.startsWith("No se pudo iniciar") }
        assertEquals(1, backend.sinks.size)
        assertEquals(listOf(backend.speaker), engine.devices.value)
    }

    @Test fun deniedFocusOpensNoTrackAndCannotEmit() {
        backend.focusAllowed = false
        engine.start(SPEAKER)
        await { !engine.state.value.preparing && engine.state.value.message.startsWith("No se pudo iniciar") }
        assertTrue(backend.sinks.isEmpty())
        engine.emit(true)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun cancellationDuringOpenReleasesTheOldSessionBeforePreparingTheNewOne() {
        backend.openGate = CountDownLatch(1)
        engine.start(USB)
        assertTrue(backend.openEntered.await(2, TimeUnit.SECONDS))
        engine.stop("Arranque cancelado")
        engine.start(SPEAKER)
        backend.openGate!!.countDown()
        await { engine.state.value.running && engine.state.value.outputId == SPEAKER }
        assertFalse(engine.state.value.emitting)
        assertTrue(backend.sinks[0].released)
        assertEquals(0, backend.sinks[0].nonzeroWrites.get())
        // A focus callback queued by the cancelled USB request must not kill the new speaker session.
        backend.focusCallbacks[0].invoke()
        assertTrue(engine.state.value.running)
        assertEquals(SPEAKER, engine.state.value.outputId)
    }

    @Test fun repeatedQueuedStartsAndStopsCannotLeaveAnOldTrackRunning() {
        repeat(20) { engine.start(USB); engine.stop() }
        Thread.sleep(300)
        assertFalse(engine.state.value.preparing)
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        await { backend.sinks.all { it.released } }
        assertTrue(backend.sinks.all { it.nonzeroWrites.get() == 0 })
    }

    @Test fun actualTrackRateConstrainsPinkBandAndSettingsSurviveReopeningInSilence() {
        backend.rate = 32000
        engine.setOctaveDivision(48)
        engine.setTone(20000.0, -12.0)
        engine.setSignalKind(SignalKind.PINK_NOISE)
        assertEquals(-24.0, engine.state.value.settings.levelDbFs, 0.0)
        engine.start(USB)
        await { engine.state.value.running }
        val settings = engine.state.value.settings
        assertEquals(32000, engine.state.value.sampleRate)
        assertEquals(2, engine.state.value.channels)
        assertEquals(GeneratorSettings.maximumFrequency(32000, SignalKind.PINK_NOISE, 48), settings.frequencyHz, 1e-8)
        assertEquals(32000 * .49, engine.state.value.noiseUpperHz!!, 1e-8)
        assertFalse(engine.state.value.emitting)
        engine.emit(true)
        await { backend.sinks.single().nonzeroWrites.get() > 0 }
        engine.setSignalKind(SignalKind.SINE)
        assertFalse(engine.state.value.emitting)
        engine.stop()
        engine.start(USB)
        await { engine.state.value.running }
        assertEquals(SignalKind.SINE, engine.state.value.settings.kind)
        assertEquals(48, engine.state.value.settings.octaveDivision)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun writeErrorsStopAndCloseCannotBeRearmed() {
        startAndEmit()
        val sink = backend.sinks.single()
        sink.failWrites = true
        await { !engine.state.value.running }
        assertFalse(engine.state.value.emitting)
        assertFalse(engine.state.value.routingVerified)
        assertTrue(engine.state.value.message.startsWith("Error de salida"))
        engine.close()
        engine.start(USB)
        engine.emit(true)
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun continuousFrequencyOnlyChangesFrequencyAndNeverEnablesOrRearmsOutput() {
        engine.setOctaveDivision(48)
        engine.setSignalKind(SignalKind.PINK_NOISE)
        engine.setTone(1000.0, -32.0)
        engine.setContinuousFrequency(1200.0)
        assertFalse(engine.state.value.emitting)
        assertEquals(SignalKind.PINK_NOISE, engine.state.value.settings.kind)
        assertEquals(48, engine.state.value.settings.octaveDivision)
        assertEquals(-32.0, engine.state.value.settings.levelDbFs, 0.0)
        engine.start(USB)
        await { engine.state.value.running }
        engine.setContinuousFrequency(1300.0)
        assertFalse(engine.state.value.emitting)
        engine.emit(true)
        await { backend.sinks.single().nonzeroWrites.get() > 0 }
        repeat(30) { engine.setContinuousFrequency(1300.0 + it) }
        assertTrue(engine.state.value.emitting)
        backend.focusLoss!!.invoke()
        engine.setContinuousFrequency(1350.0)
        assertFalse(engine.state.value.emitting)
        assertFalse(engine.state.value.running)
        engine.start(USB)
        await { engine.state.value.running }
        engine.emit(true)
        await { backend.sinks.last().nonzeroWrites.get() > 0 }
        engine.setContinuousFrequency(1375.0)
        engine.setSignalKind(SignalKind.SINE)
        assertFalse(engine.state.value.emitting)
        engine.setContinuousFrequency(1400.0)
        assertFalse(engine.state.value.emitting)
        engine.stop("Prueba de interrupción durante arrastre")
        engine.setContinuousFrequency(1500.0)
        engine.emit(true)
        assertFalse(engine.state.value.emitting)
        assertFalse(engine.state.value.running)
        assertEquals(1500.0, engine.state.value.settings.frequencyHz, 0.0)
        assertEquals(-32.0, engine.state.value.settings.levelDbFs, 0.0)
    }

    private fun startAndEmit() {
        engine.start(USB)
        await { engine.state.value.running }
        engine.emit(true)
        await { backend.sinks.single().nonzeroWrites.get() > 0 }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("Operation timed out; state=${engine.state.value}", condition())
    }

    private class FakeBackend : GeneratorAudioBackend {
        val usb = GeneratorOutputDevice(USB, "USB DAC", true, false)
        val speaker = GeneratorOutputDevice(SPEAKER, "Altavoz interno", false, true)
        @Volatile var available = listOf(usb, speaker)
        @Volatile var initialRoute: Int? = USB
        @Volatile var rate = 48000
        @Volatile var focusAllowed = true
        var devicesChanged: (() -> Unit)? = null
        @Volatile var focusLoss: (() -> Unit)? = null
        val focusCallbacks = CopyOnWriteArrayList<() -> Unit>()
        val sinks = CopyOnWriteArrayList<FakeSink>()
        @Volatile var openGate: CountDownLatch? = null
        val openEntered = CountDownLatch(1)
        override fun outputs() = available
        override fun observeDevices(onChange: () -> Unit) { devicesChanged = onChange }
        override fun requestFocus(onLoss: () -> Unit): Boolean {
            focusLoss = onLoss
            focusCallbacks += onLoss
            return focusAllowed
        }
        override fun abandonFocus() = Unit
        override fun open(outputId: Int, onRouteChange: () -> Unit): GeneratorAudioSink {
            openEntered.countDown()
            openGate?.await(3, TimeUnit.SECONDS)
            val route = if (outputId == SPEAKER && initialRoute == USB) SPEAKER else initialRoute
            return FakeSink(rate, route, onRouteChange).also { sinks += it }
        }
        override fun mediaVolume() = 4
        override fun mediaVolumeMax() = 15
        override fun close() = Unit
    }

    private class FakeSink(
        override val sampleRate: Int,
        @Volatile override var routedDeviceId: Int?,
        private val onRouteChange: () -> Unit,
    ) : GeneratorAudioSink {
        override val channels = 2
        override val bufferFrames = 2048
        override val underruns = 0
        val writes = AtomicLong()
        val nonzeroWrites = AtomicInteger()
        val flushes = AtomicInteger()
        @Volatile var lastBlockWasSilent = true
        @Volatile var released = false
        @Volatile var failWrites = false
        override fun play() = Unit
        override fun write(data: FloatArray, offset: Int, count: Int): Int {
            Thread.sleep(2)
            if (failWrites || released) return -6
            var nonzero = false
            for (i in offset until offset + count) if (data[i] != 0f) { nonzero = true; break }
            lastBlockWasSilent = !nonzero
            if (nonzero) nonzeroWrites.incrementAndGet()
            writes.incrementAndGet()
            return count
        }
        override fun pauseAndFlush() { flushes.incrementAndGet() }
        override fun stop() = Unit
        override fun release() { released = true }
        fun changeRoute(id: Int?) { routedDeviceId = id; onRouteChange() }
    }

    private companion object {
        const val USB = 7
        const val SPEAKER = 1
    }
}
