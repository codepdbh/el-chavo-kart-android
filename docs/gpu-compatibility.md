# Compatibilidad GPU

Snapdragon, Dimensity y Exynos son **plataformas SoC**; Adreno, Mali y Xclipse son **familias de GPU**. La compatibilidad se decide con lo que el driver expone en Vulkan (sonda `GpuProbeActivity`, proceso aislado `:probe`), nunca por el nombre del SoC.

La sonda informa: versión de instancia y dispositivo, nombre/versión/ID del driver, vendor/device ID, `maxBoundDescriptorSets` y demás límites, descriptor indexing (runtimeDescriptorArray, partiallyBound, update-after-bind), shaderInt64/16, BC1–BC5, ETC2, ASTC y formatos sin comprimir **por uso** (sampled / filtro lineal / attachment / blend), formatos depth/stencil, heaps y tipos de memoria, familias de colas, sample counts y todas las extensiones.

Requisitos del renderer del juego: **sin determinar** hasta que el backend Xenos→Vulkan se ejecute con este juego. Hoy solo se evalúa el mínimo del diagnóstico (Vulkan 1.1, swapchain, cola gráfica).

## Matriz de validación

| Plataforma / GPU | Dispositivo | Arranque | Driver | Sonda | Shaders | Menú | Carrera | Audio | Guardado | FPS |
|---|---|---|---|---|---|---|---|---|---|---|
| Snapdragon 665 / Adreno 610 | Redmi Note 8 (Android 17) | APK diagnóstico instalado; launcher iniciado (log) | SIN VERIFICAR | SIN VERIFICAR (pantalla bloqueada) | — | — | — | — | — | — |
| Snapdragon 8 Elite / Adreno 830 | Galaxy S25 Ultra SM-S938B (Android 17) | ✅ | Sistema (Qualcomm 0x80320040) | — | ✅ compilan | — | ✅ carrera demo renderizada | SIN VERIFICAR | — | SIN MEDIR |
| Exynos / Xclipse | — | SIN VERIFICAR | | | | | | | | |
| Exynos / Mali | — | SIN VERIFICAR | | | | | | | | |
| Dimensity / Mali | — | SIN VERIFICAR | | | | | | | | |

Ninguna familia se marca compatible por inferencia ni por haber compilado el APK.
