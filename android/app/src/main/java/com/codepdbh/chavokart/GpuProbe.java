package com.codepdbh.chavokart;

/** JNI bridge to vulkan_probe.cpp (libckprobe.so). Only called inside the isolated :probe process. */
final class GpuProbe {
    static { System.loadLibrary("ckprobe"); }

    static native String nativeProbe();

    private GpuProbe() { }
}
