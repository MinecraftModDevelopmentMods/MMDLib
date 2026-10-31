# Machines: storage, automation, screens and recipes

This page shows how MMDLib's machine pieces fit together and what a mod still writes itself.
Each piece has its own page with the details:

| Piece | Package | Page |
|---|---|---|
| Machine definition, block-entity type and block | `api` (`BlockEntityHandle`, `BlockEntityBinding`), `common.runtime` | [Block entities](block-entities.md) |
| Item, fluid and energy storage; side modes; capability registration; transfers | `api.capabilities` | [Capability wrappers](capabilities.md) |
| XML screens, the generic menu, value sync, JEI hooks | `gui.*`, `client.jei` | [GUI screens](gui-screens.md), [GUI sprites](gui-sprites.md), [gauge textures](gauge-textures.md) |
| Click and hover handlers for screens | extension files | [Method events](method-events.md) |
| The `mmdlib:crushing` recipe type | `common.recipes` | [Runtime behaviours](runtime-behaviors.md) |

## What MMDLib provides and what the mod writes

| Concern | MMDLib | The mod |
|---|---|---|
| Registering the block-entity type | Prepares a handle from the data definition | Passes its `DeferredRegister` to the handle |
| The block | `mmdlib:machine` (plain `MaterialEntityBlock`), or a binding for the mod's own block | Its own block class if it needs facing, shapes or bucket handling |
| Inventories | `ItemStorage`, `FluidStorage`, `EnergyBuffer` with roles, filters, limits, rates | Which slots and tanks exist, their roles and filters |
| Saving | `serializeNBT` / `deserializeNBT` on each storage, `SideConfig.CODEC` | Calls them from `saveAdditional` / `loadAdditional` |
| Automation access | Per-face views, `MachineCapabilities` registration helpers | Calls `MachineCapabilities.*` from its `RegisterCapabilitiesEvent` handler |
| Side configuration | `SideConfig`, `RelativeFace`, `IoMode`; the screen's `<side-config>` view edits them | Which `SideConfig`s exist and how they map to storages (see below) |
| The screen | XML layout, generic menu, slots, value sync, tooltips, side tabs, pop-outs, JEI | The XML file, `ScreenHost`, and public getters for every shown value |
| Processing | Nothing | The ticker: recipe lookup, progress, energy use, outputs |
| Recipes | Vanilla `Recipe`/`Ingredient`; one built-in type, `mmdlib:crushing` | Its own recipe types and their lookup |
| Upgrades | One-item upgrade slots in the screen | What an upgrade item is and does |

MMDLib has **no machine-ingredient or machine-recipe abstraction** of its own: no common type
for "N items, M mB of fluid and E energy", no generic recipe registry, and no matching or
consumption helpers beyond the storages themselves. Recipes are ordinary Minecraft recipe types,
and inputs are vanilla `Ingredient`s (and NeoForge sized or fluid ingredients if the mod wants
them).

## Data flow

```
                 ┌──────────── block entity (mod code) ─────────────┐
 automation ───► │ capability views ─► ItemStorage / FluidStorage /  │ ◄── ticker: recipes, progress,
 (pipes, hoppers)│   (role + face IoMode)   EnergyBuffer             │     insertResult / tank(i) /
                 │                     ▲                              │     consume
                 │   SideConfig(s) ────┘ (face modes, relative)      │
                 │   public getters  (#getEnergy, #getProgress, ...) │
                 └──────┬───────────────────────────┬────────────────┘
                        │ ScreenHost                │ getters, every tick, server only
                        ▼                           ▼
                 MachineMenu (server + client) ◄── ScreenValues / ScreenSnapshots ── sync packets
                        │                                                   │
                        ▼                                                   ▼
                 real slots by XML index                    MachineScreen (client): draws the XML
```

- The block entity is the only owner of state. The screen never changes storage directly; slot
  clicks go through the menu's real slots (the storage's own rules apply), and buttons run
  `gui-click` handlers on the server.
- Automation sees `ItemStorage.view(...)` / `FluidStorage.view(...)` / `EnergyBuffer.view(...)`,
  never the storage itself. The block entity's own code uses the storage directly, which bypasses
  roles and transfer rates.

## A minimal machine, end to end

