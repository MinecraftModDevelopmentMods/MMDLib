#!/usr/bin/env python3
"""Build MMDLib's material templates: blank textures, models and blockstates for generated
materials. Every template name contains __material__, which the runtime replaces with a material.

--source <Base Metals assets dir> regenerates the textures under mmdlib_templates/textures, each
as two PNGs:

  <name>.png           the material's pixels as grayscale, brightest pixel white; the runtime
                       multiplies it by the material color
  <name>.untinted.png  everything else (handles, strings, white highlights), kept as drawn;
                       omitted when the sprite has no such pixels

A pixel stays untinted when it is clearly not material: wood or leather brown, or bright neutral
string, feathers and highlights. Sources come from a fixed list of strongly colored Base Metals
materials (blue and red), so their metal never reads as brown or neutral.
Re-running the script is deterministic; provenance.json records each source and its hash.

--minecraft <client jar> regenerates mmdlib_templates/assets: blockstates and models. Block
forms copy the state structure of an analog vanilla block (iron door for doors, oak fence for
fences...) and use the vanilla models as parents, overriding only the textures that should be
material. The vanilla files are read, never copied into the output.

  python3 scripts/generate_material_templates.py [--source DIR] [--minecraft JAR] [--preview out.png]
"""
import argparse, colorsys, hashlib, json, struct, zipfile, zlib
from pathlib import Path

# Materials whose hue is far from wood brown and string white, best first.
SOURCES = ['aquarium', 'starsteel', 'adamantine']
TOKEN = '__material__'

# template path (relative to textures/, with TOKEN) -> source path pattern (relative to the
# Base Metals textures dir; {m} is the material). Several patterns are tried in order.
ITEMS = {
    'ingot': ['item/{m}_ingot'], 'nugget': ['item/{m}_nugget'], 'dust': ['item/{m}_powder'],
    'smallpowder': ['item/{m}_smallpowder'], 'gear': ['item/{m}_gear'], 'rod': ['item/{m}_rod'],
    'blend': ['item/{m}_blend', 'item/{m}_powder'], 'smallblend': ['item/{m}_smallblend', 'item/{m}_smallpowder'],
    'clump': ['item/{m}_clump'], 'crystal': ['item/{m}_crystal'], 'shard': ['item/{m}_shard'],
    'powder_dirty': ['item/{m}_powder_dirty'], 'gem': ['item/{m}_crystal'],
    'crackhammer': ['item/{m}_crackhammer'], 'scythe': ['item/{m}_scythe'], 'axe': ['item/{m}_axe'],
    'hoe': ['item/{m}_hoe'], 'pickaxe': ['item/{m}_pickaxe'], 'shovel': ['item/{m}_shovel'],
    'sword': ['item/{m}_sword'], 'shears': ['item/{m}_shears'], 'helmet': ['item/{m}_helmet'],
    'chestplate': ['item/{m}_chestplate'], 'leggings': ['item/{m}_leggings'], 'boots': ['item/{m}_boots'],
    'horse_armor': ['item/{m}_horse_armor'], 'arrow': ['item/{m}_arrow'], 'bolt': ['item/{m}_bolt'],
    'bow_standby': ['item/{m}_bow_standby'], 'bow_pulling_0': ['item/{m}_bow_pulling_0'],
    'bow_pulling_1': ['item/{m}_bow_pulling_1'], 'bow_pulling_2': ['item/{m}_bow_pulling_2'],
    'crossbow_standby': ['item/{m}_crossbow_standby'], 'crossbow_pulling_0': ['item/{m}_crossbow_pulling_0'],
    'crossbow_pulling_1': ['item/{m}_crossbow_pulling_1'], 'crossbow_pulling_2': ['item/{m}_crossbow_pulling_2'],
    'fishing_rod_cast': ['item/{m}_fishing_rod_cast'], 'fishing_rod_uncast': ['item/{m}_fishing_rod_uncast'],
}
BLOCKS = {
    'block': ['block/{m}_block'], 'plate': ['block/{m}_plate', 'block/{m}_block'],
    'bars': ['block/{m}_bars'], 'door_lower': ['block/{m}_door_lower'], 'door_upper': ['block/{m}_door_upper'],
    'trapdoor': ['block/{m}_trapdoor'], 'ore_overlay': ['block/ore_overlays/antimony_ore'],
    'anvil_base': ['block/{m}_anvil_base'], 'anvil_top_damaged_0': ['block/{m}_anvil_top_damaged_0'],
    'anvil_top_damaged_1': ['block/{m}_anvil_top_damaged_1'], 'anvil_top_damaged_2': ['block/{m}_anvil_top_damaged_2'],
}
OTHER = {
    'models/armor/' + TOKEN + '_layer_1': ['models/armor/{m}_layer_1'],
    'models/armor/' + TOKEN + '_layer_2': ['models/armor/{m}_layer_2'],
    'entity/horse/armor/horse_armor_' + TOKEN: ['entity/horse/armor/horse_armor_{m}'],
}
# Sprites that are all material: no untinted layer, whatever the pixel test says.
WHOLE = {'block', 'plate', 'ore_overlay', 'ingot', 'nugget', 'dust', 'smallpowder', 'blend',
         'smallblend', 'clump', 'crystal', 'shard', 'powder_dirty', 'gem', 'gear', 'rod', 'bars',
         'anvil_base', 'anvil_top_damaged_0', 'anvil_top_damaged_1', 'anvil_top_damaged_2'}


