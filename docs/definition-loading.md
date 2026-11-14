# Startup definition loading

MMDLib scans each installed physical mod file once for a root-level `mmdlib/`
directory. The subfolder determines the schema; JSON does not need another type
field for dispatch:

```text
src/main/resources/mmdlib/
  item/components/gear.json
  block/crusher.json
  fluid/coolant.json
  extension/armor_effects.json
  material/copper.json
  standalone/fluid/syrup.json
  sound/machine/hum.json
  sound_type/steel_plate.json
  creative_tab/tools.json
  machine/simple_crusher.xml
```

IDs use the owning mod's namespace and the relative path below the section,
without `.json`. For mod ID `example`, the gear above is `example:components/gear`
and the material is `example:copper`. For a file declaring several mods, the
root-level directory belongs to the first mod declared in its metadata. Extension
files also have file IDs, but their callable handler IDs come from the explicit
`id` fields in their `events` array.

Machine XML uses the same namespace/path rule, without `.xml`. It is loaded after
the other startup definitions and composed with the screen templates. See
[XML machines](xml-machines.md) for inherited storage, generated screen fills,
recipe generators and the complete SimpleCrusher example. The generic machine
runtime registers concrete definitions; abstract definitions provide reusable
configuration only.

The existing baseline under `data/mmdlib/{item,block,fluid}` remains loaded through
`ModuleCatalog.bundled()`. The startup scanner does not scan that catalog again or
reinterpret unrelated mods' data directories. `ModuleCatalog.load(packRoot)`
remains available for explicit `data/<namespace>/...` module directories.

## Standalone blocks

`mmdlib/standalone/block/<path>.json` declares one block with its own ID,
`<namespace>:<path>`, and no material, as MMDLib 1.0 allowed. For example, a
player-only plate in `example:standalone/block/human_detector.json` becomes
`example:human_detector` (BaseMetals ships one like it):

```json
{
  "module": "mmdlib:pressure_plate",
  "factory_data": { "detects": "players", "block_set_type": "minecraft:stone" },
  "template": { "properties": { "hardness": 0.5, "collision": false, "occlusion": false },
                "tags": ["minecraft:pressure_plates", "minecraft:mineable/pickaxe"] },
  "runtime": { "base_block": "minecraft:bricks", "block_set_type": "minecraft:stone" }
}
```

- `module`: a registered block module. The block is built by that module's factory,
  so it goes through the same `registerBlockImplementation` path as material blocks.
- `factory_data`: merged key by key over the module's `factory_data`.
- `template`: replaces the module's template. No material stats are applied.
- `runtime`: the settings a material would supply (block set type, sound, map color,
  base block); same schema as a material's `runtime`.
- `block_entity_data`: merged key by key over the `factory_data` of the module's
  `part.block_entity`, so several standalone machines can share one module. Setting it
  for a module without a block entity is an error.

