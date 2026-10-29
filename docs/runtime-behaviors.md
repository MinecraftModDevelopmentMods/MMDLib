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
Each crushed item costs 1 durability on top of the block break; when durability runs
short, the rest drop uncrushed. Crushed outputs are not crushed again. Right-click
crushing also costs 1 per item.

Blocks in `#c:ores` are the exception: the ore block itself is crushed, replacing all
of its drops. Fortune does not apply, and the crush costs 1 durability.

Gems are never crushed while mining, so the crackhammer stays a processing tool rather
than the best way to mine gems. A gem is any item with a tag whose path is `gems` or
starts with `gems/` (e.g. `#c:gems/diamond`), in any namespace. An ore that drops a gem
therefore drops normally, Fortune included. Gems are still crushed by right-click, and a
silk-touched gem ore block still crushes to 2 dust.

The shipped recipes cover:
- **Per material:** `#c:ores/<m>` and `#c:raw_materials/<m>` give 2 dust; a raw storage
  block gives 18. The storage block gives 9 dust, or `special.storage_block_dust`
  (4 for quartz); the repair item (ingot or gem) gives 1.
- **Fixed:** `#c:cobblestones` → gravel, `#c:gravels` → sand, uncoloured and red
  sandstone → sand and red sand, bricks and nether bricks → 4 brick items.

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

## Optional JEI integration

When JEI is installed, a "Crackhammer" category shows every `mmdlib:crushing` recipe:
input slot, a crackhammer filling from bottom to top, and the output slot, with a
note that matching drops are also crushed when mining. The texture is
`assets/mmdlib/textures/gui/jei/crackhammer.png` (a 256x256 sheet: the 168x74 panel,
the 16x16 tab icon at (169, 1), and the 32x32 fill hammer at (168, 18)).

Catalysts are found by binding, not class: any item whose definition (or material)
binds `tool-right-click` to `mmdlib:crackhammer_crush` or `tool-harvest` to
`mmdlib:crackhammer_harvest` is listed, so addon crackhammers appear automatically.
This covers definitions loaded by MMDLib's startup loader.

JEI stays optional. MMDLib compiles against the JEI API only (`compileOnly`, version
`jei_version` in `gradle.properties`), declares JEI as an optional client dependency
in `neoforge.mods.toml`, and only JEI loads the `@JeiPlugin` class
(`zone.moddev.mc.lib.client.jei`). Development runs include JEI; launch with
`-PwithoutJei` to run without it.

## Blocks

