package es.sonda

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
}
