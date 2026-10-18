package es.sonda.generador.ui

import androidx.compose.runtime.Composable
import es.sonda.generador.GeneratorViewModel
import es.sonda.generador.kit.ui.GeneratorScreen

/** Standalone application wrapper around the reusable screen. */
@Composable
fun GeneratorApp(model: GeneratorViewModel) {
    GeneratorScreen(model.engine)
}
