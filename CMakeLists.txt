# El Chavo Kart — Android Evolved
# ReXGlue recompiled project (Xbox 360 title 475807D7, Unreal Engine 3).
#
# SDK: references/nfsmw-android/sdk (NFSMW Android Evolved ReXGlue fork, pinned commit in docs/recompilation.md).
# SDK boilerplate lives in generated/rexglue.cmake (written by `rexglue init`).

cmake_minimum_required(VERSION 3.25)
project(chavokart LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Xenos with the host's graphics API: D3D12 for the PC diagnostic build, Vulkan on Android.
# CK_PC_VULKAN=ON builds the PC executable with the Android renderer path (Vulkan) to reproduce its issues on PC.
option(CK_PC_VULKAN "PC build with the Vulkan Xenos backend (same path as Android)" OFF)
if(ANDROID OR CK_PC_VULKAN)
    set(REXGLUE_USE_D3D12 OFF CACHE BOOL "Android uses Vulkan" FORCE)
    set(REXGLUE_USE_VULKAN ON CACHE BOOL "Android uses Vulkan" FORCE)
else()
    set(REXGLUE_USE_D3D12 ON CACHE BOOL "PC diagnostic backend" FORCE)
    set(REXGLUE_USE_VULKAN OFF CACHE BOOL "PC diagnostic backend" FORCE)
endif()
set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "No profiler dependency" FORCE)
set(REXGLUE_ENABLE_FIDELITYFX OFF CACHE BOOL "Not used" FORCE)
set(REXGLUE_BUILD_TESTS OFF CACHE BOOL "Build the game target only" FORCE)
set(REXGLUE_VERSION_OVERRIDE "0.10.0-dev.android" CACHE STRING "Pinned NFSMW SDK fork")
set(REXGLUE_RECOMP_DEBUG_INFO "none" CACHE STRING "Keep the initial build small")

if(NOT REXSDK_DIR)
    set(REXSDK_DIR "${CMAKE_CURRENT_SOURCE_DIR}/references/nfsmw-android/sdk" CACHE PATH "ReXGlue SDK" FORCE)
endif()

include(generated/rexglue.cmake)

# Gap analysis with computed-tail-call splitting (from NFS Carbon), without changing the shared SDK checkout.
if(TARGET rexcodegen)
    get_target_property(ck_codegen_sources rexcodegen SOURCES)
    set(ck_codegen_replaced "")
    foreach(src IN LISTS ck_codegen_sources)
        if(src MATCHES "(^|/)phase_gapfill\\.cpp$")
            continue()
        endif()
        if(IS_ABSOLUTE "${src}" OR src MATCHES "^\\$<")
            list(APPEND ck_codegen_replaced "${src}")
        else()
            list(APPEND ck_codegen_replaced "${REXSDK_DIR}/src/codegen/${src}")
        endif()
    endforeach()
    list(APPEND ck_codegen_replaced "${CMAKE_CURRENT_SOURCE_DIR}/src/codegen/phase_gapfill.cpp")
    set_property(TARGET rexcodegen PROPERTY SOURCES "${ck_codegen_replaced}")
endif()

