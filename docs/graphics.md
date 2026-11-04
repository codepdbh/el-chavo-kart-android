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

Con la compensación se comprobó la pantalla de título y una carrera controlada
en el S25: la franja desapareció y el juego se mantuvo cerca de 30 FPS durante
el tramo observado. Todavía se observaron zonas sobreexpuestas y algunas
pausas de compilación de shaders; no se ha demostrado que estén corregidas.
La lectura síncrona completa (`--readback_resolve=full`) se probó como
diagnóstico y bajó a unos 17–19 FPS, por lo que no se activa por defecto.

Para repetir las pruebas en un dispositivo desbloqueado con los datos ya
importados, ejecutar `python tools/android_render_test.py NOMBRE --seconds 75 --
--chavokart_depth_bias_units=0`. La herramienta guarda captura y registro en
`out/android-tests/NOMBRE` y restaura `extra_args.txt` al terminar. En una
compilación depurable utiliza el registro privado; en la compilación normal
recoge el registro de Android del proceso del juego.
Las capturas y los datos del juego son locales y no se incluyen en el APK.