Every `mmdlib:*` block and item factory is registered by `StandardImplementations`
through the public `registerBlockImplementation` / `registerItemImplementation`, and
built from the same `BlockContext` / `ItemContext` an addon receives (see
[material extensions](material-extensions.md#factory-implementations)). The block
factories use actual StairBlock, SlabBlock, LadderBlock, LeverBlock, and other vanilla subclasses.
These provide their native states, placement, waterlogging, redstone, shapes,
and interaction logic rather than relying on a generic Block substitute.

PlateBlock preserves BMe's six shapes, center/edge hit placement, rotations, and
mirroring. MaterialAnvilBlock keeps the vanilla anvil's three wear stages in one block
per material, as a `damage` property (0-2). Its GUI opens only through the anvil module's
`block-right-click` binding to `mmdlib:open_menu`; the block itself passes the click.
Vanilla's anvil code only knows the three vanilla anvil blocks, so the block adapts the two
places where an anvil wears:

- **Using it.** Taking a result swaps in the matching vanilla anvil while vanilla's code
  runs (its 12% chance to wear), then puts the material anvil back one stage worse, or leaves
  it broken.
- **Falling.** Vanilla's fall-damage roll would destroy any non-vanilla anvil. Instead the
  anvil lands one stage worse (or drops as an item where it cannot be placed); a fully worn
  anvil breaks as vanilla's does.

Wear survives as an item: an anvil that drops after a fall carries its `damage` in the
`minecraft:block_state` component, and the generated loot table copies it with
`minecraft:copy_state` when the anvil is mined. A mod that ships an anvil needs a
blockstate with `damage=0..2` variants for each `facing`. CompatibilityDoubleSlabBlock starts as a dry double
slab. Bookshelf overrides NeoForge enchant power. Flowers/potted variants require
their own registrations; this creates only the empty flower-pot block. Ores use
DropExperienceBlock with zero default XP. The caller chooses door/button/pressure
plate set type and fence-gate wood type and supplies the stair base explicitly.

A pressure-plate module may narrow what presses it with `factory_data.detects`:
`everything`, `mobs`, `players`, or an entity-type tag (`#example:detectable`).
Spectators and entities that ignore block triggers never press it. Without `detects`
the plate follows its block set type, as vanilla does. A player-only plate (BaseMetals'
human detector) is therefore data, not a class:

```json
"factory": "mmdlib:pressure_plate",
"factory_data": { "block_set_type": "stone", "detects": "players" }
```

MMDLib ships this as the standalone block `mmdlib:human_detector` (brick-textured; BaseMetals'
recipe: two bricks over two gears of any material, `#c:bricks/normal` / `#c:gears`); see [standalone blocks](definition-loading.md#standalone-blocks).
An unknown `detects` value fails when the definition loads.

`mmdlib:weighted_pressure_plate` is vanilla's weighted plate: its signal grows with the
number of entities on it, up to `factory_data.max_weight` (1-1024; vanilla uses 15 for
the light gold plate and 150 for the heavy iron plate). The bundled module defaults to
150; no shipped material selects it yet.

### Redstone

A `mmdlib:block` with `factory_data.redstone` reacts to and/or produces redstone.
Only the block-state properties its settings use are added.

```json
"factory": "mmdlib:block",
"factory_data": {
  "redstone": { "input": true, "output": "state", "strong": false, "faces": ["up", "down"], "comparator": "input" }
}
```

| Key | Values | Effect |
|---|---|---|
| `input` | `true` / `false` | Store the received power (`input`, 0-15) and fire `redstone-changed` on each change, server side |
| `output` | `0`-`15` or `"state"` | Emit a constant, or the power stored in the `output` property, set by handlers with `RedstoneBlock.setOutput(level, pos, power)` |
| `strong` | `true` / `false` | Also power through the touching block, like a lever's attached face (default weak) |
| `faces` | list of `down`, `up`, `north`, `south`, `west`, `east` | Faces that emit (default all) |
| `comparator` | `0`-`15`, `"input"`, or `"output"` | Comparator reading; `"input"` needs `input`, `"output"` needs `output: "state"` |

Unknown keys and out-of-range values fail when the definition loads. A block that
both reads and emits can latch itself on through neighbouring dust; that is a wiring
choice for the author. Comparator readings from a block-entity getter wait for
block-entity runtime support.

The native blocks still need the appropriate tags, blockstate models, loot tables,
recipes, textures, and registration wiring from the consuming content factory.
This pass does not turn tag metadata into generated resources for each material.

## Scope and verification

`RuntimeContentRegistrar` applies the built-in item/block helpers, material-aware
arrow/bolt classes, and fluid families to actual registries. Client rendering and
fluid extensions are installed by the client-only runtime subscriber. MMDLib's
[vanilla example](vanilla-extensions.md) exercises this route; custom machinery,
and world entities still need their specific APIs. Event bindings on loaded item,
block, and material definitions are dispatched by the runtime event bridge.
`StandardItems` by itself deliberately rejects ammunition instead of substituting
an item that cannot shoot.

verifyRuntimeBehavior checks dispatch classes without instantiating game objects,
material stat formulas, explicit tier tags, lazy repair resolution, and bundled tag
resources. The whole NeoForge project compiles and existing definition/codec/API
checks run alongside it. Scythe harvesting/protection, crackhammer processing and
breakage, armor equipment effects, anvil menus (opened through the `block-right-click`
binding, including not placing the held item and sneak-use passthrough), projectile
effects, and block placement still require in-game verification.

See [block entities and machines](block-entities.md) for data-defined machine companions, typed factories, deferred registration, and tick hooks.
