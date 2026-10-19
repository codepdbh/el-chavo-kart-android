// Derived from the-simpsons-game-android (codepdbh, GPLv3), commit ed0f1ab. Adapted for El Chavo Kart.
#pragma once
#include <vulkan/vulkan.h>
struct GPUQuirks { bool forceSafeResolvePath = false; };
GPUQuirks identify_gpu_quirks(const VkPhysicalDeviceProperties& properties);
