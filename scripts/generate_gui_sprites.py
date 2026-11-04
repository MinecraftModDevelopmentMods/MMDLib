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


def recessed_slot():
    """18px item well; the outer bevel and inward shadows stay fixed when nine-sliced."""
    img = canvas(18, 18, grey(128))
    for y in range(1, 17):
        for x in range(1, 17):
            # Light from above-left catches the lower rim of the recessed surface.
            top_shadow = {1: 34, 2: 18, 3: 7}.get(y, 0)
            left_shadow = {1: 14, 2: 6, 3: 2}.get(x, 0)
            bottom_light = {16: 10, 15: 5, 14: 2}.get(y, 0)
            right_light = {16: 5, 15: 2, 14: 1}.get(x, 0)
            img[y][x] = grey(128 - top_shadow - left_shadow + bottom_light + right_light)
    rect(img, 0, 0, 17, 1, grey(62))
    rect(img, 0, 0, 1, 17, grey(70))
    rect(img, 1, 17, 17, 1, grey(224))
    rect(img, 17, 1, 1, 17, grey(205))
    img[0][0] = grey(53)
    img[0][17] = img[17][0] = grey(140)
    img[17][17] = grey(231)
    return img


def write_slot_sprite():
    write_png("slot", recessed_slot(),
              {"type": "nine_slice", "width": 18, "height": 18, "border": 4})


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


def drawer_frame(w, h):
    """Thin raised rectangular frame from the drawer reference; tintable face and bevels."""
    img = canvas(w, h)
    rect(img, 1, 0, w - 2, h, grey(0x30))
    rect(img, 0, 1, w, h - 2, grey(0x30))
    rect(img, 1, 1, w - 2, h - 2, grey(0xDF))
    rect(img, 1, 1, w - 3, 1, grey(0xFF))
    rect(img, 1, 1, 1, h - 3, grey(0xFF))
    rect(img, 2, h - 2, w - 3, 1, grey(0x80))
    rect(img, w - 2, 2, 1, h - 3, grey(0x80))
    return img


def info_glyph():
    """White serif i with a dark one-pixel outline; the coloured tab supplies its background."""
    rows = ["..##..", "..##..", "......", ".###..", "..##..", "..##..",
            "..##..", "..##..", "..##..", ".####."]
    ink = {(5 + x, 3 + y) for y, row in enumerate(rows)
           for x, pixel in enumerate(row) if pixel == '#'}
    img = canvas(16, 16)
    for x, y in ink:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1): img[y + dy][x + dx] = grey(0x36)
    for x, y in ink: img[y][x] = grey(0xFF)
    return img


def write_drawer_sprites():
    write_png("drawer_tab", drawer_frame(22, 24))
    write_png("drawer_panel", drawer_frame(16, 16),
              {"type": "nine_slice", "width": 16, "height": 16, "border": 3})
    write_png("icon_info", info_glyph())


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
    """Bevelled blue-grey arrow; both states share a silhouette for clipped progress fills."""
    img = canvas(24, 16)
    top = (121, 198, 247) if filled else (190, 207, 223)
    bottom = (35, 101, 162) if filled else (105, 130, 157)
    outline = (40, 78, 119) if filled else (79, 99, 123)
    highlight = (196, 237, 255) if filled else (229, 242, 252)

    def inside(x, y):
        return ((1 <= x <= 16 and 5 <= y <= 10)
                or (16 <= x <= 23 and abs(y - 7.5) <= (23 - x) * 0.85 + 0.5))

    def mix(a, b, fraction):
        return tuple(round(v + (w - v) * fraction) for v, w in zip(a, b))

    for y in range(16):
        for x in range(24):
            if not inside(x, y): continue
            if any(not inside(x + dx, y + dy) for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0))):
                color = outline
            else:
                color = mix(top, bottom, max(0, min(1, (y - 2) / 11)))
                if y < 8 and not inside(x, y - 2): color = highlight
                elif y == 7: color = mix(color, highlight, 0.25)
                elif y >= 9 and not inside(x, y + 2): color = mix(color, outline, 0.35)
            img[y][x] = (*color, 255)
    return img


def write_progress_sprites():
    write_png("progress_arrow", arrow(False))
    write_png("progress_arrow_filled", arrow(True))


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


