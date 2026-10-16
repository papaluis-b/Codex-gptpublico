# Datos y reproducibilidad

## Guardado local

Usar almacenamiento privado de la app. Sesiones y capturas con UUID y schemaVersion. No guardar claves de dispositivo como si fueran identificadores permanentes: los IDs Android pueden cambiar al reconectar. Conservar descripción/modelo y huella de configuración además del ID de sesión.

Cada medida incluye: tipo, fecha UTC con zona local, versión app/algoritmo, sesión, posición, notas, nivel de volumen conocido, señal, formato de entrada/salida solicitado y real, ruta confirmada/no verificada, sample rates, canales, FFT/ventana, tiempos, calidad y compensación.

Para delay/barrido guardar WAV de excitación y recepción sin modificar, timestamps/eventos y resultado derivado. Para RTA guardar bins/frecuencias y parámetros; grabación continua solo si el usuario la activa. Audio original no se sustituye por audio normalizado.

Si se pierde alguna muestra, el registro debe indicar intervalo y causa si se conoce. No escribir resultados «compensados» encima de datos observados. Una selección manual genera un resultado derivado con método manual.

## Resultados de delay

Campos mínimos: observedDelayMs, compensatedDelayMs nullable, compensationMs nullable, timingMethod, arrivalDefinition, candidates, repeatResults, medianMs, dispersionMs, qualityStatus, flags. La precisión visual no supera la resolución/validación disponible. No rellenar null con cero.

## Resultados de barrido

IR completa, sample rate, índice/tiempo de origen, ventana aplicada para respuesta, frecuencia y magnitud relativa, máscara de banda válida, normalización visual opcional y método de deconvolución/regularización. Guardar versiones de algoritmos para poder recalcular.

## Perfil de calibración/compensación

UUID, método, configuración, fecha, equipo de referencia, valores brutos, offset, dispersión, entorno y estado: experimental/validado/invalidado. Explicar qué latencias incluye. Un offset acústico de una cadena no es una calibración universal del instrumento.

## Exportación

Un ZIP por sesión o medida: manifest.json, measurement.json, stimulus.wav, capture.wav, impulse.wav cuando exista, response.csv y spectrum.csv según tipo. CSV con cabeceras, unidades y punto decimal; especificar origen temporal. Checksums SHA-256 para audio y parámetros de algoritmo. Evitar dependencias propietarias para reabrir los datos.

Compartir mediante selector estándar Android. Advertir antes de exportar audio largo del entorno; nunca subirlo automáticamente. Permitir borrar sesión y originales con confirmación solo para borrado irreversible.

## Capacidad

A 48 kHz mono float32, un minuto de audio bruto ronda 11,52 MB antes de metadatos. Usar escritura incremental en worker y límites de sesión. Comprobar espacio antes de medir. No usar memoria ilimitada para espectrograma o grabación.

El ejemplo incluido es ficticio y muestra deliberadamente un delay no compensado. No es evidencia del comportamiento del Samsung.
