# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 R. Kravcov

"""Cuts the world's time-zone boundaries into the grid the watch looks a position up in.

The source is timezone-boundary-builder's `timezones-with-oceans` release: every IANA zone as a
polygon, plus the nautical `Etc/GMT±N` bands over open water, built from OpenStreetMap and
therefore ODbL. 185 MB of GeoJSON cannot ride on a watch, and does not need to: the dial needs a
zone *name*, and the rules behind the name — offsets, summer time, their history — are already on
the device as the platform's own tzdata, kept current by system updates. So this script answers
one question per cell of a 0.05° grid (about 5.5 km of latitude) and packs the answers as
run-lengths along each row.

Two things are decided here so that the watch does not have to guess:

- **Draw order.** Every polygon part is drawn largest first, so an enclave — Baarle, Büsingen,
  Llívia, a zone hole of any kind — is painted after the zone around it and survives. The ocean
  bands are the largest parts of all and go down first; a coastline cell belongs to the land.
- **Names an old watch does not know.** A watch's tzdata can be years behind this data:
  `Europe/Kyiv` replaced `Europe/Kiev` in 2022 and `America/Ciudad_Juarez` did not exist before it.
  For every zone the grid carries the newest name first, then the names it was known by (links
  with byte-identical rules), then established zones whose clocks agree with it for the next ten
  years, and finally its standard offset as a fixed last resort — each checked against the zone
  list of the oldest platform this face supports, `data/android30_zone_ids.txt`.

Deterministic: the same release and the same zone list give the same bytes.

Usage:
    python tools/tz/build_tz.py --fetch          # download the pinned release into tools/tz/raw/
    python tools/tz/build_tz.py                  # build app/src/main/assets/tz_v1.tzg from it
"""

import argparse
import datetime
import hashlib
import json
import os
import struct
import sys
import urllib.request
import zipfile
import zoneinfo
from importlib import resources

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
DATA = os.path.join(HERE, "data")
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "tz_v1.tzg"))

RELEASE = "2026f"
ASSET = "timezones-with-oceans.geojson.zip"
URL = f"https://github.com/evansiroky/timezone-boundary-builder/releases/download/{RELEASE}/{ASSET}"
SHA256 = "838b3c9161e09ee944edb64cc4d9e4d2eb3ad0a3ec3d8d6a1520a5f8f6e21ac9"

CELLS_PER_DEGREE = 20          # 0.05 degrees: ~5.5 km of latitude, less of longitude
COLS = 360 * CELLS_PER_DEGREE
ROWS = 180 * CELLS_PER_DEGREE

MAGIC = 0x31475A54             # "TZG1", little-endian
VERSION = 1
MAX_FALLBACKS = 3
AGREE_FROM = datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc)
AGREE_YEARS = 10