def status_icon(inside, fill, edge, glyph, ink):
    """Full-colour 16x16 pop-out icon: a shape (inside(x, y) for pixel centres) with a 1 px darker edge and a glyph."""
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if not inside(x + 0.5, y + 0.5): continue
            border = any(not inside(x + 0.5 + dx, y + 0.5 + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            img[y][x] = edge if border else fill
    ox = (16 - len(glyph[0])) // 2
    oy = 4 if glyph is BANG_LOW else 3 if glyph is BANG else (16 - len(glyph)) // 2
    for dy, row in enumerate(glyph):
        for dx, ch in enumerate(row):
            if ch != ".": img[oy + dy][ox + dx] = ink
    return img


def in_triangle(x, y): return 0.5 <= y <= 15.5 and abs(x - 8) <= (y - 0.5) * 0.5 + 0.6
def in_shield(x, y):
    """Heater shield: flat top, straight sides, curving to a point at the bottom."""
    if not (0.5 <= y <= 15.5 and 1 <= x <= 15): return False
    return y <= 7 or abs(x - 8) <= 7 * max(0.0, 1 - (y - 7) / 9) ** 0.8 + 0.2


BANG = ["##", "##", "##", "##", "..", "##"]
BANG_LOW = ["##", "##", "##", "##", "##", "..", "##"]  # sits lower, inside the triangle's wide part


def write_flat_buttons():
    """Compact controls: uniform 1px outline, flat fill, and an interior-only hover highlight."""
    for name, fill in (("button_flat", 0x8B), ("button_flat_highlighted", 0xA9)):
        img = canvas(14, 14, grey(0x37))
        rect(img, 1, 1, 12, 12, grey(fill))
        write_png(name, img, {"type": "nine_slice", "width": 14, "height": 14, "border": 1})


def write_control_icons():
    """16 px control glyphs with a one-pixel shadow and optional lane/mode badge."""
    def glyph(img, rows, x, y, ink):
        for dy, row in enumerate(rows):
            for dx, bit in enumerate(row):
                if bit != ".": img[y + dy + 1][x + dx + 1] = grey(42)
        for dy, row in enumerate(rows):
            for dx, bit in enumerate(row):
                if bit != ".": img[y + dy][x + dx] = ink

    pause = ["##...##"] * 9
    play = ["#......", "###....", "#####..", "#######", "#######", "#####..", "###....", "#......"]
    letters = {"a": [".#.", "#.#", "###", "#.#", "#.#"],
               "b": ["##.", "#.#", "##.", "#.#", "##."]}
    for name, rows, ink in (("pause", pause, (240, 198, 70, 255)),
                            ("resume", play, (100, 216, 112, 255))):
        for lane in ("", "a", "b"):
            img = canvas(16, 16)
            glyph(img, rows, 3 if lane else 4, 2 if lane else 3, ink)
            if lane: glyph(img, letters[lane], 11, 9, grey(245))
            write_png("icon_" + name + ("_" + lane if lane else ""), img)

    torch = [".###.", "#####", "#####", ".###.", "..#..", "..#..", "..#..", "..#..", "..#.."]
    badges = {"ignore": ["#.#", ".#.", "#.#"],
              "high": [".#.", "###", ".#."], "low": ["...", "###", "..."]}
    for mode, badge in badges.items():
        img = canvas(16, 16)
        glyph(img, torch, 4, 2, (242, 70, 49, 255) if mode == "high" else (155, 67, 55, 255))
        rect(img, 6, 6, 1, 5, (166, 117, 64, 255))
        if mode == "high": rect(img, 5, 3, 2, 2, (255, 201, 101, 255))
        glyph(img, badge, 11, 10, grey(245))
        write_png("icon_redstone_" + mode, img)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    print(f"writing to {OUT}:")
    write_slot_sprite()
    write_png("group", bevel_box(16, 16, 0x8B, 0xC6, 0xFF), {"type": "nine_slice", "width": 16, "height": 16, "border": 1})
    write_png("panel", panel(16, 16), {"type": "nine_slice", "width": 16, "height": 16, "border": 4})
    write_png("side_tab", side_tab(16), {"type": "nine_slice", "width": 34, "height": 16,
                                         "border": {"left": 3, "top": 3, "right": 3, "bottom": 3}})
    write_png("compact_frame", compact_frame(), {"type": "nine_slice", "width": 8, "height": 8, "border": 1})
    write_drawer_sprites()
    write_control_icons()
    write_flat_buttons()
    write_png("checkbox", checkbox(False))
    write_png("checkbox_selected", checkbox(True))
    write_png("radio", radio(False))
    write_png("radio_selected", radio(True))
    write_progress_sprites()
    for name, rows in ICONS.items(): write_png(name, icon(rows, (0x6A, 0x50, 0x20, 255)))
    write_png("icon_sides", icon(CUBE, grey(255)))
    # Pop-out kinds. Full colour, not for tinting.
    write_png("icon_warning", status_icon(in_triangle, (0xF5, 0xC5, 0x18, 255), (0x7A, 0x5A, 0x00, 255), BANG_LOW, (0x20, 0x20, 0x20, 255)))
    write_png("icon_error", status_icon(in_shield, (0xD0, 0x20, 0x20, 255), (0x70, 0x10, 0x10, 255), BANG, grey(255)))


if __name__ == "__main__":
    main()
