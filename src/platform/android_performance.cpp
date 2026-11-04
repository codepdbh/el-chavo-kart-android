// El Chavo Kart — Android Evolved
// CPU scheduling adapted from NFS-CARBON-360-ANDROID-RECOMP android/app/src/main/cpp/carbon_performance.cpp
// (commit c3ee962), itself adapted from codepdbh/nfsmw-android 5f581c6. SPDX-License-Identifier: GPL-3.0-only
#include <sched.h>
#include <unistd.h>

#include <algorithm>
#include <fstream>
#include <string>
#include <vector>

#include <rex/cvar.h>
#include <rex/logging.h>

REXCVAR_DEFINE_BOOL(chavokart_fast_cores, true, "Chavo Kart/Android",
                    "Prefer fast CPU cores for newly created game and GPU threads")
    .lifecycle(rex::cvar::Lifecycle::kInitOnly);

// Restricts the startup thread (and every thread it creates afterwards: guest threads, GPU command processor,
// audio) to the CPUs with at least 70 % of the fastest core's capacity. Android's UI and Binder threads keep
// their own affinity. Does nothing when the kernel hides capacity and frequency, or every core is equal.
void ChavokartConfigureAndroidCpu() {
  if (!REXCVAR_GET(chavokart_fast_cores)) return;
  cpu_set_t allowed;
  if (sched_getaffinity(0, sizeof(allowed), &allowed) != 0) return;
  const long count = sysconf(_SC_NPROCESSORS_CONF);
  if (count < 2 || count > CPU_SETSIZE) return;
  std::vector<long> scores(count, 0);
  int available = 0;
  for (int cpu = 0; cpu < count; ++cpu) if (CPU_ISSET(cpu, &allowed)) ++available;
  auto read_scores = [&](const char* attribute) {
    std::fill(scores.begin(), scores.end(), 0);
    for (int cpu = 0; cpu < count; ++cpu) {
      if (!CPU_ISSET(cpu, &allowed)) continue;
      std::ifstream input("/sys/devices/system/cpu/cpu" + std::to_string(cpu) + attribute);
      if (!(input >> scores[cpu]) || scores[cpu] <= 0) return false;
    }
    return true;
  };
  const bool capacity = read_scores("/cpu_capacity");
  if (!capacity && !read_scores("/cpufreq/cpuinfo_max_freq")) return;
  const long fastest = *std::max_element(scores.begin(), scores.end());
  cpu_set_t fast;
  CPU_ZERO(&fast);
  int selected = 0;
  for (int cpu = 0; cpu < count; ++cpu) {
    if (CPU_ISSET(cpu, &allowed) && scores[cpu] >= fastest * 0.7) {
      CPU_SET(cpu, &fast);
      ++selected;
    }
  }
  if (selected < 2 || selected == available) {
    REXLOG_INFO("[PERFORMANCE] CPU affinity unchanged ({} fast of {} cores)", selected, available);
    return;
  }
  if (sched_setaffinity(0, sizeof(fast), &fast) == 0) {
    REXLOG_INFO("[PERFORMANCE] Game startup on {} fast cores of {} available, selected by {}", selected, available,
                capacity ? "capacity" : "frequency");
  }
}

// ---- In-game FPS counter (GameActivity) ----------------------------------------------------------------------
#include <jni.h>
extern "C" unsigned long long chavokart_guest_swaps();  // rexgpu-xenos (patched command_processor.cpp)

extern "C" JNIEXPORT jlong JNICALL Java_com_codepdbh_chavokart_GameActivity_nativeGuestFrames(JNIEnv*, jclass) {
  return static_cast<jlong>(chavokart_guest_swaps());
}
