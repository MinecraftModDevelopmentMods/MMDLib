# Block entities and machines

World entities (`Entity` / `EntityType`) and block entities (`BlockEntity` /
`BlockEntityType`) use separate definitions, factories, plans, and registries.
Machine state belongs to the block-entity path. It does not require an item or a
world entity to exist.

## Data definition

A reusable machine form at `data/example/block/crusher.json` can contain:

```json
{
  "factory":"example:machine_block",
  "template":{"has_item":true},
  "part":{
    "block_entity":{
      "factory":"example:crusher",
      "factory_data":{"capacity":32}
    }
  }
}
```

`example:machine_block` supplies the block behavior, while `example:crusher`
supplies its block entity. These are independent implementation IDs. The nested
block-entity object uses `BlockEntityTemplate.CODEC`; its settings use the
selected factory's codec. No built-in machine names or classifications exist.

Selecting `example:crusher` in a material's `blocks` section creates the block
and a material-bound `MMDBlockEntity`, both named like `example:copper_crusher`
when the material is `example:copper`. They register in separate Minecraft
registries. Excluding the block or omitting its required mod also removes its
block entity. `has_item:false` suppresses its block item without suppressing the
block entity. Selecting no world entities does not suppress block entities.

The common plan exposes `blockEntities()` keyed by the loaded part descriptor,
and `blockEntityDefinitions()` keyed by generated registry ID. A block entity
cannot be published as an orphan of an unselected block. The definition layer
publishes these to `mmdlib:block_entities`; Minecraft runtime types must also be
queued in `minecraft:block_entity_type`.

## Implementation contract

Register a `BlockEntityFactory<E, S>` in a `BlockEntityFactories` catalog:

```java
factories.register(ResourceLocation.parse("example:crusher"),
        new BlockEntityFactory<CrusherEntity, Integer>() {
    public Class<CrusherEntity> blockEntityClass() { return CrusherEntity.class; }
    public Codec<Integer> settingsCodec() {
        return Codec.intRange(1, 4096).fieldOf("capacity").codec();
    }
    public CrusherEntity create(BlockEntityType<CrusherEntity> type, BlockPos pos,
            BlockState state, MMDBlockEntity definition, Integer capacity) {
        return new CrusherEntity(type, pos, state, capacity);
    }
    public BlockEntityTicker<CrusherEntity> ticker(Level level,
            MMDBlockEntity definition, Integer capacity) {
        return level.isClientSide ? null : CrusherEntity::serverTick;
    }
});
```

Here `CrusherEntity` is the mod's `BlockEntity` implementation. Its constructor
must use the supplied type, position, and block state. `serverTick` has Minecraft's
`(Level, BlockPos, BlockState, CrusherEntity)` ticker signature. The implementation
owns inventories, processing, persistence, synchronization, menus, capabilities,
and any client rendering. [Capability wrappers](capabilities.md) cover
the inventory, tank, energy, and side-configuration parts. Return null for no ticker or for the side that must not
tick. A new data variant can reuse this implementation with different settings.

`prepare(definition, CrusherEntity.class)` validates factory ID, class, and typed
settings immediately without constructing game objects. Its overload accepts
`DynamicOps<JsonElement>` (including `RegistryOps`) for registry-aware settings.
`Registries.BLOCK_ENTITY_FACTORIES` is the library's shared catalog; mods may also
own independent catalogs, like the existing world-entity factory API.

## Runtime registration

