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
   ./gradlew :app:assembleRelease -PappVersionName=2.0.0 -PappVersionCode=200
   ```

   Готовые файлы: `app/build/outputs/apk/{debug,release}/`. Сборка идёт **по ABI**:

   * `app-arm64-v8a-release-unsigned.apk` (≈6.4 МБ)
   * `app-armeabi-v7a-release-unsigned.apk` (≈6.6 МБ)
   * `app-universal-release-unsigned.apk` (≈11 МБ) — оба комплекта нативных библиотек

   ABI-сплиты включены в `app/build.gradle.kts` (`splits { abi { … } }`). CI подписывает
   все три и публикует как `ZapretManager-Android-<abi>-v<версия>.apk` с отдельными
   `.sha256` и общим `SHA256SUMS.txt`; приложение выбирает нужный файл само по
   `Build.SUPPORTED_ABIS`.

## CI и релизы

Сборка запускается **не на каждый коммит**, а только когда нужен релиз:

1. **Ручной запуск** — GitHub → Actions → **Android CI** → *Run workflow* → при желании указать
   версию (пусто — из `version.txt`). То же из консоли:

   ```powershell
   .\scripts\release.ps1                 # версия из version.txt
   .\scripts\release.ps1 -Version 1.1.0  # конкретная версия
   ```

2. **Тег** — `git tag v1.1.0 && git push origin v1.1.0`.

Что делает workflow:

* запускает unit-тесты и `lintDebug`;
* собирает `libtgwsproxy.so` и release APK с версией из `version.txt` и
  `versionCode` из `version-code.txt`;
* в отдельном release-job готовит публикуемый APK и контрольные суммы
  (`*.apk.sha256`, `SHA256SUMS.txt`) и проверяет их;
* публикует GitHub Release, **текст которого берётся из раздела `## <версия>` в
  [CHANGELOG.md](../CHANGELOG.md)** — поэтому перед релизом допишите туда, что изменилось;
* при ручном запуске поднимает patch-версию в `version.txt` и `version-code.txt`
  (коммит `[skip ci]`), чтобы следующий релиз не совпал с предыдущим.

Обычные коммиты и pull request-ы проверяются отдельным workflow **Check**
(`.github/workflows/check.yml`): он устанавливает SDK/NDK и запускает
`:app:testDebugUnitTest :app:lintDebug`, ничего не публикуя.

`.github/workflows/upstream.yml` по-прежнему раз в неделю подтягивает сабмодули из upstream —
но это только коммит в репозиторий, релиз после него не собирается автоматически.

## Частые проблемы

| Симптом | Решение |
| --- | --- |
| `Сабмодуль hev-socks5-tunnel не инициализирован` | `git submodule update --init --recursive` |
| `NDK ... не найден` | `sdkmanager "ndk;27.0.12077973"` и `local.properties` с `sdk.dir` |
| `Go не найден` | Установите Go 1.25+, либо `.\scripts\build-tgws.ps1 -GoExe C:\Go\bin\go.exe` |
| `Неверная архитектура ELF` | Не совпали ABI/API при сборке Go: проверьте `$ANDROID_NDK_ROOT` |
| `UnsatisfiedLinkError: dlopen failed: libtgwsproxy.so` | Go-рантайм не собрался или не попал в `jniLibs`: выполните `scripts/build-tgws.*` и проверьте, что файл 16 КБ-выровнен |
| `Не инициализированы сабмодули` | `git submodule update --init --recursive` (Gradle сам скажет, каких файлов не хватает) |
| В debug-сборке Telegram пишет «Go-рантайм недоступен» | `libtgwsproxy.so` не собрана: Gradle предупреждает об этом при сборке, выполните `scripts/build-tgws.*` |
| Android 15+: предупреждение о 16 КБ страницах | В CMake/ndk-build уже задано `-Wl,-z,max-page-size=16384` |

