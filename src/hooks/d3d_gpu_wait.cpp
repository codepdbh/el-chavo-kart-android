// El Chavo Kart — Android Evolved
// Yield in Direct3D's GPU wait callback instead of spinning.
//
// Evidence (default.xex SHA-256 1db4e96b...120f, docs/performance.md):
//   * simpleperf on the Galaxy S25 (2026-10-20): one guest thread at 100 % of a big core, 40 % of its samples in
//     sub_82B5C040, plus its callers sub_82B578B0 / sub_82B5CE88 — all in the statically linked Xbox 360 D3D
//     runtime (it also registers the GPU interrupt callback 0x82B57C28).
//   * sub_82B5C040 is a wait-iteration callback: PowerPC thread-priority hints (`or r1,r1,r1`, `or r31,r31,r31`
//     x32, `or r2,r2,r2`) that only make sense while spinning, a check of the device state at 0x2B10/0x2B08, and a
//     5000-tick timeout (cmplwi 0x1388) before the GPU-hang check sub_82B6DBC8. It returns 1 while the wait must
//     continue and 0 when it is satisfied or aborted.
// The Xbox's hints let the other hardware thread run; on ARM they are plain moves, so the loop burns a big core,
// competes with the GPU command thread and triggers thermal limits. Same remedy as The Simpsons Game Android
// (src/game/android_ring_wait.cpp, sub_82452018): keep the original logic and timeout, and sleep briefly while it
// still reports "waiting".
#include <chrono>
#include <thread>

#include <rex/cvar.h>
#include <rex/ppc.h>

REXCVAR_DEFINE_INT32(chavokart_gpu_wait_sleep_us, 250, "Chavo Kart/Performance",
                     "Microseconds to sleep in D3D's GPU wait callback while it keeps waiting (0 = original spin)");

REX_EXTERN(__imp__sub_82B5C040);
REX_EXTERN(sub_82B5C040);

REX_FUNC(sub_82B5C040) {
  __imp__sub_82B5C040(ctx, base);
  if (ctx.r3.u32 == 1) {
    const int32_t sleep_us = REXCVAR_GET(chavokart_gpu_wait_sleep_us);
    if (sleep_us > 0) {
      std::this_thread::sleep_for(std::chrono::microseconds(sleep_us));
    }
  }
}
