# Recompilación estática PPC → C++ → nativo

## Dependencias fijadas

| Componente | Origen | Commit |
|---|---|---|
| SDK ReXGlue (fork Android) | https://github.com/codepdbh/nfsmw-android `sdk/` | `507a63f7d21c9b8e2d6f15d3ed0e12b9b2c38712` |
| Terceros del SDK | rexglue-sdk v0.10.0 + submódulos | `c94f5ebdcb3c9d1a460ca48e04f9758448f8d518` |
| Referencia Carbon | https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP | `c3ee962bc6d9f20650d0225309b64d7297bca1f4` |
| Referencia Simpsons Android | https://github.com/codepdbh/the-simpsons-game-android | `ed0f1abfa1e783e18078c5e8156ffd8e4a03c828` |

`references/` no se versiona; `scripts/setup.ps1` debe clonar esos commits exactos (pendiente de automatizar por completo; hoy los clones se hicieron a mano en esos commits).

Toolchain host verificado: LLVM/clang 22.1.8, CMake 3.30.5 + Ninja del SDK Android, Visual Studio 2022 (Windows SDK), Python 3.14 con `cryptography` y `capstone`.

## Pipeline

```
scripts/generate-code.ps1
  ├─ comprueba SHA-256 de game_xex/default.xex
  ├─ configura out/pc (CMakeLists.txt) y compila rexglue con src/codegen/phase_gapfill.cpp (Carbon)
  ├─ rexglue codegen chavokart_manifest.toml  → generated/default
  ├─ tools/fix_local_branches.py               (REX_FATAL con etiqueta local → goto original)
  └─ tools/function_report.py                  → analysis/functions/{function_map,unresolved_functions}.json, coverage.md
scripts/build-pc.ps1   → out/pc/chavokart.exe (D3D12, diagnóstico)
scripts/run-pc.ps1     → out/runtime/logs/run-*.log
```

## Configuración propia del juego

- `chavokart_analysis.toml`: registros en contexto compartido (como Carbon) y 8 entradas declaradas a mano, cada una con su evidencia de desensamblado (pasada 1: 17 `UnresolvedCall`).
- `chavokart_data_entries.toml`: entradas alcanzables solo desde datos (vtables), generadas por `tools/scan_code_pointers.py` con filtros: destino tras fin de flujo, no etiqueta interna, no tablas de ámbito SEH.
- `config/recomp_dead_placeholders.txt`: marcadores inalcanzables documentados (tabla de saltos de `sub_82925E40` decodificada como código).

## Historial de pasadas

| Pasada | Cambio | Resultado |
|---|---|---|
| 1 | Manifiesto por defecto | 17 `UnresolvedCall`, sin código (347 s) |
| 2 | 8 entradas + gapfill de Carbon | 611 archivos, 73 591 funciones, 4 710 826 instrucciones; 86 `REX_FATAL` → 85 reparados localmente, 1 muerto documentado (217 s) |
| PC 1 | build + ejecución | Compila y enlaza (3 min). El runtime arranca, monta datos, inicializa D3D12; el hilo del juego registra `SetInterruptCallback(82B57C28)` y falla en llamada indirecta a `0x82BAEEB8` no registrada |
| 3 | +149 entradas desde datos | 9 saltos nuevos sin resolver: 2 entradas venían de tablas SEH (bloques internos) |
| 4 | Escáner excluye SEH: 136 entradas | 73 722 funciones, 0 marcadores pendientes. PC: 100 s sin fallos; vídeos Bink, audio XMA y **pantalla de título "Press START" renderizada** |
