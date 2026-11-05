// Experimental timing control. Zero preserves the SDK's original behavior.
#pragma once
#include <rex/cvar.h>

REXCVAR_DEFINE_INT32(chavokart_guest_vblank_hz, 0, "Chavo Kart/Performance",
                     "Experimental guest vblank frequency, or 0 for the video mode default");
