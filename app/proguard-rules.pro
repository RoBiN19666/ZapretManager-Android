# JNI: имена классов и методов входят в символы C (zm_jni.c / zm_tg_jni.c).
-keep class io.github.robin19666.zapretmanager.byedpi.Native { *; }
-keep class io.github.robin19666.zapretmanager.tgproxy.TgNative { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Компоненты, которые система создаёт по имени из манифеста.
-keep class io.github.robin19666.zapretmanager.byedpi.ByeDpiVpnService { *; }
-keep class io.github.robin19666.zapretmanager.tgproxy.TgProxyService { *; }
-keep class io.github.robin19666.zapretmanager.core.BootReceiver { *; }
-keep class io.github.robin19666.zapretmanager.core.InstallResultReceiver { *; }
-keep class io.github.robin19666.zapretmanager.core.RestartReceiver { *; }
-keep class io.github.robin19666.zapretmanager.tile.ZapretTileService { *; }
-keep class io.github.robin19666.zapretmanager.ui.MainActivity { *; }

# Enum-имена используются в SharedPreferences и broadcast-extra (valueOf/name).
-keepclassmembers enum * { *; }

# Compose/R8 обычно справляется сам; эти правила нужны для отражения/сериализации.
-keepattributes *Annotation*
-dontwarn org.jetbrains.annotations.**
