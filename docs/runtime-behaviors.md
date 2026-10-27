# Runtime behavior support adapted from the root content references

The root `content/` files are preserved as references. Adapted sources live under
`zone.moddev.mc.lib.common.runtime`; the crushing recipe is under common.recipes.
The code has no dependency on BaseMetals classes, catalogs, config, or registrations.

## Tools and armor

MaterialBacked exposes the owning immutable MMDMaterial from runtime items.
MaterialToolTier implements the 1.21 Tier API with an explicit incorrect-blocks
TagKey and a lazy repair Ingredient supplier. No numeric harvest-level mapping is
introduced. Define/populate the supplied tags in the consuming mod's datapack.

MaterialStats retains the supplied BMe formulas: tool uses = 32 * strength,
crackhammer uses = 75% of tool uses, scythe tool speed = hardness, crackhammer
speed = max(1, half tool speed), enchantability = floor(2.5 * magic), armor
durability factor = 2 * strength, and the reference per-slot armor distribution.
The material's damage property supplies the tier's base damage. Special-property
overrides use keys such as tool_durability, tool_efficiency, base_attack_damage,
crackhammer_durability, crackhammer_destroy_speed, armor_durability_factor,
armor_head/chest/legs/feet, horse_armor_protection, armor_toughness, and enchantability.

StandardItems dispatches to actual Pickaxe/Axe/Shovel/Hoe/Sword classes, material
armor and horse armor, shears, shield, fishing rod, bow, and crossbow classes.
Each implements MaterialBacked. Constructors accept actual Item.Properties from
the runtime factory; tool constructors apply their normal attack attributes.
StandardItems.Crossbow uses Minecraft's CrossbowItem. The BMe reference's legacy
bow-based crossbow behavior is not silently copied into the general library.
Bow/crossbow ammunition selection is still the vanilla baseline in these wrappers.

MaterialArmor.register queues a named ArmorMaterial in the consuming mod's
DeferredRegister, accepting explicit repair and texture-layer suppliers/IDs.
Humanoid armor uses the corresponding ArmorItem.Type and its per-slot durability.
The register must use the material's namespace. Pass its holder to
StandardItems.create(part, tier, armorHolder, itemProperties); non-armor forms may
pass null for the armor holder. Repair/color/lore/event application beyond what
these subclasses provide remains the runtime factory's responsibility.

## Scythe and crackhammer

ScytheItem retains the reference server-side 3x3 horizontal harvest of blocks in
mmdlib:scythe_harvestable. Each neighbor goes through ServerPlayer.gameMode's
normal destruction path for protection events, drops, and tool wear. A per-thread
guard prevents recursive area harvesting and is removed in finally.

The crackhammer has no dedicated Java class; it wires through the same external
hooks any other mod uses. Its module selects the generic `mmdlib:tool` factory and
configures it entirely through `factory_data`, and its right-click comes only from
its definition's `tool-right-click` binding:

```json
{
  "factory": "mmdlib:tool",
  "factory_data": {
    "abilities": ["pickaxe"],
    "mineable": "minecraft:mineable/pickaxe",
    "fast_on": "mmdlib:crackhammer_crushable",
    "attack_damage": 5.0, "attack_speed": -3.5,
    "durability": {"stat": "crackhammer_durability", "tool_factor": 0.75, "minimum": 1},
    "speed": {"stat": "crackhammer_destroy_speed", "tool_factor": 0.5, "minimum": 1}
  },
  "template": {"events": {"tool-right-click": "mmdlib:crackhammer_crush", "tool-harvest": "mmdlib:crackhammer_harvest"}}
}
```

`mmdlib:tool` settings: `mineable` blocks are correct for drops; `fast_on` blocks
(default: `mineable`) also mine at the scaled `speed`; the tier's incorrect tag
never drops, as with vanilla tools. `durability` and `speed` scale the tier's tool
durability/efficiency by `tool_factor`, not below `minimum`; `stat` names a material
special-property override. `abilities` takes `pickaxe`/`axe`/`shovel`/`hoe`/`sword`/`shears`
default sets or individual NeoForge ItemAbility names. Settings decode while queuing.

