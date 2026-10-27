# Maintained vanilla extensions

MMDLib uses its own loader, codecs, material factory, and runtime registration API
to extend vanilla materials. The definitions live in
`src/main/resources/mmdlib/material/`; there is no Java table of material names or
per-material registration loop. The imported `content/ModContent.java` is a
reference only and remains outside the production source tree.

The fifteen shipped materials are wood, stone, iron, gold, copper, diamond, coal,
charcoal, emerald, ender, quartz, obsidian, lapis, prismarine, and redstone. Their
hardness, strength, magic affinity, and ARGB colors preserve the historical 1.x
table supplied for this rewrite. Repair items, mining restriction tags, base
blocks, door interaction types, sounds, map colors, and armor texture/tint choices
are data references in the optional `runtime` object.

## What is added

The JSON selections follow the reference's `registerVanillaBits` behavior:

- Decorative diamond, emerald, gold, obsidian, iron, and quartz forms, with their
  placement/redstone logic; iron and gold plates and a fuel-bearing charcoal block.
- Additional ranged/utility equipment for those six materials, including arrows
  and bolts. Iron shears and existing vanilla tools/armor are not duplicated.
- Full tools/armor for emerald, obsidian, and quartz, which lack vanilla equipment.
- Copper, which 1.21.1 has only as ores, raw copper, ingots, blocks, doors, and
  trapdoors: full tools and armor, nuggets, ranged/utility equipment, and bars,
  buttons, levers, plates, pressure plates, slabs, stairs, and walls. Vanilla's own
  copper door, trapdoor, ingot, and block are used, not duplicated. Its artwork is
  imported from BaseMetals; the block models and blockstates follow gold's (the
  pressure plate follows diamond's), textured with vanilla's copper block.
- Crackhammers, scythes, gears, dusts, small dusts, nuggets, blends, and rods where
  the reference selected them. `dust` is the current canonical spelling of powder.
- Fifteen fluid families: source, flowing fluid, liquid block, and bucket.

The resulting example has 233 items (including block items and buckets), 64
ordinary blocks plus 15 liquid blocks, 14 projectile entity types, and 15 fluid
types/families. Ender and prismarine currently add fluids only, matching the
reference's fluid list. No vanilla material adds ores.

Existing vanilla entries retain their `minecraft:` IDs. Extensions use IDs such
as `mmdlib:iron_crackhammer`, `mmdlib:diamond_stairs`, and
`mmdlib:emerald_helmet`. The generic binding convention produces
`mmdlib:iron_double_slab` for the hidden compatibility slab (no item), and
`mmdlib:iron_molten` / `mmdlib:flowing_iron_molten` /
`mmdlib:iron_molten_bucket` for a fluid family.

## Runtime application

`MMDLib` queues its own namespace's loaded plans through `RuntimeContentRegistrar`
during deferred construction, before registration events. This is separate from
publishing definition records. The registrar owns Minecraft block, item, fluid,
fluid-type, entity-type, armor-material, and creative-tab DeferredRegisters.
Objects are created by suppliers at the appropriate registry events.

The registrar consumes the loaded plan; it does not select extra material forms.
Block items refer to their queued blocks, doors use `DoubleHighBlockItem`, and
stateful blocks use the specialized runtime classes. Standard armor slots come
from the item module's factory data. Runtime settings provide explicit tag-based
mining restrictions and repair ingredients. Registered custom item/block
implementations take precedence over built-in implementation helpers. Machines
and custom world entities require the existing dedicated factory APIs rather than
a generic fallback.

Arrows and bolts each have material-specific registered entity types. The fired
projectile receives a one-item copy of the actual ammunition stack; AbstractArrow
retains and serializes that pickup stack, including components. Both are in the
arrow tags so vanilla bows/crossbows can use them. Recovery returns the correct
material item after saving/loading. Their initial visual renderer uses vanilla
arrow artwork.

All example items are available in the **MMDLib: Vanilla Extensions** creative
tab and through `/give`. Generated crafting, smelting, and crushing recipes give
the solid forms survival uses, with inventory-based recipe unlocks. The
crackhammer can crush supported dropped items into their material dusts.
Fluid buckets can place and recover their sources. There is no invented vanilla
fluid-smelting recipe type; fluid production machinery remains another mod's
responsibility, and buckets are directly available for testing in creative.

## Resources and maintenance

