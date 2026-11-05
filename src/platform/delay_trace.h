// El Chavo Kart — Android Evolved
// Included at the top of the patched SDK src/kernel/xboxkrnl/xboxkrnl_threading.cpp (see CMakeLists.txt).
// Diagnostic, off by default: --chavokart_trace_delay=true reports every 5 s which guest call chains block in
// KeDelayExecutionThread (D) and the Ke/Nt wait functions (W, M) with requested interval/timeout, calls and host time. Works on every platform because it sits in
// the kernel export, unlike REX_FUNC hooks (Windows COFF weak aliases do not override). Used to find the 30 FPS
// frame limiter. Call chain: return addresses read from the guest stack back chain (LR saved at back_chain - 8).
#pragma once
#include <chrono>
#include <cstdint>
#include <map>
#include <mutex>
#include <tuple>

#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/system/thread_state.h>

REXCVAR_DEFINE_BOOL(chavokart_trace_delay, false, "Chavo Kart/Diagnostics",
                    "Log guest call chains that sleep through KeDelayExecutionThread");

namespace ck_delay_trace {
using Key = std::tuple<char, uint32_t, uint32_t, uint32_t, uint32_t, int64_t>;
struct Stat {
  uint64_t calls = 0;
  double blocked_ms = 0;
};
inline std::mutex mutex;
inline std::map<Key, Stat> stats;
inline auto last_report = std::chrono::steady_clock::now();

template <typename Memory>
uint32_t Load32(Memory* memory, uint32_t address) {
  if (address < 0x10000 || address >= 0xC0000000) return 0;
  const uint8_t* p = memory->template TranslateVirtual<const uint8_t*>(address);
  return uint32_t(p[0]) << 24 | uint32_t(p[1]) << 16 | uint32_t(p[2]) << 8 | p[3];
}

template <typename Memory>
void Record(char kind, Memory* memory, int64_t interval, double ms) {
  uint32_t chain[4] = {};
  if (auto* ts = rex::runtime::ThreadState::Get()) {
    // Stay inside this thread's stack: back chain pointers grow upward, below the PCR stack base (r13 + 0x70).
    const PPCContext* ctx = ts->context();
    const uint32_t pcr = ctx->r13.u32;
    const uint32_t stack_base = pcr ? Load32(memory, pcr + 0x70) : 0;
    uint32_t sp = ctx->r1.u32;
    chain[0] = uint32_t(ctx->lr);  // return address in the function that called the kernel export
    for (uint32_t i = 1; i < 4; ++i) {
      uint32_t& ret = chain[i];
      if (sp >= stack_base) break;
      const uint32_t next = Load32(memory, sp);
      if (next <= sp || next > stack_base) break;
      sp = next;
      ret = Load32(memory, sp - 8);
    }
  }
  // Never block here: a guest thread can be suspended while holding the lock. Dropped samples are acceptable.
  std::unique_lock<std::mutex> lock(mutex, std::try_to_lock);
  if (!lock.owns_lock()) return;
  Stat& s = stats[{kind, chain[0], chain[1], chain[2], chain[3], interval}];
  ++s.calls;
  s.blocked_ms += ms;
  const auto now = std::chrono::steady_clock::now();
  if (now - last_report < std::chrono::seconds(5)) return;
  last_report = now;
  for (const auto& [k, v] : stats) {
    if (v.blocked_ms < 50) continue;
    REXLOG_INFO("[PERFORMANCE] {} {:08X} < {:08X} < {:08X} < {:08X} interval={} calls={} blocked={:.0f} ms",
                std::get<0>(k), std::get<1>(k), std::get<2>(k), std::get<3>(k), std::get<4>(k), std::get<5>(k),
                v.calls, v.blocked_ms);
  }
  stats.clear();
}

// Records the enclosing kernel call on scope exit.
template <typename Memory>
struct Scope {
  char kind;
  Memory* memory;
  int64_t interval;
  bool enabled = REXCVAR_GET(chavokart_trace_delay);
  // Kernel waits are extremely frequent. Do not query the clock when tracing is disabled.
  std::chrono::steady_clock::time_point start =
      enabled ? std::chrono::steady_clock::now() : std::chrono::steady_clock::time_point{};
  ~Scope() {
    if (enabled) {
      Record(kind, memory, interval,
             std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count());
    }
  }
};
template <typename Memory>
Scope(char, Memory*, int64_t) -> Scope<Memory>;
}  // namespace ck_delay_trace
