package es.sonda

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appStartsSimulationNavigatesAndStopsWhenBackgrounded() {
        compose.onNodeWithText("SONDA").assertIsDisplayed()
        compose.onNodeWithText("Explorar simulación sin micrófono").performScrollTo().performClick()
        val model = ViewModelProvider(compose.activity)[SondaViewModel::class.java]
        try {
            compose.waitUntil(5000) { model.engine.state.value.running && model.engine.analysis.value != null }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Simulation did not start: ${model.engine.state.value}", failure)
        }
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
}
