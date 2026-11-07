# CraftTweaker definitions

When CraftTweaker is installed, scripts can define MMDLib materials, blocks, and block
modules. Scripts use the `mmdlib` loader and run once at startup, after every mod has
constructed and before registry events. What they build is planned, checked, and
registered like the matching `mmdlib/` files. Changing a script needs a restart;
`/reload` does not run these scripts again.

The `mmdlib` loader inherits CraftTweaker's `crafttweaker` loader, so its types
(`IData`, item stacks, and so on) are available. Anything that needs registries or a
world, such as `<item:...>` brackets or recipe managers, cannot run this early; use a
normal `crafttweaker` script for recipes.

## Materials

```zenscript
#loader mmdlib
import mods.mmdlib.MaterialBuilder;

var copper = new MaterialBuilder("copper");
copper.type = "metal";
copper.color = 0xFFFF9B40;
copper.hardness = 3.5;
copper.strength = 8;
copper.magic = 4.5;
copper.removeItems("all").addItems("ingot", "nugget", "dust");
copper.removeBlocks("door", "double_slab");
copper.addHarvestTags("minecraft:needs_stone_tool");
copper.addSpecial("blast_resistance", 8);
copper.hasFluid = false;
copper.build();
```

Content is registered in MMDLib's namespace: `copper` above becomes `mmdlib:copper`,
with parts such as `mmdlib:copper_ingot`. The name can also be written
`"mmdlib:copper"`; any other namespace is rejected. A name used by a material file
or another script fails loading.

### Properties

| Property | Type | Default |
| --- | --- | --- |
| `name` | string, read-only | from the constructor |
| `type` | string | `generic` |
| `color` | int (ARGB) | `-1` (opaque white) |
| `hardness`, `strength`, `magic` | double, nonnegative | `0` |
| `damage` | double | `0` |
| `hasFluid` | bool | `true` |
| `fluid` | fluid module ID | `mmdlib:molten` |
| `items`, `blocks`, `harvestTags` | string[], read-only | see below |

See [the material factory](material-factory.md) for what each stat drives.

### Collections

Each collection has an add and a remove function. They take any number of names,
return the builder so calls can be chained, and accept `"all"`:

| Functions | Names | `"all"` |
| --- | --- | --- |
| `addItems` / `removeItems` | item parts, such as `ingot` or `pickaxe` | every item part |
| `addBlocks` / `removeBlocks` | block parts, such as `block` or `ore` | every block part |
| `addHarvestTags` / `removeHarvestTags` | block tag IDs | remove only: clears the tags |
| `addSpecial(key, value)` / `removeSpecial` | special stat keys | remove only: clears the stats |

A new builder selects every item and block part, like a material file that omits
those sections; start with `removeItems("all")` to pick parts one by one. Part
names are the same as in material files, including aliases and code-registered
types. An unknown name, or a name from the other section, is an error at that call.
Projectile entities follow their items: selecting the arrow item also builds its
entity.

### Building

`build()` validates the values and queues the material. Call it once per
material. Changes made to the builder after `build()` are ignored. `build()` only
works during the startup run; `MaterialBuilder` is not available to other loaders.

## Blocks

`mods.mmdlib.BlockBuilder` builds one block with its own ID, like a
`mmdlib/standalone/block` file. The optional second argument is the block module
that decides what kind of block it is: `mmdlib:block` (a plain cube, the default),
any MMDLib block part (`mmdlib:stairs`, `mmdlib:door`, `mmdlib:pressure_plate`...),
a module another mod registers, or a script module (below).

```zenscript
#loader mmdlib
import mods.mmdlib.BlockBuilder;

var marble = new BlockBuilder("marble");
marble.hardness = 1.5;
marble.sound = "stone";
marble.build();

var plate = new BlockBuilder("mob_plate", "mmdlib:pressure_plate");
plate.factoryData = {detects: "mobs"};
plate.blockSetType = "minecraft:oak";
plate.build();
```

The block is `mmdlib:<name>`. Its values start from the module's template, and it
is put in `minecraft:mineable/pickaxe`.

