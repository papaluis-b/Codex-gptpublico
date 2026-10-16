package es.sonda.generador

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import es.sonda.dsp.SignalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GeneratorAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun octaveControlsAndPinkSelectionConfigureTheGeneratorWithoutEmitting() {
        val model = model()
        compose.onNodeWithText("SONDA Generador").assertIsDisplayed()
        compose.onNodeWithText("Preparar salida").assertIsNotEnabled()
        for (division in listOf(3, 24, 32, 48)) {
            compose.onNodeWithText("1/$division oct").performScrollTo().performClick()
            val initialFrequency = compose.runOnIdle { model.engine.state.value.settings.frequencyHz }
            compose.onNodeWithContentDescription("Subir un paso de frecuencia").performScrollTo().performClick()
            compose.runOnIdle {
                val state = model.engine.state.value
                assertEquals(division, state.settings.octaveDivision)
                assertEquals(initialFrequency * 2.0.pow(1.0 / division), state.settings.frequencyHz, 1e-8)
                assertFalse(state.emitting)
            }
            compose.onNodeWithContentDescription("Bajar un paso de frecuencia").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(initialFrequency, model.engine.state.value.settings.frequencyHz, 1e-8)
                assertFalse(model.engine.state.value.emitting)
            }
        }
        compose.onNodeWithText("Ruido rosa").performScrollTo().performClick()
        compose.onNodeWithText("CAMPANA DE RUIDO ROSA · 1/48 OCTAVA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Nivel del ruido rosa en dBFS RMS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Emitir ruido rosa").assertIsNotEnabled()
        compose.runOnIdle {
            val state = model.engine.state.value
            assertEquals(SignalKind.PINK_NOISE, state.settings.kind)
            assertEquals(48, state.settings.octaveDivision)
            assertEquals("rms", state.settings.levelReference)
            val lowerHz = requireNotNull(state.noiseLowerHz)
            val upperHz = requireNotNull(state.noiseUpperHz)
            assertTrue(lowerHz < state.settings.frequencyHz)
            assertTrue(upperHz > state.settings.frequencyHz)
            assertEquals(2.0.pow(1.0 / 48), upperHz / lowerHz, 1e-10)
            assertFalse(state.emitting)
        }
    }

    @Test fun manualFrequencyAcceptsNumbersAndRejectsInvalidTextWithoutEmitting() {
        val model = model()
        compose.onNodeWithText("Frecuencia · Hz").performScrollTo().performTextReplacement("1250.5")
        compose.onNodeWithText("Aplicar Hz").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1250.5, model.engine.state.value.settings.frequencyHz, 1e-8)
            assertFalse(model.engine.state.value.emitting)
        }
        for (invalid in listOf("-", "NaN", "25000")) {
            compose.onNodeWithText("Frecuencia · Hz").performScrollTo().performTextReplacement(invalid)
            compose.onNodeWithText("Aplicar Hz").performScrollTo().performClick()
            compose.onNodeWithText("Introduce entre 20 y", substring = true).performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(1250.5, model.engine.state.value.settings.frequencyHz, 1e-8)
                assertTrue(model.engine.state.value.settings.frequencyHz.isFinite())
                assertFalse(model.engine.state.value.emitting)
            }
        }
        compose.onNodeWithText("Emitir seno").assertIsNotEnabled()
    }

    @Test fun changingSignalUsesTheCorrectLevelReferenceAndPreservesSilence() {
        val model = model()
        compose.runOnIdle { model.engine.setTone(1000.0, -12.0) }
        compose.onNodeWithContentDescription("Nivel del seno en dBFS pico").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ruido rosa").performScrollTo().performClick()
        compose.runOnIdle {
            val settings = model.engine.state.value.settings
            assertEquals(SignalKind.PINK_NOISE, settings.kind)
            assertEquals(-24.0, settings.levelDbFs, 0.0)
            assertEquals("rms", settings.levelReference)
            assertFalse(model.engine.state.value.emitting)
        }
        compose.onNodeWithContentDescription("Nivel del ruido rosa en dBFS RMS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seno").performScrollTo().performClick()
        compose.runOnIdle {
            val settings = model.engine.state.value.settings
            assertEquals(SignalKind.SINE, settings.kind)
            assertEquals(-24.0, settings.levelDbFs, 0.0)
            assertEquals("peak", settings.levelReference)
            assertFalse(model.engine.state.value.emitting)
        }
        compose.onNodeWithText("Emitir seno").assertIsNotEnabled()
    }

    @Suppress("DEPRECATION")
    @Test fun standalonePackageDoesNotRequestMicrophoneOrInternetPermissions() {
        val model = model()
        val context = compose.activity.applicationContext
        assertEquals("es.sonda.generador", BuildConfig.APPLICATION_ID)
        assertEquals("es.sonda.generador", context.packageName)
        val permissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()
        assertFalse(permissions.contains(Manifest.permission.RECORD_AUDIO))
        assertFalse(permissions.contains(Manifest.permission.INTERNET))
        compose.runOnIdle { assertFalse(model.engine.state.value.emitting) }
    }

    private fun model(): GeneratorViewModel = compose.runOnIdle {
        ViewModelProvider(compose.activity)[GeneratorViewModel::class.java]
    }
}
