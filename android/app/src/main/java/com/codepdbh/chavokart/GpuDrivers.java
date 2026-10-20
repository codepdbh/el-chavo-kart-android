// Derived from nfsmw-android (codepdbh, GPLv3) com.nfsmw.android.GpuDrivers, commit 507a63f.
package com.codepdbh.chavokart;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * User-provided Vulkan driver ZIPs (adrenotools layout: meta.json + ARM64 .so). Drivers only affect this app's
 * game process; nothing is downloaded and the system driver is never modified.
 */
final class GpuDrivers {
    static final class Driver {
        final String id, name, library, description;
        final File directory;

        Driver(String id, String name, File directory, String library, String description) {
            this.id = id; this.name = name; this.directory = directory; this.library = library;
            this.description = description;
        }
    }

    private static File root(Context context) {
        File root = new File(context.getFilesDir(), "gpu_drivers");
        root.mkdirs();
        return root;
    }

    static List<Driver> list(Context context) {
        List<Driver> result = new ArrayList<>();
        File[] dirs = root(context).listFiles(File::isDirectory);
        if (dirs != null) for (File dir : dirs) {
            try {
                if (dir.getName().startsWith(".")) continue;
                JSONObject meta = new JSONObject(new String(Files.readAllBytes(new File(dir, "driver.json").toPath()), StandardCharsets.UTF_8));
                String library = meta.getString("libraryName");
                if (!library.matches("[A-Za-z0-9_.+-]+\\.so")) continue;
                File loadDir = new File(dir, meta.getString("folder"));
                if (!loadDir.getCanonicalPath().startsWith(dir.getCanonicalPath() + "/")) continue;
                if (new File(loadDir, library).isFile())
                    result.add(new Driver(dir.getName(), meta.getString("name"), loadDir, library, meta.optString("description")));
            } catch (Exception ignored) {
                // A damaged record is skipped; the system driver stays available.
            }
        }
        result.sort(Comparator.comparing(d -> d.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    static String selectedId(Context context) {
        return Prefs.get(context).getString("gpu_driver", "");
    }

    static Driver selected(Context context) {
        String id = selectedId(context);
        for (Driver driver : list(context)) if (driver.id.equals(id)) return driver;
        return null;
    }

    static void select(Context context, String id) {
        Prefs.get(context).edit().putString("gpu_driver", id).apply();
    }

    /** GPU family from the last isolated probe (vendor ID), or null when the probe has not run. */
    static String probedFamily(Context context) {
        try {
            JSONObject probe = new JSONObject(Prefs.get(context).getString("gpu_probe", "{}"));
            return probe.getJSONArray("devices").getJSONObject(0).getString("gpuFamily");
        } catch (Exception e) {
            return null;
        }
    }

    static Driver importZip(Context context, Uri uri) throws Exception {
        if (Build.VERSION.SDK_INT < 28) throw new UserError(R.string.driver_error_android_version);
        String family = probedFamily(context);
        if (family == null) throw new UserError(R.string.driver_error_probe_first);
        File stage = new File(root(context), ".import-" + UUID.randomUUID());
        if (!stage.mkdirs()) throw new UserError(R.string.error_create_folder);
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new UserError(R.string.driver_error_open_zip);
                DriverArchive.extract(input, stage);
            }
            File packageRoot = findMeta(stage);
            if (packageRoot == null) throw new UserError(R.string.driver_error_no_meta);
            JSONObject meta = new JSONObject(new String(Files.readAllBytes(new File(packageRoot, "meta.json").toPath()), StandardCharsets.UTF_8));
            String library = meta.getString("libraryName");
            if (!library.matches("[A-Za-z0-9_.+-]+\\.so")) throw new UserError(R.string.driver_error_bad_meta);
            if (meta.optInt("minApi", 26) > Build.VERSION.SDK_INT) throw new UserError(R.string.driver_error_android_version);
            DriverArchive.validateLibrary(new File(packageRoot, library));
            String name = meta.optString("name", library);
            if (name.trim().isEmpty() || name.length() > 120) throw new UserError(R.string.driver_error_bad_meta);
            String text = (name + " " + library + " " + meta.optString("description")).toLowerCase(Locale.ROOT);
            // Drivers are not interchangeable between GPU families.
            if ((text.contains("turnip") || text.contains("freedreno")) && !family.startsWith("Adreno"))
                throw new UserError(R.string.driver_error_turnip_family, family);
            if ((text.contains("panvk") || text.contains("panfrost")) && !family.startsWith("Mali"))
                throw new UserError(R.string.driver_error_panvk_family, family);
            if (family.startsWith("Xclipse")) throw new UserError(R.string.driver_error_xclipse);
            String relative = "payload";
            File installed = new File(root(context), UUID.randomUUID().toString());
            if (!installed.mkdirs()) throw new UserError(R.string.error_create_folder);
            try {
                if (!packageRoot.renameTo(new File(installed, relative))) throw new UserError(R.string.error_create_folder);
                JSONObject record = new JSONObject().put("name", name).put("libraryName", library).put("folder", relative)
                        .put("description", meta.optString("description")).put("importedFor", family);
                Files.write(new File(installed, "driver.json").toPath(), record.toString().getBytes(StandardCharsets.UTF_8));
                readOnly(new File(installed, relative));
            } catch (Exception error) {
                DriverArchive.delete(installed);
                throw error;
            }
            return new Driver(installed.getName(), name, new File(installed, relative), library, meta.optString("description"));
        } finally {
            if (stage.exists()) DriverArchive.delete(stage);
        }
    }

    private static File findMeta(File root) throws IOException {
        if (new File(root, "meta.json").isFile()) return root;
        File found = null;
        File[] children = root.listFiles(File::isDirectory);
        if (children != null) for (File child : children) {
            File candidate = findMeta(child);
            if (candidate != null) {
                if (found != null) throw new UserError(R.string.driver_error_ambiguous);
                found = candidate;
            }
        }
        return found;
    }

    private static void readOnly(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) readOnly(child);
        else if (!file.setReadOnly()) throw new UserError(R.string.error_create_folder);
    }

    /** Runtime options of the SDK's Android driver loader (rex_android_gpu_driver); empty name = system driver. */
    static void arguments(Context context, List<String> args) {
        Driver driver = selected(context);
        args.add("--android_native_lib_dir=" + context.getApplicationInfo().nativeLibraryDir);
        args.add("--android_tmp_dir=" + context.getCacheDir().getAbsolutePath());
        args.add("--android_gpu_driver_dir=" + (driver == null ? "" : driver.directory.getAbsolutePath() + "/"));
        args.add("--android_gpu_driver_name=" + (driver == null ? "" : driver.library));
    }

    static void remove(Context context, Driver driver) throws IOException {
        File target = new File(root(context), driver.id);
        if (!target.getCanonicalFile().getParentFile().equals(root(context).getCanonicalFile()))
            throw new UserError(R.string.driver_error_bad_path);
        DriverArchive.delete(target);
        if (selectedId(context).equals(driver.id)) select(context, "");
    }
}
