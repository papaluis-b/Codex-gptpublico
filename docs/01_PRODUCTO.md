# Producto y alcance

## Contexto de uso

Usuario único: técnico de sonido de eventos y artes escénicas. Uso de pie, recorriendo la sala, con poco tiempo de montaje. Objetivo: localizar problemas, comprobar hipótesis y comparar cambios. Inspiraciones: rigor espectral de Smaart, análisis de barrido y decaimiento de REW y manejo portátil del TerraSonde Audio Toolbox.

## Alcance inicial acordado

Dispositivo objetivo Samsung S24 Ultra; app Android nativa. Micrófono interno y salida por DAC/interfaz USB-C. La salida se conecta a la cadena de sonido existente. No hay selección de DAC concreta todavía. Sin micrófono externo obligatorio. Reservar abstracciones de entrada/salida para incorporarlo después.

### Casos de uso

UC1 — Mirar: abrir analizador, observar espectro y espectrograma y congelar una captura.

UC2 — Explorar: seleccionar 63 Hz en el gráfico, activar seno, ajustar finamente, hacer barrido local y guardar una observación. La selección de frecuencia nunca activa por sí sola la emisión.

UC3 — Medir llegada: emitir secuencia conocida, grabar la recepción, estimar delay, repetir y examinar dispersión. Mostrar curva y candidatos, no solo un número.

UC4 — Medir respuesta: barrido logarítmico configurable, captura, deconvolución, respuesta relativa e impulso. Comparar otra posición o un cambio de ajuste.

UC5 — Investigar persistencia: usar espectrograma y, después de validar el motor, excitación/silencio y análisis de decaimiento por banda. Evitar diagnosticar una causa solo por un pico.

## Requisitos funcionales

| ID | Requisito | Prioridad |
| --- | --- | --- |
| IO-01 | Enumerar entrada/salida y distinguir preferida de activa | Primer hito |
| IO-02 | Captura interna + salida USB simultáneas, o limitación explícita | Primer hito físico |
| IO-03 | Detener emisión al desconectar/cambiar ruta | Primer hito |
| GEN-01 | Seno con frecuencia manual y nivel dBFS | Primer hito |
| GEN-02 | Fases continuas y rampas de nivel | Primer hito |
| GEN-03 | Ruido rosa, barrido logarítmico y selección L/R si la ruta lo permite | Primera versión completa |
| GEN-04 | Barrido local y secuencia excitación/silencio | Después del generador básico |
| RTA-01 | FFT, bandado, cursor, promediado, máximos y congelación | Primera versión completa |
| RTA-02 | Espectrograma con escala de color y tiempo visible | Primera versión completa |
| DEL-01 | Estimación por señal conocida y gráfica temporal | Primera versión completa |
| DEL-02 | Repeticiones, dispersión, ambigüedad y selección manual | Primera versión completa |
| SWP-01 | Barrido y deconvolución con señal exacta guardada | Primera versión completa |
| DAT-01 | Persistencia local y comparación de capturas | Primera versión completa |
| DAT-02 | Exportación de WAV, JSON y CSV | Primera versión completa |
| CAL-01 | Perfil de compensación con estado validado/no validado | Experimental hasta prueba física |

## Límites de interpretación

- RTA muestra la energía recibida, no identifica automáticamente respuesta del sistema ni causa del problema.
- El seno sostenido facilita escuchar vibraciones y explorar frecuencias; no sustituye una medida de respuesta completa.
- Respuesta con micrófono interno: relativa y dependiente de orientación, procesado y nivel. Guardar esas condiciones.
- Retardo de llegada incluye procesamiento del sistema y propagación; la conversión a metros es distancia equivalente, no distancia geométrica garantizada.
- Primer inicio de energía, pico de correlación y pico del impulso pueden diferir. Identificar el método utilizado.
- Una alineación por llegada es un punto de partida; subgraves/cruces requieren validación de fase y suma en la banda pertinente.

## Fuera de alcance inicial

Transferencia de doble entrada completa, micrófonos externos, SPL certificado, STI/STIPA, control de procesadores, EQ automática, informes normativos, simulación 3D y detección automática de causas. No dejar controles que aparenten funcionar si aún no están implementados.

## Criterio de utilidad

Abrir, comprobar ruta, medir y guardar debe requerir pocas acciones. Un resultado dudoso debe ser reconocible. La app debe poder mostrar el dato que sustenta cada estimación y facilitar repetir la medida.
