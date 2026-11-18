"""Pixel-art upgrade modules, using the same PNG writer as our GUI sprites."""
from pathlib import Path
import generate_gui_sprites as sprites


def generate():
    # Steel frame, teal circuit board, gold contacts and a four-way range symbol.
    palette = {
        '.': (0, 0, 0, 0),
        'o': (32, 40, 46, 255),
        's': (84, 103, 113, 255),
        'h': (181, 205, 209, 255),
        'b': (24, 72, 70, 255),
        't': (37, 119, 102, 255),
        'g': (218, 166, 61, 255),
        'y': (255, 221, 112, 255),
        'a': (131, 241, 174, 255),
        'w': (218, 255, 224, 255),
    }
    sprites.OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mmdlib/textures/item'
    sprites.OUT.mkdir(parents=True, exist_ok=True)
    # Matching steel module with two meshing brass gears and a dark axle hub.
    rows = [
        '................',
        '..oooooooooooo..',
        '.ohhhhhhhhhhhso.',
        '.ohbbbbbbbbbbso.',
        '.ohbbggbbbbbbso.',
        '.ohbggyygbbbbso.',
        '.ohgygooggbbbso.',
        '.ohgygooggygbso.',
        '.ohbggyygoogbso.',
        '.ohbbggggoogbso.',
        '.ohbbbbgyyggbso.',
        '.ohbbbbbggbbbso.',
        '.osssssssssssso.',
        '..ooggyggyggoo..',
        '....ygyygygy....',
        '................',
    ]
    assert len(rows) == 16 and all(len(row) == 16 for row in rows)
    sprites.write_png('gearbox_upgrade', [[palette[pixel] for pixel in row] for row in rows])
    # Paired cylindrical capacitors with bright contacts, in the shared module frame.
    palette['c'] = (58, 148, 207, 255)
    palette['l'] = (137, 225, 255, 255)
    rows = [
        '................',
        '..oooooooooooo..',
        '.ohhhhhhhhhhhso.',
        '.ohbbbbbbbbbbso.',
        '.ohbggbbggbbbso.',
        '.ohcllccllcbbso.',
        '.ohcltccltcbbso.',
        '.ohcltccltcbbso.',
        '.ohclwcclwcbbso.',
        '.ohcltccltcbbso.',
        '.ohcssccsscbbso.',
        '.ohbggbbggbbbso.',
        '.osssssssssssso.',
        '..ooggyggyggoo..',
        '....ygyygygy....',
        '................',
    ]
    assert len(rows) == 16 and all(len(row) == 16 for row in rows)
    sprites.write_png('capacitor_upgrade', [[palette[pixel] for pixel in row] for row in rows])

    # Shared range module: four-way expansion arrows.
    rows = [
        '................',
        '..oooooooooooo..',
        '.ohhhhhhhhhhhso.',
        '.ohbbbbbbbbbbso.',
        '.ohbttbaabttbso.',
        '.ohbtbaaaabtbso.',
        '.ohbttbaabttbso.',
        '.ohbaabwabaabso.',
        '.ohaaaawwaaaaso.',
        '.ohbaabwabaabso.',
        '.ohbttbaabttbso.',
        '.ohbtbaaaabtbso.',
        '.osssssssssssso.',
        '..ooggyggyggoo..',
        '....ygyygygy....',
        '................',
    ]
    assert len(rows) == 16 and all(len(row) == 16 for row in rows)
    sprites.write_png('range_upgrade', [[palette[pixel] for pixel in row] for row in rows])

if __name__ == '__main__':
    generate()
