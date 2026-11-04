// El Chavo Kart — Android Evolved
// Diagnostic: where guest threads block, to find the game's frame limiter. Off by default.
// --chavokart_trace_sleep=true reports every 5 s, per (hooked function, caller return address, timeout argument):
// call count and total host time spent blocked.
//   sub_830D06A8: XAPI SleepEx (only caller of KeDelayExecutionThread in the image).
//   sub_830D07C0: XAPI WaitForSingleObject (handle r3, milliseconds r4) — used e.g. by Bink's 16 ms timer thread.
#include <chrono>
#include <cstdint>
#include <map>
#include <mutex>
#include <tuple>

#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/ppc.h>

REXCVAR_DEFINE_BOOL(chavokart_trace_sleep, false, "Chavo Kart/Diagnostics",
                    "Log where guest threads block (Sleep/WaitForSingleObject callers and durations)");

namespace {
struct Stat {
  uint64_t calls = 0;
  double blocked_ms = 0;
};
std::mutex g_mutex;
std::map<std::tuple<char, uint32_t, uint32_t>, Stat> g_stats;
auto g_last_report = std::chrono::steady_clock::now();

void Record(char kind, uint32_t caller, uint32_t timeout, double ms) {
  std::lock_guard<std::mutex> lock(g_mutex);
  Stat& s = g_stats[{kind, caller, timeout}];
  ++s.calls;
  s.blocked_ms += ms;
  const auto now = std::chrono::steady_clock::now();
  if (now - g_last_report < std::chrono::seconds(5)) return;
  g_last_report = now;
  for (const auto& [key, stat] : g_stats) {
    if (stat.blocked_ms < 50) continue;
    REXLOG_INFO("[PERFORMANCE] {} caller 0x{:08X} timeout={} calls={} blocked={:.0f} ms",
                std::get<0>(key) == 'S' ? "Sleep" : "Wait", std::get<1>(key), std::get<2>(key), stat.calls,
                stat.blocked_ms);
  }
  g_stats.clear();
}

template <typename F>
void Timed(char kind, PPCContext& ctx, uint32_t timeout, F&& original) {
  if (!REXCVAR_GET(chavokart_trace_sleep)) {
    original();
    return;
  }
  const uint32_t caller = static_cast<uint32_t>(ctx.lr);
  const auto start = std::chrono::steady_clock::now();
  original();
  Record(kind, caller, timeout,
         std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count());
}
}  // namespace

REX_EXTERN(__imp__sub_830D06A8);
REX_EXTERN(sub_830D06A8);
REX_FUNC(sub_830D06A8) {
  Timed('S', ctx, ctx.r3.u32, [&] { __imp__sub_830D06A8(ctx, base); });
}

REX_EXTERN(__imp__sub_830D07C0);
REX_EXTERN(sub_830D07C0);
REX_FUNC(sub_830D07C0) {
  Timed('W', ctx, ctx.r4.u32, [&] { __imp__sub_830D07C0(ctx, base); });
}

// TEMP diagnostic: QueryPerformanceCounter (sub_830CFEB8) calls per caller, with --chavokart_trace_qpc.
REXCVAR_DEFINE_BOOL(chavokart_trace_qpc, false, "Chavo Kart/Diagnostics", "Count QueryPerformanceCounter callers");
extern "C" void ck_trace_qpc(uint32_t caller) {
  if (!REXCVAR_GET(chavokart_trace_qpc)) return;
  static std::mutex mutex;
  static std::map<uint32_t, uint64_t> counts;
  static auto last = std::chrono::steady_clock::now();
  std::lock_guard<std::mutex> lock(mutex);
  ++counts[caller];
  const auto now = std::chrono::steady_clock::now();
  if (now - last < std::chrono::seconds(5)) return;
  last = now;
  for (const auto& [address, calls] : counts) {
    if (calls >= 500) REXLOG_INFO("[PERFORMANCE] QPC caller 0x{:08X} calls={}", address, calls);
  }
  counts.clear();
}
