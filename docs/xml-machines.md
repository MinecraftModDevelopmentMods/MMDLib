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

## Ghost inventory inputs

Declare a sample-only inventory alongside ordinary item storage:

```xml
<items id="items">
  <slots id="input" count="1" role="input" type="ghost"
         default-item="minecraft:cobblestone"/>
  <slots id="output" count="4" role="output"/>
</items>
```

`<slot id="input" count="1" role="input" type="ghost"/>` also works; `count`
is optional on the singular form. Ghost inventories require `role="input"`, have
one-item samples rather than stack limits, and are included in the 54-slot limit.
`default-item` is optional; without it the initial sample is empty. Ordinary slots
retain their default `type="item"` behavior.

Recipes use normal item ingredients; the storage type supplies the non-consuming
semantics:

```json
"inputs": {
  "input": [{"items": {"item": "minecraft:stone"}, "count": 1}],
  "water": [{"fluids": "minecraft:water", "amount": 100}],
  "energy": [{"energy": 640}]
}
```

The input resource-group generator emits ordinary `<ghost-slot>` elements under
`<inventory storage="input">`. Existing ghost clicks and JEI drops edit the sample.
When a machine has one ghost item ingredient and no real item inputs, JEI's **+**
button selects that recipe's sample through the same server-validated slot action.
Open the machine before viewing its recipes. Selection needs neither a sample in
the player's inventory nor the recipe's fluids or energy; it only changes the ghost.
An explicit screen can use `<inventory storage="input"><ghost-slot index="0"/></inventory>`.
No picker, recipe ID selection or special output rule is required. `filter-inputs`
continues to list physical admission-filter groups, excluding ghost inventories.

Ghost samples are persisted and synchronized separately from real items. They do
not allocate physical capability slots or automation groups, and never enter drops
or recovery capsules. Recipes may match them but cannot produce into them. Samples
are checked when starting a cycle and excluded from its reservations and consumption.
Changing or clearing a sample affects the next cycle, including when the old cycle
is paused and later resumes. Counts and tags use the usual item ingredient matcher.
As with other input recipes, overlapping matches follow normal recipe lookup rules.

## Explicit recipe selection

For machines that specifically need a saved recipe ID, the separate optional
selector below remains available. Ghost inventory inputs do not use it.

Processors normally choose a matching recipe automatically. Opt into a saved,
explicit selection with `selectable="true"`:

```xml
<processor id="stew" recipe-type="example:stew" selectable="true"
           default-recipe="example:stew/cobblestone"/>
```

Place `<recipe-selector processor-key="stew"/>` inside the machine's screen fill.
It occupies one 18 × 18 ghost item slot. Clicking opens a compact strip of recipe icons for
that processor's type, including unavailable recipes with requirements and costs.
Clicking with a carried item or dropping a JEI item selects recipes producing that
item without moving the item. Multiple matches open a filtered chooser; the exact
recipe ID is saved. Right-click clears the selection; Escape or clicking outside closes
the icon strip. Extra recipes use page arrows or scrolling. Costs, missing requirements,
and queued-work details are in the tooltip, with no permanent panel or caption.
The selector uses the existing element-action authorization and bounded synchronization transport.

`default-recipe` is optional and requires `selectable="true"`. Without a default,
the processor starts with no selection. Old saves use the configured default;
explicitly cleared selections stay cleared after reload. Existing automatic
processors remain automatic. Java processors opt in through
`processor.selectable(defaultRecipe)` before loading state and change selection
with `processor.selectRecipe(level, id)` (null clears it).

Changes affect the next cycle, including when a cycle is paused or power-starved.
The old cycle keeps its original output, fluid costs and paid energy progress.
The selector displays the chosen output and any previous recipe still finishing.
The processor does not fall back to another recipe when resources are missing.
Only IDs belonging to its recipe type are accepted. If `/reload` removes the
selection, no new cycle starts; a reserved cycle can finish from its saved plan.
Recipe catalogs are indexed once per recipe-manager snapshot. Only visible page
entries and selected/active details are matched for the open menu, normally once
per second or after a selection/status change; there is no per-tick catalog scan.

Costs remain ordinary recipe JSON: energy inputs are total FE per cycle, `time`
is processing ticks, and fluid inputs are required amounts. Returning the same
fluid amount to the same tank makes it reusable; omitting that return consumes it.
The selector summarizes required and consumed amounts separately for single-fluid
inputs with deterministic returns. Selection changes invalidate failed-search
backoff immediately.

