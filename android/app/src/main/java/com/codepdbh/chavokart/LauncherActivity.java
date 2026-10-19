package com.codepdbh.chavokart;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Landscape launcher. Every visible option is backed by a working implementation; nothing is simulated. */
public class LauncherActivity extends LocalizedActivity {
    private static final int SELECT_TREE = 10, DRIVER_ZIP = 11, GPU_PROBE = 12, SAVE_REPORT = 13;
    private static final int YELLOW = 0xffffc93c, GREEN = 0xff3fb950, RED = 0xffe5484d, TEXT = 0xfff2f2f2,
            MUTED = 0xffb3b8c2, CARD = 0xe6182230, BUTTON = 0xff2a3547;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelImport = new AtomicBoolean();
    private volatile boolean busy;
    private TextView status, progressText;
    private ProgressBar progress;
    private Button play, importButton;
    private LinearLayout options;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Log.init(this);
        Log.i("ANDROID", "Launcher " + Build.MODEL + " Android " + Build.VERSION.RELEASE + " withGame=" + BuildConfig.WITH_GAME);
        LinearLayout columns = new LinearLayout(this);
        columns.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff0d2a3a, 0xff123d2c, 0xff3a2a0d}));
        columns.setOnApplyWindowInsetsListener((v, insets) -> {
            int l = insets.getSystemWindowInsetLeft(), t = insets.getSystemWindowInsetTop();
            int r = insets.getSystemWindowInsetRight(), b = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                l = Math.max(l, insets.getDisplayCutout().getSafeInsetLeft());
                r = Math.max(r, insets.getDisplayCutout().getSafeInsetRight());
            }
            v.setPadding(l + dp(20), t + dp(12), r + dp(20), b + dp(12));
            return insets;
        });

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        ScrollView leftScroll = new ScrollView(this);
        leftScroll.addView(left);
        columns.addView(leftScroll, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        icon.setBackground(round(0xff1d6fb8, 18));
        identity.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, 0, 0);
        TextView title = label(getString(R.string.launcher_title), 24, YELLOW);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        names.addView(title);
        TextView subtitle = label(getString(R.string.launcher_subtitle), 13, GREEN);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setLetterSpacing(0.25f);
        names.addView(subtitle);
        identity.addView(names);
        left.addView(identity);
        left.addView(label(getString(R.string.tagline), 12, MUTED));
        left.addView(label(getString(BuildConfig.WITH_GAME ? R.string.build_game : R.string.build_diagnostic), 12,
                BuildConfig.WITH_GAME ? GREEN : YELLOW));

        LinearLayout files = card();
        left.addView(files);
        files.addView(heading(R.string.section_files));
        status = label("", 13, TEXT);
        files.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setVisibility(View.GONE);
        files.addView(progress);
        progressText = label("", 12, MUTED);
        progressText.setVisibility(View.GONE);
        files.addView(progressText);

        play = button(left, getString(R.string.btn_play), this::play);
        play.setTextSize(20);
        play.setTypeface(Typeface.DEFAULT_BOLD);
        importButton = button(left, getString(R.string.btn_import), this::importOrCancel);

        LinearLayout right = card();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -1, 1.15f);
        rightParams.leftMargin = dp(16);
        ScrollView scroll = new ScrollView(this);
        options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(options);
        right.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        columns.addView(right, rightParams);
        setContentView(columns);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        if (!busy && status != null) refresh();
    }

    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    // ---------------------------------------------------------------- state

    private void refresh() {
        boolean installed = GameFiles.installed(this);
        StringBuilder text = new StringBuilder(getString(installed ? R.string.files_ok : R.string.files_none));
        if (installed) {
            List<String> langs = GameFiles.languages(GameFiles.gameDir(this));
            text.append('\n').append(getString(R.string.files_languages, languageNames(langs)));
        }
        text.append('\n').append(gamepads());
        status.setText(text);
        play.setEnabled(!busy && installed && BuildConfig.WITH_GAME);
        play.setBackground(round(play.isEnabled() ? YELLOW : 0xff5a5f68, 14));
        play.setTextColor(play.isEnabled() ? 0xff1a1a1a : 0xffc9ccd1);
        importButton.setEnabled(true);
        importButton.setText(busy ? R.string.btn_cancel_import : R.string.btn_import);
        refreshOptions();
    }

    private String gamepads() {
        List<String> pads = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                pads.add(device.getName());
        }
        return pads.isEmpty() ? getString(R.string.status_gamepad_none) : getString(R.string.status_gamepad, String.join(", ", pads));
    }

    private String languageNames(List<String> codes) {
        List<String> names = new ArrayList<>();
        for (String code : codes) {
            int id = getResources().getIdentifier("game_lang_" + code, "string", getPackageName());
            names.add(id != 0 ? getString(id) : code);
        }
        return String.join(", ", names);
    }

    private void refreshOptions() {
        options.removeAllViews();
        options.addView(heading(R.string.section_language));
        String[] tags = Prefs.UI_LANGUAGES;
        String[] labels = {getString(R.string.lang_es), getString(R.string.lang_en), getString(R.string.lang_pt_br)};
        int current = 0;
        for (int i = 0; i < tags.length; i++) if (tags[i].equals(Prefs.uiLanguage(this))) current = i;
        final int checked = current;
        button(options, getString(R.string.ui_language) + "\n" + labels[current], () ->
                new AlertDialog.Builder(this).setTitle(R.string.ui_language)
                        .setSingleChoiceItems(labels, checked, (d, which) -> {
                            d.dismiss();
                            if (which == checked) return;
                            Prefs.get(this).edit().putString("ui_language", tags[which]).apply();
                            Log.i("LANGUAGE", "Launcher language " + tags[which]);
                            recreate();
                        }).setNegativeButton(R.string.dialog_cancel, null).show());
        List<String> langs = GameFiles.installed(this) ? GameFiles.languages(GameFiles.gameDir(this)) : new ArrayList<>();
        options.addView(label(getString(R.string.game_text_language) + ": " + (langs.isEmpty() ?
                getString(R.string.game_text_unknown) : getString(R.string.game_text_pending, languageNames(langs))), 12, MUTED));
        options.addView(label(getString(R.string.voice_language) + ": " + getString(R.string.voice_pending), 12, MUTED));

        options.addView(heading(R.string.section_driver));
        GpuDrivers.Driver driver = GpuDrivers.selected(this);
        options.addView(label(getString(R.string.driver_current, driver == null ? getString(R.string.driver_system) : driver.name), 13, TEXT));
        button(options, getString(R.string.driver_system), this::restoreDriver);
        button(options, getString(R.string.driver_import), () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, DRIVER_ZIP);
        }).setEnabled(!busy);
        button(options, getString(R.string.driver_select), this::selectDriver);
        button(options, getString(R.string.driver_test), this::testDriver).setEnabled(!busy);
        button(options, getString(R.string.driver_info), this::driverInfo);
        button(options, getString(R.string.driver_restore), this::restoreDriver);
        button(options, getString(R.string.driver_delete), this::deleteDriver).setEnabled(!busy);
        options.addView(label(getString(R.string.driver_note), 11, MUTED));

        options.addView(heading(R.string.section_diagnostics));
        button(options, getString(R.string.btn_gpu_probe), this::runProbe).setEnabled(!busy);
        button(options, getString(R.string.btn_vulkan_test), () -> startActivity(new Intent(this, VulkanTestActivity.class)));
        button(options, getString(R.string.btn_device_info), this::deviceInfo);
        button(options, getString(R.string.btn_view_log), this::viewLog);
        button(options, getString(R.string.btn_export_report), () -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "chavokart-diagnostic.zip");
            startActivityForResult(intent, SAVE_REPORT);
        });
        button(options, getString(R.string.btn_clear_cache), this::clearCache).setEnabled(!busy);
        button(options, getString(R.string.btn_credits), () -> message(R.string.btn_credits, getString(R.string.credits_text)));
    }

    // ---------------------------------------------------------------- game

    private void play() {
        if (!BuildConfig.WITH_GAME) { message(R.string.btn_play, getString(R.string.play_unavailable_build)); return; }
        if (!GameFiles.installed(this)) { message(R.string.btn_play, getString(R.string.play_unavailable_files)); return; }
        busy = true;
        refresh();
        status.setText(R.string.files_checking);
        worker.execute(() -> {
            try {
                GameFiles.verify(GameFiles.gameDir(this));
                runOnUiThread(() -> {
                    busy = false;
                    refresh();
                    startActivity(new Intent().setClassName(this, getPackageName() + ".GameActivity"));
                });
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    private void importOrCancel() {
        if (busy) { cancelImport.set(true); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        Uri.parse("content://com.android.externalstorage.documents/document/primary%3AELCHAVOKART"));
        startActivityForResult(intent, SELECT_TREE);
    }

    private void importTree(Uri tree) {
        busy = true;
        cancelImport.set(false);
        refresh();
        status.setText(R.string.files_scanning);
        progress.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        progressText.setText("");
        final long[] lastUpdate = {0};
        worker.execute(() -> {
            try {
                GameFiles.importTree(this, tree, cancelImport, (done, total) -> {
                    long now = System.nanoTime();
                    if (now - lastUpdate[0] < 100_000_000L && done < total) return;
                    lastUpdate[0] = now;
                    int permille = total == 0 ? 1000 : (int) (done * 1000 / total);
                    String text = getString(R.string.files_progress, Formatter.formatShortFileSize(this, done),
                            Formatter.formatShortFileSize(this, total), permille / 10);
                    runOnUiThread(() -> { progress.setProgress(permille); progressText.setText(text); });
                });
                runOnUiThread(() -> { endImport(); status.setText(getString(R.string.files_done) + "\n" + status.getText()); });
            } catch (java.io.InterruptedIOException cancelled) {
                Log.i("ANDROID", "Import cancelled by the user");
                runOnUiThread(() -> { endImport(); toast(R.string.files_cancelled); });
            } catch (Exception e) {
                Log.i("ANDROID", "Import failed: " + e);
                runOnUiThread(() -> { endImport(); message(R.string.btn_import, UserError.describe(this, e)); });
            }
        });
    }

    private void endImport() {
        busy = false;
        progress.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
        refresh();
    }

    // ---------------------------------------------------------------- drivers

    private void selectDriver() {
        List<GpuDrivers.Driver> drivers = GpuDrivers.list(this);
        if (drivers.isEmpty()) { toast(R.string.driver_none_imported); return; }
        String[] names = new String[drivers.size()];
        for (int i = 0; i < names.length; i++) names[i] = drivers.get(i).name;
        new AlertDialog.Builder(this).setTitle(R.string.driver_select).setItems(names, (d, which) -> {
            GpuDrivers.select(this, drivers.get(which).id);
            Log.i("VULKAN", "Selected imported driver " + drivers.get(which).name);
            refresh();
        }).setNegativeButton(R.string.dialog_cancel, null).show();
    }

    private void restoreDriver() {
        GpuDrivers.select(this, "");
        Log.i("VULKAN", "System driver restored");
        toast(R.string.driver_restored);
        refresh();
    }

    private void testDriver() {
        if (GpuDrivers.selected(this) != null) { message(R.string.driver_test, getString(R.string.driver_test_needs_runtime)); return; }
        runProbe();
    }

    private void driverInfo() {
        GpuDrivers.Driver driver = GpuDrivers.selected(this);
        if (driver == null) { runProbe(); return; }
        message(R.string.driver_info, getString(R.string.driver_info_text, driver.name, driver.library, driver.description));
    }

    private void deleteDriver() {
        List<GpuDrivers.Driver> drivers = GpuDrivers.list(this);
        if (drivers.isEmpty()) { toast(R.string.driver_none_imported); return; }
        String[] names = new String[drivers.size()];
        for (int i = 0; i < names.length; i++) names[i] = drivers.get(i).name;
        new AlertDialog.Builder(this).setTitle(R.string.driver_delete).setItems(names, (d, which) -> {
            GpuDrivers.Driver driver = drivers.get(which);
            new AlertDialog.Builder(this).setMessage(getString(R.string.dialog_confirm_delete, driver.name))
                    .setPositiveButton(R.string.dialog_ok, (d2, w2) -> {
                        try {
                            GpuDrivers.remove(this, driver);
                            Log.i("VULKAN", "Deleted driver " + driver.name);
                            toast(getString(R.string.driver_deleted, driver.name));
                        } catch (Exception e) {
                            message(R.string.driver_delete, UserError.describe(this, e));
                        }
                        refresh();
                    }).setNegativeButton(R.string.dialog_cancel, null).show();
        }).setNegativeButton(R.string.dialog_cancel, null).show();
    }

    // ---------------------------------------------------------------- diagnostics

    private void runProbe() {
        toast(R.string.probe_running);
        startActivityForResult(new Intent(this, GpuProbeActivity.class), GPU_PROBE);
    }

    private void showProbe(String report) {
        try {
            JSONObject json = new JSONObject(report);
            if (json.has("probeError")) { message(R.string.probe_title, json.getString("probeError")); return; }
            JSONObject gpu = json.getJSONArray("devices").getJSONObject(0);
            JSONObject limits = gpu.getJSONObject("limits"), features = gpu.getJSONObject("features");
            JSONObject indexing = gpu.getJSONObject("descriptorIndexing"), formats = gpu.getJSONObject("sampledFormats");
            JSONArray missing = gpu.getJSONArray("missing");
            String verdict = gpu.getBoolean("baselineCompatible") ? getString(R.string.probe_baseline_ok)
                    : getString(R.string.probe_baseline_missing, missing.join(", "));
            String driver = gpu.optString("driverName", "") + " " + gpu.optString("driverInfo", "");
            message(R.string.probe_title, getString(R.string.probe_summary, gpu.getString("name"), gpu.getString("gpuFamily"),
                    gpu.getString("apiVersion"), driver.trim().isEmpty() ? String.valueOf(gpu.getLong("driverVersionRaw")) : driver.trim(),
                    gpu.getString("vendorId"), gpu.getString("deviceId"), limits.getLong("maxBoundDescriptorSets"),
                    indexing.getBoolean("available") ? "runtimeArray=" + indexing.optBoolean("runtimeDescriptorArray") +
                            " partiallyBound=" + indexing.optBoolean("descriptorBindingPartiallyBound") : "no",
                    formats.getString("BC1"), formats.getString("BC3"), formats.getString("BC5"),
                    features.getBoolean("shaderInt64"), verdict));
        } catch (Exception e) {
            message(R.string.probe_title, report);
        }
    }

    private void deviceInfo() {
        try {
            message(R.string.device_info_title, DiagnosticReport.device(this).toString(2));
        } catch (Exception e) {
            message(R.string.device_info_title, UserError.describe(this, e));
        }
    }

    private void viewLog() {
        StringBuilder text = new StringBuilder();
        for (File f : new File[]{new File(Log.dir(this), "launcher.log"), new File(getFilesDir(), "diagnostic.log")}) {
            if (!f.isFile()) continue;
            try {
                byte[] data = Files.readAllBytes(f.toPath());
                int start = Math.max(0, data.length - 12000);
                text.append("== ").append(f.getName()).append(" ==\n")
                        .append(new String(data, start, data.length - start, StandardCharsets.UTF_8)).append('\n');
            } catch (Exception ignored) {
                // Unreadable log: skip.
            }
        }
        message(R.string.log_title, text.length() == 0 ? getString(R.string.log_empty) : text.toString());
    }

    private void clearCache() {
        long freed = GameFiles.remove(getCacheDir()) + GameFiles.remove(new File(getFilesDir(), "shader_cache"));
        getCacheDir().mkdirs();
        Log.i("ANDROID", "Cache cleared: " + freed + " bytes");
        toast(getString(R.string.cache_cleared, Formatter.formatShortFileSize(this, freed)));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == GPU_PROBE) {
            if (result == RESULT_OK && data != null && data.getStringExtra("gpu_report") != null) {
                String report = data.getStringExtra("gpu_report");
                Prefs.get(this).edit().putString("gpu_probe", report).apply();
                Log.i("GPU", "Probe: " + report);
                showProbe(report);
            } else {
                Log.i("GPU", "Probe process ended without a result");
                message(R.string.probe_title, getString(R.string.probe_crashed));
            }
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == SELECT_TREE) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                message(R.string.btn_import, getString(R.string.game_error_permission));
                return;
            }
            importTree(uri);
        } else if (request == DRIVER_ZIP) {
            busy = true;
            refresh();
            worker.execute(() -> {
                try {
                    GpuDrivers.Driver driver = GpuDrivers.importZip(this, uri);
                    GpuDrivers.select(this, driver.id);
                    Log.i("VULKAN", "Imported driver " + driver.name + " (" + driver.library + ")");
                    runOnUiThread(() -> { busy = false; refresh(); message(R.string.driver_import, getString(R.string.driver_imported, driver.name)); });
                } catch (Exception e) {
                    Log.i("VULKAN", "Driver import rejected: " + e);
                    fail(e);
                }
            });
        } else if (request == SAVE_REPORT) {
            worker.execute(() -> {
                try {
                    DiagnosticReport.write(this, uri);
                    runOnUiThread(() -> message(R.string.btn_export_report, getString(R.string.report_saved) + "\n\n" + getString(R.string.report_contents)));
                } catch (Exception e) {
                    fail(e);
                }
            });
        }
    }

    // ---------------------------------------------------------------- UI helpers

    private void fail(Exception e) {
        runOnUiThread(() -> { busy = false; refresh(); message(R.string.app_name, UserError.describe(this, e)); });
    }

    private void message(int title, String text) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton(R.string.dialog_ok, null).show();
    }

    private void toast(int id) { Toast.makeText(this, id, Toast.LENGTH_SHORT).show(); }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private Button button(LinearLayout layout, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setBackground(round(BUTTON, 12));
        b.setMinHeight(dp(48));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        layout.addView(b, params);
        return b;
    }

    private TextView heading(int id) {
        TextView t = label(getString(id), 13, YELLOW);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(10), dp(16), dp(12));
        c.setBackground(round(CARD, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        c.setLayoutParams(p);
        return c;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
