package com.codepdbh.chavokart;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import android.view.Display;
import android.view.WindowManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Local diagnostic ZIP for "SEND CRASH OR LOG". The user chooses where to save it; nothing is uploaded.
 * Excludes game files, memory dumps and personal data (no accounts, no file listings outside the app).
 */
final class DiagnosticReport {
    private static final long MAX_LOG = 8L * 1024 * 1024;

    static JSONObject device(Context context) throws Exception {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        JSONObject json = new JSONObject()
                .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("device", Build.DEVICE)
                .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
                .put("socManufacturer", Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER : "")
                .put("socModel", Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "")
                .put("hardware", Build.HARDWARE).put("abis", String.join(",", Build.SUPPORTED_ABIS))
                .put("pageSize", Os.sysconf(OsConstants._SC_PAGESIZE))
                .put("cpuCount", Runtime.getRuntime().availableProcessors())
                .put("ramTotal", memory.totalMem).put("ramAvailable", memory.availMem).put("lowRam", memory.lowMemory)
                .put("privateStorageFree", context.getFilesDir().getUsableSpace());
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        JSONArray rates = new JSONArray();
        for (Display.Mode mode : display.getSupportedModes())
            rates.put(mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight() + "@" + Math.round(mode.getRefreshRate()));
        json.put("displayModes", rates);
        return json;
    }

    static JSONObject app(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        GpuDrivers.Driver driver = GpuDrivers.selected(context);
        return new JSONObject().put("package", context.getPackageName()).put("version", info.versionName)
                .put("withGame", BuildConfig.WITH_GAME).put("uiLanguage", Prefs.uiLanguage(context))
                .put("gameImported", GameFiles.installed(context))
                .put("gameLanguages", new JSONArray(GameFiles.languages(GameFiles.gameDir(context))))
                .put("gpuDriver", driver == null ? "system" : driver.name);
    }

    static void write(Context context, Uri destination) throws Exception {
        try (OutputStream raw = context.getContentResolver().openOutputStream(destination);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            if (raw == null) throw new UserError(R.string.game_error_open, destination.toString());
            put(zip, "device.json", device(context).toString(2));
            put(zip, "app.json", app(context).toString(2));
            put(zip, "gpu_probe.json", Prefs.get(context).getString("gpu_probe", "{}"));
            File[] logs = Log.dir(context).listFiles();
            if (logs != null) for (File log : logs) if (log.isFile()) copy(zip, "logs/" + log.getName(), log);
            File native_ = new File(context.getFilesDir(), "diagnostic.log");
            if (native_.isFile()) copy(zip, "logs/" + native_.getName(), native_);
        }
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Copies at most the last MAX_LOG bytes of a log. */
    private static void copy(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, input.length() - MAX_LOG);
            input.seek(start);
            byte[] buffer = new byte[65536];
            int n;
            while ((n = input.read(buffer)) != -1) zip.write(buffer, 0, n);
        }
        zip.closeEntry();
    }
}
