# Guardar e integrar SONDA Generator Kit 0.2.0

El kit conserva el generador aprobado de SONDA Generador 0.2.0 y lo separa de la aplicación que lo aloja. Incluye el motor Android, la pantalla Compose y el DSP compartido. Permite integrarlo en otra app Android sin copiar la aplicación completa ni modificar el sonido aprobado.

Para instalarlo en un teléfono se usa el APK de SONDA Generador. El kit es el archivo para guardar el módulo, compilarlo e integrarlo desde un ordenador con las herramientas Android.

## Qué guardar

Conservar `artifacts/SONDA-Generador-0.2.0-kit.zip` junto a su SHA-256 de entrega. Al extraerlo aparece:

```text
SONDA-Generador-Kit-0.2.0/
├── maven-repository/   # AAR, JAR, fuentes y metadatos de publicación
├── example/            # Proyecto Android independiente que consume Maven
├── jvm-example/        # Ejemplo JVM puro que consume el JAR DSP desde Maven
├── sources/            # Fuentes e instrucciones del repositorio
├── docs/               # Documentación de integración y funcionamiento
├── baseline/           # Referencias de la versión aprobada
├── libraries/          # Copias de las bibliotecas de entrega
├── manifest.json       # Identificación de la entrega
├── SHA256SUMS.txt      # Integridad de los archivos del paquete
└── LEEME.md
```

Desde la carpeta extraída, `sha256sum -c SHA256SUMS.txt` comprueba los archivos relacionados. Las copias de `libraries/` son útiles para conservar los binarios; para integrar se debe usar `maven-repository/` o las fuentes. Un AAR aislado no contiene el JAR DSP ni sus dependencias Compose, AndroidX y coroutines.

La primera compilación necesita acceso a las dependencias de herramientas y bibliotecas, además del SDK Android y sus licencias. El ZIP no incluye todo el SDK/JDK ni las cachés Maven. La aplicación generada puede funcionar sin Internet; el módulo no solicita permiso de red ni micrófono.

## Referencias de versión

Repositorio: <https://github.com/papaluis-b/Codex-gptpublico>.

| Referencia | Contenido |
| --- | --- |
| Commit `01eb7fab88218c9361dd3a9b4e305ffe086d2ce5` | Aplicación SONDA Generador 0.2.0 aprobada, antes de extraer el kit. |
| Etiqueta `generador-app-v0.2.0` | Referencia de conservación de ese commit aprobado. |
| Rama `sonda-generator-kit-v0.2.0` | Trabajo de extracción, integración y empaquetado del kit. |
| Etiqueta `generator-kit-v0.2.0` | Referencia de entrega del kit y sus ejemplos. |
| `manifest.json` del ZIP | Identificación y trazabilidad del paquete concreto descargado. |

Las etiquetas se conservan sin moverlas. Para comprobar los commits a los que apuntan en un clon actualizado:

```bash
git fetch origin --tags
git rev-parse 'generador-app-v0.2.0^{}'
git rev-parse 'generator-kit-v0.2.0^{}'
```

En una etiqueta anotada, `git rev-parse etiqueta` puede mostrar el SHA del objeto de etiqueta. El sufijo `^{}` lo resuelve hasta el objeto apuntado, en estas referencias el commit. Para comparar con el SHA aprobado se usa esa forma resuelta.

Para conservar las fuentes de una entrega publicada:

```bash
git clone https://github.com/papaluis-b/Codex-gptpublico.git sonda-generador
cd sonda-generador
git switch --detach generator-kit-v0.2.0
```

Para recuperar la aplicación aprobada anterior a la extracción, usar `git switch --detach generador-app-v0.2.0`. También se puede recuperar directamente por el SHA completo indicado en la tabla. Guardar una copia del ZIP permite recuperar los binarios y el ejemplo incluso sin un clon de Git.

## Entorno de referencia

| Elemento | Versión de la entrega |
| --- | --- |
| Android mínimo | API 28, Android 9 |
| SDK de compilación / Build-Tools | 36 / 36.0.0 |
| JDK usado para compilar | 21 |
| Bytecode Java/Kotlin | 17 |
| Gradle / Android Gradle Plugin | 8.13 / 8.13.2 |
| Kotlin y plugin Compose | 2.2.21 |
| Compose BOM | 2025.10.01 |
| Activity / Lifecycle | 1.11.0 / 2.9.4 |
| API coroutines-core del kit | 1.8.1 |

