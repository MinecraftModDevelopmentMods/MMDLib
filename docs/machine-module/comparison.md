# Tesla-Core-Lib and MMDLib compared

> **Status: reference.** Describes the code as it stands in Tesla-Core-Lib (branch `1.12`, commit
> `b418bea`) and MMDLib (branch `master-2.0-1.21.1-neo`, commit `9083780`), 31 October 2026.

Both libraries give a mod the machine plumbing: storage, automation access, a GUI and recipe
inputs. They come from different eras and make opposite choices in several places.

| | Tesla-Core-Lib | MMDLib |
|---|---|---|
| Branch | `1.12` | `master-2.0-1.21.1-neo` |
| Platform | Minecraft 1.12.2, Forge, Kotlin | Minecraft 1.21.1, NeoForge, Java 21 |
| Shape | Machine base classes (`SidedTileEntity` and up), code-built GUI pieces, multi-mod energy adapters, plus `net.modcrafters.mclib`, an unfinished 2017 rewrite holding the ingredients/inventory system | Composable storages (`ItemStorage`, `FluidStorage`, `EnergyBuffer`) with per-face views, XML-defined screens with a generic menu, and data-defined machines |

Tesla-Core-Lib's own docs: `Tesla-Core-Lib/docs/gui.md` and
`Tesla-Core-Lib/docs/mclib-ingredients-inventory.md`.

## In short

- **The GUI moved from code to data.** Tesla-Core-Lib builds every screen from Kotlin pieces at
  fixed pixel positions. MMDLib reads an XML file with boxes and groups, checks it at startup,
  and binds displayed values to getters on the block entity.
- **Faces stopped renumbering slots.** In Tesla-Core-Lib a face exposes only the slots of colours
  enabled on it, so slot numbers differ per face. In MMDLib every face shows every slot, and the
  face mode only allows or refuses operations.
- **The ingredient abstraction exists in only one of them, and is unfinished.** mclib defines one
  ingredient type covering items, fluids and energy, with amount-aware matching. Its item
  extraction and recipe matching are stubs and nothing uses it. MMDLib has no such layer: recipes
  use vanilla `Ingredient`.
- **Tesla-Core-Lib ships more machine behaviour.** Pause, redstone control, lockable inventories,
  bucket fill/drain from the GUI and multi-mod energy units are built in. MMDLib leaves those to
  the mod and provides the slots, widgets and handler hooks for them.

## Side by side

### GUI

| Aspect | Tesla-Core-Lib | MMDLib |
|---|---|---|
| How a screen is defined | Kotlin: the tile returns a list of `IGuiContainerPiece`s and a list of slots, both built from its inventories | XML under `mmdlib/screen/`, validated against an XSD and checked at startup with file and line in errors |
| Layout | Absolute pixels in a 198 × 184 panel; UVs hard-coded into one texture | `vbox`/`hbox`, groups, `fill` lengths, slot units (`3.5s`); nine-slice sprites |
| Showing values | Pieces read the client-side tile; each value must be a named NBT sync part with a sync level | Getters named in the XML run on the server each tick; only changed values are sent, with no size limit (`BigInteger`) |
| Buttons and actions | Piece sends an NBT message type; tile handles it in `processClientMessage` | `on-click` names a `gui-click` handler; the server checks player, menu and element first |
| Navigation | Side drawers on the left edge; the side configurator replaces the player inventory while open | Context areas with swappable views, side tabs, info/warning/error pop-outs |
| Shift-click routing | First machine slot that accepts the stack, in slot order | `ScreenHost.screenDestinations(stack)` gives the order per stack |
| JEI | None | Built in: ghost filters, recipe click areas, "+" transfer, exclusion areas; optional |
| Text | Translation keys built in code | Every text attribute is a key; optional `-fallback`; pop-out markup |

### Inventory and automation

