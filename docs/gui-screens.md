# GUI screens (design)

> **Status: planned, not implemented.** This page records the current design for declarative
> MMDLib screens so it can be reviewed before any code exists. Element and attribute names may
> still change; open questions are listed at the end.

Screens are declared in **XML**, in keeping with MMDLib's data-driven core. Each file is loaded
at startup into a typed screen model and validated there, with the file and line number in any
error. An XSD schema, maintained alongside the model, gives authors validation and
autocompletion in their editors.

## Files and IDs

Screens live in the same root `mmdlib/` folders as other definitions, under `screen/`. As with
every other definition, the ID comes from the file's location: `mmdlib/screen/material_press.xml`
in mod `example` is the screen `example:material_press`.

## Example

A processing machine with an energy gauge, an input, a progress arrow, two outputs, a fluid tank,
a compact meter, a few controls, an upgrade tab, and a side-configuration section:

```xml
<screen xmlns="https://moddev.zone/mmdlib/screen"
        title="screen.example.material_press"
        block-entity="zone.moddev.mc.example.PressBlockEntity">

  <vbox padding="7" spacing="4">
    <hbox spacing="4" align="center">
      <!-- Glass graduated gauge: fixed 18 px thick, any length. -->
      <gauge orientation="vertical" length="62" color="#C0392B"
             value="#getEnergy" max="#getEnergyCapacity"
             tooltip="screen.example.energy" tooltip-shift="screen.example.energy.detail"/>

      <group label="screen.example.input" tint="#E1E9FF" padding="8">
        <slot index="0" tooltip="screen.example.input_slot"/>
      </group>

      <!-- Clicking the arrow opens these recipes in JEI, when JEI is installed. -->
      <progress value="#getProgress" max="#getProgressMax" jei-recipes="mmdlib:crushing"/>

      <group label="screen.example.output" tint="#FFE8D6">
        <vbox spacing="4" align="center">
          <slot index="1" size="large"/>
          <slot index="2"/>
        </vbox>
      </group>

      <gauge orientation="vertical" length="62" value="#getWaterTank" max="#getWaterCapacity"/>
    </hbox>

    <hbox spacing="5" align="center">
      <!-- Compact segmented meter: 5 or 10 px thick, 2 px segments with 1 px gaps by default. -->
      <meter orientation="horizontal" thickness="5" length="60" color="#3F76E4"
             value="#getThroughput" max="#getMaxThroughput"/>
      <spacer/>
      <button text="screen.example.eject"
              on-click="example:eject_outputs" on-right-click="example:eject_one"
              on-shift-click="example:eject_all"/>
      <checkbox text="screen.example.auto_eject" value="#isAutoEject" on-click="example:toggle_auto_eject"/>
      <button icon="mmdlib:icon_sides" opens="sides" tooltip="screen.mmdlib.side_config"/>
    </hbox>
  </vbox>

  <!-- Grows out of the panel's right edge, sharing its background. -->
  <side-tab edge="right">
    <vbox spacing="2">
      <slot index="3" kind="upgrade" icon="mmdlib:upgrade_speed"/>
      <slot index="4" kind="upgrade" icon="mmdlib:upgrade_energy"/>
      <slot index="5" kind="upgrade" icon="mmdlib:upgrade_capacity"/>
    </vbox>
  </side-tab>

  <section id="sides" title="screen.mmdlib.side_config">
    <side-config resources="items fluids energy"/>
    <radio-group label="screen.mmdlib.redstone" value="#getRedstoneMode" on-click="mmdlib:set_redstone_mode">
      <radio text="screen.mmdlib.redstone.ignore"/>
      <radio text="screen.mmdlib.redstone.high"/>
      <radio text="screen.mmdlib.redstone.low"/>
    </radio-group>
  </section>
</screen>
```

The standard player inventory (three rows of nine, a 4 px gap, then the hotbar) is added at the
bottom automatically. Write `<player-inventory/>` only to place it somewhere else, or set
`inventory="none"` on `<screen>` to leave it out.

## Layout