La app anfitriona necesita habilitar Compose y usar versiones compatibles. Mantener esta configuración es la vía de reproducción; actualizar herramientas o dependencias exige repetir la validación. La lista y los avisos de terceros están en `DEPENDENCIES.md`.

## Vía 1: dentro de este repositorio

`:generator` es la aplicación anfitriona de referencia y depende de `:generator-kit`; `:generator-kit` expone `:dsp`. La aplicación general sigue en `:app`. El motor público conserva el paquete `es.sonda.generador.audio` y el DSP `es.sonda.dsp`.

La dependencia del módulo Android anfitrión es:

```kotlin
dependencies {
    implementation(project(":generator-kit"))
}
```

No hace falta depender de `:generator` desde otra app: ese módulo tiene su propio identificador, Activity y recursos de instalación. El kit contiene las piezas reutilizables.

## Vía 2: fuentes dentro de otro repositorio

Copiar las carpetas `dsp/` y `generator-kit/`, sus instrucciones y los avisos de dependencias/licencia. Añadir en `settings.gradle.kts` del proyecto receptor:

```kotlin
include(":dsp", ":generator-kit")
```

Declarar en el bloque `plugins` raíz las versiones fijadas de `com.android.library` 8.13.2, Kotlin Android/JVM 2.2.21 y `org.jetbrains.kotlin.plugin.compose` 2.2.21, con `apply false`. Mantener los repositorios Google Maven y Maven Central. Los proyectos receptores con una app Android también declaran `com.android.application` 8.13.2. Usar el wrapper Gradle 8.13 y la configuración de SDK/JDK de la tabla.

El módulo anfitrión usa `implementation(project(":generator-kit"))`. Así compila las fuentes y puede evolucionarlas junto con su aplicación. No modificar el DSP para una integración de interfaz: primero conservar una referencia limpia de la versión aprobada.

## Vía 3: repositorio Maven incluido en el ZIP

Las coordenadas de esta entrega son:

```text
es.sonda:generator-kit:0.2.0   # Biblioteca Android AAR
es.sonda:dsp:0.2.0             # Biblioteca JVM JAR transitiva
```

El repositorio contiene POM, metadatos Gradle `.module` y archivos de fuentes. El kit publica como API el DSP, las piezas Compose usadas por su API pública y StateFlow; los demás requisitos de ejecución se resuelven como dependencias transitivas. Las bibliotecas de pruebas no forman parte del release.

