# Build Android

Toolchain verificado en la máquina de desarrollo: JDK 21 (Android Studio `jbr`; **no** usar el Java 26 del sistema), Android SDK 35 (build-tools 35/36), NDK **28.2.13676358**, CMake del SDK **3.30.5**, Gradle wrapper **8.13**, AGP **8.9.1** (mismas versiones que the-simpsons-game-android).

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-android.ps1          # APK de diagnóstico (Debug)
powershell -ExecutionPolicy Bypass -File scripts\install-android.ps1 -Launch
```

## Contenido del APK de diagnóstico (`com.codepdbh.chavokart`, 0.1.0-diagnostic)

| Biblioteca | Función |
|---|---|
| `libSDL3.so` | SDL 3.5.0 del SDK fijado |
| `libckdiag.so` | Prueba Vulkan 1.1 (clear/present, recreación de swapchain, pausa/reanudación) — proceso `:vktest` |
| `libckprobe.so` | Sonda de capacidades Vulkan por JNI — proceso `:probe` |
| `libc++_shared.so` | STL |

Todas las bibliotecas tienen segmentos `LOAD` alineados a 16 KB (verificado con `llvm-readelf`). Las funciones Vulkan 1.1 se resuelven con `vkGetInstanceProcAddr` porque el `libvulkan.so` de API 26 solo exporta 1.0.

`-WithGame` se rechaza a propósito: el objetivo Android del juego (`CMakeLists.txt` raíz con `ANDROID`) se activará cuando el runtime se haya validado en PC.

## Resultado actual

- `BUILD SUCCESSFUL`, APK 1,7 MB.
- Instalado en Redmi Note 8 (SM6125/Adreno 610, Android 17): `adb install` correcto; el launcher arranca (`[ANDROID] Launcher Redmi Note 8 Android 17 withGame=false`). Interfaz, sonda y prueba Vulkan **sin verificar visualmente** (el teléfono estaba bloqueado).