- **Boxes.** `vbox` stacks children top to bottom and `hbox` left to right, so text of any
  length (translations included) pushes its neighbours instead of overlapping them.
  `padding`, `spacing`, and `align` (`start`, `center`, `end`) control spacing.
  `<spacer/>` takes up the leftover room in its box.
- **Units.** Sizes are plain integers in GUI pixels.
- **Escape hatch.** `offset-x` and `offset-y` nudge any element by a few pixels after layout,
  for pixel-exact art.
- **Groups.** `<group>` draws a thin recessed panel with an optional `label` and `tint`.
- **Side tabs.** `<side-tab edge="right">` grows out of the panel's edge; see
  [GUI sprites](gui-sprites.md#placement).
- **Sections.** `<section>` holds an alternative view, such as side configuration. A button
  with `opens="<id>"` shows it on the client, with no handler needed.

## Values

`value` and `max` name a getter on the mod's own Java code, which is always present behind a
screen anyway. Every open screen belongs to exactly one block entity (the menu is created for
the block at a position), and that instance is what the getters are called on:

| Form | Resolves to | Called as |
|---|---|---|
| `#method` | A public instance method on the screen's `block-entity` class (or a superclass or interface of it), taking no parameters | `blockEntity.method()` |
| `Class#method` | A public **static** method taking the block entity (or a supertype) as its only parameter, e.g. a helper shared by several machines | `Class.method(blockEntity)` |

Resolution happens in two steps. At load, each reference is looked up from the class and name
alone (`findVirtual` / `findStatic`) and checked; no instance is involved. When a player opens
the screen, the server binds the handles to that menu's block entity, after checking it is an
instance of the declared `block-entity` class. An instance method on any other class has no
instance to call and is rejected at load.

Getters run only on the server, each tick while the screen is open. The client never calls
them; it only receives the values.

MMDLib provides no built-in value names. Generic names could only cover the simplest cases
(a single energy buffer, a single tank) and would need rules for every ambiguous one, while the
block entity's author can always expose exactly the value a screen should show.

The loader checks at startup that each method exists, belongs to the right class, and returns
a supported type. A fluid gauge's getter returns the fluid itself (its type and amount); energy
and other numbers return a number. Values reach the client through MMDLib's own sync packets, which send only
the values that changed and carry numbers of any size: vanilla's menu sync is limited to 16 bits,
and some values (ProjectE's EMC, for one) exceed even a Java `long`.

## Attributes shared by every element

| Attribute | Runs on | Purpose |
|---|---|---|
| `value`, `max` | server → client | What the element shows (see [Values](#values)) |
| `tooltip` | client | Translation key; `{value}` and `{max}` are filled in, with large numbers shortened (`40.2 E`) |
| `tooltip-shift` | client | Detailed tooltip shown while Shift is held; a "Shift for more" hint is added automatically |
| `on-click`, `on-right-click` | server | Handler IDs from extension files; the handler receives the player, menu, element, and held modifier keys |
| `on-shift-click`, `on-shift-right-click` | server | Optional: used instead of the plain handler while Shift is held |
| `on-hover` | client only | Handler run when the mouse enters and leaves the element; display only, it cannot change game state |
| `hover-shows` | client | ID of an element to show while hovering, without any handler |
| `offset-x`, `offset-y` | client | Pixel nudge after layout |

Clicks are sent to the server through the menu's button channel, and the server checks the
player, menu, and element before running the handler. Hover never reaches the server.

## Elements

| Element | Purpose | Notable attributes |
|---|---|---|
| `screen` | Root | `title`, `block-entity`, `inventory` |
| `vbox`, `hbox` | Layout boxes | `padding`, `spacing`, `align` |
| `spacer` | Fills leftover room in a box | |
| `group` | Recessed grouping panel | `label`, `tint`, `padding` |
| `slot` | Item slot from the menu | `index`, `size` (`normal`/`large`), `kind` (`normal`/`upgrade`/`ghost`), `tint`, `icon` |
| `gauge` | Glass graduated gauge ([gauge textures](gauge-textures.md)) | `orientation`, `length`, `color` (solid fill) or a fluid value |
| `meter` | Compact segmented gauge | `orientation`, `thickness` (5 or 10), `length`, `color`, `segment`, `gap`, `gap-color` |
| `progress` | Fill-in progress indicator | `icon` (default arrow), `direction`, `jei-recipes` |
| `label` | Text | `text` |
| `button` | Vanilla-style button | `text` or `icon`, `opens` |
| `checkbox` | Checkbox | `text` |
| `radio-group`, `radio` | Radio buttons; the group's value is the selected index | `label`, `text` |
| `side-tab` | Tab growing out of the panel edge | `edge` |
| `section` | Alternative view | `id`, `title` |
| `side-config` | Six-face IO configuration | `resources` |
| `player-inventory` | Explicit placement of the player inventory | |

## JEI (optional)

When JEI is installed:

- `slot kind="ghost"` accepts an ingredient dragged from JEI. This sets the item the slot
  **expects** (a filter) without placing any real items; the server validates the change.
- `jei-recipes` on any element makes clicking it open those recipe categories, named by their
  JEI category ID (e.g. `mmdlib:crushing`, `minecraft:smelting`); several may be listed,
  separated by spaces. JEI resolves names at runtime, so an unknown name is logged as a warning
  naming the screen file, and the attribute is simply inactive when JEI is absent. The usual
  place for it is the progress indicator, as on vanilla furnaces in JEI.
- JEI's recipe transfer ("+") can move ingredients into the screen's input slots.
- Side tabs and pop-outs are reported to JEI automatically so its item list does not cover them.

## Edge cases (reviewed)

- **Host block gone or player too far away.** MMDLib's base menu implements `stillValid` with
  vanilla's block-entity check, so the server closes the screen when the block entity is removed
  or replaced, or the player moves out of reach. Bound getters are released with the menu.
- **Getter throws.** Getters run every tick, so each call is wrapped in a try/catch: the first
  failure is logged with the screen file and getter name, and the value shows as unavailable.
  A getter that never returns cannot be detected and remains the author's responsibility.
- **Getter cost.** Getters must be cheap and must not change any state.
- **Several players viewing one machine.** Updates are broadcast: changes are computed once per
  block entity per tick and sent to every player with that screen open. A player who opens it
  later first receives a full snapshot.
- **Desync.** The server is the source of truth: when client and server disagree, the client's
  state is reset to the server's.
- **Client and server versions differ.** A client with a different screen layout cannot be fixed
  by resetting values, so MMDLib's payloads are registered with a protocol version, bumped
  whenever the screen format changes, and NeoForge refuses mismatched connections.
- **Number types.** Display and widget code handle every whole number as `BigInteger`, so int,
  long, and BigInteger getters all work. Decimal fields (float, double) are fully supported and
  kept as `double`, e.g. for temperatures, pressures, and percentages. Any other return type is
  rejected at load when the declared type shows it, or on first read otherwise. Mixing them up
  (feeding a decimal where a whole number is expected) is an author error, like an expensive
  getter.
- **Remote slot access (e.g. Super Factory Manager).** Such mods address slots by number through
  the block's item capability, never through the screen, so the rules belong to MMDLib's
  capability wrappers. Every face exposes **all** slots with the same numbering. Side
  configuration only decides which operations a face allows: an input face refuses extraction,
  and a disabled face refuses everything. A player's configuration change therefore never
  renumbers slots under an existing automation program. An index outside the handler's range is
  refused rather than thrown: reads return an empty stack, inserts hand the stack back
  unchanged, and extracts return nothing.

## Open questions

- **Navigation:** sections shown as tabs (Mekanism style, or hanging off the left edge) or by
  swapping part of the screen in place. Awaiting outside input; the format supports either.
- **Information pop-outs:** proposed `<popout kind="info|problems" edge="left|right">` elements,
  PneumaticCraft style, with problems reported live by the block entity.
- **Meter extras:** colour zones (VU style) and peak hold (EQ style) are suggested, not agreed.