| Property | Type | Meaning |
| --- | --- | --- |
| `name`, `module` | string, read-only | |
| `hardness`, `blastResistance` | float | mining time (-1 is unbreakable); explosion resistance |
| `friction`, `speedFactor`, `jumpFactor` | float | slipperiness (ice 0.98); walk and jump multipliers |
| `lightLevel` | int | 0 to 15 |
| `requiresCorrectTool`, `collision`, `occlusion`, `randomTicks`, `replaceable`, `hasItem` | bool | |
| `sound`, `mapColor` | string | such as `stone`, `wood`, `metal` |
| `factoryData` | map | settings for the module's Java implementation |
| `blockEntityData` | map, write-only | settings for a machine's block entity |
| `baseBlock` | block ID | what stairs copy their behavior from |
| `blockSetType` | ID | how doors, buttons and plates open: `minecraft:oak` by hand, `minecraft:iron` (default) by redstone |
| `tags`, `events` | string[], read-only | |

Blocks, items, and modules share three collections: `addTags`/`removeTags` (block
tags here) with `removeTags("all")` to clear them, the `factoryData` map, and events.
`addEvent(event, callback)` binds a registered callback, such as
`addEvent("block-right-click", "mmdlib:open_menu")`. `removeEvents` removes bindings
by event name, and `removeEvents("all")` clears them.

MMDLib generates the block's blockstate, models, item model, English name, loot
table, and tags from the module's template, but **not its textures**. Supply them in
`scripts/mmdlibdata/assets/mmdlib/textures/`. At startup the client log lists each
texture a block still needs. A plain block `marble` needs `block/marble.png`. Shaped
blocks use the block's name for their main texture, plus suffixed ones where the
shape needs more (doors need `block/<name>_lower.png` and `block/<name>_upper.png`).
A module with no MMDLib template gets nothing generated, and a warning says so;
supply its blockstate and models in `scripts/mmdlibdata` too.

## Block modules

`mods.mmdlib.BlockModule` defines a new kind of block, like a `data/<ns>/block`
module file. It starts as a copy of the module named in the second argument
(`mmdlib:block` by default), without that module's aliases, and has the same
properties, tags, and events as a block. In addition:

| Property | Type | Meaning |
| --- | --- | --- |
| `name` | string, read-only | `mmdlib:<name>` |
| `factory` | ID | the Java implementation, such as `mmdlib:stairs` |
| `materialDefaults` | bool | whether a material's stats override the template's |

`build()` registers the module immediately. Later script lines can then use it:

```zenscript
import mods.mmdlib.BlockModule;

var pillar = new BlockModule("pillar", "mmdlib:stairs");
pillar.hardness = 2;
pillar.build();

var steps = new BlockBuilder("marble_steps", "mmdlib:pillar");
steps.build();

var tin = new MaterialBuilder("tin");
tin.addBlocks("pillar");   // tin_pillar
tin.build();
```

