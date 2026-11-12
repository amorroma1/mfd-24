#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 R. Kravcov
"""Renders the three sensor slots as they sit beside the hub, for the documentation.

Each picture is the slot's pictogram — the heart and the walking figure straight out of
`Glyphs.kt`, through the same parser `preview_glyphs.py` uses, or the letters `QFE` — beside a
sample value, in phosphor green on black, at twice the dial's scale so a README cell stays crisp.
Writes `docs/media/slot-heart.png`, `slot-steps.png` and `slot-qfe.png`, and beside them the
eight site pictograms of the site table — civil in the lume, military in the accent, at the
22 px they are drawn at, doubled — as `site-<type>-civil.png` and `site-<type>-military.png`.

    python tools/poi/slot_pictures.py
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import preview_glyphs  # noqa: E402  (the parser and the rasteriser live there)

OUT = os.path.join(HERE, "..", "..", "docs", "media")
GREEN = (0, 255, 0)
SCALE = 2                      # twice the 454 px dial
TEXT_PX = 26 * SCALE           # the readout's type, roughly, at that scale
GLYPH_PX = int(TEXT_PX * 0.90)  # SENSOR_GLYPH_RATIO in Geometry.kt
PAD = 10 * SCALE
GAP = 8 * SCALE


def mono(size):
    for candidate in (r"C:\Windows\Fonts\consola.ttf", r"C:\Windows\Fonts\cour.ttf",
                      "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"):
        if os.path.exists(candidate):
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def glyph_image(source, name):
    mask = preview_glyphs.render(preview_glyphs.parse(source, name), GLYPH_PX)
    img = Image.new("RGBA", mask.size, GREEN + (0,))
    img.putalpha(mask)
    return img


def text_image(text, font):
    box = font.getbbox(text)
    img = Image.new("RGBA", (box[2] - box[0], box[3] - box[1]), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((-box[0], -box[1]), text, font=font, fill=GREEN)
    return img


def slot(label_img, value, font, path):
    val = text_image(value, font)
    h = max(label_img.height, val.height) + 2 * PAD
    w = label_img.width + GAP + val.width + 2 * PAD
    img = Image.new("RGB", (w, h), (0, 0, 0))
    img.paste(label_img, (PAD, (h - label_img.height) // 2), label_img)
    img.paste(val, (PAD + label_img.width + GAP, (h - val.height) // 2), val)
    img.save(path, optimize=True)
    print(os.path.relpath(path), img.size)


AMBER = (255, 176, 0)        # Palette.ALERT_AMBER: the green palette's accent, which military sites wear
SITE_PX = 22 * SCALE         # the site pictograms are drawn at about 22 px on the 454 px dial

# The site table in DESIGN.md: type, then the civil and the military silhouette. A helipad has a
# silhouette of its own for each; a spaceport draws the one launch complex either way and lets the
# colour say who owns it.
SITES = [
    ("airfield", "aircraft", "fighter"),
    ("helipad", "helicopter", "helicopterMilitary"),
    ("port", "merchantShip", "warship"),
    ("spaceport", "rocket", "rocket"),
]


def site(source, name, colour, path):
    mask = preview_glyphs.render(preview_glyphs.parse(source, name), SITE_PX)
    img = Image.new("RGB", (SITE_PX + 2 * PAD, SITE_PX + 2 * PAD), (0, 0, 0))
    tint = Image.new("RGB", mask.size, colour)
    img.paste(tint, (PAD, PAD), mask)
    img.save(path, optimize=True)
    print(os.path.relpath(path), img.size)


def main():
    with open(preview_glyphs.GLYPHS_KT, encoding="utf-8") as handle:
        source = handle.read()
    font = mono(TEXT_PX)
    os.makedirs(OUT, exist_ok=True)
    slot(glyph_image(source, "heart"), "67", font, os.path.join(OUT, "slot-heart.png"))
    slot(glyph_image(source, "pedestrian"), "7544", font, os.path.join(OUT, "slot-steps.png"))
    slot(text_image("QFE", font), "1013", font, os.path.join(OUT, "slot-qfe.png"))
    for kind, civil, military in SITES:
        site(source, civil, GREEN, os.path.join(OUT, "site-%s-civil.png" % kind))
        site(source, military, AMBER, os.path.join(OUT, "site-%s-military.png" % kind))


if __name__ == "__main__":
    main()
