#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 R. Kravcov
"""Builds the press kit under docs/press from the staged screenshots in docs/screenshots.

Writes the dial shots at their native 454 px and at 2x, and composes the 1280 x 640 social
preview GitHub shows when the repository is shared — the dial on the left, the name and three
facts on the right, on the dial's own black. Re-run after a re-shoot; the text lives here so the
picture cannot drift from the README.

    python tools/press/make_press_kit.py
"""
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SHOTS = os.path.join(ROOT, "docs", "screenshots")
OUT = os.path.join(ROOT, "docs", "press")

# (source in docs/screenshots, published name, caption for the kit's README)
SHOTS_TO_SHIP = [
    ("earth.png", "mfd-24-dial.png", "The dial, a watch under way at New York JFK"),
    ("ambient.png", "mfd-24-always-on.png", "The same face in always-on"),
    ("duty-arc.png", "mfd-24-duty-arc.png", "A booked watch on the 24-hour arc"),
    ("mandown.png", "mfd-24-man-down.png", "An unanswered check: MAN DOWN on the dial"),
    ("settings-sections.png", "mfd-24-settings.png", "The settings editor on the watch"),
]

GREEN = (0, 255, 0)
DIM = (0, 150, 0)
GREY = (170, 170, 170)


def font(name, size):
    for candidate in (r"C:\Windows\Fonts\%s" % name, "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(candidate):
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def social_preview():
    W, H = 1280, 640
    img = Image.new("RGB", (W, H), (0, 0, 0))
    dial = Image.open(os.path.join(SHOTS, "earth.png")).convert("RGBA")
    side = 560
    dial = dial.resize((side, side), Image.LANCZOS)
    img.paste(dial, (40, (H - side) // 2), dial)
    d = ImageDraw.Draw(img)
    x = 660
    d.text((x, 120), "MFD-24", font=font("consolab.ttf", 96), fill=GREEN)
    d.text((x, 232), "A 24-hour instrument dial for Wear OS", font=font("segoeui.ttf", 34), fill=GREY)
    lines = [
        "Duty arc and a dead-man's monitor",
        "METAR, sun and moon, nearest field or port",
        "Offline. No account, no cloud. GPL-3.0",
    ]
    y = 320
    for line in lines:
        # A square bullet drawn by hand: the arrow glyph is not in every build of Segoe UI.
        d.rectangle((x, y + 11, x + 10, y + 21), fill=GREEN)
        d.text((x + 24, y), line, font=font("segoeui.ttf", 27), fill=GREEN)
        y += 52
    d.text((x, 540), "github.com/amorroma1/mfd-24", font=font("consola.ttf", 28), fill=DIM)
    path = os.path.join(OUT, "social-preview.png")
    img.save(path, optimize=True)
    print(os.path.relpath(path, ROOT), img.size)


def shots():
    for src, name, _ in SHOTS_TO_SHIP:
        im = Image.open(os.path.join(SHOTS, src)).convert("RGBA")
        bg = Image.new("RGB", im.size, (0, 0, 0))
        bg.paste(im, (0, 0), im)
        p1 = os.path.join(OUT, name)
        bg.save(p1, optimize=True)
        big = bg.resize((bg.width * 2, bg.height * 2), Image.LANCZOS)
        p2 = os.path.join(OUT, name.replace(".png", "@2x.png"))
        big.save(p2, optimize=True)
        print(os.path.relpath(p1, ROOT), bg.size, "and @2x", big.size)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    shots()
    social_preview()
