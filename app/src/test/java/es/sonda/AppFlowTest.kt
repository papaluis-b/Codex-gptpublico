package es.sonda

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import es.sonda.dsp.SignalKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appStartsSimulationNavigatesAndStopsWhenBackgrounded() {
        compose.onNodeWithText("SONDA").assertIsDisplayed()
        compose.onNodeWithText("Explorar simulación sin micrófono").performScrollTo().performClick()
        val model = ViewModelProvider(compose.activity)[SondaViewModel::class.java]
        awaitAnalysisStart(model)
        compose.onNodeWithText("SIMULACIÓN · DATOS SINTÉTICOS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Generar").performClick()
        compose.onNodeWithText("Emitir seno").assertIsNotEnabled()
        assertFalse(model.engine.state.value.emitting)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(model.engine.state.value.running)
        assertFalse(model.engine.state.value.emitting)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText("Iniciar audio real").performScrollTo().assertIsDisplayed()
        assertFalse(model.engine.state.value.running)
    }

    @Test fun generatorSelectsAllFractionsAndPinkNoiseWithoutActivatingOutput() {
        compose.onNodeWithText("Generar").performClick()
        val model = ViewModelProvider(compose.activity)[SondaViewModel::class.java]
        var expected = 1000.0
        for (division in listOf(3, 24, 32, 48)) {
            compose.onNodeWithText("1/$division oct").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Subir un paso de frecuencia").performScrollTo().performClick()
            expected *= 2.0.pow(1.0 / division)
            compose.runOnIdle {
                assertEquals(division, model.engine.state.value.octaveDivision)
                assertEquals(expected, model.engine.state.value.frequencyHz, 1e-8)
                assertFalse(model.engine.state.value.emitting)
            }
        }
        compose.onNodeWithText("Ruido rosa").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Nivel del ruido rosa en dBFS RMS").assertExists()
        compose.onNodeWithText("Emitir ruido rosa").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            val s = model.engine.state.value
            assertEquals(SignalKind.PINK_NOISE, s.signalKind)
            assertEquals(48, s.octaveDivision)
            assertEquals(expected, s.frequencyHz, 1e-8)
            assertTrue(s.noiseLowerHz!! < expected && s.noiseUpperHz!! > expected)
            assertFalse(s.emitting)
        }
        compose.onNodeWithText("Seno").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SignalKind.SINE, model.engine.state.value.signalKind) }
    }

    @Test fun analyzerModulesCanBeShownIndependentlyWithoutOpeningAudio() {
        val model = ViewModelProvider(compose.activity)[SondaViewModel::class.java]
        // The chips have their own horizontal scroll: bring their section into the vertical viewport first.
        compose.onNodeWithText("RTA · Densidad de potencia · dBFS/Hz").performScrollTo()
        compose.onNodeWithText("RTA").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("RTA").assertIsSelected()
        compose.onNodeWithContentDescription("Espectro de potencia", substring = true).assertExists()
        compose.onNodeWithContentDescription("Espectrograma:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Espectrograma").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Espectrograma").assertIsSelected()
        compose.onNodeWithContentDescription("Espectro de potencia", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Espectrograma:", substring = true).assertExists()
        compose.onNodeWithText("Ambos").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ambos").assertIsSelected()
        compose.onNodeWithContentDescription("Espectro de potencia", substring = true).assertExists()
        compose.onNodeWithContentDescription("Espectrograma:", substring = true).assertExists()
        compose.runOnIdle {
            assertFalse(model.engine.state.value.running)
            assertFalse(model.engine.state.value.emitting)
            assertFalse(model.measurement.audio.state.value.running)
        }
    }

    @Test fun missingUsbInputRequiresAnExplicitNewChoiceBeforeOpeningAudio() {
        val model = ViewModelProvider(compose.activity)[SondaViewModel::class.java]
        compose.runOnIdle { model.selectInput(987654) }
        compose.waitForIdle()
        compose.onNodeWithText("Entrada USB no disponible").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Iniciar audio real").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(987654, model.selectedInput.value)
            assertFalse(model.engine.state.value.preparing)
            assertFalse(model.engine.state.value.running)
        }
        compose.onNodeWithContentDescription("Elegir entrada de audio").performScrollTo().performClick()
        compose.onNodeWithText("Micrófono interno").performClick()
        compose.onNodeWithText("Iniciar audio real").assertIsEnabled()
        compose.runOnIdle {
            assertNull(model.selectedInput.value)
            assertFalse(model.engine.state.value.preparing)
            assertFalse(model.engine.state.value.running)
            assertFalse(model.engine.state.value.emitting)
        }
    }

    @Test fun enteringMeasurementStopsAnalysisAndLeavingDoesNotRestartEitherStream() {
        val model = ViewModelProvider(compose.activity)[SondaViewModel::class.java]
        compose.onNodeWithText("Explorar simulación sin micrófono").performScrollTo().performClick()
        awaitAnalysisStart(model)
        compose.onNodeWithText("Medir").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Medir altavoces").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(model.engine.state.value.running)
            assertFalse(model.engine.state.value.emitting)
            assertFalse(model.measurement.audio.state.value.running)
            assertFalse(model.measurement.audio.state.value.emitting)
        }
        compose.onNodeWithText("Explorar SIMULACIÓN · sin emisión").performScrollTo().performClick()
        compose.onNodeWithText("Generar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Emitir seno").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            assertFalse(model.measurement.state.value.busy)
            assertFalse(model.measurement.audio.state.value.running)
            assertFalse(model.measurement.audio.state.value.emitting)
            assertFalse(model.engine.state.value.running)
        }
        compose.onNodeWithText("Medir").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(model.measurement.state.value.busy)
            assertFalse(model.measurement.audio.state.value.running)
            assertFalse(model.measurement.audio.state.value.emitting)
        }
    }

    @Test fun cancellingAPendingAnalysisHandoffCannotStartAudioAfterStopOrResume() {
        val model = ViewModelProvider(compose.activity)[SondaViewModel::class.java]
        val controlEntered = CountDownLatch(1)
        val releaseControl = CountDownLatch(1)
        val controlDrained = CountDownLatch(1)
        val barrierReleasedNormally = AtomicBoolean()
        model.measurement.audio.afterStopped {
            controlEntered.countDown()
            barrierReleasedNormally.set(releaseControl.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue("The measurement control barrier was not entered", controlEntered.await(5, TimeUnit.SECONDS))
            compose.runOnIdle {
                model.startAnalysis(null, true)
                model.stopAll("Arranque pendiente cancelado por el usuario")
            }
            model.measurement.audio.afterStopped { controlDrained.countDown() }
            releaseControl.countDown()
            assertTrue("The queued handoff did not drain", controlDrained.await(5, TimeUnit.SECONDS))
            assertTrue("The test barrier expired before cancellation", barrierReleasedNormally.get())
            compose.waitForIdle()
            compose.runOnIdle {
                assertFalse(model.engine.state.value.preparing)
                assertFalse(model.engine.state.value.running)
                assertFalse(model.engine.state.value.emitting)
                assertEquals(0L, model.engine.state.value.captureFrames)
                assertNull(model.engine.analysis.value)
                assertFalse(model.measurement.audio.state.value.preparing)
                assertFalse(model.measurement.audio.state.value.running)
                assertFalse(model.measurement.audio.state.value.emitting)
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()
            compose.runOnIdle {
                assertFalse(model.engine.state.value.preparing)
                assertFalse(model.engine.state.value.running)
                assertFalse(model.engine.state.value.emitting)
                assertEquals(0L, model.engine.state.value.captureFrames)
                assertNull(model.engine.analysis.value)
                assertFalse(model.measurement.audio.state.value.running)
                assertFalse(model.measurement.audio.state.value.emitting)
            }
        } finally {
            releaseControl.countDown()
        }
    }

    private fun awaitAnalysisStart(model: SondaViewModel) {
        // The audio-control handoff now posts engine.start onto Main. Drain it before idling Compose,
        // otherwise a later Main task can remain queued while waitUntil only observes worker state.
        compose.waitForIdle()
        val handoffDrained = CountDownLatch(1)
        model.measurement.audio.afterStopped { handoffDrained.countDown() }
        assertTrue("Analysis handoff did not drain", handoffDrained.await(5, TimeUnit.SECONDS))
        compose.waitForIdle()
        try {
            compose.waitUntil(5000) { model.engine.state.value.running && model.engine.analysis.value != null }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Simulation did not start: analysis=${model.engine.state.value}; " +
                "measurement=${model.measurement.audio.state.value}; lifecycle=${compose.activity.lifecycle.currentState}", failure)
        }
    }
}
