# Changelog

## Unreleased — 2.0.2

- Update NeoForge to 21.1.265 (from 21.1.247); it is now the minimum NeoForge
  version MMDLib requires. Update the Gradle wrapper to 9.9.1 (from 9.8.2).
- Add data-driven sound events: each `mmdlib/sound/<path>.json` registers the
  sound event `<mod>:<path>` at startup, with an optional fixed hearing `range`.
  Audio comes from `sounds.json` as usual. MMDLib registers its own namespace's;
  other mods queue theirs with `RuntimeContentRegistrar.queueSound`, and
  `MMDLib.sounds()` lists them all.
- Play sounds from data:
  - Blocks: a block's `sound` (or a material's `block_sound`) can now name a sound
    set declared in `mmdlib/sound_type/<path>.json`, choosing the break, step,
    place, hit and fall sounds, volume and pitch, over a vanilla `base` set.
    Vanilla names such as `"metal"` work as before.
  - Screens: any element can have a `click-sound`, played for the player when it
    is clicked.
  - XML machines: `<processor>` takes a `running-sound`, looped at the machine
    while a cycle runs, and a `finish-sound`, played when a cycle completes.
  - Click sounds and machine sounds are set in their XML only.
- Add creative tabs from data: each `mmdlib/creative_tab/<path>.json` describes
  the tab `<mod>:<path>`, with its contents in order (items, `#tags` and whole
  materials, each item shown once), a translated title with an optional fallback,
  an icon, `before`/`after` placement and an optional search bar.
  `CreativeTabDefinition.builder()` builds the same in Java. MMDLib registers its
  own namespace's; other mods queue theirs with `RuntimeContentRegistrar.queueTab`,
  and `MMDLib.creativeTabs()` lists them all.
- CraftTweaker: `#loader mmdlib` scripts can make creative tabs with
  `mods.mmdlib.CreativeTabBuilder`, and change tabs declared in JSON files with
  `CreativeTabBuilder.edit(id)`: title, fallback, icon, placement, search bar, and
  adding or removing items, tags and materials. `exists` and `declared` list the
  file tabs. For code-built definition sources, `Definitions` now carries creative
  tabs, and `MaterialExtensions.creativeTabFiles()` lets a source read the file
  tabs; a source's tab with a file tab's ID replaces it.
- CraftTweaker: `#loader mmdlib` scripts can declare sound events
  (`mods.mmdlib.SoundBuilder`, with `range`) and block sound sets
  (`mods.mmdlib.SoundTypeBuilder`: base, break, step, place, hit and fall sounds,
  volume, pitch), and change those declared in `mmdlib/sound/` and
  `mmdlib/sound_type/` files with `edit(id)`; `exists` and `declared` list the file
  definitions. A material's `blockSound` now accepts a sound set's ID, as a block's
  `sound` already did. For code-built definition sources, `Definitions` carries
  sound events and sound sets, and `MaterialExtensions.soundFiles()` and
  `soundTypeFiles()` expose the file definitions; a source's definition with a
  file's ID replaces it.
- CraftTweaker: scripts can supply screen click and hover handlers with
  `mods.mmdlib.ScreenHandlers.onClick(name, function)` and `onHover(...)`,
  registered as `mmdlib:<name>` for screen XML (machines' screens included) to
  name in `on-click` and its variants, or `on-hover`. Screens stay XML; their
  handler IDs are checked at startup, after scripts run. A click handler runs on
  the server with the player, block entity, screen, element, button, modifier keys
  and option; a hover handler runs on the client.
- Screen value providers: `value` and `max` in screen XML can name a provider as
  `@namespace:id`, registered with `ScreenValueProviders.GLOBAL.register(id,
  valueType, blockEntity -> value)` and checked against the element at load like
  a getter. CraftTweaker scripts register providers with
  `mods.mmdlib.ScreenValues.whole`, `decimal`, `flag` and `text`, as
  `mmdlib:<name>`; each function gets the screen's block entity and runs on the
  server.
- Remove the vanilla extensions: the fifteen bundled materials (wood, stone, iron,
  gold, copper, diamond, coal, charcoal, emerald, ender, quartz, obsidian, lapis,
  prismarine, redstone) with their tools, armor, decorative blocks, ammunition,
  crackhammers, dusts, gears and molten fluids, plus their art, recipes, loot
  tables and tags. They now live in BaseMetals 4.0. MMDLib registers no materials
  of its own; script-defined and standalone content is unaffected.
- **Worlds:** items and blocks with the removed `mmdlib:` IDs (such as
  `mmdlib:iron_crackhammer` or `mmdlib:copper_molten`) disappear from existing
  worlds when MMDLib is updated.
- The release jar no longer contains the data generator for that content, which
  ran an external Python script.
- The human detector's recipe needs `#c:gears`, which MMDLib no longer supplies;
  it is craftable when another mod (such as BaseMetals) adds gears.
- MMDLib's creative tab is now titled "MMDLib" (it was "MMDLib: Vanilla
  Extensions"); it holds MMDLib's standalone and script-defined items.
- For mods built on MMDLib:
  - `MachineDefinition` has a new `sounds` component; code constructing one
    directly must pass `MachineDefinition.Sounds.NONE` or the sounds it wants.
  - `DefinitionLoader.Loaded` and `Common.Clicks` have new components; their
    previous constructors remain.
  - `StaticAssetRules`, which only served the removed artwork, is gone.

## Unreleased — CraftTweaker integration

Changes on `crafttweaker-integration` since `master-2.0-1.21.1-neo`.

- Add optional CraftTweaker support for defining materials, standalone blocks and
  items, and reusable block and item modules in ZenScript.
- Add the `#loader mmdlib` startup loader. Definitions run before registry events
  and use MMDLib's existing validation and registration path. Changes require a
  restart; ordinary CraftTweaker scripts remain the place for recipes.
- Generate blockstates, models, English names, loot tables, and tags for script
  content. Supply material texture templates tinted by material color, and load
  `scripts/mmdlibdata` as an override pack for custom resources.
- Expose `MaterialExtensions.registerDefinitionSource` for code-built definitions.
  Definition sources can register modules before material files are decoded.
- Add the material runtime `tinted` setting so precolored textures can opt out of
  material tinting.
- Keep CraftTweaker optional, with its integration isolated under
  `compat/CraftTweaker` and initialized only when CraftTweaker is installed.
- Document the scripting API, generated resources, startup ordering, and current
  limitations in [CraftTweaker definitions](docs/crafttweaker.md) and
  [definition loading](docs/definition-loading.md).

+- Expose material repair/harvest settings, block runtime overrides, and named tool
+  stat methods to CraftTweaker. Add a typed helper for existing generic-tool factory
+  data, with script integration tests and an API/runtime capability inventory.
+  Per-tool durability scaling remains limited by vanilla TieredItem resetting the
+  computed durability to the material tier's uses; material durability works.
- Add typed item/block/tag/module ID references and independent module snapshots
  for CraftTweaker. Snapshot copies rebind locally owned handlers; module tags are
  now available to subsequent builders during the same script run.
- Add fluent item/block builder conveniences, generated English name overrides,
  and default/self/empty/referenced loot controls. Existing external resource
  overrides retain priority; shape-aware default loot remains supported.
- Add reproducible isolated client-smoke runs with CraftTweaker present and absent,
  generated-content rendering captures, and a verification report for the parity work.
- Add a typed CraftTweaker manager for shared `mmdlib:crushing` recipes under a
  separate crusher integration package. Reuse standard recipe actions, removals,
  queries and reload handling; test script-added recipes through the crackhammer.
