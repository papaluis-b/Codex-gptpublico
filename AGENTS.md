# Instrucciones del repositorio

## Prioridades

Simplicidad de uso, claridad de las unidades, resultados reproducibles y motor estable. El destinatario es un técnico de sonido que conoce su trabajo; evitar tutoriales invasivos y lenguaje comercial.

## Forma de trabajar

- Leer la documentación antes de implementar. Registrar cambios sustanciales en un documento de decisiones.
- Implementar por hitos verticales y mantener la aplicación compilable.
- No esperar confirmación por detalles reversibles de implementación. No publicar en tiendas ni conectar servicios externos sin solicitud expresa.
- No introducir cuentas, backend, telemetría, nube ni dependencia de internet para medir.
- No afirmar que se ha probado un teléfono o DAC si solo se ejecutaron pruebas sintéticas o emulador.
- No inventar capacidades del S24 Ultra: versión de sistema, ruta, sample rate y procesado se observan y registran.
- Fuentes técnicas: documentación oficial y publicaciones primarias. Registrar dependencias y licencias.

## Calidad técnica

- Callback de audio sin disco, red, bloqueos, logs frecuentes, asignaciones dinámicas ni FFT pesada. Buffers preasignados y colas acotadas.
- Detectar pérdida de muestras y marcar la medida como inválida o dudosa. No rellenar huecos sin registrarlo.
- Procesamiento DSP separado de Android para pruebas deterministas.
- Usar timestamps y contadores de muestras; conservar la información bruta aunque la visualización cambie el origen temporal.
- No confundir bandado de energía con suavizado de magnitud.
- No inventar precisión mediante interpolación gráfica o zero padding.
- No aplicar EQ correctora del micrófono por modelo sin una caracterización válida.
- Solo validar compensaciones para la configuración realmente comprobada.

## Entregas

Documentar compilación, instalación, permisos, diagnóstico, limitaciones, pruebas realizadas y pruebas pendientes. Incluir instrucciones de validación manual cortas. Los tests deben cubrir errores reales de medida, no reproducir trivialmente la implementación.
