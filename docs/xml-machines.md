# XML machines

An installed mod can declare an item-processing machine with optional energy and fluid storage in
`src/main/resources/mmdlib/machine/<path>.xml`. Its ID is `<modid>:<path>`.
MMDLib registers the block, item, block-entity type, declared recipe type and
serializer, capabilities, screen and JEI catalyst. All instances use the shared
`DefinedMachineBlock` and `DefinedMachineEntity`; no machine-specific Java class
is needed.

Definitions load at startup on both sides. Changing a machine's storage, screen,
appearance or recipe generator configuration requires a restart. Recipe JSON and
tag changes take effect on server `/reload`, including regeneration and client
synchronization. Both sides must have the same mod and startup definitions.
Ordinary world datapacks cannot register new machine blocks.

Menu opening checks a CRC32 of the resolved machine XML together with the screen
fingerprint. Inherited machine settings are included, so changing the base's energy
configuration or a slot's role/limit is detected even if the screen looks identical.
XML attribute order, comments, whitespace and local source paths do not affect the CRC.
Different definitions are rejected before constructing the client menu.

## SimpleCrusher

The playable example is
[`simple_crusher.xml`](../src/companion/resources/mmdlib/machine/simple_crusher.xml):

```xml
<machine xmlns="https://moddev.zone/mmdlib/machine" version="1"
         extends="mmdlib:base_powered_machine" name="Simple Crusher">
  <appearance preset="cube-front" front="minecraft:block/furnace_front"
              side="minecraft:block/iron_block" top="minecraft:block/smooth_stone"/>
  <storage>
    <items id="items">
      <slots id="input" count="1" role="input"/>
      <slots id="output" count="2" role="output"/>
    </items>
  </storage>
  <recipe-type id="mmdlib_companion:simple_crushing" declare="true" name="Simple Crushing">
    <generator class="zone.moddev.mc.lib.machine.recipe.generation.OreToIngotGenerator"
               hardness-level-ratio=".10"/>
  </recipe-type>
  <processor id="crusher" recipe-type="mmdlib_companion:simple_crushing"
             filter-inputs="input"/>
</machine>
```

It accepts ores with matching `c:ores/<metal>` and `c:ingots/<metal>` tags and
produces two ingots. Its `.10` hardness ratio scales the defaults of 100 processing
ticks and 2,000 FE total: regular iron ore costs 130 ticks and 2,600 FE.
Omitting that option uses the unscaled defaults. Recipe admission
filters reject unrelated input items. Output slots refuse insertion by players
and automation, and the processor reserves inputs and output space using the
normal machine recipe lifecycle. One initial tick selects/reserves the recipe
before processing begins.

The example reuses vanilla textures; your own textures can use names such as
`example:block/crusher_front`, backed by
`assets/example/textures/block/crusher_front.png`. The cube preset generates
blockstates, block/item models, English labels, mining tags and a self-drop loot
table in a low-priority resource pack. Authored resources can override these
defaults. Crafting recipes remain ordinary files under `data/<modid>/recipe/`.
The companion's SimpleCrusher is in the **Data-defined Machines** creative tab.

## Base machine and storage

The generic [`base_machine.xml`](../src/main/resources/mmdlib/machine/base_machine.xml)
provides block settings, redstone control, recovery and the common screen with generated
tank fills. It declares no storage. The powered base extends it:

```xml
<machine xmlns="https://moddev.zone/mmdlib/machine" version="1" abstract="true"
         extends="mmdlib:base_machine">
  <storage>
    <energy id="energy" capacity="100000" input-rate="1000" output-rate="0"/>
  </storage>
  <groups>
    <group id="energy" storage="energy" mode="input" color="#d85b5b"/>
  </groups>
  <screen xmlns="https://moddev.zone/mmdlib/screen" extends="mmdlib:base_powered_machine"/>
</machine>
```

The Java runtime follows the same split: `BaseMachine` owns the common lifecycle;
`BasePoweredMachine` adds an FE buffer for existing Java machines. XML machines use
`DefinedMachineEntity`, allocating an FE buffer only when `<energy>` is declared.