| Aspect | Tesla-Core-Lib | MMDLib |
|---|---|---|
| Item storage | One `ColoredItemHandler` per group, merged by `SidedItemHandler` into consecutive slot numbers | One `ItemStorage` with a `Role`, filter and limit per slot |
| Per-slot rules | Input and output filter lambdas per inventory | `STORAGE`, `INPUT`, `OUTPUT`, `MANUAL` roles; the machine bypasses them with `insertResult` |
| Side configuration unit | A dye colour per inventory or tank, on or off per face | A `SideConfig` per group, four modes per face (disabled, input, output, both) |
| What a face exposes | Only slots of enabled colours, renumbered from 0 | Every slot, same numbering; out-of-range indexes refused, not thrown |
| Several groups on one face | Automatic: each colour's slots appear when that colour is on | The mod's job: the capability lambda combines group modes ([machines](../machines.md#side-configuration-with-several-groups-on-one-storage)) |
| Locked / filtered slots | `LockableItemHandler`: lock remembers current items as filters, ghost-rendered | Ghost slots; the block entity stores and validates filters via `ScreenHost` |
| Upgrades | Addon inventory; addons can add capabilities (`ICapabilityAddon`) | One-item upgrade slots in a side tab; meaning belongs to the mod |
| Pause and redstone control | Built in: pause drawer and redstone toggle on every machine | The mod's job: checkbox and radio widgets exist; no handler ships |

### Fluids and energy

| Aspect | Tesla-Core-Lib | MMDLib |
|---|---|---|
| Tanks | `ColoredFluidHandler` per tank; `FluidTankType` input/output/both; each tank its own colour | `FluidStorage`: several tanks, one handler, a role and filter per tank |
| Buckets | Built in: click a tank in the GUI to fill or drain; a bucket slot pair; right-click with a bucket | Half: `open_menu` steps aside for fluid containers, but nothing fills the tank; the mod's block must |
| Energy | `long` storage; adapters for Tesla, Forge Energy, RF, MJ, Mekanism; GUI unit switch | `EnergyBuffer`, `long` inside, Forge Energy outside (clamped to `int`) |
| Moving things to neighbours | Per-machine code | `Transfer`: push/pull items, fluids, energy |

### Recipes and ingredients

| Aspect | Tesla-Core-Lib | MMDLib |
|---|---|---|
| Machine ingredient type | Unfinished: `IMachineIngredient` for items, fluids, energy; amount-aware matching | None: vanilla `Ingredient` |
| Machine inventories for recipes | Stub: `IMachineInventory`; item extraction always returns nothing | None: the storages themselves |
| Recipe registry | Unused: Forge registry per recipe type with find/remove helpers; input matching is empty | Vanilla datapack recipe types; ships `mmdlib:crushing` |
| JSON crafting extensions | `fluid_container` and `ore_dict_ex` ingredients, `ore_dict` condition | Tags and NeoForge ingredient types cover this |

## What one face sees

The same machine in both libraries: two inputs, one output, one upgrade. The bottom face is set
to output only, and automation asks the bottom face for its slots.

| Machine slot | Tesla-Core-Lib (orange colour on bottom) | MMDLib (output group: bottom = output) |
|---|---|---|
| input | not exposed | slot 0, insert refused |
| input | not exposed | slot 1, insert refused |
| output | slot 0, extract | slot 2, extract |
| upgrade | not exposed | slot 3, manual only |

In Tesla-Core-Lib the face has one slot, numbered 0. Turn on the blue (input) colour there too
and the output becomes slot 2, so an automation program written against slot 0 now points at an
input. In MMDLib the face always has four slots numbered 0 to 3; the face mode refuses inserts
and the slot roles refuse the rest. Changing the configuration never moves a slot.

## mclib, the ingredients and inventory system

Tesla-Core-Lib's "new" system is the `net.modcrafters.mclib` package. Its idea is worth knowing,
because MMDLib has nothing in its place.

As designed:

- A recipe lists inputs per inventory key: `"input"`, `"water"`, `"energy"`.
- The machine wraps its item handlers, tanks and energy as `IMachineInventory`s with the same keys.
- Each slot reads as an `IMachineIngredient`; items, fluids, fluid-in-container and energy all
  match each other through one call.
- Matching takes an amount rule: exact, enough, or ignore size.
- Items can be written as strings: `modid:path$meta#count`, `__ore:ingotCopper#3`, wildcards like `*gear*`.

```kotlin
val required = FluidIngredient(FluidStack(WATER, 1000))
val available = tank.fluid?.asIngredient() ?: NoIngredient
required.isEnough(available)   // 1000 <= tank amount
```

As it stands:

| Part | State |
|---|---|
| Energy storage | Works; the only part Tesla-Core-Lib itself uses |
| Ingredient matching between items, fluids and energy | Works |
| String parsing, ore and registry-mask ingredients | Works |
| Fluid extraction | Partial: ignores the slot; no item-to-fluid extraction |
| Item extraction | Stub: always returns nothing |
| Recipe input `isMatch` | Stub: returns `Unit`; shaped input is empty |
| Feature sync | Broken: sent but never received; ticking work cannot be overridden |

Ten defects in total; the full list is in `Tesla-Core-Lib/docs/mclib-ingredients-inventory.md`,
and [the rebuild plan](rebuild-plan.md#mclibs-defects-and-why-they-dont-come-back) says how each is avoided.

## Worth taking, worth leaving

- **Take: one ingredient type across resources.** Matching items, fluids, fluid containers and
  energy through one interface with an amount rule would give MMDLib machines a common
  recipe-input layer. It needs writing fresh; the 1.12 code is a sketch. See
  [the rebuild plan](rebuild-plan.md).
- **Take: tank interaction from the screen.** Clicking a fluid gauge with a held bucket to fill or
  drain it, as `FluidTankPiece` does. MMDLib now lets buckets through `open_menu` but has no code
  that fills the tank.
- **Take: stock machine controls.** Pause and redstone control are on every Tesla-Core-Lib
  machine. A shipped MMDLib handler would save each mod writing its own.
- **Take: a per-slot face mode.** Tesla-Core-Lib's colours apply to their own slots
  automatically. MMDLib's group configs need the mod to combine them in the capability lambda; a
  per-slot mode in the views would remove that step.
- **Leave: renumbering slots per face.** It breaks remote-access mods when a player changes the
  configuration. MMDLib's fixed numbering is the better rule.
- **Leave: hard-coded atlas positions.** Every Tesla-Core-Lib piece blits fixed UVs from one
  texture, so retexturing means keeping those coordinates. MMDLib's named, nine-sliced sprites
  avoid that.