When the module declares a block entity, the block becomes a machine: its block entity
is planned with the same ID as the block and registered by the runtime registrar (see
[block entities](block-entities.md#runtime-registration)). A machine for an
Advantage-style mod, `example:mmdlib/standalone/block/crusher.json`, registers as
`example:crusher` with a block-entity type of the same ID:

```json
{ "module": "example:machine_frame", "block_entity_data": { "speed": 2 } }
```

A module with a world-entity companion is rejected. A module whose `requires_mod` is
missing skips the block. Standalone IDs share the duplicate-ID check with material
content. `MMDLib.standaloneBlocks()` lists them; their block, item and block-entity
definitions are published, and the stats-free host owner is not a material.
MMDLib's generator writes block tags and the self-drop loot table for its own
standalone blocks; models, lang and recipes are static resources.

## Standalone items

`mmdlib/standalone/item/<path>.json` declares one item with its own ID,
`<namespace>:<path>`, and no material: a wrench, an upgrade card, a manual.
`example:mmdlib/standalone/item/tools/wrench.json` becomes `example:tools/wrench`:

```json
{ "module": "example:wrench", "template": { "max_stack_size": 1 } }
```

- `module`: a registered item module, whose factory builds the item.
- `factory_data`: merged key by key over the module's `factory_data`.
- `template`: replaces the module's item template.
- `runtime`: the settings a material would supply (e.g. `repair_item`).

No material stats apply; an implementation that reads them sees the defaults. A
module with a world-entity companion (an arrow's projectile) is rejected, and a
module whose `requires_mod` is missing skips the item. `MMDLib.standaloneItems()`
lists them and their item definitions are published. Models, lang, tags and recipes
are static resources.

## Standalone fluids

`mmdlib/standalone/fluid/<path>.json` declares one fluid with its own ID and no
material: a syrup, a coolant, a liquid chocolate. `example:mmdlib/standalone/fluid/syrup.json`
becomes the fluid `example:syrup`:

```json
{ "module": "mmdlib:fluid", "tint": -2051824, "viscosity": 4000, "density": 1400 }
```

- `module`: a registered fluid module. MMDLib ships `mmdlib:fluid`, a plain
  water-like liquid with water's textures, and `mmdlib:molten`, the molten-metal
  fluid materials use.
- `template`: replaces the module's fluid template (the same schema as a fluid
  module's `template`: `type_properties`, `flow_properties`, `appearance`,
  `has_block`, `block`, `has_bucket`, `bucket`).
- `tint`, `temperature`, `density`, `viscosity`: replace single values of the
  template, as a material's color and special stats would.
- `runtime`: the settings a material would supply to the liquid block.

The family follows the fluid's ID: the fluid type and source fluid `example:syrup`,
the flowing fluid `example:flowing_syrup` (for a nested path such as `test/syrup`,
`example:test/flowing_syrup`), the liquid block `example:syrup` and the bucket
`example:syrup_bucket`, each only as the template enables. A module whose
`requires_mod` is missing skips the fluid. `MMDLib.standaloneFluids()` lists them.
The bucket model, the liquid block's blockstate, names and fluid tags are static
resources, as for a material's fluid; the bucket model normally uses NeoForge's
`neoforge:fluid_container` loader.

Like materials, standalone blocks, items and fluids in another mod's namespace are
only registered when that mod queues them on its own `RuntimeContentRegistrar`
(`MMDLib.standaloneBlocks()` / `standaloneItems()` / `standaloneFluids()`, filtered
to its namespace).

## Sound events

`mmdlib/sound/<path>.json` declares one sound event, with the usual ID: for mod ID
`example`, `example:mmdlib/sound/machine/hum.json` is the sound event
`example:machine/hum`. The file holds one optional setting:

```json
{ "range": 24 }
```

* `range`: the fixed distance in blocks the sound can be heard from, whatever
  volume it is played at. Leave it out (an empty `{}` file) for Minecraft's
  default, which is 16 blocks, growing with volumes above 1.

The file only registers the event. What it sounds like is defined as for any
other sound: an entry for `machine/hum` in `assets/example/sounds.json` naming the
`.ogg` files and an optional subtitle, in the mod or in a resource pack. A
registered event with no `sounds.json` entry plays nothing.

Registration happens at startup, like items and blocks: adding a sound event needs
a restart. MMDLib registers the sound events in its own namespace. As with
materials, another mod registers its own: `MMDLib.sounds()` lists every declared
event, and `RuntimeContentRegistrar.queueSound(id, definition)` queues one, for
IDs in that registrar's namespace only:

```java
MMDLib.sounds().forEach((id, sound) -> {
    if (id.getNamespace().equals("example"))
        registrar.queueSound(id, sound);
});
```

Once registered, the event works anywhere Minecraft takes a sound event ID: the
`/playsound` command, jukebox songs, goat-horn instruments and enchantment effects
in datapacks, and code playing it through its registry holder.

## Creative tabs

`mmdlib/creative_tab/<path>.json` describes one creative tab: its title, icon,
placement and contents (items, item tags and whole materials). See
[creative tabs](creative-tabs.md).

## Block sound sets

A block's `sound` (and a material's `runtime.block_sound`) is either one of
vanilla's sound sets by name, such as `"metal"`, or the ID of a sound set declared
in `mmdlib/sound_type/<path>.json`, such as `"example:steel_plate"`:

```json
{
  "base": "metal",
  "place": "example:block/steel_place",
  "step": "example:block/steel_step",
  "pitch": 0.9
}
```

* `break`, `step`, `place`, `hit`, `fall`: the sound event played when the block is
  broken, walked on, placed, mined and landed on. Each can be any registered sound
  event: vanilla's, one from `mmdlib/sound/`, or another mod's.
* `volume`, `pitch`: how all of them play.
* `base`: the vanilla sound set anything left out comes from; defaults to `stone`.
  It must be a vanilla name, not another declared set.

Sound sets are not registry entries, so a block in any namespace can use any
declared set. A block naming an unknown set fails loading, naming the block; a set
naming an unregistered sound event fails when the first block using it is created.

CraftTweaker scripts can declare sound events and sound sets, and change the ones
these files declare; see [CraftTweaker sounds](crafttweaker.md#sounds). Code-built
definition sources can too: `MaterialExtensions.soundFiles()` and
`soundTypeFiles()` list the file definitions while sources run, and one a source
returns with a file's ID replaces it.

## Stages

1. Discover extension files in parallel across mod files and resolve all handlers.
2. After that phase completes, discover/decode item, block, and fluid modules and
   sound events in parallel across mod files. Collect material paths without decoding them.
3. Merge results in a defined order and install the modules, rejecting duplicate IDs.
4. Call each definition source registered with
   `MaterialExtensions.registerDefinitionSource`, such as MMDLib's
   [CraftTweaker scripts](crafttweaker.md), once. A source may register item and
   block modules and callbacks; everything decoded below can use them. It returns
   materials, standalone blocks and standalone items.
5. Decode materials against the completed module/callback catalog, bind their IDs,
   build content plans, and check generated registry IDs for collisions. Then plan
   the sources' materials with the same checks. Every planned item and block
   definition's `events` bindings, custom types included, are validated against the
   callback catalog; an unknown event or callback fails loading with the material
   file (or source) and definition named. A source material, standalone block or item
   whose ID matches a file's, or another source's, fails loading.
6. Retain results, index their item and block definitions (and owning materials)
   for the runtime event bridge, queue MMDLib's own content and sound events, and
   publish definitions during NeoForge registry events.

Missing folders and non-JSON files are skipped. Nested JSON files are supported.
Unknown top-level folders are ignored. Malformed data, invalid resource paths,
unknown material references, incompatible handlers, and duplicate definitions fail
loading with source information. There is no implicit replacement/last-writer-wins
policy. Installation is a startup operation, not a reload transaction; a failure
aborts startup rather than publishing incomplete material results.

## Lifecycle and API

The entrypoint schedules loading in `FMLConstructModEvent.enqueueWork`. NeoForge
runs that deferred queue after mod constructors complete, before new-registry and
registration events. Register custom event contracts and code-owned extensions in
mod constructors, so the loader sees them. Do not rely on the relative order of
other mods' deferred construction tasks.

`MMDLib.loadedMaterials()` returns an immutable map of resource ID to
`MaterialFactory.Result`. It is empty before loading completes and available by
the definition-registration stage. The existing `MaterialRegistration` hook now
publishes those materials and their item, block, world-entity, block-entity, and
fluid declarations, along with the loaded callbacks.

For explicit paths, tests, or another early loading source:

```java
var source = new DefinitionLoader.SourceRoot("example", definitionRoot, modClassLoader);
var loaded = DefinitionLoader.load(List.of(source), extensions, installedModPredicate);
var materials = loaded.materials();
```

`definitionRoot` contains the section folders described above. It can be a directory or a
path in a mounted ZIP filesystem. The caller owns any mounted filesystem and must
keep it open until `load` returns. The loader closes every traversal/reader it
opens, but never closes its caller's filesystem. All schema choice and ordering
live in the same testable loader used by `MMDLib.java`.

## Material item tags

MMDLib adds each loaded material's items to the tags their part implies, whatever
mod or script defined the material. It does this with an always-enabled data
pack, `mmdlib_material_tags`, at the bottom of the pack list:

| Part | Tags |
| --- | --- |
| `sword` | `minecraft:swords`, `minecraft:enchantable/sword` |
| `pickaxe`, `axe`, `shovel`, `hoe` | `minecraft:<part>s` |
| tools (`pickaxe`, `axe`, `shovel`, `hoe`, `crackhammer`, `scythe`) | `minecraft:enchantable/mining`, `minecraft:enchantable/mining_loot` |
| weapons (`sword`, `axe`, `scythe`, `crackhammer`) | `minecraft:enchantable/weapon`, `minecraft:enchantable/sharp_weapon` |
| `helmet`, `chestplate`, `leggings`, `boots` | `minecraft:enchantable/armor` and the slot's `minecraft:enchantable/*_armor` |
| `bow`, `crossbow`, `fishing_rod` | `minecraft:enchantable/bow`, `/crossbow`, `/fishing` |
| tools, weapons, armor, `bow`, `crossbow`, `fishing_rod`, `shears`, `shield` | `minecraft:enchantable/durability` |
| `arrow`, `bolt` | `minecraft:arrows`, `c:arrows` |
| `ingot`, `dust`, `nugget`, `rod`, `gear` | `c:<form>s/<material>`, which `c:<form>s` includes |

The parts are MMDLib's own (`mmdlib:sword` and so on); other mods' parts get no
tags here. Parts that are blocks are left to the block's own data. Every file adds
to its tag (`"replace": false`), and every entry is optional, so an item that was
loaded but never registered does not break the tag. Tags merge entries, so a mod
that also lists its items in these tags (as a fallback) changes nothing, and a
datapack can still replace a tag outright.

`MaterialItemTags.forPart` gives the same mapping to code, and
`MaterialItemTags.data` builds the tag files for a set of material results.

## Runtime scope

The published declaration tables are **MMDLib definition registries**.
`RuntimeContentRegistrar` separately applies MMDLib's own loaded material plans to
Minecraft DeferredRegisters. Other mods can use that registrar for supported
built-in forms, provide custom item/block implementation factories, or own their
runtime registrar. Machine and custom world-entity wiring still uses its dedicated
factory APIs. This stage does not discover enabled world datapacks or add registry IDs on
ordinary datapack reload.

`DefinitionLoaderTest` (JUnit) checks cross-mod module and handler references, delayed
material decoding, namespace/nested IDs, optional-mod filtering, duplicate IDs,
malformed files, immutable results, reversed source order, and mounted ZIP paths.
Lifecycle placement is checked against the pinned loader sources; an actual
NeoForge startup remains a separate runtime verification.