## Appearance presets

`preset="cube-front"` generates an orientable cube from `front`, `side` and `top`
textures. The optional `bottom` texture defaults to `side`. It supports both `part`
and `face-texture` children, including emissive masks on switched textures.

`preset="model"` uses an authored block model as the always-visible base and supports
only `part` children:

```xml
<appearance preset="model" model="example:block/mixer_casing">
  <part id="running_attachment" model="example:block/mixer_running_attachment"
        when="$processors.mixer.running"/>
  <part id="idle_attachment" model="example:block/mixer_idle_attachment"
        when="!$processors.mixer.running"/>
</appearance>
```

Here `example:block/mixer_casing` refers to
`assets/example/models/block/mixer_casing.json`. Its geometry, textures and display
transforms come from that JSON. Parts are optional; a self-closing appearance with
just `preset="model"` and `model="..."` also works. This preset rejects `face-texture`
children and the cube texture attributes (`front`, `side`, `top`, `bottom`). Author
the parts' materials in their own model JSON. Both presets use the same variable
conditions and change notifications. Model geometry does not change collision bounds.

When an inherited appearance changes preset, the old base attributes are discarded;
child rules are retained and validated against the new preset. Inherited face-texture
rules therefore prevent switching to `model`. Within the same preset, attributes and
parts continue to merge normally by ID. The authored base must have a different resource
ID from the machine's generated `block/<machine>` and `block/<machine>_base` models.

## Conditional appearance

Machine components expose typed presentation variables. An appearance may contain up to 64
conditional parts and face-texture rules in total. Each part is an ordinary block model resource:

```xml
<appearance preset="cube-front" front="example:block/mixer_front"
            side="example:block/casing" top="example:block/mixer_top">
  <part id="running_indicator" model="example:block/mixer_indicator"
        when="$processors.mixer.running"/>
  <part id="output_full" model="example:block/output_full"
        when="$inventories.output.fill &gt;= 0.9"/>
</appearance>
```

### Face texture switching

Use `face-texture` to replace a base-model face's texture without adding geometry:

```xml
<appearance preset="cube-front" front="example:block/mixer_front"
            side="example:block/casing" top="example:block/mixer_top">
  <face-texture id="blocked_front" face="front" texture="example:block/mixer_blocked"
                when="$processors.mixer.status == 'blocked'"/>
  <face-texture id="running_front" face="front" texture="example:block/mixer_running"
                when="$processors.mixer.running"/>
  <face-texture id="full_top" face="top" texture="example:block/full_top"
                when="$inventories.output.full"/>
</appearance>
```

Faces are `front`, `back`, `left`, `right`, `top`, and `bottom`, relative to the machine.
Left and right are as seen by someone looking at its front, matching side configuration.
Rules are evaluated in declaration order; the first matching rule **per face** wins.
If none matches, the existing base texture remains. Stable rule IDs allow inherited
definitions to override a rule in place without changing its priority.

Textures are block-atlas resources, normally `assets/<namespace>/textures/block/*.png`.
Without a mask, the switch preserves geometry, UV layout, tint, shading, emissive values
and render layer. Use the same material type as the base; a texture swap does not change
a solid material into a transparent one. For authored multi-element base models, all quads pointing in
the selected local direction receive the replacement texture. Conditional child parts are
unaffected, so an emissive indicator can coexist with a switched front texture.
Selections are computed from immutable snapshots, and retextured quads use a bounded cache.
World facing rotations apply to both the geometry and texture rules. Item models retain
the base appearance.

### Emissive masks

An optional `emissive-mask` on a face-texture rule selects the glowing pixels:

```xml
<face-texture id="running_front" face="front"
              texture="example:block/mixer_running"
              emissive-mask="example:block/mixer_emissive"
              when="$processors.mixer.running"/>
```

Use a static black-and-white PNG aligned with the switched texture. White pixels use that
texture's color at full brightness, without directional shading or ambient occlusion. Black
or transparent pixels keep the base face's normal lighting. The mask is binary: alpha and
brightness must both be at least 128 to select a pixel; gray is not a brightness gradient.
The mask and switched texture are selected together by the rule's `when` condition. To vary
only the glow, use the same color texture in masked and unmasked rules with different conditions.

