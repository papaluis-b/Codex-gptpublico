# Compilación y uso · SONDA 0.1

## Android Studio o SDK local

Instalar un JDK 21 (17 también es compatible con las herramientas de compilación), Android SDK Platform 36 y Build-Tools 36.0.0. Abrir el directorio raíz como proyecto Gradle. Definir `ANDROID_HOME` o configurar `sdk.dir` en un archivo local `local.properties` sin versionarlo.

```bash
./gradlew :dsp:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

El APK debug queda en `app/build/outputs/apk/debug/app-debug.apk`. Es una firma de desarrollo, sin publicación en tienda. Para instalar por USB con la depuración autorizada:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

También puedes copiar el APK al teléfono y abrirlo, autorizando esa fuente de instalación en los ajustes Android cuando lo solicite.

## Entorno cloud Linux x86_64

Cada tarea cloud ya está aislada: usar este checkout, sin crear worktrees. Las herramientas están en `.tooling/` y no se versionan. Las descargas conservan TLS y se contrastan con los digests oficiales. No copiar credenciales ni desactivar validación TLS.

```bash
bash scripts/setup-cloud.sh
```

Se fijan Command-line Tools 19.0 para mantener la instalación compatible con AGP 8.13 y su gestión de licencias SDK. El script no instala fuera del checkout ni requiere sudo. `cloud-env.sh` usa el proxy heredado y el almacén CA gestionado de la máquina cuando están disponibles; no contiene secretos.

Destinos de instalación: `github.com`, sus hosts de assets de releases, `services.gradle.org`, `downloads.gradle.org`, `dl.google.com`, `repo.maven.apache.org` y `plugins.gradle.org`. La aplicación instalada funciona sin Internet. Las dependencias descargadas y el SDK sobreviven en la imagen de entorno; volver a cargar las variables con `source scripts/cloud-env.sh` en cada shell nueva.

## Uso inicial

1. Conectar el DAC USB antes de iniciar, si se va a emitir. Elegir USB; sin DAC, usar Solo micrófono. El altavoz interno se activa únicamente eligiendo el modo de prueba.
2. Pulsar Iniciar audio real y permitir el micrófono. Revisar las rutas activas en Diagnóstico. La apertura empieza en silencio.
3. En Analizar, observar el espectro, RMS, clipping y el espectrograma. Tocar el gráfico cambia el cursor; Usar frecuencia en el generador es una acción separada.
4. En Generar, fijar frecuencia y nivel y pulsar Emitir seno. Empezar con la ganancia de la cadena controlada. SILENCIO permanece accesible en todas las áreas.
5. Grabar WAV inicia una captura explícita; Finalizar la guarda. El límite por captura es 120 s. En Capturas, Exportar ZIP permite guardar o compartir WAV, JSON y último espectro CSV.
6. Explorar simulación permite recorrer el flujo sin permisos ni hardware. El estado y los archivos se etiquetan como sintéticos; no emite sonido.

El analizador funciona sin salida. Para cambiar de salida hay que detener todo y abrir de nuevo. Al ir al fondo, bloquear pantalla, perder foco o cambiar una ruta, el audio se detiene y no se reanuda solo. La rotación también detiene la sesión en este primer hito.

## Prueba manual corta en Samsung + DAC

- Registrar modelo, Android, One UI, DAC, cable/hub y volumen. La app no inventa One UI ni una calibración.
- Confirmar por Diagnóstico y mediante una prueba controlada que captura el micrófono del teléfono y emite por el DAC; la ruta de plataforma necesita contraste físico.
- Probar captura/reproducción simultáneas durante diez minutos. Revisar clipping, pérdidas de cola, discontinuidades, underruns y comportamiento térmico.
- Desconectar el DAC mientras emite; pulsar Home; bloquear pantalla; provocar una interrupción de audio; volver: el tono debe permanecer apagado.
- Grabar diez segundos, exportar, reabrir WAV/JSON en un ordenador y comprobar amplitudes, sample rate, flags de calidad y checksum.
- Repetir con texto grande y orientación horizontal. Probar rechazo y revocación del permiso.

Estos pasos físicos permanecen pendientes hasta ejecutarlos en el dispositivo real. Rutas declaradas y muestras sintéticas no los sustituyen.
