# Сборка

## Что нужно

| Компонент | Версия | Зачем |
| --- | --- | --- |
| JDK | 17 | Gradle, AGP, keytool |
| Android SDK Platform | 35 | `compileSdk` |
| Android Build-Tools | 35.0.0 | aapt2, d8 |
| Android NDK | 27.0.12077973 | byedpi (C), tun2socks (C), Go-рантайм |
| CMake | 3.22.1 | сборка `libzapretnative.so` |
| Go | 1.25+ | сборка `libtgwsproxy.so` |
| Git | любая | сабмодули |

## Клонирование

```bash
git clone --recursive https://github.com/RoBiN19666/ZapretManager-Android.git
cd ZapretManager-Android
# если клонировали без --recursive:
git submodule update --init --recursive
```

Сабмодули:

| Путь | Репозиторий | Что даёт |
| --- | --- | --- |
| `app/src/main/cpp/byedpi` | hufrea/byedpi | desync-движок |
| `app/src/main/jni/hev-socks5-tunnel` | heiher/hev-socks5-tunnel | tun2socks (с вложенными подмодулями lwip/yaml/task-system) |
| `third_party/tgwsproxy` | Regstar2/tg-ws-proxy-android | Go-рантайм Telegram-прокси |

## Порядок сборки

1. **Go-рантайм** (собирается один раз и при обновлении сабмодуля):

   ```powershell
   # Windows
   $env:ANDROID_SDK_ROOT = "C:\Android\Sdk"
   .\scripts\build-tgws.ps1
   ```

   ```bash
   # Linux / CI
   export ANDROID_SDK_ROOT="$ANDROID_HOME"
   bash scripts/build-tgws.sh
   ```

   Итог: `app/src/main/jniLibs/arm64-v8a/libtgwsproxy.so` и `.../armeabi-v7a/...`.

2. **APK** — Gradle сам вызывает CMake (byedpi) и ndk-build (tun2socks):

   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :app:assembleRelease -PappVersionName=1.0.0 -PappVersionCode=1
   ```

   Готовые файлы: `app/build/outputs/apk/{debug,release}/`.

## Подпись

`keystore/zapretmanager.jks` лежит в репозитории, пароли — `gradle.properties`
(`keystorePassword`, `keyAlias`, `keyPassword`, по умолчанию `zapretmanager`).
Причина: Android не установит обновление поверх приложения с другой подписью, поэтому
самообновление возможно только при стабильном ключе.

Переопределить для приватной сборки:

```bash
./gradlew :app:assembleRelease \
  -PkeystorePassword=... -PkeyAlias=... -PkeyPassword=...
```

или положить свой `keystore/zapretmanager.jks`.

## CI

`.github/workflows/android.yml` — сборка на каждый push в `main` и на теги `v*`:

1. ставит JDK/SDK/NDK/CMake/Go;
2. собирает `libtgwsproxy.so`;
3. собирает release APK с `versionName` из тега или `version.txt`, `versionCode` = номер запуска + 100;
4. публикует GitHub Release с APK и `.sha256`;
5. на push в `main` поднимает patch-версию в `version.txt` (коммит `[skip ci]`).

`.github/workflows/upstream.yml` — раз в неделю обновляет сабмодули из upstream-репозиториев
и пушит изменение; дальше срабатывает обычная сборка и релиз.

## Частые проблемы

| Симптом | Решение |
| --- | --- |
| `Сабмодуль hev-socks5-tunnel не инициализирован` | `git submodule update --init --recursive` |
| `NDK ... не найден` | `sdkmanager "ndk;27.0.12077973"` и `local.properties` с `sdk.dir` |
| `Go не найден` | Установите Go 1.25+, либо `.\scripts\build-tgws.ps1 -GoExe C:\Go\bin\go.exe` |
| `Неверная архитектура ELF` | Не совпали ABI/API при сборке Go: проверьте `$ANDROID_NDK_ROOT` |
| `UnsatisfiedLinkError: jnidispatch` | JNA AAR не попал в APK: проверьте `net.java.dev.jna:jna:5.14.0@aar` |
| APK не устанавливается поверх | Другая подпись: удалите приложение или используйте тот же keystore |
| Android 15+: предупреждение о 16 КБ страницах | В CMake/ndk-build уже задано `-Wl,-z,max-page-size=16384` |
