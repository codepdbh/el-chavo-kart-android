# Diagnóstico gráfico de Vulkan

En el Galaxy S25 Ultra (Adreno 830, driver del sistema `0x80320040`), la pista
presentaba una franja negra. Una captura de RenderDoc mostró que, en un píxel
de la franja, la pasada de profundidad escribía aproximadamente `0.00523878`.
La pasada de color para la misma geometría generaba color, pero fallaba la
comparación de profundidad `GREATER_OR_EQUAL`. La captura de la pasada de color
todavía contenía el negro de la limpieza. La conversión opcional a profundidad
float24 y la lectura de resoluciones de la GPU no eliminaron el defecto.

En Android con GPU Qualcomm, el renderizador aplica por defecto una pequeña
compensación de 16 unidades Vulkan a las pasadas de color con profundidad
`D24FS8` y comparación `GREATER_OR_EQUAL`. No se aplica a la pasada solo de
profundidad, a shaders que escriben profundidad ni al resto de comparaciones.
El estado original se restaura en el siguiente dibujo. Se puede desactivar para
diagnóstico con `--chavokart_depth_bias_units=0`; es una solución específica
para una diferencia observada en Adreno, no una garantía para otros drivers.
Una compensación de profundidad también puede cambiar la visibilidad de
superficies casi coincidentes, por lo que deben revisarse más circuitos y GPU.
El estado corregido se incorpora a la caché de estado dinámica del renderizador,
sin enviar un cambio adicional por cada dibujo.

Con la compensación se comprobó la pantalla de título y una carrera controlada
en el S25: la franja desapareció y el juego se mantuvo cerca de 30 FPS durante
el tramo observado. Todavía se observaron zonas sobreexpuestas y algunas
pausas de compilación de shaders; no se ha demostrado que estén corregidas.
La lectura síncrona completa (`--readback_resolve=full`) se probó como
diagnóstico y bajó a unos 17–19 FPS, por lo que no se activa por defecto.
Los destellos blancos también se han reproducido con la compilación asíncrona
desactivada; su causa sigue abierta.

Para repetir las pruebas en un dispositivo desbloqueado con los datos ya
importados, ejecutar `python tools/android_render_test.py NOMBRE --seconds 75 --
--chavokart_depth_bias_units=0`. La herramienta guarda captura y registro en
`out/android-tests/NOMBRE` y restaura `extra_args.txt` al terminar. En una
compilación depurable utiliza el registro privado; en la compilación normal
recoge el registro de Android del proceso del juego.
Las capturas y los datos del juego son locales y no se incluyen en el APK.

## Modos de vídeo y compatibilidad móvil

El selector de modo de vídeo anuncia 1280×720 (predeterminado) o 1024×576
al juego mediante `video_mode_width` y `video_mode_height`. El modo menor es
experimental: en el Galaxy S25 Ultra arrancó y mostró la carrera, pero todavía
no se ha medido el tamaño de todos los objetivos de render ni demostrado una
mejora de FPS. El swapchain seguía siendo 2340×1080. No confundir este ajuste
con un escalado fraccional de todos los dibujos: el camino Xenos del SDK usa
escalas internas enteras. Una prueba a 640×360 mostró la imagen estrecha y
sobreexpuesta, por lo que no se ofrece ese modo.

Se revisó [NFSMW-Recompiled-Mobile](https://github.com/victorgbd/NFSMW-Recompiled-Mobile)
en el commit `0b3b3be1f700e2227c03b557dc33e9e50a56c6cc`. Su motor nativo
tiene adaptaciones de capacidades, conversión de texturas y correcciones de
sincronización comprobadas en Mali-G57; ese motor es distinto del renderizador
Xenos de este port y no pueden trasladarse como un interruptor genérico. El
diagnóstico Vulkan existente informa de formatos BC, descriptores y otras
capacidades reales. MediaTek identifica el SoC, no una familia única de GPU;
no se han aplicado cambios globales por marca ni validado este APK en Mali.
