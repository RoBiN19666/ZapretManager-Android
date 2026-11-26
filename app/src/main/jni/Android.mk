LOCAL_PATH := $(call my-dir)

# tun2socks собирается штатным ndk-build из сабмодуля hev-socks5-tunnel
# (у него нет CMakeLists, зато есть готовый Android.mk со всеми зависимостями).
include $(LOCAL_PATH)/hev-socks5-tunnel/Android.mk
