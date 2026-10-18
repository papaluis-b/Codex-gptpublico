package es.sonda.generador.kit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.sonda.generador.audio.GeneratorAudioBackend
import es.sonda.generador.audio.GeneratorAudioSink
import es.sonda.generador.audio.GeneratorOutputDevice
import es.sonda.generador.audio.GeneratorOutputEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GeneratorScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var backend: FakeBackend
    private lateinit var engine: GeneratorOutputEngine

    @Before fun prepare() {
        backend = FakeBackend()
        engine = GeneratorOutputEngine(backend)
    }

    @After fun finish() {
        engine.close()
        compose.waitUntil(5000) { backend.closed.get() }
    }

    @Test fun leavingTheScreenStopsAndFlushesButTheHostCanReuseTheSameEngine() {
        val owner = compose.runOnIdle { ScreenOwner(Lifecycle.State.RESUMED) }
        var shown by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown) GeneratorScreen(engine)
            }
        }
        val firstSink = startAndEmit()
        compose.runOnIdle { shown = false }
        // Flush the recomposition that removes the screen before waiting on the audio worker.
        compose.waitForIdle()
        compose.onNodeWithText("SONDA Generador").assertDoesNotExist()
        compose.waitUntil(5000) { !engine.state.value.running && firstSink.flushes.get() > 0 }
        compose.runOnIdle {
            // The Activity and supplied owner remain resumed: disposal alone must stop output.
            assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
            assertStopped()
            assertFalse(backend.closed.get())
            shown = true
        }
        compose.waitForIdle()
        compose.runOnIdle { assertStopped() }
        assertEquals(1, backend.sinks.size)

        val secondSink = startAndEmit()
        compose.runOnIdle {
            assertTrue(engine.state.value.emitting)
            assertFalse(backend.closed.get())
        }
        assertEquals(2, backend.sinks.size)
        assertTrue(secondSink.nonzeroWrites.get() > 0)
    }

    @Test fun pausingTheSuppliedLifecycleStopsOutputAndResumeCannotRearmIt() {
        val owner = compose.runOnIdle { ScreenOwner(Lifecycle.State.RESUMED) }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { GeneratorScreen(engine) }
        }
        val sink = startAndEmit()
        // Only this supplied owner pauses. No Activity.onPause override can provide the stop.
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        compose.waitUntil(5000) { !engine.state.value.running && sink.flushes.get() > 0 }
        compose.runOnIdle {
            assertStopped()
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
            assertStopped()
            assertFalse(backend.closed.get())
        }
        assertEquals(1, backend.sinks.size)
    }

    @Test fun mountingWithAnAlreadyPausedOwnerStopsAnExistingOutputImmediately() {
        val owner = compose.runOnIdle { ScreenOwner(Lifecycle.State.CREATED) }
        val sink = startAndEmit()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { GeneratorScreen(engine) }
        }
        compose.waitUntil(5000) { !engine.state.value.running && sink.flushes.get() > 0 }
        compose.runOnIdle {
            // Registering at CREATED delivers no ON_PAUSE: the initial-state check is required.
            assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
            assertStopped()
            assertFalse(backend.closed.get())
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertStopped() }
        assertEquals(1, backend.sinks.size)
    }

    private fun startAndEmit(): FakeSink {
        val previousSinks = backend.sinks.size
        compose.runOnIdle { engine.start(USB) }
        compose.waitUntil(5000) { engine.state.value.running && backend.sinks.size > previousSinks }
        val sink = backend.sinks.last()
        compose.runOnIdle { engine.emit(true) }
        compose.waitUntil(5000) { sink.nonzeroWrites.get() > 0 }
        return sink
    }

    private fun assertStopped() {
        val state = engine.state.value
        assertFalse("Preparing after screen stop: $state", state.preparing)
        assertFalse("Running after screen stop: $state", state.running)
        assertFalse("Emitting after screen stop: $state", state.emitting)
        assertFalse("Route still armed after screen stop: $state", state.routingVerified)
    }

    private class ScreenOwner(initial: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = initial }
        override val lifecycle: Lifecycle get() = registry
    }

    private class FakeBackend : GeneratorAudioBackend {
        val sinks = CopyOnWriteArrayList<FakeSink>()
        val closed = AtomicBoolean()
        override fun outputs() = listOf(GeneratorOutputDevice(USB, "USB de prueba", true, false))
        override fun observeDevices(onChange: () -> Unit) = Unit
        override fun requestFocus(onLoss: () -> Unit) = !closed.get()
        override fun abandonFocus() = Unit
        override fun open(outputId: Int, onRouteChange: () -> Unit): GeneratorAudioSink =
            FakeSink().also { sinks += it }
        override fun mediaVolume() = 4
        override fun mediaVolumeMax() = 15
        override fun close() { closed.set(true) }
    }

    private class FakeSink : GeneratorAudioSink {
        override val sampleRate = 48000
        override val channels = 2
        override val bufferFrames = 2048
        override val underruns = 0
        override val routedDeviceId = USB
        val flushes = AtomicInteger()
        val nonzeroWrites = AtomicInteger()
        @Volatile private var released = false
        override fun play() = Unit
        override fun write(data: FloatArray, offset: Int, count: Int): Int {
            Thread.sleep(2)
            if (released) return -6
            for (i in offset until offset + count) {
                if (data[i] != 0f) { nonzeroWrites.incrementAndGet(); break }
            }
            return count
        }
        override fun pauseAndFlush() { flushes.incrementAndGet() }
        override fun stop() = Unit
        override fun release() { released = true }
    }

    private companion object { const val USB = 7 }
}
