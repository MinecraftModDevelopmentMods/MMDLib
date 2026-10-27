# Authoring a material-enabled mod

This guide targets this checkout of MMDLib on **NeoForge 1.21.1 / Java 21**.
The example material is `example:orichalcum`. Substitute your mod ID for `example`.

## What “all features” currently means

A material JSON file describes item, block, projectile, block-entity, and fluid
definitions. `MaterialFactory` validates it and creates an immutable content plan.
The startup loader discovers root-level `mmdlib/` definitions; see
[loading stages](definition-loading.md). The explicit paths in this guide remain
available for mods that own their loading stage.

`RuntimeContentRegistrar` can apply supported built-in plans to Minecraft
registries, including ammunition, armor, stateful blocks, fluid families, and
client hooks. MMDLib uses it for its [playable vanilla extensions](vanilla-extensions.md).
Mods must still supply their assets/recipes, or a generator appropriate to their
forms. Machines and custom world entities use their dedicated typed factory APIs.
The manual wiring below remains an alternative; do not queue objects twice.

## 1. Lay out the data

A useful layout in the consuming mod is:

```text
src/main/resources/
  META-INF/neoforge.mods.toml
  data/example/mmdlib/materials/orichalcum.json
  data/example/recipe/orichalcum_dust_from_ingot.json
  data/example/loot_table/blocks/orichalcum_block.json
  data/example/tags/block/incorrect_for_orichalcum_tool.json
  data/minecraft/tags/block/mineable/pickaxe.json
  data/minecraft/tags/block/needs_iron_tool.json
  data/c/tags/item/ingots/orichalcum.json
  assets/example/lang/en_us.json
  assets/example/blockstates/orichalcum_block.json
  assets/example/models/block/orichalcum_block.json
  assets/example/models/item/orichalcum_block.json
  assets/example/models/item/orichalcum_ingot.json
  assets/example/textures/...
```

The material-file path above is a **convention for your explicit loader**, not a
path MMDLib scans automatically. MaterialFactory accepts a Path or Reader. The
legacy `MMDMaterial.load(id)` uses the older `assets/mmdlib-data/material/` layout;
use MaterialFactory directly for this guide's layout.

The file is read from the mod's bundled resources during mod construction,
before registry events. It is not a server datapack reload entry: item, block,
fluid, and entity types cannot be added by reloading this JSON later.

Declare MMDLib as a required dependency in your NeoForge metadata. This checkout's
mod ID is `mmdlib`, and its current version is `2.0.0-alpha`:

```toml
[[dependencies.example]]
modId="mmdlib"
type="required"
versionRange="[2.0.0-alpha,)"
ordering="AFTER"
side="BOTH"
```

Also declare the usual NeoForge and Minecraft dependencies for your own mod.

## 2. Define the material

Copy [the full example](examples/full-material/orichalcum.json) into the material
path above. Its essential fields are:

```json
{
  "type": "metal",
  "color": -10040150,
  "hardness": 8,
  "strength": 10,
  "magic": 6,
  "damage": 3,
  "items": "all",
  "blocks": "all",
  "entities": "all",
  "has_fluid": true,
  "harvest_tags": ["minecraft:mineable/pickaxe", "minecraft:needs_iron_tool"],
  "special": {
    "blast_resistance": 12,
    "temperature": 1400,
    "density": 7500,
    "viscosity": 4000
  },
  "event": {"example:armor_tick": "example:orichalcum_armor_bonus"}
}
```

`color` is signed integer ARGB; the sample value is `0xFF66CCAA` in Java. JSON
requires a decimal integer, not an unquoted hexadecimal literal. Hardness,
strength, and magic may be fractional. Magic is raw affinity; MaterialStats
normally converts it to integer enchantability using `floor(2.5 * magic)`.
`damage` is the runtime tier's base damage bonus. Type is a classification string,
not a Java class name.