Machine and screen definitions have separate namespaces of IDs, so these two
`mmdlib:base_powered_machine` resources serve different purposes. The machine
inherits common configuration; its screen inherits the reusable visual template.
Abstract machines register nothing. Single-valued sections (`items`, `fluids`, `energy`,
`recipe-type`, `processor`, and the other machine sections) merge by element name,
so a child may change their IDs. Repeated slots/groups merge by element name and
`id`; fills merge by `name`. A child can override just the energy capacity while
retaining the base rates. Generator and fill elements replace their inherited
counterparts completely. Unnamed screen siblings remain in order: inherited
content first, followed by child content. Duplicated single-valued sections in
one file are errors, even if they have different IDs.

Numbers use locale-independent syntax with a dot decimal separator, such as
`hardness="3.5"`. Invalid hardness values and resource locations report the source
file, line, attribute and value, including when authored in an inherited base.

Each item resource group has its own physical `ItemStorage`. Generated screen fills use an
`InventoryElement` named after that group, with local `ItemSlotElement` indexes starting at zero.
The machine retains a combined capability view for external automation and legacy callers;
recipe groups operate on the independent handlers.

`<slots id="input" count="2" role="input"/>` creates stable member IDs `input/0`
and `input/1`, plus a recipe/automation group named `input`. Growing the count
retains those identities. `<slot id="catalyst" role="input"/>` creates one member
with exactly that ID; add it to an explicit group:

```xml
<groups>
  <group id="catalysts" storage="items" members="catalyst" mode="input" color="#568bd8"/>
</groups>
```

Both forms can coexist. Each item member belongs to exactly one group. Explicit
groups can configure the mode/color of an automatic group without changing its
membership. Roles are `input`, `output`, `storage`, and `manual`; automatic group
modes are respectively `input`, `output`, `both`, and `disabled`. A group cannot
grant automation permissions that its members' roles prohibit. Slot `limit`
defaults to 64. There must be 1–54 item slots overall.

The runtime supports one item storage, an optional fluid storage, an optional energy
buffer named `energy`, one recipe type and one processor per machine. Energy automation
uses the `energy/input` group. Item storage IDs `energy`, `features`, `automation`,
`redstone_control`, `redstone_mode`, `id`, `components`, `mmdlib_features`, and
`mmdlib_machine` are reserved. Input-mode item and fluid groups are automatically linked to
the processor's recipes; an explicit `filter-inputs` list must name those groups.
Processor `name` optionally overrides the displayed machine name.

A processor can also have sounds, given as sound event IDs:

* `running-sound` loops at the machine for every player nearby while a cycle runs,
  and stops when the cycle finishes, stalls or pauses, or the machine is removed.
* `finish-sound` plays once at the machine each time a cycle completes.

```xml
<processor id="crusher" recipe-type="example:crushing"
           running-sound="example:machine/crusher_run"
           finish-sound="example:machine/crusher_done"/>
```

