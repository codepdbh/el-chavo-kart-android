package com.codepdbh.chavokart;

/**
 * Standalone Vulkan 1.1 diagnostic (clear + present loop with SDL3 lifecycle handling). It does not run the game,
 * so it works even while the recompiled runtime cannot start yet.
 */
public class VulkanTestActivity extends LandscapeSDLActivity {
    @Override protected String[] getLibraries() { return new String[] { "SDL3", "ckdiag" }; }
}
