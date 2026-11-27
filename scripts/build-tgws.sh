#!/usr/bin/env bash
# Builds the TG WS Proxy Go runtime (libtgwsproxy.so) for Android (Linux/CI).
set -euo pipefail

API_LEVEL="${API_LEVEL:-26}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
module_dir="$repo_root/third_party/tgwsproxy/native/tgwsproxy"
jni_libs="$repo_root/app/src/main/jniLibs"

if [ ! -f "$module_dir/go.mod" ]; then
  echo "Go module not found: $module_dir (git submodule update --init --recursive)" >&2
  exit 1
fi

sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$sdk" ]; then
  echo "ANDROID_SDK_ROOT/ANDROID_HOME is not set" >&2
  exit 1
fi

ndk="${ANDROID_NDK_ROOT:-}"
if [ -z "$ndk" ]; then
  ndk="$(ls -d "$sdk"/ndk/* | sort -V | tail -n 1)"
fi
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"

build_abi() {
  local abi="$1" goarch="$2" goarm="$3" triple="$4"
  local clang="$toolchain/${triple}${API_LEVEL}-clang"
  [ -x "$clang" ] || { echo "Compiler not found: $clang" >&2; exit 1; }
  mkdir -p "$jni_libs/$abi"
  local out="$jni_libs/$abi/libtgwsproxy.so"
  rm -f "$out"
  echo "==> $abi (GOARCH=$goarch)"
  (
    cd "$module_dir"
    # 16K-aligned ELF segments are required by Android 15+ devices with 16K pages.
    GOOS=android GOARCH="$goarch" GOARM="$goarm" CGO_ENABLED=1 CC="$clang" \
      CGO_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384" \
      go build -buildmode=c-shared -trimpath -ldflags="-s -w" -o "$out" .
  )
  rm -f "${out%.so}.h"
  if command -v readelf >/dev/null 2>&1; then
    readelf -l "$out" | grep -q 0x4000 || { echo "ELF is not 16K-aligned: $out" >&2; exit 1; }
  fi
  file "$out"
}

build_abi arm64-v8a arm64 "" aarch64-linux-android
build_abi armeabi-v7a arm 7 armv7a-linux-androideabi
echo "Done: $jni_libs"
