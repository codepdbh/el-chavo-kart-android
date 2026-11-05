// Derived from nfsmw-android (codepdbh, GPLv3) com.nfsmw.android.GameActivity, commit 507a63f: touch pad host,
// physical gamepads, tilt steering and the layout editor. Localized and adapted to El Chavo Kart.
package com.codepdbh.chavokart;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import java.io.File;

/** Runs the recompiled game (libmain.so) in the isolated ":game" process with the virtual pad on top. */
public class GameActivity extends LandscapeSDLActivity
        implements TouchControlsView.Host, InputManager.InputDeviceListener, SensorEventListener {
    static final String EXTRA_ARGS = "com.codepdbh.chavokart.ARGS";
    static final String EXTRA_FPS = "com.codepdbh.chavokart.FPS";

    /** Guest frames submitted so far (XE_SWAP packets counted in rexgpu-xenos). */
    private static native long nativeGuestFrames();

    private TextView fpsView;
    private final android.os.Handler fpsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long fpsLastFrames = -1, fpsLastTime;
    private final Runnable fpsTick = new Runnable() {
        @Override public void run() {
            try {
                long frames = nativeGuestFrames(), now = System.nanoTime();
                if (fpsLastFrames >= 0 && now > fpsLastTime) {
                    double fps = (frames - fpsLastFrames) * 1e9 / (now - fpsLastTime);
                    fpsView.setText(getString(R.string.fps_guest_format, fps));
                }
                fpsLastFrames = frames;
                fpsLastTime = now;
            } catch (UnsatisfiedLinkError notLoadedYet) {
                // libmain.so is loaded by SDL a moment after onCreate.
            }
            fpsHandler.postDelayed(this, 1000);
        }
    };

    private TouchControlsView controls;
    private LinearLayout toolbar;
    private InputManager inputManager;
    private SensorManager sensorManager;
    private Sensor gravity;

    @Override protected void onCreate(Bundle state) {
        Log.init(this);
        File user = new File(getFilesDir(), "user");
        user.mkdirs();
        new File(getFilesDir(), "cache").mkdirs();
        try {
            // The runtime keeps its settings file and pipeline cache in the "executable folder".
            Os.setenv("REX_APP_FOLDER", user.getAbsolutePath(), true);
        } catch (ErrnoException e) {
            throw new IllegalStateException("REX_APP_FOLDER", e);
        }
        super.onCreate(state);
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        if (Build.VERSION.SDK_INT >= 28) {
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setAttributes(attrs);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ViewGroup root = findViewById(android.R.id.content);
        controls = new TouchControlsView(this);
        controls.setHost(this);
        root.addView(controls, new FrameLayout.LayoutParams(-1, -1, Gravity.FILL));
        toolbar = buildToolbar();
        toolbar.setVisibility(View.GONE);
        root.addView(toolbar, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, null);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (gravity == null) gravity = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_FPS, false)) {
            fpsView = new TextView(this);
            fpsView.setTextColor(0xFFFFFFFF);
            fpsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            fpsView.setShadowLayer(3, 0, 1, 0xFF000000);
            fpsView.setPadding(dp(8), dp(4), dp(8), dp(4));
            fpsView.setBackgroundColor(0x66000000);
            root.addView(fpsView, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            fpsHandler.postDelayed(fpsTick, 1000);
        }
        refreshGamepads();
        Log.i("ANDROID", "Game activity started; args " + String.join(" ", getArguments()));
    }

    @Override protected void onResume() {
        super.onResume();
        updateTiltListener();
    }

    @Override protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (controls != null) controls.save();
        super.onPause();
    }

    @Override protected void onDestroy() {
        fpsHandler.removeCallbacks(fpsTick);
        inputManager.unregisterInputDeviceListener(this);
        super.onDestroy();
        // SDL's native main runs once per process: end the ":game" process so the next start is clean and reads
        // the launcher's current settings.
        if (isFinishing()) android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override protected String[] getLibraries() { return new String[] {"SDL3", "main"}; }

    @Override protected String[] getArguments() {
        String[] args = getIntent() == null ? null : getIntent().getStringArrayExtra(EXTRA_ARGS);
        return args != null ? args : new String[0];
    }

    // ---- Physical gamepads ---------------------------------------------------------------------------------

    private static boolean isGamepad(InputDevice device) {
        if (device == null || device.isVirtual()) return false;
        // Only real controllers: some phones expose internal sensors/keys as joystick sources, which hid the pad.
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD &&
                (Build.VERSION.SDK_INT < 29 || device.isExternal());
    }

    private void refreshGamepads() {
        boolean any = false;
        for (int id : inputManager.getInputDeviceIds()) {
            InputDevice device = inputManager.getInputDevice(id);
            if (isGamepad(device)) {
                any = true;
                Log.i("INPUT", "Physical controller: " + device.getName());
            }
        }
        if (any) controls.onGamepadActive(); else controls.onGamepadGone();
    }

    @Override public void onInputDeviceAdded(int id) { refreshGamepads(); }
    @Override public void onInputDeviceRemoved(int id) { refreshGamepads(); }
    @Override public void onInputDeviceChanged(int id) { refreshGamepads(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (isGamepad(event.getDevice())) controls.onGamepadActive();
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isGamepad(event.getDevice())) controls.onGamepadActive();
        return super.dispatchGenericMotionEvent(event);
    }

    // ---- Tilt steering -------------------------------------------------------------------------------------

    private void updateTiltListener() {
        if (sensorManager == null || gravity == null || controls == null) return;
        sensorManager.unregisterListener(this);
        if (controls.tiltSteering) sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override public void onSensorChanged(SensorEvent event) {
        float gx = event.values[0], gy = event.values[1];
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        float ux = rotation == Surface.ROTATION_270 ? -gx : gx, uy = rotation == Surface.ROTATION_270 ? -gy : gy;
        controls.setTilt((float) (Math.toDegrees(Math.atan2(uy, ux)) / 30.0));
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // ---- Layout editor -------------------------------------------------------------------------------------

    @Override public void onEditModeChanged(boolean editing) {
        toolbar.setVisibility(editing ? View.VISIBLE : View.GONE);
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6121A22);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x55FFFFFF);
        bar.setBackground(bg);
        TextView hint = new TextView(this);
        hint.setText(R.string.touch_editor_hint);
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        bar.addView(hint);
        LinearLayout row = new LinearLayout(this);
        row.addView(toolButton(R.string.touch_size_down, v -> controls.resizeSelected(1f / 1.12f)));
        row.addView(toolButton(R.string.touch_size_up, v -> controls.resizeSelected(1.12f)));
        row.addView(toolButton(R.string.touch_toggle_visible, v -> controls.toggleSelectedVisible()));
        row.addView(toolButton(R.string.touch_settings, v -> showSettings()));
        row.addView(toolButton(R.string.touch_reset, v -> confirmReset()));
        Button done = toolButton(R.string.touch_done_button, v -> controls.setEditing(false));
        ((GradientDrawable) done.getBackground()).setColor(0xFFE07B00);
        row.addView(done);
        bar.addView(row);
        return bar;
    }

    private Button toolButton(int label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A3440);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(4), dp(6), dp(4), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(onClick);
        return b;
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.touch_reset_title).setMessage(R.string.touch_reset_message)
                .setPositiveButton(R.string.touch_reset, (d, w) -> controls.resetLayout())
                .setNegativeButton(R.string.dialog_cancel, null).show();
    }

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(8));
        panel.addView(toggle(R.string.touch_haptics, controls.haptics, on -> controls.haptics = on));
        panel.addView(toggle(R.string.touch_hide_with_gamepad, controls.hideWithGamepad, on -> controls.hideWithGamepad = on));
        panel.addView(toggle(R.string.touch_tilt_steering, controls.tiltSteering, on -> {
            controls.tiltSteering = on;
            updateTiltListener();
        }));
        panel.addView(slider(R.string.touch_tilt_sensitivity, 50, 250, Math.round(controls.tiltSensitivity * 100),
                v -> controls.tiltSensitivity = v / 100f));
        panel.addView(slider(R.string.touch_opacity, 15, 100, Math.round(controls.opacity * 100), v -> {
            controls.opacity = v / 100f;
            controls.invalidate();
        }));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.touch_settings_title).setView(panel)
                .setPositiveButton(R.string.dialog_ok, (d, w) -> controls.save())
                .setOnDismissListener(d -> controls.save()).show();
    }

    private interface OnToggle { void set(boolean on); }

    private interface OnValue { void set(int value); }

    private View toggle(int label, boolean value, OnToggle onToggle) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setChecked(value);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setOnCheckedChangeListener((v, on) -> onToggle.set(on));
        return s;
    }

    private View slider(int label, int min, int max, int value, OnValue onValue) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(4));
        TextView title = new TextView(this);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        box.addView(title);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        String name = getString(label);
        title.setText(name + ": " + value + " %");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                title.setText(name + ": " + (progress + min) + " %");
                onValue.set(progress + min);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(bar);
        return box;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
