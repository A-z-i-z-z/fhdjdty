# Host build of the app's inference engine, for testing with real models off-device.
cmake_minimum_required(VERSION 3.22)
project(yerel-native-test C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   ON  CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../third_party/llama.cpp)
set(APP_CPP   ${CMAKE_CURRENT_LIST_DIR}/../app/src/main/cpp)

add_subdirectory(${LLAMA_SRC} llama.cpp)

add_executable(engine_test engine_test.cpp ${APP_CPP}/engine.cpp)
target_include_directories(engine_test PRIVATE ${APP_CPP} ${LLAMA_SRC}/common)
target_link_libraries(engine_test PRIVATE llama llama-common)
