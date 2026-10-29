# Roadmap

## Remaining MMDLib 1.0 parity

- **GUI subsystem.** Loading is implemented (schema, hardened reader, typed model, getter
  binding, layout; `zone.moddev.mc.lib.gui`); menu, sync, rendering and JEI remain. The fill-gauge design and its textures are ready; see
  [gauge textures](gauge-textures.md). The widget sprites (slots, groups, panel, upgrade side
  tab, checkbox, radio, progress arrow, upgrade outlines) are ready; see [GUI sprites](gui-sprites.md).
  Navigation: Industrial Foregoing style context areas, where buttons swap part of the screen
  in place (`<context>` / `<view>`); every button needs a tooltip.
  Proposed addition: PneumaticCraft-style informational pop-outs, collapsed icon tabs on either
  edge that slide out with rich text, in three kinds: info (explains the screen), warning and
  error (live block-entity issues synced from the server). Edge and icon are set per tab.
  These pop-outs need JEI exclusion areas.
  See [GUI screens](gui-screens.md) for the current plan with a full example.
  Direction: screens are declarative **XML** (`mmdlib/screen/*.xml`), loaded into a typed screen
  model with load-time validation and an XSD for editor support. The parser must be hardened
  (no DTDs or external entities). Every element can take `on-click` / `on-right-click`
  handler IDs from extension files. Game-changing clicks run on the server via the menu's
  button channel. Elements bind to synced values. A `<player-inventory/>` is included by
  default. Optional JEI tie-ins: ghost slots (drag from JEI sets the expected item without
  moving items), recipe click areas, recipe transfer, and automatic exclusion areas.
- **Block entities for data-defined machines.** The shared runtime registrar still refuses
  plans with block entities. Decided (hybrid): MMDLib validates the definition, builds the
  block-entity type and keeps a handle for the wiring (block, screen lookup, capabilities); the
  implementing mod registers it in its own namespace through that handle, and an unregistered
  handle is a load error. The GUI does not wait on this: screens find a block
  entity by its registry name, whoever registered it.
- **Block-entity right-click connection** for that GUI system. This should build on
  the existing `block-right-click` contract ([method events](method-events.md#block-contracts))
  rather than a separate mechanism.
## Done since the parity list was written

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
  block-entity getter waits for block-entity runtime support.

- **Standalone blocks** (`mmdlib/standalone/block/*.json`), material-less as in 1.0, built by
  any block module's factory; first use is the player-only plate `mmdlib:human_detector`. See
  [startup definition loading](definition-loading.md#standalone-blocks).

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
