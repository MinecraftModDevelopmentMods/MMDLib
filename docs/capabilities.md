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

`ScreenHost.screenFluids()` exposes storage to resource-bound screen elements. `FluidTankElement`
owns the player view for its selected tank: manual extraction is allowed from input/manual tanks,
while insertion filters, output-role restrictions and reservations remain enforced by storage.
Side modes apply to automation. The storage has no tooltip or cursor-container interaction code.

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

## Stable storage identities and recovery

`ItemInventories` groups named, **independent `ItemStorage` handlers**. Each handler owns its
contents, limits, filters, revisions and reservation plans. `inventory("input")` returns that
physical handler; `address(global)` / `index(name, local)` translate the optional combined view
used by block capabilities and legacy callers. Both `ItemStorage` and `ItemInventories` implement
`ItemStore`, so recipe groups and `MachineInventoryLifecycle` support either. Transactions track
the physical handler and local index even when several recipe aliases address the same slot.

Named saves store each handler under `Inventories/<name>`, with slot identities scoped to that
name. Inventory order can change without moving contents between groups. Loading also accepts
old flat `ItemStorage` saves: globally unique old slot IDs and legacy numeric mappings migrate
into the configured handlers. Removed inventories and reduced limits retain displaced entries
in `Recovery`; restoring the inventory makes those entries available again. Keep inventory names
stable as well as slot IDs. `ItemInventories.partition(names)` allocates handlers while retaining
an existing combined slot order, including interleaved groups.

Assign durable identities with `items.slotId(index, "input")` and `fluids.tankId(index, "coolant")`.
IDs must be nonblank and unique within that storage. They are save identities, separate from
numeric recipe/menu indexes and from resource-group or XML element IDs.
Assign explicit IDs before the first save/load. `ItemInventories` supplies IDs for remaining
unnamed slots at that point, choosing unused local numeric IDs (prefixed with underscores when
needed), so explicit numeric IDs such as `"1"` and `"2"` cannot collide with constructor defaults.

Saves include `SlotId` / `TankId` where configured. Loading matches identified entries by ID;
an unknown ID never falls back to its former numeric position. Saves without identities load by
index, or through the explicit `deserializeNBT(registries, tag, IntUnaryOperator)` migration
overload. Select that mapping by your machine's save version; it applies only to legacy entries.
Keep slot/tank IDs stable when reordering a definition.

For `BasePoweredMachine`, override `protected IntUnaryOperator legacySlots(String key,
CompoundTag tag)`. The key identifies the storage and `tag` is the complete world-save tag, so
the hook can read your saved version before `loadMachineData` runs. It defaults to identity
and applies to both items and fluids before features restore their reservations. Entries with
stable IDs bypass the mapping. Direct lifecycle users can pass the same resolver to `load`.

Storage save keys cannot use `automation`, `features`, `redstone_control`, `redstone_mode`,
`id`, `components`, `mmdlib_features`, or `mmdlib_machine`; those belong to persistence or sync.

Storage keeps its configured shape. Removed slots/tanks, overflow after reducing limits/capacity,
incompatible colliding contents and undecodable entries are retained in raw `Recovery` data,
instead of being discarded. Subsequent loads retry recovery entries, allowing returning IDs or
registrations to make them usable again. Loading restores existing contents without treating them
as a new insertion through recipe filters.

`recoveryContents()` returns a snapshot. `recover(Consumer<CompoundTag>)` hands displaced contents
to a durable consumer-owned sink, clearing them only after the callback succeeds. `StorageRecovery`
uses the same `resources` envelope as automation escrow, with `mmdlib:items` / `mmdlib:fluids`
payloads. MMDLib does not register a recovery item; the companion demonstrates a Recovery Capsule.
Use `MachineInventoryLifecycle` or [BasePoweredMachine](block-entities.md#basepoweredmachine) to
coordinate storage, reservations, saving, clearing and this handoff. Explicit `clear()` destroys
both ordinary and recovery contents; stop/reset owning features first.

External admission rules can be combined with `restrict(predicate, indexes...)`. Recipe-linked
input rules are installed by [MachineResources](inventory-automation.md#shared-machine-resource-definitions).
They supplement existing filters and reservations. Machine products use `insertResult` for items
or `fillResult` for fluids to bypass external admission rules; fluid products still obey the
tank's structural filter and capacity. Restoring a saved item explicitly can use `restoreStack`.
Recipe-linked filters index accepted items and fluids per recipe-manager snapshot and rebuild
after reload or client recipe synchronization. Component-sensitive custom ingredients still test
the actual stack on every probe; item identity alone cannot decide those matches.

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
  Per-slot/per-tank mode functions are also supported. Prefer `MachineResources` and
  `automation.mode(type, index, side)` for independent groups sharing a storage.

- `ItemStorage` keeps its configured slot count and `FluidStorage` its configured tanks. Entries
  that cannot be restored remain in recovery; see stable identities above.
- `EnergyBuffer` stores up to `Long.MAX_VALUE`. `EnergyGaugeElement` reads the full `long`
  from `ScreenHost.screenEnergy()` and owns its UI snapshots and tooltip. Other mods see at
  most `Integer.MAX_VALUE` through the automation capability.
- The runtime smoke test covers roles, filters, limits, faces, out-of-range indices, transfer,
  NBT round trips, int clamping, and the face mapping. Real cables and pipes still need testing
  in game.

### Physically unavailable faces

Use `new SideConfig(IoMode.BOTH).disabledFaces(Set.of(RelativeFace.RIGHT))` for a face that
must never expose this resource. All faces are available by default. Restrictions are relative
to the controller front and rotate with it; RIGHT means the viewer's right while looking at
the front (west for a north-facing controller).

This is machine policy, distinct from a player's Disabled mode. `faceAvailable(face)` reports
it; `get`, `set`, `cycle`, and `copyFrom` enforce it. Restore decoded modes into the existing
policy-bearing instance. The codec saves modes only, not restrictions. Changing the mask
notifies the owner once, and re-enabling a face leaves its mode Disabled. AutomationInventory
also advances its view revision and invalidates capability caches on side changes.

Retained handlers must still consult the current policy: use `items.view(slot ->
sides.mode(facing(), side))` and `energy.view(() -> sides.mode(facing(), side))`, intersected
with each resource's supported direction. Passing a mode value creates a fixed view and cannot
track future changes. Active transfers must consult the same policy. Null-side/internal access
remains host-owned; a machine may refuse it independently of its six physical faces.
