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

CrackhammerItem retains top-face right-click crushing of dropped item entities.
Normal use processes one input; shift-use processes as many as remaining durability
allows. Block items additionally require an adequate tool tier. Outputs split into
legal stacks, input is consumed once, and tool damage is charged per operation.
Empty recipe outputs are skipped before consuming inputs. Mining speed uses
mmdlib:crackhammer_crushable and checks the supplied tool tier.

The library registers mmdlib:crushing recipe type/serializer. Recipes use ingredient
and result fields with modern ItemStack components, plus the supplied legacy
item/count result form. No crushing recipes are generated automatically. Example:

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

These classes are runtime building blocks, not automatic material discovery or a
full registry application pipeline. The new dispatcher rejects arrow/bolt items
until their entity-aware implementations are installed, rather than producing
basic items that cannot shoot. Projectile entity factories, client models and
renderers, fluid-family factories, and behavior callback dispatch from the new
runtime subclasses remain separate work.

verifyRuntimeBehavior checks dispatch classes without instantiating game objects,
material stat formulas, explicit tier tags, lazy repair resolution, and bundled tag
resources. The whole NeoForge project compiles and existing definition/codec/API
checks run alongside it. Scythe harvesting/protection, crackhammer processing and
breakage, armor equipment effects, anvil menus, and block placement still require
in-game verification.
