# Zapret Manager для Android

Единый менеджер обхода блокировок для Android: **обход DPI** (byedpi + hev-socks5-tunnel) и
**локальный MTProto-прокси для Telegram** (рантайм TG WS Proxy) в одном приложении.

[![Android CI](https://github.com/RoBiN19666/ZapretManager-Android/actions/workflows/android.yml/badge.svg)](https://github.com/RoBiN19666/ZapretManager-Android/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/RoBiN19666/ZapretManager-Android?display_name=tag)](https://github.com/RoBiN19666/ZapretManager-Android/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

Продолжение десктопного [ZapretManager](https://github.com/RoBiN19666/ZapretManager-Desktop) на Android:
те же принципы (стратегии, автообновления, диагностика), но с учётом мобильных ограничений —
без root, без системных iptables, всё через `VpnService` и локальный прокси. Иконка и логотип
взяты из десктопной версии.

## Что умеет

| Раздел | Возможности |
| --- | --- |
| **Обзор** | Два независимых движка: обход DPI и Telegram-прокси. Статус, uptime, активная стратегия, проверка сервисов через SOCKS-порт byedpi (YouTube/Discord/Telegram/Cloudflare), живой журнал, кнопка подключения Telegram |
| **Стратегии** | Четыре источника: встроенные наборы, авторские пресеты byedpi, список ByeByeDPI и свои (ручной ввод + импорт списком). Поиск, счётчики по источникам, мгновенное применение. **Тест всех стратегий на живом туннеле** (YouTube/Discord/Telegram/Cloudflare) с выбором лучшей. **Глубокий автоподбор параметров**: split/disorder/OOB/fake TLS/tlsrec/mod-http, комбинации и модификаторы — с сохранением собранной команды |
| **Telegram** | Локальный MTProto-прокси на `127.0.0.1`, маршруты `cf_proxy_ws` / `direct_ws` / `tcp_fallback`, Fake TLS домен, ссылки `tg://`, `t.me`, SOCKS5, автозапуск |
| **Обновления** | Самообновление APK из GitHub Releases (SHA-256 + подпись + versionCode), обновление стратегий и списков сайтов из ByeByeDPI, пула Cloudflare-доменов из Flowseal/tg-ws-proxy |
| **Настройки** | Тема (системная/светлая/тёмная), автозапуск, DNS, IPv6, режимы раздельного туннелирования (все / кроме выбранных / только выбранные), настройки энергии, **редактор списков сайтов (8 файлов, дубли, откат)**, **проверка сайта (DNS → TCP → TLS → HTTP)**, **резервная копия и восстановление**, **полная диагностика (23 проверки с кнопками исправления)**, экспорт журнала |
| **Плитка** | Быстрое включение обхода из шторки (Quick Settings) |

## Как это устроено

```
┌─────────────────────────── приложение ───────────────────────────┐
│                                                                  │
│  UI-процесс (Compose)                                            │
│    ├── DpiEngine        — управление VpnService                  │
│    ├── TgProxyController— управление Telegram-прокси             │
│    └── UpdateManager    — компоненты и самообновление            │
│                                                                  │
│  Процесс :native                     Главный процесс             │
│    ByeDpiVpnService                    TgProxyService            │
│      VpnService.Builder                  libtgwsproxy.so (Go)    │
│        tun-fd                                  │                 │
│        ↓                                       │                 │
│      libhev-socks5-tunnel.so  (tun2socks)      │                 │
│        ↓ SOCKS5 127.0.0.1:1080                 │                 │
│      libzapretnative.so (byedpi, desync)       │                 │
└──────────────────────────────────────────────────────────────────┘
```

* **Обход DPI** — `VpnService` поднимает tun, весь трафик попадает в userspace-стек
  `hev-socks5-tunnel`, оттуда — в `byedpi`, который выполняет desync-трюки (split, fake TLS,
  OOB, disorder). Приложения видят обычную сеть; root не нужен.
* **Telegram** — локальный MTProto-прокси слушает `127.0.0.1:1443`, Telegram подключается по
  ссылке `tg://proxy`, трафик уходит через Cloudflare Proxy по WebSocket. Системный VPN для
  этого не требуется, остальные приложения идут напрямую.
* Движки живут в сабмодулях (`third_party/tgwsproxy`, `app/src/main/cpp/byedpi`,
  `app/src/main/jni/hev-socks5-tunnel`) и собираются из исходников при сборке APK.

## Установка

1. Скачайте APK из [Releases](https://github.com/RoBiN19666/ZapretManager-Android/releases).
2. Разрешите установку из этого источника (Android спросит сам).
3. Для обхода DPI подтвердите запрос VPN при первом включении.
4. Для Telegram: включите прокси → «Применить в Telegram» → подтвердите.

Требования: Android 8.0+ (`minSdk 26`), ARM64 (`arm64-v8a`) или ARMv7 (`armeabi-v7a`).

## Обновления

* **Менеджер** обновляется из собственных GitHub Releases: приложение сравнивает версии,
  скачивает APK, проверяет SHA-256 из релиза, сертификат подписи и `versionCode`, затем
  передаёт файл системному установщику. Репозиторий публичный, поэтому проверка и загрузка
  работают без авторизации.
* **Стратегии и списки сайтов** — из репозитория `romanvht/ByeByeDPI` (по коммитам).
* **Пул Cloudflare-доменов** — из `Flowseal/tg-ws-proxy`.
* **Движки** собираются в APK, поэтому обновляются вместе с приложением; GitHub Actions
  еженедельно проверяет upstream-репозитории и поднимает сабмодули (см. `upstream.yml`).

## Сборка

Нужны JDK 17, Android SDK 35, NDK 27, CMake 3.22.1 и Go 1.25+.

```bash
git clone --recursive https://github.com/RoBiN19666/ZapretManager-Android.git
cd ZapretManager-Android

# 1. Go-рантайм Telegram → app/src/main/jniLibs/<abi>/libtgwsproxy.so
bash scripts/build-tgws.sh          # Windows: .\scripts\build-tgws.ps1

# 2. byedpi + tun2socks собираются Gradle (CMake + ndk-build) автоматически
./gradlew :app:assembleRelease -PappVersionName=1.0.0 -PappVersionCode=1
```

APK: `app/build/outputs/apk/release/`. Debug-сборка: `./gradlew :app:assembleDebug`.

Подробнее — [docs/BUILD.md](docs/BUILD.md).

## Подпись

Релизы подписываются ключом `keystore/zapretmanager.jks` (пароль в `gradle.properties` по
умолчанию `zapretmanager`). Ключ лежит в репозитории осознанно: без него невозможны
обновления поверх установленной версии. **Не используйте этот ключ ни для чего другого.**
Для приватной сборки замените keystore и пароли — но тогда обновления потребуют переустановки.

## Ограничения

* Прокси для Telegram — не системный VPN: он обрабатывает только то, что Telegram отправляет
  в локальный прокси.
* `awg_warp`-маршрут (AmneziaWG/WARP) в этой сборке не включён: используется проверенный набор
  `cf_proxy_ws` / `direct_ws` / `tcp_fallback`.
* Один экземпляр byedpi на процесс: остановка обхода завершает процесс `:native` — так
  задумано в движке, UI-процесс не страдает.
* Нет автоматического переподключения при смене сети: после смены Wi-Fi ↔ мобильной сети
  обход может потребовать перезапуска.

## Благодарности и лицензии

Проект не содержит чужого кода в исходниках, но **линкует** GPL-компонент, поэтому
распространяется по **GPL-3.0** (см. [LICENSE](LICENSE)).

* [hufrea/byedpi](https://github.com/hufrea/byedpi) — desync-движок (MIT).
* [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) — tun2socks (MIT).
* [romanvht/ByeByeDPI](https://github.com/romanvht/ByeByeDPI) — Android-обвязка VpnService и списки стратегий (GPL-3.0).
* [Regstar2/tg-ws-proxy-android](https://github.com/Regstar2/tg-ws-proxy-android) — Go-рантайм MTProto-прокси (GPL-3.0).
* [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) — пул Cloudflare-доменов.
* [bol-van/zapret](https://github.com/bol-van/zapret) — идеи desync-обхода, вдохновившие десктопную версию.

Автор: RoBiN19666. Проект не связан с Telegram, Cloudflare и перечисленными авторами.