Masks are read and partitioned once during model baking. The renderer caches the resulting
lit/unlit geometry; the regions share edges rather than overlaying a second face. There is no
per-tick pixel scan, and no additional overlay model is necessary. The mask must be a single
frame, at most 256 by 256 pixels and at most 4096 merged regions. Source faces need rectangular,
non-degenerate UVs within the texture; ordinary cube-front models and their facing rotations
meet this requirement. Masked faces keep the base render layer and do not emit world light.

### Variables and conditions

Use the actual processor, item-group, tank and energy IDs from the machine definition:

| Component prefix | Variables | Meaning |
| --- | --- | --- |
| `processors.<id>` | `running`, `paused` | Boolean activity/manual pause; completion ticks remain running. |
| `processors.<id>` | `status` | Text: `idle`, `running`, `stalled`, `blocked`, `held`, `paused`. |
| `processors.<id>` | `progress` | Fraction 0–1; retained during a pause, 1 on completion, 0 without a cycle. |
| `inventories.<group>` | `count`, `fill`, `empty`, `full` | Total item count, mean slot fullness, and booleans. Fullness respects slot and item stack limits. |
| `tanks.<tank>` | `amount`, `capacity`, `fill`, `empty` | Fluid amounts in mB, fraction 0–1, and boolean emptiness. |
| `energy.<id>` | `stored`, `capacity`, `fill` | FE amounts and fraction 0–1. |

Conditions support booleans, numbers, quoted text, parentheses, `!`, `&&`, `||`,
`==`, `!=`, `<`, `<=`, `>`, `>=`, and arithmetic `+ - * /`. Escape `&` and `<` in XML.
Use `${tanks.input/0.fill}` for IDs containing slashes, hyphens or colons. Division by
zero produces zero. Conditions must return a boolean; unknown variables and type
mismatches fail at startup with the XML source location. Numbers use double precision,
so use fill ratios rather than exact comparisons of very large FE totals.

Parts inherit the block's facing rotation and are additive. Use complementary conditions
to swap geometry. The part's model owns its textures, render type and emissive face data;
an illuminated strip can use NeoForge `neoforge_data` with `block_light: 15`,
`sky_light: 15`, `ambient_occlusion: false`, and `shade: false` on its element.
Keep overlay faces slightly offset to avoid z-fighting. Emissive faces do not emit world light.

With parts or face-texture rules present, the generated `block/<machine>` model uses the
`mmdlib:conditional_machine` loader and references `block/<machine>_base` plus each part.
For `cube-front`, the generated base is a cube; for `model`, it inherits the authored
model named in XML. Override the **base** model to customize the casing; overriding the root model bypasses
the generated composition. `base` is a reserved part ID. Items display only the base.
Machines without conditional appearance keep their existing generated models and do not sample variables.

Only referenced values are subscribed and sent to tracking players, including their initial
chunk snapshot, independently of menus. Component change notifications invalidate cached values
and schedule one update for the next server tick; multiple notifications coalesce. There is no
repeating appearance tick or getter polling. Reading a clean snapshot, including one for a newly
tracking player, reuses cached values without consuming any pending broadcast. Client snapshots
are immutable; identical snapshots skip condition evaluation, and chunk geometry is invalidated
only when an appearance condition changes result. Processor activity and progress have separate
signals: a running-only indicator does no appearance work for progress increments or energy use.
Variables that actually change each tick can still produce updates each tick when referenced.
These are static conditions; transforms, interpolation and animations are not implemented.
Keep conditions in the machine XML: changing predicates only in a resource-pack model would
not change the server's subscriptions or the client's invalidation rules.

### Java components

Implement `VisualVariableProvider` or use a component adapter. Declare each local name,
its typed default, and a server-side getter, then compose providers under stable prefixes:

```java
var heaterChanges = new ChangeSignal();
VisualVariableProvider heater = vars -> vars.observedBy(heaterChanges)
    .add("temperature", 0.0, this::temperature)
    .add("ready", false, this::ready);
var variables = new VisualVariables.Builder()
    .component("custom.heater", heater)
    .component("processors.main", processor)
    .build();
var visuals = new MachineVisualState(variables,
    List.of("$custom.heater.ready && $processors.main.running"));
// After a component mutation (not from a polling loop):
heaterChanges.changed();
```

