package es.sonda.measurement.audio

import android.Manifest
import android.app.Application
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import es.sonda.measurement.MeasurementConfig
import es.sonda.measurement.MeasurementDsp
import es.sonda.measurement.MeasurementSignal
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Exercises cancellation and error completion, without pretending Robolectric is an audio device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeasurementAudioEngineTest {
    private lateinit var app: Application
    private lateinit var engine: MeasurementAudioEngine
    private val config = MeasurementConfig(kind = MeasurementSignal.PULSE, lowHz = 80.0, highHz = 12000.0,
        durationSeconds = 0.08, preSilenceSeconds = 0.05, tailSeconds = 0.1)

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val manager = app.getSystemService(AudioManager::class.java)
        shadowOf(manager).setInputDevices(emptyList())
        shadowOf(manager).setOutputDevices(emptyList())
        engine = MeasurementAudioEngine(app)
    }

    @After fun tearDown() { engine.close() }

    @Test fun deniedMicrophoneCompletesWithErrorAndNeverClaimsAValidRoute() {
        val error = AtomicReference<String>()
        val complete = AtomicInteger()
        val finished = CountDownLatch(1)
        engine.run(MeasurementDsp.create(config), null, 987654,
            onComplete = { complete.incrementAndGet(); finished.countDown() },
            onError = { error.set(it); finished.countDown() })
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(error.get().contains("permiso de micrófono"))
        assertEquals(0, complete.get())
        assertIdle()
        assertNull(shadowOf(app.getSystemService(AudioManager::class.java)).lastAudioFocusRequest)
    }

    @Test fun disconnectedSelectedInputReportsErrorWithoutFallingBackToAnotherRoute() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val error = AtomicReference<String>()
        val complete = AtomicInteger()
        val finished = CountDownLatch(1)
        engine.run(MeasurementDsp.create(config), 987654, 765432,
            onComplete = { complete.incrementAndGet(); finished.countDown() },
            onError = { error.set(it); finished.countDown() })
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(error.get().contains("Entrada integrada o USB seleccionada no disponible"))
        assertEquals(0, complete.get())
        assertIdle()
        assertNull(shadowOf(app.getSystemService(AudioManager::class.java)).lastAudioFocusRequest)
    }

    @Test fun unsupportedStimulusRateFinishesTheRequestBeforeOpeningAnyStream() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val error = AtomicReference<String>()
        val finished = CountDownLatch(1)
        engine.run(MeasurementDsp.create(config.copy(sampleRate = 44100)), null, 765432,
            onComplete = { fail("An unsupported stream format must not complete as a capture") },
            onError = { error.set(it); finished.countDown() })
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(error.get().contains("48 kHz"))
        assertIdle()
    }

    @Test fun repeatedQueuedStartsThenStopsNeverArmAndNeverDeliverStaleCallbacks() {
        val controlEntered = CountDownLatch(1)
        val releaseControl = CountDownLatch(1)
        val drained = CountDownLatch(1)
        val callbacks = AtomicInteger()
        val stimulus = MeasurementDsp.create(config)
        engine.afterStopped {
            controlEntered.countDown()
            releaseControl.await(5, TimeUnit.SECONDS)
        }
        assertTrue(controlEntered.await(5, TimeUnit.SECONDS))
        try {
            repeat(20) {
                engine.run(stimulus, null, 987654,
                    onComplete = { callbacks.incrementAndGet() }, onError = { callbacks.incrementAndGet() })
                engine.stop("Cancelación de prueba")
            }
            engine.afterStopped { drained.countDown() }
        } finally { releaseControl.countDown() }
        assertTrue(drained.await(5, TimeUnit.SECONDS))
        assertEquals(0, callbacks.get())
        assertEquals("Cancelación de prueba", engine.state.value.message)
        assertIdle()
        assertNull(shadowOf(app.getSystemService(AudioManager::class.java)).lastAudioFocusRequest)
    }

    private fun assertIdle() {
        assertFalse(engine.state.value.preparing)
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        assertFalse(engine.state.value.routingVerified)
    }
}
