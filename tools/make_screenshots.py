import base64
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(r"C:\DeepSeek_Projects\ZapretManager-Android")
OUT = ROOT / "assets" / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

CSS = """
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  width: 412px; height: 915px; overflow: hidden;
  background: #0B1017; color: #E6EDF5;
  font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
  display: flex; flex-direction: column;
}
.status { display:flex; justify-content:space-between; padding:10px 18px 2px; font-size:12px; color:#93A4B8; }
.header { padding: 10px 18px 6px; }
.header h1 { font-size: 22px; font-weight: 600; letter-spacing: -0.4px; }
.header p { font-size: 12px; color:#93A4B8; margin-top:3px; }
.content { flex:1; padding: 6px 14px 8px; display:flex; flex-direction:column; gap:10px; overflow:hidden; }
.card { background:#121A24; border-radius:16px; padding:14px; border:1px solid #1F2A38; }
.card h2 { font-size:14px; font-weight:600; margin-bottom:6px; }
.row { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.muted { color:#93A4B8; font-size:11px; line-height:1.45; }
.accent { color:#31D0AA; }
.warn { color:#FFC857; }
.error { color:#FF6B6B; }
.chip { display:inline-flex; align-items:center; gap:6px; padding:7px 12px; border-radius:20px;
  background:#18222F; color:#E6EDF5; font-size:11px; border:1px solid #1F2A38; }
.chip.on { background: rgba(49,208,170,.16); color:#31D0AA; border-color: rgba(49,208,170,.35); }
.big { font-size:20px; font-weight:600; }
.toggle { width:52px; height:30px; border-radius:16px; background:#31D0AA; position:relative; flex:0 0 auto; }
.toggle:after { content:''; position:absolute; width:24px; height:24px; border-radius:12px; background:#04231C; top:3px; right:3px; }
.btn { display:flex; align-items:center; justify-content:center; padding:11px; border-radius:12px;
  background: rgba(49,208,170,.16); color:#31D0AA; font-size:13px; font-weight:600; }
.btn.ghost { background:#18222F; color:#E6EDF5; }
.list { display:flex; flex-direction:column; gap:8px; }
.item { background:#121A24; border:1px solid #1F2A38; border-radius:14px; padding:12px; display:flex; justify-content:space-between; gap:8px; align-items:center; }
.item .title { font-size:13px; }
.item .sub { font-size:10px; color:#93A4B8; margin-top:2px; }
.badge { font-size:9px; padding:3px 7px; border-radius:8px; background:#18222F; color:#93A4B8; }
.badge.ok { background: rgba(49,208,170,.16); color:#31D0AA; }
.badge.err { background: rgba(255,107,107,.15); color:#FF6B6B; }
.nav { height:64px; border-top:1px solid #1F2A38; display:flex; background:#0B1017; }
.nav div { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:3px; font-size:9px; color:#93A4B8; }
.nav div.active { color:#31D0AA; }
.dot { width:5px; height:5px; border-radius:3px; background:currentColor; }
.bar { height:6px; border-radius:3px; background:#18222F; overflow:hidden; }
.bar > i { display:block; height:100%; width:62%; background:#31D0AA; }
.kv { display:flex; justify-content:space-between; font-size:11px; padding:4px 0; }
.kv span:last-child { color:#E6EDF5; }
.swatches { display:flex; gap:8px; margin-top:8px; }
.sw { width:26px; height:26px; border-radius:13px; border:2px solid transparent; }
.sw.sel { border-color:#E6EDF5; }
"""

def screen(title, subtitle, body, active):
    nav_items = [("Обзор", "overview"), ("Стратегии", "strategies"), ("Telegram", "telegram"), ("Релизы", "releases"), ("Ещё", "more")]
    nav = "".join(
        f'<div class="{"active" if key == active else ""}"><span class="dot"></span>{label}</div>'
        for label, key in nav_items
    )
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head>
<body>
  <div class="status"><span>9:41</span><span>LTE &nbsp; 82%</span></div>
  <div class="header"><h1>{title}</h1><p>{subtitle}</p></div>
  <div class="content">{body}</div>
  <div class="nav">{nav}</div>
</body></html>"""

overview = screen("Zapret Manager", "Ускорение соединения и Telegram-прокси", """
  <div class="card">
    <div class="row">
      <div>
        <h2>Ускорение соединения</h2>
        <div class="muted">Стратегия «51. -o2 -d6+s -q5+hm»</div>
      </div>
      <div class="toggle"></div>
    </div>
    <div style="margin-top:10px" class="row">
      <span class="badge ok">● Работает</span>
      <span class="muted">включено 12 мин</span>
    </div>
  </div>
  <div class="card">
    <div class="row">
      <div>
        <h2>Telegram-прокси</h2>
        <div class="muted">Порт 1443 · Cloudflare WSS</div>
      </div>
      <span class="badge ok">Работает</span>
    </div>
    <div class="btn" style="margin-top:10px">Применить в Telegram</div>
  </div>
  <div class="card">
    <h2>Проверка сервисов</h2>
    <div class="kv"><span>Cloudflare</span><span class="accent">18 мс</span></div>
    <div class="kv"><span>YouTube</span><span class="accent">96 мс</span></div>
    <div class="kv"><span>Telegram</span><span class="accent">41 мс</span></div>
    <div class="btn ghost" style="margin-top:8px">Проверить сайт</div>
  </div>
  <div class="card">
    <div class="row"><div><h2>Сеть</h2><div class="muted">Домашний Wi-Fi · профиль «Дом»</div></div><span class="badge">Wi-Fi</span></div>
    <div class="muted" style="margin-top:6px">Сторож включён · проверка раз в 2 минуты</div>
  </div>