Material files can select script modules too: scripts run before material files are
read. A material's part gets generated art from the template of the module it was copied
from (or else its factory's): `tin_pillar` looks like stairs, in the material color.

## Items

`mods.mmdlib.ItemBuilder` builds one item with its own ID, like a
`mmdlib/standalone/item` file. The optional second argument is the item module:
`mmdlib:ingot` (a plain item, the default), any MMDLib item part (`mmdlib:sword`,
`mmdlib:bow`, `mmdlib:helmet`...), a module another mod registers, or a script item
module.

```zenscript
#loader mmdlib
import mods.mmdlib.ItemBuilder;

var wrench = new ItemBuilder("wrench");
wrench.durability = 250;
wrench.addTags("c:tools/wrench");
wrench.build();

var longbow = new ItemBuilder("longbow", "mmdlib:bow");
longbow.durability = 600;
longbow.build();
```

| Property | Type | Meaning |
| --- | --- | --- |
| `name`, `module` | string, read-only | |
| `stackSize` | int | 1 to 99 |
| `durability` | int | uses before breaking; 0 for none. Setting it also sets `stackSize` to 1 |
| `fireResistant` | bool | survives fire and lava |
| `container` | item ID | left in the crafting grid after use, such as `minecraft:bucket`; `""` for nothing |
| `factoryData` | map | settings for the module's Java implementation |
| `tags`, `events` | string[], read-only | |

Tags are item tags. `addTags`/`removeTags` and `addEvent`/`removeEvents` work as
they do for blocks. An item has no tags unless its script module gave it some.

MMDLib generates the item's model, English name, and tags, but not its textures.
The client log lists what each item needs: a plain item `wrench` needs
`item/wrench.png`, and a bow `longbow` needs `item/longbow_standby.png` and
`item/longbow_pulling_0.png` to `_2.png`.

## Item modules

`mods.mmdlib.ItemModule` defines a new kind of item. It starts as a copy of the
module named in the second argument (`mmdlib:ingot` by default), without that
module's aliases, and has the same properties as an item, plus a settable `factory`.
`build()` registers it at once. Materials then select it by name, in scripts and in
material files:

```zenscript
import mods.mmdlib.ItemModule;

var widget = new ItemModule("widget");
widget.stackSize = 16;
widget.addTags("c:widgets");
widget.build();

var tin = new MaterialBuilder("tin");
tin.addItems("widget");   // tin_widget, in c:widgets, drawn like a tin ingot
tin.build();
```

A module's tags go to every material part built from it. The part's art comes from
the template of the module it was copied from, in the material color.

## Generated resources

Script materials need no resource pack, and script blocks and items need only their
textures.
MMDLib adds two packs whenever CraftTweaker is installed. Both are always enabled, for
client resources and for world data:

1. `mmdlib/script_materials` is generated at startup from the materials, blocks, and
   items the scripts built. For materials it contains:
   * blockstates and item and block models for every selected part;
   * textures: MMDLib's blank templates multiplied by the material color;
   * English names, such as "Copper Ingot" and "Block of Copper";
   * block loot tables;
   * block tags: the module's tags, `minecraft:mineable/pickaxe`, the harvest tags,
     and a `needs_*_tool` tag from hardness (5 stone, 8 iron, 10 diamond);
   * item tags: tool, armor and enchantability tags, `minecraft:arrows`, and
     `c:ingots/<name>`, `c:dusts/<name>` and the like;
   * the fluid's `c:molten_<name>` tag.
2. `mmdlib/mmdlibdata` is the `scripts/mmdlibdata` folder, if it exists. It is a
   normal pack root (`assets/`, `data/`, an optional `pack.mcmeta`) placed above the
   generated pack.

Recipes are not generated; add them from scripts. Parts from other mods'
code-registered types get no generated assets; their mod supplies them.

### Replacing artwork

* **One material's texture**: put finished art at the texture's own path, such as
  `scripts/mmdlibdata/assets/mmdlib/textures/item/copper_ingot.png`. It is used as
  drawn, with no tint. Models, blockstates, loot tables, tags, and names can be
  replaced the same way. Lang files merge with the generated names, so a lang file
  only needs the keys it changes.
* **A blank for every script material**: put a file at the template's path under
  `scripts/mmdlibdata/templates/`, mirroring MMDLib's `mmdlib_templates/`. For example,
  `templates/textures/item/__material___ingot.png` replaces the ingot blank.
  `__material__` stands for the material's name.
  * A texture template is a grayscale image whose white is the material color.
  * An optional `<name>.untinted.png` beside it is drawn over the result unchanged;
    MMDLib uses this for handles, strings, and feathers.
  * Model and blockstate templates are JSON in which `__material__` is replaced.

The generated textures are colored already, so script materials set the runtime
`tinted` flag to false and the client does not tint them a second time. A JSON
material can set `"runtime": {"tinted": false}` for the same reason.

MMDLib's templates come from Base Metals artwork and vanilla model structure. See
`scripts/generate_material_templates.py`; `provenance.json` beside the templates
records each source sprite.

Clients need the same scripts and `scripts/mmdlibdata` folder as the server, since
items, blocks, and their resources are created at startup on both sides.

## Not covered yet

* Material events, runtime settings (sound, map color, block set type), and parent
  materials. These need the material file format for now.
* Behavior written in script: event handlers as script functions. This is next.
* Item template fields that are kept but not applied to registered items yet: lore,
  food, use time and animation, attribute modifiers, and tool actions. Scripts do not
  expose them until they work.

## Implementation

The integration lives in `zone.moddev.mc.lib.compat.CraftTweaker`. CraftTweaker
finds its plugin and script classes by annotation. The only reference from the rest
of MMDLib is the `CraftTweakerInit.register(modBus)` call in MMDLib's constructor, made
only when CraftTweaker is loaded. That call adds a code material source to
`MaterialExtensions.GLOBAL`; see [startup definition loading](definition-loading.md#stages).
