# Material item templates

`ItemBuilder.INSTANCE` (also returned by `create()`) is the shared empty template.
Every chained operation returns a new builder; always retain its return value.
There is no public mutable builder constructor. Loader code can parse a template
once and reuse it for any number of materials.

```java
var template = ItemBuilder.create().setName("mmdlib:ingot").setMaxStackSize(64);
var copper = template.forMaterial("basemetals:copper").build();
var tin = template.forMaterial("basemetals:tin").build();
```

The built definition's registry ID is `<material namespace>:<material path>_<template path>`.
The source template ID and material ID are retained separately. Build requires
both; `getRegistryName()` returns null until both are available. `buildFor(id)`
is a convenience for `forMaterial(id).build()`. Building never changes either the
root or the parsed template and produces a new MMDItem definition each time.

`MMDItem` is an immutable definition rather than a registered Minecraft Item.
Properties, parent/type references, food/container references, use action IDs,
lore, tool action names, event action references, and creative-stack declarations
are retained for the material loader/content factory to resolve later. Event
references in a template's `events` map are dispatched at runtime by MMDLib's event
bridge (see [item and material bindings](method-events.md#item-and-material-bindings)). Parent
resolution and Minecraft object registration are not performed by ItemBuilder.
Automatic material discovery and runtime registration remain unfinished; this is not a complete load-to-registry
pipeline. ItemParser now decodes through ItemTemplate.CODEC instead of the old
JsonThings helper references. Food is a named reference; inline food objects are
rejected until a separate food-definition builder/schema is implemented.

Defaults are stack size 64, no durability, no fire resistance, no delayed use,
and empty action/lore/event/creative declarations. Setting positive durability
also sets stack size to one. Explicitly combining durability with a larger stack
fails at build time. Collections and creative-stack NBT are defensively copied.
Finish behavior is a namespaced callback ID, not the previously undefined
UseFinishMode type. Lore is currently stored as text strings.

The parser now returns an item template and retains returned versions for its
fluent setter call sites. Attribute modifiers use unresolved 1.21 declarations with type, id, amount,
operation, and slot fields. Attribute holders are resolved later by content factories.
No legacy numeric harvest level has been added. Future tool/block factories must
use the target Minecraft version's tag-based mining rules.

The checkout now targets Java 21 and NeoForge 21.1.247 for Minecraft 1.21.1.
`./gradlew check` runs verifyItemBuilder and verifyItemTemplate. StackContext NBT
is stored in the CUSTOM_DATA component; it is not interpreted as legacy item NBT.
