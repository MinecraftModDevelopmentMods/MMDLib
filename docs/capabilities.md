# Capability wrappers

`zone.moddev.mc.lib.api.capabilities` wraps NeoForge's item, fluid, and energy capabilities
for machine block entities. A block entity owns one storage per resource. It uses the storage
directly, and automation gets per-face views of it.

| Class | Wraps | Main points |
|---|---|---|
| `ItemStorage` | `ItemStackHandler` | Per-slot `Role`, filter, limit; `insertResult` for products; `dropAll`; reservations |
| `FluidStorage` | several `FluidTank`s as one `IFluidHandler` | Per-tank `Role`, capacity, filter; `tank(i)` for the machine; reservations |
| `EnergyBuffer` | `IEnergyStorage` | `long` internally; `int` values clamped for other mods; transfer rates |
| `SideConfig` | | Per-face `IoMode`, stored relative to the machine's front; codec |
| `MachineCapabilities` | `RegisterCapabilitiesEvent` | One-line registration with per-side modes |
| `Transfer` | | Move items, fluids, or energy between handlers or to a neighbour |

## Rules

- **Roles** limit what automation may do with a slot or tank:
  - `STORAGE` allows insert and extract.
  - `INPUT` allows insert only.
  - `OUTPUT` allows extract only, and players cannot place items into it either.
  - `MANUAL` allows neither, so only players can use the slot through the screen (for example
    upgrade slots).
- **The block entity is not limited by roles.** It fills outputs with
  `ItemStorage.insertResult` or `FluidStorage.tank(i)`, and spends energy with
  `EnergyBuffer.extract` / `consume`, which ignore the transfer rates. Filters always apply,
  except to `insertResult`.
- **Faces.** Every face shows all slots and tanks with the same numbering. A face's `IoMode`
  (`DISABLED`, `INPUT`, `OUTPUT`, `BOTH`) only allows or refuses operations. Contents stay
  readable on every face.
- **Out-of-range slot indices** are refused, not thrown. A read returns an empty stack, an
  insert hands the stack back, and an extract returns nothing.
- **A view reads its mode on every call**, so changing the side configuration or rotating the
  machine needs no `invalidateCapabilities`. A null side (unsided access, for example
  information mods) gets `BOTH`. Roles still apply to it.
- **Relative faces.** `LEFT` and `RIGHT` are as seen by a player looking at the front. For a
  machine facing up or down, `TOP` is the north side. `RelativeFace.facing(state)` reads the
  `facing` or `horizontal_facing` property, and defaults to north when the state has neither.

## Reservations

`ItemStorage` and the client menu handler share `PlannedItemHandler` for insertion, extraction and
capacity rules. Item and fluid storages use `ReservationPlans` for plan merging, release and
transaction rollback. Direct content setters remain available to the owning machine and save loading.

Custom menu handlers and wrappers can implement `ReservedSlots`, including `plan(slot)`, to publish
the same snapshots as `ItemStorage`. During a click, menu slots expose only unreserved items to
vanilla; outside the interaction, rendering and synchronization still show the full stack. A number
key or offhand swap can therefore move the free portion without moving reserved items.

A machine that runs a recipe over many ticks can reserve what the recipe needs. `ItemStorage`
keeps one `SlotPlan` per slot and `FluidStorage` one `TankPlan` per tank: how much the machine will
take from it, and what it will add. `RecipeProcessor` makes and releases the plans itself; a mod
that runs its own cycles uses `reserve(slot, plan)`, `release(slot, plan)` and `plan(slot)`
(`tank` for fluids). A slot or tank with no plan behaves exactly as without this feature.

What a plan does to the calls on the storage:

| Call | With a plan |
|---|---|
| `extractItem`, `drain` | Never goes below the reserved amount. 50 iron with 1 reserved gives up 49 |
| `insertItem`, `fill` | Accepted only if the slot still ends up as one legal stack within its limit after the machine takes and adds what it planned, and the stack in the slot now can hold it |
| `insertResult` | Obeys the plan like `insertItem`; it skips roles and filters, not reservations |
| `tank(i)` | Its `fill` and `drain` obey the tank's plan |
| `isItemValid` | Answers for the planned end state, so a hopper is not offered what would break the plan |
| `setStackInSlot`, `setFluid` | Still bypass the plan. They stay available for undoing a transaction and loading a save, and they are the one way to break a running cycle |

Plans are not saved by the storages. A feature saves its own cycle and puts the plans back when it
loads, so load the storages first.

## Example

```java
public class CrusherEntity extends BlockEntity {
  final ItemStorage items = new ItemStorage(4)
      .onChange(this::setChanged)
      .role(Role.INPUT, 0)
      .role(Role.OUTPUT, 1, 2)
      .role(Role.MANUAL, 3)   // upgrade
      .limit(1, 3);
  final EnergyBuffer energy = new EnergyBuffer(100_000, 1_000).onChange(this::setChanged);
  final SideConfig itemSides = new SideConfig(IoMode.INPUT)
      .set(RelativeFace.BOTTOM, IoMode.OUTPUT)
      .onChange(this::setChanged);

  static void serverTick(Level level, BlockPos pos, BlockState state, CrusherEntity be) {
    var input = be.items.getStackInSlot(0);
    var result = ...; // the mod's recipe lookup
    if (!input.isEmpty() && be.items.fits(1, result) && be.energy.consume(20)) {
      be.items.extractItem(0, 1, false);
      be.items.insertResult(1, result, false);
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("Items", items.serializeNBT(registries));
    tag.put("Energy", energy.serializeNBT(registries));
    tag.put("ItemSides", SideConfig.CODEC.encodeStart(NbtOps.INSTANCE, itemSides).getOrThrow());
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    items.deserializeNBT(registries, tag.getCompound("Items"));
    energy.deserializeNBT(registries, tag.get("Energy"));
    SideConfig.CODEC.parse(NbtOps.INSTANCE, tag.get("ItemSides")).ifSuccess(itemSides::copyFrom);
  }
}

// In the mod's RegisterCapabilitiesEvent handler:
MachineCapabilities.items(event, CRUSHER.get(), be -> be.items,
    (be, side) -> be.itemSides.mode(be.getBlockState(), side));
MachineCapabilities.energy(event, CRUSHER.get(), be -> be.energy);
```

To push results out each tick, use
`Transfer.pushItems(level, pos, side, be.items.view(IoMode.OUTPUT), 8)`. For a neighbour that
is checked every tick, keep a NeoForge `BlockCapabilityCache` instead.

## Notes

- **Side configuration per resource group.** The screen's `<side-config>` view edits one
  `SideConfig` per group (item input, item output, a tank, energy), Industrial Foregoing style;
  the block entity returns them from `ScreenHost.screenSides()`. A view still takes one mode per
  face for the whole storage, so the mod's `sides` function decides how groups combine. When
  groups differ by `Role`, take insert from the input group and extract from the output group.
  A per-slot mode in the views, which would remove that step, is not implemented.

- `ItemStorage` keeps the slot count given to its constructor. Loading a save that has more
  slots drops the stacks that no longer fit. `FluidStorage` loads its tanks by index.
- `EnergyBuffer` stores up to `Long.MAX_VALUE`. Screens show the full `long` through a getter
  such as `#getEnergy` (`stored()`), while other mods see at most `Integer.MAX_VALUE`.
- The runtime smoke test covers roles, filters, limits, faces, out-of-range indices, transfer,
  NBT round trips, int clamping, and the face mapping. Real cables and pipes still need testing
  in game.
