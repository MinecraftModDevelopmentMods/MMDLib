# Standard material-part planning

MaterialPart defines all 54 canonical forms (55 historical names, with MEK_CRYSTAL aliasing CRYSTAL) supplied in the catalog, preserving ITEM,
BLOCK, and ITEM_BLOCK classifications. POWDER serializes as `dust`; `powder` is
accepted as a legacy alias. Other enum names serialize in lowercase. `end_ore`
and `nether_ore` are accepted aliases for endore and netherore.

MaterialParts.CODEC is a DFU-coded selection:

```json
{
  "parts": ["ingot", "arrow", "bolt", "block", "slab", "crystal"],
  "has_fluid": true
}
```

Omitted parts means the full catalog. An explicit empty list selects no parts.
Omitted has_fluid means true, independently of which parts are selected. False
suppresses the entire fluid family. Duplicate entries, including aliases, and
unknown names fail decoding. MaterialSelection and MMDMaterialCodec now provide the full material-stat and
section-expression schema; see material-factory.md. This flat selection remains
available for explicit programmatic use.

## Optional Mekanism content

CRYSTAL (also accepted as MEK_CRYSTAL), SHARD, CLUMP, and POWDER_DIRTY are gated on the `mekanism`
mod ID. Selecting them explicitly cannot bypass the gate. ORE, ENDORE, and
NETHERORE do not depend on Mekanism. No Mekanism classes or dependencies are
referenced. Definitions are omitted before their builders run, so no standalone
Mekanism item/block references are produced when the mod is absent.

## Applying templates

```java
var planner = new MaterialContentPlanner();
var plan = planner.plan(materialId, MaterialParts.DEFAULT);
```

The runtime overload checks NeoForge ModList. An overload taking
Predicate<String> supports tests and explicit loading environments.
MMDMaterial now owns an immutable MaterialParts selection (defaulting to DEFAULT)
and exposes planContent(materialId), plus overloads for custom planners and mod
predicates. These methods are explicit; material discovery is not wired to run
automatically by the entrypoint.

Direct item forms use ItemBuilder with their part ID as type. Block forms use
BlockBuilder, with paired item definitions by default. ANVIL, DOOR, and SLAB
retain their historical ITEM_BLOCK classification and use their block's item
rather than creating another independent item. DOUBLE_SLAB has no separate item.
Arrow and bolt each run both ItemBuilder and EntityBuilder; their item and entity
IDs match across separate registries. Default factory IDs are mmdlib:arrow and
mmdlib:bolt. Factory implementations must still be supplied before preparing types.
Every material runs FluidBuilder unless has_fluid is false. Default fluid template
name is mmdlib:molten, producing material_molten and its normal fluid companions.

Plan items() includes direct items and enabled block items. Fluid block/bucket
companions remain under fluid(), since they are not separate MaterialPart values.
The plan's maps are immutable and contain definitions, not live game objects.

Use withItemTemplate, withBlockTemplate, withEntityTemplate, and withFluidTemplate
to customize an immutable planner. Parts keep their standard names so item/entity
and block/item counterparts retain matching registry IDs. Fluid templates must
have a name. Custom item settings for a block go through its BlockBuilder item
template. Separate material plans share no mutable builder state.

The planner supplies concrete *definition types*, not implementations of each
Minecraft class. Swords, scythes, crackhammers, redstone blocks, projectile entities,
and other runtime behaviors must be created by the later content-factory stage;
none are silently replaced with generic objects. MaterialFactory now applies block/fluid defaults from material stats and can
decode raw JSON. Parent inheritance, resource/tag generation, automatic discovery,
and runtime object registration remain unfinished.

verifyMaterialParts runs under check/build, testing all forms with and without
Mekanism, default/disabled fluids, projectile companions, selection codecs,
custom templates, and isolation between material plans.

## Runtime handling requirements

MaterialPart.handling() distinguishes basic items/blocks from armor, tools,
ammunition, ranged weapons, and specialized blocks. armorSlot() identifies HEAD,
CHEST, LEGS, and FEET for the four wearable armor pieces; horse armor is separate.
These pieces require a material-specific ArmorMaterial holder and ArmorItem type,
not ordinary Item instances. requiresSpecializedFactory() and hasPlacementLogic()
make that requirement explicit for runtime factory dispatch.

Stairs, slabs (including the historical double slab), plates, ladders, levers,
doors, trapdoors, buttons, connecting blocks, and other stateful blocks need their
own state/shape/placement/interaction implementations. A plate is the placeable
crafting plate, distinct from PRESSURE_PLATE. Placement and state handling do not
by themselves imply a BlockEntity. No listed part is arbitrarily marked as needing
one; machinery factories must register block-entity types/tickers where required.

This metadata is not a runtime implementation. The generic content planner still
creates declarations. Actual ArmorItem creation, specialized block subclasses,
placement states, and block entities remain runtime-factory work.

MEK_CRYSTAL is a source-level constant alias and legacy JSON spelling of CRYSTAL.
It is an item, retains the Mekanism gate, serializes canonically as crystal, and
never generates another item or block. In section expressions either name selects
or excludes the same item. Flat part arrays reject both names together as duplicates.