En el `settings.gradle.kts` del proyecto receptor, añadir el directorio extraído como repositorio Maven junto a Google y Maven Central:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("/ruta/SONDA-Generador-Kit-0.2.0/maven-repository") }
        google()
        mavenCentral()
    }
}
```

En el módulo Android que muestra la pantalla:

```kotlin
dependencies {
    implementation("es.sonda:generator-kit:0.2.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
}
```

Activity y ViewModel son responsabilidad del anfitrión. El ejemplo incluido ya configura los plugins Compose, las herramientas y estas dependencias.

### Compilar el ejemplo independiente

Desde el ZIP extraído:

```bash
cd SONDA-Generador-Kit-0.2.0/example
./gradlew :app:assembleDebug
```

El ejemplo busca por defecto `../maven-repository`. Para usar otra ubicación:

```bash
./gradlew :app:assembleDebug -PsondaRepository=/ruta/absoluta/maven-repository
```

En Windows usar `gradlew.bat`. El APK resultante queda en `app/build/outputs/apk/debug/`. El ejemplo consume las coordenadas publicadas: no usa `project()` ni `includeBuild` para acceder a las fuentes. También está en `examples/generator-host` del repositorio; desde allí se indica como propiedad la ruta absoluta a `build/generator-kit-repository` después de publicar localmente.

### Ejecutar el ejemplo JVM, sin Android

El segundo ejemplo está en `examples/dsp-jvm` del repositorio y en `jvm-example/` del ZIP. Usa los plugins Java/Application y únicamente `implementation("es.sonda:dsp:0.2.0")`; resuelve ese JAR y sus dependencias mediante Maven, sin enlazar los proyectos fuente.

```bash
cd SONDA-Generador-Kit-0.2.0/jvm-example
./gradlew run
```

También busca `../maven-repository` por defecto. Para otra ubicación se usa `./gradlew run -PsondaRepository=/ruta/absoluta/maven-repository`. Genera buffers y comprueba niveles RMS de seno y ruido rosa; no abre una salida de audio ni reproduce sonido. Este ejemplo no necesita SDK Android ni Compose, aunque sí JDK y la descarga inicial de Gradle y dependencias JVM.

## Mostrar el generador en una app

La API de pantalla es `GeneratorScreen(engine, modifier = Modifier)`, en `es.sonda.generador.kit.ui`. La versión se consulta mediante `es.sonda.generador.kit.GeneratorKit.VERSION`. El anfitrión crea y conserva el motor; la pantalla recibe esa instancia:

```kotlin
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import es.sonda.generador.audio.GeneratorOutputEngine
import es.sonda.generador.kit.ui.GeneratorScreen

class GeneratorHostViewModel(application: Application) : AndroidViewModel(application) {
    val engine = GeneratorOutputEngine(application)

    override fun onCleared() {
        engine.close()
    }
}

class GeneratorHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: GeneratorHostViewModel = viewModel()
            GeneratorScreen(model.engine, Modifier.fillMaxSize())
        }
    }
}
```

Declarar la Activity en el manifiesto del anfitrión según su navegación. Este código no prepara ninguna ruta ni emite automáticamente. El usuario selecciona USB o altavoz explícito, pulsa Preparar salida y después Emitir.

### Ciclo de vida y propiedad

`GeneratorScreen` observa el `LifecycleOwner` del anfitrión y llama a `stop()` en pausa, parada, destrucción o al salir de la composición. Si entra con un motor activo mientras el propietario no está en estado RESUMED, también lo detiene. Volver a primer plano o mostrar de nuevo la pantalla no rearma la señal.

La pantalla no llama a `close()`: el ViewModel sigue siendo dueño del motor y lo cierra en `onCleared()`. Puede volver a abrirse manualmente después de salir de la pantalla. No crear un motor nuevo en cada recomposición ni compartirlo entre varias pantallas visibles con ciclos de vida independientes: al cerrar una se silenciaría la sesión compartida.

Si se usa solo el motor con una interfaz propia, el anfitrión debe llamar a `stop()` al pausar, ocultar o abandonar su pantalla y a `close()` al terminar su propietario. El motor gestiona foco y rutas, pero no conoce por sí solo si una interfaz externa sigue en primer plano. No debe iniciarse desde un servicio de reproducción en segundo plano con este contrato.

## API principal del motor

| API | Uso |
| --- | --- |
| `state` / `devices` | StateFlow con ajustes, diagnóstico, estado de ruta y salidas admitidas. |
| `start(outputId)` | Prepara la salida elegida en silencio. Antes de emitir exige una escritura de ceros y verificar esa ruta exacta. |
| `emit(true)` | Activa la señal únicamente con sesión y ruta confirmadas. Se llama por acción explícita del usuario. |
| `emit(false)` | Silencia con rampa y conserva la salida abierta. |
| `stop(reason)` | Desarma, silencia y libera la sesión; conserva los ajustes. |
| `setTone(frequency, level)` | Ajuste discreto de frecuencia y nivel digital. |
| `setContinuousFrequency(frequency)` | Ajuste continuo del deslizador; conserva nivel, tipo y fracción, sin activar emisión. |
| `setSignalKind(SINE / PINK_NOISE)` | Cambia el tipo y deja la emisión en silencio. |
| `setOctaveDivision(3 / 24 / 32 / 48)` | Selecciona el paso y, en rosa, la anchura de la campana. |
| `stepFrequency(-1 / 1)` | Divide o multiplica la frecuencia por `2^(1/n)`, respetando los límites. |
| `close()` | Cierre final del motor; libera observadores y recursos. Para reutilizar después se crea otra instancia. |

Los valores numéricos deben ser finitos. El DSP restringe frecuencia y nivel al formato de salida; una interfaz propia debe validar el texto antes de llamar. No se elige una salida por defecto cuando falta la solicitada. Pérdida de foco, desconexión, cambio de ruta, error o clipping detienen la sesión sin rearme.

## Comportamiento de señal que debe conservarse

El motor solicita AudioTrack estéreo float32 a 48 kHz y usa el sample rate del stream abierto para generar y acotar la banda. L y R contienen la misma señal. No captura audio ni usa el micrófono.

El seno usa dBFS pico, con límite de −12 dBFS. El ruido rosa usa dBFS RMS con referencia de amplitud 1 y límite de −24 dBFS. El valor inicial es −40 dBFS. Son niveles digitales: el volumen multimedia Android, la ganancia del DAC y la cadena física son independientes; no representan SPL.

Para 1/n de octava los bordes son `fc / 2^(1/(2n))` y `fc * 2^(1/(2n))`. Son bordes a −3 dB de un Butterworth de orden total 4, con caída gradual; no es una banda rectangular ni una declaración de conformidad IEC. A 48 kHz se permiten centros de 20 Hz–20 kHz; el máximo se adapta al formato y a la fracción. Las razones de paso son binarias exactas, sin redondeo a una tabla preferida.

El seno conserva fase. El ajuste continuo rosa enlaza dos bancos preasignados mediante fundidos lineales de 20 ms y toma el último objetivo tras completar cada fundido. Los cambios discretos conservan sus rampas. Las bandas estrechas necesitan asentarse y el RMS varía durante el arrastre; no se promete nivel estacionario durante un barrido. La preparación de filtros queda fuera del render, que reutiliza sus buffers.

La definición completa del algoritmo, semilla y normalización está en `12_GENERADOR_FRACCIONES_Y_ROSA.md`; el manejo y la validación física de la app, en `13_GENERADOR_INDEPENDIENTE.md`.

## Construir y guardar el kit completo

El helper prepara la entrega desde el repositorio configurado:

```bash
bash scripts/build-generator-kit.sh
```

Ejecuta las suites y compilaciones del repositorio, publica los dos módulos en el directorio Maven local y llama al empaquetador. El ZIP de salida es `build/generator-kit-dist/SONDA-Generador-0.2.0-kit.zip`. Conservarlo junto a su hash; la copia distribuida se guarda en `artifacts/`.

Para trabajar desde las fuentes del ZIP, entrar en `sources/` e inicializar su repositorio con `git init` y `git add .` antes de ejecutar el helper. El empaquetador toma de Git la lista de fuentes y usa el directorio hermano `baseline/` para recuperar los originales aprobados. Mantener ambos directorios juntos. La compilación necesita el JDK y el SDK de la tabla; en el entorno cloud se preparan con `bash scripts/setup-cloud.sh`.

Para repetir únicamente el empaquetado después de publicar y validar las bibliotecas:

```bash
python3 scripts/package-generator-kit.py
```

Este paso comprueba las coordenadas, los metadatos, el contenido de AAR/JAR y la conservación de la base aprobada antes de crear el ZIP. No compila ni reemplaza la publicación Maven. La comprobación de los ejemplos externos se realiza además con los comandos anteriores.

### Publicar de nuevo en el directorio local

Desde la raíz del repositorio y con el entorno configurado:

```bash
source scripts/cloud-env.sh
./gradlew :dsp:publishReleasePublicationToKitRepository \
  :generator-kit:publishReleasePublicationToKitRepository \
  --no-daemon --console=plain