def read_png(path):
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n': raise ValueError(f'{path}: not a PNG')
    pos, idat, palette, trns = 8, b'', None, None
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8]); body = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR': width, height, depth, ctype, _, _, interlace = struct.unpack('>IIBBBBB', body)
        elif kind == b'PLTE': palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b'tRNS': trns = body
        elif kind == b'IDAT': idat += body
        pos += 12 + length
    if interlace: raise ValueError(f'{path}: interlaced PNGs are not supported')
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bpp = max(1, channels * depth // 8); stride = (width * channels * depth + 7) // 8
    raw = zlib.decompress(idat); rows, prev, at = [], bytearray(stride), 0
    for _ in range(height):
        ftype = raw[at]; line = bytearray(raw[at + 1:at + 1 + stride]); at += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0; b = prev[i]; c = prev[i - bpp] if i >= bpp else 0
            if ftype == 1: line[i] = (line[i] + a) & 255
            elif ftype == 2: line[i] = (line[i] + b) & 255
            elif ftype == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif ftype == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line); prev = line
    pixels = []
    for line in rows:
        if depth < 8:
            per = 8 // depth; mask = (1 << depth) - 1
            values = [(line[i // per] >> (8 - depth * (i % per + 1))) & mask for i in range(width)]
        else:
            values = list(line)
        for x in range(width):
            if ctype == 3:
                index = values[x]; r, g, b = palette[index]
                a = trns[index] if trns is not None and index < len(trns) else 255
            elif ctype == 6: r, g, b, a = values[x * 4:x * 4 + 4]
            elif ctype == 2: r, g, b = values[x * 3:x * 3 + 3]; a = 255
            elif ctype == 4: r = g = b = values[x * 2]; a = values[x * 2 + 1]
            else:
                r = g = b = values[x] * (255 // ((1 << depth) - 1)) if depth < 8 else values[x]; a = 255
            pixels.append((r, g, b, a))
    return width, height, pixels


def write_png(path, width, height, pixels):
    raw = b''.join(b'\x00' + bytes(v for p in pixels[y * width:(y + 1) * width] for v in p) for y in range(height))
    def chunk(kind, body): return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def split(pixels, whole):
    """Splits a sprite into (tint, untinted) pixel lists; tint is normalized grayscale."""
    tint, keep, lums = [], [], []
    for r, g, b, a in pixels:
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        # Only clearly foreign pixels stay as drawn: wood and leather browns, and bright neutral
        # string, feathers and highlights. Every other pixel, accents included, is material.
        brown = 0.02 <= h <= 0.17 and s > 0.25 and v > 0.15
        bright = s < 0.18 and v > 0.7
        material = whole or (a > 0 and not (brown or bright))
        if a == 0: tint.append(None); keep.append((0, 0, 0, 0))
        elif material:
            lum = 0.299 * r + 0.587 * g + 0.114 * b; lums.append(lum); tint.append((lum, a)); keep.append((0, 0, 0, 0))
        else: tint.append(None); keep.append((r, g, b, a))
    # Stretch so the brightest material pixel (98th percentile, ignoring stray sparkles) is white.
    top = sorted(lums)[int(len(lums) * 0.98)] if lums else 255
    scale = 255 / max(top, 1)
    out = [(0, 0, 0, 0) if t is None else (min(255, round(t[0] * scale)),) * 3 + (t[1],) for t in tint]
    return out, keep if any(p[3] for p in keep) else None


def main():
    repo = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--source', type=Path, help='Base Metals assets dir (contains textures/)')
    parser.add_argument('--minecraft', type=Path, help='Minecraft client jar, for vanilla blockstates and models')
    parser.add_argument('--output', type=Path, default=repo / 'src/main/resources/mmdlib_templates')
    parser.add_argument('--preview', type=Path, help='Also write a contact sheet tinted with sample colors')
    args = parser.parse_args()
    if not args.source and not args.minecraft: parser.error('give --source, --minecraft or both')
    if args.source: textures_from(args.source, args.output, args.preview)
    if args.minecraft: models_from(args.minecraft, args.output)


def textures_from(source, output, preview_path):
    textures = source / 'textures'; out = output / 'textures'
    targets = {('item/' + TOKEN + '_' + k): v for k, v in ITEMS.items()}
    targets.update({('block/' + TOKEN + '_' + k): v for k, v in BLOCKS.items()}); targets.update(OTHER)
    provenance, previews = {}, []
    for target, patterns in sorted(targets.items()):
        for pattern in patterns:
            found = next(((m, textures / (pattern.format(m=m) + '.png')) for m in SOURCES
                          if (textures / (pattern.format(m=m) + '.png')).is_file()), None)
            if found: break
        if not found: raise SystemExit(f'No source sprite for {target}: tried {patterns} for {SOURCES}')
        material, file = found
        width, height, pixels = read_png(file)
        form = target.rsplit('/', 1)[-1].replace(TOKEN + '_', '')
        tint, keep = split(pixels, form in WHOLE)
        save(out, target, width, height, tint, keep)
        mcmeta = file.with_name(file.name + '.mcmeta')
        if mcmeta.is_file(): (out / (target + '.png.mcmeta')).write_bytes(mcmeta.read_bytes())
        provenance[target] = {'source': file.relative_to(source).as_posix(),
                              'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'untinted': bool(keep)}
        previews.append((width, height, tint, keep))
    for target, (width, height, tint, keep, note) in derived(out).items():
        save(out, target, width, height, tint, keep)
        provenance[target] = {'derived': note, 'untinted': bool(keep)}
        previews.append((width, height, tint, keep))
    (output / 'provenance.json').write_text(json.dumps(provenance, indent=2, sort_keys=True) + '\n')
    if preview_path: preview(preview_path, previews)
    print(f'Wrote {len(provenance)} texture templates to {out}')


def save(out, target, width, height, tint, keep):
    write_png(out / (target + '.png'), width, height, tint)
    untinted = out / (target + '.untinted.png')
    if keep: write_png(untinted, width, height, keep)
    elif untinted.exists(): untinted.unlink()


def load(out, target):
    """A written template as (width, height, tint, keep)."""
    w, h, tint = read_png(out / (target + '.png'))
    file = out / (target + '.untinted.png')
    return w, h, tint, read_png(file)[2] if file.is_file() else [(0, 0, 0, 0)] * (w * h)


def derived(out):
    """Templates Base Metals has no sprite for, built from templates written above."""
    result = {}
    # Ladder: two rails and four rungs cut from the block texture, with a dark edge below each.
    w, h, block, _ = load(out, 'block/' + TOKEN + '_block')
    def solid(x, y): return x in (2, 3, 12, 13) or (y % 4 == 1 and 2 <= x <= 13)
    tint = []
    for y in range(16):
        for x in range(16):
            if solid(x, y): tint.append(block[y * w + x])
            elif solid(x, y - 1): tint.append((40, 40, 40, 255))
            else: tint.append((0, 0, 0, 0))
    result['block/' + TOKEN + '_ladder'] = (16, 16, tint, None, 'rails and rungs cut from block')
    # Door item: both door halves stacked, then halved to 8x16 and centered.
    _, _, lt, lk = load(out, 'block/' + TOKEN + '_door_lower')
    _, _, ut, uk = load(out, 'block/' + TOKEN + '_door_upper')
    tall_t, tall_k = ut + lt, uk + lk
    tint, keep = [], []
    for y in range(16):
        for x in range(16):
            if 4 <= x < 12:
                sx, sy = (x - 4) * 2, y * 2
                tint.append(tall_t[sy * 16 + sx]); keep.append(tall_k[sy * 16 + sx])
            else:
                tint.append((0, 0, 0, 0)); keep.append((0, 0, 0, 0))
    result['item/' + TOKEN + '_door'] = (16, 16, tint, keep if any(p[3] for p in keep) else None,
                                         'door halves stacked and halved')
    return result


T = 'mmdlib:block/' + TOKEN
I = 'mmdlib:item/' + TOKEN

# Part -> the vanilla block whose states and models it copies.
ANALOGS = {
    'block': 'iron_block', 'slab': 'cut_copper_slab', 'stairs': 'cut_copper_stairs',
    'wall': 'cobblestone_wall', 'fence': 'oak_fence', 'fence_gate': 'oak_fence_gate',
    'door': 'iron_door', 'trapdoor': 'iron_trapdoor', 'button': 'stone_button',
    'pressure_plate': 'stone_pressure_plate', 'weighted_pressure_plate': 'heavy_weighted_pressure_plate',
    'bars': 'iron_bars', 'ladder': 'ladder', 'lever': 'lever', 'tripwire_hook': 'tripwire_hook',
    'flower_pot': 'flower_pot', 'bookshelf': 'bookshelf', 'anvil': 'anvil',
}
# Vanilla models named outside their block's prefix.
RENAMED = {'cut_copper': T + '_slab_double', 'chipped_anvil': T + '_anvil_chipped',
           'damaged_anvil': T + '_anvil_damaged'}
# Vanilla textures that become material textures; all others (lever handle, dirt, hook, books)
# stay vanilla.
MATERIAL_TEXTURES = {
    'block/iron_block': T + '_block', 'block/cut_copper': T + '_block', 'block/cobblestone': T + '_block',
    'block/oak_planks': T + '_block', 'block/stone': T + '_block', 'block/flower_pot': T + '_block',
    'block/iron_door_bottom': T + '_door_lower', 'block/iron_door_top': T + '_door_upper',
    'block/iron_trapdoor': T + '_trapdoor', 'block/iron_bars': T + '_bars', 'block/ladder': T + '_ladder',
    'block/anvil': T + '_anvil_base', 'block/anvil_top': T + '_anvil_top_damaged_0',
    'block/chipped_anvil_top': T + '_anvil_top_damaged_1', 'block/damaged_anvil_top': T + '_anvil_top_damaged_2',
    'item/iron_door': I + '_door',
}
CUTOUT = {'door', 'trapdoor', 'bars', 'ladder', 'lever', 'tripwire_hook', 'flower_pot'}
GENERATED = ['ingot', 'nugget', 'dust', 'smallpowder', 'gear', 'rod', 'blend', 'smallblend', 'clump',
             'crystal', 'shard', 'powder_dirty', 'gem', 'shears', 'helmet', 'chestplate', 'leggings',
             'boots', 'horse_armor', 'arrow', 'bolt']
HANDHELD = ['axe', 'hoe', 'pickaxe', 'shovel', 'sword', 'crackhammer', 'scythe']
PLATE_FACES = {'down': ([0, 0, 0], [16, 1, 16]), 'up': ([0, 15, 0], [16, 16, 16]),
               'north': ([0, 0, 0], [16, 16, 1]), 'south': ([0, 0, 15], [16, 16, 16]),
               'west': ([0, 0, 0], [1, 16, 16]), 'east': ([15, 0, 0], [16, 16, 16])}


def ref(value):
    """A vanilla resource reference with its namespace and without it: ('minecraft', 'block/x')."""
    return value.split(':', 1) if ':' in value else ('minecraft', value)


def models_from(jar, output):
    vanilla = zipfile.ZipFile(jar)
    def read(path): return json.loads(vanilla.read('assets/minecraft/' + path + '.json'))
    assets = output / 'assets/mmdlib'
    for old in list(assets.rglob('*.json')) if assets.is_dir() else []: old.unlink()
    files = {}
    def put(path, data): files[path] = data
    def our_model(vanilla_ref, part, analog):
        namespace, path = ref(vanilla_ref)
        name = path.removeprefix('block/')
        if name in RENAMED: ours = RENAMED[name]
        elif name.startswith(analog): ours = T + '_' + part + name[len(analog):]
        else: raise SystemExit(f'{analog}: unexpected model {vanilla_ref}')
        define(name, ours, part)
        return ours
    def define(name, ours, part):
        path = 'models/block/' + ours.split('/', 1)[1]
        if path in files: return
        textures, model, chain = {}, read('models/block/' + name), []
        while model is not None:
            chain.append(model)
            parent = model.get('parent')
            model = read('models/' + ref(parent)[1]) if parent and ref(parent)[1] != 'block/block' and not ref(parent)[1].startswith('builtin') else None
        for m in reversed(chain): textures.update(m.get('textures', {}))
        overrides = {k: MATERIAL_TEXTURES[ref(v)[1]] for k, v in textures.items()
                     if not v.startswith('#') and ref(v)[1] in MATERIAL_TEXTURES}
        data = {'parent': 'minecraft:block/' + name, 'textures': overrides}
        if part in CUTOUT: data['render_type'] = 'minecraft:cutout'
        put(path, data)
    for part, analog in ANALOGS.items():
        state = read('blockstates/' + analog)
        def rewrite(node):
            if isinstance(node, dict):
                return {k: (our_model(v, part, analog) if k == 'model' else rewrite(v)) for k, v in node.items()}
            if isinstance(node, list): return [rewrite(v) for v in node]
            return node
        state = rewrite(state)
        if part == 'anvil':
            # MMDLib's anvil keeps its wear in a damage property instead of three blocks.
            variants = {}
            for key, variant in state['variants'].items():
                for damage, suffix in enumerate(['', '_chipped', '_damaged']):
                    our_model('minecraft:block/' + ['anvil', 'chipped_anvil', 'damaged_anvil'][damage], part, analog)
                    variants[f'damage={damage},{key}'] = dict(variant, model=T + '_anvil' + suffix)
            state = {'variants': variants}
        put('blockstates/' + TOKEN + '_' + part, state)
        item = read('models/item/' + analog)
        if part == 'flower_pot': item = {'parent': T + '_flower_pot'}
        elif 'layer0' in item.get('textures', {}):
            layer = ref(item['textures']['layer0'])[1]
            item = {'parent': 'minecraft:item/generated',
                    'textures': {'layer0': MATERIAL_TEXTURES.get(layer, 'minecraft:' + layer)}}
        else:
            item = {'parent': our_model(item['parent'], part, analog)}
        put('models/item/' + TOKEN + '_' + part, item)
    # The double slab is a full block in every state.
    put('blockstates/' + TOKEN + '_double_slab', {'variants': {'': {'model': T + '_slab_double'}}})
    # Plates: one face-hugging slab per direction, MMDLib's own shape.
    put('blockstates/' + TOKEN + '_plate',
        {'variants': {f'facing={d}': {'model': T + '_plate_' + d} for d in PLATE_FACES}})
    for d, (low, high) in PLATE_FACES.items():
        put('models/block/' + TOKEN + '_plate_' + d,
            {'parent': 'minecraft:block/block', 'textures': {'all': T + '_plate', 'particle': T + '_plate'},
             'elements': [{'from': low, 'to': high, 'faces': {f: {'texture': '#all'} for f in
                                                              ['up', 'down', 'north', 'south', 'east', 'west']}}]})
    put('models/item/' + TOKEN + '_plate',
        {'parent': 'mmdlib:block/plate', 'textures': {k: T + '_plate' for k in ['top', 'side', 'bottom']}})
    # Ores: the vanilla base stone with the tinted overlay drawn over it.
    for part, base in [('ore', 'stone'), ('netherore', 'netherrack'), ('endore', 'end_stone')]:
        faces = lambda texture: {f: {'texture': texture, 'cullface': f} for f in
                                 ['up', 'down', 'north', 'south', 'east', 'west']}
        put('blockstates/' + TOKEN + '_' + part, {'variants': {'': {'model': T + '_' + part}}})
        put('models/block/' + TOKEN + '_' + part,
            {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
             'textures': {'particle': 'minecraft:block/' + base, 'base': 'minecraft:block/' + base,
                          'overlay': T + '_ore_overlay'},
             'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces('#base')},
                          {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces('#overlay')}]})
        put('models/item/' + TOKEN + '_' + part, {'parent': T + '_' + part})
    # The molten fluid: rendered by its fluid type, so the block itself has no model.
    put('blockstates/' + TOKEN + '_molten', {'variants': {'': {'model': 'minecraft:block/air'}}})
    put('models/item/' + TOKEN + '_molten_bucket',
        {'parent': 'neoforge:item/bucket', 'loader': 'neoforge:fluid_container', 'fluid': 'mmdlib:' + TOKEN + '_molten'})
    # Items.
    for part in GENERATED:
        put('models/item/' + TOKEN + '_' + part, {'parent': 'minecraft:item/generated', 'textures': {'layer0': I + '_' + part}})
    for part in HANDHELD:
        put('models/item/' + TOKEN + '_' + part, {'parent': 'minecraft:item/handheld', 'textures': {'layer0': I + '_' + part}})
    put('models/item/' + TOKEN + '_bow', {'parent': 'minecraft:item/bow', 'textures': {'layer0': I + '_bow_standby'},
        'overrides': [{'predicate': {'pulling': 1}, 'model': I + '_bow_pulling_0'},
                      {'predicate': {'pulling': 1, 'pull': 0.65}, 'model': I + '_bow_pulling_1'},
                      {'predicate': {'pulling': 1, 'pull': 0.9}, 'model': I + '_bow_pulling_2'}]})
    for n in range(3):
        put(f'models/item/{TOKEN}_bow_pulling_{n}', {'parent': I + '_bow', 'textures': {'layer0': f'{I}_bow_pulling_{n}'}})
    put('models/item/' + TOKEN + '_crossbow', {'parent': 'minecraft:item/crossbow', 'textures': {'layer0': I + '_crossbow_standby'},
        'overrides': [{'predicate': {'pulling': 1}, 'model': I + '_crossbow_pulling_0'},
                      {'predicate': {'pulling': 1, 'pull': 0.65}, 'model': I + '_crossbow_pulling_1'},
                      {'predicate': {'pulling': 1, 'pull': 0.9}, 'model': I + '_crossbow_pulling_2'},
                      {'predicate': {'charged': 1}, 'model': I + '_crossbow_arrow'},
                      {'predicate': {'charged': 1, 'firework': 1}, 'model': I + '_crossbow_firework'}]})
    for n in range(3):
        put(f'models/item/{TOKEN}_crossbow_pulling_{n}', {'parent': 'minecraft:item/crossbow', 'textures': {'layer0': f'{I}_crossbow_pulling_{n}'}})
    for loaded in ['arrow', 'firework']:
        # Base Metals draws one fully loaded sprite; both loaded states use it.
        put(f'models/item/{TOKEN}_crossbow_{loaded}', {'parent': 'minecraft:item/crossbow', 'textures': {'layer0': I + '_crossbow_pulling_2'}})
    put('models/item/' + TOKEN + '_fishing_rod', {'parent': 'minecraft:item/handheld_rod', 'textures': {'layer0': I + '_fishing_rod_uncast'},
        'overrides': [{'predicate': {'cast': 1}, 'model': I + '_fishing_rod_cast'}]})
    put('models/item/' + TOKEN + '_fishing_rod_cast', {'parent': 'minecraft:item/fishing_rod', 'textures': {'layer0': I + '_fishing_rod_cast'}})
    put('models/item/' + TOKEN + '_shield', {'parent': 'mmdlib:item/shield', 'textures': {'shield': T + '_block'},
        'overrides': [{'predicate': {'blocking': 1}, 'model': I + '_shield_blocking'}]})
    put('models/item/' + TOKEN + '_shield_blocking', {'parent': 'mmdlib:item/shield_blocking', 'textures': {'shield': T + '_block'}})
    for path, data in sorted(files.items()):
        target = assets / (path + '.json'); target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data, indent=2) + '\n')
    print(f'Wrote {len(files)} model and blockstate templates to {assets}')


def preview(path, sprites, colors=(0xFF3A6FE0, 0xFFE0A030, 0xFF40C060, 0xFFB0B0B8), scale=4):
    """A sheet: one column per sprite, one row per sample color, each 16x16 cell upscaled."""
    cell = 16 * scale + 4; width = cell * len(sprites); height = cell * len(colors)
    canvas = [(40, 40, 40, 255)] * (width * height)
    for col, (w, h, tint, keep) in enumerate(sprites):
        for row, color in enumerate(colors):
            cr, cg, cb = (color >> 16) & 255, (color >> 8) & 255, color & 255
            for y in range(min(h, 16)):
                for x in range(min(w, 16)):
                    p = tint[y * w + x]; k = keep[y * w + x] if keep else (0, 0, 0, 0)
                    if k[3]: c = k
                    elif p[3]: c = (p[0] * cr // 255, p[1] * cg // 255, p[2] * cb // 255, 255)
                    else: continue
                    for dy in range(scale):
                        for dx in range(scale):
                            canvas[(row * cell + y * scale + dy) * width + col * cell + x * scale + dx] = c
    write_png(path, width, height, canvas)


if __name__ == '__main__':
    main()
