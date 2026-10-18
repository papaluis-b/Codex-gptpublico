package es.sonda

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import es.sonda.measurement.MeasurementConfig
import es.sonda.ui.SondaApp
import es.sonda.audio.BluetoothOutputTypes

class MainActivity : ComponentActivity() {
    private val model by viewModels<SondaViewModel>()
    private data class StartRequest(val output: Int?, val input: Int?, val measurement: MeasurementConfig? = null)
    private var pending: StartRequest? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val request = pending
        pending = null
        if (request != null && result.values.all { it }) {
            // Permission dialogs do not arm a known-signal measurement on return.
            if (request.measurement != null) model.measurement.notify("Permisos concedidos. Vuelve a pulsar Emitir y medir.")
            else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) execute(request)
        } else {
            model.stopAll("Permiso denegado: no se abre otra ruta. Puedes concederlo en Ajustes o usar la simulación.")
            model.measurement.notify("Permiso de micrófono o dispositivos cercanos denegado. No se ha emitido ninguna señal.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.engine.state.collectAsState()
            val measuring by model.measurement.state.collectAsState()
            LaunchedEffect(state.running, measuring.busy) {
                if (state.running || measuring.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            SondaApp(model, onStart = { output, simulation ->
                if (simulation) model.startAnalysis(null, true)
                else startWithPermissions(StartRequest(output, model.selectedInput.value))
            }, onMeasure = { config, input, output ->
                startWithPermissions(StartRequest(output, input, config))
            }, onExportMeasurements = {
                model.measurement.export { shareFile(it, "Exportar fuentes SONDA") }
            }, onShare = { capture ->
                model.export(capture.id) { file ->
                    shareFile(file, "Exportar captura SONDA")
                }
            })
        }
    }

    override fun onPause() {
        // Covers home, screen lock, focus transitions and rotation: no tone auto-resumes.
        model.stopAll("Aplicación fuera de primer plano: audio detenido")
        super.onPause()
    }

    private fun startWithPermissions(request: StartRequest) {
        val bluetooth = model.engine.devices.value.any { it.id == request.output && !it.input && it.kind in BluetoothOutputTypes }
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31 && bluetooth) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (required.isEmpty()) execute(request)
        else { pending = request; permission.launch(required.toTypedArray()) }
    }

    private fun execute(request: StartRequest) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (request.measurement == null) model.startAnalysis(request.output, false, request.input)
        else request.output?.let { model.startMeasurement(request.measurement, request.input, it) }
    }

    private fun shareFile(file: java.io.File, title: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, title))
    }
}
