# Material block templates

`BlockBuilder.INSTANCE`/`create()` is the shared empty root. Every setter returns
a new builder. Parse/configure once, then bind independent copies for materials:

```java
var template = BlockBuilder.create().setName("mmdlib:block")
        .setStrength(3, 8).setRequiresCorrectToolForDrops(true)
        .setBlockTags(Set.of(ResourceLocation.parse("minecraft:mineable/pickaxe")));
var copper = template.forMaterial("basemetals:copper").build();
var tin = template.forMaterial("basemetals:tin").build();
```

Build requires a name and material. Registry names follow ItemBuilder's
`<material namespace>:<material path>_<template path>` rule. `buildFor(id)` is a
shortcut for binding and building. The result is an immutable MMDBlock definition,
not a Minecraft Block instance or registry entry. Parent/type and event action IDs
are retained for the later material-loading/content-factory stage.

Properties cover hardness, blast resistance, friction, speed/jump factors,
constant light level, correct-tool requirement, collision, occlusion, random
ticking, replaceability, and unresolved sound/map-color keys. Defaults are
stone-like (hardness 1.5, resistance 6, friction .6), normal speed/jump, zero light,
collision/occlusion enabled, and other flags disabled. Hardness -1 means
unbreakable; other values must be nonnegative. Nonfinite numbers are rejected.
Light must be 0..15. Event maps and block tag sets are defensively copied.

Harvest requirements use block tag memberships, such as mineable/pickaxe and
needs_iron_tool, together with requires_correct_tool. There is no numeric harvest
level. The builder declares tags; a later registration/datagen stage must emit
those memberships. Setting random_ticks or an event reference declares behavior;
content factories/callback implementations still have to implement it.

A block item definition is created by default with the same registry ID/material.
`setItemTemplate(itemBuilder)` supplies its settings and enables it. The source
item template remains unchanged; its name/material are specialized to the block.
`setHasItem(false)` suppresses it. The builder does not register either object.

BlockParser uses BlockTemplate.CODEC, with JSON parsing limited to the JsonOps
boundary. The same codec supports NbtOps. Example:

```json
{
  "properties": {
    "hardness": 3,
    "blast_resistance": 8,
    "requires_correct_tool": true,
    "sound": "metal"
  },
  "tags": ["minecraft:mineable/pickaxe", "minecraft:needs_iron_tool"],
  "item": {"fire_resistant": true}
}
```

Name can come from the resource path via `new BlockParser(templateId, json)`;
conflicting embedded names fail. Properties are nested under `properties`.
`has_item` defaults true and cannot be false alongside an explicit item template.
The paired item uses ItemTemplate.CODEC; custom shapes/state-dependent properties
and specialized block classes remain content-factory concerns.

`verifyBlockBuilder` runs with Gradle check/build, testing chain isolation,
material/item binding, validation, and JSON/NBT round trips. The overall material
loading and object-registration pipeline remains unfinished.
