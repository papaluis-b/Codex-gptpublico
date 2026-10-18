# Consumidor JVM del DSP

Este proyecto Java usa solo `es.sonda:dsp:0.2.0` del repositorio Maven incluido en el kit. No necesita Android ni reproduce por los altavoces: genera buffers float32 y comprueba silencio inicial, RMS del seno/ruido y muestras válidas.

Con JDK 21, desde `jvm-example/` dentro del paquete:

```bash
./gradlew run
```

Desde el repositorio fuente, indicar el repositorio publicado:

```bash
./gradlew run -PsondaRepository=/ruta/Codex-gptpublico/build/generator-kit-repository
```

La dependencia Kotlin estándar se resuelve transitivamente mediante Maven. La salida `DSP_JVM_OK` confirma las comprobaciones del ejemplo; no valida una salida de audio física. Para escuchar o integrarlo en otra plataforma, el anfitrión debe llevar estos buffers a su propia API de audio.
