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
```

IDs use the owning mod's namespace and the relative path below the section,
without `.json`. For mod ID `example`, the gear above is `example:components/gear`
and the material is `example:copper`. For a file declaring several mods, the
root-level directory belongs to the first mod declared in its metadata. Extension
files also have file IDs, but their callable handler IDs come from the explicit
`id` fields in their `events` array.

The existing baseline under `data/mmdlib/{item,block,fluid}` remains loaded through
`ModuleCatalog.bundled()`. The startup scanner does not scan that catalog again or
reinterpret unrelated mods' data directories. `ModuleCatalog.load(packRoot)`
remains available for explicit `data/<namespace>/...` module directories.

## Standalone blocks

`mmdlib/standalone/block/<path>.json` declares one block with its own ID,
`<namespace>:<path>`, and no material, as MMDLib 1.0 allowed. MMDLib's
player-only plate is `mmdlib:standalone/block/human_detector.json`, which becomes
`mmdlib:human_detector`:

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

A module with entity or block-entity companions is rejected. A module whose
`requires_mod` is missing skips the block. Standalone IDs share the duplicate-ID
check with material content. `MMDLib.standaloneBlocks()` lists them; their block
and item definitions are published, and the stats-free host owner is not a material.
The generator writes their block tags and self-drop loot table; models, lang and
recipes are static resources.

## Stages

1. Discover extension files in parallel across mod files and resolve all handlers.
2. After that phase completes, discover/decode item, block, and fluid modules in
   parallel across mod files. Collect material paths without decoding them.
3. Merge results in a defined order and install the modules, rejecting duplicate IDs.
4. Decode materials against the completed module/callback catalog, bind their IDs,
   build content plans, and check generated registry IDs for collisions. Every
   planned item and block definition's `events` bindings, custom types included,
   are validated against the callback catalog; an unknown event or callback fails
   loading with the material file and definition named.
5. Retain results, index their item and block definitions (and owning materials)
   for the runtime event bridge, queue MMDLib's playable example content, and
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

`definitionRoot` contains the five section folders. It can be a directory or a
path in a mounted ZIP filesystem. The caller owns any mounted filesystem and must
keep it open until `load` returns. The loader closes every traversal/reader it
opens, but never closes its caller's filesystem. All schema choice and ordering
live in the same testable loader used by `MMDLib.java`.

## Runtime scope

The published declaration tables are **MMDLib definition registries**.
`RuntimeContentRegistrar` separately applies MMDLib's own loaded material plans to
Minecraft DeferredRegisters. Other mods can use that registrar for supported
built-in forms, provide custom item/block implementation factories, or own their
runtime registrar. Machine and custom world-entity wiring still uses its dedicated
factory APIs. See [the playable vanilla example](vanilla-extensions.md). This stage does not discover enabled world datapacks or add registry IDs on
ordinary datapack reload.

`verifyDefinitionLoader` checks cross-mod module and handler references, delayed
material decoding, namespace/nested IDs, optional-mod filtering, duplicate IDs,
malformed files, immutable results, reversed source order, and mounted ZIP paths.
Lifecycle placement is checked against the pinned loader sources; an actual
NeoForge startup remains a separate runtime verification.
