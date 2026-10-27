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

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(yerel SHARED jni_bridge.cpp engine.cpp)
target_include_directories(yerel PRIVATE ${LLAMA_SRC}/common)
target_link_libraries(yerel llama llama-common android log)
# smaller library, no exported internals
target_link_options(yerel PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
