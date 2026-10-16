# Investigación, referencias y decisiones derivadas

Consulta documental: 15 de octubre de 2026. Las funciones publicadas pueden cambiar. Antes de integrar una API o dependencia, Codex debe comprobar su documentación vigente. Las comparaciones siguientes no son pruebas de rendimiento ni de precisión.

## Herramientas revisadas

### Smaart — Rational Acoustics

Smaart RT combina espectro, espectrograma y transferencia de dos canales con magnitud, fase, coherencia y Live IR. Dispone de generador de señales. La documentación reciente incluye control de frecuencia senoidal mediante deslizadores.

Lección de diseño: separar observar señal y medir respuesta; no reducir un analizador de ajuste a RTA. Para nuestra primera versión, no prometer la precisión o las funciones de transferencia de Smaart.

- https://support.rationalacoustics.com/support/solutions/articles/150000069194-smaart-rt-feature-list
- https://www.rationalacoustics.com/pages/about-smaart
- https://support.rationalacoustics.com/support/solutions/articles/150000187340-introducing-the-new-signal-generator-fly-up-menu
- https://support.rationalacoustics.com/support/solutions/articles/150000069197-smaart-suite-release-notes

### REW — Room EQ Wizard

Referencia principal para barridos, impulso, decaimiento y generador. Su generador puede seguir el cursor del gráfico y ofrece tonos, ruido, barridos y ráfagas. Los barridos se procesan para obtener impulso y respuesta en frecuencia. Sus referencias temporales ayudan a separar retardos; los relojes distintos requieren atención.

Lección de diseño: seleccionar una frecuencia en un gráfico ya existe; la mejora propuesta es reducir pasos desde móvil para explorar, excitar, repetir y comparar. No presentar esa selección como innovación exclusiva.

- https://www.roomeqwizard.com/features.html
- https://www.roomeqwizard.com/help/help_en-GB/html/siggen.html
- https://www.roomeqwizard.com/help/help_en-GB/html/primer.html
- https://www.roomeqwizard.com/help/help_en-GB/html/graph_waterfall.html
- https://www.roomeqwizard.com/help/help_en-GB/html/graph_csd.html
- https://www.roomeqwizard.com/help/help_en-GB/html/makingmeasurements.html
- https://www.roomeqwizard.com/help/help/html/analysis.html
- https://www.roomeqwizard.com/betahelp/help/html/impulseresponse.html

### TerraSonde Audio Toolbox — hardware histórico

El manual del fabricante describe Energy-Time Graph: genera un pulso, registra la evolución de la recepción y muestra la llegada inicial. Permite leer tiempo o distancia equivalente y examinar picos. Es la referencia aportada por el usuario para el módulo de delay.

Lección de diseño: conservar el manejo de instrumento portátil, con una lectura rápida y una gráfica inspeccionable. La arquitectura de un móvil con DAC no hereda automáticamente la sincronización de ese hardware.

Manual original del fabricante conservado por un tercero (fuente primaria por autoría, alojamiento archivado):
- https://manualzz.com/doc/6982850/the-audio-toolbox-user-manual
- https://www.manualslib.com/manual/3753099/Terrasonde-Audio-Toolbox.html

### Open Sound Meter

Analizador de espectro y transferencia con magnitud, fase, impulso, coherencia y otras vistas. El fabricante indica versiones de escritorio y versión iPad.

Lección de diseño: claridad y comparabilidad de trazas. No se presupone versión Android por ser multiplataforma.

- https://opensoundmeter.com/en/
- https://opensoundmeter.com/static/manuals/v1.3.pdf

### AudioTools — Studio Six Digital

Conjunto de herramientas de medida en iOS. Su módulo de transferencia describe uso de referencia y señal medida; generadores y herramientas móviles muestran la viabilidad de la interacción portátil.

- https://studiosixdigital.com/
- https://studiosixdigital.com/audiotools-modules-2/acoustic-analysis-modules/transfer-function/
- https://studiosixdigital.com/audiotools-modules-2/utilities/generator/

### AudioTool — Android, jjbunn

Producto diferente de AudioTools. Su ficha incluye RTA, espectrograma, waterfall, generador y calibración. Es referencia directa para probar un flujo existente en el Samsung, sin tomar su catálogo como validación de nuestro hardware.

- https://play.google.com/store/apps/details?id=com.julian.apps.AudioTool
- https://sites.google.com/site/bofinit/audiotool

### AFMG SysTune

Referencia adicional de medición multicanal y uso de barridos/ruido para sistemas de sonido. No se adopta su complejidad multicanal en el MVP.

- https://www.afmg.eu/en/afmg-systune
- https://www.afmg.eu/en/afmg-systune-standard

## Fuentes oficiales de Android

- Oboe, biblioteca C++ de audio: https://developer.android.com/games/sdk/oboe
- Rendimiento/latencia y callbacks: https://developer.android.com/games/sdk/oboe/low-latency-audio
- Definición de latencia de entrada/salida: https://developer.android.com/ndk/guides/audio/audio-latency
- Captura UNPROCESSED y fallback: https://developer.android.com/reference/android/media/MediaRecorder.AudioSource
- Disponibilidad de captura sin procesado: https://developer.android.com/media/platform/mediarecorder
- Audio USB de Android: https://source.android.com/docs/core/audio/usb
- Timestamps: https://developer.android.com/reference/android/media/AudioTimestamp

## Qué está confirmado y qué es propuesta

Confirmado por decisión del usuario: Android/S24 Ultra, micrófono interno, salida DAC/interfaz, generador manual, delay por señal conocida, respuesta mediante barrido y previsión de hardware futuro.

Confirmado documentalmente: las herramientas citadas tienen los métodos descritos en sus fuentes. No confirma su exactitud en nuestro montaje.

Propuesta de este paquete: nombre SONDA, Kotlin/Compose+C++/Oboe, tres áreas de UI, parámetros iniciales y objetivos de test. Son decisiones revisables durante implementación.

Pendiente de prueba: rutas simultáneas en el Samsung concreto, procesado real del micrófono, banda útil, latencia y deriva, compensación absoluta y prestaciones del DAC no seleccionado.

## Oportunidad de mejora

Unir selección de frecuencia, excitación, observación temporal y capturas por posición en pocas acciones. Registrar condiciones y calidad para evitar comparaciones engañosas. No añadir IA, clasificación de causas o EQ automática antes de que la cadena de medición sea útil y verificable.
