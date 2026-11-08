# XML machines

An installed mod can declare a powered item-processing machine in
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

The library's separate machine definition
[`base_powered_machine.xml`](../src/main/resources/mmdlib/machine/base_powered_machine.xml)
provides:

```xml
<machine xmlns="https://moddev.zone/mmdlib/machine" version="1" abstract="true">
  <block hardness="3.5" facing="horizontal" drop="self"
         mineable="minecraft:mineable/pickaxe" requires-tool="true"/>
  <storage>
    <energy id="energy" capacity="100000" input-rate="1000" output-rate="0"/>
  </storage>
  <groups>
    <group id="energy" storage="energy" mode="input" color="#d85b5b"/>
  </groups>
  <redstone enabled="true" pause="processing-only"/>
  <recovery policy="capsule"/>
  <screen xmlns="https://moddev.zone/mmdlib/screen" extends="mmdlib:base_powered_machine"/>
</machine>
```

Machine and screen definitions have separate namespaces of IDs, so these two
`mmdlib:base_powered_machine` resources serve different purposes. The machine
inherits common configuration; its screen inherits the reusable visual template.
Abstract machines register nothing. Single-valued sections (`items`, `energy`,
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

The initial runtime supports one item storage, the inherited energy buffer
named `energy`, one recipe type and one processor per machine. Energy automation
uses the `energy/input` group. Item storage IDs `energy`, `features`, `automation`,
`redstone_control`, `redstone_mode`, `id`, `components`, `mmdlib_features`, and
`mmdlib_machine` are reserved. Input-mode item groups are automatically linked to
the processor's recipes; an explicit `filter-inputs` list must name those groups.
Processor `name` optionally overrides the displayed machine name.

Stable storage IDs, processing reservations and side settings survive saves.
Displaced resources use `mmdlib:machine_recovery_capsule`; use a capsule on an XML
machine to restore compatible items and energy. Overflow and unknown resource
entries remain in the capsule.

## Generated screen fills

The base screen supplies the power gauge, player inventory, processor button,
side/redstone controls, Info and Statistics. Its machine-specific region is:

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

This delivery covers powered item processing and cube appearances. XML fluid
storage, multiple processors, world-interaction features and standalone loose
content packs remain outside this format's implemented scope.
