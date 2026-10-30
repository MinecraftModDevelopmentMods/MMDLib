# GUI screens (design)

> **Status: implemented; runtime validation is described below.** Screens load at startup,
> open through `block-right-click`, and use one generic menu with custom value packets.
> Rendering and optional JEI integration live exclusively on the client.

## Implementation map

| Piece | Where | State |
|---|---|---|
| Schema | `mmdlib/schema/screen.xsd` (in MMDLib's jar) | Done |
| Typed model | `gui.model`: `Screen`, `Element` (one record per XML element), `Common`, `Text`, `ValueRef` | Done |
| Reading and checks | `gui.load.ScreenReader`: hardened SAX parser (no DOCTYPE, no external entities), schema validation, then the checks below | Done |
| Getter binding | `gui.load.ScreenBinder`: `#method` / `Class#method` resolved to method handles, return kinds checked per element | Done |
| Discovery | `gui.load.ScreenLoader`, called at startup; results in `gui.Screens` | Done |
| Layout | `gui.layout.ScreenLayout`: positions in GUI pixels; tab fit checked at load | Done |
| Menu and opening | `gui.runtime.MachineMenu`, `ScreenRuntime`, `BlockEvents.openMenu` | Implemented |
| Value sync | `gui.runtime.ScreenNetwork`, `ScreenValues`, `ScreenSnapshots` | Implemented |
| Client rendering | `gui.client.MachineScreen`, `GaugeRenderer`, `ScreenText` | Implemented |
| Optional JEI | `client.jei.MachineJei`, loaded only by the JEI plugin | Implemented |

Checks made at load, beyond the schema: unique `id`s and slot indexes; `shows`, `hover-shows`
and `side-config groups` name existing elements; buttons have a tooltip and text or an icon; a
`-fallback` attribute has its key; `max` has a `value`; gauges, meters and progress indicators
have both; checkboxes and radio groups have a `value`; info pop-outs have text and
warning/error pop-outs a `value`; `spacer` sits directly in a box; at most one
`player-inventory`, and none with `inventory="none"`; the block-entity class exists and is a
`BlockEntity`; every getter exists, is public, and returns a kind the element can show; side
tabs and pop-out tabs fit their edge.

Getter return kinds: whole numbers (`byte` to `long`, boxes, `BigInteger`), decimals (`float`,
`double`), a declared `Number`, `boolean`, `FluidStack` (fluid gauges), `Component` and
collections of components (warning and error pop-outs).

Screens are declared in **XML**, in keeping with MMDLib's data-driven core. Each file is loaded
at startup into a typed screen model and validated there, with the file and line number in any
error. An XSD schema, maintained alongside the model, gives authors validation and
autocompletion in their editors.

## Wiring a machine

Implement `gui.runtime.ScreenHost` on the block entity. `screenItems()` exposes its player-facing
modifiable inventory (`IItemHandlerModifiable`); using `ItemStorage` preserves output restrictions and item filters. The menu adds real
slots by the XML indexes, then the optional player inventory. Upgrade slots additionally limit
stacks to one item. Sparse machine indexes remain reserved and inactive.

```java
@Override public IItemHandlerModifiable screenItems() { return items; }
@Override public int[] screenDestinations(ItemStack stack) {
    return isFuel(stack) ? new int[] {1} : new int[] {0};
}
@Override public Map<String, SideConfig> screenSides() {
    return Map.of("input", inputSides, "output", outputSides, "energy", energySides);
}
@Override public int[] screenRecipeSlots(ResourceLocation category) {
    return category.equals(MY_RECIPE_CATEGORY) ? new int[] {0} : new int[0];
}
```

`screenDestinations` controls shift-click routing into the machine; its default tries all storage
indexes, subject to their filters. `screenRecipeSlots` explicitly names recipe inputs in recipe
order; its default disables JEI transfer. Both methods must work on the client as well as the
server. JEI uses its standard transfer handler with these slots, including its server validation.
Only visible normal slots participate in recipe transfer. Category IDs must also occur in the
screen's `jei-recipes` attributes.

Ghost filters are separate from the real inventory. Override `setScreenGhost(player, index, filter)`
to validate and persist a filter, and `screenGhost(index)` to expose its authoritative value. The
menu sends at most one item as the filter; an empty stack clears it. Defaults reject changes and
show empty filters. No real items move through this path.

Bind the block's `block-right-click` event to `mmdlib:open_menu`. This handler first looks for an
XML screen matching the clicked block entity's type; otherwise it uses the block's own vanilla
menu provider. It consumes matching clicks on both sides and opens only on the server. A direct
code caller may use `ScreenRuntime.open(player, blockEntity)`.

Click extension methods use event type `gui-click` and signature `public static void method(ScreenClick click)`.
The context supplies the player, menu, element, mouse button, Shift/Control/Alt state, and selected
radio option. Hover extensions use `gui-hover` with `ScreenHover` and run only on the client.
All handler IDs and their event contracts are checked at startup. A click handler should still
validate any machine-specific permission or operation it implements.

```json
{"events": [{"type": "gui-click", "id": "example:eject_outputs",
             "handler": "example.PressEvents#eject", "name": "Eject outputs"}]}
```

The owning mod must register every prepared block-entity handle after queuing its definitions;
see [block entities](block-entities.md). MMDLib uses the resulting type for block construction,
screen lookup and capability registration.

The button channel supports 4,096 elements and machine slot indexes through 4,095; a side
configuration offers at most 682 groups. These limits are checked at startup. Whole values use
signed `BigInteger` byte arrays (up to 1 MiB per decoded integer), decimals use doubles, and fluid
and rich text values use Minecraft/NeoForge codecs. Snapshots copy mutable values before comparison.
Repeated references to one getter are evaluated once per machine per tick, shared by viewers.
Navigation is checked on the server and its selection is synced back to the client. Opening also
compares a screen-model fingerprint, so differing XML definitions fail explicitly instead of
assigning values or actions to the wrong elements.

`./gradlew check` includes numeric protocol and layout regression checks. The isolated graphical
smoke run is `xvfb-run -a ./gradlew runClient --offline -I scripts/gui-smoke.init.gradle`; repeat
with `-PwithoutJei` for the optional-integration case. It creates a disposable integrated world
under `build/gui-smoke*`, checks server actions and full codecs, opens the actual menu, verifies
synced values, and saves `screenshots/gui-smoke.png`. Run a normal build afterward to remove
injected smoke classes from main output; release verification rejects any leaked smoke classes.

## Files and IDs

Screens live in the same root `mmdlib/` folders as other definitions, under `screen/`. As with
every other definition, the ID comes from the file's location: `mmdlib/screen/material_press.xml`
in mod `example` is the screen `example:material_press`.

A block entity's screen is found from its registry name: block-entity type
`example:material_press` opens screen `example:material_press`, with no other configuration.
A block entity generated for a material (for example `example:copper_press` from module
`example:press`) needs a screen file under its own name. The first version has **no fallback**
to the module's ID. Adding one later would break nothing, while removing one would break
existing packs, so it will be added only if early users ask for it. If it is added, it must
not hide a missing or misnamed screen file without a log entry.

## Example

A processing machine with an energy gauge, an input, a progress arrow, two outputs, a fluid tank,
a compact meter, a few controls, an upgrade tab, and a side-configuration view:

```xml
<screen xmlns="https://moddev.zone/mmdlib/screen"
        title="screen.example.material_press"
        block-entity="zone.moddev.mc.example.PressBlockEntity">

  <vbox padding="7" spacing="4">
    <!-- A context area: buttons swap what it shows. The first view is the default. -->
    <context id="main">
      <view id="machine">
        <hbox spacing="4" align="center">
          <!-- Glass graduated gauge, one slot thick; fill makes it as tall as this row. -->
          <gauge id="energy" orientation="vertical" length="fill" color="#C0392B"
                 value="#getEnergy" max="#getEnergyCapacity"
                 tooltip="screen.example.energy" tooltip-shift="screen.example.energy.detail"/>

          <group id="input" label="screen.example.input" tint="#E1E9FF" padding="8">
            <slot index="0" tooltip="screen.example.input_slot"/>
          </group>

          <!-- Clicking the arrow opens these recipes in JEI, when JEI is installed. -->
          <progress value="#getProgress" max="#getProgressMax" jei-recipes="mmdlib:crushing"/>

          <group id="output" label="screen.example.output" tint="#FFE8D6">
            <vbox spacing="4" align="center">
              <slot index="1" size="large"/>
              <slot index="2"/>
            </vbox>
          </group>

          <!-- 3.5s is three and a half slots: 63 px. -->
          <gauge id="water" orientation="vertical" length="3.5s" value="#getWaterTank" max="#getWaterCapacity"/>
        </hbox>
      </view>

      <view id="sides" title="screen.mmdlib.side_config">
        <side-config groups="input output water energy"/>
        <radio-group label="screen.mmdlib.redstone" value="#getRedstoneMode" on-click="mmdlib:set_redstone_mode">
          <radio text="screen.mmdlib.redstone.ignore"/>
          <radio text="screen.mmdlib.redstone.high"/>
          <radio text="screen.mmdlib.redstone.low"/>
        </radio-group>
      </view>
    </context>

    <hbox spacing="5" align="center">
      <!-- Compact segmented meter: 5 or 10 px thick, 2 px segments with 1 px gaps by default. -->
      <meter orientation="horizontal" thickness="5" length="60" color="#3F76E4"
             value="#getThroughput" max="#getMaxThroughput"/>
      <spacer/>
      <!-- Switches the context area; the tooltip is required and says what the button shows. -->
      <button icon="mmdlib:icon_sides" shows="main:sides" tooltip="screen.mmdlib.side_config.show"/>
      <button text="screen.example.eject"
              on-click="example:eject_outputs" on-right-click="example:eject_one"
              on-shift-click="example:eject_all" tooltip="screen.example.eject.tooltip"/>
      <checkbox text="screen.example.auto_eject" value="#isAutoEject" on-click="example:toggle_auto_eject"/>
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
- **Units.** Most authors never give a size: boxes place everything. Where a size is needed
  (`length`, `padding`, `spacing`, `segment`, `gap`, `offset-x`, `offset-y`) it is written
  either in **slots** or in **GUI pixels**:
  - `3s` is three slots, one slot being 18 px (a slot's size on screen); fractions are allowed
    (`3.5s` = 63 px) and are rounded to the nearest pixel. Use slots to line things up with
    slot rows and columns.
  - A plain number is GUI pixels, for fine detail such as the 5 px meter or a 2 px gap.
  - GUI pixels are not screen pixels: Minecraft's GUI Scale setting scales the whole screen.
- **`fill`.** `length="fill"` makes a gauge or meter as long as the box around it is across, so
  it always matches its neighbours, even when they change. A vertical gauge or meter with `fill`
  goes directly in an `hbox` (it matches the row's height); a horizontal one goes in a vertical
  stack such as a `vbox`, `group` or `view` (it matches the column's width). Anywhere else is
  an error at load.
- **Escape hatch.** `offset-x` and `offset-y` nudge any element by a few pixels after layout,
  for pixel-exact art.
- **Groups.** `<group>` draws a thin recessed panel with an optional `label` and `tint`.
- **Side tabs.** `<side-tab edge="right">` grows out of the panel's edge; see
  [GUI sprites](gui-sprites.md#placement).
- **Context areas (Industrial Foregoing style).** `<context id>` is part of the screen whose
  content buttons can swap in place. Each `<view id>` inside it is one alternative, such as side
  configuration; the first view is shown when the screen opens. Behaviour, all client side with
  no handler needed:
  - A button with `shows="<context>:<view>"` switches that context area to the view. Pressing
    it again, or the back button drawn in the corner of every non-default view, returns to the
    default view. Escape also returns to the default view first, then closes the screen.
  - The area is sized to its largest view, so switching never moves the rest of the screen.
  - A view's `title` is drawn at its top, so the player can always tell which view is showing.
  - The shown view is remembered per screen file for the session.
  - Slots in a hidden view exist on the server menu at all times, but are hidden and refuse
    clicks and shift-click transfers.
- **Button tooltips are required.** A `button` must have a `tooltip`, and the loader rejects a
  screen file without one. The tooltip says what the button does or shows (for example
  "Side configuration: choose which faces accept and output each slot group, tank and energy"),
  because icon buttons alone were found unclear.

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
| `id` | client | Names the element so others can refer to it (`hover-shows`, `side-config groups`); unique within the screen |
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
| `slot` | Item slot from the menu | `index`, `size` (`normal`/`large`), `kind` (`normal`/`upgrade`/`ghost`; `upgrade` holds one item), `tint`, `icon` |
| `gauge` | Glass graduated gauge ([gauge textures](gauge-textures.md)) | `orientation`, `length` (size or `fill`), `color` (solid fill) or a fluid value |
| `meter` | Compact segmented gauge | `orientation`, `thickness` (5 or 10), `length` (size or `fill`), `color`, `segment`, `gap`, `gap-color` |
| `progress` | Fill-in progress indicator | `icon` (default arrow), `direction`, `jei-recipes` |
| `label` | Text | `text` |
| `button` | Vanilla-style button | `text` or `icon`, `tooltip` (required), `shows` (`context:view`) |
| `checkbox` | Checkbox | `text` |
| `radio-group`, `radio` | Radio buttons; the group's value is the selected index | `label`, `text` |
| `side-tab` | Tab growing out of the panel edge | `edge` |
| `context`, `view` | Area whose content buttons swap; one alternative content | `id`; `id`, `title` |
| `popout` | Information pop-out on an edge tab | `kind` (`info`/`warning`/`error`), `icon`, `edge`, `text` |
| `side-config` | Six-face IO configuration, one resource group at a time | `groups` (element IDs) |
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
- Side tabs and pop-outs (open or collapsed) are reported
  to JEI automatically so its item list does not cover them.

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

## Decisions

- **Side configuration by resource group (Industrial Foregoing style).** Slots and tanks are
  grouped by role, such as item input, item output, a fluid tank or energy, and each group has
  its own six-face setting. This is less granular than Mekanism's per-slot setup. The block
  entity's code defines the groups; `<side-config groups="...">` lists which ones the screen
  offers, each naming the `id` of the element that shows it (a `<group>`, `<gauge>`, ...).
  While a group is selected in the side-configuration view, that element's background is
  highlighted when it is visible on the screen, so the player sees which slots or tank they are changing.
  Numbering is unaffected: every face still shows every slot, and a group's setting only
  allows or refuses operations on its own slots.
- **Block-entity registration is a hybrid.** For a data-defined machine, MMDLib validates the
  definition, prepares its factory, builds the type and keeps a handle to it, so it can do the
  wiring: the block, the screen lookup and capability registration all go through that handle.
  The mod that provides the block-entity implementation does the actual registration, in its
  own namespace, by passing its register to the handle. This keeps registration out of
  MMDLib. A handle left unregistered is reported as an error at load, before such a block can
  be placed.

- **Navigation:** Industrial Foregoing style context areas (`<context>` / `<view>`): buttons
  swap part of the screen in place. This replaces the earlier Mekanism-style detached windows,
  following feedback from players of games with complex interfaces. Because such buttons can be
  unclear, every button needs a tooltip that says what it does.
- **Upgrades (AE2 / Industrial Foregoing style, not Mekanism's).** Upgrades stay in an
  always-visible `<side-tab>` column with a small, fixed number of slots; they never move into
  a view. Upgrades are **tiered, not stacked**: an upgrade slot holds exactly one item, and a
  stronger effect comes from a higher-tier upgrade item, not from a larger stack.

- **Upgrade meaning belongs to the mod.** MMDLib supplies upgrade slots (one item each) and
  their layout, and defines no upgrade items, tiers, or effects. The mod's block entity decides
  what an upgrade item is and what it does.
- **JEI recipe click areas** (`jei-recipes`) are wanted, as described under JEI.
- **Information pop-outs** are wanted (PneumaticCraft style), in three kinds: `info`
  (explains the screen), and `warning` and `error` (reported live by the block entity). Their
  text is styled rich text built on Minecraft's own text formatting (translatable components
  with colour, bold, italic, and so on), so translations carry their styling.
- **All text is translated.** Screen files never contain display text. Every text-bearing
  attribute (`title`, `text`, `label`, `tooltip`, `tooltip-shift`) is a translation key, and
  elements take no text content, so the XSD and loader reject inline text. Warnings and errors
  reported by a block entity are translation keys with arguments (translatable components),
  never preformatted strings. Lang files live on the client, so a key missing there is logged
  once per screen file and key when the screen first opens. It then shows as the raw key
  (vanilla's `Language.getOrDefault` returns the key itself). Authors may opt in to a fallback:
  each text attribute has an optional `-fallback` partner (`text-fallback`, `title-fallback`,
  ...) holding literal text shown only when the key is missing. A block entity may likewise
  report `Component.translatableWithFallback`. The missing key is still logged. These are the
  only literal text a screen file may contain, and MMDLib never adds a fallback itself, so by
  default a missing translation is always visible.
- **Pop-out text markup.** Text comes from lang keys and is turned into Minecraft text
  components. Two notations combine:
  - Minecraft's own `§` formatting codes (`§c` red, `§l` bold, `§r` reset, ...) for colour
    and style. Lang strings already render these.
  - Usenet / old e-mail emphasis, deliberately not Markdown: `*bold*`, `/italic/`,
    `_underline_`. A marker only counts at a word boundary: it opens before a word and closes
    after one. So `a/b`, `snake_case` and `2*3` stay literal. `\*`, `\/` and `\_` escape a
    marker.
  - Layout: a blank line starts a new paragraph. Text wraps to the pop-out's width and
    scrolls when it is taller.
- **Edge tabs are author-placed.** Each pop-out tab
  takes `edge` (`left` or `right`) and `icon`, so a screen author spreads them across the
  two sides instead of crowding one. Each pop-out kind has a default icon
  (`mmdlib:icon_info`, `mmdlib:icon_warning`, `mmdlib:icon_error`; see
  [GUI sprites](gui-sprites.md)), which `icon` replaces. Tabs on the same edge stack in document order, below any
  `<side-tab>` there. A screen whose tabs do not fit the panel height on an edge fails at
  load, naming the screen file and edge.

- **The screen is display only.** Behaviour belongs to the block entity's own code, which the
  screen is wired to:
  - `<slot index="N">` is slot N of the menu, exactly as numbered. What the slot holds and
    what it accepts (including which items an upgrade slot takes) is decided by the block
    entity's storage code.
  - Shift-click routing is the block entity's menu code, as in vanilla. A furnace, for example,
    sends a smeltable item to its ingredient slot and other fuel to its fuel slot. MMDLib only
    keeps slots in hidden views out of that routing.

## Proper use

These are guidance for screen authors, not rules MMDLib can check:

- **Say exactly what a control does.** A tooltip or description names the action and its
  effect ("Eject: move everything in the output slots into the inventory below"), not just the
  control's name ("Eject"). A button that switches the screen says what the new view is for.
- **Name things the way players see them.** "Input slots", "Water tank", "Energy", not internal
  names or slot numbers.
- **Explain state, not only actions.** A warning or error says what is wrong and what the
  player can do about it ("No power: connect an energy source to any face set to input").
- **Write for translation.** Keep one idea per key, and use arguments (`{value}`, `{max}`)
  instead of building sentences from fragments.

MMDLib requires a `tooltip` on every button, since its presence can be checked; whether the
text is clear is the author's responsibility.

## Open questions

None.

## Possible later

- **Meter extras:** colour zones (VU style) and peak hold (EQ style). Left out to keep the
  first version simple.