The full example also demonstrates the supported stat overrides for tool uses,
armor protection/durability, shield durability, and crackhammer behavior.
Unspecified overrides use the formulas documented in [runtime behaviors](runtime-behaviors.md).
`special` is numeric data; texture IDs, repair ingredients, and entity factory
settings do not go there. Parent references are retained but inheritance is not
implemented, so do not rely on `parent` to fill missing properties.

### Selecting forms

* `items` includes basic forms, tools, armor, arrows/bolts, and optional Mekanism
  crafting forms.
* `blocks` includes placeable forms and anvil, door, slab, and ores. Their paired
  items are controlled here, not by `items`.
* `entities` controls the arrow/bolt companions. Both their item and entity
  selections must permit them, otherwise neither is planned.
* `has_fluid` defaults true. False suppresses the complete fluid family.

Omitted sections mean all registered types in that section. Use `""` or `[]` for
none. Strings and arrays both support selection expressions:

```json
{
  "items": "all !hoe !smallpowder",
  "blocks": ["all", "!door", "!double_slab"]
}
```

Exclusions win regardless of order. A lone `!hoe` selects nothing; use `all !hoe`.
Unknown names and wrong-section names fail validation. `dust` is the current name
for POWDER; `powder` remains an alias. `mek_crystal` aliases the CRYSTAL **item**;
it is not another block or item. PLATE is a thin placeable crafting plate, distinct
from PRESSURE_PLATE. DOUBLE_SLAB has no separate item by default.

CRYSTAL, SHARD, CLUMP, and POWDER_DIRTY are omitted when Mekanism is absent,
even under `all` or an explicit selection. Do not make Mekanism a required
dependency just to use these gates. The ordinary, End, and Nether ores remain
available independently. Actual Mekanism processing recipes are not generated.

## 3. Register event callbacks before decoding

The example JSON references a callback, so register its key and implementation
first. These imports are used by the following snippets:

```java
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.api.MaterialRegistration;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.runtime.MaterialBacked;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
```

In your mod constructor, using its injected `IEventBus modBus`:

```java
var id = ResourceLocation.parse("example:orichalcum");
var api = MaterialExtensions.GLOBAL;
var armorTick = api.events().registerEvent("example:armor_tick", PlayerTickEvent.Post.class);
api.events().registerCallback(ResourceLocation.parse("example:orichalcum_armor_bonus"),
        armorTick, event -> {
    var player = event.getEntity();
    if (player.level().isClientSide() || player.tickCount % 20 != 0) return;
    int pieces = 0;
    for (var stack : player.getArmorSlots()) {
        if (!stack.isEmpty() && stack.getItem() instanceof MaterialBacked backed
                && backed.material().name().filter(id::equals).isPresent()) pieces++;
    }
    if (pieces > 0) player.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SPEED, 40, pieces - 1, true, false));
});
```

This example gives one level of speed per matching armor piece and lets the short
buff expire after removal. It is illustrative mod behavior, not an intrinsic
material stat. Event context types are checked against registered callbacks.
For no custom effects, omit `event` and this registration entirely. `events` is
accepted as an older spelling, but do not provide both.

## 4. Load and inspect the plan

Still during mod construction:

```java
var file = net.neoforged.fml.ModList.get().getModFileById("example").getFile()
        .findResource("data", "example", "mmdlib", "materials", "orichalcum.json");
MaterialFactory.Result result;
try {
    result = new MaterialFactory().load(id, file);
} catch (java.io.IOException ex) {
    throw new java.io.UncheckedIOException(ex);
}

modBus.addListener((RegisterEvent event) ->
        MaterialRegistration.publishDefinitions(event, result));

// Retain this subscription in your mod if you need to close/remove it later.
var armorSubscription = api.events().listen(NeoForge.EVENT_BUS, armorTick,
        event -> java.util.List.of(result.material()));
```

