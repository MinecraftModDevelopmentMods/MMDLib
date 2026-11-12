# Java multiblock machines

MMDLib provides whole-machine placement for fixed Java-defined structures. One controller owns storage and processing; linked physical parts expose interactions and optional resource ports. The companion's **Multiblock Test Bench** is a complete registration example in `MultiblockDemo` and `MultiblockDemoEntity`.

The common structure model is in `zone.moddev.mc.lib.machine.multiblock`. NeoForge placement, blocks, linked block entities and capability adapters live in `machine.neoforge`. These APIs do not depend on the XML machine-definition runtime. Screens can still use ordinary MMDLib XML.

## Define the structure

Construct one `MultiblockDefinition` after your block registrations are available and return it from your placement item's supplier and controller factory. It declares:

- A namespaced ID and positive version. Changing the saved structure contract requires an explicit version/migration decision.
- A `StructureResolver`, initially a `FixedLayout`.
- Finite limits: at most 4,096 members and offsets bounded by 128 blocks on each axis. Choose much smaller limits for ordinary machines.
- A `Dismantling` policy: `REMOVE_OWNED` for packaged machines or `LEAVE_BLOCKS` for already-built structures.
- Named requirement predicates and item/fluid/energy port declarations.

For example, the fixed layout can be constructed as follows, with `controllerBlock` and `beltBlock` already registered:

```java
var layout = new FixedLayout(
    List.of(
        new StructureMember("controller", "controller", BlockPos.ZERO,
            controllerBlock.defaultBlockState()),
        new StructureMember("belt", "belt", new BlockPos(1, 0, 0),
            beltBlock.defaultBlockState())),
    List.of(new StructureRequirement("clearance", new BlockPos(1, 1, 0), true)));
```

The controller is the sole member with role `controller`, at offset zero. Member keys and positions must be unique. States and offsets are authored facing north; `StructureTransform` rotates positions and states consistently for four horizontal orientations. Custom blocks must implement state rotation; `MultiblockBlock` supplies horizontal facing and rotation.

Required space is separate from physical membership. A rule is a mod-owned `(Level, BlockPos) -> boolean` predicate. Set `continuous` for conditions that suspend operation while obstructed; otherwise a requirement is checked only at formation. Clearance rules for an animal gym should allow the intended animal while refusing obstructing blocks. Requirements never become removable dummy blocks.

## Register the blocks and item

Register a controller block/type, one or more physical part blocks/types and one `MultiblockItem`. No independent part item or part loot is needed for packaged machines.

`MultiblockBlock` is an optional extensible block base that supplies controller ticking, part reconciliation, click routing, horizontal orientation and removal hooks. Register parts with `MultiblockPartEntity`; it saves the controller location, formation UUID and member key without owning storage. Its scheduled load reconciliation handles unavailable controllers and stale links.

Use `new MultiblockBlock(properties, typeSupplier, false)` for non-ticking part blocks. The two-argument constructor enables the controller's `BaseMachine` ticker. Part load reconciliation uses scheduled block ticks rather than a permanent block-entity ticker.

Create the item with:

```java
new MultiblockItem(new Item.Properties(), MyMachine::definition)
```

Placement checks the full footprint, replacement rules, build/world bounds, player permissions, entity collision and requirement predicates. It posts NeoForge's multi-place event and rechecks the candidate before committing. Cancellation rolls back owned writes without consuming the item. A callback's unrelated replacement is preserved. The item consumes one unit on successful survival placement; creative placement consumes none.

`MultiblockPlacement.place(context, definition)` is also available directly. It performs the server transaction and returns a result/diagnostic; it does **not** consume an item. Do not invoke both it and the item's `useOn` for the same placement.

## Compose the controller

Create a `MultiblockInstance` in your `BaseMachine` or `BasePoweredMachine` subclass and override `multiblock()` to return it:

```java
structure = new MultiblockInstance(this, MyMachine.definition(),
    MultiblockMembers.INSTANCE, new MultiblockInstance.Listener() {
        @Override
        public void changed(MultiblockInstance.Status previous,
                MultiblockInstance.Status current, String reason) {
            // Stop/release temporary world interactions when suspended or invalidated.
        }

        @Override
        public void dismantling(MultiblockInstance.Cause cause) {
            // Cancel features, recover/drop owned contents and apply the machine's loot policy.
        }
    });
```

`BaseMachine` maintains the instance before processing, persists its manifest and synchronizes presentation state. While unavailable it suspends features, skips custom before/after processing hooks and blocks lifecycle automation transfers. Refund settlement still runs because already-owed resources must be returned. Other code that pushes resources outside these hooks must check `structureReady()`.

The statuses are `UNFORMED`, `READY`, `SUSPENDED` and `DISASSEMBLING`. Ready means structurally available, not powered or actively processing. An unavailable member chunk suspends the instance without loading that chunk or deleting members. A known missing/wrong member dismantles according to policy. Manifests are saved under `mmdlib_machine.multiblock`; earlier flat manifests still load. Reloaded manifests are suspended until verified; incompatible manifests remain inactive and are retained alongside storage.

