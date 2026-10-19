// Derived from the-simpsons-game-android (codepdbh, GPLv3), commit ed0f1ab. Adapted for El Chavo Kart.
#include "gpu_quirks.h"
GPUQuirks identify_gpu_quirks(const VkPhysicalDeviceProperties&) {
    // No evidence-based device workarounds are needed by the clear-screen renderer.
    return {};
}