```java
public class CrusherEntity extends BlockEntity implements ScreenHost {
  final ItemStorage items = new ItemStorage(3)
      .onChange(this::setChanged)
      .role(Role.INPUT, 0)
      .role(Role.OUTPUT, 1)
      .role(Role.MANUAL, 2).limit(1, 2);            // upgrade slot
  final EnergyBuffer energy = new EnergyBuffer(100_000, 1_000).onChange(this::setChanged);
  final SideConfig inputSides = new SideConfig(IoMode.INPUT).onChange(this::setChanged);
  final SideConfig outputSides = new SideConfig(IoMode.DISABLED)
      .set(RelativeFace.BOTTOM, IoMode.OUTPUT).onChange(this::setChanged);
  final SideConfig energySides = new SideConfig(IoMode.INPUT).onChange(this::setChanged);
  int progress;

  // ScreenHost
  @Override public IItemHandlerModifiable screenItems() { return items; }
  @Override public Map<String, SideConfig> screenSides() {
    // Keys are the element ids listed in <side-config groups="input output energy"/>
    return Map.of("input", inputSides, "output", outputSides, "energy", energySides);
  }
  @Override public int[] screenRecipeSlots(ResourceLocation category) {
    return category.equals(CrushingRecipe.TYPE.getId()) ? new int[] {0} : new int[0];
  }

  // Getters named in the XML (value="#getEnergy" ...); run on the server only
  public long getEnergy() { return energy.stored(); }
  public long getEnergyCapacity() { return energy.capacity(); }
  public int getProgress() { return progress; }
  public int getProgressMax() { return 100; }

  static void serverTick(Level level, BlockPos pos, BlockState state, CrusherEntity be) {
    var input = new SingleRecipeInput(be.items.getStackInSlot(0));
    var recipe = level.getRecipeManager().getRecipeFor(CrushingRecipe.TYPE.get(), input, level);
    if (recipe.isEmpty()) { be.progress = 0; return; }
    var result = recipe.get().value().assemble(input, level.registryAccess());
    if (!be.items.fits(1, result) || !be.energy.consume(20)) return;
    if (++be.progress < 100) return;
    be.progress = 0;
    be.items.extractItem(0, 1, false);
    be.items.insertResult(1, result, false);
  }

  // saveAdditional / loadAdditional: see capabilities.md
}
```

```java
// RegisterCapabilitiesEvent handler (mod bus)
MachineCapabilities.items(event, CRUSHER.get(), be -> be.items,
    (be, side) -> MachineSides.combine(
        be.inputSides.mode(be.getBlockState(), side),
        be.outputSides.mode(be.getBlockState(), side)));
MachineCapabilities.energy(event, CRUSHER.get(), be -> be.energy,
    (be, side) -> be.energySides.mode(be.getBlockState(), side));
```

`MachineSides.combine` is the mod's own helper, shown in the next section. The screen file is
`data/example/mmdlib/screen/crusher.xml` (named after the block-entity type), and the block's
`block-right-click` event is bound to `mmdlib:open_menu`; see [GUI screens](gui-screens.md).

## Side configuration with several groups on one storage

The screen's `<side-config>` view edits one `SideConfig` per **group** (the keys of
`screenSides()`, which must equal the element ids in `groups="..."`). A capability view, however,
asks for one `IoMode` per face for the **whole** storage. MMDLib does not join the two; the mod's
capability lambda does.

When groups are split by role (input slots have `Role.INPUT`, output slots `Role.OUTPUT`), the
per-slot roles already keep each group to its own direction, so the face mode can take its insert
permission from the input group and its extract permission from the output group:

```java
final class MachineSides {
  /** Insert as the input group allows, extract as the output group allows. */
  static IoMode combine(IoMode input, IoMode output) {
    boolean in = input.canInsert(), out = output.canExtract();
    return in ? (out ? IoMode.BOTH : IoMode.INPUT) : (out ? IoMode.OUTPUT : IoMode.DISABLED);
  }
}
```

Limits of this pattern:

- A `Role.STORAGE` slot obeys both groups (inserts follow the input group, extracts the output
  group). Give such slots their own storage if they need their own setting.
- Two groups with the **same** role on one storage (for example two separate input groups) cannot
  be told apart by a face; use one `ItemStorage` per group in that case. `screenItems()` must then
  return one `IItemHandlerModifiable` covering all screen slots, for example NeoForge's
  `CombinedInvWrapper`, and each storage gets its own capability view only if it is exposed to
  automation separately.
- Fluids and energy normally need no combining: each tank set or buffer has its own `SideConfig`.

## Buckets and fluid containers

`mmdlib:open_menu` does not open the screen when the player holds a fluid container (an item with
a fluid-handler capability) and the clicked face exposes a fluid handler; it returns so the click
can reach the block's own item use. MMDLib does not fill or empty the tank itself:
`MaterialEntityBlock` (the `mmdlib:machine` block) has no bucket handling. A machine with tanks
needs its own block whose `useItemOn` calls
`FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection())`.

## Values shown on screen

Every `value`/`max` in a screen file names a public getter (`#method` on the block entity, or a
static `Class#method(blockEntity)`). Getters run on the server each tick while someone has the
screen open, and only changed values are sent. The client never calls them. So:

- any number, fluid or text the screen shows needs a getter, even if it is a field;
- getters must be cheap and must not change state;
- `long`, `BigInteger` and decimal getters are all fine; there is no 16-bit limit.

## Recipes

MMDLib ships one recipe type, `mmdlib:crushing` (`CrushingRecipe`: one `Ingredient` -> one
`ItemStack`, input `SingleRecipeInput`), used by the crackhammer and shown in JEI. A machine may
use it, vanilla types, or its own. For JEI:

- `jei-recipes="<category id>"` on a screen element opens those categories when clicked;
- `ScreenHost.screenRecipeSlots(category)` returns the input slots, in recipe order, for JEI's
  "+" transfer; the default returns none, which disables transfer.

## Planned: machine module

A machine-ingredient and recipe layer (items, fluids and energy in one recipe, transactions, a
processing loop) and, later, machines defined entirely in JSON are designed but not built. See
[machine module](machine-module/README.md).
