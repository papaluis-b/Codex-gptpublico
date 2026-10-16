# Validación y criterios de aceptación

## Estados de evidencia

Cada criterio se registra como PASS, FAIL, NO EJECUTADO o BLOQUEADO con fecha, configuración y evidencia. Separar: unitario DSP, integración Android, emulador y prueba física. No sustituir prueba física por un vídeo de gráficos sintéticos.

Los umbrales siguientes son objetivos de ingeniería propuestos, no prestaciones prometidas del teléfono. Si fallan, investigar, registrar y revisar alcance sin ocultarlo.

## A. Pruebas deterministas del DSP

| Prueba | Criterio inicial |
| --- | --- |
| Seno centrado en bin | Amplitud recuperada ±0,2 dB con convención documentada |
| Seno entre bins | Comportamiento de ventana/interpolación documentado; sin afirmar exactitud de pico no validada |
| Ruido y Parseval | Energía temporal/espectral consistente ±0,2 dB en fixture determinista |
| Cambio de FFT | Potencia de banda ancha estable; PSD y bandado conservan sus unidades |
| Promediado | Resultado igual al promedio de potencia esperado, no promedio de dB |
| Silencio | Sin NaN/inf, sin pico de delay falso, piso visual documentado |
| Oscilador | Frecuencia nominal dentro de tolerancia numérica sobre stream sintético y fase continua |
| Rampas y mute | Envolvente de subida/bajada definida, sin saltos de amplitud inesperados |
| Delay entero | Recuperar retardo añadido con error ≤1 muestra en señal limpia de banda ancha |
| Delay fraccional | Cuantificar error y método, sin deducir precisión física de la interpolación |
| Inversión de polaridad | Detectar llegada sin rechazar solo por signo negativo |
| Reflexión más fuerte | Mostrar ambigüedad/candidatos y no llamar «directa» automáticamente al pico mayor |
| Ruido/clipping/huecos | Rechazo o indicador de calidad reproducible |
| Barrido con filtro FIR conocido | Respuesta recuperada ±0,5 dB en banda útil con tolerancias de borde documentadas |
| Barrido con delay y cola | Preservar delay y recuperar cola sin wrap-around |
| Deriva de reloj simulada | Detectar degradación y probar método de corrección si existe |
| Exportar/reabrir | Muestras, unidades y metadatos se conservan |

Fixtures: seno, multisinusoidal, ruido determinista, FIR de respuesta conocida, reflexiones sintéticas, filtro resonante, delays y distorsión/noise controlados. La señal esperada se obtiene con métodos independientes cuando sea posible. No usar exactamente la misma función a ambos lados para «demostrar» corrección.

## B. Primera prueba física: rutas

Registrar Samsung/modelo, Android, One UI, DAC, cable/hub y configuración. Confirmar salida por DAC y entrada por el teléfono: verificar por observación de ruta y prueba física controlada de proximidad/tap suave sobre el micrófono, sin golpearlo. Verificar que conectar USB no cambia la entrada silenciosamente.

Capturar y reproducir simultáneamente durante al menos diez minutos. Registrar huecos, fallos, uso de memoria y comportamiento térmico. Objetivo: sin huecos de audio detectados en condiciones de prueba; si no se cumple, ajustar carga/buffers y repetir. Esto no demuestra precisión de respuesta acústica.

Desconectar DAC, cambiar ruta, retirar permiso, perder foco y bloquear pantalla: salida debe silenciarse y la medida quedar cancelada. El generador no vuelve a arrancar solo al regresar.

## C. Caracterización del micrófono

Con fuente y posición fijas, repetir tonos/barridos a varios niveles de salida separados por pasos conocidos. Comprobar proporcionalidad del nivel recibido y comportamiento al inicio/final del tono. Buscar AGC, puertas, reducción de ruido y filtrado. Registrar capacidades declaradas UNPROCESSED y evidencia experimental por separado.

Comparar cinco barridos idénticos sin mover el móvil. Objetivo provisional de repetibilidad: ±1 dB en banda útil definida empíricamente y por encima del ruido; no exigir un rango completo que el micrófono no demuestre. La repetibilidad no demuestra exactitud absoluta. Mantener orientación y posición; registrar funda y obstrucción de micrófonos.

Si aparece procesado no lineal relevante, conservar el modo orientativo, marcarlo y limitar los resultados afectados. No «corregir» AGC con una curva estática.

## D. Delay físico

1. Fuente/móvil inmóviles: medir al menos diez veces sin reiniciar streams y después con reinicios. Registrar mediana, dispersión y outliers.
2. Introducir un retardo digital conocido en la misma cadena y sin mover nada. Objetivo propuesto: recuperar el incremento con error ≤1 ms para fuente de banda ancha y buena relación señal/ruido. No valida el retardo absoluto.
3. Mover el micrófono una distancia conocida sobre el eje y comprobar el incremento temporal aproximado, controlando geometría/reflexiones. Repetir varias distancias.
4. Validar el retardo absoluto frente a referencia independiente solo cuando se disponga de ella. Hasta entonces, estado no validado.

Si el offset cambia entre aperturas/rutas, no reutilizar compensación silenciosamente. Mostrar incertidumbre o mantener lectura observada. Subgraves necesitan protocolo separado por banda y definición de llegada.

## E. Barrido físico y comparación

Validar ausencia de clipping, captura completa y cola suficiente. Repetir misma posición y nivel. Para contrastar con REW/Smaart, usar una cadena de referencia con metodología compatible y registrar las diferencias de micrófono y ruta. Una diferencia entre dos micrófonos no demuestra error del algoritmo.

La comparación independiente es una prueba futura si falta equipo; no bloquea tests sintéticos ni interfaz. Sin esa referencia no declarar equivalencia de precisión con un analizador profesional.

## F. Aceptación de producto

- Instalación e inicio en el Samsung real.
- RTA utilizable sin DAC conectado.
- Entrada interna + salida DAC confirmadas o limitación transparente.
- Generador y captura simultáneos; cancelación y mute fiables.
- Resultados con unidades, método y estado de calidad.
- Guardar/reabrir/exportar sin perder originales.
- UI sin bloqueos durante análisis, en vertical y horizontal.
- Simulación inequívocamente separada de medición.
- Funciones no validadas identificadas en entrega y UI cuando afecten al resultado.

## Plantilla de informe por hito

Fecha/configuración; versión/commit; prueba; resultado; evidencia; limitación; siguiente acción. Adjuntar logs sin información sensible y archivos mínimos para reproducir fallos DSP.
