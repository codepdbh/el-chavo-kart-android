package com.codepdbh.chavokart;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

/**
 * Game options passed to the runtime as cvars. Only options whose cvar exists in the pinned SDK and has an
 * effect are listed (see docs/graphics.md and docs/localization.md).
 */
final class GameOptions {
    static final class Option {
        final String key;
        final int title;
        final String[] values;
        final int[] labels;
        final String defaultValue;

        Option(String key, int title, String defaultValue, String[] values, int[] labels) {
            this.key = key; this.title = title; this.defaultValue = defaultValue; this.values = values; this.labels = labels;
        }

        int label(String value) {
            for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return labels[i];
            return labels[0];
        }
    }

    /**
     * UE3 language of El Chavo Kart (appGetLanguageExt at 0x829E58B0): XGetLanguage 1 -> INT, 9 -> PTB,
     * 5 -> ESN when XGetLocale is Spain (0x1F), otherwise ESM. XGetLocale maps XCONFIG_USER_COUNTRY with the
     * table at 0x83BC4168 (31 -> Spain, 71 -> Mexico). Voices and movie audio tracks follow the same language.
     */
    static final String[][] LANGUAGES = {
            // code, user_language, user_country
            {"INT", "1", "103"}, {"ESM", "5", "71"}, {"ESN", "5", "31"}, {"PTB", "9", "13"},
    };

    static final Option LANGUAGE = new Option("game_language", R.string.opt_game_language, "auto",
            new String[]{"auto", "INT", "ESM", "ESN", "PTB"},
            new int[]{R.string.opt_language_auto, R.string.game_lang_INT, R.string.game_lang_ESM, R.string.game_lang_ESN,
                    R.string.game_lang_PTB});
    static final Option ASYNC = new Option("async_shaders", R.string.opt_shaders, "true",
            new String[]{"true", "false"}, new int[]{R.string.opt_shaders_async, R.string.opt_shaders_sync});
    static final Option CPU = new Option("fast_cores", R.string.opt_cpu, "true",
            new String[]{"true", "false"}, new int[]{R.string.opt_cpu_fast, R.string.opt_cpu_system});
    static final Option VSYNC = new Option("vsync", R.string.opt_vsync, "true",
            new String[]{"true", "false"}, new int[]{R.string.opt_on, R.string.opt_off});
    static final Option FRAME_RATE = new Option("frame_rate", R.string.opt_frame_rate, "0",
            new String[]{"0", "120"}, new int[]{R.string.opt_frame_original, R.string.opt_frame_60});
    static final Option LOGS = new Option("logs", R.string.opt_logs, "warn",
            new String[]{"warn", "info"}, new int[]{R.string.opt_logs_warn, R.string.opt_logs_info});
    /** Launcher-side option: counts the guest XE_SWAP packets, i.e. the frames the game really submits. */
    static final Option FPS_OVERLAY = new Option("fps_overlay", R.string.opt_fps_counter, "true",
            new String[]{"true", "false"}, new int[]{R.string.opt_on, R.string.opt_off});
    static final Option[] ALL = {LANGUAGE, ASYNC, CPU, VSYNC, FRAME_RATE, LOGS, FPS_OVERLAY};

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("chavokart_game", Context.MODE_PRIVATE);
    }

    static String get(Context context, Option option) {
        String value = prefs(context).getString(option.key, option.defaultValue);
        for (String allowed : option.values) if (allowed.equals(value)) return value;
        return option.defaultValue;
    }

    static void set(Context context, Option option, String value) {
        // commit(): the game runs in another process and reads these preferences when it starts.
        prefs(context).edit().putString(option.key, value).commit();
    }

    /** The UE3 language that will be used: the chosen one if present in the copy, else one matching the launcher. */
    static String effectiveLanguage(Context context, List<String> available) {
        String chosen = get(context, LANGUAGE);
        if (!"auto".equals(chosen) && available.contains(chosen)) return chosen;
        String ui = Prefs.uiLanguage(context);
        String[] order = ui.startsWith("pt") ? new String[]{"PTB", "ESM", "ESN", "INT"}
                : ui.startsWith("en") ? new String[]{"INT", "ESM", "PTB", "ESN"}
                : new String[]{"ESM", "ESN", "INT", "PTB"};
        for (String code : order) if (available.contains(code)) return code;
        return "INT";
    }

    static void arguments(Context context, List<String> args) {
        // Xenos on Android Vulkan drivers, as measured by NFS Carbon on the Galaxy S25 (Adreno 830): classic render
        // passes (dynamic rendering cost ~40 % of the GPU command thread), host framebuffers, ordinary descriptor
        // sets, and no hard requirement on geometry shaders or non-solid fill.
        args.add("--vulkan_dynamic_rendering=false");
        args.add("--render_target_path_vulkan=fbo");
        args.add("--vulkan_native_shader_features=false");
        args.add("--vulkan_require_geometry_shader=false");
        args.add("--vulkan_require_fill_mode_non_solid=false");
        args.add("--vulkan_async_skip_incomplete_frames=true");
        args.add("--async_shader_compilation=" + get(context, ASYNC));
        args.add("--vsync=" + get(context, VSYNC));
        args.add("--chavokart_guest_vblank_hz=" + get(context, FRAME_RATE));
        args.add("--chavokart_fast_cores=" + get(context, CPU));
        args.add("--log_level=" + get(context, LOGS));
        String language = effectiveLanguage(context, GameFiles.languages(GameFiles.gameDir(context)));
        for (String[] l : LANGUAGES) {
            if (l[0].equals(language)) {
                args.add("--user_language=" + l[1]);
                args.add("--user_country=" + l[2]);
            }
        }
    }

    private GameOptions() { }
}
