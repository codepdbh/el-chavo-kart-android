# Informe XEX — El Chavo Kart (Xbox 360)

Generado por `tools/xex_analyze.py` el 2026-10-19 18:29. Datos extraídos del ejecutable real; no editar a mano.

| Campo | Valor |
|---|---|
| Archivo | default.xex |
| Tamaño | 30003200 bytes |
| SHA-256 (XEX) | 1db4e96b413d5629206ffe63e73c0e1cfaca9eea24bcf6fced6db9b135ce120f |
| SHA-256 (imagen PE reconstruida) | 14ea6c13d5217023b1af50ac86dbf1a10598ddb119313dd8d14c762113907719 |
| Formato | XEX2 |
| Tipo de módulo | title |
| Nombre PE original | ChavoKartGame-Xbox360-Shipping.pe |
| Title ID | 475807D7 |
| Media ID | 5C2ECDD6 |
| Versión | 0.0.0.3 |
| Versión base | 0.0.0.3 |
| Disco | 1/1 |
| Savegame ID | 00000000 |
| Regiones | ALL (`0xFFFFFFFF`) |
| Cifrado | AES-128 (retail key) |
| Compresión | basic (zero-fill blocks) |
| Image base | 0x82000000 |
| Entry point | 0x830DB568 |
| Tamaño de imagen | 0x1DB0000 (29.7 MiB) |
| Tamaño de página XEX | 0x10000 |
| Pila por defecto | 262144 |
| System flags | 0x00004600 |
| Fecha de enlace | 2013-11-28T17:24:27+00:00 |
| TLS | {"slot_count": 64, "raw_data_address": "0x00000000", "data_size": 0, "raw_data_size": 0} |

## Secciones PE

| Nombre | Dirección | Tamaño virtual | Ejecutable | Escritura |
|---|---|---|---|---|
| `.rdata` | 0x82000600 | 0x25ADFC | no | no |
| `.pdata` | 0x8225B400 | 0x66470 | no | no |
| `BINKCONS` | 0x822C1A00 | 0x2940 | no | no |
| `.text` | 0x822D0000 | 0x120B3F8 | sí | no |
| `BINK` | 0x834DB400 | 0x13600 | sí | no |
| `RADCODE` | 0x834EEA00 | 0xAF4 | sí | no |
| `BINKBSS` | 0x834F0000 | 0x5FC8 | no | sí |
| `.data` | 0x834F6000 | 0x8367E0 | no | sí |
| `.XBMOVIE` | 0x83D2C800 | 0xC | no | sí |
| `BINKDATA` | 0x83D2CA00 | 0x2B70 | no | sí |
| `RADDATA` | 0x83D2F600 | 0x24 | no | sí |
| `.edata` | 0x83D30000 | 0x3F6 | no | no |
| `.idata` | 0x83D40000 | 0x544 | no | sí |
| `.XBLD` | 0x83D50000 | 0x150 | no | no |
| `.reloc` | 0x83D50200 | 0x1640B8 | no | no |

## Rangos de páginas (descriptor de seguridad)

| Inicio | Fin | Tipo |
|---|---|---|
| 0x82000000 | 0x822D0000 | readonly |
| 0x822D0000 | 0x834F0000 | code |
| 0x834F0000 | 0x83DB0000 | data |

## Bibliotecas estáticas enlazadas (XDK)

| Biblioteca | Versión | Aprobación |
|---|---|---|
| XGRAPHC | 2.0.21250.0 | approved |
| XNET | 2.0.21250.0 | approved |
| XONLINE | 2.0.21250.0 | approved |
| XMP | 2.0.21250.0 | approved |
| XAPOBA | 2.0.21250.0 | approved |
| XAUDIO2 | 2.0.21250.0 | approved |
| XAPOFX | 2.0.21250.0 | approved |
| D3DX9 | 2.0.21250.0 | approved |
| LIBCMT | 2.0.11886.0 | unapproved |
| XAPILIB | 2.0.21250.6 | approved |
| XPARTY | 2.0.21250.0 | approved |
| XHV2 | 2.0.21250.0 | approved |
| XMCORE | 2.0.21250.0 | approved |
| XBOXKRNL | 2.0.21250.0 | approved |
| XMIC | 2.0.21250.0 | approved |
| XWMAD | 2.0.21250.0 | approved |
| X3DAUDL | 2.0.21250.0 | approved |
| D3D9LTCG | 2.0.21250.0 | approved |
| LINK | 10.0.11886.0 | approved |
| C2 | 16.0.11886.0 | approved |
| C1 | 16.0.11886.0 | approved |

## Estadísticas del código (patrones exactos de instrucción)

- Palabras en secciones ejecutables: 4750651
- `blr`: 47812 · `bctr` (saltos indirectos/tablas): 3150 · `bl`: 254433 · `mflr r12` (prólogos): 51981

El número real de funciones lo determina el análisis de ReXGlue (`analysis/functions/`).

## Indicios de middleware en la imagen

| Componente | Coincidencias |
|---|---|
| Unreal Engine 3 | 260 |
| FMOD | 381 |
| Bink video | 29 |
| Scaleform GFx | 194 |
| PhysX / NovodeX | 35 |
| XAudio / XACT | 6 |
| SpeedTree | 3 |
| Xbox Live / XUI | 39 |

Los indicios por cadenas son orientativos; cada componente se confirma en `docs/ue3-analysis.md`.
