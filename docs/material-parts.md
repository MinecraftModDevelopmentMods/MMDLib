# Loaded material parts

`MaterialPart` is an immutable descriptor supplied by the loaded catalog. It has
no named part constants, behavior enum, placement switch, global registry lookup,
or global codec. Its item/block kind identifies the owning registry section;
block-item creation is controlled by the block template's `has_item` setting.

`PartRegistry` resolves IDs and namespace-scoped aliases. `MaterialParts` holds a
material's chosen descriptors and fluid flag. Resolve and decode against the
catalog you loaded, including its extensions:

```java
var parts = extensions.snapshot().parts();
var machine = parts.resolve("example:machine").getOrThrow();
var partCodec = parts.codec();
var selectionCodec = MaterialParts.forRegistry(parts);
```

Descriptors compare by namespaced module ID. Do not compare object references
across snapshots. The material codec takes a fresh snapshot for each operation;
it sees modules registered after the codec object was created. Static default
selections/codecs on the selection helpers cover the resource-defined baseline;
use the explicit catalog APIs for extended sets.

The flat `MaterialParts` codec accepts:

```json
{"parts":["ingot","arrow","example:machine"],"has_fluid":false}
```

Omitting `parts` selects that codec's entire catalog. An empty array selects none.
Unknown IDs and duplicates, including aliases of the same part, fail decoding.
The full material codec exposes section expressions instead: `items`, `blocks`,
and `entities`, accepting `all` and `!` exclusions. Exclusions win regardless of
order. See [material factory](material-factory.md).

## Data and behavior

The bundled resource manifest `data/mmdlib/modules.json` indexes the baseline;
external module directories are scanned without a manifest. The baseline
currently contains 54 forms, but there is no fixed registry size. An empty
`MaterialExtensions(false)` has no parts until modules are installed.

A module's `factory` ID selects registered Java behavior. Its `factory_data` is
interpreted by that factory's codec. For example, armor's slot is data for the
armor implementation, not a classification inside `MaterialPart`:

```json
{
  "factory":"mmdlib:armor",
  "factory_data":{"slot":"helmet"},
  "template":{"max_stack_size":1}
}
```

The previous `handling`, `armor_slot`, and `item_block` part fields are removed.
There is no behavior fallback inferred from a part's name. Standard runtime
helpers dispatch registered implementation IDs, and an unknown implementation
fails explicitly.

Optional `part` metadata defines aliases and companions:

```json
{
  "factory":"example:projectile_item",
  "part":{
    "aliases":["dart"],
    "entity_factory":"example:projectile_entity",
    "entity_data":{"lifetime":100}
  },
  "template":{"max_stack_size":24}
}
```

In `data/example/item/needle.json`, this defines `example:needle` with alias
`example:dart`. Aliases cannot shadow another ID or alias, and must stay in the
module's namespace. Short names resolve in `mmdlib`; added forms use namespaced IDs.

`entity_factory` describes a world entity and is allowed on **items or blocks**.
It enters the material's `entities` selection and receives `entity_data` as its
factory settings. An excluded required entity also excludes its owning form.
Projectile item recovery remains the responsibility of the entity implementation.

A machine's block entity is separate from a world entity. A block module can use
`part.block_entity` to declare one, with no item or world-entity companion needed.
See [block entities and machines](block-entities.md) for its schema, typed factory,
deferred type registration, and ticking hooks.

`requires_mod` stays at module level. When its mod is absent, the form and its
companions are omitted before their factories run. The bundled Mekanism crystal,
shard, clump, and dirty-dust modules use this gate without a hard dependency.
Historical aliases (`powder`, `mek_crystal`, `end_ore`, `nether_ore`) are in data.
