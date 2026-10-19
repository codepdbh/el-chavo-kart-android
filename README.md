# El Chavo Kart — Android Evolved

Port nativo experimental de **El Chavo Kart (Xbox 360, Unreal Engine 3)** a Android ARM64 mediante recompilación estática PowerPC → C++ con ReXGlue. No es un emulador, no usa Xenia ni streaming, y no incluye el juego: cada usuario aporta su propia copia.

## Estado real

| Fase | Estado |
|---|---|
| Análisis XEX / UE3 | ✅ Hecho (`analysis/`, `docs/xex-analysis.md`, `docs/ue3-analysis.md`) |
| Código generado | ✅ 73 000+ funciones, ≥ 99,9 % de las instrucciones de código traducidas |
| Código compilado (PC x64) | ✅ `chavokart.exe` enlaza sin símbolos pendientes |
| Inicio del runtime (PC) | ✅ Monta datos, carga imagen, D3D12, hilo del juego en marcha |
| Inicio del motor UE3 (PC) | ✅ Vídeos Bink, audio XMA y pantalla de título "Press START" renderizados |
| APK de diagnóstico | ✅ Compila e instala (launcher es/en/pt-BR, sonda GPU aislada, prueba Vulkan, drivers ZIP, informe local) |
| Menú real (PC) | ⏳ Pendiente de enviar entrada |
| Juego en Android ARM64 | ❌ Aún no compilado para ARM64 |
| Gameplay / idiomas del juego / FPS | ❌ No iniciado |

Ver `docs/progress.md` para el detalle y `docs/known-issues.md` para los problemas abiertos.

## Requisitos de desarrollo (Windows)

Git, Python 3 (`cryptography`, `capstone`), LLVM/clang, Visual Studio 2022 C++ (Windows SDK), Android SDK 35 con NDK 28.2.13676358 y CMake 3.30.5, Android Studio (JDK 21).

## Uso

1. Coloca tu copia extraída junto al proyecto y crea la unión `game_xex` → carpeta con `default.xex` (solo lectura; nunca se modifica).
2. Clona las referencias en `references/` en los commits de `docs/recompilation.md`.
3. ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\generate-code.ps1   # análisis + codegen + reparaciones + informe
   powershell -ExecutionPolicy Bypass -File scripts\build-pc.ps1        # build de diagnóstico en PC
   powershell -ExecutionPolicy Bypass -File scripts\run-pc.ps1 -Seconds 90
   powershell -ExecutionPolicy Bypass -File scripts\build-android.ps1   # APK de diagnóstico
   powershell -ExecutionPolicy Bypass -File scripts\install-android.ps1 -Launch
   ```

Solo se acepta `default.xex` con SHA-256 `1db4e96b413d5629206ffe63e73c0e1cfaca9eea24bcf6fced6db9b135ce120f`.

## Estructura

`tools/` analizadores y reparaciones · `scripts/` pipeline reproducible · `src/` app ReXGlue y adaptaciones · `android/` launcher y diagnóstico · `analysis/` informes generados · `docs/` documentación · `config/` listas documentadas. `generated/`, `out/`, `references/` y `game_xex` no se versionan.

## Licencia y créditos

GPLv3 (incluye código de nfsmw-android y the-simpsons-game-android). Basado en ReXGlue SDK (BSD-3, con partes de Xenia), NFS Carbon 360 Android Recomp, NFSMW Android Evolved, The Simpsons Game Android, SDL3 y Vulkan. Detalle en `docs/third-party-notices.md`. El Chavo Kart y sus marcas pertenecen a sus propietarios. Desarrollado por codepdbh.
