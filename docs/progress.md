# Progreso

## 2026-10-19 — Sesión 1

### Estado verificado

| Nivel | PC x64 (diagnóstico, D3D12) | Android ARM64 |
|---|---|---|
| Código generado | ✅ 73 722 funciones, 4 711 444 instrucciones (≥ 99,9 %) | (mismo código) |
| Código compilado | ✅ `chavokart.exe` 115 MB | ❌ objetivo del juego aún no compilado para ARM64 |
| Inicio del runtime | ✅ | ❌ |
| Inicio del motor UE3 | ✅ config, paquetes LZX, audio XMA→SDL, vídeos Bink | ❌ |
| Primer frame real | ✅ logos Bink (Televisa, etc.) | ❌ |
| Pantalla de título | ✅ "Press START" a los ~95 s (captura `out/runtime/logs/run-20261019-182051-t95.png`, local) | ❌ |
| Menú navegable | ⏳ no probado (requiere entrada) | ❌ |
| Gameplay | ❌ | ❌ |
| APK de diagnóstico | — | ✅ compila, 16 KB alineado, instalado en Redmi Note 8; launcher iniciado (UI sin ver: teléfono bloqueado) |

### Implementado

- Análisis XEX completo y reproducible (`tools/xex_analyze.py`): descifrado, imagen PE, imports clasificados frente al SDK.
- Inventario UE3 (`tools/ue3_inventory.py`): versiones de paquete, LZX, FSB5/XMA, Bink, idiomas INT/ESN/ESM/PTB.
- Pipeline ReXGlue propio (`CMakeLists.txt`, `scripts/*.ps1`) con el SDK de nfsmw-android fijado y el gapfill de Carbon.
- Correcciones de descubrimiento con evidencia: 8 entradas manuales, 136 entradas referenciadas desde datos (excluyendo tablas SEH), 85 saltos locales reparados, 1 marcador muerto documentado.
- APK de diagnóstico: launcher horizontal es/en/pt-BR, importación SAF con SHA-256 y progreso real, sonda Vulkan aislada, prueba Vulkan, gestor de drivers ZIP con validación ELF/rutas/familia, informe ZIP local, borrado de caché.

### Pruebas realizadas

- `generate-code.ps1` ×4, `build-pc.ps1` ×3, `run-pc.ps1` ×4 (última: 100 s sin fallo, 6 capturas).
- `build-android.ps1` (BUILD SUCCESSFUL), `llvm-readelf` (LOAD 0x4000), `install-android.ps1` en Redmi Note 8.

### Próximo paso técnico

1. Enviar entrada (START/A) en PC y llegar al menú principal; revisar stubs que aparezcan (`XamVoiceSetMicArrayIdleUsers` es el único llamado hasta ahora).
2. Localizar en ejecución cómo el motor elige idioma (`--user_language`/locale → INT/ESN/ESM/PTB).
3. Compilar el objetivo del juego para Android ARM64 (vigilar el espacio en disco: ~12 GB libres).
4. Probar en el teléfono desbloqueado: UI del launcher, sonda GPU y prueba Vulkan.
