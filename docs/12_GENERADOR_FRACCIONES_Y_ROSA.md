# Generador por fracciones de octava · SONDA 0.2.0

Fecha: 16 de octubre de 2026. Cambios solicitados: incrementos de frecuencia de 1/3, 1/24, 1/32 y 1/48 de octava y selección de ruido rosa en campanas de frecuencia, además del seno.

## Controles

En Generar, elegir Seno o Ruido rosa, seleccionar la fracción y usar − / + o introducir la frecuencia manualmente. La frecuencia mostrada es el centro geométrico de la banda. La selección no activa la salida. Cambiar de tipo silencia la salida y requiere pulsar Emitir otra vez. Cambiar frecuencia o anchura con ruido activo realiza una rampa hasta cero, sustituye los filtros y vuelve al nivel solicitado.

Para una fracción 1/n, cada paso multiplica o divide la frecuencia actual por `2^(1/n)`. Por ejemplo, desde 1000 Hz el siguiente paso de tercio es 1259,92 Hz. Son razones binarias exactas desde cualquier frecuencia actual; no una tabla de números preferidos IEC. Al alcanzar el límite, la frecuencia queda acotada. El rango de centros es 20 Hz–20 kHz a 48 kHz; a otros sample rates se restringe para mantener la banda por debajo de Nyquist.

En ruido rosa, los bordes del filtro son `fc / 2^(1/(2n))` y `fc * 2^(1/(2n))`. A 1000 Hz, un tercio cubre 890,90–1122,46 Hz y 1/48 cubre 992,81–1007,25 Hz. Son bordes a −3 dB de la transferencia del filtro: hay energía fuera de esos bordes por la caída gradual. La forma no es una gaussiana ni un corte rectangular. No se declara conformidad IEC. En centros próximos a los extremos audibles, parte de la banda puede quedar fuera de 20 Hz–20 kHz; la pantalla muestra los límites reales.

## Señal, nivel y reproducción

