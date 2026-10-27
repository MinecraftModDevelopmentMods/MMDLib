#!/usr/bin/env python3
"""Generates MMDLib's GUI widget sprites (stdlib only).

Writes PNGs (and .mcmeta scaling where a sprite stretches) to
src/main/resources/assets/mmdlib/textures/gui/sprites/. Sprite IDs are mmdlib:<name>, drawn with
GuiGraphics.blitSprite. Greyscale sprites are meant to be tinted with GuiGraphics.setColor.
The pixels match the agreed machine-screen mockup; see docs/gui-sprites.md.
"""
import json
import math
import struct
import zlib
from pathlib import Path

CLEAR = (0, 0, 0, 0)
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/mmdlib/textures/gui/sprites"


def grey(v): return (v, v, v, 255)


def write_png(name, pixels, scaling=None):
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)
    chunk = lambda kind, data: (struct.pack(">I", len(data)) + kind + data
                                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))
    (OUT / f"{name}.png").write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                                      + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    meta = OUT / f"{name}.png.mcmeta"
    if scaling: meta.write_text(json.dumps({"gui": {"scaling": scaling}}, indent=2) + "\n")
    elif meta.exists(): meta.unlink()
    print(f"  mmdlib:{name}  {width}x{height}" + (f"  ({scaling['type']})" if scaling else ""))


def canvas(w, h, fill=CLEAR): return [[fill] * w for _ in range(h)]


def rect(img, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w): img[yy][xx] = c


def bevel_box(w, h, dark, fill, light):
    """Recessed box: dark top/left, light bottom/right, the two meeting corners left as fill."""
    img = canvas(w, h, grey(fill))
    rect(img, 0, 0, w - 1, 1, grey(dark)); rect(img, 0, 0, 1, h - 1, grey(dark))
    rect(img, 1, h - 1, w - 1, 1, grey(light)); rect(img, w - 1, 1, 1, h - 1, grey(light))
    return img


def panel(w, h):
    """Vanilla container panel: black outline with cut corners, 2 px white highlight, 2 px shadow."""
    img = canvas(w, h)
    rect(img, 1, 0, w - 2, 1, grey(0)); rect(img, 1, h - 1, w - 2, 1, grey(0))
    rect(img, 0, 1, 1, h - 2, grey(0)); rect(img, w - 1, 1, 1, h - 2, grey(0))
    rect(img, 1, 1, w - 2, h - 2, grey(0xC6))
    rect(img, 1, 1, w - 3, 2, grey(255)); rect(img, 1, 1, 2, h - 3, grey(255))
    rect(img, 3, h - 3, w - 4, 2, grey(0x55)); rect(img, w - 3, 3, 2, h - 4, grey(0x55))
    return img


def side_tab(h, w=30):
    """Tab that grows out of a panel's right edge. Column 3 overlays the panel's outline column;
    columns 0-2 cover the panel's border so both share one background (transparent where the
    panel's own pixels should show)."""
    x0, x1 = 3, 3 + w
    img = canvas(x1 + 1, h)
    rect(img, 0, 1, w + 3, h - 2, grey(0xC6))
    rect(img, x0, 0, w, 1, grey(0)); rect(img, x0, h - 1, w, 1, grey(0)); rect(img, x1, 1, 1, h - 2, grey(0))
    rect(img, x0, 1, w - 1, 2, grey(255))
    rect(img, x1 - 2, 3, 2, h - 4, grey(0x55))
    rect(img, x0, h - 3, w - 1, 2, grey(0x55))
    rect(img, 1, 1, 2, 2, grey(0x55)); rect(img, 1, h - 3, 2, 2, grey(0x55))
    return img


def compact_frame(size=8):
    """Recessed 1 px frame for the compact gauge; the transparent middle shows the gap fill drawn
    underneath. Corners where the dark and light edges meet are left neutral, as on the slot."""
    img = canvas(size, size)
    rect(img, 0, 0, size - 1, 1, grey(0x37)); rect(img, 0, 0, 1, size - 1, grey(0x37))
    rect(img, 1, size - 1, size - 1, 1, grey(0xFF)); rect(img, size - 1, 1, 1, size - 1, grey(0xFF))
    img[0][size - 1] = grey(0x8B); img[size - 1][0] = grey(0x8B)
    return img


