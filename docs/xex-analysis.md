# Análisis del XEX

Reproducir: `python tools/xex_analyze.py --xex game_xex/default.xex --sdk references/nfsmw-android/sdk --out analysis --image out/analysis/chavokart-82000000.bin`

Informes generados (datos reales del ejecutable): `analysis/xex/xex_report.md|json`, `analysis/imports/imports_report.md`.

## Identidad de la revisión soportada

| Campo | Valor |
|---|---|
| SHA-256 `default.xex` | `1db4e96b413d5629206ffe63e73c0e1cfaca9eea24bcf6fced6db9b135ce120f` |
| SHA-256 imagen PE reconstruida | `14ea6c13d5217023b1af50ac86dbf1a10598ddb119313dd8d14c762113907719` |
| Title ID / Media ID | `475807D7` / `5C2ECDD6` |
| Versión | 0.0.0.3 (base 0.0.0.3), disco 1/1 |
| Región | todas (`0xFFFFFFFF`) |
| PE original | `ChavoKartGame-Xbox360-Shipping.pe` (build Shipping de UE3) |
| Enlace | 2013-11-28, XDK 2.0.21250 |
| Formato | XEX2, AES-128 (clave retail pública de Xenia, leída del SDK), compresión *basic* |
| Image base / entry | `0x82000000` / `0x830DB568`, imagen 0x1DB0000 (29,7 MiB) |
| TLS | 64 slots, sin datos iniciales |

Solo esta revisión se acepta: `scripts/common.ps1` y `GameFiles.SUPPORTED` (APK) comprueban el hash.

## Secciones

`.text` 0x822D0000 (18,0 MiB de código), `BINK` y `RADCODE` (Bink de RAD enlazado estáticamente), `.rdata`, `.pdata` (52 366 entradas), `.data` (8,2 MiB), `.idata`, `.XBLD`, `.reloc`.

## Bibliotecas XDK enlazadas

XAUDIO2, XAPOFX, XAPOBA, X3DAUDL, XMCORE, XWMAD, D3D9LTCG, D3DX9, XGRAPHC, XONLINE, XNET, XPARTY, XHV2, XMIC, XMP, XAPILIB, XBOXKRNL, LIBCMT.

## Imports

307 imports (xboxkrnl 189, xam 118). Frente al SDK fijado: 251 implementados, 43 stubs, 13 variables de kernel, **0 ausentes**. Los stubs son sobre todo UI de Xbox Live (`XamShow*UI`), red (`NetDll_*`), dispositivos de E/S del kernel (`Io*ShareAccess`…) y `XAudioGetDucker*`; se revisarán si el juego los usa en un camino necesario (el runtime registra cada llamada a stub).
