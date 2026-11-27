cmake_minimum_required(VERSION 3.22.1)

project(zapretnative C)

set(BYEDPI_DIR ${CMAKE_CURRENT_SOURCE_DIR}/byedpi)

# Исходники byedpi подключаются целиком, кроме Windows-службы.
file(GLOB BYEDPI_SRC ${BYEDPI_DIR}/*.c)
list(REMOVE_ITEM BYEDPI_SRC ${BYEDPI_DIR}/win_service.c)

add_library(zapretnative SHARED ${BYEDPI_SRC} zm_jni.c zm_tg_jni.c)

target_include_directories(zapretnative PRIVATE ${BYEDPI_DIR})

target_compile_options(zapretnative PRIVATE
    -std=c99 -O2 -Wall -Wno-unused -Wextra -Wno-unused-parameter)

# ANDROID_APP включает в byedpi ветки для Android (нет daemon(), другой путь сокетов).
target_compile_definitions(zapretnative PRIVATE ANDROID_APP)

target_link_libraries(zapretnative PRIVATE android log dl)

# В bionic нет daemon(); byedpi вызывает её в некоторых режимах.
target_link_options(zapretnative PRIVATE "-Wl,--wrap=daemon")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
