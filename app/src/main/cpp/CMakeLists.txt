cmake_minimum_required(VERSION 3.22.1)
project(trispeech_whisper LANGUAGES C CXX)

add_library(trispeech_whisper SHARED whisper_jni.cpp)
find_library(log-lib log)

# The vendored official whisper.cpp checkout is linked into the JNI bridge.
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/CMakeLists.txt")
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    add_subdirectory(whisper.cpp)
    target_compile_definitions(trispeech_whisper PRIVATE TRISPEECH_HAS_WHISPER=1)
    target_link_libraries(trispeech_whisper whisper ${log-lib})
else()
    target_link_libraries(trispeech_whisper ${log-lib})
endif()