# The SDK normally writes binaries into its own source tree; keep this project's binaries in its build folder.
function(ck_stage_sdk_outputs directory)
    get_property(local_targets DIRECTORY "${directory}" PROPERTY BUILDSYSTEM_TARGETS)
    foreach(local_target IN LISTS local_targets)
        get_target_property(local_type ${local_target} TYPE)
        if(NOT local_type STREQUAL "INTERFACE_LIBRARY" AND NOT local_type STREQUAL "UTILITY")
            set_target_properties(${local_target} PROPERTIES
                RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/sdk-bin"
                LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/sdk-bin"
                ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/sdk-bin")
        endif()
    endforeach()
    get_property(child_directories DIRECTORY "${directory}" PROPERTY SUBDIRECTORIES)
    foreach(child_directory IN LISTS child_directories)
        ck_stage_sdk_outputs("${child_directory}")
    endforeach()
endfunction()
ck_stage_sdk_outputs("${REXSDK_DIR}")

set(CHAVOKART_SOURCES
    src/main.cpp
    src/hooks/d3d_gpu_wait.cpp
    src/hooks/sleep_trace.cpp
)

if(ANDROID)
    add_library(chavokart SHARED ${CHAVOKART_SOURCES})
    set_target_properties(chavokart PROPERTIES OUTPUT_NAME main)
    target_link_options(chavokart PRIVATE "-Wl,-z,max-page-size=16384")
elseif(WIN32)
    add_executable(chavokart WIN32 ${CHAVOKART_SOURCES})
else()
    add_executable(chavokart ${CHAVOKART_SOURCES})
endif()

rexglue_setup_target(chavokart GPU_PLUGINS xenos)

# Replaces one SDK source of <target> with a patched copy in the build tree. Each <before> text must exist exactly
# as written, otherwise configuration fails (the SDK changed and the patch must be reviewed). The SDK is untouched.
function(ck_patch_sdk_source target relpath prefix)
    file(READ "${REXSDK_DIR}/${relpath}" text)
    set(pairs ${ARGN})
    list(LENGTH pairs n)
    math(EXPR last "${n} - 1")
    foreach(i RANGE 0 ${last} 2)
        math(EXPR j "${i} + 1")
        list(GET pairs ${i} before_var)
        list(GET pairs ${j} after_var)
        string(FIND "${text}" "${${before_var}}" found)
        if(found EQUAL -1)
            message(FATAL_ERROR "SDK ${relpath} changed (${before_var}); review the El Chavo Kart patch")
        endif()
        string(REPLACE "${${before_var}}" "${${after_var}}" text "${text}")
    endforeach()
    # Unique per SDK path: several SDK files share a name (graphics/command_processor.cpp and vulkan/...).
    string(REPLACE "/" "_" name "${relpath}")
    set(out "${CMAKE_CURRENT_BINARY_DIR}/ck_patched/${name}")
    set(text "${prefix}${text}")
    set(previous "")
    if(EXISTS "${out}")
        file(READ "${out}" previous)
    endif()
    if(NOT previous STREQUAL text)
        file(WRITE "${out}" "${text}")
    endif()
    get_filename_component(dir "${relpath}" DIRECTORY)
    get_target_property(sources ${target} SOURCES)
    get_target_property(target_source_dir ${target} SOURCE_DIR)
    set(replaced "")
    set(matched FALSE)
    foreach(src IN LISTS sources)
        if(src MATCHES "^\$<")
            list(APPEND replaced "${src}")
            continue()
        endif()
        if(IS_ABSOLUTE "${src}")
            set(full "${src}")
        else()
            set(full "${target_source_dir}/${src}")
        endif()
        # Exact path match: several SDK files share a name (graphics/command_processor.cpp and vulkan/...).
        cmake_path(NORMAL_PATH full OUTPUT_VARIABLE full_norm)
        cmake_path(SET target_path NORMALIZE "${REXSDK_DIR}/${relpath}")
        if(full_norm STREQUAL target_path)
            set(matched TRUE)
            continue()
        endif()
        list(APPEND replaced "${full}")
    endforeach()
    if(NOT matched)
        message(FATAL_ERROR "${target} does not compile ${relpath}; the El Chavo Kart patch would add it")
    endif()
    list(APPEND replaced "${out}")
    set_property(TARGET ${target} PROPERTY SOURCES "${replaced}")
    target_include_directories(${target} PRIVATE "${REXSDK_DIR}/${dir}")
endfunction()

if(NOT WIN32 AND TARGET rexcore)
    # Xbox 360 lets several waits succeed on the same finished thread. The SDK's POSIX thread condition calls
    # pthread_join after every successful wait, so the second wait joins a reaped pthread_t: bionic aborts with
    # "invalid pthread_t passed to pthread_join" (first S25 run, 2026-10-20, via NtWaitForSingleObjectEx).
    # Join and destroy the suspend semaphore exactly once.
    set(ck_join_before "  inline void post_execution() override {\n    if (thread_) {\n      pthread_join(thread_, nullptr);\n    }")
    set(ck_join_after "  inline void post_execution() override {\n    // El Chavo Kart: join/destroy once; finished threads may be waited on repeatedly.\n    if (joined_.exchange(true)) return;\n    if (thread_) {\n      pthread_join(thread_, nullptr);\n    }")
    set(ck_member_before "  pthread_t thread_;\n  bool signaled_;\n  int exit_code_;")
    set(ck_member_after "  pthread_t thread_;\n  std::atomic<bool> joined_{false};\n  bool signaled_;\n  int exit_code_;")
    ck_patch_sdk_source(rexcore src/core/threading_posix.cpp "#include <atomic>\n"
        ck_join_before ck_join_after ck_member_before ck_member_after)
endif()

if(ANDROID AND TARGET rexcore)
    # QueryProtect is hot in the write-watch fault handler on Android: read /proc/self/maps with fixed buffers
    # instead of allocating/parsing every line (measured by NFS Carbon, android/app/src/main/cpp/CMakeLists.txt).
    file(READ "${REXSDK_DIR}/src/core/memory_posix.cpp" ck_memory_source)
    set(ck_mem_start "static bool FindEntryForAddress(void* address, LinuxMapEntry& out_entry) {")
    set(ck_mem_end "// Check if [base, base+length) is fully covered by existing mappings (no gaps)")
    string(FIND "${ck_memory_source}" "${ck_mem_start}" ck_mem_begin)
    string(FIND "${ck_memory_source}" "${ck_mem_end}" ck_mem_finish)
    if(ck_mem_begin LESS 0 OR ck_mem_finish LESS ck_mem_begin)
        message(FATAL_ERROR "SDK memory_posix.cpp changed; review the /proc/self/maps reader")
    endif()
    math(EXPR ck_mem_len "${ck_mem_finish} - ${ck_mem_begin}")
    string(SUBSTRING "${ck_memory_source}" ${ck_mem_begin} ${ck_mem_len} ck_mem_old)
    set(ck_mem_new "${ck_mem_start}\n  carbon::MapEntry entry;\n  if (!carbon::FindProcMapEntry(address, entry)) return false;\n  out_entry.start = entry.start;\n  out_entry.end = entry.end;\n  std::memcpy(out_entry.perms, entry.perms, sizeof(entry.perms));\n  return true;\n}\n\n")
    ck_patch_sdk_source(rexcore src/core/memory_posix.cpp
        "#include \"${CMAKE_CURRENT_SOURCE_DIR}/src/platform/proc_maps.h\"\n" ck_mem_old ck_mem_new)
endif()

if(TARGET rexgpu-xenos)
    # Count guest frames (XE_SWAP packets) for the in-game FPS counter. The SDK's perf counters are compiled out of
    # Release builds; this is a single relaxed atomic increment per swap, read through chavokart_guest_swaps().
    set(ck_swap_before "bool CommandProcessor::ExecutePacketType3_XE_SWAP(memory::RingBuffer* reader, uint32_t packet,\n                                                  uint32_t count) {\n  SCOPE_profile_cpu_f(\"gpu\");")
    set(ck_swap_after "bool CommandProcessor::ExecutePacketType3_XE_SWAP(memory::RingBuffer* reader, uint32_t packet,\n                                                  uint32_t count) {\n  SCOPE_profile_cpu_f(\"gpu\");\n  ck_on_guest_swap(ck_guest_swaps.fetch_add(1, std::memory_order_relaxed));")
    set(ck_wait_a_before "  bool matched = false;\n  do {\n    uint32_t value = 0;")
    set(ck_wait_a_after "  bool matched = false;\n  const bool ck_trace_wait = REXCVAR_GET(chavokart_trace_gpu_wait);\n  const auto ck_wait_start = ck_trace_wait ? std::chrono::steady_clock::now() : std::chrono::steady_clock::time_point{};\n  do {\n    uint32_t value = 0;")
    set(ck_wait_b_before "  } while (!matched);\n\n  return true;\n}\n\nbool CommandProcessor::ExecutePacketType3_REG_RMW(")
    set(ck_wait_b_after "  } while (!matched);\n  if (ck_trace_wait) {\n    ck_record_gpu_wait(is_memory, poll_reg_addr, wait_info, ref, ck_wait_start);\n  }\n\n  return true;\n}\n\nbool CommandProcessor::ExecutePacketType3_REG_RMW(")
    ck_patch_sdk_source(rexgpu-xenos src/graphics/command_processor.cpp
        "#include \"${CMAKE_CURRENT_SOURCE_DIR}/src/platform/guest_swap_hooks.h\"\n"
        ck_swap_before ck_swap_after ck_wait_a_before ck_wait_a_after ck_wait_b_before ck_wait_b_after)
endif()

if(TARGET rexruntime)
    # Diagnostic (--chavokart_trace_delay): which guest call chains sleep/wait in the kernel; see delay_trace.h.
    set(ck_scope "ck_delay_trace::Scope ck_scope{")
    set(ck_timeout "REX_KERNEL_MEMORY(), timeout_ptr ? int64_t(uint64_t(*timeout_ptr)) : -1};
")
    set(ck_delay_before "  XThread* thread = XThread::GetCurrentThread();

  if (alertable) {")
    set(ck_delay_after "  ${ck_scope}'D', REX_KERNEL_MEMORY(), int64_t(uint64_t(*interval_ptr))};
  XThread* thread = XThread::GetCurrentThread();

  if (alertable) {")
    set(ck_kwait_before "u32 alertable, mapped_u64 timeout_ptr) {
  uint64_t timeout = timeout_ptr ? static_cast<uint64_t>(*timeout_ptr) : 0u;
  // REXKRNL_IMPORT_TRACE")
    set(ck_kwait_after "u32 alertable, mapped_u64 timeout_ptr) {
  ${ck_scope}'K', ${ck_timeout}  uint64_t timeout = timeout_ptr ? static_cast<uint64_t>(*timeout_ptr) : 0u;
  // REXKRNL_IMPORT_TRACE")
    set(ck_nwait_before "                                  mapped_u64 timeout_ptr) {
  X_STATUS result = X_STATUS_SUCCESS;

  auto object = REX_KERNEL_OBJECTS()")
    set(ck_nwait_after "                                  mapped_u64 timeout_ptr) {
  ${ck_scope}'N', ${ck_timeout}  X_STATUS result = X_STATUS_SUCCESS;

  auto object = REX_KERNEL_OBJECTS()")
    set(ck_kmulti_before "mapped_void wait_block_array_ptr) {
  assert_true(wait_type <= 1);")
    set(ck_kmulti_after "mapped_void wait_block_array_ptr) {
  ${ck_scope}'M', ${ck_timeout}  assert_true(wait_type <= 1);")
    set(ck_nmulti_before "u32 alertable, mapped_u64 timeout_ptr) {
  uint64_t timeout = timeout_ptr ? static_cast<uint64_t>(*timeout_ptr) : 0u;
  return xeNtWaitForMultipleObjectsEx(")
    set(ck_nmulti_after "u32 alertable, mapped_u64 timeout_ptr) {
  ${ck_scope}'X', ${ck_timeout}  uint64_t timeout = timeout_ptr ? static_cast<uint64_t>(*timeout_ptr) : 0u;
  return xeNtWaitForMultipleObjectsEx(")
    ck_patch_sdk_source(rexruntime src/kernel/xboxkrnl/xboxkrnl_threading.cpp
        "#include \"${CMAKE_CURRENT_SOURCE_DIR}/src/platform/delay_trace.h\"
"
        ck_delay_before ck_delay_after ck_kwait_before ck_kwait_after ck_nwait_before ck_nwait_after
        ck_kmulti_before ck_kmulti_after ck_nmulti_before ck_nmulti_after)
endif()

if(TARGET rexgpu-xenos AND REXGLUE_USE_VULKAN)
    # Vulkan vertex residency shortcut: a reused buffer address/size skipped RequestRange and kept stale dynamic
    # vertices from earlier frames (dark karts / black road band on Android). Reset the shortcut on swap, on GPU
    # memory invalidation and per fetch constant (same fix as the-simpsons-game-android patches/android-sdk.patch).
    set(ck_vb_a_before "void VulkanCommandProcessor::InvalidateGpuMemory() {
")
    set(ck_vb_a_after "void VulkanCommandProcessor::InvalidateGpuMemory() {
  InvalidateAllVertexBufferResidency();
")
    set(ck_vb_b_before "  vertex_buffers_in_sync_[vfetch_index >> 6] &= ~(uint64_t(1) << (vfetch_index & 63));
}")
    set(ck_vb_b_after "  vertex_buffers_in_sync_[vfetch_index >> 6] &= ~(uint64_t(1) << (vfetch_index & 63));
  vertex_buffer_states_[vfetch_index].address = UINT32_MAX;
  vertex_buffer_states_[vfetch_index].size = UINT32_MAX;
}")
    set(ck_vb_c_before "#endif
  vertex_buffers_in_sync_[0] = 0;
  vertex_buffers_in_sync_[1] = 0;

  if (!graphics_system_)")
    set(ck_vb_c_after "#endif
  InvalidateAllVertexBufferResidency();

  if (!graphics_system_)")
    set(ck_vb_inc_before "#include \"../shaders/")
    set(ck_vb_inc_after "#include \"${REXSDK_DIR}/src/graphics/shaders/")
    # Adreno reversed-Z prepass / color-pass precision workaround. The game's color pass reuses
    # depth from an earlier pass, but the two rasterized depths differ by a few float32 ULPs.
    set(ck_depth_before "  UpdateDynamicState(viewport_info, primitive_polygonal, normalized_depth_control);")
    set(ck_depth_after "  UpdateDynamicState(viewport_info, primitive_polygonal, normalized_depth_control);
  const int32_t ck_depth_bias = REXCVAR_GET(chavokart_depth_bias_units);
  if (ck_depth_bias && GetVulkanDevice()->properties().vendorID == 0x5143 &&
      host_render_targets_used && normalized_color_mask && pixel_shader &&
      !pixel_shader->writes_depth() && normalized_depth_control.z_enable &&
      normalized_depth_control.zfunc == xenos::CompareFunction::kGreaterEqual &&
      regs.Get<reg::RB_DEPTH_INFO>().depth_format == xenos::DepthRenderTargetFormat::kD24FS8) {
    deferred_command_buffer_.CmdVkSetDepthBias(dynamic_depth_bias_constant_factor_ + float(ck_depth_bias),
                                               0.0f, dynamic_depth_bias_slope_factor_);
    // Restore the guest's unmodified state on the next draw, including depth-only draws.
    dynamic_depth_bias_update_needed_ = true;
  }")
    ck_patch_sdk_source(rexgpu-xenos src/graphics/vulkan/command_processor.cpp
        "// Altered for El Chavo Kart: vertex residency invalidation and depth diagnostic.
#include \"${CMAKE_CURRENT_SOURCE_DIR}/src/platform/vulkan_depth_bias.h\"
"
        ck_vb_inc_before ck_vb_inc_after ck_vb_a_before ck_vb_a_after ck_vb_b_before ck_vb_b_after ck_vb_c_before ck_vb_c_after ck_depth_before ck_depth_after)
endif()

if(ANDROID AND TARGET SDL3-shared)
    # SDL's Android event wait wakes itself when it inserts its polling sentinel; do not send a wake for a
    # sentinel-only batch (real input/lifecycle wakes remain). Same change as NFS Carbon; SDL license retained.
    set(ck_sdl_a_before "int i, used, sentinels_expected = 0;")
    set(ck_sdl_a_after "int i, used, sentinels_expected = 0;\n    int wakeup_events = 0;")
    set(ck_sdl_b_before "                used += SDL_AddEvent(&events[i]);")
    set(ck_sdl_b_after "                int added = SDL_AddEvent(&events[i]);\n                used += added;\n                if (added > 0 && events[i].type != SDL_EVENT_POLL_SENTINEL) ++wakeup_events;")
    set(ck_sdl_c_before "if (used > 0 && action == SDL_ADDEVENT)")
    set(ck_sdl_c_after "if (wakeup_events > 0 && action == SDL_ADDEVENT)")
    ck_patch_sdk_source(SDL3-shared thirdparty/sdl3/src/events/SDL_events.c
        "/* Altered for El Chavo Kart (as NFS Carbon): avoid sentinel-only self-wakes. */\n"
        ck_sdl_a_before ck_sdl_a_after ck_sdl_b_before ck_sdl_b_after ck_sdl_c_before ck_sdl_c_after)
endif()

if(ANDROID)
    # The shared NFSMW SDK entry point creates the app "nfsmw"; use this project's entry instead.
    get_target_property(ck_app_sources chavokart SOURCES)
    list(FILTER ck_app_sources EXCLUDE REGEX "(^|/)windowed_app_main_sdl\\.cpp$")
    set_property(TARGET chavokart PROPERTY SOURCES "${ck_app_sources}")
    target_sources(chavokart PRIVATE src/platform/android_entry.cpp src/platform/android_touch.cpp
        src/platform/android_performance.cpp)
    target_link_libraries(chavokart PRIVATE rexgpu-xenos log)
    # Custom Vulkan driver loader and its namespace hooks are packaged next to the game (as NFSMW/Carbon).
    foreach(ck_hook adrenotools main_hook hook_impl file_redirect_hook gsl_alloc_hook)
        if(TARGET ${ck_hook})
            add_dependencies(chavokart ${ck_hook})
        endif()
    endforeach()
    # Keep each FFmpeg instance's assembly DSP bindings and tables together (same measurement as Carbon).
    foreach(ck_target chavokart rexruntime)
        target_link_options(${ck_target} PRIVATE "LINKER:--exclude-libs,liblibavcodec.a:liblibavutil.a")
    endforeach()
    if(TARGET SDL3-shared)
        set_target_properties(SDL3-shared PROPERTIES OUTPUT_NAME SDL3 DEBUG_POSTFIX ""
            RELEASE_POSTFIX "" RELWITHDEBINFO_POSTFIX "" MINSIZEREL_POSTFIX "")
    endif()
    # 15 GB RAM on the development machine: bound compile/link concurrency for the generated sources.
    set_property(GLOBAL PROPERTY JOB_POOLS ck_compile=6 ck_link=1)
    foreach(ck_target chavokart chavokart_recomp rexruntime rexgpu-xenos)
        if(TARGET ${ck_target})
            set_property(TARGET ${ck_target} PROPERTY JOB_POOL_COMPILE ck_compile)
            set_property(TARGET ${ck_target} PROPERTY JOB_POOL_LINK ck_link)
        endif()
    endforeach()
endif()
if(ANDROID AND TARGET chavokart_recomp)
    # ReXApp registers PPCImageConfig.func_mappings from chavokart_init.cpp; the optional RegisterFunctions
    # helper has no callers and is very slow to compile for AArch64 (same measurement as NFS Carbon).
    get_target_property(ck_recomp_sources chavokart_recomp SOURCES)
    list(FILTER ck_recomp_sources EXCLUDE REGEX "(^|/)chavokart_register\\.cpp$")
    set_property(TARGET chavokart_recomp PROPERTY SOURCES "${ck_recomp_sources}")
endif()
foreach(ui_dependency imgui renderdoc)
    if(TARGET ${ui_dependency})
        target_link_libraries(chavokart PRIVATE ${ui_dependency})
    endif()
endforeach()
