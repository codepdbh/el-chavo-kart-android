// Optional timing of synchronous Vulkan occlusion readbacks. Off by default.
#pragma once
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <rex/cvar.h>
#include <rex/logging.h>

REXCVAR_DEFINE_BOOL(chavokart_trace_occlusion, false, "Chavo Kart/Diagnostics",
                    "Report count and duration of Vulkan occlusion readbacks every five seconds");

class ChavoOcclusionTrace {
 public:
  ChavoOcclusionTrace() : enabled_(REXCVAR_GET(chavokart_trace_occlusion)) {
    if (enabled_) start_ = Clock::now();
  }
  ~ChavoOcclusionTrace() {
    if (!enabled_) return;
    const auto now = Clock::now();
    const double ms = std::chrono::duration<double, std::milli>(now - start_).count();
    // Occlusion queries run on the GPU command thread.
    static auto last_report = now;
    static uint64_t calls = 0;
    static double total_ms = 0, max_ms = 0;
    ++calls;
    total_ms += ms;
    max_ms = std::max(max_ms, ms);
    if (now - last_report >= std::chrono::seconds(5)) {
      REXLOG_INFO("[PERFORMANCE] occlusion readbacks={} total={:.2f} ms max={:.2f} ms", calls, total_ms, max_ms);
      calls = 0;
      total_ms = max_ms = 0;
      last_report = now;
    }
  }
 private:
  using Clock = std::chrono::steady_clock;
  bool enabled_;
  Clock::time_point start_{};
};