""", "overview")

strategies = screen("Стратегии", "66 стратегий · выбрана «51. -o2 -d6+s -q5+hm»", """
  <div class="card">
    <h2>Источники</h2>
    <div class="muted">Встроенные, авторские и загруженные из upstream. Обновление — из GitHub.</div>
    <div class="btn ghost" style="margin-top:8px">Обновить стратегии</div>
  </div>
  <div class="card">
    <h2>Проверка стратегий</h2>
    <div class="muted">4 цели · задержка и доступность</div>
    <div class="bar" style="margin-top:8px"><i></i></div>
    <div class="muted" style="margin-top:6px">12 из 20 · 34 мс</div>
    <div class="btn" style="margin-top:8px">Запустить тест</div>
  </div>
  <div class="list">
    <div class="item"><div><div class="title">51. -o2 -d6+s -q5+hm</div><div class="sub">4/4 · 34 мс</div></div><span class="badge ok">выбрана</span></div>
    <div class="item"><div><div class="title">12. --split 1 --disorder 1</div><div class="sub">4/4 · 41 мс</div></div><span class="badge ok">ок</span></div>
    <div class="item"><div><div class="title">7. --fake-tls-mod</div><div class="sub">3/4 · 58 мс</div></div><span class="badge">частично</span></div>
    <div class="item"><div><div class="title">33. --oob 1 --split 2</div><div class="sub">2/4 · 74 мс</div></div><span class="badge">частично</span></div>
  </div>
""", "strategies")

telegram = screen("Telegram", "Локальный MTProto-прокси поверх Cloudflare", """
  <div class="card">
    <div class="row"><div><h2>Прокси</h2><div class="muted">127.0.0.1:1443 · секрет dd</div></div><div class="toggle"></div></div>
    <div style="margin-top:10px" class="row"><span class="badge ok">● Работает</span><span class="muted">маршрут CF WSS/443</span></div>
    <div class="btn" style="margin-top:10px">Применить в Telegram</div>
    <div class="btn ghost" style="margin-top:8px">Копировать отчёт</div>
  </div>
  <div class="card">
    <h2>Параметры</h2>
    <div class="kv"><span>Порт</span><span>1443</span></div>
    <div class="kv"><span>Маршрут</span><span>Cloudflare WS → WSS</span></div>
    <div class="kv"><span>Fake TLS домен</span><span>авто</span></div>
    <div class="kv"><span>CF-домены</span><span>20 шт · 0 ч назад</span></div>
  </div>
  <div class="card">
    <h2>Подключение</h2>
    <div class="muted">Ссылка tg://proxy?server=127.0.0.1&amp;port=1443&amp;secret=dd…</div>
    <div class="btn ghost" style="margin-top:8px">Скопировать ссылку</div>
  </div>
  <div class="card">
    <div class="kv"><span>Проверка SOCKS</span><span class="accent">OK · 23 мс</span></div>
    <div class="kv"><span>Состояние</span><span class="accent">running</span></div>
  </div>
""", "telegram")

settings = screen("Настройки", "Поведение приложения, туннель и диагностика", """
  <div class="card">
    <h2>Оформление</h2>
    <div class="muted">Тема интерфейса</div>
    <div style="display:flex; gap:8px; margin-top:8px">
      <span class="chip on">Как в системе</span><span class="chip">Светлая</span><span class="chip">Тёмная</span>
    </div>
    <div class="muted" style="margin-top:8px">Цвет акцента</div>
    <div class="swatches">
      <span class="sw sel" style="background:#31D0AA"></span><span class="sw" style="background:#4C8DFF"></span>
      <span class="sw" style="background:#A78BFA"></span><span class="sw" style="background:#4ADE80"></span>
      <span class="sw" style="background:#FF9F43"></span><span class="sw" style="background:#FF6B6B"></span>
    </div>
  </div>
  <div class="card">
    <h2>Туннель</h2>
    <div class="kv"><span>Чёрный и белый списки</span><span>Белый список</span></div>
    <div class="kv"><span>Приложений в списке</span><span>19</span></div>
    <div class="kv"><span>Домены для ускорения</span><span>54 · исключений 3</span></div>
  </div>
  <div class="card">
    <div class="row"><div><h2>Сторож</h2><div class="muted">Проверка раз в 2 минуты</div></div><div class="toggle"></div></div>
  </div>
  <div class="card">
    <div class="kv"><span>Режим батареи</span><span class="accent">без ограничений</span></div>
    <div class="kv"><span>Хранилище</span><span>41,2 МБ · кэш 0,8 МБ</span></div>
    <div class="btn ghost" style="margin-top:8px">Настройки приложения в системе</div>
  </div>
  <div class="card">
    <div class="row"><div><h2>Диагностика</h2><div class="muted">23 проверки с исправлениями</div></div><span class="badge ok">OK</span></div>
  </div>
""", "more")

screens = {
    "overview": overview,
    "strategies": strategies,
    "telegram": telegram,
    "settings": settings,
}

for name, html in screens.items():
    html_path = OUT / f"{name}.html"
    html_path.write_text(html, encoding="utf-8")
    png = OUT / f"{name}.png"
    cmd = [
        EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars",
        "--force-device-scale-factor=2", "--window-size=412,915",
        f"--screenshot={png}", f"file:///{html_path.as_posix()}",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    print(name, "rc=", result.returncode, "png=", png.exists(), png.stat().st_size if png.exists() else 0)
    html_path.unlink(missing_ok=True)
