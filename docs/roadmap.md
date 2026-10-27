# Roadmap

## Remaining MMDLib 1.0 parity

- **GUI subsystem.** The fill-gauge design and its textures are ready; see
  [gauge textures](gauge-textures.md). The widget sprites (slots, groups, panel, upgrade side
  tab, checkbox, radio, progress arrow, upgrade outlines) are ready; see [GUI sprites](gui-sprites.md).
  Still open: tabs versus in-place section switching for navigation.
  Proposed addition: PneumaticCraft-style informational pop-outs, collapsed icon tabs on either
  edge that slide out with text. An info tab (blue, left) explains the screen; a problems tab
  (amber/red, right, below the upgrades) lists live block-entity issues synced from the server.
  These pop-outs need JEI exclusion areas.
  Direction: screens are declarative **XML** (`mmdlib/screen/*.xml`), loaded into a typed screen
  model with load-time validation and an XSD for editor support. The parser must be hardened
  (no DTDs or external entities). Every element can take `on-click` / `on-right-click`
  handler IDs from extension files. Game-changing clicks run on the server via the menu's
  button channel. Elements bind to synced values. A `<player-inventory/>` is included by
  default. Optional JEI tie-ins: ghost slots (drag from JEI sets the expected item without
  moving items), recipe click areas, recipe transfer, and automatic exclusion areas.
- **Block-entity right-click connection** for that GUI system. This should build on
  the existing `block-right-click` contract ([method events](method-events.md#block-contracts))
  rather than a separate mechanism.
- **Config-file crackhammer recipes**, alongside the datapack `mmdlib:crushing` recipes.
- **Capability wrappers**: easy-to-use helpers around the Item, Fluid, and Energy capabilities.

## Done since the parity list was written

- **Optional JEI integration** for crackhammer recipes; see
  [runtime behaviors](runtime-behaviors.md#optional-jei-integration). Needs an in-game
  check with and without JEI installed.

## Possible future expansions

These are flagged for consideration, not committed work.

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
