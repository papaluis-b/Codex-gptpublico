# SONDA Generator Kit 0.2.0

Biblioteca Android para integrar el generador aprobado de SONDA Generador 0.2.0 en otra app. Incluye seno, ruido rosa bandado, pasos de 1/3, 1/24, 1/32 y 1/48 de octava, ajuste continuo y pantalla Compose. Conserva el motor y el DSP; el anfitrión aporta su Activity y navegación.

La guía completa está en [docs/14_MODULO_REUTILIZABLE.md](../docs/14_MODULO_REUTILIZABLE.md). La entrega para guardar e integrar es `artifacts/SONDA-Generador-0.2.0-kit.zip`, con repositorio Maven, ejemplo externo, fuentes, documentación y hashes.

## Integración

En este repositorio o copiando también el módulo `dsp`:

```kotlin
implementation(project(":generator-kit"))
```

Desde el repositorio Maven incluido en el ZIP:

```kotlin
implementation("es.sonda:generator-kit:0.2.0")
```

La dependencia DSP transitiva es `es.sonda:dsp:0.2.0`. Usar el repositorio completo; copiar solo el AAR deja fuera el DSP y los metadatos de dependencias. El ejemplo `example/` del ZIP usa únicamente las coordenadas publicadas y busca `../maven-repository`:

```bash
./gradlew :app:assembleDebug
# Otra ubicación del repositorio:
./gradlew :app:assembleDebug -PsondaRepository=/ruta/absoluta/maven-repository
```

La primera compilación necesita herramientas Android y dependencias descargadas; la aplicación funciona sin Internet y el módulo no solicita micrófono ni red.

El segundo ejemplo, `examples/dsp-jvm` en el repositorio y `jvm-example/` en el ZIP, consume solo `es.sonda:dsp:0.2.0` con Java/Application. Desde su carpeta ejecutar `./gradlew run`, o añadir `-PsondaRepository=/ruta/absoluta/maven-repository`. Usa por defecto `../maven-repository`, comprueba buffers/RMS de seno y rosa y no reproduce audio. No requiere Android ni Compose.

## API y ciclo de vida

- Pantalla: `es.sonda.generador.kit.ui.GeneratorScreen(engine, modifier = Modifier)`.
- Versión: `es.sonda.generador.kit.GeneratorKit.VERSION`.
- Motor: `es.sonda.generador.audio.GeneratorOutputEngine(context)`.
- DSP: paquete `es.sonda.dsp`.

El anfitrión conserva el motor en un ViewModel y llama a `close()` en `onCleared()`. La pantalla llama a `stop()` al pausar, detenerse, destruirse o salir de la composición; no cierra el motor. Si se integra solo el motor, el anfitrión debe detenerlo al pausar u ocultar su interfaz.

Crear o mostrar el módulo no emite. El usuario debe elegir USB o altavoz explícito, preparar la ruta y pulsar Emitir. `setTone`, `setContinuousFrequency`, `setSignalKind`, `setOctaveDivision` y `stepFrequency` cambian controles sin activar una salida silenciada. Volver a primer plano, recuperar foco o reconectar un DAC tampoco rearma la señal.

Seno: dBFS pico, máximo −12. Ruido: dBFS RMS, máximo −24. El inicio es −40 dBFS; el volumen Android y las ganancias físicas son independientes. Los bordes de la campana son a −3 dB, con caída gradual. Los filtros estrechos necesitan asentarse; el RMS puede variar durante el ajuste continuo.

## Compilar y publicar localmente

Referencia: minSdk28, compileSdk36, JDK21, bytecode17, Kotlin2.2.21, Compose BOM2025.10.01, Gradle8.13 y AGP8.13.2.

Desde la raíz del repositorio:

```bash
source scripts/cloud-env.sh
./gradlew :dsp:test :generator-kit:testDebugUnitTest :generator-kit:lintDebug :generator-kit:assembleRelease
./gradlew :dsp:publishReleasePublicationToKitRepository :generator-kit:publishReleasePublicationToKitRepository
```

La publicación queda solo en `build/generator-kit-repository`; no usa Maven Central ni `mavenLocal`. Los artefactos release no incluyen bibliotecas de pruebas.

El flujo completo es `bash scripts/build-generator-kit.sh`; produce `build/generator-kit-dist/SONDA-Generador-0.2.0-kit.zip`. Después de publicar, `python3 scripts/package-generator-kit.py` permite repetir solo el empaquetado y sus comprobaciones de integridad y conservación de la base aprobada. Los ejemplos Maven se validan también por separado.

La base aprobada es el commit `01eb7fab88218c9361dd3a9b4e305ffe086d2ce5`, con etiqueta de conservación `generador-app-v0.2.0`. La extracción se entrega desde `sonda-generator-kit-v0.2.0`, con etiqueta `generator-kit-v0.2.0`. Consultar `git rev-parse 'generator-kit-v0.2.0^{}'` para obtener el commit: sin `^{}`, una etiqueta anotada puede devolver el SHA del objeto de etiqueta.

Conservar estas referencias y crear versiones nuevas para cambios futuros. Actualizar las versiones Gradle, `GeneratorKit.VERSION` y `VERSION`/`DSP_VERSION` del empaquetador según los módulos que evolucionen. `APPROVED_VERSION` y los hashes identifican por separado la base conservada 0.2.0: el motor/DSP de esta extracción coinciden exactamente con ella. Futuras versiones pueden diferir registrando hashes y cambios; no se modifica la base guardada salvo nueva aprobación deliberada y documentada.

## Validación de la entrega

**55 pruebas superadas:** 27 DSP, 14 del kit, seis de la app anfitriona y ocho de SONDA general. Lint sin errores; permanecen las advertencias conocidas de versiones fijadas.

El ejemplo Android extraído del ZIP compiló como proyecto externo (38 tareas), y `dependencyInsight` confirmó las coordenadas del kit y su DSP transitivo. El ejemplo JVM externo también completó `run`: `DSP_JVM_OK sine=-43.0103 dBFS RMS pink=-40.0418 dBFS RMS samples=48000`, sin abrir una salida de audio.

Estas pruebas validan consumo Maven y buffers; no se ha probado esta entrega en un teléfono ni DAC físico.

Consultar [LICENSE_STATUS.md](../LICENSE_STATUS.md) y [DEPENDENCIES.md](../DEPENDENCIES.md): no hay una licencia general definida para el código propio. El JAR DSP puede usarse en JVM; llevar motor y pantalla a iOS o web requiere una adaptación de plataforma.
