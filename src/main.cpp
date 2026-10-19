// El Chavo Kart — Android Evolved
// ReXGlue recompiled project entry point.

#include "generated/default/chavokart_init.h"

#include "chavokart_app.h"

REXCVAR_DEFINE_BOOL(chavokart_dump_image, false, "Chavo Kart diagnostics",
                    "Save the loaded executable image locally for address analysis");

REX_DEFINE_APP(chavokart, ChavokartApp::Create)
