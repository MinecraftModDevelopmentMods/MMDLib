# Data-defined content modules

A module defines a reusable form; a material supplies its identity and properties. For example, the `mmdlib:ingot` module becomes a copper ingot when selected by a copper material. The bundled forms use the same DFU codecs and catalog entries as forms supplied by another mod or pack.

The baseline lives in `src/main/resources/data/mmdlib/item/`, `block/`, and `fluid/`: 32 item forms, 22 block forms, and the `molten` fluid module. `MaterialExtensions()` loads these resources automatically. `MaterialExtensions(false)` creates an empty catalog for isolated use.

## Module files

A pack or mod resource root contains these singular directories:

```text
data/example/item/token.json
data/example/block/panel.json
data/example/fluid/coolant.json
```

Their IDs are `example:token`, `example:panel`, and `example:coolant`. Nested paths are supported. These files are reusable definitions, separate from models, recipes, tags, and material definitions.

An item module uses this format:

```json
{
  "factory": "mmdlib:basic_item",
  "template": {
    "max_stack_size": 64,
    "fire_resistant": false
  }
}
```

A block module can retain literal settings instead of deriving them from the material:

```json
{
  "factory": "mmdlib:plate",
  "material_defaults": false,
  "template": {
    "properties": { "hardness": 2.0 }
  }
}
```

All three module kinds require `factory` and `template`. Their optional `requires_mod` field gates availability; for example, the bundled crystal, shard, clump, and dirty-dust modules require `mekanism`. The legacy `mek_crystal` name aliases `crystal`, without creating a second form.

`factory_data` is an optional object interpreted by the Java behavior implementation. Bundled armor modules specify their slot there; several placement-sensitive blocks specify block-set types or a base block. Supplying data to an implementation that does not explicitly handle it fails rather than silently discarding it.

Block and fluid modules support `material_defaults`, which defaults to `true`. Blocks then derive hardness and blast resistance from material properties, merge material harvest tags with template tags, and require a correct tool when harvest tags are present. Fluids derive tint from the material and apply any material `special.temperature`, `special.density`, and `special.viscosity` values. Set `material_defaults` to `false` to retain literal template values. The bundled molten template supplies temperature 1300, density 3000, and viscosity 6000 before material overrides.

See the bundled JSON files and [builder documentation](item-builder.md) for template fields; [block](block-builder.md) and [fluid](fluid-builder.md) templates use their respective builder schemas.

## Loading and selecting modules

Load modules before decoding materials and before Minecraft registry events:

```java
var extensions = new MaterialExtensions();
ModuleCatalog.load(packRoot).install(extensions);
var factory = new MaterialFactory(extensions, modId -> ModList.get().isLoaded(modId));
```

`packRoot` contains `data/`. It can be a normal directory or the root of an already mounted ZIP filesystem. The caller discovers pack roots, opens archives, orders them, and closes any filesystem it owns after loading. Missing `data/` produces an empty catalog. Parse failures include the source filename.

Duplicate module IDs fail by default. To intentionally replace definitions, including bundled forms, call `install(extensions, true)` before decoding materials. Installation is sequential; validate packs before publishing a shared catalog if partial installation after an error would matter.

A material can select those modules through its existing sections:

```json
{
  "type": "metal",
  "color": -3368704,
  "hardness": 5,
  "strength": 8,
  "items": "all !hoe !example:token",
  "blocks": "all",
  "fluid": "example:coolant"
}
```

`all` includes available extension forms in that section. Exclusions win regardless of token order. `has_fluid: false` disables fluid creation; otherwise `fluid` defaults to `mmdlib:molten`. Explicitly choosing a fluid whose required mod is absent fails with an error. The primary plan maps use loaded `MaterialPart` descriptors for every form. `extraItems`/`extraBlocks` are compatibility views of added forms, keyed by module ID; they do not use a separate planning path.

## Behavior implementations and runtime integration

The `factory` ID selects executable behavior independently of the module ID. Register an `ItemImplementation` or `BlockImplementation` once with `registerItemImplementation` or `registerBlockImplementation`. Its contract supplies the runtime class and creates the object from a material and bound definition; an overload can decode `FactoryData`. New data variants can reuse that behavior without writing another type factory. Existing code-defined `ItemTypeFactory` and `BlockTypeFactory` extensions remain supported.

Planning does not instantiate Minecraft objects or require behavior implementations to be resolved. Runtime queueing resolves the selected implementation and reports missing handlers. The bundled files provide baseline definitions; automatic registration of all their behavior handlers and generated runtime forms is not supplied by this loader. Fluid factory metadata is preserved in the fluid definition for its runtime integration.

This is the shared data layer for datapack-defined materials, not yet automatic world datapack discovery. There is no world-pack reload listener here. Minecraft item, block, entity, and fluid registry entries must be created during startup, so introducing new registered forms requires an early pack-loading stage and a restart. Runtime registration, client assets, recipes, and emitted tags still need their respective integration; template tag metadata alone does not publish Minecraft tags. These limits apply equally to bundled and added modules.


The optional `part` object defines form metadata and aliases; see
[loaded material parts](material-parts.md). Bundled membership is indexed by the
resource manifest `data/mmdlib/modules.json`, rather than a Java enum. Added
modules automatically participate in the same part registry and section scopes.


For publication or runtime iteration, `MaterialContentPlan.itemDefinitions()` and
`blockDefinitions()` return unique definitions keyed by generated registry ID,
including fluid companions. Compatibility views do not cause duplicate registration.

See [block entities and machines](block-entities.md) for data-defined machine companions, typed factories, deferred registration, and tick hooks.