def fetch():
    os.makedirs(RAW, exist_ok=True)
    target = os.path.join(RAW, ASSET)
    if not os.path.exists(target):
        print("downloading", URL)
        urllib.request.urlretrieve(URL, target)
    with open(target, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()
    if digest != SHA256:
        sys.exit(f"{ASSET}: sha256 {digest}, expected {SHA256}")
    with zipfile.ZipFile(target) as archive:
        archive.extractall(RAW)
    print("ok", target)


def ring_area(ring):
    total = 0.0
    for (x0, y0), (x1, y1) in zip(ring, ring[1:] + ring[:1]):
        total += x0 * y1 - x1 * y0
    return abs(total) / 2.0


def parts(feature):
    geometry = feature["geometry"]
    if geometry["type"] == "Polygon":
        yield geometry["coordinates"]
    else:
        yield from geometry["coordinates"]


def to_pixels(ring):
    return [((lon + 180.0) * CELLS_PER_DEGREE, (90.0 - lat) * CELLS_PER_DEGREE) for lon, lat in ring]


def rasterise(features, index_of):
    """Paints every outer ring, largest first; holes are left to the smaller parts that fill them."""
    image = Image.new("I", (COLS, ROWS), -1)
    draw = ImageDraw.Draw(image)
    jobs = []
    for feature in features:
        zone = index_of[feature["properties"]["tzid"]]
        for polygon in parts(feature):
            outer = polygon[0]
            jobs.append((ring_area(outer), zone, outer))
    jobs.sort(key=lambda job: (-job[0], job[1]))
    for _, zone, outer in jobs:
        points = to_pixels(outer)
        if len(points) >= 3:
            draw.polygon(points, fill=zone, outline=zone)
    return image


def tzif(name):
    path = name.split("/")
    package = ".".join(["tzdata", "zoneinfo"] + path[:-1])
    return resources.files(package).joinpath(path[-1]).read_bytes()


def offsets(name):
    """The zone's UTC offset at every midnight and noon for the next ten years, as one tuple."""
    zone = zoneinfo.ZoneInfo(name)
    out = []
    instant = AGREE_FROM
    end = AGREE_FROM.replace(year=AGREE_FROM.year + AGREE_YEARS)
    step = datetime.timedelta(hours=12)
    while instant < end:
        out.append(instant.astimezone(zone).utcoffset())
        instant += step
    return tuple(out)


def standard_offset_seconds(name):
    moment = datetime.datetime.now(datetime.timezone.utc).astimezone(zoneinfo.ZoneInfo(name))
    return int((moment.utcoffset() - (moment.dst() or datetime.timedelta(0))).total_seconds())


def names_for(primary, known, all_names, bytes_of, offsets_of):
    """Primary name, then known links with identical rules, then known zones that agree for a decade."""
    names = [primary]
    links = sorted(n for n in all_names
                   if n != primary and n in known and bytes_of(n) == bytes_of(primary))
    names += links
    if primary not in known and not links:
        target = offsets_of(primary)
        region = primary.split("/")[0]
        agreeing = [n for n in sorted(known)
                    if "/" in n and not n.startswith(("Etc/", "SystemV/"))
                    and n in all_names and offsets_of(n) == target]
        agreeing.sort(key=lambda n: (n.split("/")[0] != region, n))
        names += agreeing
    return names[: 1 + MAX_FALLBACKS]


def build():
    source = os.path.join(RAW, "combined-with-oceans.json")
    if not os.path.exists(source):
        sys.exit(f"{source} missing: run with --fetch first")
    with open(os.path.join(DATA, "android30_zone_ids.txt"), encoding="ascii") as handle:
        known = {line.strip() for line in handle if line.strip() and not line.startswith("#")}

    with open(source, encoding="utf-8") as handle:
        features = json.load(handle)["features"]
    zones = sorted({f["properties"]["tzid"] for f in features})
    index_of = {name: i for i, name in enumerate(zones)}

    all_names = zoneinfo.available_timezones()
    byte_cache, offset_cache = {}, {}

    def bytes_of(name):
        if name not in byte_cache:
            byte_cache[name] = tzif(name)
        return byte_cache[name]

    def offsets_of(name):
        if name not in offset_cache:
            offset_cache[name] = offsets(name)
        return offset_cache[name]

    table = bytearray()
    unresolved, renamed = [], []
    for name in zones:
        names = names_for(name, known, all_names, bytes_of, offsets_of)
        if not any(n in known for n in names):
            unresolved.append(name)
        elif name not in known:
            renamed.append(f"{name} -> {next(n for n in names if n in known)}")
        table += struct.pack("<B", len(names))
        for n in names:
            encoded = n.encode("ascii")
            table += struct.pack("<B", len(encoded)) + encoded
        table += struct.pack("<i", standard_offset_seconds(name))

    image = rasterise(features, index_of)
    pixels = image.load()
    row_starts, runs = [], bytearray()
    run_count = 0
    unpainted = 0
    utc = index_of["Etc/UTC"]
    for y in range(ROWS):
        row_starts.append(run_count)
        previous = None
        for x in range(COLS):
            zone = pixels[x, y]
            if zone < 0:
                unpainted += 1
                zone = utc if previous is None else previous
            if zone != previous:
                runs += struct.pack("<HH", x, zone)
                run_count += 1
                previous = zone
    row_starts.append(run_count)

    header = struct.pack("<IHHHHHH", MAGIC, VERSION, CELLS_PER_DEGREE, COLS, ROWS, len(zones), 0)
    index = b"".join(struct.pack("<I", start) for start in row_starts)
    blob = header + struct.pack("<I", len(table)) + bytes(table) + index + bytes(runs)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as handle:
        handle.write(blob)
    print(f"{len(zones)} zones, {run_count} runs, {unpainted} unpainted cells, "
          f"{len(blob)} bytes -> {OUT}")
    for line in renamed:
        print("  older name on API 30:", line)
    for name in unresolved:
        print("  no name known to API 30, fixed offset only:", name)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fetch", action="store_true")
    args = parser.parse_args()
    if args.fetch:
        fetch()
    else:
        build()


if __name__ == "__main__":
    main()
