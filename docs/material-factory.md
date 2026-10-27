# Material definitions, builder, and factory

For a mod-author walkthrough with a complete material-data example, see [the mod-author guide](mod-author-guide.md).


MMDMaterial is an immutable definition. MaterialBuilder.INSTANCE/create() is its
shared empty builder; every setter returns a new version. A builder needs a name
before build(). MMDMaterialCodec.MMD_MATERIAL_CODEC is the standard DFU codec and
supports JsonOps and NbtOps. It can decode an anonymous definition; MaterialFactory
binds its identity from the owning resource ID and rejects conflicting names.

```json
{
  "type": "metal",
  "color": -25792,
  "hardness": 3.5,
  "strength": 8,
  "magic": 4.5,
  "damage": 2,
  "items": "all !hoe !smallpowder",
  "blocks": ["all", "!door", "!double_slab"],
  "entities": "all",
  "has_fluid": true,
  "harvest_tags": ["minecraft:mineable/pickaxe"],
  "special": {"blast_resistance": 8, "temperature": 1200, "density": 7000}
}
```

## Selection sections

Each section accepts a whitespace-separated string or array of strings. `all`
means every standard part in that section; `!name` removes a part. Exclusions
always win, including when they precede all or a later explicit include. An empty
string/list selects nothing. A negative-only expression selects nothing: use
`all !hoe`, rather than `!hoe`, to select everything except the hoe.

Omitted sections default to all. Unknown names and names from another section
fail decoding, including unknown excluded names. POWDER's current name is dust;
powder remains an accepted alias. The codec serializes resolved selections in a
deterministic form, rather than preserving the original expression's spelling.

* items covers the catalog's ITEM forms (including tools, armor, ammo, and the
  Mekanism crafting forms).
* blocks covers BLOCK and ITEM_BLOCK forms, including anvil, door, slab, ores,
  and the placeable crafting plate. Paired block items are controlled here.
* entities covers arrow and bolt companions. Both the item and entity section
  must enable the corresponding projectile; excluding its entity also excludes
  that ammunition item, so no item with a missing companion is planned.
* has_fluid defaults true. False skips the fluid-family builder.

Mekanism gates still apply after selection; all cannot enable optional forms when
Mekanism is absent. There is no hard dependency on Mekanism. MaterialSelection
exposes the section selections, and parts() resolves them into MaterialParts for
the standard planner. Existing flat MaterialParts constructors remain supported.

## Properties and construction

Color is a signed integer ARGB value (default -1, opaque white). Hardness, strength,
and magic are finite nonnegative doubles, preserving fractional affinities such
as 4.5. Damage is a finite double. Type defaults to generic. Unspecified numeric
stats default to zero. Extra finite numeric stats go under special. Harvest
requirements use tag IDs, not numeric harvest levels. Material event declarations
are namespaced action references; parent is an unresolved material reference.

```java
var material = MaterialBuilder.create().setName("example:copper")
        .setType("metal").setColor(0xFFFF9B40)
        .setHardness(3.5).setStrength(8).setMagic(4.5)
        .setItems("all !hoe").setBlocks("all !door").build();
var result = new MaterialFactory().create(material);
```

MaterialFactory.load(materialId, Reader/Path) decodes and plans in one operation;
decode(materialId, Reader) only decodes/binds the material. Result contains both
material metadata and its immutable MaterialContentPlan. File-based errors include
the path; codec and construction errors identify the material. A predicate-taking
constructor supports explicit mod environments and isolated tests. The default
constructor checks NeoForge ModList while planning.

Standard block templates receive material hardness, harvest tags, and blast
resistance (special.blast_resistance, otherwise twice hardness). Nonempty harvest
tags also enable requiresCorrectToolForDrops. Fluid appearance receives material
color; special.temperature, density, and viscosity override their defaults and
must be integers accepted by FluidBuilder. Fluid settings are not applied when
has_fluid is false. Strength, magic, damage, type, extra stats, and material event
references remain in the definition for later runtime content factories; no new
tool balancing or callback implementation is assumed here.

withTemplates(planner -> ...) returns a new factory whose overrides run after
material defaults, using the planner's existing withItemTemplate/withBlockTemplate/
withEntityTemplate/withFluidTemplate methods. For example:

```java
var factory = new MaterialFactory().withTemplates(planner ->
        planner.withBlockTemplate(zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.snapshot().parts().resolve("block").getOrThrow(),
                BlockBuilder.create().setStrength(9, 20)));
```

MMDMaterial.planContent(id) uses factory defaults. Overloads accepting a planner
use that planner directly, for callers who explicitly own all template settings.
The legacy instance load(id) now reads standard material JSON, rather than trying
to execute the previous incomplete JSLT transform.

These operations create definitions, not registered Minecraft items/blocks,
fluids, or entity types. Concrete behavior factories, parent inheritance,
automatic mod-resource discovery, and the final registry/application stage remain
separate work. verifyMaterialFactory runs with check/build and covers exclusions,
section validation, JSON/NBT round trips, material stats, optional-mod gates,
factory overrides, and identity binding.

## Registered extension types and callbacks

The current material codec includes namespaced item/block extension IDs in the
same section selections and accepts typed callback bindings under `event`.
See [the extension API](material-extensions.md). Unknown IDs are errors, so register
code extensions before decoding. `events` remains a legacy input alias.
MaterialFactory results also expose custom definitions in extraItems/extraBlocks.

See [data-defined content modules](data-modules.md) for the bundled baseline, pack schemas, overrides, and current runtime integration limits.

See [the maintained vanilla extensions](vanilla-extensions.md) for a complete, playable use of the same data and runtime APIs.
