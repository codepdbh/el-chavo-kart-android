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
if(ANDROID)
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

if(NOT WIN32 AND TARGET rexcore)
    # Xbox 360 lets several waits succeed on the same finished thread. The SDK's POSIX thread condition calls
    # pthread_join after every successful wait, so the second wait joins a reaped pthread_t: bionic aborts with
    # "invalid pthread_t passed to pthread_join" (first S25 run, 2026-10-20, via NtWaitForSingleObjectEx).
    # Join and destroy the suspend semaphore exactly once. Patched copy in the build tree; the SDK is untouched.
    file(READ "${REXSDK_DIR}/src/core/threading_posix.cpp" ck_threading_source)
    set(ck_join_before "  inline void post_execution() override {\n    if (thread_) {\n      pthread_join(thread_, nullptr);\n    }")
    set(ck_join_after "  inline void post_execution() override {\n    // El Chavo Kart: join/destroy once; finished threads may be waited on repeatedly.\n    if (joined_.exchange(true)) return;\n    if (thread_) {\n      pthread_join(thread_, nullptr);\n    }")
    set(ck_member_before "  pthread_t thread_;\n  bool signaled_;\n  int exit_code_;")
    set(ck_member_after "  pthread_t thread_;\n  std::atomic<bool> joined_{false};\n  bool signaled_;\n  int exit_code_;")
    foreach(ck_pattern ck_join_before ck_member_before)
        string(FIND "${ck_threading_source}" "${${ck_pattern}}" ck_found)
        if(ck_found EQUAL -1)
            message(FATAL_ERROR "SDK threading_posix.cpp changed (${ck_pattern}); review the thread join fix")
        endif()
    endforeach()
    string(REPLACE "${ck_join_before}" "${ck_join_after}" ck_threading_source "${ck_threading_source}")
    string(REPLACE "${ck_member_before}" "${ck_member_after}" ck_threading_source "${ck_threading_source}")
    set(ck_threading_source "#include <atomic>\n${ck_threading_source}")
    set(ck_threading_path "${CMAKE_CURRENT_BINARY_DIR}/ck_threading_posix.cpp")
    if(EXISTS "${ck_threading_path}")
        file(READ "${ck_threading_path}" ck_threading_previous)
    endif()
    if(NOT ck_threading_previous STREQUAL ck_threading_source)
        file(WRITE "${ck_threading_path}" "${ck_threading_source}")
    endif()
    get_target_property(ck_core_sources rexcore SOURCES)
    set(ck_core_replaced "")
    foreach(src IN LISTS ck_core_sources)
        if(src MATCHES "(^|/)threading_posix\\.cpp$")
            continue()
        endif()
        if(IS_ABSOLUTE "${src}" OR src MATCHES "^\\$<")
            list(APPEND ck_core_replaced "${src}")
        else()
            list(APPEND ck_core_replaced "${REXSDK_DIR}/src/core/${src}")
        endif()
    endforeach()
    list(APPEND ck_core_replaced "${ck_threading_path}")
    set_property(TARGET rexcore PROPERTY SOURCES "${ck_core_replaced}")
    target_include_directories(rexcore PRIVATE "${REXSDK_DIR}/src/core")
endif()

if(ANDROID)
    # The shared NFSMW SDK entry point creates the app "nfsmw"; use this project's entry instead.
    get_target_property(ck_app_sources chavokart SOURCES)
    list(FILTER ck_app_sources EXCLUDE REGEX "(^|/)windowed_app_main_sdl\\.cpp$")
    set_property(TARGET chavokart PROPERTY SOURCES "${ck_app_sources}")
    target_sources(chavokart PRIVATE src/platform/android_entry.cpp src/platform/android_touch.cpp)
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
