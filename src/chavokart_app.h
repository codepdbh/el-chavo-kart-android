// El Chavo Kart — Android Evolved
// ReXApp customization. Game-specific hooks live in src/hooks/, UE3 adaptations in src/ue3/; each must cite
// the verified address evidence in analysis/.

#pragma once

#include <fstream>

#include <rex/logging.h>
#include <rex/rex_app.h>

REXCVAR_DECLARE(bool, chavokart_dump_image);

class ChavokartApp : public rex::ReXApp {
 public:
  using rex::ReXApp::ReXApp;

  static std::unique_ptr<rex::ui::WindowedApp> Create(rex::ui::WindowedAppContext& ctx) {
    return std::unique_ptr<ChavokartApp>(new ChavokartApp(ctx, "chavokart", PPCImageConfig));
  }

  void OnPreSetup(rex::RuntimeConfig& config) override { config.gpu_plugin = "xenos"; }

#if REX_PLATFORM_ANDROID
  // Paths come from GameActivity arguments; keep the runtime settings file in the private user folder.
  void OnConfigurePaths(rex::PathConfig& paths) override {
    paths.config_path = paths.user_data_root / "chavokart.toml";
  }
#endif

  void OnPreLaunchModule() override {
    if (!REXCVAR_GET(chavokart_dump_image)) return;
    // Diagnostic copy of the loaded image (relocated, imports bound) for address analysis.
    // Copyrighted game data: it stays in the local cache folder and is never packaged.
    auto path = runtime()->cache_root() / "chavokart-82000000.bin";
    std::ofstream image(path, std::ios::binary);
    image.write(reinterpret_cast<const char*>(runtime()->virtual_membase() + PPCImageConfig.image_base),
                PPCImageConfig.image_size);
    if (image) REXLOG_INFO("[XEX] Loaded image saved to {}", path.string());
    else REXLOG_WARN("[XEX] Could not save diagnostic image {}", path.string());
  }
};