The callback guards side/tick frequency and checks actual armor. The resolver
above supplies just this mod's material; a multi-material mod can select only the
materials represented by equipped items. MMDLib does not invoke callbacks merely
because their IDs appear in JSON.

`publishDefinitions` registers MMDLib's **definition records**, not the actual
Minecraft objects. Queue those separately before the relevant registry events.

For this example, the plan names include:

| Part | ID |
| --- | --- |
| Ingot | `example:orichalcum_ingot` |
| Powder/dust | `example:orichalcum_dust` |
| Storage block | `example:orichalcum_block` |
| Plate | `example:orichalcum_plate` |
| Scythe / crackhammer | `example:orichalcum_scythe` / `example:orichalcum_crackhammer` |
| Arrow item and entity type | `example:orichalcum_arrow` in separate registries |
| Fluid type, source, optional liquid block | `example:orichalcum_molten` in separate registries |
| Flowing fluid | `example:flowing_orichalcum_molten` |
| Bucket | `example:orichalcum_molten_bucket` |

All loaded forms are in content().items()/blocks()/entities(), keyed by MaterialPart descriptors.
Added forms also appear in extraItems()/extraBlocks() compatibility views, keyed by namespaced type ID.
Fluid companions are under content().fluid(). Recipes and assets must match the
IDs in the plan; do not guess older BaseMetals naming conventions.

### Optional: customize the item, block, and fluid templates

The material schema does not accept a top-level texture/appearance object. Those
settings belong to FluidTemplate. The example folder also includes
[molten.json](examples/full-material/molten.json),
[scythe.json](examples/full-material/scythe.json), and
[stairs.json](examples/full-material/stairs.json). Bundle them anywhere you choose
and explicitly read each as a JsonObject, just as you explicitly locate the
material file. For example, use `data/example/mmdlib/templates/fluids/molten.json`.

```java
var scythe = new zone.moddev.mc.lib.common.parsers.ItemParser(
        ResourceLocation.parse("mmdlib:scythe"), scytheJson).parse();
var stairs = new zone.moddev.mc.lib.common.parsers.BlockParser(
        ResourceLocation.parse("mmdlib:stairs"), stairsJson).parse();
var molten = new zone.moddev.mc.lib.common.parsers.FluidParser(
        ResourceLocation.parse("mmdlib:molten"), moltenJson).parse();
var factory = new MaterialFactory().withTemplates(planner -> planner
        .withItemTemplate(zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.snapshot().parts().resolve("scythe").getOrThrow(), scythe)
        .withBlockTemplate(zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.snapshot().parts().resolve("stairs").getOrThrow(), stairs)
        .withFluidTemplate(molten));
var result = factory.load(id, file);
```

Here scytheJson/stairsJson/moltenJson are the JsonObjects read from those files;
this snippet replaces the earlier `new MaterialFactory().load(...)` call. Register
the same event callback before it executes. Template names/material bindings are
specialized by the planner; keep the fluid template name `mmdlib:molten` if you
want the IDs in the table above.

Overrides replace the corresponding whole template, **not individual fields**
within the factory's computed defaults. The sample fluid/stair files therefore
repeat the desired density, temperature, tint, hardness, and tag values. For
material-dependent overrides, decode the material first and construct builders
from its properties before calling factory.create(material). Fields omitted from
an override use that template codec's defaults. An item definition's fire/lore
settings must still be applied when your runtime factory creates the Item.

## 5. Supply the runtime registration layer

Create and attach DeferredRegisters for Minecraft ITEM, BLOCK, ARMOR_MATERIAL,
ENTITY_TYPE, FLUID, and NeoForge's FLUID_TYPE registry as needed. Use the material's
namespace. The following is an example for **one armor piece**, not a complete
registrar for `all`:

