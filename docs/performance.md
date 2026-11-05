# Rendimiento y modo experimental de 60 FPS

El launcher permite elegir la frecuencia original o «Hasta 60 FPS · experimental».
La opción original conserva el comportamiento anterior. La opción experimental
solicita 120 interrupciones verticales por segundo al juego, que en las pruebas
observadas presenta aproximadamente un fotograma por cada dos interrupciones.
La tasa real depende del dispositivo y la escena. La reproducción Bink conserva
su propia cadencia. No se ha validado todavía que la física, la velocidad del
juego y todos los temporizadores sean idénticos en ambos modos.

En Galaxy S25 Ultra con el driver del sistema, el contador del juego llegó a
60 FPS en la pantalla de título, pero se observó aproximadamente 30–50 FPS en
una carrera; SurfaceFlinger midió 39,9 FPS presentados durante una ventana de
8 segundos. El contador superpuesto mide paquetes de fotograma del juego y no
los fotogramas que finalmente aparecen en pantalla, por lo que puede diferir de
SurfaceFlinger. La compilación síncrona de nuevas canalizaciones y la temperatura
del dispositivo también afectaron las medidas. La compilación asíncrona conserva
la protección que evita presentar fotogramas con shaders incompletos.

Un perfil de 10 segundos con simpleperf mostró trabajo en el hilo de comandos
de GPU, el juego y el bucle de consulta de D3D `sub_82B5CE88`. Una pausa
experimental en ese bucle se descartó: todavía no existe una comparación de la
misma escena que demuestre una mejora sin afectar la latencia. La instrumentación
de esperas ya no consulta el reloj cuando está desactivada, y la compensación de
profundidad Adreno se integra ahora en la caché de estado de Vulkan para evitar
enviar dos cambios de profundidad por cada dibujo afectado.

La prueba también reprodujo los destellos blancos con compilación síncrona de
shaders. No se deben atribuir únicamente a la compilación asíncrona y siguen
pendientes de corrección. Las mediciones se encuentran en `out/android-tests/`
en el equipo de desarrollo; no se incluyen capturas ni datos del juego en el APK.

## Consultas de visibilidad y medición

En el backend Vulkan, `EndGuestOcclusionQuery` termina la entrega de comandos
y espera a que concluyan todas las operaciones de la cola antes de escribir el
resultado en memoria del juego. El perfil existente muestra que UE3 espera
activamente los resultados (`sub_82B5CE88`, 7,5 % de las muestras), pero no
demuestra cuánto de esa espera se debe a la lectura Vulkan.

La opción experimental **Consultas de visibilidad: Rápidas** desactiva las
consultas reales y usa el resultado aproximado del SDK (1000 muestras). Evita
esa sincronización por consulta, pero puede afectar la visibilidad, los efectos
y la carga de GPU. Por defecto se mantienen las consultas reales. No se ha
probado aún en un teléfono ni medido su efecto en FPS o iluminación.

Para comparar en el mismo circuito, temperatura y cámara, activar en
`extra_args.txt` `--chavokart_trace_occlusion=true` y
`--chavokart_trace_frame_times=true`. El registro indica cantidad, tiempo total
y máximo de lecturas de oclusión cada cinco segundos, y percentiles de tiempo
entre fotogramas enviados por el juego cada 120 fotogramas. Esos intervalos no
sustituyen una medida de fotogramas presentados con SurfaceFlinger. Comparar el
modo real y el rápido con la caché de shaders ya caliente y observar objetos,
sombras y luz; no inferir una mejora por una única escena.