`RecipeProcessor` implements the provider contract. `BuiltinVisualVariables` supplies
item, tank and energy adapters. This is an instance-level composition API, with no global
provider registry and no renderer-specific component type switches. Declarations are fixed
for the instance; duplicate names, non-finite numbers and changes in value type are rejected.
The current bounds are 256 variables, 256 characters per name/text value, and 2048 characters
per condition. Custom providers currently attach through Java; XML assembles the built-ins.

`ChangeSignal` lives in `api.util`; component listeners are separate from storage persistence
callbacks. A getter declaration without `observedBy` must be explicitly invalidated with
`variables.changed("fully.qualified.name")` after changes. Getters are read-only. Component
schemas are stable for the lifetime of an instance; reconnecting a removed owner resamples them.

For a Java machine, call `visuals.start(scheduleOnce)` when the server-side owner loads. The
scheduler must defer a callback that calls `visuals.flush(this)` until mutations have settled;
XML machines use a scheduled block tick with a one-tick delay. Call `visuals.stop()` when the
owner is removed/unloaded to detach subscriptions. No repeating tick hook is needed. Write
`visuals.write()` into `saveClientData`, apply it with `visuals.read(tag, this)` in
`loadClientData`, and expose `visuals.modelData()` from `getModelData`. Do not persist these
snapshots as inventory data. A hand-authored `mmdlib:conditional_machine` model supplies
`base` (a model object), `variables` (name-to-default values), and optional `parts` (objects with
`id`, `when`, and `model`). Optional `face_textures` entries contain `face`, `when` and
`texture`, and optionally `emissive_mask`; the texture and mask names refer to variables in
the root model's `textures` object, without `#`.
Its declarations and conditions must match the Java subscriptions.
Getters are never invoked on the rendering thread.

## Base machine and storage

The generic [`base_machine.xml`](../src/main/resources/mmdlib/machine/base_machine.xml)
provides block settings, redstone control, recovery and the common screen with generated
tank fills. It declares no storage. The powered base extends it:

```xml
<machine xmlns="https://moddev.zone/mmdlib/machine" version="1" abstract="true"
         extends="mmdlib:base_machine">
  <storage>
    <energy id="energy" capacity="100000" input-rate="1000" output-rate="0" color="energy"/>
  </storage>
  <groups>
    <group id="energy" storage="energy" mode="input"/>
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

Stable storage IDs, processing reservations and side settings survive saves.
Displaced resources use `mmdlib:machine_recovery_capsule`; use a capsule on an XML
machine to restore compatible items, fluids and energy. Overflow and unknown resource
entries remain in the capsule.

### Inline colors and the default palette

Automatic groups accept `color` directly on `<slots>`, `<tank>`, `<tanks>` and
`<energy>`. Use a palette name or a six-digit `#RRGGBB` color; both resolve to the
same RGB value used by automation controls, resource highlights and generated
inventory outlines. Colors are presentation only: a name never changes IO rules
or fluid filters. These names apply to **machine XML**; handwritten screen XML
keeps its existing color syntax.

```xml
<storage>
  <energy capacity="32000" input-rate="128"/>
  <items id="items">
    <slots id="output" count="4" role="output" color="output.primary"/>
  </items>
  <fluids id="fluids">
    <tank id="water" capacity="1000" role="input"
          fluid="minecraft:water" color="fluid.primary"/>
    <tank id="lava" capacity="1000" role="input"
          fluid="minecraft:lava" color="fluid.secondary"/>
  </fluids>
</storage>
```

This example inherits the energy ID and zero output rate from
`mmdlib:base_powered_machine`. No `<groups>` section is needed: each declaration
already supplies its group's ID, membership, role and color.

The default palette uses blue/violet/rose for item inputs, cyan/green/gold for
item outputs, blue/amber/violet for fluids, and coral for power:

| Name | RGB | Intended use |
| --- | --- | --- |
| `input.primary` | `#568BD8` | Main item input |
| `input.secondary` | `#A184CE` | Additional item input |
| `input.tertiary` | `#CF84AA` | Third item input |
| `output.primary` | `#4DC6D8` | Main product |
| `output.secondary` | `#83B967` | Secondary product |
| `output.tertiary` | `#D9BB62` | Further output/byproduct |
| `fluid.primary` | `#508EE0` | First fluid group; useful for water/coolant |
| `fluid.secondary` | `#EE963C` | Second fluid group; useful for lava/fuel |
| `fluid.tertiary` | `#9C83CF` | Third fluid/reagent group |
| `energy` | `#D85B5B` | Power |
| `storage` | `#8C9EAC` | Bidirectional item buffer |
| `manual` | `#AAA0B7` | Manual-only items |

