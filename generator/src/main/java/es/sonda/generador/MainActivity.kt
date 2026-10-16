package es.sonda.generador

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import es.sonda.generador.ui.GeneratorApp

class MainActivity : ComponentActivity() {
    private val model by viewModels<GeneratorViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.engine.state.collectAsState()
            LaunchedEffect(state.running, state.preparing) {
                if (state.running || state.preparing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            GeneratorApp(model)
        }
    }

    override fun onPause() {
        model.engine.stop("Aplicación fuera de primer plano: salida detenida")
        super.onPause()
    }
}
