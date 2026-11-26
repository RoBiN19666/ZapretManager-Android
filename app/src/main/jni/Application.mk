APP_OPTIM := release
# API 21 достаточно для byedpi/hev и совместимо с minSdk 26.
APP_PLATFORM := android-21
APP_ABI := arm64-v8a armeabi-v7a
APP_CFLAGS := -O3
NDK_TOOLCHAIN_VERSION := clang
APP_LDFLAGS := -Wl,-z,max-page-size=16384
# Собираем только разделяемую библиотеку, исполняемый файл стенда не нужен.
APP_MODULES := hev-socks5-tunnel