Models, blockstates, PNG textures, animation metadata, and localization are
maintained **statically** under `src/main/resources/assets/mmdlib`. The applicable
legacy BaseMetals artwork is copied there, with model resource references changed
from `basemetals:` to `mmdlib:`. Powder assets use the canonical dust names.
The normal build and datagen never import, recreate, or overwrite this artwork.

`scripts/import_basemetals_assets.py` is an explicit one-time/manual import helper
for the reference `content` tree. It imports only dependencies needed by the
vanilla extensions, ignores Windows Zone.Identifier sidecars, preserves PNG bytes
and animation metadata, and records provenance in `assets/mmdlib/imported_assets.json`.
That metadata also identifies precolored items/blocks, so generic material colors
are not multiplied into the artwork again. Six pure-material blends reuse their
material's supplied dust sprites. Loaded crossbow states use the supplied fully
drawn sprite, since separate loaded-projectile sprites were not supplied.

`--material <name>` (repeatable) imports only the named materials and merges into the
existing `imported_assets.json`, so the `content` tree needs only that material's art.
Shared parents imported earlier (such as `item/shield`) and block item models already
in assets are kept. Copper was added this way:
`python3 scripts/import_basemetals_assets.py --material copper`.

The static inventory models include the original bow, crossbow, fishing-rod, and
shield variants. Blockstates retain the existing 1.21.1 geometry with material
textures. Armor and horse armor reference the supplied material-specific layers;
animated molten-metal sprites are shared and tinted through the fluid type.
Non-occluding blocks use cutout rendering for transparent bars and door textures.

`scripts/generate_vanilla_example.py` now generates **gameplay data only**: recipes,
loot tables, unlock advancements, and tags under `src/generated/resources/data`.
Client art is not owned by Minecraft's datagen hash cache and cannot be purged by
its stale-file cleanup.

After editing the example selections or properties:

```sh
./gradlew runData build --offline --stacktrace
python3 scripts/generate_vanilla_example.py --check
```

`runData` now registers the `MMDLib vanilla extensions` provider. It runs the same
maintained generator into a temporary staging directory and publishes every file
through Minecraft's CachedOutput/hash cache. This is important: running datagen
with zero providers against this directory would delete its contents as stale.
A post-datagen resource refresh makes a combined `runData build` invocation package
the newly generated files. Hash-cache metadata is excluded from release resources.
The standalone Python command remains available when NeoForge startup is unnecessary.
Python 3 is required for generation; use `-PmmdlibPython=PATH` for another executable,
The older client-assets override is unnecessary for gameplay-data generation.

For manual gameplay testing, launch `./gradlew runClient`; `runData` generates
resources and does not open the game window.

Gameplay-data generation needs Python 3; it no longer reads Minecraft's client
asset JAR. The legacy `--minecraft-assets` argument is accepted but unused.
`scripts/validate_static_assets.py` uses the pinned Minecraft/NeoForge JARs to
check all static parent/model/texture references, concrete model texture variables,
PNG headers, and animation metadata. `verifyStaticAssets` runs it during `check`.

`verifyVanillaExamples` loads the actual shipped files through `DefinitionLoader`,
checks every planned item/block has its packaged resources, verifies no unwanted
vanilla tool/armor duplicates, and round-trips the runtime references through
JSON/NBT and MaterialBuilder.

An opt-in, self-stopping server smoke test exercises real registration, block
placement, ammunition stack recovery after entity save/load, fluid placement and
bucket recovery, actual mining tags, and packaged armor texture references:

```sh
./gradlew runServer --offline --stacktrace -I scripts/runtime-smoke.init.gradle
./gradlew build check --offline --stacktrace
```

The smoke run uses `build/runtime-smoke/server`, loopback networking, and a random
port. It stops itself and the Gradle task fails if the success marker is absent.
Its entrypoint lives in `src/runtimeTest/java`, outside release sources; `./gradlew check`
compiles it to prevent API drift. The second command restores regular
main class output after the opt-in test run. The release JAR must contain no
`zone/moddev/mc/lib/smoke` classes.

Verified on a dedicated development server: all 50 block items placed, all twelve
arrow/bolt types preserved the correct pickup item and components across entity
save/load, all fourteen buckets placed and recovered their sources, and actual
mining tiers and packaged material-specific armor texture references matched data. Dropped-item crushing consumed one input, produced the correct dust, and wore the crackhammer once. Recipes and advancements loaded
without parse errors. Client rendering still needs visual verification in game.
