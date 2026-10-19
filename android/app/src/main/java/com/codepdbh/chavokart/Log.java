package com.codepdbh.chavokart;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Launcher log with the project categories ([XEX], [GPU], [VULKAN], [ANDROID], [LANGUAGE]…). Written to logcat
 * (tag ChavoKart) and to files/logs/launcher.log, which "SEND CRASH OR LOG" packs.
 */
final class Log {
    private static File file;
    private static final long MAX_SIZE = 2L * 1024 * 1024;

    static synchronized void init(Context context) {
        File dir = new File(context.getFilesDir(), "logs");
        dir.mkdirs();
        file = new File(dir, "launcher.log");
        if (file.length() > MAX_SIZE) {
            File old = new File(dir, "launcher.1.log");
            old.delete();
            file.renameTo(old);
            file = new File(dir, "launcher.log");
        }
    }

    static synchronized void i(String category, String message) {
        android.util.Log.i("ChavoKart", "[" + category + "] " + message);
        if (file == null) return;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8")) {
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " [" + category + "] " + message + "\n");
        } catch (IOException ignored) {
            // Logging must never break the launcher.
        }
    }

    static File dir(Context context) {
        return new File(context.getFilesDir(), "logs");
    }
}
