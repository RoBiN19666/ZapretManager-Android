# Архитектура

## Процессы

| Процесс | Что делает | Почему отдельный |
| --- | --- | --- |
| основной | UI (Compose), `TgProxyService` (Go), `UpdateManager`, приёмники | — |
| `:native` | `ByeDpiVpnService`, byedpi, tun2socks | byedpi — программа с блокирующим `main()` и без корректной остановки; после `shutdown()` её нельзя запустить повторно, поэтому процесс завершается целиком |

Связь процессов — широковещательные сообщения внутри приложения
(`ACTION_STATE`, `setPackage(...)`, `RECEIVER_NOT_EXPORTED`). Состояние движков
публикуется в `DpiEngine.state` / `TgProxyController.state` (`StateFlow`), UI читает его через
`ZmViewModel`.

## Обход DPI

```
VpnService.Builder
  addAddress 10.10.10.10/32, addRoute 0.0.0.0/0
  split tunneling: addDisallowedApplication / addAllowedApplication
        │ establish()
        ▼
   tun fd ──► libhev-socks5-tunnel.so (tun2socks, lwip)
                    │ SOCKS5 127.0.0.1:1080
                    ▼
              libzapretnative.so → byedpi main(argv)
                    │ desync: split / fake TLS / OOB / disorder
                    ▼
                 интернет
```

* Запуск: `ByeDpiVpnService.startEngine()` → `VpnService.Builder.establish()` →
  `Native.startByedpi(argv)` → ожидание SOCKS5-порта (TCP-проба) → `Native.startTunnel(yaml, fd)`.
* Аргументы: `ciadpi --ip 127.0.0.1 --port 1080 <токены стратегии>`, где токены берутся из
  выбранной стратегии или из пользовательских аргументов (`{sni}` подставляется автоматически).
* Остановка: `Native.stopTunnel()` (hev quit) → `Native.stopByedpi()` (`shutdown(server_fd)`) →
  закрытие tun → `Process.killProcess` процесса `:native`.
* Ошибки старта (нет VPN-разрешения, порт занят, byedpi не поднялся) публикуются как
  `EnginePhase.ERROR` с текстом, UI показывает их на «Обзоре».

## Telegram-прокси

```
Telegram ──tg://proxy──► 127.0.0.1:1443 (libtgwsproxy.so)
                                 │ маршрут (route_order)
                                 ├─ cf_proxy_ws  → Cloudflare Proxy (WS/80 или WSS/443)
                                 ├─ direct_ws    → kws{dc}.web.telegram.org
                                 └─ tcp_fallback → DC:443
```

* Секрет генерируется в Kotlin (`SecureRandom`, 16 байт → 32 hex) и хранится в настройках.
  Go-рантайм принимает только «сырой» секрет; префиксы `dd`/`ee` добавляются при сборке ссылки.
* Конфигурация рантайма — строка токенов `@route_order=...,@route_cf_proxy_ws=1,...`.
  Важно: при наличии `route_order` рантайм считает политику явной и выключает CF, если не передан
  `@route_cf_proxy_ws=1` — поэтому флаги всех маршрутов передаются всегда.
* Статус читается через `GetMtProtoProxyStatus()` (строка `key=value;...`) и обязательно
  освобождается `FreeString`, иначе утечка.
* Пул CF-доменов обновляется из `Flowseal/tg-ws-proxy` и применяется через
  `SetCachedCFDomains` (минимум 3 домена, иначе рантайм молча игнорирует).

## Обновления

| Компонент | Источник | Механизм |
| --- | --- | --- |
| APK менеджера | `RoBiN19666/ZapretManager-Android` releases | GitHub API → проверка версии → загрузка → SHA-256 → подпись/`versionCode` → `PackageInstaller` |
| Стратегии | `romanvht/ByeByeDPI` (`app/src/main/assets/proxytest_strategies.list`) | raw-файл по коммиту, парсинг в `Strategy` |
| Списки сайтов | `romanvht/ByeByeDPI` (`proxytest_*.sites`) | raw-файлы в `files/components/sites` |
| CF-домены | `Flowseal/tg-ws-proxy` (`.github/cfproxy-domains.txt`) | raw-файл + `SetCachedCFDomains` |
| Движки | git-сабмодули | обновляются в репозитории, собираются в APK |

Версии компонентов хранятся в `files/components/meta.json`, поэтому «установлено/доступно»
показывается честно и после перезапуска.

## Самообновление APK

1. `GitHub.latestRelease(SELF_REPO)` → сравнение `versionName`.
2. Выбор ассета по ABI (`Build.SUPPORTED_ABIS`), иначе `universal`, иначе первый `*.apk`.
3. Загрузка с прогрессом в `cache/updates`.
4. Проверки: SHA-256 из `*.apk.sha256`, `packageName`, сертификат подписи (сравнение с
   установленным), `versionCode` строго больше текущего.
5. Установка через `PackageInstaller` (системный диалог), при недоступности — `ACTION_VIEW`
   через `FileProvider`.

## Настройки и состояние

`Settings` — синглтон с `StateFlow<AppSettings>` и `SharedPreferences`. Пишут и UI, и сервисы.
`LogBus` — кольцевой буфер на 800 строк, экспорт в буфер обмена.

## Безопасность

* Все сетевые запросы — HTTPS, сертификаты проверяются системным хранилищем.
* Секрет MTProto и токен GitHub не логируются (в журнал попадает только префикс секрета).
* APK обновления проверяются по подписи — подменить файл без ключа нельзя.
* Разрешение VPN выдаётся пользователем; приложение не является системным VPN по умолчанию.
