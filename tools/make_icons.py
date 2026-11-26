#!/usr/bin/env python3
"""Генерация Android-иконок из оригинального арта Zapret Manager.

Источник: assets/icon/source-icon.png (1254x1254, прозрачные поля) и
assets/icon/logo-wide.png. Скрипт создаёт:
  * mipmap-<density>/ic_launcher.png и ic_launcher_round.png   — легаси-иконки;
  * drawable-nodpi/ic_launcher_foreground.png                 — слой adaptive-иконки;
  * drawable-nodpi/logo_zm.png, logo_wide.png                 — логотипы внутри приложения.

Запуск: python tools/make_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE_ICON = ROOT / "assets" / "icon" / "source-icon.png"
SOURCE_WIDE = ROOT / "assets" / "icon" / "logo-wide.png"
RES = ROOT / "app" / "src" / "main" / "res"

BACKGROUND = (10, 10, 9, 255)
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE_SIZE = 432  # 108dp при xxxhdpi
ADAPTIVE_ART = 288   # 72dp: видимая область adaptive-иконки, рамка арта остаётся целой


def load_art() -> Image.Image:
    image = Image.open(SOURCE_ICON).convert("RGBA")
    # Обрезаем прозрачные поля, чтобы арт занимал весь холст.
    alpha = image.getchannel("A").point(lambda a: 255 if a > 10 else 0)
    bbox = alpha.getbbox()
    return image.crop(bbox) if bbox else image


def square_icon(art: Image.Image, size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), BACKGROUND)
    canvas.alpha_composite(art.resize((size, size), Image.LANCZOS))
    return canvas


def round_icon(art: Image.Image, size: int) -> Image.Image:
    square = square_icon(art, size)
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(square, (0, 0), mask)
    return result


def main() -> None:
    art = load_art()

    for density, size in DENSITIES.items():
        target = RES / f"mipmap-{density}"
        target.mkdir(parents=True, exist_ok=True)
        square_icon(art, size).convert("RGB").save(target / "ic_launcher.png", optimize=True)
        round_icon(art, size).save(target / "ic_launcher_round.png", optimize=True)

    drawable = RES / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)

    # Adaptive-иконка: арт занимает видимую область 72dp, поля 108dp прозрачные.
    foreground = Image.new("RGBA", (ADAPTIVE_SIZE, ADAPTIVE_SIZE), (0, 0, 0, 0))
    offset = (ADAPTIVE_SIZE - ADAPTIVE_ART) // 2
    foreground.alpha_composite(
        art.resize((ADAPTIVE_ART, ADAPTIVE_ART), Image.LANCZOS), (offset, offset)
    )
    foreground.save(drawable / "ic_launcher_foreground.png", optimize=True)

    art.resize((256, 256), Image.LANCZOS).save(drawable / "logo_zm.png", optimize=True)

    wide = Image.open(SOURCE_WIDE).convert("RGBA")
    wide.thumbnail((720, 720), Image.LANCZOS)
    wide.save(drawable / "logo_wide.png", optimize=True)

    print("Иконки сгенерированы в", RES)


if __name__ == "__main__":
    main()
