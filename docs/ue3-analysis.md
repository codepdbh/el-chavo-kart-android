# Análisis Unreal Engine 3

Reproducir: `python tools/ue3_inventory.py --game game_xex --out analysis` → `analysis/ue3/ue3_inventory.md|json`, `analysis/localization/languages.json`.

## Datos confirmados (cabeceras reales)

- Estructura: `ChavoKartGame/{Config/Xbox360, CookedXbox360, Movies}`, `Engine/Splash`, TOC por idioma (`Xbox360TOC*.txt`).
- 324 paquetes UE3 (`.xxx`, `.upk`, `.tfc`): **FileVersion 860, LicenseeVersion 0, EngineVersion 10246, CookerVersion 136** (UE3 estándar de Epic, época UDK 2013).
  - 302 paquetes con chunks **LZX**; 20 totalmente comprimidos (con `.uncompressed_size`). La descompresión la hace el propio código del juego (XMCORE estático), no hace falta un descompresor en el runtime.
  - Paquetes clave: `Core`, `Engine`, `GameFramework`, `IpDrv`, `OnlineSubsystemLive`, `GFxUI` (**Scaleform GFx**), `FMODAudio` (**FMOD**), `ChavoKartGame`, `ChavoKartEntry`, 13 personajes (`KartPawn*`), pistas (Acapulco, BrasilC, BrasilM, Castillo, Ciudad, ElEspacio, Heroes, LaFeria, Museo, Tanga…).
  - `GlobalShaderCache-Xbox360.bin` (shaders globales UE3); las cachés de shaders de materiales viajan en los paquetes.
- Configuración: `Coalesced_<LANG>.bin` (ini cocinados por idioma) + `Config/Xbox360/*.ini` (`TextureStreaming PoolSize=140`, `DetailMode=2`, `AmbientOcclusion=True`).
- Audio: 15 bancos **FMOD FSB5**, todos en formato **XMA** (decodificador XMA del SDK, FFmpeg).
- Vídeo: 9 películas **Bink 1** (`BIKi`) 1280×720 a 24/29,97/30 fps. Bink está enlazado en el XEX (secciones `BINK`, `RADCODE`, recompiladas: 159 + 24 funciones), por lo que lo decodifica el código recompilado.
- Middleware por cadenas en la imagen: UE3, FMOD, Bink, Scaleform GFx, PhysX, SpeedTree (indicio débil), Xbox Live.

## Idiomas presentes

| Código | Idioma | Texto (`*_LOC_*.xxx`) | Coalesced | Vídeo | TOC |
|---|---|---|---|---|---|
| INT | Inglés | sí | sí | base | base |
| ESN | Español (España) | sí | sí | disclaimer | sí |
| ESM | Español (Latinoamérica) | sí | sí | disclaimer | sí |
| PTB | Portugués (Brasil) | sí | sí | disclaimer | sí |

Voces: dos bancos FSB5 de 2884 muestras (`875C0317`, `D002C463`, ~30 y ~27 MB) son **candidatos** a bancos de voces por idioma. Hipótesis sin verificar: hace falta seguir en el runtime qué banco carga el juego según el idioma.

## Pendiente (requiere ejecutar el motor)

Localizar en el código recompilado: `appInit`/`UEngine::Init`, el game loop y `MaxTickRate`/frame smoothing, la lectura de idioma (`XGetLanguage`/`XGetLocale` → código UE3), la carga de paquetes y la integración FMOD/Scaleform. Ninguna dirección se fija hasta tener evidencia del runtime.
