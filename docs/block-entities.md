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
and any client rendering. Return null for no ticker or for the side that must not
tick. A new data variant can reuse this implementation with different settings.

`prepare(definition, CrusherEntity.class)` validates factory ID, class, and typed
settings immediately without constructing game objects. Its overload accepts
`DynamicOps<JsonElement>` (including `RegistryOps`) for registry-aware settings.
`Registries.BLOCK_ENTITY_FACTORIES` is the library's shared catalog; mods may also
own independent catalogs, like the existing world-entity factory API.

## Deferred registration and block hooks

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