```

Los dos módulos publican únicamente en `build/generator-kit-repository`. Este flujo no usa `mavenLocal()` ni publica en Maven Central. Para empaquetar se conserva la estructura Maven completa, incluidas las coordenadas DSP y los metadatos; no solo el AAR.

## Evolucionar y devolver una versión para revisión

Crear una rama desde la referencia guardada:

```bash
git switch -c mi-generador-v0.3.0 generator-kit-v0.2.0
```

Hacer primero el cambio mínimo en esa rama. Si cambia la API, la señal o las versiones de dependencias, documentarlo y actualizar la versión del kit en Gradle, `GeneratorKit.VERSION`, las coordenadas y el paquete de entrega. El empaquetador también declara `VERSION` y `DSP_VERSION`: deben coincidir respectivamente con las versiones publicadas del kit y del DSP. Cambiar la versión DSP si se modifica ese módulo; conservar la dependencia existente si solo evoluciona la interfaz. Si se entrega otro APK, actualizar también su versionCode y versionName.

`APPROVED_VERSION`, el commit y los hashes de aprobación del empaquetador identifican la aplicación base 0.2.0 y permanecen separados de `VERSION`/`DSP_VERSION`. Subir la versión del SDK no cambia esa base conservada. En esta extracción 0.2.0, los archivos del motor y el DSP deben coincidir exactamente con los aprobados. En versiones futuras pueden evolucionar, registrando sus hashes y las diferencias respecto a la base, sin afirmar que siguen siendo idénticos. Una nueva aprobación requiere actualizar deliberadamente la referencia y sus comprobaciones, documentar el nuevo commit y conservar los artefactos aprobados anteriores.

No reemplazar un artefacto ya aprobado bajo las mismas coordenadas ni mover etiquetas existentes. Una nueva entrega obtiene una versión y una etiqueta nuevas; se conservan el ZIP anterior y sus hashes para poder volver a él.

Al devolver el trabajo, incluir rama y commit completo, problema resuelto, cambios de comportamiento, herramientas usadas, comandos/resultados y límites pendientes. Entregar los archivos fuente o una revisión Git, el repositorio Maven regenerado y el ejemplo compilado contra él. Distinguir las comprobaciones sintéticas de cualquier prueba hecha en teléfono/DAC.

### Comandos de validación

```bash
source scripts/cloud-env.sh
./gradlew :dsp:test :generator-kit:testDebugUnitTest :generator-kit:lintDebug \
  :generator-kit:assembleRelease :generator:testDebugUnitTest \
  :generator:lintDebug :generator:assembleDebug \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --no-daemon --console=plain
