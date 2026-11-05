// El Chavo Kart — Android Evolved
// Included at the top of the patched SDK src/graphics/command_processor.cpp (see ck_patch_sdk_source in
// CMakeLists.txt). Called once per guest XE_SWAP packet, i.e. once per frame the game submits.
//   * chavokart_guest_swaps(): frame counter for the in-game FPS overlay (Android).
//   * --chavokart_trace_gpu_wait: where the GPU command thread waits (PM4 WAIT_REG_MEM), every 120 frames.
//   * Windows diagnostics: when the file named by the environment variable CK_RDOC_TRIGGER exists and the process
//     runs under RenderDoc, the next frame is captured and the file is deleted. Used to capture renderer glitches.
#pragma once
#include <atomic>
#include <cstdlib>

static std::atomic<unsigned long long> ck_guest_swaps{0};

extern "C" __attribute__((visibility("default"))) unsigned long long chavokart_guest_swaps() {
  return ck_guest_swaps.load(std::memory_order_relaxed);
}

#include <chrono>
#include <algorithm>
#include <array>
#include <rex/logging.h>

#include <map>
#include <mutex>
#include <tuple>
#include <rex/cvar.h>

REXCVAR_DEFINE_BOOL(chavokart_trace_gpu_wait, false, "Chavo Kart/Diagnostics",
                    "Log PM4 WAIT_REG_MEM waits (register/memory polled, reference, host time) every 120 frames");
REXCVAR_DEFINE_BOOL(chavokart_trace_frame_times, false, "Chavo Kart/Diagnostics",
                    "Report guest frame interval percentiles every 120 frames");

static inline void ck_trace_frame_time() {
  if (!REXCVAR_GET(chavokart_trace_frame_times)) return;
  using Clock = std::chrono::steady_clock;
  static auto last = Clock::now();
  static std::array<double, 120> intervals{};
  static size_t count = 0;
  const auto now = Clock::now();
  intervals[count++] = std::chrono::duration<double, std::milli>(now - last).count();
  last = now;
  if (count != intervals.size()) return;
  auto sorted = intervals;
  std::sort(sorted.begin(), sorted.end());
  REXLOG_INFO("[PERFORMANCE] guest frame intervals ms p50={:.2f} p95={:.2f} p99={:.2f} max={:.2f}",
              sorted[59], sorted[113], sorted[118], sorted[119]);
  count = 0;
}

// Accumulated WAIT_REG_MEM time per (memory?, address, function, reference), written by the GPU command thread.
static std::mutex ck_gpu_wait_mutex;
static std::map<std::tuple<bool, uint32_t, uint32_t, uint32_t>, std::pair<uint64_t, double>> ck_gpu_waits;

static inline void ck_record_gpu_wait(bool is_memory, uint32_t address, uint32_t wait_info, uint32_t ref,
                                      std::chrono::steady_clock::time_point start) {
  const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
  std::lock_guard<std::mutex> lock(ck_gpu_wait_mutex);
  auto& entry = ck_gpu_waits[{is_memory, address, wait_info & 7, ref}];
  ++entry.first;
  entry.second += ms;
}

static inline void ck_report_gpu_waits() {
  std::lock_guard<std::mutex> lock(ck_gpu_wait_mutex);
  for (const auto& [key, value] : ck_gpu_waits) {
    if (value.second < 20) continue;
    REXLOG_INFO("[PERFORMANCE] WAIT_REG_MEM {} 0x{:08X} func={} ref=0x{:X} waits={} total={:.0f} ms",
                std::get<0>(key) ? "mem" : "reg", std::get<1>(key), std::get<2>(key), std::get<3>(key),
                value.first, value.second);
  }
  ck_gpu_waits.clear();
}

// Guest frame rate in the runtime log every 120 frames ([PERFORMANCE] lines), for measurements without a profiler.
static inline void ck_log_guest_fps(unsigned long long frame) {
  static std::chrono::steady_clock::time_point last = std::chrono::steady_clock::now();
  if (frame == 0 || frame % 120 != 0) return;
  const auto now = std::chrono::steady_clock::now();
  const double seconds = std::chrono::duration<double>(now - last).count();
  last = now;
  if (seconds > 0) REXLOG_INFO("[PERFORMANCE] guest frames {}: {:.1f} FPS", frame, 120.0 / seconds);
  if (REXCVAR_GET(chavokart_trace_gpu_wait)) ck_report_gpu_waits();
}

#if defined(_WIN32)
#include <windows.h>
#include "renderdoc_app.h"

static RENDERDOC_API_1_0_1* ck_renderdoc_api() {
  static RENDERDOC_API_1_0_1* api = []() -> RENDERDOC_API_1_0_1* {
    HMODULE module = GetModuleHandleA("renderdoc.dll");
    if (!module) return nullptr;
    auto get_api = reinterpret_cast<pRENDERDOC_GetAPI>(GetProcAddress(module, "RENDERDOC_GetAPI"));
    void* result = nullptr;
    if (!get_api || !get_api(eRENDERDOC_API_Version_1_0_1, &result)) return nullptr;
    return static_cast<RENDERDOC_API_1_0_1*>(result);
  }();
  return api;
}

static inline void ck_on_guest_swap(unsigned long long frame) {
  ck_log_guest_fps(frame);
  ck_trace_frame_time();
  if (frame % 10 != 0) return;
  static const char* trigger = std::getenv("CK_RDOC_TRIGGER");
  if (!trigger || GetFileAttributesA(trigger) == INVALID_FILE_ATTRIBUTES) return;
  DeleteFileA(trigger);
  if (RENDERDOC_API_1_0_1* api = ck_renderdoc_api()) api->TriggerCapture();
}
#else
static inline void ck_on_guest_swap(unsigned long long frame) { ck_log_guest_fps(frame); ck_trace_frame_time(); }
#endif
