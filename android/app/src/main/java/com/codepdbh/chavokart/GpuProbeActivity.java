package com.codepdbh.chavokart;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONObject;

/**
 * Runs the Vulkan capability probe in its own process (android:process=":probe"). If the driver crashes, only this
 * process dies and the launcher receives RESULT_CANCELED.
 */
public class GpuProbeActivity extends LocalizedActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            String report;
            try {
                JSONObject json = new JSONObject(GpuProbe.nativeProbe());
                json.put("android", new JSONObject()
                        .put("release", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
                        .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("device", Build.DEVICE)
                        .put("socManufacturer", Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER : "")
                        .put("socModel", Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "")
                        .put("hardware", Build.HARDWARE).put("abis", String.join(",", Build.SUPPORTED_ABIS)));
                report = json.toString();
            } catch (Throwable error) {
                try {
                    report = new JSONObject().put("probeError", String.valueOf(error.getMessage())).toString();
                } catch (Exception impossible) {
                    report = "{}";
                }
            }
            Intent result = new Intent().putExtra("gpu_report", report);
            runOnUiThread(() -> { setResult(RESULT_OK, result); finish(); });
        }, "gpu-probe").start();
    }
}
