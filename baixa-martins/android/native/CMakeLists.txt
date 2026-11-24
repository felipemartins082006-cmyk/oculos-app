# Whisper (IA que transcreve) compilado para o celular: vira o arquivo libwhispercli.so do APK.
# Código do whisper.cpp (licença MIT) em ./whisper.cpp, só as partes necessárias.
cmake_minimum_required(VERSION 3.14)
project(baixa_whisper C CXX)
set(CMAKE_CXX_STANDARD 17)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE ON CACHE BOOL "" FORCE)
add_subdirectory(whisper.cpp)

set(EX whisper.cpp/examples)
add_executable(whispercli ${EX}/cli/cli.cpp ${EX}/common.cpp ${EX}/common-whisper.cpp ${EX}/common-ggml.cpp ${EX}/grammar-parser.cpp)
target_include_directories(whispercli PRIVATE ${EX})
target_link_libraries(whispercli PRIVATE whisper)