def checkbox(on):
    img = bevel_box(10, 10, 0x37, 0x8B, 0xFF)
    if on:
        for x, y in [(2, 5), (3, 6), (4, 7), (5, 6), (6, 5), (7, 4), (8, 3), (2, 4), (3, 5), (4, 6), (6, 4), (7, 3)]:
            img[y][x] = grey(255)
    return img


def radio(on):
    """Circular Athena 3D diamond: raised when off, pressed in (bevel flipped, darker face) when on."""
    upper, lower, face = (0x37, 0xFF, 0x6E) if on else (0xFF, 0x37, 0xB4)
    img = canvas(10, 10)
    for y in range(10):
        for x in range(10):
            dx, dy = x + 0.5 - 5, y + 0.5 - 5
            r = math.hypot(dx, dy); d = r - 5
            if d > 0: continue
            c = face
            if d > -1.6:
                s = (dx + dy) / max(r, 0.001)
                c = upper if s < -0.4 else lower if s > 0.4 else 0x8B
            img[y][x] = grey(c)
    return img


def arrow(filled):
    """24x16 progress arrow; draw the filled sprite over the empty one, cropped to the progress."""
    img = canvas(24, 16)
    for y in range(16):
        for x in range(24):
            if (x <= 13 and 6 <= y <= 9) or (x >= 12 and abs(y + 0.5 - 8) <= (24 - x) * 0.72):
                img[y][x] = grey(255 if filled else 0x8B)
    return img


def icon(rows, color):
    """A small bitmap centred in a 16x16 icon."""
    img = canvas(16, 16)
    ox, oy = (16 - len(rows[0])) // 2, (16 - len(rows)) // 2
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != ".": img[oy + dy][ox + dx] = color
    return img


ICONS = {
    "upgrade_speed": ["..........", ".#...#....", "..#...#...", "...#...#..", "....#...#.", "...#...#..", "..#...#...", ".#...#....", ".........."],
    "upgrade_energy": ["....##....", "...##.....", "..##......", ".#######..", "....##....", "...##.....", "..##......", ".##.......", ".........."],
    "upgrade_capacity": ["..........", ".########.", ".#......#.", ".########.", ".#......#.", ".#......#.", ".#......#.", ".########.", ".........."],
}
CUBE = ["...####...", ".##....##.", "#..####..#", "#.#....#.#", "#.#....#.#", "#..####..#", ".##....##.", "...####..."]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    print(f"writing to {OUT}:")
    write_png("slot", bevel_box(18, 18, 0x37, 0x8B, 0xFF), {"type": "nine_slice", "width": 18, "height": 18, "border": 1})
    write_png("group", bevel_box(16, 16, 0x8B, 0xC6, 0xFF), {"type": "nine_slice", "width": 16, "height": 16, "border": 1})
    write_png("panel", panel(16, 16), {"type": "nine_slice", "width": 16, "height": 16, "border": 4})
    write_png("side_tab", side_tab(16), {"type": "nine_slice", "width": 34, "height": 16,
                                         "border": {"left": 3, "top": 3, "right": 3, "bottom": 3}})
    write_png("compact_frame", compact_frame(), {"type": "nine_slice", "width": 8, "height": 8, "border": 1})
    write_png("checkbox", checkbox(False))
    write_png("checkbox_selected", checkbox(True))
    write_png("radio", radio(False))
    write_png("radio_selected", radio(True))
    write_png("progress_arrow", arrow(False))
    write_png("progress_arrow_filled", arrow(True))
    for name, rows in ICONS.items(): write_png(name, icon(rows, (0x6A, 0x50, 0x20, 255)))
    write_png("icon_sides", icon(CUBE, grey(255)))


if __name__ == "__main__":
    main()
