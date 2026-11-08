# Roadmap

## TODO: food and creative tabs

These are follow-up work for Java, JSON, and CraftTweaker users, outside the
current [CraftTweaker parity implementation](crafttweaker-parity-plan.md).
The policy choices below remain open; these items are not implemented or verified.

- [ ] **Food definitions and consumption.** Complete the stored food reference's
  path from definition to actual item behavior using one public API shared by
  Java, JSON, and CraftTweaker. Add reusable food definitions and consider inline
  definitions, retaining existing ID-based declarations. Cover nutrition,
  saturation, always-edible behavior, eating duration, consumption remainder, and
  effects/probabilities. Resolve and validate registry references through the
  normal loading path; apply resolved food properties during item construction.
  Add CraftTweaker builders/accessors over that public functionality, and exercise
  the same API with MMDLib-owned example content.
  **Decisions needed:** saturation value versus multiplier; conflicting timed-use
  settings; supported item factories; definition ownership/loading; distinguish
  crafting remainders from consumption remainders.
  **Verification:** equivalent Java/JSON/script definitions; invalid/missing
  reference errors; server consumption tests for hunger, saturation, fullness,
  quantity, duration, remainders, effects, and use callbacks; real-client eating
  and animation checks. Storing a food ID alone does not satisfy this item.
  **Source:** [ItemBuilder.setFood](../src/main/java/zone/moddev/mc/lib/common/builders/ItemBuilder.java),
  [ItemTemplate](../src/main/java/zone/moddev/mc/lib/common/codecs/ItemTemplate.java),
  [RuntimeProperties.item](../src/main/java/zone/moddev/mc/lib/common/runtime/RuntimeProperties.java),
  [StandardItems.Basic](../src/main/java/zone/moddev/mc/lib/common/runtime/StandardItems.java).

- [ ] **Creative-tab membership and custom tabs.** First apply existing `group`
  and `creative_menu_stacks` declarations to the requested vanilla or modded tabs.
  Then add public custom-tab definitions/registration for Java and JSON, with
  CraftTweaker wrappers using that same path: ID, translated title, icon stack,
  contents, relative placement, and basic presentation. Use the public mechanism
  for MMDLib's own materials tab.
  **Decisions needed:** duplicate entries, ordering, visibility, missing tab IDs,
  default materials-tab membership for explicitly assigned items, and modern
  component-aware stack definitions versus the existing legacy `tag` field.
  Keep tab registration at startup; do not imply `/reload` can add registry IDs.
  **Verification:** equivalent Java/JSON/script tab definitions and membership;
  invalid-reference and duplicate/order tests; real-client inspection of vanilla,
  modded, and custom tabs, titles/icons, component-bearing stacks, and default-tab
  behavior; launch without CraftTweaker to verify optional integration.
  **Source:** [ItemTemplate](../src/main/java/zone/moddev/mc/lib/common/codecs/ItemTemplate.java),
  [MMDItem.CreativeStack](../src/main/java/zone/moddev/mc/lib/common/types/MMDItem.java),
  [RuntimeContentRegistrar materials tab](../src/main/java/zone/moddev/mc/lib/common/runtime/RuntimeContentRegistrar.java).

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

### Machine module

Machine ingredients and recipes across items, fluids and energy, transactions, a processing
loop, then machines defined entirely in JSON; also the plan for NeoForge 26.1 and Fabric 26.3
builds. Stage 1 is built (internals without GUI, and ingredients, groups, transactions and
matching).

### KubeJS recipe integration

Possible optional polish over generic JSON support: a KubeJS recipe schema
(`event.recipes.mmdlib.crushing(output, input)`). The typed CraftTweaker manager
for shared `mmdlib:crushing` recipes is implemented; see
[crushing recipe scripting](crafttweaker-crusher.md).

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
