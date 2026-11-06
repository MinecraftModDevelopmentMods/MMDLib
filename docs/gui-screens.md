# GUI screens (design)

> **Status: implemented; runtime validation is described below.** Screens load at startup,
> open through `block-right-click`, and use one generic menu with custom value packets.
> Rendering and optional JEI integration live exclusively on the client.

Machines can opt into [inventory colours, side drawers and item-provided filter editors](inventory-automation.md)
through `ScreenHost.screenAutomation()`. This adds the selectors, fixed addon/filter slots and
embedded editor without adding per-filter XML to each screen.

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

`screenItems()` must also work on the client. The client's menu keeps its own copy of the slot
contents, and asks the client block entity's `screenItems()` what each slot accepts and how much it
holds, so a click is predicted the way the server will answer it. It's only asked about rules, so its
contents don't need to be synced.

Return the `ItemStorage` itself, or a handler/wrapper implementing `ReservedSlots`, including
`plan(slot)`, so both menu sides receive and enforce the same reservations.

When the machine has reserved items in a slot (see [capabilities](capabilities.md#reservations)),
the menu keeps them from being taken or replaced:

- Left and right click, Q and Ctrl+Q, and shift-click take only what is free.
- Clicking the slot with a different item is refused.
- Number-key and offhand swaps move only the unreserved portion.
- Double-clicking a slot with a plan doesn't collect from elsewhere. Double-clicking another slot
  still takes the planned slot's free part.

Machine slots show reservations at the top right. Both reservation and held quantities use
87.5% of vanilla's font size:
`-2` means two items reserved for input, and `+1` means space reserved for one output. The held
quantity stays at the bottom right, using the same size even when no reservation is present.
An empty planned output shows its item model at 50%
opacity with `+1` at the top, without creating a real stack. Mixed consume/produce plans show
both signed counts on the top row. Tooltips provide reservation counts and the planned output's
name. Clearing the plan removes the badge and ghost. Player inventory, equipment and filter
slots are unaffected.
Chance outputs show their reserved capacity, not a guarantee that the product will be rolled.
No extra packet or XML binding is needed; `MachineMenu.reservationPlan(index)` exposes the existing
synchronized snapshot for real machine slots.

The client's menu works out the same answer from the slot's plan, which arrives with the screen's
values, so clicks don't flicker. Bars and numbers still come from the getters named in the XML; a
feature's own client sync (see [block entities](block-entities.md#machine-features)) doesn't
replace them.

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
menu provider. It consumes matching clicks on both sides and opens only on the server. A click
holding a fluid container on a face that has a fluid handler is left alone, so the block's own
item use can fill or empty the tank; MMDLib's `MaterialEntityBlock` does not do that itself. A
direct code caller may use `ScreenRuntime.open(player, blockEntity)`.

`screenSides()` keys must equal the element `id`s named in `<side-config groups="...">`; a group
with no entry in the map is listed, but shows no modes and ignores clicks.

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

## Registering custom widget types

A consuming mod can add a leaf widget without replacing MMDLib's screen or menu. Register its
common-side contract in the mod constructor, before screen discovery:

```java
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.gui.WidgetType;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.load.BoundValue.Kind;

WidgetTypes.GLOBAL.register(ResourceLocation.parse("example:temperature"),
    new WidgetType(Set.of(Kind.WHOLE, Kind.DECIMAL, Kind.NUMBER), widget -> {
        if (widget.common().value().isEmpty() || widget.common().max().isEmpty())
            throw new IllegalArgumentException("Temperature needs value and max");
        if (!widget.properties().isEmpty())
            throw new IllegalArgumentException("Temperature has no custom properties");
    }));
```

`valueKinds` lists the declared getter return kinds the widget accepts. Use an empty set for a
decoration without a value; `max`, when present, is always numeric and requires `value`. The
validator checks required bindings, dimensions and properties. Throw `IllegalArgumentException`
with an author-facing explanation; MMDLib adds the screen file, line and widget type. Validators
run on both sides at startup and must not use client classes or a live world. IDs cannot be
replaced, and type registration closes when startup screen discovery begins.

Use the registered type in any ordinary widget container:

```xml
<custom id="temperature" type="example:temperature" width="36" height="2s"
        value="#getTemperature" max="#getMaxTemperature"
        tooltip="screen.example.temperature"
        tooltip-fallback="Temperature: {value} / {max}"/>
```

Width and height are required, from 1 to 1024 GUI pixels; the existing `s` slot unit is supported.
The element receives normal box placement and supports all common attributes, including offsets,
tooltips, `on-click`, `on-right-click`, shifted clicks, `on-hover`, `hover-shows`, and `jei-recipes`.
An interactive instance handles input first. If it returns `PASS`, JEI recipe actions take
priority over ordinary click handlers; use a separate recipes control when needed.

Types that need configuration can accept named properties:

```xml
<custom type="mmdlib_companion:progress_dial" width="24" height="24"
        value="#getProgress" max="#getTime">
  <property name="color" value="#55CC88"/>
</custom>
```

The type validator owns the allowed property names and value formats. Duplicate names fail
loading. `widget.properties()` is an immutable, sorted map of strings, included in the screen
fingerprint. Property nodes do not create menu elements or slots. Unknown XML attributes still
fail schema validation. The generic XSD can describe property structure; per-type rules are
checked by Java at startup.

Register drawing separately, **only on the client**, during client mod construction or
`FMLClientSetupEvent`:

```java
import zone.moddev.mc.lib.gui.client.WidgetRenderers;

WidgetRenderers.GLOBAL.register(ResourceLocation.parse("example:temperature"), context -> {
    var g = context.graphics();
    var r = context.bounds();
    g.fill(r.x(), r.y(), r.right(), r.bottom(), 0xff353535);
    int filled = (int) Math.round(r.height() * context.ratio());
    g.fill(r.x(), r.bottom() - filled, r.right(), r.bottom(), 0xffdd6644);
});
```

The context supplies the custom element, absolute GUI bounds and mouse coordinates, partial
tick, and the server-synced `value()` / `maximum()`. Values can be null before sync or when a
getter fails. Whole numbers arrive as `BigInteger`, decimals as `Double`; boolean, fluid and
text kinds use the existing protocol. `ratio()` clamps numeric values to 0..1 and returns zero
when unavailable. `hovered()` tests the widget's rectangle. Do not mutate or retain synced
objects, and never call machine getters from the renderer.

MMDLib clips drawing to the widget rectangle and restores its pose and tint afterward. Renderers
must balance any additional pose/scissor pushes and restore any other render state they change.
Renderer IDs must have a common-side type, and every custom widget used in a loaded screen must
have a renderer. These checks run at client load completion, when renderer registration closes.
Rendering and this registry are never loaded on a dedicated server.

This API supports leaf widgets. It does not provide custom child containers or additional getter
channels beyond `value` and `max`. Stateful input and typed actions are described below. Ordinary
click handlers continue to use menu ownership, distance, visibility and action validation.

The companion Crusher demonstrates a clickable progress dial in
`CompanionWidgets`, `client.CompanionWidgetsClient`, and `mmdlib/screen/crusher.xml`. Its custom
click pauses/resumes the machine through the same server callback as the pause button. The
companion game tests exercise it on a dedicated server, and the client smoke checks its synced
values and click path. For tools that own loading, `ScreenReader.read` and `ScreenBinder.bind`
also accept an explicit `WidgetTypes` registry; use the same registry for both.

### Interactive instances

Use `WidgetRenderers.GLOBAL.registerInteractive(typeId, MyWidget::new)` to register a
`WidgetFactory`. It receives a `WidgetAccess` and returns a **fresh `WidgetInstance`** per element
per screen. Keep draft text, cursor/selection, drag previews and other local state in that instance.
Do not keep editing state in a static renderer. The existing `register` method remains available
for stateless drawing.

`WidgetAccess` exposes the element, live absolute bounds, server-confirmed value and maximum,
`pending()`, and `submit(action, payload)`. `WidgetInstance` implements rendering plus optional
mouse, scroll, key, character, tick, focus and lifecycle methods. Its click result is:

| Result | Meaning |
|---|---|
| `PASS` | Try the element's ordinary recipe/click behavior and underlying screen handling |
| `CONSUME` | Handle this click; focus the instance if `focusable()` is true |
| `CAPTURE` | Also receive dragging and release for this mouse button outside the widget bounds |

Only a captured press receives mouse drag/release callbacks. Releasing an inventory drag over a
widget leaves the release with the inventory, regardless of which widget is under the pointer.

Tab and Shift+Tab move between visible focusable custom widgets in document order. While one is
focused, inventory shortcuts are consumed even when its `keyPressed` returns false, so typing E,
Q or a number cannot close the screen or move a slot. `charTyped` is separate from `keyPressed`.
Escape first cancels capture; otherwise it is offered to the focused instance. If unhandled, it
clears focus. With no focused instance, the screen's normal back/close behavior applies.

Hiding a widget cancels capture, removes focus and calls `visibilityChanged(false)`. A cancelled
drag does not receive a committing release, and that release cannot leak into an inventory slot.
Resize calls `cancelInteraction()` on a captured instance and `resized()` on all instances; the
instances and keyboard focus survive. Keep text drafts in `resized()` and use `visibilityChanged`
to decide whether to discard drafts on a view change. `removed()` runs once per instance when
the screen is removed. Balance any widget-owned resources there.
If another screen (such as a recipe browser) returns to the same `MachineScreen` object, fresh
widget instances are created on reinitialization. Ordinary resizing keeps the existing instances.

The companion `NameEditor` adapts Minecraft's `EditBox`, including clipboard shortcuts, cursor
movement, selection, drag selection, a 32-character limit, and Apply/Cancel. It retains a dirty
draft while server values change, and only replaces it on successful submission or explicit
cancellation. `ReserveSlider` previews while dragging and sends one update on release; it also
supports scroll, arrow keys, Shift for ten-point steps, Home and End.

### Typed actions and acknowledgments

Define an action on the common side and add it to the third argument of `WidgetType`. Only actions
in that type's list can be invoked by its elements. A handler receives the authenticated server
player, menu and custom element; return false to reject. Check permissions, numeric bounds,
allowed options and any element-specific property **before** mutating the machine.

```java
var setLimit = new WidgetAction<Integer>(ResourceLocation.parse("example:set_limit"),
    StreamCodec.of((buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
    5, (context, value) -> {
        if (value < 0 || value > 100) return false;
        if (!(context.menu().blockEntity instanceof PressBlockEntity press)) return false;
        press.setLimit(value);
        return true;
    });

WidgetTypes.GLOBAL.register(ResourceLocation.parse("example:limit_slider"),
    new WidgetType(Set.of(Kind.WHOLE), widget -> {
        if (widget.common().value().isEmpty())
            throw new IllegalArgumentException("Limit slider needs value");
    }, List.of(setLimit)));
```

The relevant imports are `gui.WidgetAction`, `gui.WidgetType`, `gui.WidgetTypes`,
`gui.load.BoundValue.Kind`, `net.minecraft.network.codec.StreamCodec`, and the ordinary Java
collections. Codecs use `RegistryFriendlyByteBuf`, so structured records and registry-aware
values are supported. Register matching action IDs and codecs on both sides. Each action declares
a maximum encoded size of 1–4096 bytes. The envelope is bounded before the mod codec runs, the
encoder uses a bounded buffer, and trailing bytes are refused. **Codecs must additionally bound
their own string and collection lengths before allocating.**

The client instance calls `access.submit(setLimit, previewValue)`. False means the widget is
hidden, another request is pending, an earlier result still needs delivery, or encoding failed.
Codec runtime exceptions (including buffer overflow) are logged and return false without sending
a packet or marking the widget pending. Do not submit every render frame. For sliders, commit on
release; throttle continuous controls yourself.

The server checks menu ownership, interaction distance, visibility, element type, allowed action,
menu-session token and increasing request number before dispatch. At most eight typed actions
per menu per server tick are processed; excess edits receive an immediate rejection using the last
sent values, without invoking their codec, getters or handler. Replayed requests are dropped. Handler
exceptions are logged and rejected, but MMDLib cannot undo a handler's partial mutations.

The response includes accept/reject and fresh authoritative `value`/`max` snapshots after the
handler (or the last sent values for rate-limited edits). They are applied before
`WidgetInstance.actionResult(boolean)` runs and update the server's delta baseline. The next ordinary
snapshot also refreshes those values, so a late acknowledgment ignored after timeout cannot leave
the client stale. Ordinary snapshot sync continues for every viewer, including changes to other
elements. Preserve a rejected text
draft and show feedback so the player can correct it. Requests are limited to one in flight per
widget. After 100 client ticks without a matching response, the control is unlocked and receives
`actionResult(false)` to report an **unconfirmed** edit; that does not prove the server rolled back
or never applied the edit. The timer follows the player's open menu even while another screen
covers it; result callbacks are delivered when the machine screen resumes. Responses for old
sessions or obsolete requests are ignored.

The companion Settings view demonstrates all three cases: a named machine, an energy-reserve
slider that holds processing below the chosen charge percentage, and a Run/Pause selector whose
payload contains both lane index and state. The server restricts that selector to the lane named
in its XML property. These settings persist with the machine. The graphical smoke run checks
focus, typing, cancellation, resizing, view changes, release outside bounds and server rejection.

The expanded menu protocol is version **9**, including a session token in menu-opening data.
Client and server must use matching MMDLib builds.

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
        <!-- Redstone control is the mod's own: MMDLib ships no handler or keys for it. -->
        <radio-group label="screen.example.redstone" value="#getRedstoneMode" on-click="example:set_redstone_mode">
          <radio text="screen.example.redstone.ignore"/>
          <radio text="screen.example.redstone.high"/>
          <radio text="screen.example.redstone.low"/>
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

The standard player inventory is added at the bottom automatically: three rows of nine,
a 4 px gap, then the hotbar. A column on the right contains helmet, chestplate, leggings and
boots, with the offhand slot beside the hotbar. Empty equipment slots show vanilla icons;
armor uses NeoForge's equipment checks, a one-item limit, and vanilla binding-curse protection.
The complete inventory panel is 184 by 94 GUI pixels, including its toolbar row. There is no
inventory label: the toolbar shares the helmet's row, directly above the three storage rows.
Write `<player-inventory/>` only to place it somewhere else, or set `inventory="none"` on
`<screen>` to leave out the inventory and equipment together.

Shift-clicking equipment moves it into the ordinary inventory/hotbar. Machine outputs also
go there; they do not automatically equip armor or fill the offhand. Shift-clicking ordinary
inventory slots keeps the machine's `screenDestinations` routing. JEI recipe transfer uses
only the ordinary inventory and hotbar, leaving equipped items alone.

Optional `<inventory-controls>` goes after the main body and before tabs/popouts. Its button
children form a right-aligned row above the player inventory (for example manual pause
toggles). Automation's reusable `InventoryColorButtonBar` shares that row on the left, with
scrollable overflow. Both sides align with the nine-column storage grid, leaving the equipment
column clear. Flat controls fit in the inventory's existing top row; taller controls add only
the extra height they need.
Use ordinary button declarations inside it; no spacer or coordinates are needed. This element
requires a player inventory and is available to every screen, not just companion machines.

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
  - The area measures the active view and fills the available width in vertical layouts.
    Switching views resizes and recentres the panel, moving the inventory, toolbar and drawers
    with it. Hidden settings do not reserve empty space; menu slot identities stay unchanged.
  - A view's `title` is drawn at its top, so the player can always tell which view is showing.
  - The shown view is remembered per screen file for the session.
  - Slots in a hidden view exist on the server menu at all times, but are hidden and refuse
    clicks and shift-click transfers.
- **Button tooltips are required.** A `button` must have a `tooltip`, and the loader rejects a
  screen file without one. The tooltip says what the button does or shows (for example
  "Side configuration: choose which faces accept and output each slot group, tank and energy"),
  because icon buttons alone were found unclear.
- **Stateful icon buttons:** `icons` is a whitespace-separated list of sprite IDs selected by a
  boolean (`false` = 0, `true` = 1) or whole-number `value`. Optional `value-labels` supplies
  translated names for `{value}` in the tooltip. When both lists are present their lengths must
  match. Missing or out-of-range values display the first state. These buttons keep the standard
  20×20 icon-button size by default. Set `flat="true"` for a 14×14 control with a 12×12
  icon, a uniform one-pixel dark border and a flat grey background that lightens on hover.
  Flat buttons do not take text captions. An `hbox` with a
  leading `spacer` places them against its right edge.

  ```xml
  <button value="#getPaused" on-click="example:toggle_pause"
          icons="mmdlib:icon_pause mmdlib:icon_resume"
          value-labels="screen.example.running screen.example.paused"
          tooltip="screen.example.pause.tip"/>
  ```

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

Values name Java getters, not storage handles. The `energy-gauge` convenience widget defaults
to `#getEnergy` and `#getEnergyCapacity`; the block entity must expose these public getters,
or the screen must override `value` and `max` to name its own.

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
| `energy-gauge` | Reusable slim gauge with thin, lightly glossy red charge bars | Defaults: vertical, `length="54"`, `color="#E64B38"`, `value="#getEnergy"`, `max="#getEnergyCapacity"`; all can be overridden |
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

### Reusable energy gauge

Any screen can use the standard energy gauge:

```xml
<energy-gauge id="energy"/>
```

It is 18 px wide and 54 px tall, fills from the bottom, and shows
`{value} / {max} FE` on hover using `screen.mmdlib.energy`. It shares the fluid gauge's thin casing
and medium-grey empty interior, with no terminal or scale markings. Thin, lightly glossy red charge bars
sit inside; the last charged bar fills partially to track the level. Red stays red at every
charge level. `EnergyGaugeRenderer` draws the bars over the shared gauge background.
It uses the same binding validation and synchronization as other numeric
widgets. Multiple buffers can use separate ids and getters:

```xml
<energy-gauge id="reserve" length="4s" color="#D23838"
              value="#getReserveEnergy" max="#getReserveCapacity"
              tooltip="screen.example.reserve" tooltip-fallback="Reserve: {value} / {max} FE"/>
```

`orientation="horizontal"` fills from left to right. `length="fill"`
follows the ordinary gauge rules; explicit lengths must be at least 8 px. A custom
`tooltip` replaces the built-in tooltip; its fallback is optional.

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
  - A pop-out with both `text` and `value` shows its fixed explanation first, followed by the
    live component or component list. An info drawer can therefore include current warnings.
- **Edge tabs are author-placed.** Each pop-out tab
  takes `edge` (`left` or `right`) and `icon`, so a screen author spreads them across the
  two sides instead of crowding one. Each pop-out kind has a default icon
  (`mmdlib:icon_info`, `mmdlib:icon_warning`, `mmdlib:icon_error`; see
  [GUI sprites](gui-sprites.md)), which `icon` replaces. Tabs on the same edge stack in document order, below any
  `<side-tab>` there. A screen whose tabs do not fit the panel height on an edge fails at
  load, naming the screen file and edge.
  Pop-out tabs are 22×24 rectangles, coloured by kind: yellow information, amber warning,
  red error. Clicking one opens a matching drawer outward from that edge, with the icon
  retained on a darker rail as the close control. Its body is up to 150 pixels wide and
  fits its text. Info and inventory drawers share a 4-pixel inset from the main panel's top
  and bottom, and stop earlier if the screen or an overlay limits the space. The side-config
  drawer shares the top inset but can grow below the main panel to fit its filter controls. Text wraps to the available width; scroll inside
  to read longer content. Opening another drawer on the same edge replaces the open drawer;
  tabs remain visible and clickable on the rail.

- **The screen is display only.** Behaviour belongs to the block entity's own code, which the
  screen is wired to:
  - `<slot index="N">` is slot N of the menu, exactly as numbered. What the slot holds and
    what it accepts (including which items an upgrade slot takes) is decided by the block
    entity's storage code.
  - Shift-click routing is the block entity's menu code, as in vanilla. A furnace, for example,
    sends a smeltable item to its ingredient slot and other fuel to its fuel slot. MMDLib only
    keeps slots in hidden views out of that routing.

Drawer choices are remembered across MMDLib machine screens for the current connection.
The left drawer (info, backpack or Curios) and right automation drawer are independent.
Backpack selection uses its equipment slot address, and automation selection uses its resource
group ID. Missing features are skipped; explicit closes are remembered. Joining another world
or server resets these preferences. Filter-editor drafts are not carried between machines.

## Equipped storage drawer (optional Curios integration)

When Curios is installed, machine screens with a player inventory automatically show a
bottom-left tab for an equipped item exposing NeoForge's `Capabilities.ItemHandler.ITEM`.
The tab renders the actual item, including its model/tint components and custom name in
the tooltip. No screen XML changes or backpack-specific API are needed.

The server config `inventory.container_slots` selects functional Curios slots to inspect;
the defaults are `back` and `backpack`. Cosmetic slots are excluded. Empty slots, ordinary
items and zero-slot capabilities show no tab. Curios remains optional, and neither Curios
nor any backpack mod is bundled with MMDLib.

Click the tab to open storage on the left. Left/right clicks take a stack/half stack or
insert the carried stack/one item. Shift-click a storage cell to move items into the player
inventory; **while storage is open, shift-clicking the ordinary player inventory stores
items in the selected container**. Machine-output and equipment transfers keep their usual
behavior. Drag with the left button to divide the carried stack evenly, or with the right
button to place one item in each visited slot. Drags starting in the backpack can include ordinary
player inventory slots. Gestures starting in the normal inventory retain Minecraft's usual drag
and double-click behavior. Press a configured hotbar key over a backpack slot to exchange its contents with that
hotbar slot. Double-click a backpack stack to collect matching items from the entire selected backpack
and ordinary player inventory, up to the carried item's stack limit. Item components,
insertion/extraction restrictions and slot limits are respected. All transfers use only the
container's insert/extract capability. A container may redirect insertions (for example,
merging into existing stacks instead of the hovered empty slots), including during drags
and hotbar swaps. Drag previews highlight backpack targets; final counts come from the container
after release. The drawer does not bypass that behavior with direct slot setters.

Backpack-specific upgrades/settings/crafting screens are not provided. Ordinary clicks with
different carried items do not swap stacks; use a hotbar key for an explicit swap.

Storage uses six slots per row inside a drawer filling the panel height between the shared
4-pixel top and bottom insets, with the same tinted frame and
darker icon rail as the other drawers. Large inventories are paged with the arrows or mouse wheel. Click the header to cycle
between eligible equipped containers. The main panel stays stationary, opening storage
closes a left information drawer, and the right automation drawer remains independent.
If the window has insufficient space, the tab explains how to make room. Unequipping,
replacing or resizing a container closes the drawer; requests for an old container or page are
rejected by the server. Live item-count or icon updates do not cancel drags or block clicks:
transfers use the current server contents, filters and limits. Both equipment drawers share one
menu-local Curios discovery per tick, refreshed before and after item actions.
The drawer respects the capability's insertion/extraction filters and limits.

Some items expose storage only after being initialized through their own normal UI. Such
items show the tab only after initialization. The library does not initialize third-party
storage or interpret its private data.

For development, `runCompanionClient` and `runCompanionClientSmoke` include Curios,
Sophisticated Backpacks and Sophisticated Core as runtime fixtures. Use
`-PwithoutBackpacks` to omit them. These dependencies do not enter either published jar.
Server GameTests run without these fixtures because Curios login sync requires a negotiated
client connection; the client smoke test covers real backpack discovery and networked transfers.

## Curios equipment drawer

With Curios installed, screens that show player inventory also gain a **Curios** tab on
the left. It sits above the backpack tab when an equipped container is available. The
full-height drawer lists the player's visible Curios slot groups in their configured order,
using Curios' names and empty-slot icons. The **E** column contains functional equipment;
the **C** column contains cosmetic equipment where the slot type supports it. A dash means
that slot has no cosmetic counterpart. Arrows and the mouse wheel page through longer lists.

Left/right clicking equips or unequips through the actual Curios handlers. Shift-clicking
an accessory moves it into ordinary player storage. While this drawer is open, shift-clicking
player storage attempts to equip the item in a compatible **functional** slot; place cosmetic
items directly in the cosmetic column. Existing equipment is not swapped automatically.
Curios' validation, equip/unequip events and removal restrictions still apply.

Opening this drawer closes backpack storage or the left information drawer; the right
automation drawer remains independent. Escape closes the equipment drawer first. A change
to the available slot groups or counts closes the old view and invalidates stale requests;
reopening shows the updated slots. Curios stays optional, with no tab or class-loading
requirement when absent. This drawer does not change slot definitions or rendering toggles.

## Redstone face drawer

Implement `ScreenHost.screenRedstone()` to return a persistent `RedstoneControl`. Its right-side
drawer uses the inventory drawer's neighbour grid, directional badges and shared frame. Clicking
a different face selects it; clicking the selected face cycles Ignore, Read and Emit (right-click
cycles backwards). Faces have a spaced selection outline and small background-free signal numbers:
bright green incoming strength for Read, orange emitted strength for Emit, with thin glyph outlines.

- **Ignore:** no input gate and no emitted signal.
- **Read:** a single bar has a minimum handle above and a maximum below. Endpoints are inclusive,
  and dragging either past the other pushes both together. Between highlights the selected interval;
  Outside highlights its complement. The icon toggle sits to the left of the bar: inward arrows
  between boundaries mean Between, outward arrows beyond boundaries mean Outside. Equal endpoints
  simplify only the info text to For N / Except N; ranges touching 0 or 15 use a single
  above/below condition (including equality for Between). The icon stays unchanged. Processing pauses when
  the face receives a highlighted level.
  The default is Between 1�15. Dragging previews locally, release applies both endpoints atomically,
  and Escape cancels the drag. Any Read face requesting pause holds processing.
- **Emit:** click the colour swatch or sprite and source name to cycle outputs; right-click
  cycles backwards. Resource fullness entries come first in the companion, followed by each
  processor's own Processing, Paused/idle and Progress entries. A small reverse-arrows button
  before the picker toggles Revert (`15 - level`). Until a source is selected, or if that source
  no longer exists, the face emits zero even when reverted.

These controls are built in: there are no redstone upgrade items or slots. Each face saves its
interval, Between/Outside choice, stable output ID and inversion independently. Changing the
face mode retains those settings. The small yellow info box explains the selected source and
current signal. The drawer fits its content and scrolls when the viewport cannot fit it. The
old main redstone button is removed; manual pause remains above the inventory. Right drawers
are mutually exclusive, and drawer/face selection is remembered for the current connection.

### Output providers

Runtime parts opt in by implementing `api.redstone.IRedstoneOutput`. Its `redstoneOutputs()`
returns a stable, ordered list of `RedstoneOutput` descriptors. Each descriptor has:

- A stable ID unique across this machine, used for saved selections.
- Translatable name and description components, used by the picker and yellow info box.
- An RGB colour and optional GUI sprite ID, used as the picker's swatch or icon.
- A server-side `IntSupplier` whose result is clamped to 0-15.

Construct `RedstoneControl(owner, parts)` after registering the parts; it discovers only parts
implementing the interface and rejects duplicate IDs. The catalog must have the same order and
metadata on client and server. Descriptors use common-side types, without client rendering
classes. Suppliers are evaluated only on the server, once per selected source per output update.
`FeatureHost` implements the interface by collecting outputs from participating features.

`RedstoneOutput.resource(group, fullnessSupplier)` adapts an existing `ResourceGroup`, preserving
its ID, label and colour. Fullness is 0-1 and maps to `floor(fullness * 15)`. The companion measures
item groups by average slot fullness, fluid groups by amount/capacity, and energy by stored/capacity.
The resource-only `RedstoneControl(owner, groups, fullnessFunction)` constructor remains available.

Each `RecipeProcessor` contributes three independent sources, prefixed by its saved feature key:

| Source | Signal |
|---|---|
| Processing | 15 while actively processing (including the start and completion ticks), otherwise 0 |
| Paused/idle | 15 while paused, idle, blocked, energy-stalled or held by the host; otherwise 0 |
| Progress | `floor(completedTicks / recipeTicks * 15)`; retained while held/paused, 15 on completion, 0 when idle |

Use the constructor accepting a display-name `Component` to label processors clearly; the original
constructor defaults to the feature key. Multiple processors produce multiple sets of entries.
There is no aggregate machine-wide running signal.

Tick in this order on the server:

```java
redstone.readInputs();
features.tick(level, !redstone.paused() && otherProcessingGates);
// Perform any automatic transfers here.
redstone.updateOutputs();
```

Passing `false` to `FeatureHost.tick` invokes each feature's `suspended` callback instead of
processing it, so a held processor cannot retain a stale running signal. Save/load the controller
tag and return `signal(direction)` from the block's redstone output hook. The direction passed
to `signal` is Minecraft's query direction, not the outward face. Stateless hosts can call
`redstone.tick()` to read inputs and update outputs together. The companion emits weak power,
keeps transfers running while processing is held, and migrates old inventory selections and
the previous global redstone mode. Manual pause remains independent.

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