`RuntimeContentRegistrar.queue` registers block entities with the rest of a plan,
for material machines and [standalone machines](definition-loading.md#standalone-blocks)
alike. For each block whose part declares `block_entity` it:

- looks up the factory in `Registries.BLOCK_ENTITY_FACTORIES` (or the catalog passed
  to the registrar's four-argument constructor) and decodes its settings at load, so
  an unknown factory or bad `factory_data` fails at startup;
- refuses the block if its implementation's `blockClass()` is not an `EntityBlock`;
- prepares a `BlockEntityHandle` with the block's ID for the owning mod to register;
- hands the block implementation a `BlockEntityBinding` through
  `BlockContext.blockEntity()`.

After `registrar.queue(...)`, the owning mod passes its own block-entity register to each
prepared handle. MMDLib does not create or attach that register:

```java
// During construction, before registry events:
var types = DeferredRegister.<BlockEntityType<?>>create(
        net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, MODID);
types.register(modBus);
// After definitions have been queued, still before registry events:
registrar.blockEntityHandles().values().forEach(handle -> handle.register(types));
```

Each handle permits one registration in its definition's namespace. The block receives the
same handle through its binding; `handle.get()` and `binding.type().get()` resolve the registered
type. An unregistered handle fails common setup, before a machine can be placed.
`registrar.blockEntityTypes()` lists registered holders by ID, e.g. for capability registration. The built-in block implementation `mmdlib:machine` builds a plain
`MaterialEntityBlock` from the binding, so a machine needs no block class of its own:

```json
{
  "factory": "mmdlib:machine",
  "template": {"has_item": true},
  "part": {"block_entity": {"factory": "example:crusher", "factory_data": {"capacity": 32}}}
}
```

A machine that needs its own block (facing, shapes, a menu on right-click) registers a
block implementation whose `create(BlockContext)` reads the binding:

```java
public CrusherBlock create(BlockContext context) {
    var binding = context.blockEntity().orElseThrow().as(CrusherEntity.class);
    return new CrusherBlock(context.properties(), binding.prepared(), binding.type());
}
```

The block delegates `newBlockEntity` and `getTicker` to `binding.prepared()`, as
`MaterialEntityBlock` does. It also overrides `onRemove` as `MaterialEntityBlock` does: without
it, `DropsContents` is never called and nothing reports the missing hook. It reads `binding.type()` only after registration (for example
when a block entity is created), never in its constructor.

## Machine features

A `FeatureHost` runs a machine's `MachineFeature`s, such as a `RecipeProcessor`, and saves each
under its key. The block entity ticks it from `serverTick` with `features.tick(level)`, and saves
and loads it through a `DynamicOps`, after the storages, since a processor checks the loaded
contents against its saved cycle:

```java
final FeatureHost features = new FeatureHost(this::setChanged).add(processor);

// saveAdditional
tag.put("features", features.save(registries.createSerializationContext(NbtOps.INSTANCE)));
// loadAdditional, after the storages
features.load(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("features"));
```

**Running recipes.** A `RecipeProcessor` is built from a feature key, a machine recipe type and a
`MachineInventory`, and added to the host; the block entity's ticker calls `features.tick(level)`:

```java
final RecipeProcessor processor = new RecipeProcessor("work", MyRecipes.CRUSHING, inventory);
final FeatureHost features = new FeatureHost(this::setChanged).add(processor);

// The factory's server-side ticker:
BlockEntityTicker<CrusherEntity> ticker =
    (level, pos, state, entity) -> entity.features.tick(level);
```

`status()` is one of: `IDLE`, no cycle is running; `RUNNING`, a cycle is running; `STALLED`, a
cycle is waiting for energy and keeps its progress and reservations; `BLOCKED`, a cycle has run its
time but cannot finish, say an input was taken, and retries each tick; and `PAUSED`, held by an
interrupt. `progress()` and `time()` give the ticks done and the ticks the cycle takes, for a
screen; `time()` is 0 when idle or paused.

On `/reload` a running cycle finishes as planned, and the next cycle uses the new recipes.

**Interrupting.** `interrupt()` stops the processor from drawing energy and starts nothing new
until `resume()`; `paused()` says whether it is held. It works without power and whatever the
status. It also releases the running cycle's reservations, so the items it locked can be taken
out. This is the trap for players: an unpowered machine keeps its inputs and outputs locked, and
the only ways to get them back are an interrupt or breaking the block. A mod should give the
player a button, an `Element.Button` in the screen XML with a `gui-click` handler (see
[GUI screens](gui-screens.md)) that calls `interrupt()` or `resume()`. Resuming reserves the paused
recipe again if it still can. Progress is kept only if that recipe still exists, can reserve its
inputs and output space again, and has the same `time`; otherwise progress resets.

**Groups.** The processor reaches storage through the inventory's group keys. Groups in one
inventory must not overlap: use one key for each set of slots or tanks. Multiple processor lanes
can share that inventory and reserve the same slot, provided enough unreserved inputs and output
space remain; each lane holds only its own share of the slot's plan.

A machine with one slot for both input and output must use one key for both. For a different-item
product, the cycle must also consume the entire stack: a recipe turning 1 iron into 1 gold cannot
start with 2 iron in the slot, because the leftover iron blocks the gold (`NO_ROOM`). A slot limit
of 1 makes that recipe usable without asking players to remove surplus iron. A product that stacks
with the remaining input can share the slot, subject to its limit.

**Clearing.** When replacing a block, `/setblock` and `/fill` normally call `Clearable.tryClear` on
its block entity first, which is how a chest's contents vanish instead of dropping. A
block entity with `DropsContents` that should do the same also implements `Clearable`, and
empties its storages (and their plans) in `clearContent()`. Without it, the command drops the
contents. Both commands' `destroy` mode destroys the old block before clearing, so its contents
still drop even when the block entity implements `Clearable`.

**Dropping contents.** A block entity of a `MaterialEntityBlock` (which includes `mmdlib:machine`)
that implements `DropsContents` has `dropContents(level, pos)` called on the server when its block
is broken or replaced by another block, but not when only its state changes. Usually it is
`items.dropAll(level, pos)`. Reserved items are still in their slots, so nothing else needs to be
given back.

**Syncing to clients.** Nothing is sent to clients unless the block entity opts in. A feature that
should be readable on the client, for a renderer, particles or a tooltip, returns true from
`syncsToClient()`; `RecipeProcessor` does. The block entity then wires `FeatureSync`:

```java
final FeatureHost features =
    new FeatureHost(this::setChanged, () -> FeatureSync.send(this)).add(processor);

@Override
public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
  return FeatureSync.updateTag(features, registries);
}

@Override
public Packet<ClientGamePacketListener> getUpdatePacket() {
  return ClientboundBlockEntityDataPacket.create(this);
}

@Override
protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
  super.loadAdditional(tag, registries);
  if (FeatureSync.read(features, tag, registries)) return;
  // the usual loading
}
```

The host sends at most once every 10 ticks (`FeatureHost.SYNC_INTERVAL`), and always sends the
latest change. Storages are not part of the update.

A feature controls what clients get with two methods. `clientState()` is the state to send, by
default what `state()` saves; a feature overrides it for a moment clients shouldn't see.
`restoreOnClient(state)` takes it on, by default as `restore`. `RecipeProcessor` overrides the
second to copy only its status, pause flag, progress and time, because a client has no storage
contents to check a cycle against, and never reserves anything.

On the client, `processor.status()` and `processor.time()` are current. `processor.progress()` is
as of the last status change, so a progress bar reads it from a screen getter instead; screens keep
their own getter sync (see [GUI screens](gui-screens.md#wiring-a-machine)). While paused, `time()`
is 0, so a bar must not divide by it without checking.

`FeatureSync.read` goes by the tag's key, not the side: a `/data merge` that includes the key
runs `restoreOnClient` on the server.

## Deferred registration and block hooks

The registrar does all of the following for you; it is how to wire a block entity by
hand, outside a registrar.

`BlockEntityFactories.queue` takes a Minecraft block-entity DeferredRegister, a
prepared factory, and a lazy block supplier. It checks namespace immediately,
then resolves and validates the owning block when that registry supplier runs.
Blocks must already be registered at that point and must implement `EntityBlock`.

`MaterialEntityBlock<E>` is an optional generic block base implementing the
creation and ticker hooks. A custom machine block can delegate the same hooks to
`Prepared.create` and `Prepared.ticker` instead. A registration helper owned by
the mod can look like this (surrounding imports and machine class omitted):

```java
private DeferredHolder<BlockEntityType<?>, BlockEntityType<CrusherEntity>> crusherType;

void registerMachine(MaterialFactory.Result result, MaterialPart part,
        BlockBehaviour.Properties properties, IEventBus modBus) {
    var factories = Registries.BLOCK_ENTITY_FACTORIES;
    var definition = result.content().blockEntities().get(part);
    var prepared = factories.prepare(definition, CrusherEntity.class);
    var blocks = DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK,
            definition.block().getNamespace());
    var types = DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE,
            definition.name().getNamespace());
    var block = blocks.register(definition.block().getPath(), () ->
            new MaterialEntityBlock<>(properties, prepared, () -> crusherType.get()));
    crusherType = factories.queue(types, prepared, block);
    blocks.register(modBus);
    types.register(modBus);
}
```

This illustrates one registration; integrate it into the consuming mod's runtime
registrar instead of registering the same block twice. `properties` comes from
that registrar's conversion of the bound block definition. The field is assigned
before registration events fire, so its type supplier is not evaluated early.
Use one holder per generated machine variant. The selected machine block factory
should perform equivalent wiring for repeated data-driven variants.

`Prepared.buildType(block)` is the lower-level alternative for a custom registry
supplier. `Prepared.create` checks the returned object's type, position, and state;
the ticker adapter checks the requested block-entity type before returning a
ticker. World-entity factories and world-entity registration are unaffected.

The loader does not automatically discover world datapacks or queue every planned
runtime object. Startup discovery and complete runtime application remain separate
integration work. The regression checks cover planning, codecs, mod gates, class
and settings validation, and deferred construction. Actual machine placement,
save/load, ticking, menus, and capability interactions require in-game validation.
