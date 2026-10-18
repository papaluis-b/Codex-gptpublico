package es.sonda.example

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import es.sonda.generador.audio.GeneratorOutputEngine
import es.sonda.generador.kit.ui.GeneratorScreen

/** This separate project consumes the published AAR and DSP JAR, never repository source modules. */
class HostActivity : ComponentActivity() {
    private val model by viewModels<GeneratorHostModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.engine.state.collectAsState()
            LaunchedEffect(state.running, state.preparing) {
                if (state.running || state.preparing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            GeneratorScreen(model.engine)
        }
    }

    override fun onPause() {
        model.engine.stop("Aplicación anfitriona fuera de primer plano")
        super.onPause()
    }
}

class GeneratorHostModel(application: Application) : AndroidViewModel(application) {
    val engine = GeneratorOutputEngine(application)
    override fun onCleared() { engine.close() }
}
