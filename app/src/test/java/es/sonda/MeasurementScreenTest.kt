package es.sonda

import android.media.AudioDeviceInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import es.sonda.audio.AudioDevice
import es.sonda.measurement.MeasurementConfig
import es.sonda.measurement.MeasurementResult
import es.sonda.measurement.MeasurementSignal
import es.sonda.measurement.audio.MeasurementAudioState
import es.sonda.measurement.data.SourceMeasurement
import es.sonda.measurement.data.SourceSlot
import es.sonda.ui.MeasurementScreen
import es.sonda.ui.MeasurementUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeasurementScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var available by mutableStateOf<List<AudioDevice>>(emptyList())
    private var presented by mutableStateOf(MeasurementUiState())
    private var requests = mutableListOf<Triple<MeasurementConfig, Int?, Int>>()

    private fun screen(state: MeasurementUiState = MeasurementUiState()) {
        presented = state
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        MeasurementScreen(presented, MeasurementAudioState(), available,
                            onMeasure = { config, input, output -> requests += Triple(config, input, output) },
                            onStop = {}, onDemo = {}, onSave = {}, onClear = {}, onExport = {},
                            onReference = { presented = presented.copy(reference = it) }, onSpeed = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun bluetoothAndBuiltInSpeakerRequireAnExplicitSelectionAndStart() {
        available = listOf(
            AudioDevice(1, "Teléfono", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, false, ""),
            AudioDevice(2, "Test BT", AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, false, ""),
        )
        screen()
        compose.onNodeWithText("Emitir y medir").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, requests.size) }
        compose.onNodeWithText("Seleccionar salida para emitir").performScrollTo().performClick()
        compose.onNodeWithText("Bluetooth · Test BT · exploración").performClick()
        compose.onNodeWithText("Emitir y medir").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, requests.size) }
        compose.onNodeWithText("Emitir y medir").performClick()
        compose.runOnIdle {
            assertEquals(1, requests.size)
            assertEquals(2, requests.single().third)
            assertNull(requests.single().second)
        }
    }

    @Test fun usbDefaultsToSilentSelectionAndPulseUsesBandLimitedPreset() {
        available = listOf(AudioDevice(3, "DAC", AudioDeviceInfo.TYPE_USB_DEVICE, false, ""))
        screen()
        compose.onNodeWithText("Emitir y medir").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, requests.size) }
        compose.onNodeWithText("Pulso").performScrollTo().performClick()
        compose.onNodeWithText("Pulso pasa-banda · 80 ms · 5 repeticiones").assertExists()
        compose.onNodeWithText("Emitir y medir").performScrollTo().performClick()
        compose.runOnIdle {
            val request = requests.single()
            assertEquals(MeasurementSignal.PULSE, request.first.kind)
            assertEquals(0.08, request.first.durationSeconds, 0.0)
            assertEquals(40.0, request.first.lowHz, 0.0)
            assertEquals(16000.0, request.first.highHz, 0.0)
            assertEquals(-30.0, request.first.levelDbFs, 0.0)
            assertEquals(3, request.third)
        }
    }

    @Test fun disconnectedUsbMicrophoneNeverSilentlyBecomesInternalMicrophone() {
        val dac = AudioDevice(3, "DAC", AudioDeviceInfo.TYPE_USB_DEVICE, false, "")
        available = listOf(dac, AudioDevice(4, "Mic USB", AudioDeviceInfo.TYPE_USB_DEVICE, true, ""))
        screen()
        compose.onNodeWithText("Entrada: micrófono interno").performScrollTo().performClick()
        compose.onNodeWithText("USB · Mic USB").performClick()
        compose.runOnIdle { available = listOf(dac) }
        compose.onNodeWithText("Emitir y medir").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Entrada USB desconectada · selecciona una entrada").assertExists()
        compose.runOnIdle { assertEquals(0, requests.size) }
        compose.onNodeWithText("Entrada USB desconectada · selecciona una entrada").performScrollTo().performClick()
        compose.onNodeWithText("Micrófono interno").performClick()
        compose.onNodeWithText("Emitir y medir").performScrollTo().assertIsEnabled()
    }

    @Test fun invalidBandCannotStartAndInvalidResultCannotBeSaved() {
        available = listOf(AudioDevice(3, "DAC", AudioDeviceInfo.TYPE_USB_DEVICE, false, ""))
        val invalid = MeasurementResult(
            delaySamples = 5904.0, delayMs = 123.0,
            frequenciesHz = doubleArrayOf(100.0, 1000.0), magnitudeDb = doubleArrayOf(-20.0, -21.0),
            impulse = floatArrayOf(0f), impulseStartCaptureFrame = 0,
            peakToNoiseDb = 0.0, normalizedCorrelation = 0.01, valid = false,
            warnings = listOf("Referencia temporal no válida"), candidateDelaysMs = emptyList(),
        )
        // Even if a host mistakenly enables saving, the screen still requires a valid result.
        screen(MeasurementUiState(result = invalid, canSave = true))
        compose.onNodeWithText("Guardar resultado en Fuente 1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Retardo observado:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("MEDIDA NO VÁLIDA · revisa los motivos").assertExists()
        compose.onNodeWithText("Desde · Hz").performScrollTo().performTextReplacement("17000")
        compose.onNodeWithText("Emitir y medir").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, requests.size) }
    }

    @Test fun relativeArrivalsKeepSignsWhenChangingReferenceAndIgnoreCommonSystemLatency() {
        screen(MeasurementUiState(result = validResult(106.0), resultComparisonKey = "same-chain",
            slots = sourceSlots(), repeatDelaysMs = listOf(106.0)))
        val four = number(4.0)
        val zero = number(0.0)
        compose.onNodeWithText("Δt respecto a Fuente 1: +$four ms").performScrollTo().assertExists()
        compose.onNodeWithText("Δ equivalente: +${number(1.372)} m").assertExists()
        compose.onNodeWithText("Diferencias").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Fuente 1: diferencia $zero ms").assertExists()
        compose.onNodeWithContentDescription("Fuente 2: diferencia +$four ms").assertExists()
        compose.onNodeWithText("Tiempo observado del sistema:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Repeticiones del sistema:", substring = true).assertDoesNotExist()

        // A different common offset must not alter any relative arrival.
        compose.runOnIdle {
            presented = presented.copy(result = validResult(206.0), slots = sourceSlots(offset = 100.0),
                repeatDelaysMs = listOf(206.0))
        }
        compose.onNodeWithText("Δt respecto a Fuente 1: +$four ms").assertExists()
        compose.onNodeWithContentDescription("Fuente 2: diferencia +$four ms").assertExists()

        compose.onNodeWithText("Referencia: Fuente 1").performScrollTo().performClick()
        compose.onNodeWithText("Fuente 2 · con registro").performClick()
        compose.onNodeWithContentDescription("Fuente 1: diferencia -$four ms").assertExists()
        compose.onNodeWithContentDescription("Fuente 1: diferencia -${number(1.372)} m").assertExists()
        compose.onNodeWithContentDescription("Fuente 2: diferencia $zero ms").assertExists()
        compose.onNodeWithText("Δt respecto a Fuente 2: $zero ms").assertExists()
        compose.onNodeWithText("Ver diagnóstico de tiempos del sistema").performScrollTo().performClick()
        compose.onNodeWithText("Tiempo observado del sistema: ${number(206.0)} ms").assertExists()
        compose.onNodeWithText("Repeticiones del sistema: ${number(206.0)} ms").assertExists()
        compose.runOnIdle { assertEquals(0, requests.size) }
    }

    @Test fun latestArrivalNeedsCompatibleReferenceAndNeverTreatsSystemTimeAsDifference() {
        screen(MeasurementUiState(result = validResult(106.0), resultComparisonKey = "different-chain", slots = sourceSlots()))
        compose.onNodeWithText("La referencia y el último resultado tienen rutas o configuraciones de medida diferentes").performScrollTo().assertExists()
        compose.onNodeWithText("Δt respecto a", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Tiempo observado del sistema:", substring = true).assertDoesNotExist()
        compose.runOnIdle { presented = presented.copy(slots = (1..10).map { SourceSlot(it) }) }
        compose.onNodeWithText("Guarda la fuente de referencia para comparar llegadas").assertExists()
        compose.onNodeWithText("Δt respecto a", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, requests.size) }
    }

    private fun number(value: Double) = String.format(Locale.getDefault(), "%.2f", value)

    private fun sourceSlots(offset: Double = 0.0) = (1..10).map { number ->
        if (number > 2) SourceSlot(number) else SourceSlot(number, SourceMeasurement(
            id = "00000000-0000-4000-8000-00000000000$number", createdAt = "2026-10-18T12:00:00Z",
            signal = "SWEEP", sampleRate = 48000, observedDelayMs = offset + if (number == 1) 102.0 else 106.0,
            comparisonKey = "same-chain", inputName = "Micrófono", outputName = "DAC", bluetooth = false,
            frequenciesHz = doubleArrayOf(100.0, 1000.0), magnitudeDb = doubleArrayOf(-20.0, -21.0), warnings = emptyList()))
    }

    private fun validResult(delayMs: Double) = MeasurementResult(
        delaySamples = delayMs * 48.0, delayMs = delayMs,
        frequenciesHz = doubleArrayOf(100.0, 1000.0), magnitudeDb = doubleArrayOf(-20.0, -21.0),
        impulse = floatArrayOf(0f), impulseStartCaptureFrame = 0,
        peakToNoiseDb = 40.0, normalizedCorrelation = 0.95, valid = true,
        warnings = emptyList(), candidateDelaysMs = listOf(delayMs))
}