Names are case-sensitive. Any name may be used on any group, and hex digits may
be upper- or lowercase. Unknown names and malformed hex fail at startup with the
authored file, line and value, including errors in inherited definitions.

With `color` omitted, input and output item groups advance independently through
their three variants in resolved declaration order and repeat after the third.
Fluid groups use their own sequence, irrespective of input/output role. Explicitly
colored groups still occupy their position in that sequence. A `<slots count="4">`
or `<tanks count="2">` is one group and receives one color, not one per member.
Bidirectional and manual item groups use `storage` and `manual` respectively.
Explicit names pin a color even when group order changes. Fluid identity does not
select colors automatically.

An explicit `<group color="...">` accepts the same names and overrides an inline
color; omitting its color preserves the inline/default choice. Inheritance merges
inline attributes normally. Explicit groups remain useful for grouping standalone
`<slot>` entries or overriding automation mode. A standalone `<slot>` has no
automatic group, so its color belongs on that explicit group.

For a tank that players fill through its screen gauge but automation must not
access or configure, use `role="manual" automation="false"`:

```xml
<tank id="water" capacity="1000" role="manual"
      fluid="minecraft:water" automation="false"/>
```

`automation` defaults to `true` on `<tank>` and `<tanks>`. Setting it to `false`
requires `role="manual"`, removes the automation group and side-configuration
highlight, and keeps a recipe group with the declared ID. Screen gauges, saved
contents, fluid filters and recipe reservations still work. Use `"consumed": false`
on that recipe input when the fluid must remain in place. Manual filling uses the
screen gauge; pipes and bucket interactions on the block cannot access these tanks.

The library base declares its energy color inline, so a child can override it with
`<energy color="#FF8844"/>`. It retains the uncolored energy group declaration for
compatibility with existing partial group overrides. Existing explicit group hex colors remain supported.
Uncolored machines adopt this palette; storage identities and capacities do not
change. Java consumers can use `MachineColors.parse(...)` and the immutable
`MachineColors.palette()` map from `zone.moddev.mc.lib.machine.definition`.

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

### Reusable recipe inputs

Item and fluid inputs accept `"consumed": false`; omitted means `true`:

```json
"inputs": {
  "water": [{"fluids": "minecraft:water", "amount": 1000, "consumed": false}],
  "catalyst": [{"items": {"item": "minecraft:diamond"}, "count": 1, "consumed": false}]
}
```

These are real resources: the full amount must be present and is reserved while
the cycle runs, preventing extraction or use by another cycle. Completion releases
the reservation without extracting the reusable amount. Do not duplicate it in
`outputs`. It still occupies storage space, including when products share the
same group. Consumed and reusable inputs in one group require separate amounts.
Reservations survive save/load and are released on pause as usual. JEI labels
these inputs **Not consumed**. Energy is spent per tick and rejects `consumed: false`.

Ghost storage remains different: its sample only matches the recipe and represents
no real resource to reserve or consume, regardless of this flag.
JEI also labels ghost item inputs **Not consumed**, using the storage declarations.
For a recipe type shared by several XML machines, this automatic label applies only
to groups that are ghost inputs on every machine using that type.
Ghost samples and reusable items/fluids appear faded in a separate group before
the consumed ingredients: `[ghosts] : [inputs] → [outputs]`. The colon is omitted
when there are no consumed ingredients. Samples are display-only in JEI, while
real reusable resources are catalysts and remain available in usage lookups.
Fluid icons use a capacity rounded up to the next 1,000 mB: 250 mB fills one
quarter of a bucket, 100 mB one tenth, and 1,250 mB uses a 2,000 mB scale. This
applies to consumed inputs, reusable fluids and outputs; tooltips keep the exact amount.
Reusable-fluid tooltips explicitly say **Requires N mB** alongside **Not consumed**;
the ghost appearance does not remove or reduce that requirement.

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

This delivery covers item processing with optional FE and fluids, and cube or authored-model appearances.
Multiple processors, world-interaction features and standalone loose content packs
remain outside this format's implemented scope.
