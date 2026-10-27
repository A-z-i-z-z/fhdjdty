cmake_minimum_required(VERSION 3.22)
project(yerel C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp as shared libraries; CPU backends are separate .so files picked at runtime
set(BUILD_SHARED_LIBS     ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP       OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL            OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL       ON  CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # one CPU backend per SoC feature level (dotprod, fp16, i8mm, SVE2, SME), best one loaded at runtime,
    # plus Arm KleidiAI matmul kernels
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
    set(GGML_CPU_KLEIDIAI     ON CACHE BOOL "" FORCE)
    set(GGML_OPENMP           ON CACHE BOOL "" FORCE)
else()
    # x86_64 is only built for the emulator smoke test: SSE4.2 is what the Android x86_64 ABI
    # guarantees, emulators often lack AVX
    set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_KLEIDIAI     OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
    set(GGML_SSE42 ON  CACHE BOOL "" FORCE)
    set(GGML_AVX   OFF CACHE BOOL "" FORCE)
    set(GGML_AVX2  OFF CACHE BOOL "" FORCE)
    set(GGML_FMA   OFF CACHE BOOL "" FORCE)
    set(GGML_F16C  OFF CACHE BOOL "" FORCE)
    set(GGML_BMI2  OFF CACHE BOOL "" FORCE)
endif()

# Accelerators (arm64 only), each a separate backend library loaded on demand:
#   YEREL_OPENCL_ROOT       include/CL headers + lib/libOpenCL.so to link against (the device's own
#                           libOpenCL.so is used at runtime, it is not packaged)
#   YEREL_HEXAGON_SDK_ROOT  Hexagon SDK, builds the NPU backend and its DSP-side kernels (HTP skels)
set(YEREL_OPENCL_ROOT      "" CACHE PATH "OpenCL headers and link library for the GPU backend")
set(YEREL_HEXAGON_SDK_ROOT "" CACHE PATH "Hexagon SDK for the NPU backend")
set(YEREL_HEXAGON_TOOLS_ROOT "" CACHE PATH "Hexagon tools (default: from the SDK)")
set(YEREL_HTP_VERSIONS v73 v75 v79 v81 v85)

if(ANDROID_ABI STREQUAL "arm64-v8a" AND YEREL_OPENCL_ROOT)
    message(STATUS "yerel: OpenCL GPU backend from ${YEREL_OPENCL_ROOT}")
    set(GGML_OPENCL                    ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_EMBED_KERNELS      ON CACHE BOOL "" FORCE)
    set(OpenCL_INCLUDE_DIR "${YEREL_OPENCL_ROOT}/include"          CACHE PATH     "" FORCE)
    set(OpenCL_LIBRARY     "${YEREL_OPENCL_ROOT}/lib/libOpenCL.so" CACHE FILEPATH "" FORCE)
endif()

if(ANDROID_ABI STREQUAL "arm64-v8a" AND YEREL_HEXAGON_SDK_ROOT)
    message(STATUS "yerel: Hexagon NPU backend from ${YEREL_HEXAGON_SDK_ROOT}")
    set(GGML_HEXAGON       ON CACHE BOOL "" FORCE)
    set(HEXAGON_SDK_ROOT   "${YEREL_HEXAGON_SDK_ROOT}" CACHE PATH "" FORCE)
    if(YEREL_HEXAGON_TOOLS_ROOT)
        set(HEXAGON_TOOLS_ROOT "${YEREL_HEXAGON_TOOLS_ROOT}" CACHE PATH "" FORCE)
    endif()
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(yerel SHARED jni_bridge.cpp engine.cpp)
target_include_directories(yerel PRIVATE ${LLAMA_SRC}/common)
target_link_libraries(yerel llama llama-common android log)
# smaller library, no exported internals
target_link_options(yerel PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)

if(GGML_HEXAGON)
    # the HTP skels run on the DSP; packaged next to the other libraries, the app copies them out
    # of the APK to a directory on ADSP_LIBRARY_PATH before the NPU backend is loaded
    foreach(V ${YEREL_HTP_VERSIONS})
        add_dependencies(yerel htp-${V})
        add_custom_command(TARGET yerel POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
                    ${CMAKE_CURRENT_BINARY_DIR}/build-llama/ggml/src/ggml-hexagon/libggml-htp-${V}.so
                    $<TARGET_FILE_DIR:yerel>/libggml-htp-${V}.so)
    endforeach()
endif()
