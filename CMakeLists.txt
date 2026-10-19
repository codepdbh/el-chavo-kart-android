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
