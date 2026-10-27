#!/usr/bin/env python3
"""Generates the graduated-cylinder gauge textures (stdlib only).

Writes to src/main/resources/assets/mmdlib/textures/gui/gauge/:
  body_top.png, body_middle.png, body_bottom.png    tube body: outline + dark glass interior
  glass_top.png, glass_middle.png, glass_bottom.png  translucent highlight drawn over the contents
  digits.png                                         white 3x5 digits 0-9, 4 px apart
and prints the per-row content clip table for the caps (see docs/gauge-textures.md).

The textures are vertical; horizontal gauges draw them rotated 90 degrees clockwise.
Usage: generate_gauge_textures.py [thickness] (default 18)
"""
import math
import struct
import sys
import zlib
from pathlib import Path

OUTLINE = (0x1E, 0x22, 0x26)
INTERIOR = (0x2A, 0x31, 0x37)
DIGITS = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "001", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
}


def write_png(path, width, height, pixels):
    """pixels: rows of (r, g, b, a) tuples."""
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)
    chunk = lambda kind, data: (struct.pack(">I", len(data)) + kind + data
                                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    path.write_bytes(png)


def round_rect(x, y, w, h, r):
    """Signed distance from a pixel centre to a rounded rectangle."""
    cx, cy = x + 0.5 - w / 2, y + 0.5 - h / 2
    qx, qy = abs(cx) - (w / 2 - r), abs(cy) - (h / 2 - r)
    return math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r


def compose(layers):
    """Collapses successive (rgb, alpha) blends into one straight-alpha RGBA overlay pixel."""
    color, alpha = (0.0, 0.0, 0.0), 0.0
    for rgb, a in layers:
        new_alpha = 1 - (1 - alpha) * (1 - a)
        color = tuple((c * alpha * (1 - a) + n * a) / new_alpha for c, n in zip(color, rgb))
        alpha = new_alpha
    return tuple(round(c) for c in color) + (round(alpha * 255),)


def main():
    width = int(sys.argv[1]) if len(sys.argv) > 1 else 18
    radius = max(2, min(4, width // 4))
    cap = radius + 1
    height = cap * 2 + 1  # a full tube with a single middle row
    inner_w = width - 2
    inside = lambda x, y: round_rect(x, y, width, height, radius) <= -1
    on_shape = lambda x, y: round_rect(x, y, width, height, radius) <= 0

    body, glass = [], []
    for y in range(height):
        body_row, glass_row = [], []
        for x in range(width):
            if not on_shape(x, y):
                body_row.append((0, 0, 0, 0)); glass_row.append((0, 0, 0, 0)); continue
            body_row.append((INTERIOR if inside(x, y) else OUTLINE) + (255,))
            if not inside(x, y):
                glass_row.append((0, 0, 0, 0)); continue
            layers = [((255, 255, 255), 0.05)]
            if x == 2: layers.append(((255, 255, 255), 0.32))
            if x == 3: layers.append(((255, 255, 255), 0.16))
            if x == inner_w: layers.append(((0, 0, 0), 0.22))
            glass_row.append(compose(layers))
        body.append(body_row); glass.append(glass_row)

    out = Path(__file__).resolve().parent.parent / "src/main/resources/assets/mmdlib/textures/gui/gauge"
    out.mkdir(parents=True, exist_ok=True)
    for name, rows in (("body", body), ("glass", glass)):
        write_png(out / f"{name}_top.png", width, cap, rows[:cap])
        write_png(out / f"{name}_middle.png", width, 1, rows[cap:cap + 1])
        write_png(out / f"{name}_bottom.png", width, cap, rows[cap + 1:])

    digits = [[(0, 0, 0, 0)] * 39 for _ in range(5)]
    for i, ch in enumerate("0123456789"):
        for dy, row in enumerate(DIGITS[ch]):
            for dx, bit in enumerate(row):
                if bit == "1": digits[dy][i * 4 + dx] = (255, 255, 255, 255)
    write_png(out / "digits.png", 39, 5, digits)

    print(f"thickness {width}, corner radius {radius}, cap rows {cap}")
    print("content clip per top-cap row (row: first..last interior x); bottom cap mirrors it:")
    for y in range(cap):
        xs = [x for x in range(width) if inside(x, y)]
        print(f"  {y}: " + (f"{xs[0]}..{xs[-1]}" if xs else "none"))


if __name__ == "__main__":
    main()
