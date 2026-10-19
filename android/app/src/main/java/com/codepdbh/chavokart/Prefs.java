package com.codepdbh.chavokart;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.Locale;

/** Launcher settings. The launcher language is independent of the game text and voice languages. */
final class Prefs {
    static final String[] UI_LANGUAGES = {"es", "en", "pt-BR"};

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences("chavokart", Context.MODE_PRIVATE);
    }

    static String uiLanguage(Context context) {
        String saved = get(context).getString("ui_language", "");
        if (!saved.isEmpty()) return saved;
        Locale system = Locale.getDefault();
        if (system.getLanguage().equals("pt")) return "pt-BR";
        if (system.getLanguage().equals("en")) return "en";
        return "es";
    }

    static Locale locale(String tag) {
        return Locale.forLanguageTag(tag);
    }

    /** Wraps a base context so every activity uses the launcher language chosen by the user. */
    static Context localize(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        Locale locale = locale(uiLanguage(base));
        Locale.setDefault(locale);
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
