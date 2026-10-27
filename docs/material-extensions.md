# Material extension API

`MaterialExtensions.GLOBAL` is the common code-owned catalog. Register event keys,
callbacks, and custom item/block types during mod construction, **before** material
JSON that references them is decoded. Independent MaterialExtensions instances
can be passed to MaterialFactory and MMDMaterialCodec.forExtensions for isolated
loading/testing. Registration rejects duplicate IDs. Mekanism remains optional.

## Events and callbacks

```java
var api = MaterialExtensions.GLOBAL;
var tick = api.events().registerEvent("example:armor_tick", ArmorContext.class);
api.events().registerCallback(ResourceLocation.parse("example:starsteel_bonus"), tick,
        context -> context.applyBonus());
```

ArmorContext is supplied by the consuming mod; the API does not impose an armor
implementation. Another example is a callback taking a native PlayerTickEvent.Post
or LivingEquipmentChangeEvent. Event keys declare the required context class.
Callbacks are registered for exactly one such event. Material JSON binds them:

```json
{
  "event": {
    "example:armor_tick": "example:starsteel_bonus"
  }
}
```

The material codec validates event names, callback IDs, and their matching event
contracts. `event` is canonical; the older `events` spelling is accepted on input,
but supplying both is an error. The existing events() accessor and setEventMap
remain source-compatible. Each event has one callback binding; compose Consumers
or dispatch within the callback if several actions are needed.

```java
api.events().dispatch(materialDefinition, tick, context);
```

Built-in contracts bound in a material's `event` map are dispatched through its items and blocks
by MMDLib's event bridge (see [method events](method-events.md#item-and-material-bindings)).
There is no blanket execution for other events. To bridge another NeoForge gameplay
event, register a key with that event class and attach a resolver:

```java
var playerTick = api.events().registerEvent("example:player_tick", PlayerTickEvent.Post.class);
var subscription = api.events().listen(NeoForge.EVENT_BUS, playerTick,
        event -> materialsAffectedBy(event.getEntity()));
```

The mod implements materialsAffectedBy, for example by inspecting equipped items.
The bridge deduplicates equal definitions within one event and dispatches only
those materials' bound callbacks. Closing the AutoCloseable removes its listener.
NeoForge cancellation, threading, and logical-side semantics are retained; a
server-only effect callback/resolver must check the event's side. Exceptions
propagate rather than being silently discarded. Client-only context registration
belongs in client-only code.

The global callback catalog is published to MMDLib's EVENT_CALLBACK_REGISTRY by
the mod's registration listener. Independent catalogs can be published explicitly
with MaterialRegistration.publishCallbacks during RegisterEvent.

## New item and block types

Implement `ItemTypeFactory<I extends Item>`:

* itemClass(): the concrete class token.
* template(material): an immutable ItemBuilder for that material's definition.
* create(material, definition): a fresh game object, invoked by the deferred supplier.

`BlockTypeFactory<B extends Block>` provides blockClass(), template(), and create()
with BlockBuilder/MMDBlock equivalents. Template methods may use material stats;
create methods apply the supplied definition's properties and implement behavior.
The library does not silently substitute generic objects for custom machinery.

```java
api.registerItemType(ResourceLocation.parse("example:component"), componentFactory);
api.registerBlockType(ResourceLocation.parse("example:crusher"), crusherFactory);
```

The overloads accepting Optional<String> requiredMod gate planning on another mod.
Do not reference unavailable third-party implementation classes while registering
optional integrations. No Mekanism classes are referenced by MMDLib itself.

Custom types live in the normal selection sections:

```json
{
  "items": "all !example:component",
  "blocks": ["all", "!example:crusher"],
  "event": {"example:armor_tick": "example:starsteel_bonus"}
}
```

`all`, and omitted sections, include registered custom types as well as standard
parts. Exclusions work for namespaced types. Wrong-section/unknown IDs fail rather
than being ignored. Decoding takes a current catalog snapshot for each operation,
so a codec created before registration still sees types added before decoding.
Already decoded selections stay immutable; adding a type later does not alter
old definitions. Register all types before decoding to keep defaults predictable.
Programmatic MaterialBuilder setters use the global catalog; with an independent
catalog, obtain selections via its codec and MaterialBuilder.from(decoded).

