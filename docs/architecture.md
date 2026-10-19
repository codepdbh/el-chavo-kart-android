# Arquitectura — El Chavo Kart Android Evolved

## Cadena

```
game_xex/default.xex  (copia del usuario, solo lectura; unión local)
   │  tools/xex_analyze.py      → analysis/xex, analysis/imports
   │  tools/ue3_inventory.py    → analysis/ue3, analysis/localization
   ▼
rexglue codegen (SDK nfsmw-android + src/codegen/phase_gapfill.cpp de Carbon)
   │  chavokart_manifest.toml + chavokart_analysis.toml
   ▼
generated/default/*.cpp  (C++ derivado del XEX: local, ignorado por git)
   │  clang (PC x64 diagnóstico, D3D12)  /  NDK clang (arm64-v8a, Vulkan)
   ▼
libmain.so + runtime ReXGlue (kernel, XAM, VFS, Xenos→Vulkan, audio XMA, SDL3)
   ▼
APK (launcher Java + SDLActivity) — sin datos del juego dentro
```

## Separación de capas

| Capa | Ubicación | Regla |
|---|---|---|
| Runtime genérico Xbox 360 | `references/nfsmw-android/sdk` (fijado) | No se edita en el checkout; los cambios se aplican desde este proyecto por sustitución de fuentes en CMake o parches versionados. |
| Adaptaciones de código generado | `src/codegen/`, `chavokart_analysis.toml` | Cada dirección lleva evidencia en `analysis/functions/`. |
| Adaptaciones UE3 | `src/ue3/` (pendiente) | Solo comportamiento del motor confirmado en este XEX. |
| Hooks exclusivos de El Chavo Kart | `src/hooks/` (pendiente) | Direcciones verificadas en el ejecutable SHA-256 `1db4e96b…120f`. |
| Android | `android/` (pendiente) | Launcher, controles, drivers, diagnóstico. |

## Matriz de reutilización de las referencias

Estado tras inspeccionar el código real (commits en `docs/recompilation.md`).

| Módulo | Origen | Decisión | Motivo |
|---|---|---|---|
| SDK ReXGlue con plataforma Android (SDL3, Vulkan, `surface_android`, `rex_android_gpu_driver`, libadrenotools) | nfsmw-android `sdk/` | **Reutilizable** | Runtime genérico; usado ya por Carbon y Simpsons en Android. |
| Kernel xboxkrnl/XAM del SDK | nfsmw-android `sdk/src/kernel` | **Reutilizable** | Los 307 imports del juego están registrados (251 implementados, 43 stubs, 13 variables); ver `analysis/imports/imports_report.md`. |
| Decodificador XMA (FFmpeg) | nfsmw-android `sdk/src/audio` | **Reutilizable, a validar** | Los 15 bancos FMOD FSB5 del juego son XMA. |
| `phase_gapfill.cpp` (corte en `bctr` incondicional) | nfs-carbon `src/codegen` | **Reutilizado** | Mismo patrón MSVC de thunks virtuales consecutivos verificado en este XEX (7 errores de la pasada 1). |
| CMake con sustitución de fuentes del SDK y `sdk-bin` | nfs-carbon `CMakeLists.txt` | **Adaptado** | Mismo esquema; nombres y objetivos propios. |
| `build_pc.ps1` / `build_android.ps1` | nfs-carbon | **Adaptado** | `scripts/*.ps1` con comprobación de hash y logs. |
| `fix_local_branches.py`, `recover_small_entries.py`, `verify_*` | nfs-carbon `tools/` | **Adaptable** | Genéricos sobre el código generado; se aplicarán si aparecen `REX_FATAL` locales. |
| Entradas de `carbon_analysis.toml`, `carbon_missing_entries.cpp`, `carbon_nativo_*` | nfs-carbon | **No reutilizable** | Direcciones de NFS Carbon. |
| Renderizador nativo `nfsmw_nativo_*`, `nfsmw_shader_*`, `nfsmw_escenario_*` | nfsmw-android `app/src` | **Referencia de arquitectura** | Ligado a funciones D3D/escena del motor EA; UE3 tiene otro ciclo de render. |
| Vídeo `nfsmw_video_*` (WMV3) | nfsmw-android | **No reutilizable** | El Chavo Kart usa Bink 1 enlazado en el XEX (secciones `BINK`, `RADCODE`): lo decodifica el código recompilado. |
| Audio `nfsmw_audio_*` | nfsmw-android | **Referencia** | Ajustado a la mezcla de NFS; este juego usa FMOD sobre XMA. |
| Launcher, importación SAF, gestor de drivers ZIP, prueba GPU en proceso aparte, controles táctiles y editor | the-simpsons-game-android `app/` (deriva de nfsmw-android) | **Adaptable** | Genérico; requiere cambiar identidad, textos (es/en/pt-BR) y mapeo de botones de este juego. |
| `vulkan_context.cpp`, `gpu_quirks.cpp`, app de diagnóstico sin juego | the-simpsons-game-android | **Adaptable** | Permite un APK de diagnóstico antes de que el juego arranque. |
| Hooks de Simpsons (`frame_pacing`, `game_clock`, `physics_step`, `ink_outlines`…) | TheSimpsonsGameRecomp | **No reutilizable** | Direcciones y motor de otro juego. |

## Licencias

- SDK ReXGlue y nfs-carbon: BSD 3-Clause (con partes de Xenia, BSD).
- nfsmw-android y the-simpsons-game-android: GPLv3. Reutilizar su launcher o código de app implica distribuir este proyecto bajo GPLv3.
- Detalle en `docs/third-party-notices.md`.
