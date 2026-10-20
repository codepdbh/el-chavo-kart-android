// El Chavo Kart — Android Evolved
// Android SDL entry point. Adapted from ReXGlue's src/ui/windowed_app_main_sdl.cpp (BSD 3-Clause,
// Copyright (c) 2026 Tom Clay) as done by NFS Carbon (android/app/src/main/cpp/carbon_entry.cpp): the shared
// NFSMW SDK selects the app creator "nfsmw" on Android, this project registers "chavokart".

#include <algorithm>
#include <cstdlib>
#include <exception>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include <android/log.h>
#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>
#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/ui/windowed_app.h>
#include <rex/ui/windowed_app_context_sdl.h>

int main(int argc, char* argv[]) {
  try {
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");
    auto remaining = rex::cvar::Init(argc, argv);
    rex::cvar::ApplyEnvironment();
    rex::InitLoggingEarly();

    rex::ui::SDLWindowedAppContext context;
    if (!context.Initialize()) {
      __android_log_print(ANDROID_LOG_ERROR, "ChavoKart", "[ANDROID] SDL context initialization failed");
      return EXIT_FAILURE;
    }
    auto creator = rex::ui::WindowedApp::GetCreator("chavokart");
    if (!creator) {
      REXLOG_ERROR("[ANDROID] App creator 'chavokart' was not registered");
      return EXIT_FAILURE;
    }
    auto app = creator(context);
    const auto& options = app->GetPositionalOptions();
    std::map<std::string, std::string> parsed;
    for (size_t i = 0; i < std::min(remaining.size(), options.size()); ++i) {
      parsed[options[i]] = remaining[i];
    }
    app->SetParsedArguments(std::move(parsed));
    int result = app->OnInitialize() ? context.RunMainMessageLoop() : EXIT_FAILURE;
    app->InvokeOnDestroy();
    return result;
  } catch (const std::exception& error) {
    __android_log_print(ANDROID_LOG_ERROR, "ChavoKart", "[ANDROID] Initialization failed: %s", error.what());
    return EXIT_FAILURE;
  }
}
