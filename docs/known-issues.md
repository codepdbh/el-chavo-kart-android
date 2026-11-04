# Problemas conocidos

| # | Área | Problema | Evidencia | Estado |
|---|---|---|---|---|
| 1 | RECOMP | Funciones alcanzables solo por vtable/callback no descubiertas | `0x82BAEEB8` (PC, 2026-10-19) | Resuelto hasta la pantalla de título con `chavokart_data_entries.toml`; pueden aparecer más en menús/carreras |
| 2 | RECOMP | `sub_82925E90` es una tabla de saltos traducida como código (gap-fill) | sin referencias en la imagen | Inalcanzable, documentado en `config/recomp_dead_placeholders.txt` |
| 3 | RECOMP | 137 punteros desde datos a código tras instrucción que no cierra flujo, rechazados por prudencia | `analysis/functions/data_referenced_entries.json` | Revisar solo si el runtime los llama |
| 4 | DISCO | Quedan ~12 GB libres en C:; el árbol de build PC + código generado ocupa ~1 GB y el build Android con juego necesitará varios GB más | `df` | Vigilar antes del build Android con juego |
| 5 | ANDROID | Interfaz del launcher, sonda GPU y prueba Vulkan sin comprobar visualmente | teléfono bloqueado durante la prueba | Pendiente de prueba con el teléfono desbloqueado |
| 6 | IDIOMA | No se sabe aún cómo el motor elige ESN vs ESM | — | Requiere runtime |
| 7 | AUDIO | Bancos de voces por idioma no identificados | hipótesis en `docs/ue3-analysis.md` | Requiere runtime |
| 8 | GRÁFICOS | Pista con franja negra y zonas sobreexpuestas en Adreno | Captura de profundidad y pruebas en Galaxy S25 Ultra, 2026-11-04 | La compensación de profundidad elimina la franja en el circuito probado; quedan zonas blancas y falta validar otras pistas y GPU. Ver `docs/graphics.md`. |