MaterialFactory plans all loaded definitions together. extraItems()/extraBlocks() are keyed by type ID; blocks contribute their
paired item definition unless their BlockBuilder disables it. Each custom type's
name/material/type are specialized by the factory. IDs follow the existing naming
rule using the type's path: example:copper_crusher. Custom namespaces do not change
that rule; conflicting generated IDs, including collisions with fluid companions,
fail explicitly. Builtin parts cannot be replaced by registration.

## Factory implementations

A data module's `factory` names an `ItemImplementation` or `BlockImplementation`,
registered with `registerItemImplementation` / `registerBlockImplementation`. MMDLib's
own factories (`mmdlib:pickaxe`, `mmdlib:armor`, `mmdlib:door`, `mmdlib:pressure_plate`,
...) are registered exactly this way by `StandardImplementations`, alongside the bundled
modules; the runtime registrar has no list of built-in IDs, and an unregistered factory
fails when the definition is queued.

The registrar calls `create(ItemContext)` / `create(BlockContext)`. The contexts carry
what MMDLib's own items and blocks are built from, so an addon can build the same kind
of object:

| `ItemContext` | `BlockContext` |
|---|---|
| `material()`, `definition()`, `part()`, `factoryData()` | `material()`, `definition()`, `part()`, `factoryData()` |
| `properties()`: fresh `Item.Properties` from the definition | `properties()`: fresh `BlockBehaviour.Properties` with the material's sound and map colour |
| `tier()`: the material's tool tier | `blockSetType()`: `factory_data.block_set_type`, else the material's |
| `armor()`: the material's armor material | `materialBlockSetType()`: always the material's |
| `projectile()`: the part's registered projectile type | `baseBlock()`: the material's base block, lazily |
| | `events()`: the event registry, for blocks that dispatch bindings |

Implementations may also override:

- `validate(part)` to decode `factory_data` at load, so bad data fails before registry
  construction.
- `usesArmorMaterial()` (items) to have the material's armor material registered; it is
  registered only when some selected part asks.
- `createItem(block, context)` (blocks) to choose the block's item, e.g. a door's
  `DoubleHighBlockItem`; the default is a `MaterialBlockItem` that burns for the
  material's `block_burn_time`.

```java
api.registerBlockImplementation(ResourceLocation.parse("example:glowing_plate"), new BlockImplementation<GlowingPlate>() {
    public Class<GlowingPlate> blockClass() { return GlowingPlate.class; }
    public GlowingPlate create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("needs a context"); }
    public GlowingPlate create(BlockContext context) { return new GlowingPlate(context.blockSetType(), context.properties().lightLevel(state -> 7)); }
});
```

Existing implementations that only override `create(material, definition, data)` keep
working: the context overload calls it by default. A `MaterialExtensions(false)` has no
bundled modules or built-in factories; call `StandardImplementations.install(api)` to
add the factories.

## Registry hooks

Queue actual custom game objects through a consuming mod's DeferredRegisters:

```java
var holder = api.queueBlock(blockRegister, crusherTypeId, CrusherBlock.class,
        result.material(), result.content().extraBlocks().get(crusherTypeId));
```

queueItem is equivalent for custom items. Registers must use the material's
namespace and be attached to the mod bus. These helpers validate class tokens,
material/type ownership, and registry namespaces, then defer create() until the
registry supplier executes. Actual factories must return fresh non-null objects.
This API does not auto-register a block's game BlockItem; register it through the
item register with the block holder and the paired item definition's settings.
Do not pass a paired block-item definition to an unrelated item type factory.

MaterialRegistration.publishDefinitions(event, result) hooks the general MMDLib
MATERIALS/ITEMS/BLOCKS/ENTITIES/FLUIDS definition registries. It includes custom
definitions, block items, and fluid companions. Call it from the mod-bus
RegisterEvent listener after decoding/planning, once per result. Those definition
registries are distinct from Minecraft's ITEM/BLOCK registries.

Automatic material discovery and registration of every builtin runtime form are
still separate work. Planning and codec use do not instantiate game objects.
Existing direct MaterialContentPlanner calls plan standard parts; use
MaterialFactory to include registered extension types.

verifyMaterialExtensions tests dynamic type selections, excludes, optional mods,
codec round trips, typed callback validation/dispatch, listener cleanup, deferred
creation boundaries, registry validation, and ID collisions. Actual game-object
registration, behaviors, and renderer effects require an in-game test.

See [load-time method events](method-events.md) for `events` extension arrays, public static method resolution, every built-in contract (player, weapon, tool, harvest, block, projectile) with its handler signature, and the runtime event bridge.
