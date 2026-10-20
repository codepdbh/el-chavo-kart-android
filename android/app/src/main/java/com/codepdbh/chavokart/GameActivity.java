// Derived from the-simpsons-game-android GameActivity (codepdbh, GPLv3), commit ed0f1ab.
package com.codepdbh.chavokart;

import android.hardware.input.InputManager;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Runs the recompiled game (libmain.so) in the isolated ":game" process with the virtual pad on top. */
public class GameActivity extends LandscapeSDLActivity implements InputManager.InputDeviceListener {
    private TouchControlsView controls;
    private LinearLayout editor;
    private TextView editorLabel;
    private SeekBar sizeSlider, opacitySlider;
    private boolean updatingEditor;

    @Override protected void onCreate(Bundle state) {
        Log.init(this);
        File user = new File(getFilesDir(), "user");
        user.mkdirs();
        new File(getFilesDir(), "cache").mkdirs();
        try {
            Os.setenv("REX_APP_FOLDER", user.getAbsolutePath(), true);
        } catch (ErrnoException e) {
            throw new IllegalStateException("REX_APP_FOLDER", e);
        }
        super.onCreate(state);
        controls = new TouchControlsView(this);
        ViewGroup content = findViewById(android.R.id.content);
        content.addView(controls, new FrameLayout.LayoutParams(-1, -1));
        buildEditor(content);
        ((InputManager) getSystemService(INPUT_SERVICE)).registerInputDeviceListener(this, null);
        updateControlsVisibility();
        Log.i("ANDROID", "Game activity started; args " + String.join(" ", getArguments()));
    }

    private void buildEditor(ViewGroup content) {
        editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        editor.setPadding(pad, pad, pad, pad);
        editor.setBackgroundColor(0xee1c2433);
        editorLabel = new TextView(this);
        editorLabel.setTextColor(0xffffffff);
        editor.addView(editorLabel);
        sizeSlider = new SeekBar(this);
        sizeSlider.setMax(195);
        editor.addView(sizeSlider);
        TextView opacityLabel = new TextView(this);
        opacityLabel.setTextColor(0xffffffff);
        opacityLabel.setText(R.string.touch_opacity);
        editor.addView(opacityLabel);
        opacitySlider = new SeekBar(this);
        opacitySlider.setMax(85);
        editor.addView(opacitySlider);
        sizeSlider.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && !updatingEditor) controls.resizeSelected((progress + 25) / 1000f);
            }
        });
        opacitySlider.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && !updatingEditor) controls.setOpacity((progress + 15) / 100f);
            }
        });
        LinearLayout buttons = new LinearLayout(this);
        editor.addView(buttons);
        Button reset = new Button(this);
        reset.setText(R.string.touch_reset);
        reset.setOnClickListener(v -> controls.resetLayout());
        buttons.addView(reset, new LinearLayout.LayoutParams(0, -2, 1));
        Button done = new Button(this);
        done.setText(R.string.touch_save);
        done.setOnClickListener(v -> controls.setEditing(false));
        buttons.addView(done, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.round(340 * getResources().getDisplayMetrics().density), -2, Gravity.CENTER);
        content.addView(editor, params);
        controls.setEditorListener(() -> {
            editor.setVisibility(controls.isEditing() ? View.VISIBLE : View.GONE);
            editorLabel.setText(getString(R.string.touch_editor_help, controls.selectedLabel()));
            updatingEditor = true;
            sizeSlider.setEnabled(controls.hasSelection());
            sizeSlider.setProgress(Math.round(controls.selectedSize() * 1000) - 25);
            opacitySlider.setProgress(Math.round(controls.opacity() * 100) - 15);
            updatingEditor = false;
        });
        editor.setVisibility(View.GONE);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar bar) { }
        @Override public void onStopTrackingTouch(SeekBar bar) { }
    }

    /** Hide the virtual pad while a physical gamepad is connected (it can be edited again once removed). */
    private void updateControlsVisibility() {
        boolean pad = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && !d.isVirtual() && (d.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) pad = true;
        }
        if (controls != null) {
            if (pad) controls.release();
            controls.setVisibility(pad && !controls.isEditing() ? View.GONE : View.VISIBLE);
        }
    }

    @Override public void onInputDeviceAdded(int id) { updateControlsVisibility(); }
    @Override public void onInputDeviceRemoved(int id) { updateControlsVisibility(); }
    @Override public void onInputDeviceChanged(int id) { updateControlsVisibility(); }

    @Override protected void onPause() {
        if (controls != null) { controls.release(); controls.saveLayout(); }
        super.onPause();
    }

    @Override protected void onDestroy() {
        ((InputManager) getSystemService(INPUT_SERVICE)).unregisterInputDeviceListener(this);
        super.onDestroy();
    }

    @Override protected String[] getLibraries() { return new String[] {"SDL3", "main"}; }

    @Override protected String[] getArguments() {
        List<String> args = new ArrayList<>();
        File files = getFilesDir();
        args.add("--game_data_root=" + new File(files, "game"));
        args.add("--user_data_root=" + new File(files, "user"));
        args.add("--cache_root=" + new File(files, "cache"));
        args.add("--log_file=" + new File(new File(files, "logs"), "game.log"));
        args.add("--gpu_plugin=xenos");
        GpuDrivers.arguments(this, args);
        return args.toArray(new String[0]);
    }
}