```

Después publicar en el repositorio local y compilar `examples/generator-host` con `-PsondaRepository` apuntando a ese directorio; ejecutar también `examples/dsp-jvm` con `run` y la misma propiedad. Comprobar que el AAR/JAR, POM y `.module` contienen las coordenadas correctas y no publican dependencias de pruebas. Revisar el manifiesto de la app resultante y sus permisos. Verificar los hashes del paquete final.

### Validación realizada de la entrega

La validación del repositorio de esta extracción ha completado **55 pruebas**: 27 DSP, 14 del kit, seis de la app anfitriona del generador y ocho de SONDA general. Lint no informó errores; el kit conserva dos advertencias conocidas de versiones fijadas (coroutines y Robolectric), el anfitrión una y SONDA general dos.

También se compilaron los consumidores externos extraídos del ZIP, separados del repositorio fuente:

- **Android:** `:app:assembleDebug` completó una compilación de 38 tareas. `dependencyInsight` confirmó que `es.sonda:generator-kit:0.2.0` resuelve su dependencia `es.sonda:dsp:0.2.0` mediante el repositorio Maven empaquetado.
- **JVM:** `run` completó la generación y comprobación de buffers desde el JAR publicado, con salida `DSP_JVM_OK sine=-43.0103 dBFS RMS pink=-40.0418 dBFS RMS samples=48000`. El seno configurado a −40 dBFS pico tiene RMS aproximadamente −43,01 dBFS; el ruido se configura a −40 dBFS RMS. El ejemplo no abre ningún dispositivo de audio.

Estas comprobaciones validan la integración de los artefactos publicados y los buffers generados. No se ha probado esta entrega en un teléfono ni un DAC físico.

En teléfono/DAC, comprobar ruta exacta, arranque en silencio, emisión manual, controles y niveles, desconexión, foco, pausa y navegación. El host debe detenerse al salir y exigir intervención manual para reanudar. Una compilación o una prueba Robolectric no valida por sí sola el hardware físico.

## Alcance fuera de Android y licencia

`es.sonda:dsp:0.2.0` es un JAR JVM independiente de Android. Puede generar buffers y ejecutar el DSP en un anfitrión JVM compatible con bytecode 17, pero no aporta salida de audio, permisos, rutas Android ni pantalla Compose. Android AAR no se integra directamente en iOS o una web; esos destinos necesitan una adaptación de plataforma y su propia validación. Esta entrega no declara una biblioteca universal.

La licencia general del código propio no está definida: consultar `LICENSE_STATUS.md` antes de redistribuir o asignar permisos de uso a terceros. No se presupone MIT ni Apache por usar herramientas con esas licencias. Los avisos y referencias de terceros están en `DEPENDENCIES.md`; dentro del ZIP se conservan con las fuentes, en `sources/`.
