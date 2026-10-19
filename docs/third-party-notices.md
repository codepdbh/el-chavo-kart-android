# Avisos de terceros

| Componente | Uso | Licencia |
|---|---|---|
| ReXGlue SDK (rexglue/rexglue-sdk, fork en nfsmw-android `sdk/`) | runtime, codegen | BSD 3-Clause, © 2026 Tom Clay; partes de Xenia © Ben Vanik y colaboradores (BSD) |
| NFS-CARBON-360-ANDROID-RECOMP | `src/codegen/phase_gapfill.cpp`, `tools/fix_local_branches.py` (adaptado), estructura CMake/scripts | BSD 3-Clause |
| nfsmw-android (NFSMW Android Evolved) | `DriverArchive`, `GpuDrivers` (adaptados) | GPLv3 |
| the-simpsons-game-android | diagnóstico Vulkan (`vulkan_context`, `android_main`, logging), `LandscapeSDLActivity`, importación SAF, wrapper Gradle | GPLv3 |
| XenonRecomp (hedge-dev) | base conceptual del codegen de ReXGlue | MIT |
| SDL3 | ventana, entrada, ciclo de vida Android | zlib |
| Vulkan headers / loader | API gráfica | Apache-2.0 / MIT |
| FFmpeg (vía SDK) | decodificación XMA | LGPL-2.1+ |
| libadrenotools (vía SDK) | drivers Vulkan de usuario (build con juego) | BSD-2-Clause |
| Capstone (herramientas de análisis, no se distribuye) | desensamblado PPC | BSD-3 |

Al incluir código GPLv3 de nfsmw-android y the-simpsons-game-android, el código de este proyecto se distribuye bajo **GPLv3**.

No se distribuyen: el juego, su ejecutable, sus recursos, el código C++ generado a partir del XEX (`generated/`), imágenes ni volcados derivados, ni drivers gráficos de terceros. El Chavo Kart y sus marcas pertenecen a sus propietarios.