Any sound in a `sounds.json` works; to declare your own sound events, see
[sound events](definition-loading.md#sound-events). A looping sound's `.ogg` should
loop cleanly.

A processor can also run handlers, named by callback ID:

* `on-start` runs when the processor starts a new cycle: a `machine-cycle-start`
  handler. Resuming a paused or saved cycle does not count.
* `on-finish` runs when a cycle has finished and placed its outputs: a
  `machine-cycle-finish` handler.

```xml
<processor id="crusher" recipe-type="example:crushing"
           on-start="example:crusher_started" on-finish="example:crusher_done"/>
```

Both run on the server and receive a `MaterialEventContexts.MachineCycle`: the
machine's block entity, its level and position, the machine definition's ID, and
the cycle's recipe ID and length in ticks. Register a handler in code with
`MaterialExtensions.GLOBAL.events().registerCallback(id,
MaterialEventTypes.MACHINE_CYCLE_FINISH, cycle -> ...)`, in an extension file
(`"type": "machine-cycle-finish"`, a `public static void method(MachineCycle)`), or
from a [CraftTweaker script](crafttweaker.md#machine-handlers). Machines load after
extension files, code and scripts have registered their handlers; a machine naming
an ID nobody registered, or a handler of the other event, fails loading with the
machine's ID.

Stable storage IDs, processing reservations and side settings survive saves.
Displaced resources use `mmdlib:machine_recovery_capsule`; use a capsule on an XML
machine to restore compatible items, fluids and energy. Overflow and unknown resource
entries remain in the capsule.

## Simple Lava Furnace

[`simple_lava_furnace.xml`](../src/companion/resources/mmdlib/machine/simple_lava_furnace.xml)
extends `mmdlib:base_machine`, with one input slot, one output slot and this storage:

```xml
<fluids id="fluids">
  <tank id="fuel" capacity="8000" role="input" fluid="minecraft:lava"/>
</fluids>
```

There is no base fluid machine. The furnace owns its tank definition and has no FE
buffer or FE capability. Right-click it with a lava bucket or fill it through its
fluid capability. The generated gauge and side controls use the `fuel` group.
Wrong fluids are rejected; a failed bucket interaction does not spill fluid into the world.

Generated gauges also support cursor containers: left-click pours into the selected tank;
right-click fills the container from that tank. Players can drain input tanks, while reserved
fluid and output space remain protected. The shared gauge behavior also applies to handwritten
screens using `<fluid-tank tank="0" length="3s"/>`. Both create `FluidTankElement`, which owns
its snapshots, tooltips and transfers; machine classes only expose storage through `screenFluids()`.

Generated tank tooltips show the resource group name, current amount/fluid (or
`Empty`), and accepted fluid names. Carry a filled fluid container on the cursor
to see a blue `Accepts Lava in Bucket` or red `Doesn't accept Water in Tank` line.
Modded containers use their display name; empty containers add no compatibility
line. The server evaluates each viewer's carried stack against the tank's role,
recipe filter and current fluid, using simulation only. Acceptance describes
fluid compatibility, not free capacity. These lines update when recipes reload.

Both `<tank>` and `<tanks count="2">` create automatic groups. The latter expands
stable tank IDs to `fuel/0`, `fuel/1`, etc. Capacity is per tank, in mB, and must be
positive. Declare 1–16 tanks per fluid storage. The optional `fluid` attribute names
one registered fluid; omit it to allow any fluid accepted by the recipe filter.
Roles are `input`, `output`, `storage` or `manual`, as for slots. Explicit fluid
`<group>` entries may override an automatic group's mode/color, but cannot regroup
its members. Item, fluid and energy group IDs must be distinct. Storage IDs must be
distinct and cannot use the lifecycle's reserved keys. An explicit `filter-inputs`
list includes fluid input groups: the furnace uses `filter-inputs="input fuel"`.
Fluid products, reservations, stable IDs, save/load, recovery and side automation
use the same machinery as Java machines. Paid automation requires an FE buffer;
free automation also works on machines without one.

The generic base includes `<fill name="tanks" generator="mmdlib:tanks"/>`.
An explicit `<fill name="tanks">…</fill>` replaces it (including an empty fill).
Generated gauges bind `#getTankFluid(0)` and `#getTankCapacity(0)`, then index 1,
and so on. Indexed references call a public instance getter with one fixed
nonnegative `int` argument on the server; its result is synchronized like an
ordinary getter. Invalid tank indexes fail startup screen validation.

### Smelting and energy-to-fluid recipe adapters

The furnace selects its mod-owned generator:

```xml
<generator class="zone.moddev.mc.companion.recipe.LavaSmeltingRecipes"/>
```

MMDLib's `SmeltingRecipeGenerator` adapts all existing `SmeltingRecipe` entries,
including recipes added by mods/datapacks. It preserves the input ingredient,
output stack (including count/components), and cooking duration. This includes
non-ore smelting, such as sand to glass. Furnace experience rewards are not copied.
The optional `input-group`, `output-group`, and `energy-group` default to `input`,
`output`, and `energy`; `energy-per-tick` defaults to 20.

The companion's [`LavaSmeltingRecipes`](../src/companion/java/zone/moddev/mc/companion/recipe/LavaSmeltingRecipes.java)
subclasses this generator and wraps its output. It replaces FE with lava before
emitting recipes for validation and publication. Optional `fluid-group` defaults
to `fuel` and `energy-per-mb` defaults to 40. Thus a 200-tick vanilla recipe costs
100 mB lava and yields the original one ingot. Generated IDs have the form
`<machine-type-namespace>:generated/<machine-type-path>/smelting/<source-namespace>/<source-path>`.
Authored recipes can override these IDs; `/reload` rebuilds them from current recipes.

The same pattern works with `OreToIngotGenerator` directly:

```java
public final class LavaOres extends OreToIngotGenerator {
  @Override
  public void generate(RecipeGenerationContext ctx, MachineRecipeOutput output) {
    long rate = ctx.properties().longValue("energy-per-mb", 40, 1, Long.MAX_VALUE);
    var lava = ctx.registries().lookupOrThrow(Registries.FLUID)
        .getOrThrow(Fluids.LAVA.builtInRegistryHolder().key());
    super.generate(ctx.withProperties(ctx.properties().without("energy-per-mb")),
        (id, recipe) -> output.accept(id, RecipeTransforms.energyToFluid(
            recipe, "fuel", HolderSet.direct(lava), rate)));
  }
}
```

Use holders from the context's registry lookup: reload candidates may have a
different holder owner from the globally registered fluid.
`GeneratorProperties.without(...)` removes only the subclass's custom options so
its parent still rejects misspelled options. `RecipeTransforms.energyToFluid`
sums all energy inputs with overflow checks, removes them, and adds one fluid
input using `ceil(total FE / FE per mB)`. It preserves other inputs, outputs and
duration. Recipes with no FE inputs remain unchanged. This ore subclass retains
the ore generator's default 1 → 2 yield and hardness/tool scaling; the furnace's
smelting adapter preserves the existing smelting yield instead.

## Generated screen fills

The generic base screen supplies player inventory, a processor button,
side/redstone controls, Info and Statistics. The powered screen adds the power gauge. Its machine-specific region is:

```xml
<placeholder name="machine" generator="mmdlib:processing"/>
```

`mmdlib:processing` places input groups on the left, a progress arrow in the
middle, output groups on the right, and storage/manual groups below. Groups and
their members retain definition order within each category. Slot IDs and group
highlights are derived automatically. The processing cluster is centered in the
area before the base template's energy gauge. Generated processor arrows open
their recipe type in JEI when clicked. SimpleCrusher needs no `<screen>` or
`<fill>` of its own.

A machine can replace that region:

```xml
<screen xmlns="https://moddev.zone/mmdlib/screen">
  <fill name="machine">
    <hbox spacing="6">
      <slot id="ore" index="0"/>
      <progress value="#getProgress" max="#getTime"/>
      <slot id="ingot_a" index="1"/>
      <slot id="ingot_b" index="2"/>
    </hbox>
  </fill>
</screen>
```

This screen inherits `extends` from the machine base. To replace the generator
instead, use `<fill name="machine" generator="example:custom"/>`. An explicit
fill always replaces the template's default, including an empty fill. The nearest
fill in the inheritance chain wins. Extra attributes are allowed only when a
generator is present, and that generator validates its own options. Literal fills
and placeholders reject unknown attributes even when later overridden.
A generator cannot have literal children,
and required placeholders cannot also have defaults.

Custom screen templates can use these fragment generators on their placeholders:

| Generator | Options | Content |
| --- | --- | --- |
| `mmdlib:processing` | None | Complete processor region, excluding inherited frame/controls |
| `mmdlib:resource-groups` | `role="input\|output\|storage"`, optional `columns="1..9"` (default 3) | Slots for matching groups; storage includes manual groups |
| `mmdlib:processors` | None | The single processor's progress arrow |
| `mmdlib:tanks` | None | One indexed fluid gauge per tank, in declaration order |

Register custom namespaced builders with `MachineScreenGenerators.register`
before definitions load. A builder receives the resolved `MachineDefinition`
and the chosen placeholder/fill `ScreenReader.Node`, including custom attributes.
It returns ordinary screen nodes. The normal schema, layout and getter checks
run after expansion, followed by validation of slot indices and processor keys
against the machine. Literal fills use the existing screen XML syntax; a
`<slots ref="..."/>` screen shorthand is not part of this implementation.

## Recipe generators and authored recipes

`<generator class="..."/>` names a public, concrete class implementing
`MachineRecipeGenerator`, with a public no-argument constructor. All other
attributes become immutable custom properties. The generator validates and
interprets its own options; there is no global list of recipe generator options.

```java
public void generate(RecipeGenerationContext context, MachineRecipeOutput output) {
  var p = context.properties();
  p.requireOnly("input-group", "output-group", "energy-group", "time", "energy");
  output.accept(
      ResourceLocation.fromNamespaceAndPath(context.recipeType().id().getNamespace(),
          "generated/my_recipe"),
      MachineRecipeBuilder.recipe(context.recipeType())
          .inputItems(p.requiredString("input-group"), Ingredient.of(Items.COBBLESTONE), 1)
          .inputEnergy(p.requiredString("energy-group"), p.requiredLong("energy", 1, Long.MAX_VALUE))
          .outputItem(p.requiredString("output-group"), new ItemStack(Items.GRAVEL))
          .time(p.requiredInt("time", 1, Integer.MAX_VALUE))
          .build());
}
```

The context supplies the declared `recipeType()`, registry lookup `registries()`,
staged item/block `tags()` for the current reload, authored `recipes()`, and `properties()`.
Use `tags().ingredient(tagId)` to build an ingredient from that reload's item tag
members rather than consulting previously bound tags. It has no live block entity
or inventory. The resulting recipe names logical input, energy and output groups;
the shared processor handles selection, reservations, progress and completion.

Generation runs once per declared recipe type on initial server load and every
reload, after vanilla recipes and staged tags are ready. A fresh generator
instance builds a fresh recipe set. Emitted IDs must use the recipe type's
namespace. Outputs are codec-validated and copied, and group resource kinds are
checked against every XML machine using the type. Duplicate generated IDs fail
the reload. An authored recipe with the same ID replaces the generated one.
Generator failure prevents publishing the candidate recipe manager; a successful
reload synchronizes ordinary machine recipes to clients.

`OreToIngotGenerator` matches `c:ores/<metal>` with `c:ingots/<metal>`, including
metals from other installed mods. Empty/unmatched tags are skipped. It consumes
one ore and produces two ingots; these are generator defaults, independent of
inventory size. There are no `input-count` or `output-count` properties.

All properties are optional; the declaration needs only `class`. Code defaults are:

| Property | Default | Meaning |
| --- | --- | --- |
| `input-group` | `input` | Item group consumed by the recipe |
| `output-group` | `output` | Item group receiving the ingots |
| `energy-group` | `energy` | Energy group supplying the recipe cost |
| `time` | `100` | Base processing ticks, positive integer |
| `energy` | `2000` | Base total FE cost, positive integer |
| `preferred-mods` | `minecraft;mmdlib` | Ordered, semicolon-separated output namespaces |
| `tool-level-ratio` | `0.0` | Additional cost multiplier per required tool level |
| `hardness-level-ratio` | `0.0` | Additional cost multiplier per block hardness unit |

For multiple ingot choices, the first available preferred namespace wins, then
registry ID order breaks ties. Unlisted namespaces follow all preferred ones.
For example, `preferred-mods="my_metals;minecraft;mmdlib"` prefers your mod's ingot.
Empty entries, duplicate namespaces, invalid numbers and overflowing costs fail
with the definition's source location.

Both time and energy use the same multiplier, rounding each final amount up:

```text
multiplier = 1 + toolLevel * tool-level-ratio + hardness * hardness-level-ratio
ticks = ceil(time * multiplier)
total FE = ceil(energy * multiplier)
```

`.10` means an additional 10% of the base cost per level/unit, without compounding.
Set these options on `<generator>`, not `<processor>`:

```xml
<generator class="zone.moddev.mc.lib.machine.recipe.generation.OreToIngotGenerator"
           hardness-level-ratio=".10"/>
```

For example, tool level 2 with `tool-level-ratio=".10"` and no hardness scaling
costs 120 ticks and 2,400 FE using the defaults. Both ratios at `0.0` leave costs unchanged.
Calculations use `double`. Values within four floating-point steps of an integer
are snapped to that integer before rounding up, so `100 * 1.1` costs 110 rather
than 111 ticks. Non-finite or out-of-range results are rejected; unchanged costs
retain their exact integer value.

Ratios must be finite and nonnegative. Tool levels use the current reload's block
tags: wood/no tier requirement = 0, stone = 1, iron = 2, diamond = 3, netherite = 4,
and blocks rejecting netherite = 5. The generator reads `needs_*_tool` and
`incorrect_for_*_tool` tags, rather than stale globally bound tags. These are vanilla
tier bands; custom tool tiers need to express their requirements in those tags.
Hardness is the block's default destruction time, without a world position or
state-specific overrides. Non-block items use level/hardness zero; when scaling
is enabled, unbreakable blocks are skipped.

With both ratios zero, recipe IDs are
`<type namespace>:generated/<type path>/ores/<metal>`; for example,
`mmdlib_companion:generated/simple_crushing/ores/iron`. Removing a tag on reload
removes its generated recipe. Enabling either ratio creates one recipe per ore
item, appending `/<ore namespace>/<ore path>` to the ID. Regular and deepslate ores
can then have different costs, and an authored override can target either variant.

You may omit `<generator>` and use only JSON, or combine both. An authored recipe
uses the declared type's normal codec:

```json
{
  "type": "mmdlib_companion:simple_crushing",
  "inputs": {
    "input": [{"items": {"item": "minecraft:cobblestone"}}],
    "energy": [{"energy": 2000}]
  },
  "outputs": {"output": [{"item": {"id": "minecraft:gravel", "count": 1}}]},
  "time": 100
}
```

### Several recipes in one JSON file

Use `mmdlib:machine_recipes` with a named `recipes` object in the usual recipe
directory. Each entry contains a complete machine recipe, including its `type`:

```json
{
  "type": "mmdlib:machine_recipes",
  "recipes": {
    "cobblestone": {
      "type": "mmdlib_companion:simple_crushing",
      "inputs": {
        "input": [{"items": {"item": "minecraft:cobblestone"}}],
        "energy": [{"energy": 2000}]
      },
      "outputs": {"output": [{"item": {"id": "minecraft:gravel"}}]},
      "time": 100
    },
    "gravel": {
      "type": "mmdlib_companion:simple_crushing",
      "inputs": {
        "input": [{"items": {"item": "minecraft:gravel"}}],
        "energy": [{"energy": 2000}]
      },
      "outputs": {"output": [{"item": {"id": "minecraft:sand"}}]},
      "time": 100
    }
  }
}
```

The companion ships this as `data/mmdlib_companion/recipe/simple_crushing/stone.json`.
Its recipe IDs are `mmdlib_companion:simple_crushing/stone/cobblestone` and
`mmdlib_companion:simple_crushing/stone/gravel`. Entry names are resource paths
(lowercase letters, digits, `_`, `-`, `.`, `/`), not array positions; reordering
entries preserves IDs. Renaming an entry changes its ID.

Entries may use different machine recipe types, including types registered in Java.
Nested bundles and vanilla crafting/smelting recipes are not supported inside a bundle.
NeoForge `neoforge:conditions` work on both the whole file and individual entries.
Only the resulting individual recipes are published, synchronized and shown in JEI.

Datapack priority replaces the entire file. An empty `recipes` object removes its
entries. Two authored definitions producing the same final ID are an error; this
includes a bundle entry colliding with a standalone file. Authored entries continue
to override generated recipes with the same ID. Bundles are expanded before recipe
generators run, so generators see the individual authored recipes.

Both standalone and bundled recipes are checked against every XML machine using
their type. Unknown groups and wrong resource kinds fail the candidate reload with
the recipe ID and machine ID. This validation also runs for types without a recipe
generator. A rejected reload leaves the previously active recipes intact.

Multiple machines can use a type declared by one XML definition. Repeated
declarations must agree on name, generator class and properties. A definition
referencing it with `declare="false"` must not supply a generator. Types registered
only by Java are not currently resolved by this XML loader.

This delivery covers item processing with optional FE and fluids, and cube appearances.
Multiple processors, world-interaction features and standalone loose content packs
remain outside this format's implemented scope.