```java
var repairTag = net.minecraft.tags.ItemTags.create(
        ResourceLocation.parse("c:ingots/orichalcum"));
java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair =
        () -> net.minecraft.world.item.crafting.Ingredient.of(repairTag);
var tier = new zone.moddev.mc.lib.common.runtime.MaterialToolTier(
        result.material(), net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL, repair);

// armorRegister and itemRegister are your attached DeferredRegisters.
var armor = zone.moddev.mc.lib.common.runtime.MaterialArmor.register(
        armorRegister, result.material(), repair,
        ResourceLocation.parse("example:orichalcum"));
var helmetDefinition = result.content().items().get(
        zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.snapshot().parts().resolve("helmet").getOrThrow());
var helmet = itemRegister.register(helmetDefinition.name().getPath(), () ->
        zone.moddev.mc.lib.common.runtime.StandardItems.create(
                zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.snapshot().parts().resolve("helmet").getOrThrow(),
                tier, armor, new net.minecraft.world.item.Item.Properties()));
```

The armor holder must be registered before the item registry creates the helmet.
The layer ID corresponds to `assets/example/textures/models/armor/orichalcum_layer_1.png`
and `orichalcum_layer_2.png`. Register all four armor pieces with their respective
types. Horse armor uses the separate AnimalArmorItem implementation and needs its
animal armor texture as well.

The explicit incorrect-blocks tag defines what the **tool cannot harvest**. The
material's harvest_tags instead define block tag memberships to emit. They are
not interchangeable, and neither is a legacy numeric harvest level.

Use StandardItems.create for supported basic/tool/armor forms, including SCYTHE
and CRACKHAMMER. Use StandardBlocks.create for specialized block classes. Supply
fresh properties, an appropriate BlockSetType/WoodType, and a lazy base-block state
for stairs. Queue the storage block before dependent stair creation. Queue block
items separately; do not register a second independent item for anvil/door/slab.
Apply any extra item-definition components/settings in your runtime factory.

**Remaining all-features work:**

| Area | Runtime work required |
| --- | --- |
| Arrow / bolt | Register entity factories matching their template IDs, entity types, ammunition items retaining their pickup stack, and client renderers. These implementations are not installed yet. |
| Fluid | Create FluidType, BaseFlowingFluid.Source/Flowing, optional LiquidBlock and bucket using deferred suppliers for circular references. Apply the declared type/flow settings. |
| Fluid rendering | Install client fluid extensions with still/flowing/overlay textures and tint. JSON appearance alone does not install them. |
| Flower pots | Register potted counterparts; the helper creates an empty pot. |
| Other placement-sensitive blocks | Supply blockstates/models, connection/mining tags, and the native/helper subclass selected by StandardBlocks. |
| Callbacks | Attach the event bridge or invoke dispatch at the actual behavior hook. |

For entity factory setup see [entity builder](entity-builder.md). For fluid data
see [fluid builder](fluid-builder.md). For the supported built-in forms,
`RuntimeContentRegistrar` handles arrow/bolt entities, exact ammunition pickup,
fluid families, and client setup; `StandardItems` alone remains a specialized
item helper rather than the whole application layer.

## 6. Add resources and recipes for the registered objects

The vanilla example includes static artwork and a maintained gameplay-data generator. Other mods can
supply resources by hand or through their own datagen providers; arbitrary mod
assets are not inferred automatically from a material definition.

* **Item models/textures:** every registered item, including ammunition and block
  items. Bows/crossbows, shields, and fishing rods need appropriate property-based
  model overrides/client properties, not only a static texture.
* **Blockstates/models:** use the actual runtime block's properties. Stair facing/
  half/shape, slab type/waterlogged, plate facing, lever attachment/power, and anvil
  damage states must match their implementations.
* **Armor/entity/fluid textures:** armor layers/horse armor, projectile renderer
  textures, and animated still/flowing fluids as applicable.
* **Loot tables:** regular self-drops, ores, doubled slab counts, and other special
  drops. Use `data/<modid>/loot_table/blocks/...` on 1.21.1.
