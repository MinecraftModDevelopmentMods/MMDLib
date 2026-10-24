# Pluggable material entity templates

EntityBuilder uses the same immutable root/material binding contract as ItemBuilder
and BlockBuilder. Build produces an MMDEntity definition, not a live entity or an
EntityType registration. Registry names use the same material_namespace:
material_path_template_path rule. Name, material binding, and a factory ID are
required; getRegistryName returns null until both name and material are set.

## Factory contract

```java
public final class MyProjectileFactory implements EntityFactory<MyProjectile, Settings> {
    public Class<MyProjectile> entityClass() { return MyProjectile.class; }
    public Codec<Settings> settingsCodec() { return Settings.CODEC; }
    public MyProjectile create(EntityType<MyProjectile> type, Level level,
                               MMDEntity definition, Settings settings) {
        return new MyProjectile(type, level, definition.material(), settings);
    }
}
```

Both the entity class E and factory-specific settings S are generic. Register code
implementations under namespaced IDs, for example during the consuming mod's
constructor:

```java
Registries.ENTITY_FACTORIES.register(
        ResourceLocation.parse("example:my_projectile"), new MyProjectileFactory());
```

The catalog is also independently instantiable for isolated loading/testing. IDs
must be unique. Factory creation must return a non-null instance of the declared
class using exactly the supplied EntityType and Level. Each spawn calls create
again; implementations must return a fresh entity. The settings codec should
produce immutable settings because its decoded value is shared by spawns of the
type. Optional configureType(builder, definition, settings) can customize the
EntityType builder after common settings have been applied.

## Templates and resolution

```json
{
  "factory": "example:my_projectile",
  "category": "misc",
  "width": 0.5,
  "height": 0.5,
  "tracking_range": 4,
  "update_interval": 3,
  "save": true,
  "summon": true,
  "fire_immune": false,
  "factory_data": {"power": 2}
}
```

EntityTemplate.CODEC handles the common schema using DFU. EntityParser accepts a
resource ID plus JSON, as with the other template parsers. FactoryData preserves
a defensive copy of the factory-specific input; the actual factory's settingsCodec
decodes it during prepare, allowing arbitrary factory-defined schemas. Templates
round-trip through JsonOps and NbtOps, for inputs representable by those ops.

```java
var template = new EntityParser(templateId, json).parse();
var definition = template.forMaterial("example:copper").build();
var prepared = Registries.ENTITY_FACTORIES.prepare(definition, MyProjectile.class);
// During mod construction, queue the type on the consuming mod's DeferredRegister:
var holder = entityTypes.register(definition.name().getPath(), prepared::buildType);
```

Use the material namespace for that DeferredRegister. prepare checks the factory
ID, exact class token, and factory settings without creating game objects. A
three-argument overload accepts DynamicOps<JsonElement>, including RegistryOps,
for codecs requiring registry lookups. Prepare those only after their required
registries exist. Prepared.buildType runs inside the registry supplier; the actual
entity factory runs when that EntityType creates an entity, including deserialization.

Definitions have a NeoForge custom ENTITIES_REGISTRY alongside the existing
item/block/material definition registries. It is distinct from Minecraft's
ENTITY_TYPE registry. The material loader must still populate definitions and
queue type registrations; it is not completed by this builder.

Defaults are misc, .5 by .5 dimensions, tracking range 4 (Minecraft chunk units),
update interval 3 ticks, saving/summoning enabled, and no fire immunity. Dimensions
are finite and nonnegative (including zero-sized marker entities), tracking is
nonnegative, and update interval must be positive. Parent and event action IDs are
metadata for the future loader and factory; this builder does not implement
inheritance or dispatch callbacks.

Renderers stay client-side and are registered for the resulting holder through
EntityRenderersEvent.RegisterRenderers. No client renderer classes are referenced
by the common factory API. Mob factories still need attribute suppliers, spawn
placement rules, goals, and other relevant NeoForge registrations. Arrows/bolts
can implement this interface, including their own ammunition pickup and spawn
synchronization; no projectile-specific implementation is installed yet.

verifyEntityBuilder runs with check/build. It covers immutable material branches,
defensive factory data, codec round trips, factory lookup/class mismatch/errors,
and factory-specific settings validation. Actual spawning, persistence, rendering,
and type registration need verification in a running NeoForge instance.
