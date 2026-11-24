#!/bin/bash
# Gera as partes nativas do APK (pasta app/src/main/jniLibs/arm64-v8a):
#   libbaixa.so          o programa Baixa Martins (Go), o mesmo do PC, rodando dentro do celular
#   libwhispercli.so     Whisper para celulares novos (ARMv8.2: dotprod/fp16, bem mais rápido)
#   libwhispercli_v8.so  Whisper para celulares antigos (ARMv8.0)
# Precisa do Android NDK (ANDROID_NDK) e do Go 1.26+. Uso: ./build-native.sh
set -euo pipefail
cd "$(dirname "$0")"
NDK=${ANDROID_NDK:-$(ls -d ${ANDROID_HOME:-/opt/android-sdk}/ndk/* | sort -V | tail -1)}
OUT=app/src/main/jniLibs/arm64-v8a
mkdir -p "$OUT"

build_whisper() { # $1 = pasta, $2 = arquitetura, $3 = arquivo de saída
  cmake -S native -B "$1" -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static \
    -DGGML_CPU_ARM_ARCH="$2" > /dev/null
  cmake --build "$1" --target whispercli -j"$(nproc)" > /dev/null
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" -o "$OUT/$3" "$1/whispercli"
}
build_whisper build-native/v82 "armv8.2-a+dotprod+fp16" libwhispercli.so
build_whisper build-native/v8 "armv8-a" libwhispercli_v8.so

CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang" \
  CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  go build -C .. -trimpath -ldflags "-s -w" -o "android/$OUT/libbaixa.so" ./agente
ls -la "$OUT"
