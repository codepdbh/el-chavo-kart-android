# Localización

## Launcher (implementado)

Español (por defecto), English y Português do Brasil en `android/app/src/main/res/values{,-en,-pt-rBR}/strings.xml`, con las mismas claves. Ningún texto visible está fijo en el código Java: los errores usan `UserError` con un recurso traducido. El idioma del launcher se elige en IDIOMA → Idioma del launcher y se aplica con `Prefs.localize()` en todas las actividades; es independiente del idioma del juego.

## Juego (pendiente de runtime)

Idiomas presentes en la copia analizada (`analysis/localization/languages.json`): **INT, ESN, ESM, PTB**. El launcher los detecta en la copia importada (`ChavoKartGame_LOC_<LANG>.xxx` + `Coalesced_<LANG>.bin`) y los muestra, pero **no ofrece todavía un selector**, porque no se ha verificado cómo el motor elige el idioma:

1. Xbox: `XGetLanguage`/`XGetLocale` (XAM/xboxkrnl, implementados en el SDK y controlados por `--user_language`).
2. UE3: el código de idioma de 3 letras que usa para `*_LOC_XXX`, `Coalesced_XXX.bin` y `Xbox360TOC_XXX.txt`. ESN y ESM comparten idioma Xbox (español) y probablemente se distinguen por locale (México vs España): hipótesis a confirmar.

Voces: ver `docs/ue3-analysis.md` (bancos FSB5 candidatos sin confirmar). No se ofrece ninguna opción hasta comprobarla en ejecución.
