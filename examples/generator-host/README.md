# App anfitriona del módulo SONDA

Este es un proyecto Gradle independiente. Consume `es.sonda:generator-kit:0.2.0` y su dependencia DSP a partir de un repositorio Maven de archivos; no incluye los proyectos fuente del repositorio principal.

En el kit descargado, abrir el directorio `example` en Android Studio, con JDK 21 y Android SDK 36, o ejecutar:

```bash
./gradlew :app:assembleDebug
```

Por defecto busca los binarios en `../maven-repository`. Para compilar este mismo ejemplo desde el repositorio fuente, publicar antes los dos módulos y pasar la ruta absoluta:

```bash
./gradlew :app:assembleDebug -PsondaRepository=/ruta/Codex-gptpublico/build/generator-kit-repository
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk` y usa `es.sonda.example`, distinto de SONDA y SONDA Generador. El anfitrión crea el motor en un ViewModel, lo cierra en `onCleared` y lo muestra con `GeneratorScreen`. La salida siempre necesita selección y activación explícitas.

La primera compilación necesita las herramientas Android y las dependencias de Google Maven/Maven Central. El APK funciona sin Internet ni permiso de micrófono.