El ruido blanco procede de xorshift32 con semilla `0x534F4E44`. La coloración usa la aproximación de siete términos de [Paul Kellett, nota original de marzo de 2000](https://raw.githubusercontent.com/bdejong/musicdsp/master/source/files/pink.txt). El paso banda es Butterworth de orden total 4, preparado a partir de un prototipo analógico y transformación bilineal con predeformación de ambos bordes. Se ejecuta como dos biquads en doble precisión. Identificador exportado: `kellet7-xorshift32-butterworth4-v1`.

Una integración numérica de la potencia esperada del filtro de coloración y del paso banda determina la normalización RMS. La cuadratura usa coordenadas de banda para resolver incluso 1/48 a 20 Hz. Estrechar la campana conserva el RMS objetivo; la densidad de potencia dentro de la banda aumenta. El nivel de ruido es **dBFS RMS**, con referencia de amplitud 1 y límite de −24 dBFS RMS; su nivel instantáneo fluctúa. El seno mantiene **dBFS pico**, hasta −12 dBFS. Ambos arrancan en −40 dBFS. No son medidas de SPL y no incluyen el volumen Android ni la ganancia de la cadena física.

Las rampas duran 20 ms. Los estados del generador pertenecen al hilo de salida; la preparación de filtros y normalización queda en el hilo de control. No se añaden FFT, disco, bloqueos ni asignaciones dinámicas al renderizado. Los canales L/R contienen la misma señal. Si se detecta una muestra no finita o fuera de escala, el bloque no se entrega a AudioTrack, se detiene la salida y se registra el contador de clipping.

Los filtros estrechos necesitan tiempo para asentarse. La pantalla muestra una estimación del decaimiento de polos hasta el 1%; no asegura convergencia estadística del RMS. En 1/48 a 20 Hz, la anchura es aproximadamente 0,289 Hz y se necesitan capturas largas para estimar su energía. El analizador de 8192 muestras tiene bins de 5,859 Hz a 48 kHz; no resuelve todas esas campanas y muestra un aviso cuando su anchura es menor que un bin. No se amplía artificialmente la resolución gráfica.

## Datos y compatibilidad

El JSON conserva tipo de señal, centro, fracción, base de octava, nivel y referencia, bordes, algoritmo, semilla, estabilización aproximada y clipping del generador, junto a los estados de inicio y fin y los eventos de cambio. El WAV conserva el audio original del micrófono; no se sustituye por la señal emitida. La semilla y versión identifican el algoritmo determinista, pero no reconstruyen una salida física sin su historia completa de frames, cambios y tiempos de Android.

Los archivos de 0.1 siguen siendo legibles. La entrega 0.2 mantiene el identificador de aplicación y certificado debug de la entrega 0.1, con versionCode 2, para permitir la actualización conservando capturas. La simulación sigue generando una entrada fija de 1 kHz; permite probar controles y exportación de parámetros, pero no simula el ruido seleccionado ni su reproducción física.

## Verificación automática

```bash
source scripts/cloud-env.sh
./gradlew :dsp:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

- **20 pruebas DSP, PASS.** Incluyen las 11 de 0.1 y nueve nuevas: pasos y bordes logarítmicos; transferencia contrastada con un prototipo analógico independiente en todas las fracciones y centros de 20 Hz a 20 kHz; potencia rosa proporcional a 1/f; energía renderizada por octava; RMS al estrechar banda; estabilidad y ausencia de clipping en 1/48 a 20 Hz; rechazo fuera de banda; rampas y estéreo; determinismo entre bloques y ausencia de activación automática.
- La ondulación analítica de `f * |H(f)|²` del filtro rosa es menor de 0,35 dB entre 20 Hz y 20 kHz a 48 kHz. En señal renderizada, la diferencia entre energías por octava de 40 Hz a 20,48 kHz es menor de 0,8 dB. Son tolerancias verificadas, no precisión certificada.
- El RMS renderizado a 1 kHz coincide con −40 dBFS ±0,6 dB para las cuatro anchuras en tramos de unos 87 s. La prueba extrema de 20 Hz, 1/48 y −24 dBFS RMS admite ±3 dB por la fluctuación de una banda muy estrecha. Ninguna detecta clipping.
- **8 pruebas Android Robolectric, PASS.** Incluyen selección de todas las fracciones y tipos desde Compose, pasos reales, límites de nivel, conservación de ajustes al abrir una sesión y exportación de RMS/bordes/semilla, además de las pruebas previas de captura, ciclo de vida y rutas ausentes.
- **lintDebug y assembleDebug, PASS.** Cero errores lint; siguen las dos advertencias documentadas en la entrega 0.1 (versión de Robolectric y criterio conservador de espacio libre).
- **APK, PASS.** Firma v2 verificada con apksigner; el certificado SHA-256 coincide con el de 0.1 (`e647455e30c1617ccca0eb3e855e7d25a3f20d92d61752a094ada61ae7b703ef`). El manifiesto declara `es.sonda`, versión 0.2.0 y versionCode 2, con RECORD_AUDIO y sin INTERNET; se comprobó la biblioteca arm64 incluida por Compose.

Total: **28 pruebas, 0 fallos, 0 errores y 0 omitidas**. Informes bajo `dsp/build/test-results/test`, `app/build/test-results/testDebugUnitTest` y `app/build/reports/lint-results-debug.html`. Los artefactos de entrega y sus SHA-256 se guardan en `artifacts/`.

## Pendiente en el teléfono y DAC

No se ha probado esta versión en el Samsung ni en un DAC físico. Falta contrastar instalación/actualización, rutas reales, niveles, reproducción prolongada, cambio de campanas y parada ante desconexión, foco y segundo plano. Seguir el protocolo de `10_COMPILAR_Y_PROBAR.md`. El ruido rosa bandado completa esta parte del generador; no incorpora bandado de energía al RTA, barrido, delay, compensación temporal ni calibración del micrófono.
