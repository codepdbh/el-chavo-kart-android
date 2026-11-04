// The game's reversed-Z depth prepass and color pass can disagree by a few
// float32 ULPs on Adreno, causing the color pass to fail GREATER_OR_EQUAL.
#pragma once
#include <rex/cvar.h>

#if defined(__ANDROID__)
constexpr int32_t kChavokartDefaultDepthBias = 16;
#else
constexpr int32_t kChavokartDefaultDepthBias = 0;
#endif
REXCVAR_DEFINE_INT32(chavokart_depth_bias_units, kChavokartDefaultDepthBias, "Chavo Kart/Graphics",
                     "Adreno reversed-Z color-pass bias in Vulkan units (0 disables)");
