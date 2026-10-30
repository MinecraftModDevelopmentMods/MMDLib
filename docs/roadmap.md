# Roadmap

## Remaining MMDLib 1.0 parity

- **GUI gameplay and visual review.** The GUI runtime is implemented; validate addon-specific
  routing, handlers, translations and JEI recipe transfer in the target modpack. See
  [GUI screens](gui-screens.md) for the integration API and isolated smoke command.

## Done since the parity list was written

- **GUI runtime.** Hybrid block-entity registration handles, generic menu and block-right-click
  opening, exact large-number delta sync, server-checked actions, client widgets/context views/
  side configuration/pop-outs/tooltips, and optional JEI ghost/click/transfer/exclusion hooks.


- **Capability wrappers.** `ItemStorage`, `FluidStorage`, `EnergyBuffer`, `SideConfig`,
  `MachineCapabilities`, and `Transfer` in `api.capabilities`. Every face shows all slots with
  the same numbering, side configuration only allows or refuses operations, and out-of-range
  indices are refused without throwing; see [capability wrappers](capabilities.md).
- **Built-in factories through the public API.** Every `mmdlib:*` item and block factory
  is registered with `registerItemImplementation` / `registerBlockImplementation` and
  built from the `ItemContext` / `BlockContext` addons receive; see
  [material extensions](material-extensions.md#factory-implementations). Still special-cased:
  the arrow/bolt entity factories and the `mmdlib:flowing_fluid` fluid factory.
- **Redstone.** `redstone-changed` contract, signal and comparator output for
  `mmdlib:block` via `factory_data.redstone`, `detects` filters for pressure plates
  (player-only plates), and `mmdlib:weighted_pressure_plate`; see
  [runtime behaviors](runtime-behaviors.md#redstone). Comparator output from a
  block-entity getter is not implemented yet; block entities are now registered at runtime.

- **Standalone blocks** (`mmdlib/standalone/block/*.json`), material-less as in 1.0, built by
  any block module's factory; first use is the player-only plate `mmdlib:human_detector`. See
  [startup definition loading](definition-loading.md#standalone-blocks).
- **Block entities for data-defined machines.** Each mod's `RuntimeContentRegistrar` registers
  the block-entity type of every planned machine in its own namespace, with the block's ID,
  and hands the block implementation a `BlockEntityBinding`; settings are decoded at load.
  Standalone blocks may be machines (`block_entity_data` overrides per block), and
  `mmdlib:machine` builds a plain machine block from data alone. See
  [block entities](block-entities.md#runtime-registration).
- **Standalone items** (`mmdlib/standalone/item/*.json`), material-less items such as wrenches
  and upgrade cards. See [startup definition loading](definition-loading.md#standalone-items).

- **Optional JEI integration** for crackhammer recipes; see
  [runtime behaviors](runtime-behaviors.md#optional-jei-integration). Needs an in-game
  check with and without JEI installed.

## Decided against

- **Config-file crackhammer recipes.** Crushing recipes are a normal datapack recipe type
  (`mmdlib:crushing`), which datapacks, KubeJS (`event.custom`, `event.remove`), and
  CraftTweaker (`<recipetype:mmdlib:crushing>.addJsonRecipe`) can already add and remove.
  The config stays NeoForge's standard TOML server config for tuning values only.

## Possible future expansions

These are flagged for consideration, not committed work.

### KubeJS and CraftTweaker recipe integrations

Optional polish over the generic support above, each loaded only when that mod is present: a
KubeJS recipe schema (`event.recipes.mmdlib.crushing(output, input)`) and a typed CraftTweaker
recipe manager (`addRecipe(name, output, input)`).

### Global data pack loader

Let players and pack makers supply MMDLib definitions (materials, item/block/fluid
modules, extension files) from a global location, without writing a mod.

Constraints from the current loader ([startup definition loading](definition-loading.md)):

- Definitions add registry IDs, so they must load at startup, before registries
  freeze. World datapacks and `/reload` arrive too late to add items or blocks.
- A global source would therefore be read once at startup, for example from a
  folder in the game directory, and passed to `DefinitionLoader` as another
  `SourceRoot` with a fixed namespace.
- Extension files name Java handlers, so a loader with no mod jar can only bind
  handlers that already exist (MMDLib's own, such as `mmdlib:open_menu` and the
  crackhammer handlers, or other mods'). Behavior without Java needs the scripting
  route below or more generic built-in handlers.

### CraftTweaker binding

Expose MMDLib to CraftTweaker scripts, so behavior can be written without Java:

- Script-defined event handlers, registered as code callbacks
  (`MaterialEvents.registerCallback`) for the built-in contracts, and bound by ID
  from material/item/block JSON like any other handler.
- Possibly material and module definitions from scripts.

Open points:

- Ordering: script callbacks must be registered before MMDLib decodes materials
  that reference them, and that decoding happens during mod construction. The
  binding must fit CraftTweaker's script loading stages.
- Handlers fire on both logical sides; script handlers that change the world must
  check the side, as Java handlers do.
- This would be an optional integration; MMDLib must not require CraftTweaker.