The current fixed-layout runtime validates its bounded membership once per controller tick. `ready()` only reads the last lifecycle result; capability access never scans or dismantles the structure. Retained ports still verify their controller and accessed member identities on every call. World removals invalidate through block hooks; obstruction and chunk availability are refreshed on the next controller tick. It does not rediscover structure shape. Large variable structures will need indexed invalidation/bounded validation scheduling when their resolver support is added.

Other block entities can implement `MultiblockController` and compose the instance directly. They must call its tick/save/load/client-state hooks and apply processing/transfer gates themselves. Only `BaseMachine` supplies that wiring automatically.

## Dismantling and loot

The library revokes processing and port access before invoking `dismantling`. The consumer owns feature cancellation, resource recovery and machine loot. Give physical members no independent machine-item loot; otherwise ordinary block destruction could duplicate the callback's item drop.

The companion drops stored items once and awards one machine item for a survival break or explosion (`SURVIVAL`/`EXPLOSION`). Creative destruction awards no machine item. Generic replacements and detected broken membership use `INVALIDATED`; interrupted placement uses `RECOVERY`. Neither awards a machine item. If the original break context is unavailable after chunk loading, recovery does not invent survival loot.

`REMOVE_OWNED` removes only matching members with reciprocal ownership, with the controller last. Reentrant removal cannot repeat the dismantling callback. `LEAVE_BLOCKS` clears links and leaves blocks intact; its callback should retain storage and settle reservations without dropping the machine. If the controller itself is removed, `MultiblockBlock` invokes its `DropsContents` hook before removing the block entity, including unformed and `LEAVE_BLOCKS` controllers. Implement that hook for custom storage; it must drain what it drops so repeated removal callbacks cannot duplicate contents. Storage reduction during a future reformation must use recovery rather than truncation.

Handle world interactions such as animal restraint on controller/chunk unload as well as lifecycle transitions. An unloaded controller cannot keep running a cleanup tick. Store persistent animal fitness on the animal, not in a part block.

## Expose guarded ports

Declare `StructurePort` entries by member key, stable resource name, kind, local faces, unsided permission and input/output permission. Multiple bindings for the same member/kind/face are rejected. Register capabilities on your controller/part types using:

```java
MultiblockPorts.register(event, controllerType, partType);
```

The controller implements `MultiblockResources`, returning its normal filtered resource views from `multiblockItems`, `multiblockFluids` and `multiblockEnergy`. The face argument is in world coordinates. Resolve side configuration against the controller's orientation. Return null for unknown resources. Port permissions only narrow those views; they do not bypass roles, filters, reservations or transfer rates.

The wrappers bind to a controller instance, formation UUID, member link and revision. They check live readiness on reads and writes. A cached wrapper becomes inert while suspended or after teardown and cannot attach to a replacement controller or a new formation. Capability availability changes also invalidate NeoForge caches. Unsided access is refused unless explicitly enabled in the declaration.

Use these adapters for controller ports too; separately registering raw handlers can bypass structure availability. Resource pushing outside the normal machine lifecycle must use the same gate. Remote redstone forwarding is not included.

## Route menus and rendering

`MultiblockBlock` routes an ordinary click through the verified member to the controller. Override item interactions for buckets, tools or other behavior. Custom blocks can call `ScreenRuntime.open(player, controller, clickedMember)`.

`ScreenHost.screenInteractionAnchor` and `screenInteractionIdentity` authorize physical anchors. `BaseMachine` supplies reciprocal member checks and a formation identity for remote anchors. `MachineMenu` retains controller identity, level and player-reach checks throughout the session. Arbitrary nearby positions cannot act as anchors. Diagnostic menus at the controller may remain available while inactive; machine actions must still check readiness.

Menu protocol version **21** includes the opening anchor, so client and server need matching MMDLib versions. Existing single-block hosts default to their controller position.

Use `StructureTransform.bounds` for controller rendering bounds when rendering beyond one block. Shapes and models remain mod-owned. The companion uses per-member static models and a status/resource screen; the library does not render animals or animate a treadmill.

## Extension points for later assembly

`StructureResolver` returns a `ResolvedStructure` containing actual members, requirements and derived measurements. It also validates saved manifests through `accepts`. Its world view refuses reads from unloaded chunks. The fixed-layout adapter produces a candidate without requiring the world to contain the machine already.

`MultiblockInstance.form(candidate, facing)` activates already-existing members independently of placement or item consumption. The caller must authorize any player action before invoking it. `StructureMembership` separates claims from storage, allowing a future persistent casing index instead of a block entity at every position. `LEAVE_BLOCKS` supports non-destructive invalidation. Server tests exercise this path with existing linked members.

Variable-size discovery, passive vanilla-casing ownership, controller activation UX, resizing, in-place rotation/movement and cross-dimension structures are not shipped by this phase. Their implementation can reuse the committed-instance, port and lifecycle contracts.

## Verify the example

`./gradlew check` runs unit and server GameTests, including placement rollback, chunk suspension, save recovery, retained capability guards, member menus and existing-member formation. `./gradlew runMultiblockClientSmoke` runs only the focused multiblock client check and saves a screenshot under `build/multiblock-client-smoke/screenshots/`. The runner verifies real survival placement, part synchronization, remote menu inventory and teardown, and fails without its explicit success marker.
