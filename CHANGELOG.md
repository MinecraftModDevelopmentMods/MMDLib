# Changelog

## Unreleased — 2.0.2

- Update NeoForge to 21.1.265 (from 21.1.247); it is now the minimum NeoForge
  version MMDLib requires. Update the Gradle wrapper to 9.9.1 (from 9.8.2).
- Machines (#105):
  - Multiblocks, as an early proof of concept whose API will change: Java
    machines can span several blocks, with placement from one item, rotation,
    formation and dismantling, member links, ports that expose the controller's
    storage, and requirement rules. See [multiblocks](docs/multiblocks.md); the
    companion adds a test bench.
  - XML machine appearances: a `model` preset beside `cube-front`, conditional
    `<part>` models and `<face-texture>` switches with optional emissive masks,
    chosen by `when` conditions over the machine's processors, item groups,
    tanks and energy (such as `$processors.worker.running`). Java machines
    supply variables with `VisualVariableProvider`.
  - Ghost inventories: sample-only item slots that recipes match without
    consuming, with JEI's **+** button filling them.
  - Recipe selection: a processor can be `selectable`, with an optional
    `default-recipe`, chosen in screens through `<recipe-selector>`.
  - Reusable recipe inputs: item and fluid inputs accept `"consumed": false`,
    so they are required and reserved but not spent.
  - Inline group colors (`color` on `<slots>`, `<tank>` and `<tanks>`) and a
    default palette.
  - Machine-owned unavailable faces for side configuration and redstone
    (`SideConfig.disabledFaces`, `RedstoneControl.disabledFaces`), shown as
    blocked in the drawers.
  - Ghost slot default items and fixed tank fluids are checked at startup, and
    recipes asking a ghost group for more samples than it has slots fail to
    load.
  - The screen protocol is now 21, and the automation and redstone drawers 17;
    clients and servers must both run 2.0.2.
  - A local Maven repository can be configured for development builds; see
    [local development](docs/local-development.md).
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
- XML machine cycle events: a `<processor>` can name `on-start` and `on-finish`
  handlers, new `machine-cycle-start` and `machine-cycle-finish` events run on the
  server when a new cycle starts and when one finishes with its outputs placed.
  Handlers get the machine, level, position, machine ID, recipe and cycle length,
  and are registered in code, extension files, or CraftTweaker scripts
  (`mods.mmdlib.MachineHandlers.onStart` / `onFinish`, as `mmdlib:<name>`). Each
  machine's handler IDs are checked when machines load.
- CraftTweaker machine recipes: recipe scripts can add and remove recipes of XML
  machines' declared recipe types with CraftTweaker's own `<recipetype:...>`
  manager (`addJsonRecipe`, `removeByName`, ...), or add them with the typed
  `mods.mmdlib.MachineRecipeBuilder`: item, fluid and energy inputs (item and
  fluid inputs optionally reusable, as `consumed: false`), item and fluid outputs
  with chances, and time. The typed builder checks groups against the machines
  using the type, as recipe reloads do; recipes added with `addJsonRecipe` are
  not checked. `inputFluids` takes a fluid tag or several fluids.
- CraftTweaker recipe handler for machine recipes: `/ct recipes` dumps them as
  `MachineRecipeBuilder` calls, and CraftTweaker's ingredient replacement
  (`Replacer`) works on them, keeping each part's group, count and consumption;
  energy is `<recipecomponent:mmdlib:machine/energy>`.
- CraftTweaker recipe generators: a `#loader mmdlib` script registers a generator
  with `mods.mmdlib.RecipeGenerators.register(name, function)`, and machine XML
  runs it with `<generator class="zone.moddev.mc.lib.compat.CraftTweaker.ScriptRecipeGenerator"
  script="mmdlib:<name>" .../>`. On every data reload the function reads the
  reload's item tags and the generator's attributes and adds recipes.
- Standalone fluids: each `mmdlib/standalone/fluid/<path>.json` declares a fluid
  with its own ID and no material, built from a fluid module with optional
  `template`, `tint`, `temperature`, `density` and `viscosity` overrides: the fluid,
  its flowing form, a liquid block and a bucket. MMDLib adds a plain water-like
  `mmdlib:fluid` module beside `mmdlib:molten`. `MMDLib.standaloneFluids()` lists
  them. CraftTweaker: `#loader mmdlib` scripts build them with
  `mods.mmdlib.FluidBuilder` (type, flow and appearance values, block and bucket
  switches, display name), with generated bucket models, blockstates and names.
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
- CraftTweaker API docs: every script type is documented for CraftTweaker's docs
  wiki. Compiling writes the pages (`docs.json` and one page per type, under Mods ›
  MMDLib) to `build/crafttweaker-docs`, with descriptions, parameters, return values
  and example values for each member. See "Generated API docs" in
  [CraftTweaker definitions](docs/crafttweaker.md).
- Build: the Java API Javadoc leaves out the CraftTweaker script classes, which the
  wiki pages document instead. The formatter keeps `@param name description` on one
  line, which the CraftTweaker docs need; files reformatted under it change only in
  their Javadoc.
- For mods built on MMDLib:
  - `MachineDefinition` has new components: `baseModel`, `modelParts`,
    `faceTextures`, `recipeSelection` and `ghosts` (#105), then `sounds` and
    `events`. The 2.0.x constructor remains, as do constructors without sounds
    and events, which use `Sounds.NONE` and `Events.NONE`.
  - `DefinitionLoader.Loaded`, `MaterialExtensions.Definitions` and
    `Common.Clicks` have new components (standalone fluids among them); their
    previous constructors remain. `FluidBuilder.setRegistryName` fixes a fluid's
    ID, as for blocks and items. `MMDFluid.flowingId()` puts `flowing_` on the
    last path segment (`test/flowing_syrup` for `test/syrup`); IDs without a
    slash, such as every material fluid's, are unchanged.
  - `StaticAssetRules`, which only served the removed artwork, is gone.
  - New for code: `RecipeProcessor.startedCycles()`, `cycle()` and
    `justFinished()`; a `ScreenBinder.bind` overload taking a
    `ScreenValueProviders` registry, whose constructor is now public for
    isolated binding and tests. `GeneratedMachineRecipes.validateGroups` is
    public, for checking recipes added after a reload.
- Known issues, to be fixed by #105's author before release:
  - Multiblock members must keep exactly their authored block state, so a member
    whose state changes (connections, waterlogging, `lit`) breaks the structure,
    and under `REMOVE_OWNED` removes it.
  - Dismantling skips members in unloaded chunks, which keep stale links.
  - Changing a multiblock definition's version or policy leaves existing
    controllers unable to form again.

## 2.0.1 — 2026-11-10

2.0.0 (2026-11-03) shipped without a changelog; this lists what 2.0.1 added.

### Machines and screens

- Shared machine recipe processor, with centralized input and output
  reservations, compact pause and redstone controls above machine inventories,
  and persistence fixes for in-place shift-click transfers (#90).
- Mod-defined interactive GUI widgets (#91).
- Coloured inventory and side-configuration drawers, with capability-driven
  automation addons, filter editors and ghost samples (#92); player equipment and
  optional inventory drawers, including Curios slots (#93).
- Per-face redstone controls and machine reservation overlays (#94).
- A provider-based statistics drawer and configurable generator panels (#95).
- A shared powered-machine entity and a reusable powered-machine screen template;
  machine screens can inherit info panels and processor controls (#96).
- XML-defined machines, with generated recipes, recipe bundles, an XML lava
  furnace, fluid recipe adapters and tank tooltips (#97).
- Extensible, self-contained machine UI elements; redstone dust connects to a
  machine's configured redstone inputs and outputs (#103).

### Fluids

- A generic fluid capability lets glass bottles hold fluids in
  `#mmdlib:bottleable`, unless another mod already gives glass bottles one (#99).
- Glass bottles filled with an experience fluid (`#c:experience`) become Bottles
  o' Enchanting, 250 mB each (#101).

### CraftTweaker

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

- Expose material repair/harvest settings, block runtime overrides, and named tool
  stat methods to CraftTweaker. Add a typed helper for existing generic-tool factory
  data, with script integration tests and an API/runtime capability inventory.
  Per-tool durability scaling remains limited by vanilla TieredItem resetting the
  computed durability to the material tier's uses; material durability works.
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

### Build

- Spotless formats the code: an Eclipse profile (4-space indent, 80-column
  target) and import blocks, ratcheted so files are reformatted as they are
  edited, with `-PformatAll` for a whole-tree pass (#102, #104).
