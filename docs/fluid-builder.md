# Material fluid templates

FluidBuilder.create()/INSTANCE is the shared empty template. All setters return
new versions, and build requires both a template name and a material binding.
It returns an immutable MMDFluid definition, not registered NeoForge objects.

```java
var molten = FluidBuilder.create().setName("mmdlib:molten")
        .setDensity(7000).setViscosity(4000).setTemperature(1200)
        .setLightLevel(8).setTickRate(20);
var copper = molten.forMaterial("basemetals:copper").build();
var tin = molten.forMaterial("basemetals:tin").build();
```

The family IDs for copper are basemetals:copper_molten (fluid type, source fluid,
and optional liquid block, each in its own registry),
basemetals:flowing_copper_molten, and basemetals:copper_molten_bucket. Naming follows
the same material_namespace:material_path_template_path rule as other builders.

TypeProperties holds density, viscosity, temperature (Kelvin), light, motion
scale, swimming/drowning/extinguishing, boating, and hydration flags. Density may
be negative for buoyant gases. Viscosity and temperature are nonnegative, light
is 0..15, and motion scale must be finite/nonnegative. Defaults are density 1000,
viscosity 1000, temperature 300, zero light, motion scale .014, swimming/drowning
true, and other flags false.

FlowProperties holds slope search distance (nonnegative), level decrease per block
(1..8), tick rate (positive), explosion resistance (finite/nonnegative), and source
conversion. Defaults are 4, 1, 5, 100, and false. Appearance holds still/flowing
texture IDs, an optional overlay, and ARGB tint. Defaults use vanilla water
textures, no overlay, and opaque white. This is unresolved client configuration;
a later factory/client extension must actually apply it.

Liquid block and filled bucket definitions are enabled by default. The liquid
block has no ordinary BlockItem; the bucket stacks to one. setBlockTemplate and
setBucketTemplate reuse the existing block/item builders while specializing their
names/materials to the fluid family. The source templates remain unchanged.
setHasBlock(false) or setHasBucket(false) suppresses those definitions. A disabled
blockId is null; bucketId always returns the reserved family name even if the
bucket is disabled. The default liquid-block declaration disables collision and
occlusion and uses hardness 100; custom block settings are retained otherwise.
Bucket container references and fire resistance can be supplied by its item
template; none is inferred automatically.

FluidTemplate.CODEC is a standard DFU codec with JSON and NBT support:

```json
{
  "type_properties": {"density": 7000, "viscosity": 4000, "temperature": 1200},
  "flow_properties": {"tick_rate": 20, "can_convert_to_source": false},
  "appearance": {
    "still": "example:block/molten_still",
    "flowing": "example:block/molten_flow",
    "tint": -1
  },
  "block": {"properties": {"light_level": 8}},
  "bucket": {"fire_resistant": true, "container": "minecraft:bucket"}
}
```

FluidParser(templateId, json).parse() returns an unbound builder for the material
loader. Conflicting resource and embedded names fail. Explicit block/bucket
settings cannot accompany disabled components; liquid blocks cannot declare an
ordinary block item. Parent and event action IDs remain unresolved metadata.

FLUIDS_REGISTRY is the NeoForge definition registry, separate from the actual
FLUID_TYPE, FLUID, BLOCK, and ITEM registries. A later material content factory
must create/register the type, source, flowing variant, liquid block, and bucket
using deferred suppliers for their circular references. This builder does not
register fluids, provide runtime flow behavior, install renderers, generate tags,
or implement interaction callbacks. The startup loader now loads material plans, and RuntimeContentRegistrar applies their fluid families; see [the vanilla example](vanilla-extensions.md).

verifyFluidBuilder runs under check/build and covers material isolation, family
IDs, validation, optional companion definitions, and JSON/NBT round trips.
