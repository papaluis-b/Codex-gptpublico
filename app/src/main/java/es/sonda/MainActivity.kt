package es.sonda

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import es.sonda.ui.SondaApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model by viewModels<SondaViewModel>()
    private var pendingOutput: Int? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lifecycleScope.launch {
            lifecycle.withResumed { model.engine.start(pendingOutput, false) }
        } else model.engine.stop("Permiso de micrófono denegado. Puedes usar la simulación o concederlo en Ajustes.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.engine.state.collectAsState()
            LaunchedEffect(state.running) {
                if (state.running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            SondaApp(model, onStart = { output, simulation ->
                if (simulation) model.engine.start(null, true)
                else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    model.engine.start(output, false)
                } else {
                    pendingOutput = output
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }, onShare = { capture ->
                model.export(capture.id) { file ->
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Exportar captura SONDA"))
                }
            })
        }
    }

    override fun onPause() {
        // Covers home, screen lock, focus transitions and rotation: no tone auto-resumes.
        model.engine.stop("Aplicación fuera de primer plano: audio detenido")
        super.onPause()
    }
}
