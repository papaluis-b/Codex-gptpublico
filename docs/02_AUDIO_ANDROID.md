# Audio Android y arquitectura

## Propuesta tecnológica

Kotlin + Jetpack Compose para UI, ciclo de vida y persistencia. C++ con Oboe para streaming y núcleo DSP probado fuera del teléfono. Android NDK/CMake donde corresponda. Elegir versiones estables compatibles en el entorno real y fijarlas en el repositorio. Objetivo inicial arm64 y Android moderno; minSdk propuesto 28, revisable según dependencias. El S24 Ultra es el objetivo físico, no una garantía de compatibilidad con todas sus versiones de One UI.

No crear un driver USB propio en la primera versión. Usar el audio USB gestionado por Android. No prometer modo bit-perfect ni ausencia de resampling.

## Componentes

| Componente | Responsabilidad |
| --- | --- |
| AudioDeviceRepository | Inventario, capacidades declaradas y eventos de conexión |
| AudioEngine | Abrir streams, negociar configuración y mantener captura/salida |
| SignalGenerator | Excitación determinista y estado del oscilador |
| CaptureBuffer | Audio continuo/pretrigger y contadores de frames |
| SpectrumProcessor | FFT, PSD, bandado y espectrograma |
| MeasurementRunner | Orquestación de pulso/barrido y cancelación |
| DelayEstimator | Correlación, candidatos y métricas de calidad |
| SweepProcessor | Deconvolución, impulso y respuesta |
| CalibrationRepository | Perfiles y vigencia de compensación |
| SessionRepository | Guardado, recuperación y exportación |
| UI/ViewModels | Presentación de estados, nunca reloj de medida |

Mantener interfaces de captura y reproducción para sustituir después el micrófono por una interfaz de dos entradas sin reescribir DSP.

## Ruta inicial

Entrada solicitada: micrófono integrado. Salida solicitada: DAC USB. Enumerar dispositivos y usar identificadores de entrada/salida independientes. Comprobar dispositivo realmente activo al abrir y durante la operación. Capacidades declaradas no equivalen a formato negociado.

No dar por supuesto que una selección preferida se respeta. Si Android cambia la captura al micrófono USB o al auricular, detener la medida y explicarlo. Si no se puede confirmar la ruta, mostrar estado no verificado.

Pedir captura UNPROCESSED cuando sea viable, consultar soporte declarado y registrar el resultado. La disponibilidad declarada no basta para afirmar ausencia de AGC, filtrado o procesado del fabricante; validarlo mediante pruebas de nivel y tiempo. No usar rutas orientadas a voz que puedan cancelar el propio estímulo. Desactivar efectos de sesión controlables y registrar los que no se puedan controlar. No afirmar que la app puede desactivar todo el DSP de Samsung.

## Streaming

- Solicitar 48 kHz inicialmente; comprobar el valor real. Usar el real en todas las fórmulas.
- Captura mono suficiente para el MVP; salida estéreo si se negocia. Selección de canal solo cuando esté soportada.
- Solicitar rendimiento de baja latencia. Usar modo compartido o exclusivo según estabilidad; menor latencia no es más importante que continuidad y repetibilidad.
- Registrar sample rate y formato de ambos streams, tamaño de buffer, canales, tiempos de arranque y discontinuidades disponibles.
- La captura debe estar activa antes de emitir; mantener pretrigger y postcaptura.
- Evitar reiniciar streams entre repeticiones de una misma medida si hacerlo introduce variabilidad.
- No reproducir la entrada hacia la salida: no crear monitorización acústica involuntaria.

## Relojes y timestamps

DAC y captura interna pueden tener relojes distintos. No identificar frame de salida N con frame de entrada N por igualdad de índices. Mapear ambos a una base monotónica común cuando los timestamps sean válidos. Usar contadores de 64 bits y comprobar avance y discontinuidades.

Guardar timestamps de hardware/plataforma disponibles, incertidumbre y fallback usado. El momento de pulsar un botón, programar un callback o escribir un buffer no es el instante físico de emisión.

Para barridos largos, estimar/describir deriva entre relojes y corregir solo con método validado. La señal temporal recibida por la propia PA no permite separar por sí sola latencia del instrumento y tiempo de vuelo.

## Estados

Idle → Preparing → Capturing → Emitting → TailCapture → Processing → Result.

Error y Cancelled deben ser accesibles desde cualquier etapa activa. Cambio de ruta: cancelar, silenciar, registrar causa e invalidar el resultado incompleto. Tras recuperar ruta no reanudar tonos automáticamente.

## Ciclo de vida y permisos

Permiso de micrófono y política actual de audio de Android. MVP funciona en primer plano: al ir a segundo plano, perder foco, recibir interrupción relevante o bloquear pantalla, cancelar emisión y medida. No añadir servicio persistente inicialmente. Mantener pantalla activa durante la medición iniciada por el usuario. Revalidar ruta al volver.

Guardado privado y exportación mediante mecanismos estándar de Android, sin permisos amplios de almacenamiento. Sin permiso de internet si ninguna dependencia lo necesita.

## Fallos observables

Permiso denegado, dispositivo ocupado, ruta no confirmada, formato no disponible, captura silenciosa, clipping, pérdida de frames, timestamps inválidos, cambio de volumen/ruta, falta de espacio y error de archivo. Ninguno debe terminar en una curva aparentemente válida sin aviso.