* **Tags:** emit every declared harvest membership, repair ingredient tags, ores,
  ingots, storage blocks, and native connection/enchanting tags where needed.
  A definition's tags are declarations, not edits to Minecraft's tag registry.
* **Recipes:** crafting, smelting, processing, and conversions desired by the mod.
  On 1.21.1 the folder is `data/<modid>/recipe/`.
* **Client color handlers:** install item/block/fluid tint handling and models
  with the appropriate tint indices if you want the material color applied.
  Storing color in the definition does not recolor textures automatically.
* **Ore world generation:** supply configured/placed features and biome/dimension
  integration if ores should occur naturally. Selecting an ore part does not
  schedule world generation.
* **Translations and creative tabs:** supply display names and include the actual
  registered item holders in your mod's creative tab.

For example, the repair tag `data/c/tags/item/ingots/orichalcum.json`:

```json
{"replace": false, "values": ["example:orichalcum_ingot"]}
```

Add your relevant block IDs to the real Minecraft mining/requirement tags, e.g.
`data/minecraft/tags/block/mineable/pickaxe.json` and `needs_iron_tool.json`. A
custom incorrect-for-tool tag is only needed if you choose one instead of the
vanilla tag used in the example tier.

A crackhammer recipe, after both items exist:

```json
{
  "type": "mmdlib:crushing",
  "ingredient": {"item": "example:orichalcum_ingot"},
  "result": {"id": "example:orichalcum_dust", "count": 1}
}
```

MMDLib registers this recipe serializer/type. Right-click the top face of a block
under a dropped ingredient, or the item itself: ordinary use processes the server
config's `crackhammer.crush_count` (default 8), shift-use processes as many as tool
durability allows. The library ships scythe/crackhammer base tags;
your mod can extend them with its harvestable/crushable blocks. For example,
`data/mmdlib/tags/block/crackhammer_crushable.json` can add
`"#c:ores/orichalcum"` and `"#c:storage_blocks/orichalcum"` after your mod defines
those tags. Scythe harvesting
uses the normal server destruction path for each neighboring block.

## 7. Extend beyond standard parts

Register custom ItemTypeFactory/BlockTypeFactory implementations under your own
IDs **before decoding**. Their template methods can inspect the material stats;
their create methods supply the actual behavior, such as a machine block with a
block entity. Those IDs participate in the same `all` and `!namespace:type`
selections. See [material extensions](material-extensions.md) for typed factories,
queueItem/queueBlock, callback contracts, and registry hooks.

Register any block-entity types, tickers, capabilities, menus, networking, and
client renderers required by machinery. Placement logic alone does not make a
block a block entity. Extra factory definitions are not produced for missing
optional mods when their requiredMod gate is set.

## 8. Verify in a running mod

The MMDLib build checks definition codecs and planning. Your consuming mod still
needs to verify registration, resource reloads, harvesting levels, repair tags,
scythe protection events, crackhammer counts/durability, all block placement states,
armor effects, projectile pickup/save/reload, and fluid placement/rendering.

Test with and without Mekanism. `all` should retain the standard material without
loading Mekanism classes when it is absent. Exclude a form and disable has_fluid
in separate runs to ensure registration follows the plan. Adding definitions to
MMDLib's registries alone is not evidence that the Minecraft objects were created.

See [data-defined content modules](data-modules.md) for the bundled baseline, pack schemas, overrides, and current runtime integration limits.

See [block entities and machines](block-entities.md) for data-defined machine companions, typed factories, deferred registration, and tick hooks.

See [load-time method events](method-events.md) for `events` extension arrays, public static method resolution, and player/weapon/tool handler signatures.

See [startup definition loading](definition-loading.md) for folder dispatch, IDs, loading stages, and access to loaded material plans.

See [the maintained vanilla extensions](vanilla-extensions.md) for a complete, playable use of the same data and runtime APIs.
