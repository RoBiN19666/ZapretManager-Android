# Иконки приложения

Здесь лежат исходники фирменного знака Zapret Manager, перенесённые из
[ZapretManager-Desktop](https://github.com/RoBiN19666/ZapretManager-Desktop) (MIT, автор RoBiN19666):

| Файл | Что это |
| --- | --- |
| `source-icon.png` | Основной арт 1254×1254 с прозрачными полями — источник всех иконок |
| `app-icon.png` | Готовая иконка 256×256 (как в десктопной версии) |
| `logo-wide.png` | Широкий логотип для экрана «О программе» |

Android-ресурсы пересобираются скриптом:

```bash
python tools/make_icons.py
```

Скрипт создаёт `mipmap-*/ic_launcher.png`, `mipmap-*/ic_launcher_round.png`,
`drawable-nodpi/ic_launcher_foreground.png` (adaptive-иконка), а также
`drawable-nodpi/logo_zm.png` и `logo_wide.png` для интерфейса.

Правила: арт обрезается по непрозрачным пикселям, фон — `#0A0A09`, для adaptive-иконки
арт занимает видимую область 72dp из 108dp, поэтому рамка знака остаётся целой под маской лаунчера.