Blocks in the tier's incorrect tag (the material's `incorrect_blocks`) neither drop
nor mine faster than bare hands, so a wooden-tier crackhammer cannot mine iron ore.

While mining, the `tool-harvest` binding `mmdlib:crackhammer_harvest` replaces every
drop that has a crushing recipe with its crushed output (Fortune multiplies first).
Each crushed item costs 1 durability on top of the block break, so an iron ore whose
raw iron is crushed costs 2; when durability runs short, the rest drop uncrushed.
Crushed outputs are not crushed again. Right-click crushing also costs 1 per item.
Ores are covered through their drops (e.g. raw metal or gems), not separate ore recipes.

Both handlers are declared in `mmdlib/extension/crackhammer.json` and implemented by
`CrackhammerEvents`. Right-click (`crush`) runs on the server only and processes
matching `mmdlib:crushing` recipes when right-clicking
a dropped item in reach. Its item-targeting ray stops at terrain; a
top-face block click also processes drops directly above that block.
Normal use processes the server config's `crackhammer.crush_count` inputs (default 8);
shift-use processes as many as remaining durability allows. Block items additionally require an adequate tool tier. Outputs split into
legal stacks, input is consumed once, and tool damage is charged per operation.
Empty recipe outputs are skipped before consuming inputs. Mining speed comes from the tool settings above.

The library registers mmdlib:crushing recipe type/serializer. Recipes use ingredient
and result fields with modern ItemStack components, plus the supplied legacy
item/count result form. The vanilla-example generator emits crushing recipes for its dust forms. MMDLib also ships
the fixed stone chain in `data/mmdlib/recipe`: smooth stone → stone → cobblestone → gravel → sand. Other material sets supply their own recipes. Example:

```json
{
  "type": "mmdlib:crushing",
  "ingredient": {"item": "minecraft:stone"},
  "result": {"id": "minecraft:cobblestone", "count": 1}
}
```

The harvest/crushable tags are adapted from BMe. Optional common-tag references
avoid requiring another mod's tag definitions. The consuming mod can extend them.

## Blocks

StandardBlocks.create accepts fresh BlockBehaviour.Properties, BlockSetType,
WoodType, and a lazy stair-base BlockState supplier. Its dispatcher uses actual
StairBlock, SlabBlock, LadderBlock, LeverBlock, and other vanilla subclasses.
These provide their native states, placement, waterlogging, redstone, shapes,
and interaction logic rather than relying on a generic Block substitute.

PlateBlock preserves BMe's six shapes, center/edge hit placement, rotations, and
mirroring. MaterialAnvilBlock preserves the three damage states and custom menu
validity/damage handling. CompatibilityDoubleSlabBlock starts as a dry double
slab. Bookshelf overrides NeoForge enchant power. Flowers/potted variants require
their own registrations; this creates only the empty flower-pot block. Ores use
DropExperienceBlock with zero default XP. The caller chooses door/button/pressure
plate set type and fence-gate wood type and supplies the stair base explicitly.

The native blocks still need the appropriate tags, blockstate models, loot tables,
recipes, textures, and registration wiring from the consuming content factory.
This pass does not turn tag metadata into generated resources for each material.

## Scope and verification

`RuntimeContentRegistrar` applies the built-in item/block helpers, material-aware
arrow/bolt classes, and fluid families to actual registries. Client rendering and
fluid extensions are installed by the client-only runtime subscriber. MMDLib's
[vanilla example](vanilla-extensions.md) exercises this route; custom machinery,
world entities, and effect callback dispatch still need their specific APIs.
`StandardItems` by itself deliberately rejects ammunition instead of substituting
an item that cannot shoot.

verifyRuntimeBehavior checks dispatch classes without instantiating game objects,
material stat formulas, explicit tier tags, lazy repair resolution, and bundled tag
resources. The whole NeoForge project compiles and existing definition/codec/API
checks run alongside it. Scythe harvesting/protection, crackhammer processing and
breakage, armor equipment effects, anvil menus, and block placement still require
in-game verification.

See [block entities and machines](block-entities.md) for data-defined machine companions, typed factories, deferred registration, and tick hooks.
