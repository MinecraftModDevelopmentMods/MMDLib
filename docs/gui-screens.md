# GUI screens

> **Status: implemented; runtime validation is described below.** Screens load at startup,
> open through `block-right-click`, and use one generic menu with custom value packets.
> Rendering and optional JEI integration live exclusively on the client.

Machines can opt into [inventory colours, side drawers and item-provided filter editors](inventory-automation.md)
through `ScreenHost.screenAutomation()`. This adds the selectors, fixed addon/filter slots and
embedded editor without adding per-filter XML to each screen.

## Implementation map

| Piece | Where | State |
|---|---|---|
| Element declarations | `gui.ElementType`, `gui.client.ElementClient`; built-ins in `gui.load.BuiltinElements` and `gui.client.BuiltinElementClients` | Implemented |
| XML tag contracts | `gui.load.ScreenTags`, `ScreenTag`, `ScreenAttribute`; `screen.xsd` supplies built-in declarations/shared types | Implemented |
| Typed model | `gui.model`: `Screen`, `Element` (one record per XML element), `Common`, `Text`, `ValueRef` | Done |
| Tank component | `gui.model.elements.FluidTankElement`: binding, snapshots, tooltip and container interaction | Done |
| Energy component | `gui.model.elements.EnergyGaugeElement`: binding, snapshots and default tooltip | Done |
| Reading and checks | `gui.load.ScreenReader`: hardened SAX parser (no DOCTYPE, no external entities), schema validation, then the checks below | Done |
| Getter binding | `gui.load.ScreenBinder`: `#method` / `Class#method` resolved to method handles, return kinds checked per element | Done |
| Discovery | `gui.load.ScreenLoader`, called at startup; results in `gui.Screens` | Done |
| Inheritance | `gui.load.ScreenTemplates`: cross-mod bases, placeholders/fills, source-aware expansion | Done |
| Layout | `gui.layout.ScreenLayout`: positions in GUI pixels; tab fit checked at load | Done |
| Menu and opening | `gui.runtime.MachineMenu`, `ScreenRuntime`, `BlockEvents.openMenu` | Implemented |
| Value sync | `gui.runtime.ScreenNetwork`, `ScreenValues`, `ScreenSnapshots` | Implemented |
| Network registration | `gui.runtime.network.NetworkRegistry`; feature-owned codecs and handlers | Implemented |
| Client rendering | `gui.client.MachineScreen`, `GaugeRenderer`, `ScreenText` | Implemented |
| Optional JEI | `client.jei.MachineJei`, loaded only by the JEI plugin | Implemented |

Checks made at load, beyond the schema: unique `id`s and slot indexes; `shows`, `hover-shows`
and `side-config groups` name existing elements; buttons have a tooltip and text or an icon
(generic processor controls supply their own defaults); a
`-fallback` attribute has its key; `max` has a `value`; display gauges, meters and progress indicators
have both; fluid-tank components own their values and require a tank binding;
checkboxes and radio groups have a `value`; info pop-outs have text and
warning/error pop-outs a `value`; `spacer` sits directly in a box; at most one of each
player inventory/armour/offhand section, and none with `inventory="none"`; the block-entity class exists and is a
`BlockEntity`; every getter exists, is public, and returns a kind the element can show; side
tabs and pop-out tabs fit their edge.

Element-local limits are checked by the elements during both XML loading and Java screen binding:
`SlotElement.validate()` restricts indexes to 0..4095, including item, ghost and custom slots;
`SideConfigElement.validate()` permits at most 682 groups. `ScreenRuntime.validate()` checks
screen-wide transport limits and registered callback references, without element-type branches.

Built-in getter types: whole numbers (`byte` to `long`, boxes, `BigInteger`), decimals (`float`,
`double`), a declared `Number`, `boolean`, `FluidStack` (fluid gauges), `ItemStack`, `Component`
and collections of components (warning and error pop-outs). Mods can register additional types.

Screens are declared in **XML**, in keeping with MMDLib's data-driven core. Each file is loaded
at startup into a typed screen model and validated there, with the file and line number in any
error. An XSD schema, maintained alongside the model, gives authors validation and
autocompletion in their editors.

## Wiring a machine

### Registering getter value types

`gui.value.ValueTypeRegistry.GLOBAL` owns the getter and wire type catalog. Register common-side
`ValueType<T>` definitions during mod construction, before screens load and freeze the registry.
`ValueTypes` supplies the built-ins through the same API. For example:

```java
public record Reading(int amount) {}

public static final ValueType<Reading> READING = ValueType.<Reading>builder(
    ResourceLocation.parse("example:reading"), Reading.class,
    () -> StreamCodec.of(
        (buffer, value) -> buffer.writeInt(value.amount()),
        buffer -> new Reading(buffer.readInt())),
    4)
    .version(1)
    .description("a machine reading")
    .validate(value -> value.amount() >= 0)
    .immutable()
    .build();

// In the mod constructor, on both physical sides:
ValueTypeRegistry.GLOBAL.register(READING);

// On a custom Element (or pass Set.of(READING) to WidgetType):
public Set<ValueType<?>> valueKinds(WidgetTypes widgets) { return Set.of(READING); }
```

Import `ValueType`, `ValueTypeRegistry` and `ValueTypes` from `gui.value`; codecs use
`StreamCodec<RegistryFriendlyByteBuf, T>`. An XML getter returning `Reading` now binds without
changes to the loader, menu or screen. Renderers receive the decoded value through
`access.value(element, false)`. `valueKinds` and `maximumKinds` contain registered descriptors;
the former `BoundValue.Kind` enum has been removed.

Each type owns Java aliases (`javaTypes`), canonical normalization (`normalize`), runtime
validation, copying, equality and a bounded codec. Exact Java registrations win, followed by the
most specific registered superclass/interface; ambiguous matches and duplicate IDs/Java bindings
are rejected. Use `subtypes(false)` for exact Java bindings only (as the built-in declared `Number`
type does). The declared getter's descriptor normalizes and snapshots its result. Null means
unavailable and requires no type registration. Standard whole-number bindings normalize to
`BigInteger`; decimals normalize to finite `Double` values.

Snapshots default to a codec round trip to detach mutable data. Override `copy` only with a deep
copy, or use `immutable()` for genuinely immutable values. Use `equality` when the value's ordinary
`equals` does not compare contents. Copy results still pass validation; transport always enforces
the byte limit. Built-in item/fluid copies do not require serialization or registry access.
The codec supplier is lazy so registration does not initialize Minecraft registries prematurely.
The maximum is 1 MiB per value; declare the smallest positive limit your codec needs.
Built-in text collections additionally allow at most 4096 components.

Deltas and action-result corrections use the same registered codecs. Each non-null wire value
carries its namespaced type ID, positive schema version and bounded payload. Unknown IDs, version
mismatches, excess bytes, trailing bytes and invalid decoded values are rejected. Type declarations
enter the menu fingerprint in deterministic ID order. Bump the type's version when changing its
codec or semantics, and register matching definitions on the client and server. The core screen
protocol is now 19. Internal typed-state envelopes use a wire-only registration, excluded from
getter binding.

`gui.model` holds the open `Element` interface, `Screen`, and shared data types such as
`Common`, `Text`, `ValueRef`, `Interaction` and `ResourceBinding`.

`gui.model.containers` holds composable containers and layout elements: `BoxElement`,
`GroupElement`, `ColouredGroupElement`, `ContextElement`, `ViewElement`, `InventoryElement`,
`InventoryControlsElement`, `RadioGroupElement`, `SideTabElement`, and the placement elements
`SpacerElement`, `PlayerInventoryElement`, `PlayerArmourElement` and `PlayerOffhandElement`.
The player sections share `PlayerSlotsElement`. `BoxElement` represents both `hbox` and `vbox`.

`gui.model.elements` holds leaf controls and displays, including slots, buttons, sprites,
fluid/energy gauges and popouts. `gui.layout` holds geometry and orchestration helpers such as
`Rect`, `TextMeasure`, `ElementLayouts`, `LayoutContext` and `ScreenLayout`; these are not
elements in the screen model. Java imports use these packages; XML names remain unchanged.

Every built-in element is a standalone `*Element` class: for example, `ButtonElement`,
`BoxElement`, `CustomElement`, `ProgressElement` and `PopoutElement`. `Element` contains only
the common contract and sizing constants. Former nested references such as `Element.Button`
become `ButtonElement`; shared enums, `ViewRef` and `ElementSnapshot` live in `gui.model`.
The progress direction enum is `ProgressDirection`. XML names and attributes stay the same.

Built-in elements own their measurement and child placement too. `ScreenLayout` composes the
outer panel, contributed footers and registered tabs, then delegates to `measure` and `childLayout`.
`stretchX`, `stretchY` and `stretchToPanel` opt into the available space; `spacer` opts into
leftover main-axis space in a box. `prepareLayout(LayoutContext)` supplies context-dependent
metadata before measurement, such as reserving the back-button header in alternate views.

`Element.layoutContribution(context)` optionally returns a fresh `LayoutContribution` for each
layout. The context supplies the screen, measurement context and resolved drawer definitions.
Plans run in element traversal order. They can claim body roots for custom placement, request
centred footer space, and place their elements through generic `Panel` operations in `finish`.
`shiftBelow` moves already placed roots and pending footer allocations; `grow` increases panel
height. `beforeOffsets` observes final placement after every plan finishes; all elements then receive
authored offsets. `resolveOffsets` can align associated
elements, and `regions()` publishes final named rectangles. Duplicate root claims and region IDs
are rejected. Multiple contributions compose without a registry or element-type checks in
`ScreenLayout`; keep mutable plan state out of the element definition.

Consumers use `layout.region(id)`. Player inventory publishes `PlayerInventoryElement.INVENTORY`
and `TOOLBAR`; these replace `layout.inventory()` and `layout.inventoryColors()`. Default slot
geometry and dimensions live in `PlayerInventoryElement.slot`, `WIDTH` and `HEIGHT`, replacing
the former `ScreenLayout.playerSlot` and `INVENTORY_*` aliases. Item coordinates come from
`SlotElement.itemPosition(bounds)`, including custom item sizes. Default native item, ghost and
player-slot factories honor this hook for direct `SlotElement` implementations as well as subclasses.
Drawer tab defaults live in
`DrawerPlacement`; drawer content insets live in `SideDrawerLayout`. `AutomationInventoryElement`
owns the addon/filter slot preset used by automation menus and layout. `InventoryElement` only
provides the reusable named inventory/grid; player sections use their three dedicated containers.

`ScreenHost.screenAutomationInventory(group)` supplies a fresh `AutomationInventoryElement` per
menu/group. Its slot-factory constructor accepts `IntFunction<? extends SlotElement>`; the factory
runs once per local index and retains the returned element identities. Defaults are extensible
`AutomationSlotElement`s with `AUXILIARY` roles. Custom slots may override the native factory,
`itemPosition` and `role`, including direct `SlotElement` implementations. Keep all eight real,
non-player slots and their local indexes: addon/filter storage semantics remain feature-owned.
`AutomationMenu` also accepts an explicit inventory factory for custom component construction.

Override `drawerSlotBounds`, `filterToggleBounds` or `contentHeight` on the automation inventory to
customize presentation. Native creation/repositioning, drawn frames, filter-toggle hit boxes and
drawer height consume that same inventory instance; item coordinates always use the slot's
`itemPosition`. The static `AutomationLayout` helpers describe the default preset. Keep definitions
consistent on client and server. These hooks use the existing menu component and renderer paths.


`Element` and `SlotElement` are open interfaces: mods can implement them in their own packages
and include those instances in a Java-built `Screen`. A custom element implements `common()`
and `measure(TextMeasure)`; containers also expose `children()` and `childLayout(bounds, text)`
with child rectangles relative to the container. Existing `validate`, `snapshot`, `interactive`,
`interact` and tooltip hooks apply to these elements too. All concrete built-in elements are
inheritable classes, including containers, item/ghost slots and `FilterControl` implementations.
Their existing constructors and accessor names remain available; configuration fields are final
and child collections are copied. Mods can extend a built-in and override only the relevant hooks.
Subclasses inherit their base type's renderer and behavior unless a more specific registration exists.
The converted classes retain structural equality within an exact runtime type. If a subclass adds
configuration fields, include them in `equals`, `hashCode` and a deterministic `toString`; screen
fingerprints include concrete element class names and model text. Keep per-screen mutable state in
registered behaviors. Non-element data records such as slot appearances and sprite layers remain records.

Elements also own value presentation, independently of synchronization:

- `displayValue(value)` converts a synchronized snapshot for display; its default returns the value.
- `displayText(value, detailed)` formats current or maximum values. The default formats the converted
  value numerically, abbreviating large integers unless detailed output is requested.
- `fillRatio(value, maximum)` supplies a finite fraction between zero and one. Its default computes
  a clamped numeric ratio of the converted values, preserving large integer/decimal precision.

Fluid tanks and generic fluid gauges convert `FluidStack` snapshots into amounts themselves.
Shared tooltips call `displayText`; gauge, meter, sprite and widget rendering call `fillRatio`.
No presentation registry is needed. Override conversion for custom value objects or override the
formatting/fill hook directly. These hooks receive synchronized snapshots and must not mutate them.
The numeric utility `DisplayValues` contains only reusable formatting/arithmetic defaults.

`GaugeElement.fill(value)` and `FluidTankElement.fill(value)` select a common-side `GaugeFill`.
Override these methods to return `GaugeFill.solid(rgb)`, `GaugeFill.fluid(stack)`, or
`GaugeFill.fluid(stack, argbTint)` while inheriting the existing renderer. Selection is independent
of `fillRatio`: a numeric snapshot can select a fluid texture, and a tank can select a solid colour.
Default gauges use their configured solid colour first, otherwise the snapshot fluid, otherwise
grey; tanks default to the snapshot fluid. Fluid fills copy their contents on construction and
access. The renderer resolves textures/default tint and draws the selected fill; it does not
interpret raw snapshot types. No additional registration is needed.

```java
public class ReserveTankElement extends FluidTankElement {
    public ReserveTankElement(Common common, int tank) {
        super(common, tank, Orientation.VERTICAL, 54);
    }

    @Override
    public double fillRatio(Object value, Object maximum) {
        // For example, visualize the square root of the normal fraction.
        return Math.sqrt(super.fillRatio(value, maximum));
    }

    @Override
    public GaugeFill fill(Object value) {
        return GaugeFill.solid(0x5599cc);
    }
}
```

This subclass retains the tank's snapshots, tooltip, container interaction and registered renderer.
Override `appendTooltip` to add custom rows, using `displayText` for any values you include.

For a complete worked example, see [a custom fluid element](custom-fluid-element.md): mod-owned
storage and persistence, server clicks, client rendering, common/XML registration and screen resources.

For a new element, declare its common definition once and attach its client implementation
separately. `ElementType<E>` is an immutable declaration, not another registry. Its optional XML
contracts register into `ScreenTags`; `ElementClient<E>` forwards drawing/input attachments into
`ElementRenderers` and `ElementBehaviors`.

```java
// Common code: MyFluidTankElement(Common, int) is your mod's element class.
public static final ElementType<MyFluidTankElement> MY_TANK =
    ElementType.xml(
        MyFluidTankElement.class,
        new QName("https://example.org/gui", "fluid-tank"),
        xml -> new MyFluidTankElement(xml.common(), xml.integer("tank")))
    .attribute("tank", ScreenAttribute.required("xs:nonNegativeInteger"))
    .build();

// Mod construction, on both physical sides:
MY_TANK.register();
```

```java
// Client setup only; keep this code in a client class.
ElementClient.of(MY_TANK)
    .renderer(MyTankRenderer::render)
    .register();
```

Imports: `gui.ElementType`, `gui.client.ElementClient`, `gui.load.ScreenAttribute`, and
`javax.xml.namespace.QName`. Add `.behavior(MyTankBehavior::new)` when custom client input is needed;
ordinary tank clicks already use inherited/default behavior. Omitted render passes and behaviors
install nothing and preserve the existing most-specific inheritance rules. Factories still create
fresh behavior instances per screen. Missing drawing is checked when the screen is validated.

Use `ElementType.of(MyElement.class)` for a Java-only element or an abstract/interface family.
`ElementType.xml(name, existingScreenTag)` wraps an existing tag contract. `withXml(name, tag)` returns
a new immutable declaration with another XML alias, leaving the original unchanged. The builder
exposes `groups(...)`, `children(group, min, max)` and `children(QName, min, max)` using the existing
schema contracts. Document roots, data children and template directives remain ordinary `ScreenTag`
registrations because they are not UI elements.

Client attachments support `.overlay(...)`, `.foreground(...)`, `.top(...)` and
`.renderer(pass, clip, renderer)` for explicit clipping. Register the renderer object itself to retain
its `validate` hook. Duplicate attachments/registrations fail; intentional replacements still use the
existing registries' `replace` APIs. Common registration accepts an explicit `ScreenTags`, and client
registration accepts explicit `ElementRenderers`/`ElementBehaviors` for isolated tooling or tests.

The common descriptor has no client-class references: use `ElementClient.of(MY_TANK)`, not a common
`MY_TANK.client()` method. Built-ins follow the same split. `BuiltinElements` owns their shared common
declarations and XML parsers; `BuiltinElementClients` pairs their drawing and input attachments.
Existing XML schemas, aliases and lower-level registration APIs remain supported. The legacy `gauge`
tag can construct two unrelated element classes, so its XML declaration is separate from the typed
`GAUGE`/`FLUID_TANK` client attachments.

Register drawing during client setup with
`ElementRenderers.GLOBAL.register(MyElement.class, (element, context) -> { ... })`.
Built-ins use this same registry: `MachineScreen` has no element-specific drawing switch.
The context contains GUI bounds, synchronized values, mouse/frame inputs, and read-only
`context.access()` presentation services (font/text, element lookup, parent, bounds, typed
element state and displayed slot contents). It exposes neither the machine nor the menu. Treat synchronized
values as immutable; slot contents/hints supplied by the access object are copies. No client
classes are needed in the common element implementation.

An exact registration wins; otherwise the most specific registered superclass/interface wins.
Thus custom `SlotElement` and `ButtonControl` implementations inherit shared drawing, and sprite
subclasses inherit `SpriteRenderer`. Register the concrete mod class to override its inherited
drawing. Unrelated matching interfaces are an error until an exact or more specific registration
resolves the ambiguity; selection never depends on registration order. Duplicate registrations
are rejected. Use `replace` to intentionally replace an existing registration for that exact type.
Missing background renderers and ambiguous matches are checked when a screen is created.
A renderer can also override `Renderer.validate(element)` to check client dependencies; the
resolved registration for each pass owns that check, including inherited and replaced registrations.
Throw `IllegalArgumentException` with a useful message; validation adds the element's original XML
source and line. The built-in custom-widget adapter checks its widget factory through this hook.
`MachineScreen` and `ElementFragment` share this validation for every rendering pass. Fragments
validate before creating behaviors; their host can override `source(element)` for XML diagnostics
(the default is the host ID). `ElementRenderers` also validates loaded screens at load completion;
`WidgetRenderers` only freezes its own factory registrations.

```java
// In client setup; MyElement remains a common-side model class.
ElementRenderers.GLOBAL.register(MyElement.class, (element, context) -> {
    var r = context.bounds();
    context.graphics().fill(r.x(), r.y(), r.right(), r.bottom(), 0xff228855);
});
```

There are four passes, each with independent registration and inheritance:

| Registration | Draw order | Default clipping |
|---|---|---|
| `register` | Element backgrounds in tree order | Element bounds |
| `registerOverlay` | After all element backgrounds, for outlines | Element bounds |
| `registerForeground` | After vanilla container rendering, for popout bodies | Screen viewport |
| `registerTop` | Above foreground content, for tabs | Element bounds |

Optional passes can be omitted. Pure layout containers explicitly register no-op backgrounds.
`register(pass, type, clip, renderer)` supports an explicit clip policy; side-config highlights,
for example, use viewport clipping because their target is another element. Each call balances
the registry's pose/scissor scope and resets drawing colour; renderers must balance any additional
pose/scissor changes of their own. `context.elapsedTicks()` counts game ticks since the screen's
first frame, including partial ticks, for local animation. Renderers may be shared across screens;
keep per-screen editing/open/scroll state in the appropriate client instance.

Custom slots inherit shared slot rendering and the default vanilla adapter, or override
`createSlot` for their own adapter. Ghost behavior follows `ghost()`, with overridable
`selection`, `read` and `accept` hooks. XML-defined extensions continue to use the registered
`<widget type="...">` / `WidgetTypes` API.

Client behavior is registered independently from drawing:

```java
ElementBehaviors.GLOBAL.register(MyElement.class, MyElementBehavior::new);
```

The factory receives `(element, ElementAccess)` and must create a fresh `ElementBehavior` for
each element in each open screen. Registration uses the same exact-type/most-specific-base
rules as renderers, including explicit `replace`. Keep focus, drafts, drag state and local
selection in that instance. Unregistered elements use `DefaultElementBehavior`, which handles
common interactions, recipe links and click callbacks. Extend it and override its protected
`click` hook to retain those defaults, or implement `ElementBehavior` directly for full control.

`ElementInput` routes mouse, keyboard, focus, capture, visibility, resize and removal for every
element. Return `CAPTURE` to receive drag/release outside the element; hiding or resizing cancels
the capture without committing. `ElementAccess` supplies current bounds, synchronized values,
presentation services and typed action submission. It exposes no block entity or raw menu.
Navigation, inline side configuration, radio controls and slot behavior use this same registry.
Real inventory gestures still pass through vanilla slots. Existing `WidgetInstance` factories
are adapted to the same lifecycle, preserving the XML custom-widget API.

Screens and fragments both call `ElementInput.click(x, y, button)` after updating visibility.
It checks the viewport and visits visible hits in reverse traversal order; `PASS` continues to
the next hit. Unhandled presses clear focus and permit native fallback. An existing capture
consumes further presses even outside the viewport until its release or cancellation. Hosts only
handle overlays first and native fallback afterward. Scrolling uses the same viewport predicate.

Hover tracking also belongs to `ElementInput`. Screens and fragments select the last visible
element under the pointer in traversal order, matching their topmost tooltip target. A fragment
also requires the pointer to be inside its viewport. `hoverChanged(true/false)` runs only when the
target changes, with the previous target's leave before the next target's enter. Repeated frames
do not repeat callbacks; geometry, visibility and clipping changes recheck a stationary pointer.
Removal sends the final leave before disposing behaviors. Focus/capture cancellation alone does
not change hover, and a captured drag's release still belongs to its original control.

Hosts sample `hover(x, y)` each frame and forward actual movement through `mouseMoved(x, y)`.
`ElementFragment.Host.hoverChanged` receives the same transitions; machine-backed hosts use
`ElementHover.dispatch` to supply the menu context for declarative `on-hover` callbacks. The filter
drawer provides this bridge. `ElementHover.preview` resolves `hover-shows` for both screens and
fragments, with fragment previews retaining viewport clipping. No additional registry is needed.

Radio options share the `OptionSelection` parent behavior capability for drawing and input.
`selected(option)` supplies the selected state; `activate(option, button, modifiers)` owns the
action and returns its input result. `RadioGroupBehavior` resolves membership by element identity,
reads the synchronized group index and submits the group's ordinary click action. It does not
predict a selection before the server updates the value. An option's explicit click bindings
take precedence over parent activation. Mods can register a behavior implementing `OptionSelection`
for their own group types, using their own state and typed actions; radio rendering/input requires
no concrete parent class. Return `PASS` for unknown options and `CONSUME` for handled options,
including when a pending action prevents another submission.

`ElementBehavior` contains input, lifecycle and tooltip hooks only. Specialized presentation is
optional: `ValueLabelProvider` supplies a tooltip value label, `SideConfigPresentation` supplies
the inline side control's selection/modes, and `WidgetRenderer` supplies custom-widget drawing.
Renderers request these through `context.access().capability(element, Contract.class)`.
Mods can define their own interfaces; a behavior implements them directly, or an adapter implements
`BehaviorCapabilities` to forward requests. The existing widget adapter forwards these contracts.

Tooltips belong to the element. Override `Element.appendTooltip(TooltipContext, List<Component>)`
to supply its rows. The default calls `context.appendDefaults(rows)`, which includes server rows,
the authored tooltip (or Shift variant), value substitutions, the Shift hint and `valueTooltip`.
Call it before adding rows to retain those defaults, or omit it to replace them. Styled components
retain their formatting when split into multiple lines.

Containers can override `appendChildTooltip` to add context when a descendant is hovered:

```java
@Override
public void appendChildTooltip(TooltipContext context, List<Component> rows) {
    rows.add(context.text(new Text("screen.example.input_group", Optional.of("Inputs"))));
}
```

Composition visits the hovered element first, then its parent, then each ancestor to the root.
At each level the element hook runs before the corresponding registered behavior hook.
`ElementBehavior.appendTooltip` and `appendChildTooltip` support client-local state; the default
own-tooltip hook also calls existing `tooltip(mouseX, mouseY)` overrides. A parent's ordinary
tooltip is **not** repeated automatically; `appendChildTooltip` does nothing unless overridden.

`TooltipContext.target()` always identifies the hovered element; `element()` identifies the
current contributor, and `child()` is the immediate child along the path (empty at the target).
It exposes mouse coordinates, modifiers, synchronized values and translated text, without machine
storage. `serverLines()` returns the contributor's own server rows; a parent can append these
explicitly in its child hook. `appendDefaults` never invokes other hooks or walks parents.

`ElementTooltips` composes the rows for machine screens, JEI recipe areas and embedded fragments.
The screen draws the result once. Filled native slots retain vanilla item details and append the
same composed rows; empty slots use the ordinary element tooltip. Server-dependent content still
comes from `Element.tooltip(ScreenHost, carried)`, sampled per viewer by `TooltipMenuComponent`
through the registered `mmdlib:tooltips` component field. Its sparse map permits up to 4096 element
entries and 4096 rows per entry, within a total 65536-byte encoded field bound. Fragments without
a server tooltip source return no server rows. Core screen protocol 20 requires matching versions.

Group captions and highlighting use that same mechanism. A behavior can implement
`GroupLabelProvider.groupLabel()` to supply an optional `Text`, and
`HighlightPresentation.highlight(graphics, bounds, request)` to draw its own outline or another
shape. Register the behavior with `ElementBehaviors.GLOBAL` as above; the element need not inherit
from a built-in group. Both capabilities also work through `BehaviorCapabilities` adapters.

Consumers call `ElementRenderAccess.groupLabel(element)` and
`highlight(graphics, element, ElementHighlight)`. `MachineScreen.presentation()` exposes the same
read-only services to drawers and integrations. Without a caption provider, the label falls back
to the element's common tooltip; an explicit empty caption remains empty. Without a highlight
provider, a rectangular outline uses the request's colour and outset. Highlights skip hidden
targets and run with viewport clipping and isolated pose/colour state.

`ElementHighlight` distinguishes persistent `DECORATION`, `EMPHASIS` of an existing decoration,
and a control's `SELECTION` marker. Factory methods supply the usual zero/one-pixel outsets;
providers may reuse `request.outline(bounds)` or `request.render(graphics, bounds)`.
`GroupBehavior` supplies panel captions. `ColouredGroupBehavior` suppresses duplicate persistent
decoration, emphasizes its own coloured outline, and retains the requesting control's selection
style. The inline side-config renderer and side-config drawer depend only on these contracts;
they do not recognize concrete group classes or construct temporary group wrappers.

Declare server actions in the common element's `actions()` list using `ElementAction<T>`.
Its bounded codec and handler work like the `WidgetAction<T>` example below, but the handler's
context contains any `Element`. Call `access.submit(action, payload)` from the client behavior;
`actionResult(accepted)` runs after authoritative values are applied. Include
`ElementActions.CLICK` when overriding `actions()` if common interactions/callbacks should
remain available. The menu owns player/session/distance/visibility checks, replay protection,
rate limits and acknowledgement transport; the element's handler validates its own payload and
performs its operation. Built-in navigation, back, side-mode cycling and ghost selection all
declare their actions this way. Existing widget factories and action APIs remain supported;
client and server must use matching network protocol versions.

For additional synchronized data, implement `gui.sync.SynchronizedElement` and return stable,
ordered `ElementSync<?>` declarations from `synchronizedValues()`. Each declaration owns its ID,
bounded `StreamCodec<RegistryFriendlyByteBuf, T>`, initial value and validation. For example:

```java
public static final ElementSync<Integer> SELECTED = ElementSync.field(
    ResourceLocation.parse("example:selected"),
    StreamCodec.of((buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
    5, element -> 0)
    .validate((element, value) -> value >= 0 && value < 3)
    .affectsLayout()
    .build();

public List<ElementSync<?>> synchronizedValues() { return List.of(SELECTED); }
```

Without a sampler, a field retains per-menu state. A server action updates it through
`context.menu().elementStates().set(context.element(), SELECTED, selection)` after validating
the operation. Use `.sample((source, previous) -> ...)` for machine-derived data; it runs only
on the server and receives the element, machine and viewer. Ordinary `value`/`max` getters
keep their existing shared machine/tick cache. Renderers read
`context.access().state().get(element, SELECTED)`; behaviors use `access.presentation().state()`.

`ElementStates` owns per-menu storage, wire addresses, byte comparisons and transport validation.
Values are serialized defensively; reads cannot mutate stored data. Batches validate before
application, and `affectsLayout()` invalidates layout/visibility once per changed batch.
`SelectiveContainer` lets a container choose visible children from this state, and
`NavigableElement` supplies optional named navigation. `ContextElement` owns selected-view state;
`SideConfigElement` owns its side-mode codec and server sampling. Shared snapshot/menu code
contains no context or side-mode serialization branches.

Any element can declare navigation through `Optional<Navigation> navigation()`. For example, a
custom control can return `Optional.of(new Navigation(new ViewRef("panel", "settings")))`.
`ButtonElement.shows` supplies this same binding. `Element` validates its destination and includes
`Navigation.NAVIGATE` in its default actions; controls that override `actions()` must retain that
action when using navigation. The server derives the destination from the element definition,
not from a client-provided view name, and ordinary visibility/session/action checks still apply.

The destination implements `NavigableElement`: `defaultSelection()`, `hasSelection(name)`,
`selected(state)` and `select(states, name)`. It owns its synchronized fields, selection rules and
layout. Selecting an already-selected target returns to its default selection. Both server
selection and client prediction use the same `Navigation` calculation, with no button/context
casts. `ContextElement` implements this contract for its views; mod destinations can use another
representation.

`DefaultElementBehavior` handles navigation clicks, prediction and acknowledgment/timeout
correction for ordinary elements; no navigation-specific behavior registration is needed for a
plain custom control. Custom behaviors extending it should delegate unhandled clicks to
`super.click(...)` and acknowledgments to `super.actionResult(...)`. `ButtonBehavior` adds only
button tooltip labels. `NavigableBehavior` is registered for destination implementations and owns
remembered-view restoration; `ContextBehavior` adds its back arrow and Escape behavior.
`NavigationMemory` restores through a visible control declaring the matching binding and typed
action, waits for server confirmation, and never edits destination state directly during restore.
Custom lifecycle implementations can reuse these services. `ButtonElement.NAVIGATE` is an alias
of the shared action for existing callers; the wire ID and click payload remain unchanged.

Limits are 16 fields per element, 64 KiB per field, and 256 KiB including envelope accounting
per screen. Declaration IDs, order, bounds and layout flags must match on both sides and enter
the screen fingerprint. Field codecs and semantics must also match between mod versions.
Typed state travels in ordinary deltas and action acknowledgments, including rejections, before
`actionResult`. Local prediction never authorizes a server mutation; timeouts restore the last
confirmed typed state. Local-only focus, hover and selection need no synchronized field.

Element definitions also own their load-time contracts: `validate()` checks local configuration,
`validateScreen(ScreenValidationContext)` checks references and shared constraints, and
`valueKinds(widgets)` / `maximumKinds()` constrain getter return types. The context exposes
`screen()` and `unique(domain, key, duplicateMessage)`: namespaced claims let built-in and mod
elements reject repeated bindings without adding branches to shared validation. Claims live only
for one validation pass. `SlotElement` claims inventory/local-index pairs; `PlayerSlotsElement`
owns section uniqueness and screen-inventory checks. Player-backed `SlotElement`s claim player indices.
`ValueContract.require` checks required
value/max bindings. These contracts apply to both XML and Java-built screens, with source/line
diagnostics. `ScreenValidation` keeps common tree checks such as duplicate IDs and fill/spacer
relationships; `ScreenBinder` resolves getters against the element's declared kinds.

Runtime host validation calls `validateHost(ScreenValidationContext, ScreenHost)` with a fresh
context before constructing slots. Slots validate their named storage bounds through this hook;
custom elements can validate host services and claim bindings through the same path. The default
contextual hooks delegate to existing `validateScreen(Screen)` and `validateHost(ScreenHost)`
overrides. When overriding a contextual hook, call the inherited implementation to retain those
checks (including shared slot rules when implementing `SlotElement`).

`InventoryElement` contains `ItemSlotElement` and `GhostSlotElement` children. Its `storage`
name selects an independent handler from `ScreenHost.screenInventories()`; child indexes are
local to that handler. For example, both inventories below have their own slot zero:

```xml
<inventory storage="input" columns="2" spacing="2">
  <item-slot index="0"/>
  <ghost-slot index="1" icon="example:filter" ghost-alpha="0.5"/>
</inventory>
<inventory storage="output">
  <item-slot index="0" size="large" background="example:output_slot"/>
</inventory>
```

`SlotElement.Appearance` owns the shared background sprite, tint, empty icon, icon atlas,
item size (4â€“32 pixels, default 16), and ghost opacity (0â€“1, default 0.42). XML uses
`background`, `tint`, `icon`, `icon-atlas`, `item-size`, and `ghost-alpha`. Without an atlas,
icons are GUI sprites; equipment slots use vanilla's block atlas. Real slots can show a
remembered ghost through `screenSlotHint(inventory, slot)` when empty. Editable ghost slots
use `screenGhost(inventory, slot)` and `setScreenGhost(player, inventory, slot, filter)`;
left-click copies one carried item, right-click clears, and neither consumes an item.

The same classes provide player equipment and addon slots. `InventoryElement` owns grid
placement and menu assembly; `ItemSlotElement` owns machine adapters and reservation-aware
clicks, while `PlayerSlotElement` owns equipment rules; `SlotElement` owns ghost and reservation presentation. Client
renderers supply drawing primitives. The menu retains vanilla coordination, synchronization,
packet checks and routing between inventories.

Returning `ItemInventories` from `screenItems()` exposes its independent handlers automatically.
Custom hosts can override `screenInventories()`. Use named `ItemInventories.Address` values in
`screenItemDestinations(stack)` and `screenRecipeItems(category)` for shift-click and JEI routing.
JEI both checks eligibility and resolves inputs through `screenRecipeItems`; a host implementing
that named API does not need to implement the legacy `screenRecipeSlots` method.
Hosts may override `screenInventoryBinding(inventory, index, standalone)` for custom wrappers or
logical-to-physical mappings. It returns an `InventoryBinding` containing the physical handler,
physical slot index, legacy index (`-1` for none), and named address aliases. Validation uses this
same hook; logical XML indexes need not equal physical indexes. Keep handler identities stable:
assembly uses handler identity/local index to reject duplicate physical slots and share client
mirrors. Different slots cannot claim the same named address. Default resolution preserves
`ItemInventories` and `ItemStore` behavior; assembly does not inspect those storage types.

Custom containers can implement `gui.model.InventoryScope` and return their storage name from
`inventory()`. `InventoryElement` implements this same contract. `InventoryScope.owner(screen, element)`
finds the nearest enclosing scope by element identity, through ordinary layout wrappers; nested
scopes override outer scopes. Body, tabs and implicit sections all participate. `InventoryScope.name`
returns the scoped name, or `items` for legacy unscoped slots. Binding, validation, group associations
and generated-machine highlight compilation use this lookup. These helpers replace the former
`InventoryElement.parent`/static `inventory` helpers. Keep names and children stable on both sides;
custom containers still own their normal layout, rendering and XML registration.

`SlotElement.hint(host, inventory)` supplies the server-sampled remembered item shown in an empty
slot. The default calls `host.screenSlotHint` for machine slots and returns empty for player slots
or an absent host. Mods may override it for either kind of slot; the host argument can be null.
Player slots receive the name `player`. Every registered native slot with an element participates,
including contributed drawer slots outside the screen tree. Contributors can pass a logical inventory
name to `MenuSlots.Registrar.add(slot, element, role, inventory)`; the three-argument overload defaults
to `player` for player-backed elements and `items` otherwise. Sampling and lookup copy the stacks.
The existing `HINTS` field synchronizes native menu indexes (schema version 2, up to 32,767 slots,
within the existing 64 KiB field limit). Complete empty snapshots clear old hints. Schema versions
participate in the menu fingerprint, so both sides must match. No new registry is needed.
`AutomationSlotElement` defaults to no hint; override `hint` to customize addon/filter previews.
The side configuration drawer uses the same synchronized hints. Hint items never become stored contents.

`screenLegacySlotCount()` reserves the legacy numbering range (default: `screenItems().getSlots()`).
Bindings with legacy indexes retain those menu indexes; assembly resolves all bindings before
allocating other slots, so later legacy indexes cannot collide with earlier automatic allocations.
`screenSlotAddresses(inventory, index, standalone)` supplies default logical aliases and aliases
for ghost slots, which do not have physical bindings. Override the binding's returned addresses
for custom real-slot routing. Both menu sides must produce matching addresses and numbering.

`SlotElement.role()` supplies the native transfer role for both machine and player slots.
`MACHINE` remains the default for machine storage; inactive numbering placeholders retain it too.
Roles supply the defaults for reservation participation and shift-click routing. Override
`reservationOverlay(entry)` to choose whether the native bridge calls the element's overlay drawing;
`renderOverlay` owns its contents. Override `reservationSync(entry)` independently to select slots
whose plans participate in synchronization. Hiding an overlay does not disable reservation rules.
Synchronization requires a `SlotAdapter`; a plain native slot can still opt into custom overlay
rendering. Both hooks default to machine roles (drawing also requires a `SlotAdapter`). Keep sync
eligibility stable and consistent on both menu sides.

Override `quickMoveTargets(SlotTransferContext)` to supply ordered native destination slots, or
return an empty list to refuse shift-click routing. The context exposes the source entry, all entries
in native registration order, a defensive stack copy, and lazy `machineDestinations()` from the
registered menu contributors. Default routing sends player storage to those destinations; other
sources target player storage in reverse registration order. Native contributions without an element
retain that same default policy. Treat the context's native slots as read-only when choosing targets.

`MenuSlots` filters foreign, self, inactive and incompatible targets, removes duplicates by identity,
and merges existing stacks before filling empty slots, preserving the supplied order within each
pass. Source pickup checks and adapter-specific reserved extraction remain enforced by the shared
executor. These hooks require no new registry and retain the existing adapters and renderers.

Legacy standalone `<slot>` elements and integer routing methods still use the combined `items`
view by default. `menu.slot(element)` and `menu.slotIndex(element)` resolve a local UI slot to its native
menu slot; a local index is not necessarily a menu index.

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

`gui.runtime.slot.ReservationMenuComponent` owns reservation synchronization. It is registered as
`BuiltinMenuComponents.RESERVATIONS` with the typed `PLANS` field (`mmdlib:reservation_plans`).
The field contains a complete map from native menu slot index to `SlotPlan`, sampled from eligible
slots implementing `SlotAdapter`, including slots contributed by mods. By default, only machine
roles participate; elements can override `reservationSync(entry)` to opt other roles in or out.
Contributions without an element retain the machine-role default. Hidden underlying storage is not
sampled. Overlay visibility is independent of synchronization eligibility.

The slot subsystem owns the bounded codec (64 KiB per snapshot), including input counts and output
item components. Registry allocation supplies the field's transport address; native slot indexes
are only keys inside its payload. Equal plan contents produce no delta. On a client update,
`SlotAdapter.applyPlan` refreshes eligible slots; `SlotPlan.NONE` and omitted entries clear previous
plans and insertion predictions. Server sampling never calls client adapters. `MachineMenu` and
`ScreenValues` no longer allocate reservation keys or serialize reservation-specific values.
Use the normal component snapshot path; `PLAN_KEYS` and `reservationSnapshot()` are removed.

When the machine has reserved items in a slot (see [capabilities](capabilities.md#reservations)),
the menu keeps them from being taken or replaced:

- Left and right click, Q and Ctrl+Q, and shift-click take only what is free.
- Clicking the slot with a different item is refused.
- Number-key and offhand swaps move only the unreserved portion.
- Double-clicking a slot with a plan doesn't collect from elsewhere. Double-clicking another slot
  still takes the planned slot's free part.

By default, machine slots show reservations at the top right. Both reservation and held quantities use
87.5% of vanilla's font size:
`-2` means two items reserved for input, and `+1` means space reserved for one output. The held
quantity stays at the bottom right, using the same size even when no reservation is present.
An empty planned output shows its item model at the slot's configured ghost
opacity with `+1` at the top, without creating a real stack. Mixed consume/produce plans show
both signed counts on the top row. Tooltips provide reservation counts and the planned output's
name. Clearing the plan removes the badge and ghost. Player inventory, equipment and filter
slots are unaffected by default; custom elements can opt into overlays using the hooks above.
Chance outputs show their reserved capacity, not a guarantee that the product will be rolled.
No extra packet or XML binding is needed; `MachineMenu.reservationPlan(index)` exposes the existing
synchronized snapshot for real machine slots.

The client's menu works out the same answer from the slot's plan, which arrives with the screen's
values, so clicks don't flicker. Bars and numbers still come from the getters named in the XML; a
feature's own client sync (see [block entities](block-entities.md#machine-features)) doesn't
replace them.

`screenItemDestinations` controls shift-click routing into the machine. `screenRecipeItems` names
recipe inputs in recipe order; an empty result disables JEI transfer. The named methods adapt the
legacy integer methods `screenDestinations` and `screenRecipeSlots` for hosts with combined storage.
Routing methods must work on the client as well as the server. JEI uses its standard transfer
handler with these slots, including its server validation.
Only visible normal slots participate in recipe transfer. Category IDs must also occur in the
screen's `jei-recipes` attributes.

Ghost filters are separate from the real inventory. Override `setScreenGhost(player, index, filter)`
to validate and persist a filter, and `screenGhost(index)` to expose its authoritative value. The
ghost element normalizes the filter to one item; an empty stack clears it. Defaults reject changes and
show empty filters. No real items move through this path.

By default, ghost clicks and JEI ingredient drops submit `GhostSlotElement.SELECT_GHOST` through
the typed element-action channel. `SlotElement.selectionAction()` selects the `ElementAction<ItemStack>`;
return empty to opt out. The default `actions()` declares that action automatically. If overriding
`actions()` too, include the same action instance: the behavior checks declaration identity before
submission. `acceptsSelection(button, stack)` controls client eligibility, and `selection(button,
carried)` prepares the payload. Both receive detached stacks. The action handler remains responsible
for authoritative server validation. The default ghost `accept(host, inventory, player, stack)` calls
`selection(0, stack)` on the original slot element again before writing to the host; overrides work
for direct `SlotElement` implementations and subclasses. Make normalization idempotent because it
runs during client preparation and server acceptance. The default keeps one item, or clears an empty
selection. Override `accept` for different authoritative rules. Submitted and stored stacks are copied;
`read(host, inventory)` returns a detached ghost value.

`selectionIngredient(typeId, ingredient)` converts a detached viewer ingredient into an optional
item selection. The default accepts `mmdlib:item`; mods can recognize other registered ingredient
IDs and convert them without changing the shared behavior. Unsupported inputs return empty.
`SlotBehavior` rechecks action declaration, eligibility and pending state when accepting a drop.
 `MachineScreen.submitElementAction` supplies the shared client
submission path, including pending-request handling. The server dispatcher checks menu/session,
request order, reach and visibility before decoding. The ghost action delegates to the inventory
component; `InventoryElement.Session` resolves the named inventory and rejects inactive,
unregistered or non-ghost slots. The old `ScreenNetwork.Ghost` packet and `MachineMenu.ghost`
entry point are removed. Core screen protocol 20 requires matching client/server versions.

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

Code can register a handler directly, with
`MaterialExtensions.GLOBAL.events().registerCallback(id, MaterialEventTypes.GUI_CLICK, click -> ...)`.
CraftTweaker scripts can supply handlers too, as `mmdlib:` IDs: the screen stays
XML and names them like any other handler. See
[CraftTweaker screen handlers](crafttweaker.md#screen-handlers).

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
`ScreenHost.screenDefinitionFingerprint()` can contribute additional startup configuration;
XML machines supply their resolved definition CRC, including inherited storage settings.

`./gradlew check` includes numeric protocol and layout regression checks. The isolated graphical
smoke run is `xvfb-run -a ./gradlew runClient --offline -I scripts/gui-smoke.init.gradle`; repeat
with `-PwithoutJei` for the optional-integration case. It creates a disposable integrated world
under `build/gui-smoke*`, checks server actions and full codecs, opens the actual menu, verifies
synced values, and saves `screenshots/gui-smoke.png`. Run a normal build afterward to remove
injected smoke classes from main output; release verification rejects any leaked smoke classes.

For a side-configuration-only drawer, return `false` from
`ScreenHost.screenAutomationAddons()`. The menu registers no addon/filter slots and the drawer
ends below its face controls. Combine this with `inventory="none"` to omit player inventory and
equipment drawers, as in the companion Creative Power Source.

## Client drawer classes

`gui.client.Drawer` is the public base for client drawers. Its constructor takes the owning
screen, a registered `DrawerDefinition`, an `IconSprite` and an RGB colour. Subclasses choose
their appearance and own their content layout, input, tooltips and local state. `IconSprite.gui`
uses a GUI sprite ID; `atlas` uses an atlas sprite and `item` preserves a live item model. A custom
icon can also implement the drawing callback. The base draws the shared tab/frame.

The built-ins are `SideConfigDrawer` (previously `AutomationDrawer`), `RedstoneDrawer`,
`StatsDrawer`, `ContainerDrawer` (backpack or Curios equipment), and `PopoutDrawer` (XML info,
warning and error popouts). Each interprets and restores its own `Drawer.Selection` preference.
`DrawerMemory` only stores connection-scoped preferences and retries restoration. Server-owned
inventory, configuration and acknowledgments remain in their menu/network classes.

All drawers come from registrations, including built-ins and XML popouts. Register a common-side
provider during mod construction and its client factory during client setup:

```java
// Common setup; use immutable definition/feature presence, not live storage values.
var type = ResourceLocation.parse("example:diagnostics");
DrawerRegistry.GLOBAL.register(type, context -> List.of(
    new DrawerDefinition(type, "example:diagnostics",
        DrawerPlacement.top(Edge.RIGHT, 15))));

// Client setup only. The constructor calls super(screen, definition, icon, colour).
DrawerFactories.GLOBAL.register(type, DiagnosticsDrawer::new);
```

`gui.drawer.DrawerRegistry` providers return zero or more definitions for a screen/host. Definitions
declare stable IDs, edge, top/bottom anchor, order, tab dimensions, overlap, gap, optional model
element and inventory-toolbar width. Smaller order values sit closest to their anchor. Built-in
top orders are side configuration 0, redstone 10, XML side tabs 20, XML popouts 30 and statistics 40.
Backpack and Curios tabs use bottom orders 0 and 10. Equal orders use provider ID order, then the
provider's list order, so mod loading order does not affect geometry. IDs must be unique per edge.

`DrawerTabs` packs tabs generically, and `ScreenLayout` reserves the required panel height and
toolbar space. Top tab positions stay reserved when hidden, keeping XML/vanilla slot geometry
stable; unavailable bottom drawers collapse out of the stack. Bottom tabs respect viewport and
overlay insets while keeping clear of the top stack. A viewport too short to fit the stack needs
a larger window or smaller GUI scale. Drawers use the inherited `tabBounds()` for their allocated
position and own the body geometry. Inventory drawers expose `contentLayout()` for integrations;
the content-layout helper accepts an allocated tab instead of guessing where other drawers sit.

Every interactive definition needs a factory returning a fresh drawer for that screen. Duplicate
registrations fail; `replace` explicitly replaces an existing provider/factory. Missing factories,
duplicate identities and unclaimed model tabs fail explicitly. A layout-only definition can
place a persistent side tab without creating an interactive drawer. Mods can associate any custom
element with a tab by registering a provider; neither layout nor dispatch needs a new type branch.
Keep registrations and provider results stable while menus are open. Common definitions enter
the client/server opening fingerprint; client factories never load on a dedicated server.

Override `open` and `close`, then whichever render/input/lifecycle hooks the drawer needs.
Use `drawFrame` for shared framing and override `drawIcon` for badges. The screen forwards events
through `Drawers`. Bounds returned by `exclusions` are also reserved for recipe-browser overlays.
Direct `screen.drawers().add(...)` has been replaced by the registry path above.

Drawer bodies follow the definition's edge as well as its tab. `SideDrawerLayout.bodyX` and
`room` provide shared horizontal geometry; inventory bodies also check available viewport space.
Server-controlled drawers implement `gui.runtime.component.DrawerSession` on their menu component.
After authorizing an open request, call `menu.components().activate(this)`: registered sessions on
the same edge close through `closeServer()`. Sessions identify their registered drawer type, which
must occupy only one edge. Controllers do not name sibling drawers.

`gui.runtime.MenuRequests` provides optional bookkeeping primitives for custom menu controllers,
without another registry or a shared payload protocol. Keep one `Client(timeoutTicks)` and one
`Server(perTick)` per controller/session, accessed on the game thread. `Client.begin()` returns a
positive request ID, or zero while pending/after sequence exhaustion. IDs never wrap or get reused.
`tick()` releases an expired request and reports the timeout; the controller chooses recovery.
Use `acknowledgeExact` for exact replies or `acknowledgeThrough` for cumulative snapshots (which
also advance the local sequence). Apply revision/session validation before acknowledging.
On the server, authorize first, then `receive(request)` rejects replays and consumes fresh IDs.
Call `allow(gameTime)` once per fresh request to apply the allowance. Feature rejection and rate
limiting still consume the request ID, allowing authoritative replies to acknowledge it.

The built-in controllers retain their own policies:

| Controller | Timeout | Recovery | Acknowledgment | Requests per tick |
|---|---:|---|---|---:|
| Automation | 100 ticks | Unlock | Exact | 8 |
| Redstone | 100 ticks | Send sync | Cumulative | 16 |
| Equipped containers | 20 ticks | Send sync, retain inventory lock | Cumulative | 32 |

Controllers still own revisions, attachment generations, inventory locks, action validation,
state application and packet transport. These primitives do not replace `ElementActionSession`,
which supports multiple independently pending element actions and typed state correction.

Local drawers call `openLocal()` before opening to close siblings. Each drawer decides whether
pending requests should delay opening: Statistics waits, while Info remains accessible during
a pending item transfer. Override `closeLocal()` to close local presentation when a server-controlled
sibling activates. Server-controlled drawers call `activated()` after accepting an open request;
their server handles mutually exclusive authoritative state. Use `remember(open, selection)` for
accepted user intent, and override `selection`/`restore` for preference persistence. Returning
`false` from `restore` waits for another snapshot or acknowledgment; `pending()` protects an
unconfirmed preference when the screen closes. `close()` itself should not record user intent
because it is also used when another drawer opens.

`PopoutElement.colour()` supplies the RGB tab/body colour, and
`content(value, resolveText)` supplies ordered, unwrapped `Component` paragraphs. Empty components
represent blank rows. The defaults retain info/warning/error colours, translated fixed-text
emphasis and styled live text/list entries. Subclasses can override both hooks (and `valueKinds`
for other synchronized value types) while inheriting the existing drawer and renderers; no new
registration is needed. `PopoutDrawer` only wraps the supplied content and owns placement,
scrolling and open/close behavior.

XML popouts still draw through `ElementRenderers`, whose registered adapter delegates to
`PopoutDrawer`. The generic `drawerPresentation(element, Contract.class)` bridge lets a drawer
expose read-only presentation to its element renderer. Mods can replace drawing passes as before.
`MachineScreen` holds no
drawer-specific open, scroll, layout or restoration logic.

`ElementFragment.Host.state()` supplies the state used by renderers, behaviors and layout.
`Host.navigate(target, selection)` applies host-owned navigation/prediction, while `Host.submit`
retains action transport and authorization. The fragment forwards only declared actions from
visible, non-pending elements. After applying an authoritative result, call
`fragment.actionResult(element, accepted)` to notify its behavior.

Fragments run element layout preparation and use `SelectiveContainer.childVisible` for every
ancestor, composed with `Host.visible(element)` and viewport clipping. Rendering, input and
tooltips share this visibility. State changes remeasure the tree and cancel focus/capture when
an element becomes hidden; unchanged bounds preserve interaction. `refresh()` reconciles state
explicitly; drawing, input, ticking, placement and height queries also refresh automatically.
Behavior initialization occurs on first placement, when the viewport is known. A fragment keeps
its element identities and behavior instances; replace it if the actual tree changes.

Item-provided filter editors also use the element registries. `ElementFragment` hosts an independent
element tree inside a clipped viewport with the shared focus/capture lifecycle. Its host supplies
values and optional behavior services through `ElementAccess.service`; rendering access stays
read-only. See [filter panels](inventory-automation.md#item-provided-filter-panels) for control
registration, validation and the existing typed-widget adapter.

### Registered network contributors

`gui.runtime.network.NetworkRegistry.GLOBAL` collects feature-owned play payloads. Register on
both logical sides during mod construction, before NeoForge's payload-registration event:

```java
NetworkRegistry.GLOBAL.register(ResourceLocation.parse("example:diagnostics"), "1", network -> {
    network.playToServer(DiagnosticRequest.TYPE, DiagnosticRequest.CODEC, DiagnosticsNetwork::request);
    network.playToClient(DiagnosticReply.TYPE, DiagnosticReply.CODEC, DiagnosticsNetwork::reply);
});
```

The contributor supplies its protocol version; each payload supplies its NeoForge type, bounded
stream codec and handler. The callback runs immediately and its registrar closes when it returns.
Contributor IDs and payload IDs must be unique across this registry, including payloads declared
in opposite directions. A failed declaration registers nothing. Startup freezes the registry and
installs contributors in ID order; registering after that is an error.

`BuiltinNetworks` declares the built-in contributors through the same API. `ScreenNetwork`,
`AutomationNetwork`, `RedstoneNetwork` and `ContainerDrawerNetwork` each own their version,
payloads, codecs and handlers. `ScreenRuntime` only installs the registry; the generic screen
network has no list of feature networks. NeoForge continues to negotiate versions and dispatch
payloads on its normal handler thread. Handlers remain responsible for menu/session identity,
authorization, revisions and rate limits. Each feature declares its own version; the core screen
protocol is 19, and automation, redstone and container drawers remain at version 16.

### Registered menu components and native slots

`gui.runtime.component.MenuComponentRegistry.GLOBAL` creates fresh state for each menu. Register
on both logical sides during mod construction, before opening screens:

```java
public static final MenuComponentKey<DiagnosticsMenu> DIAGNOSTICS =
    new MenuComponentKey<>(ResourceLocation.parse("example:diagnostics"), DiagnosticsMenu.class);

MenuComponentRegistry.GLOBAL.register(DIAGNOSTICS, 50, context ->
    supportsDiagnostics(context.host()) ? new DiagnosticsMenu(context) : null);

var diagnostics = menu.components().get(DIAGNOSTICS); // null when unavailable
```

Factories receive the menu, player inventory, optional host and logical side. They construct their
own state; other components become available after all factories finish. A factory may return
`null` when its feature is absent. Registration order is the numeric order followed by ID; duplicate
IDs fail, and `replace` deliberately replaces an existing typed registration. Keep availability
and slot ordering identical on client and server. Registration IDs, types, order and synchronized
field schemas enter the menu opening fingerprint (core protocol 20). Keep factories and drawer declarations consistent when disabling
a built-in feature.

`MenuComponent` receives client ticks, before/after interaction, before/after native broadcast and
removal hooks. Optional contracts supply additional behavior:

| Contract | Responsibility |
|---|---|
| `MenuSlotContributor` | Add native slots, position them and supply ordered shift-click destinations |
| `MenuSnapshot` | Sample registered typed fields and receive validated state changes |
| `DrawerSession` | Close server state when another registered session on the same edge opens |
| `InventoryAccess` | Resolve legacy/named item addresses and handle inventory hints/ghost edits |

Declare synchronized fields alongside the component registration. Each `ComponentSync<T>` provides
a namespaced ID, schema version, `StreamCodec<RegistryFriendlyByteBuf, T>`, byte limit, initial-value
supplier and optional validator:

```java
public static final ComponentSync<Integer> COUNT = new ComponentSync<>(
    ResourceLocation.parse("example:count"), 1,
    StreamCodec.of((buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
    5, () -> 0, value -> value >= 0);

MenuComponentRegistry.GLOBAL.register(DIAGNOSTICS, 50, context ->
    supportsDiagnostics(context.host()) ? new DiagnosticsMenu(context) : null, COUNT);

// On DiagnosticsMenu implements MenuSnapshot:
@Override public void snapshot(ComponentSnapshot values) {
    values.put(COUNT, sampleServerCount());
}

int displayedCount = menu.components().state(diagnostics, COUNT);
```

The registration owns its field namespace: two components may use the same field ID without
colliding. Fields receive stable transport addresses ordered by component ID and field ID; omitted
components retain their reserved addresses. Components never choose integer wire keys. A writer
accepts only the exact field definitions declared for its component, once per sampling pass.
`replace` replaces the field declarations too; supply the replacement's complete field list.
Bump a field's schema version when its codec or meaning changes, so incompatible peers fail the
opening fingerprint check.

Each field is limited to 64 KiB, with a 256 KiB encoded-state packet budget shared with element
state. A registry may declare at most 4,096 component fields. Codecs must also bound collection
counts before allocating or reading them. Updates validate the complete component batch before
committing it; reads decode independent copies. Optional `applied(ComponentStateView values)` runs
once per changed component after all values commit, on both server sampling and client updates.
It can cache decoded presentation data. Initial values come from the supplier without a callback.
Inventory hints, statistics and reservation plans use this same registered path.

`MenuComponent.presentationRoots()` supplies stable per-menu element trees outside the XML screen.
Return matching trees in the same order on client and server. Tooltip synchronization includes those
roots and descendants, followed by any remaining contributed native slot elements, deduplicated by
identity. The existing `TOOLTIPS.ROWS` field uses schema v2 and permits up to 32,767 element addresses
within its existing 64 KiB budget; both sides must have matching schemas. This is a presentation/
tooltip contribution, not an automatic declaration of element actions or synchronized element fields.
`ElementFragment.Host` exposes `contents`, `hint`, `reservation` and `serverTooltip` for such hosts;
fragments never sample server storage themselves. No additional registry is required.


All contributors run `addSlots` before any run `finishSlots`; the built-in inventory component
adds player slots in the second phase. Use `MenuSlots.Registrar` so the menu knows each slot's
element (optional) and role. With the default routing policy, `PLAYER_STORAGE` receives extraction
from machine, auxiliary and equipment slots; insertion from player storage uses contributor destinations, ordered by
`transferPriority`, topping up matching stacks before empty slots. The native adapter still checks
activity, pickup and placement permissions. Storage lookups follow actual menu indexes even if a
mod contributes slots before the built-in inventory.

Built-ins use these same contracts. Access them through `BuiltinMenuComponents` keys instead of
the removed `menu.automation`, `menu.redstone`, `menu.containerDrawer` and `menu.curiosDrawer` fields.
Statistics use `menu.components().require(BuiltinMenuComponents.STATS).pages()`: each page supplies
a stable timescale ID, selector label and formatted rows.

`gui.runtime.slot.MenuSlots` owns native assembly and transfer execution; slot elements choose routing targets. Optional `SlotAdapter`
hooks own reservation plans, interaction cleanup and specialized extraction. Item slot adapters
extract only the available portion of a reserved stack. `NativeSlotRenderer` adapts native drawing,
scaling, highlights, hit bounds and item-tooltip routing to `SlotElement`; ghost and reservation
presentation still comes from the element. For filled slots, `SlotElement.nativeTooltip(stack, vanilla)`
selects the native item rows: retain, edit, replace, or return an empty list to suppress them. Its stack
and row components are detached copies; copy the list before adding/removing entries. The bridge then
appends the element's composed tooltip, including all ancestor rows, so replacing native rows never
removes parent context. The default returns vanilla rows unchanged. Empty slots use the ordinary
element tooltip. The bridge checks the registered
native slot's element identity, so unrelated hovered elements retain their own tooltips.
`MachineScreen` only forwards vanilla callbacks to that adapter.

Drawers can claim an occupied native slot's tooltip through `Drawer.nativeTooltip(slot, vanilla,
mouseX, mouseY)`. Return empty to leave it to the screen, or composed rows when the drawer owns it.
`SideConfigDrawer` uses the same native bridge and an `ElementFragment` rooted in the selected
`AutomationInventoryElement`. Registered slot and inventory renderers run in all four passes, and
registered behaviors contribute tooltip rows. The fragment supplies copied contents/hints,
reservations and synchronized server rows. Empty slots use ordinary element/parent composition;
occupied slots apply `nativeTooltip` first and preserve all composed parent rows afterward.
The default compatible-addon caption belongs to `AutomationInventoryElement.appendChildTooltip`.
In this fragment, a slot's value is its copied contents and the inventory's value is its group-name
component; custom tooltip hooks can read them through `TooltipContext.value(element, false)`.
Override that hook to customize inventory context; each slot still controls its own rows.
`drawerSlotBounds` also drives the inventory's fragment layout, keeping native and rendered positions
aligned. Hidden filter slots are excluded, and closing/switching the drawer removes old behaviors.

Backpack and equipment pages use `ContainerInventoryElement` with `ContainerSlotElement` children.
`ContainerDrawer.createInventory(Page)` is a protected factory: subclass the drawer through the existing
`DrawerFactories.replace` API to supply your inventory and slot subclasses. The inventory accepts an
index-to-slot factory, preserving absolute container indexes; it owns `cellBounds`, label/header bounds,
and `drawerValue`. The page supplies detached provider metadata and body-relative cell rectangles.
These virtual cells do not create vanilla slots or change container request validation.
`SlotElement.cellHitBounds(frame)` supplies the same geometry to hover highlighting, clicks,
drag-target selection and tooltips. Its default applies `itemPosition`, `hitBounds` and vanilla's
one-pixel tolerance; an override returns the final bounds. The drawer viewport still clips hits.

`ContainerSlotElement` inherits slot appearance, item positioning, native bounds, rendering and
`nativeTooltip` hooks. Its registered renderer draws the frame, empty icon, real item/decorations,
placeholder and selection overlay. Register a renderer for your subclass to replace those defaults;
all four passes use the existing element registry. `Display` values hold copied contents and visual
drag selection; maximum values default to null. Slot and inventory tooltip hooks run through
`ElementFragment`. Filled cells prepend the slot's `nativeTooltip` rows, then append element and all
parent rows, including behavior contributions. Suppressing native rows preserves parent context.
Item tooltip images and NeoForge item-tooltip context are retained.

`ContainerSlotElement.description()` supplies the default availability, label, placeholder, tooltip
rows and empty icon. Override it to replace the provider description; an explicitly supplied
`Appearance` retains its own icon. Each individual presentation hook remains overridable.
`SlotElement.cellLabel()` supplies an optional label; `ContainerInventoryElement.cellLabel(slot)`
and `headings()` can customize labels and headings without replacing the renderer.

Container fragments route hover, clicks, scrolling, keyboard/text input, key releases and pointer
capture through the existing `ElementBehaviors` registrations. A consumed click suppresses the
container gesture; `PASS` leaves the validated drawer fallback in charge. Cell hits use
`cellHitBounds` for both routes, clipped to the drawer body. Drawer controls retain click priority.
Capture receives dragging/releases outside the body. Closing, replacing a page, resizing or receiving
a new revision cancels interaction; pending locks cancel focus/capture and block input independently
of visible contents/tooltips. A cancelled press's release is consumed without committing.
`ElementFragment.Host.hitBounds` and `inputEnabled` expose the same geometry/lock policies to other
fragment hosts. Virtual cells do not register server actions: custom local behaviours cannot bypass
the existing item-transfer protocol or its cursor, session, revision and gesture validation.

Equipment providers supply `EquippedContainers.Source.presentation` and `selectionKey` separately
from the live capability's topology key. `Presentation` owns title, icon, colour, column headings and
header/tab instructions; `SlotInfo` owns labels, tooltip rows and unavailable placeholders. Components
are copied on construction and access. The Curios adapter supplies its own translations, fallback
labels and sprites; the generic drawer does not derive them from Curios identifiers or cosmetic flags.
The three-argument `Source` constructor retains the live item name and standard container presentation.
Container snapshots now use protocol 19, with bounded text, row counts, policy settings and gesture instructions.

Each source accepts a `ContainerPolicy`. Construct it with `Settings`, or subclass it and override
`settings()` for provider-dependent choices. `STORAGE` and `EQUIPMENT` are the default presets.
Settings supply body width, columns, row limits, cell placement/stride/size, footer space, initial
page size, alignment, allowed gestures and source-item protection. The server samples settings into
`Presentation.policy`; the client consumes that snapshot. Policy changes advance the interaction
revision and realign an incompatible page. The source's policy is authoritative; changing client
presentation cannot enable a forbidden action. Curios explicitly supplies the equipment preset.
Slot descriptions work with either drawer identity.

`ContainerPolicy.instructions(Settings)` receives the same sampled settings and supplies copied,
bounded gesture rows. Defaults independently describe enabled distribution, collection and hotbar
swapping, so partial policies still show their supported actions. Override this method for mod text;
`Presentation.gestureRows()` carries the result to the client. A drawer subclass may override
`gestureInstructions()` for client presentation. Instructions never authorize gestures.

`ContainerDrawerLayout` lays out the supplied settings; the boolean convenience overloads only select
a preset. Inventory header/label bounds follow the policy's cell positions and remain overridable.
Mod layouts must fit the bounded page size and preserve their declared page alignment.

Container-to-player gestures use `MachineScreen.nativeSlotAt`, including custom hit bounds and vanilla
tolerance. Pending inventory guards use the geometry-only overload to block slots even while their
native interaction is disabled. This never authorizes a transfer. Cursor, source, revision, page,
request and drag validation remain independent of presentation overrides. `ContainerDrawerMenu.targets()`
provides the menu-owned `ContainerTargets` context for registered player hits, target encoding and
copied stack lookup. Server distribution uses its live-source resolver. The ordinary 36-slot player
storage range, hotbar range and wire limits remain validation constraints in the runtime.

Override `AutomationInventoryElement.slotVisible(slot, Presentation)` to select visible slots.
The read-only context supplies copied group labels and local-slot contents, plus filter support.
Both native activation and fragment rendering use this policy; filter-row height and controls follow
it too. Open/selected-group checks remain in the menu. Pending requests independently disable native
interaction, so slots do not disappear while a request is in flight. Visibility does not override
storage acceptance or server authorization. Keep the policy consistent on both menu sides.

Override `drawerValue(element, maximum, Presentation)` for renderer and tooltip values, including
maximums. Defaults expose copied slot contents or the group label and return null for maximums.
The drawer delegates without selecting values by element type. Render contents and hints remain
separate services, so custom display values do not change the actual inventory.

`MachineScreen.nativeSlotAt(x, y)` resolves the first active native slot in screen coordinates using
its overridable `hitBounds`, vanilla's one-pixel tolerance and menu order. Drawer click fallback and
native-tooltip ownership use this hit result instead of slot frames. Enlarged/offset hits can reach
vanilla interaction; shrunken hits leave the surrounding frame to the drawer. Empty-slot tooltips
compose against the hit element, and an occupied slot suppresses the fragment tooltip only on a
native hit. Drawer controls/editor regions retain priority, and pending slots cannot receive hits.


`ItemSlotElement.MenuSlot` and `PlayerSlotElement.PlayerMenuSlot` are subclassable with protected
constructors. Override the element's slot factory to return your adapter subclass and refine its
rules while retaining reservation handling or vanilla equipment behavior. A custom client storage
can implement `api.capabilities.ReservationReceiver.setPlan` to receive synchronized plans through
the default item adapter; implement `ReservedSlots` for the corresponding reservation rules.
There is no dependency on the built-in `ClientMenuItems` implementation in that adapter.


Slot elements also own native presentation policy. `renderNative()` controls whether the bridge
calls vanilla's slot drawing (default: true except for ghosts). `nativeBounds(vanillaBounds)` chooses
the destination rectangle; the bridge translates and scales vanilla's 16x16 drawing to it and restores
the graphics pose afterward. `highlightBounds` and `hitBounds` default to that rectangle but can be
overridden independently, including offsets and different widths/heights. All rectangles use panel
coordinates, with the original native slot rectangle supplied as input. Default bounds use the
configured item size. Native hit routing retains active-slot checks and vanilla hover tolerance;
non-slot hit tests retain their original bounds. Use the existing element rendering contract for
custom artwork, and disable native drawing when that artwork replaces the item. No new registration
is needed for these policy hooks.


## Statistics drawer

The left Statistics drawer shows sections supplied by machine parts through
`api.stats.StatsProvider`. `StatsMenuComponent` samples this interface without knowing concrete
storage or processor classes. Return the contributing parts from `ScreenHost.screenStats()` in display order:

```java
@Override public List<StatsProvider> screenStats() {
    var parts = new ArrayList<StatsProvider>();
    parts.add(energy);
    parts.add(items);
    parts.add(fluids);
    parts.addAll(lanes);
    return List.copyOf(parts);
}
```

Return stable part instances so the menu can retain counter history. Repeated references to the
same part are sampled once. Providers run only on the server, once per 20 game ticks per host,
shared by its viewers; page selection is local and uses the synchronized component field.

A provider returns `Statistics`: a translated section title, rows with stable IDs, and optional
energy-demand contributions. Built-in values are cumulative `Counter`, exact `Rate`, explanatory
`Text`, and aggregate `MaximumUse`. Each row declares its label, unit, precision and whether it
scales with time. `row.whenUsed(resource)` hides it unless a provider reports positive demand for
that exact resource. Empty sections add no title or rows.

`Statistics.Value` is an open interface. A value owns `sample(StatsSamplingContext, previous)`,
returning a `Statistics.Sample` whose `format(StatsFormatContext)` produces a styled `Component`.
Sampling runs once per update, regardless of the number of timescales. Formatting must use the
sampled data without changing history or reading live storage. No statistic registration, renderer
or custom network codec is needed:

```java
record Percentage(double fraction) implements Statistics.Value {
    @Override public Statistics.Sample sample(StatsSamplingContext context,
                                             Statistics.Sample previous) {
        long percent = Math.round(fraction * 100);
        return format -> Component.literal(percent + "%");
    }
}
```

Stateful values can reuse `previous`; it is scoped to provider identity and row ID. It is null on
first appearance, after removal, when game time moves backward, or when the value class or its
`samplingKey()` changes. Return an immutable configuration key (for example the counter window),
excluding the current reading. Fresh value descriptions and reordered rows retain history. The
sampling context exposes game time and aggregated resource demand; the formatting context exposes
the timescale, precision and units, plus `ticks()` and `number(...)` helpers. Numeric conversion
and rolling history helpers live in `api.stats.StatsNumbers` and `CounterRate`.

Hosts supply ordered timescale data through `screenStatsTimescales()`. Defaults are tick, second
and minute. Add or replace entries without registration:

```java
@Override public List<StatsTimescale> screenStatsTimescales() {
    var scales = new ArrayList<>(StatsTimescale.DEFAULTS);
    scales.add(new StatsTimescale(ResourceLocation.parse("example:hour"), 72_000,
        Component.translatable("example.stats.per_hour"),
        Component.translatable("example.stats.hour_unit")));
    return List.copyOf(scales);
}
```

Timescales have a stable namespaced ID, positive tick duration, selector label and rate-suffix unit.
A host may return up to 16 uniquely identified entries, including an empty list. The drawer retains
the selected ID across reordering and falls back to the first remaining page when it disappears;
empty lists have no selection and recover when pages return. Left/right clicks cycle forward/back.

`ScreenStats` only coordinates providers, sample lifecycle, combined demand and section assembly.
`StatsMenuComponent.PAGES` synchronizes bounded `StatsPage(id, label, lines)` values; the client
never needs the custom value implementation. The field schema is version 2, reflected in menu
fingerprints. Each section supports at most 64 distinct rows, each page at most 4,096 lines, and
the encoded field at most 64 KiB. `PAGES`/`pages()` replace the fixed `LINES`/`lines(unit)` API.

Built-in providers:

- `ItemStorage` and `FluidStorage` return `Statistics.EMPTY`.
- `RecipeProcessor` uses its display name as header, followed by measured cycles and maximum
  cycles per selected timescale. The maximum is based on the active recipe's duration; without an
  active plan it says No recipe. A manually interrupted processor has released its plan, so its
  maximum is unavailable until it reserves again. The just-finished plan bridges the one-tick
  gap before the next recipe. Idle/reset processors do not retain stale recipe limits.
- `EnergyBuffer` shows RF in and its maximum transfer rate when it supports input, and RF out and
  its maximum transfer rate when it supports output. RF used and RF max use appear only while
  registered processors report positive current-recipe demand for this buffer. Generators and idle
  buffers omit both usage rows. Beneath the Energy header there are two rows per supported
  direction, plus two usage rows when applicable.

Input/output maxima follow the selected timescale: the configured transfer limit is the RF/t
baseline, multiplied by 20 for RF/s or 1,200 for RF/min. This uses one transfer per tick;
the capability still enforces the limit per call, so multiple callers can exceed this nominal
rate. Measured input/output continues to report actual total flow.
Maximum processing use is the sum of each registered processor's active recipe peak
`ceil(recipe energy / recipe duration)` for this buffer, scaled to the selected time unit.
`EnergyGroup.statisticsResource()` identifies the backing storage; `StorageGroups.energy` returns
the exact buffer instance. This attributes demand correctly when lanes share buffers or a machine
has multiple independent buffers. Register all contributing processors, not just the buffer.

`EnergyBuffer.insert` counts incoming energy, `extract` counts outgoing energy, and `consume`
counts processing use separately. Simulations, direct assignments, loading and resizing do not
count. Recipe processors call `EnergyGroup.consume`; its default delegates to `extract` for custom
implementations, while the built-in adapter records use separately. Transactions restore all
three counters along with stored energy on rollback. Creative Power refills through `insert`.
Counters are transient; saved NBT remains unchanged.

Measured RF uses the most recent 20 ticks. Completed cycles use up to 1,200 ticks (60 game seconds),
warming up from menu opening. Stalls, interruptions and unsuccessful output commits do not count
as completions. Rates use game time at 20 ticks per second, not wall-clock time. A first sample
shows a dash until enough time has elapsed. RF values are grouped integers; cycles have exactly
two decimal places. For example: 24,000 RF/min and 12.00 cycles/min.

The compact header selector cycles the host-provided pages, defaulting to Per tick / Per second /
Per minute; right-click reverses. Labels and styled rows come from the synchronized pages.
Info, Statistics and equipment drawers replace each other on the left. Right-side automation
and redstone drawers remain independent. Statistics scrolls when needed, reserves its bounds
from JEI, closes on Escape, and remembers its open state for the connection.

## Registering XML tags

Every screen tag, including built-ins, resolves through `ScreenTags`. Each registration supplies
its factory, XML attribute contract, allowed children and placement groups. `ScreenReader` handles
secure parsing and delegates construction to the registry. The runtime XSD is compiled from those
registrations: adding a tag also adds its validation contract, without editing MMDLib's schema.
The bundled `screen.xsd` remains the built-in type library and an editor reference for built-ins.

Register during mod construction, on both physical sides, before screen loading freezes the
registry. For example, assuming your mod defines a `TemperatureElement`:

```java
ScreenTags.GLOBAL.register(new QName("https://example.org/gui", "temperature"),
    ScreenTag.element(TemperatureElement.class, xml -> new TemperatureElement(
            xml.common(), xml.size("width", 1), xml.size("height", 1), xml.attribute("unit")))
        .attribute("width", ScreenAttribute.defaulted("pixels", "32"))
        .attribute("height", ScreenAttribute.defaulted("pixels", "18"))
        .attribute("unit", ScreenAttribute.choices("celsius", "kelvin").withDefault("celsius"))
        .build());
```

Imports are `javax.xml.namespace.QName` and `gui.load.ScreenTags`, `ScreenTag`, `ScreenAttribute`.
Use the tag inside ordinary built-in containers:

```xml
<screen xmlns="https://moddev.zone/mmdlib/screen"
        xmlns:example="https://example.org/gui"
        title="screen.example.press" block-entity="example.PressBlockEntity">
  <vbox>
    <example:temperature id="temperature" value="#getTemperature" unit="kelvin"/>
  </vbox>
</screen>
```

Tag identity is the namespace URI plus local name; prefixes are arbitrary. A URI identifies a
namespace and is never downloaded. `register("my-tag", definition)` uses the standard screen
namespace. Prefer a mod-owned namespace to avoid collisions. Duplicate registrations fail;
`replace(QName, definition)` explicitly replaces both the factory and XML contract before freeze.

`ScreenTag.element` includes common attributes and membership in the `widget` placement group.
`ScreenAttribute.required(type)`, `optional(type)` and `defaulted(type, value)` accept standard
XSD types such as `xs:string`, `xs:int`, `xs:boolean`, or shared types such as `pixels`, `length`,
`color`, `resourceId` and `translationKey`. Enum choices are supported directly. The model's
validation/value contracts still apply after construction.

For containers, add `.children("widget", 0, -1)` and construct children with `xml.children()`.
The count `-1` means unbounded. Each child goes through the registry, including nested mod tags.
`.children(QName, min, max)` accepts one exact child tag. Register configuration-only children
with `ScreenTag.data(DataRecord.class, factory)` and read them with `xml.children(DataRecord.class)`;
they do not need to be UI elements. Mods can define their own child groups by assigning tags
`.group("example_options")` and accepting that group in their parent contract.

Built-in placement groups include `widget`, `slots`, `buttons`, `views`, `radios`, `controls`
and `dock`. `.group(...)` adds membership; `.groups(...)` replaces the default memberships.
For example, an XML factory returning a custom `SlotElement` can join `slots` to work inside
`<inventory>`. Register additional inventory-row controls in `buttons`. Context/view and radio
containers still expect their model types when parsing children; group membership does not
bypass those type checks.
Root `dock` membership places the resulting element in `Screen.tabs()`.

`ScreenXml` supplies the current node/source, shared `common()`, text/ID/colour/size helpers,
translation-key expansion, recursive child construction and `error(message)` with file/line.
Keep client drawing and local interaction in `ElementRenderers` and `ElementBehaviors` as above.

Namespaced tags survive inheritance, fills, generated content and embedded machine screens.
`ScreenReader.Node` retains the namespace; generators can use its namespace-bearing constructor.
For tools/tests, use `ScreenTags.builtins()` and the explicit-registry overloads of `ScreenReader.read`
or `ScreenLoader.read`. Unknown tags/attributes, invalid values and illegal child placement remain
errors. DOCTYPE, external entities and XInclude remain disabled; schema imports resolve only to
registered, in-memory contracts.

## Registering custom widget types

A consuming mod can add a leaf widget without replacing MMDLib's screen or menu. Register its
common-side contract in the mod constructor, before screen discovery:

```java
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.gui.WidgetType;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.value.ValueTypes;

WidgetTypes.GLOBAL.register(ResourceLocation.parse("example:temperature"),
    new WidgetType(Set.of(ValueTypes.WHOLE, ValueTypes.DECIMAL, ValueTypes.NUMBER), widget -> {
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

Tab and Shift+Tab move between visible focusable element behaviors in document order. While one is
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
    new WidgetType(Set.of(ValueTypes.WHOLE), widget -> {
        if (widget.common().value().isEmpty())
            throw new IllegalArgumentException("Limit slider needs value");
    }, List.of(setLimit)));
```

The relevant imports are `gui.WidgetAction`, `gui.WidgetType`, `gui.WidgetTypes`,
`gui.value.ValueTypes`, `net.minecraft.network.codec.StreamCodec`, and the ordinary Java
collections. Codecs use `RegistryFriendlyByteBuf`, so structured records and registry-aware
values are supported. Register matching action IDs and codecs on both sides. Each action declares
a maximum encoded size of 1â€“4096 bytes. The envelope is bounded before the mod codec runs, the
encoder uses a bounded buffer, and trailing bytes are refused. **Codecs must additionally bound
their own string and collection lengths before allocating.**

The client instance calls `access.submit(setLimit, previewValue)`. False means the widget is
hidden, another request is pending, an earlier result still needs delivery, or encoding failed.
Codec runtime exceptions (including buffer overflow) are logged and return false without sending
a packet or marking the widget pending. Do not submit every render frame. For sliders, commit on
release; throttle continuous controls yourself.

The server checks menu ownership, interaction distance, visibility, the element's allowed action,
menu-session token and increasing request number before dispatch. At most eight typed actions
per menu per server tick are processed; excess edits receive an immediate rejection using the last
sent values, without invoking their codec, getters or handler. Replayed requests are dropped. Handler
exceptions are logged and rejected, but MMDLib cannot undo a handler's partial mutations.

The response includes accept/reject, fresh authoritative `value`/`max` snapshots and typed element
state after the handler (cached values/state for rate-limited edits). They are applied before
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

`gui.runtime.ElementActionSession` owns this per-menu lifecycle: request IDs, pending/results,
timeout recovery, replay/rate checks, acknowledgment baselines and next-broadcast correction keys.
`MachineMenu` delegates action operations and supplies player/menu/distance authorization plus fresh
snapshots. Removing the menu closes the action session, clears pending/results and refuses later
requests or acknowledgments. Covering its screen leaves the menu session active.

Direct integrations use `sessionId()`, `prepareElementAction`, `elementPending`,
`receiveElementResult`, `takeElementResult` and `tickElementActions`. `tickClientActions` also ticks
registered menu components. The older widget-named methods are forwarding aliases; the existing
`ScreenNetwork.Widget`/`WidgetResult` envelopes and wire IDs are retained. No action-session registry
or additional registration is needed.

The companion Settings view demonstrates all three cases: a named machine, an energy-reserve
slider that holds processing below the chosen charge percentage, and a Run/Pause selector whose
payload contains both lane index and state. The server restricts that selector to the lane named
in its XML property. These settings persist with the machine. The graphical smoke run checks
focus, typing, cancellation, resizing, view changes, release outside bounds and server rejection.

The expanded menu protocol is version **14**, including a session token and registered drawer
definitions in menu-opening validation, plus typed element state in deltas and acknowledgments.
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

## Inherited screens

Use `abstract="true"` for a template and `extends="namespace:screen_id"` for a child. Templates
are discovered across mod namespaces but are not bound or opened themselves. Each concrete screen
still has its own file ID and must provide its own `title`. It can inherit `block-entity` and
`inventory`, or override either; getters bind against the resulting block-entity class.
Inheritance does not replace the type-ID screen lookup described above.

```xml
<!-- mmdlib/screen/base_press.xml in mod example -->
<screen xmlns="https://moddev.zone/mmdlib/screen" abstract="true"
        block-entity="example.PressEntity">
  <vbox padding="7">
    <placeholder name="machine" required="true"/>
  </vbox>
  <placeholder name="help">
    <popout kind="info" text="{machineId}.info"/>
  </placeholder>
</screen>
```

```xml
<!-- mmdlib/screen/press.xml in mod example -->
<screen xmlns="https://moddev.zone/mmdlib/screen" extends="example:base_press"
        title="block.example.press">
  <fill name="machine"><slot id="input" index="0"/></fill>
</screen>
```

- Children supply content only through top-level `<fill name="...">` elements. A fill replaces
  the named placeholder's contents; an empty fill removes its default content.
- An unfilled optional placeholder uses its children, or nothing when it has none. A required
  placeholder must be filled and cannot also have default content.
- A placeholder may instead supply `generator="namespace:id"` and generator options.
  An explicit fill replaces that generator, even when empty; a fill can choose its own
  generator. Generated fills cannot also contain literal children. Generators return normal
  screen elements, which undergo the same expanded validation. See
  [machine screen generators](xml-machines.md#generated-screen-fills) for the available
  machine-aware generators and registration API.
- Chains can introduce placeholders and override earlier fills. Replacing an outer placeholder
  removes its nested extension points; also filling a removed inner point is an error.
- Unknown bases/fills, duplicate placeholders/fills and cycles fail at load. Expanded slots, IDs,
  widget structure, bindings and layout are validated together. Errors retain the originating
  file and line; a sibling's fills never mutate the shared template.

`{machineId}` in translation keys expands to the concrete screen ID, including namespace and any
path, after inheritance: `{machineId}.info` becomes `example:press.info`, never the base ID.
It works in `text`, `title`, `label`, `tooltip`, `tooltip-shift` and button `value-labels`.
Literal `*-fallback` strings and runtime placeholders such as `{value}` / `{max}` are unchanged.
Declare the resulting key in the consuming mod's language file.

### Library powered-machine screen

MMDLib ships `mmdlib:base_powered_machine`, paired with
[`BasePoweredMachine`](block-entities.md#basepoweredmachine). A one-processor child can be:

```xml
<screen xmlns="https://moddev.zone/mmdlib/screen"
        extends="mmdlib:base_powered_machine" title="block.example.press">
  <fill name="machine">
    <slot id="input" index="0"/>
    <progress value="#getProgress" max="#getTime"/>
    <slot id="output" index="1"/>
  </fill>
</screen>
```

The base supplies the entity binding, padded layout, right-side energy gauge, default player
inventory, keyless processor control and left info popout. Its info text is `{machineId}.info`,
with dynamic `#getWarnings` from the entity. Override `block-entity` when child widgets use getters
available only on the concrete subclass. Inventory/redstone/statistics drawers still come from
runtime host interfaces; XML inheritance does not configure their backing data.

| Placeholder | Default |
|---|---|
| `body` | Entire padded machine area; replace for layouts with contexts/settings views |
| `heading` | Empty, above the machine row |
| `machine` | Required machine-specific contents, before the energy gauge |
| `inventory_controls` | `<inventory-controls id="inventory_controls"><processor-button id="toggle_0"/></inventory-controls>` |
| `info` | `<popout id="info" kind="info" edge="left" text="{machineId}.info" value="#getWarnings"/>` |

Replacing `body` also replaces its nested `heading` and `machine` placeholders. Machines without
a recipe processor use `<fill name="inventory_controls"/>`; generators can also set
`inventory="none"`. Multi-processor machines can fill that control area with explicitly keyed buttons.

### Generic processor control

```xml
<processor-button id="pause"/>
<!-- Or select a particular registered RecipeProcessor: -->
<processor-button id="pause_second" processor-key="lane_1"/>
```

The host exposes a `FeatureHost` through `screenFeatures()`; `BasePoweredMachine` supplies it.
An omitted key selects the first registered **recipe processor**, skipping other features. An
explicit key must match exactly and never falls back; blank keys and hosts with no matching
processor are errors. The menu validates this before opening and resolves clicks on the server.
The control owns its state binding and pause action, so it needs no per-machine getter or click
handler. Do not supply its `value`, `max`, `on-click`, `on-right-click`, `shows`, `icons` or `value-labels`.

Icons show the current state, not the next action:

| Processor state | Icon | Tooltip lines / click |
|---|---|---|
| Manually paused | Pause | `Paused` / `Click to unpause` |
| Processing | Stop | `Working` / `Click to pause and release reservations` / `Progress is kept` |
| Waiting for energy | Stop | `Needs power` / same pause action |
| Cannot complete the recipe | Stop | `Recipe cannot finish` / same pause action |
| Held by the host | Stop | `Held by machine controls` / same pause action |
| Idle | Play | `Idle` / `Click to pause` |

Tooltips are compact and multiline. Manual pause releases reservations, keeps resumable progress,
marks the machine changed and synchronizes immediately. Redstone/host holds preserve reservations
and cannot override a manual pause. Removing a redstone hold never resumes a manually paused lane.

When input watching is configured, a small red dot at the button's bottom right marks disagreement
between manual pause and the redstone gate. The extra tooltip explains either that manual pause
takes priority or that redstone is preventing processing. The redstone drawer icon has a small
green play overlay when watched inputs allow processing, or a yellow pause overlay when they
block it. With no input watching, neither the overlay nor conflict dot is shown.

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
Write `<player-inventory/>` to move the inventory and its default equipment column, or place
the three sections separately with `<player-inventory/>`, `<player-armour/>` and
`<player-offhand/>`. Each section may appear once; omitted sections retain their default placement.
Set `inventory="none"` on `<screen>` to leave out all three sections.

```xml
<hbox spacing="6">
  <player-armour/>
  <player-inventory/>
  <player-offhand/>
</hbox>
```

Here the armour column is on the left and the offhand is independently placed on the right.
Containers and offsets work normally. When equipment is explicitly placed elsewhere, the inventory
section is 162 pixels wide; omitted equipment reserves the default right-hand column.

Shift-clicking equipment moves it into the ordinary inventory/hotbar. Machine outputs also
go there; they do not automatically equip armor or fill the offhand. Shift-clicking ordinary
inventory slots keeps the machine's `screenDestinations` routing. JEI recipe transfer uses
only the ordinary inventory and hotbar, leaving equipped items alone.

Optional `<inventory-controls>` goes after the main body and before tabs/popouts. Its button
children form a right-aligned row above the player inventory (for example manual pause
toggles). Automation uses the reusable `ColourButtonBar<K>` on the left, with
scrollable overflow. Both sides align with the nine-column storage grid, leaving the equipment
column clear. Flat controls fit in the inventory's existing top row; taller controls add only
the extra height they need.
Use ordinary button declarations inside it; no spacer or coordinates are needed. This element
requires a player inventory and is available to every screen, not just companion machines.

Elements own group associations through `Element.associatedGroups(screen, groups)`. The default
matches the element ID against each `ElementGroup`'s explicit anchors, or matches an unanchored
group's resource type/index to `resourceBinding()`. `ResourceBinding.groupType()` maps unqualified
kinds such as `fluids` to `mmdlib:fluids`; mod kinds can use namespaced IDs directly, without a registry.
`SlotElement` additionally matches unanchored item groups by named inventory and local slot index;
unscoped slots use the group's `legacyIndices`. The three-argument `ElementGroup.Members` constructor
uses the same indices for both address spaces; generated machine metadata supplies them separately.
Explicit anchors always take precedence over resource/member fallback.
`ElementGroup` carries read-only IDs, anchors and optional resource-member metadata, without
exposing storage or automation operations. Mods can override the hook directly, including in
subclasses, and combine their associations with `super.associatedGroups(...)`.
`Element.highlightedGroups(screen, groups)` defaults to those same associations. Override it to
opt out of highlighting or choose a different set while retaining the ordering associations.
The drawer and generated-machine highlight compilation both use `GroupHighlights.of` to resolve
these hooks. Unknown group IDs are ignored, duplicates are removed per element, and targets retain
document order. A selected container can override `replacesChildHighlight(screen, descendant, group,
groups)` to represent a descendant's highlight for that group. Only actual ancestor targets can
suppress descendants; an unnamed container cannot suppress named targets during generated binding
compilation. `ColouredGroupElement` represents slots in its child inventory scope by default, leaving
nested scopes independent. Override its association or replacement hooks to change that policy.
Registered `HighlightPresentation` still owns drawing, visibility filtering and clipping.

`GroupOrder.of` walks every element in document order (including inactive views), keeps the first
occurrence of each declared ID, then appends unassociated groups in declaration order. Unknown
IDs are ignored; declared IDs must be unique. It accepts an association function and has no slot
or resource-type checks. `ColourButtonBar<K>` takes ordered `Entry<K>(id, colour)` values and a
selection callback; it owns drawing, overflow, hit testing and selection visibility. Its RGB
colours and IDs can represent any caller's data. `SideConfigDrawer` supplies the metadata and
maps the resulting order back to stable automation indices. No additional registry is involved.

`PlayerInventoryElement` owns the 36 storage/hotbar slots; `PlayerArmourElement` owns the four
armour slots; `PlayerOffhandElement` owns the offhand. Their shared `PlayerSlotsElement` base exposes
ordinary `PlayerSlotElement` children, inheriting item-slot rendering. `PlayerSlotElement` owns
player-slot creation, empty equipment icons, armour acceptance/stack limits, equip callbacks and
binding-curse pickup restrictions; `ItemSlotElement` owns machine-storage interactions.
`MachineMenu.playerSlotElements()` identifies player backing through `playerInventory()`, independently
of transfer roles, and retains native registration order.

`SlotElement.playerInventory()` opts any implementation into player-slot assembly. Override
`createPlayerSlot(inventory, bounds, visible)`, `role()` and `playerSlotOrder()` to control the native
adapter, transfer routing and ordering. Lower order values come first; equal values retain tree
traversal order. `PlayerSlotElement` supplies vanilla equipment/storage roles and the usual main
inventory, hotbar, armour, offhand order. Subclasses may override each hook independently.
Custom player slots can appear in ordinary containers without a standard player-inventory region;
no registry is needed. Keep these definitions deterministic on client and server, and include
additional configuration in the element's model equality/text used by screen fingerprints.
All three containers accept `(Common, IntFunction<? extends SlotElement>)` constructors. The factory
runs once per standard player index and must return a player-backed slot with that index. Its
stable children participate in traversal, validation, layout, equality and screen fingerprints.
For example, `new PlayerArmourElement(common, MyArmourSlot::new)` keeps the standard armour layout
and uses the custom slots' appearance, native factory, role and ordering hooks. Constructors do not
call overridable methods on a partly initialized section. Custom `PlayerSlotsElement` subclasses
can instead pass a collection of already-created slot children to its protected constructor and
supply their own positioning.

All three containers are extensible. Default sections are stable implicit model roots, included
in screen traversal, validation and fingerprints. `MachineScreen` uses the same rendering and
behavior traversal for every element, without a separate player-slot pass.

Inventory placement owns the toolbar policy, automatic footer and extra space for tall controls.
`ScreenLayout` composes the measured regions and supplies generic placement operations.
Offsets on an explicit inventory or its parents move native slots, toolbar and controls together.
Implicit equipment follows the inventory's visibility; explicitly placed sections follow their
own parents. Player slots declare their backing inventory, so machine storage validation and
group-membership fallback do not treat them as machine slots. Native slot numbering and transfer
roles remain stable regardless of section placement.
`PlayerInventoryElement.slot` describes the default arrangement;
use `layout.bounds(slotElement)` for independently placed sections.

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
  20Ã—20 icon-button size by default. Set `flat="true"` for a 14Ã—14 control with a 12Ã—12
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
screen anyway, or a named value provider. Every open screen belongs to exactly one block entity (the menu is created for
the block at a position), and that instance is what the getters are called on:

| Form | Resolves to | Called as |
|---|---|---|
| `#method` | A public instance method on the screen's `block-entity` class (or a superclass or interface of it), taking no parameters | `blockEntity.method()` |
| `#method(index)` | A public instance getter taking one fixed nonnegative `int`, e.g. `#getTankFluid(0)` | `blockEntity.method(index)` |
| `Class#method` | A public **static** method taking the block entity (or a supertype) as its only parameter, e.g. a helper shared by several machines | `Class.method(blockEntity)` |
| `@namespace:id` | A value provider registered under that ID, by code or a CraftTweaker script | `provider(blockEntity)` |

A value provider is registered with
`ScreenValueProviders.GLOBAL.register(id, valueType, blockEntity -> value)` during mod
construction, on both sides, like a value type; the registry freezes when screens load. It
declares the value type it returns (`ValueTypes.WHOLE`, `DECIMAL`, `BOOLEAN`, `TEXT`, ...), which
is checked against the element at load like a getter's return type; an unknown provider fails
with the file and line. Providers suit values that don't belong on the block entity class, and
let a pack's [CraftTweaker scripts](crafttweaker.md#screen-values) supply values to XML screens.

Resolution happens in two steps. At load, each reference is looked up from the class and name
alone (`findVirtual` / `findStatic`) and checked; no instance is involved. When a player opens
the screen, the server binds the handles to that menu's block entity, after checking it is an
instance of the declared `block-entity` class. An instance method on any other class has no
instance to call and is rejected at load.

Indexed references support generated gauges for multiple tanks without one Java
getter per tank. For example, `value="#getTankFluid(0)" max="#getTankCapacity(0)"`.

Use `<fluid-tank tank="0" length="3s"/>` for a complete tank component. The host only
exposes its `FluidStorage` through `ScreenHost.screenFluids()`; XML-defined machines provide
this automatically. `FluidTankElement` owns binding validation, fluid/capacity snapshots,
tooltips and container transfers. It needs no value getters or machine-specific callbacks.
It rejects explicit `value`, `max` and `interaction` attributes so display and interaction
cannot accidentally target different tanks. `<gauge tank="0" length="3s"/>` is an alias
for the same component; a plain `<gauge>` remains a general display with getter bindings.

With a fluid container on the cursor, **left-click fills the tank** and **right-click drains
it into the container**. Input and manual tanks can be drained by players. Insertion retains
fluid/recipe filters and rejects output tanks; both directions respect reservations. Each
click handles one container, including partial modded containers. Stacked results go into the
player inventory (or drop when full); creative containers remain unchanged.

Getters and element-owned snapshots run only on the server, each tick while the screen is open.
The client only receives their values.

Explicit `value` and `max` attributes name Java getters. Resource components own their snapshots:
`fluid-tank` reads `ScreenHost.screenFluids()` and `energy-gauge` reads `ScreenHost.screenEnergy()`.
Neither component needs value/capacity getters on the block entity.

The loader checks at startup that each method exists, belongs to the right class, and returns
a supported type. A fluid gauge's getter returns the fluid itself (its type and amount); energy
and other numbers return a number. Values reach the client through MMDLib's own sync packets, which send only
the values that changed and carry numbers of any size: vanilla's menu sync is limited to 16 bits,
and some values (ProjectE's EMC, for one) exceed even a Java `long`.

## Attributes shared by every element

| Attribute | Runs on | Purpose |
|---|---|---|
| `id` | client | Names the element so others can refer to it (`hover-shows`, `side-config groups`); unique within the screen |
| `value`, `max` | server â†’ client | What the element shows (see [Values](#values)) |
| `tooltip` | client | Translation key; `{value}` and `{max}` are filled in, with large numbers shortened (`40.2 E`) |
| `tooltip-shift` | client | Detailed tooltip shown while Shift is held; a "Shift for more" hint is added automatically |
| `on-click`, `on-right-click` | server | Handler IDs from extension files; the handler receives the player, menu, element, and held modifier keys |
| `on-shift-click`, `on-shift-right-click` | server | Optional: used instead of the plain handler while Shift is held |
| `click-sound` | client only | Sound event ID played for the player when the element is clicked, as an interface sound like vanilla's button click. Any sound in a `sounds.json` works, registered or not; see [sound events](definition-loading.md#sound-events) |
| `interaction`, `interaction-target` | server | Reusable registered behavior and its screen-authored target; supported on every element except inventory slots |
| `on-hover` | client only | Handler run when the mouse enters and leaves the element; display only, it cannot change game state |
| `hover-shows` | client | ID of an element to show while hovering, without any handler |
| `offset-x`, `offset-y` | client | Pixel nudge after layout |

Clicks are sent to the server through the menu's button channel, and the server checks the
player, menu, and element before running the handler. Hover never reaches the server.

### Reusable element interactions

An interaction belongs to the element, regardless of whether Java, XML, or a generator created
the screen. For example, a label can use the same fluid transfer behavior as a gauge:

```xml
<label text="example:tank.transfer" interaction="mmdlib:fluid-container" interaction-target="0"/>
```

Register additional behaviors during mod construction with `ElementInteractions.register(id, type)`.
Implement `ElementInteractions.Type.validate(target)` to reject invalid configuration and
`click(ScreenClick, target)` to execute the server action. `Element.interaction()` exposes the
binding stored in `Common`; Java callers can use `FluidTankElement.containerInteraction(index)`
to reuse that component's transfers on another element. The registry delegates to the component;
it contains no fluid transfer implementation.
Targets are included in the screen fingerprint and read from the server's screen definition.
Click packets supply an element index, mouse button and modifiers, never a storage target or item.

Explicit interactions take priority over the element's usual click behavior, including navigation
and JEI links. A handler returning `true` consumes the click and refreshes the menu snapshots;
returning `false` allows the server's ordinary click callback to run. Client-only navigation/JEI
does not run as a fallback. Shift, Control and Alt are available through `ScreenClick`; the standard
fluid interaction uses the same left/right directions with those modifiers. Menu ownership,
distance, visibility and packet fields are checked before dispatch. Slots keep vanilla inventory
handling and reject `interaction` attributes.
`GhostSlotElement`'s full native factory delegates to `createSlot(bounds, visible)` on the original
element instance. Subclasses can override either overload; menu assembly preserves that dispatch
on both sides, along with virtual positioning. Real storage and player-slot factories remain
separate overridable hooks.


Self-contained components implement the generic `Element` hooks: `validate()`,
`resourceBinding()`, `validateResources(counts)`, `interactive()`, `interact(ScreenClick)`,
`snapshot(ScreenHost)`, `tooltipText()` and `tooltip(ScreenHost, carried)`.
Generated-machine validation calls `validateResources(ResourceValidationContext)`. The immutable
context supplies the screen, resource counts, named inventory sizes and named binding sets, with open
string keys. Its default delegates to the existing `validateBindings(bindings)` and
`validateResources(counts)` methods. Slot elements additionally validate their own scoped or legacy
index, using virtual `ghost()`/`playerInventory()` policies. Mods can override the context hook,
including on slot subclasses, without generator-side type checks or a new registry. The machine
adapter only supplies definition data and attaches XML source diagnostics to validation failures.
The menu dispatches runtime hooks without inspecting the element's resource type. `FluidTankElement` owns all tank UI behavior,
including the single-tank player view; the underlying storage still enforces filters and recipe
reservations. Its client renderer reuses the glass gauge drawing code and never touches storage.

## Elements

| Element | Purpose | Notable attributes |
|---|---|---|
| `screen` | Root | `title`, `block-entity`, `inventory`, `abstract`, `extends` |
| `placeholder`, `fill` | Template extension point / child replacement | `name`; placeholders can be `required` |
| `vbox`, `hbox` | Layout boxes | `padding`, `spacing`, `align` |
| `spacer` | Fills leftover room in a box | |
| `group` | Recessed grouping panel | `label`, `tint`, `padding` |
| `coloured-group` | Outline around exactly one child, without added padding | `color` |
| `slot` | Item slot from the menu | `index`, `size` (`normal`/`large`), `kind` (`normal`/`upgrade`/`ghost`; `upgrade` holds one item), `tint`, `icon` |
| `gauge` | General glass graduated display ([gauge textures](gauge-textures.md)) | `orientation`, `length` (size or `fill`), `color` (solid fill) or a fluid value; `tank` selects the `fluid-tank` component |
| `fluid-tank` | Complete tank component with synchronized contents, tooltip and container transfers | `tank` (storage index), `orientation`, `length`; no `value`, `max` or `interaction` |
| `energy-gauge` | Energy component with synchronized contents and an FE tooltip | Defaults: vertical, `length="54"`, `color="#E64B38"`; style and tooltip can be overridden; no `value` or `max` |
| `meter` | Compact segmented gauge | `orientation`, `thickness` (5 or 10), `length` (size or `fill`), `color`, `segment`, `gap`, `gap-color` |
| `progress` | Fill-in progress indicator | `icon` (default arrow), `direction`, `jei-recipes` |
| `sprite` | Static GUI-atlas sprite | `sprite`, `width`, `height`, optional `tint` |
| `animated-sprite` | Sequence of GUI-atlas sprites | `frames`, `width`, `height`, `ticks-per-frame` (default 1), `loop` (default true), optional `tint` |
| `progress-sprite` | Background and progressively revealed foreground | `background`, `foreground`, `width`, `height`, `direction`, `value`, `max`, optional `tint` / `background-tint` |
| `label` | Text | `text` |
| `button` | Vanilla-style button | `text` or `icon`, `tooltip` (required), `shows` (`context:view`) |
| `processor-button` | Generic server-controlled pause/stop/resume button | Optional `processor-key`; built-in state icons, tooltip and action |
| `inventory-controls` | Controls above player slots, right-aligned alongside the colour selector | Top-level container after the machine body |
| `checkbox` | Checkbox | `text` |
| `radio-group`, `radio` | Radio buttons; the group's value is the selected index | `label`, `text` |
| `side-tab` | Tab growing out of the panel edge | `edge` |
| `context`, `view` | Area whose content buttons swap; one alternative content | `id`; `id`, `title` |
| `popout` | Information pop-out on an edge tab | `kind` (`info`/`warning`/`error`), `icon`, `edge`, `text` |
| `side-config` | Six-face IO configuration, one resource group at a time | `groups` (element IDs) |
| `player-inventory` | Explicit placement of the player inventory | |
| `player-armour` | Explicit placement of the four armour slots | |
| `player-offhand` | Explicit placement of the offhand slot | |

### Reusable sprites and group outlines

`SpriteElement` is an extensible base class. `AnimatedSpriteElement` selects frames and
`ProgressSpriteElement` selects layers and fill clipping; the shared client `SpriteRenderer`
draws those layers without knowing machine state. Sprite IDs refer to the GUI atlas, just like
button icons. Both subclasses can be used directly in Java or through XML:

```xml
<sprite sprite="example:logo" width="24" height="16"/>
<animated-sprite frames="example:fan_0 example:fan_1 example:fan_2"
                 width="16" height="16" ticks-per-frame="2"/>
<progress-sprite background="example:arrow_empty" foreground="example:arrow_full"
                 width="24" height="16" direction="right"
                 value="#getProgress" max="#getTime"/>
```

Animation starts with the screen; `loop="false"` holds the last frame. Progress clamps to
0â€“100%, fills right/left/up/down, and leaves custom artwork unrotated. The existing `<progress>`
processor arrow composes a `ProgressSpriteElement`, retaining its default arrow rotation and
darkened custom-icon background.

`ColouredGroupElement` decorates exactly one inner element, which can itself contain any number
of children. It preserves that child's dimensions and stretching and paints its outline after
the child backgrounds. Generated resource-group inventories use this wrapper; automation
selection highlights the whole inventory once. Older hand-authored highlight targets still
work through the generic highlight service's outline fallback; mod targets can provide their
own `HighlightPresentation`.

```xml
<coloured-group id="input_group" color="#55AA88">
  <inventory storage="input" columns="2" spacing="2">
    <item-slot index="0"/>
    <item-slot index="1"/>
  </inventory>
</coloured-group>
```

`ProcessorButtonElement` owns processor lookup, host validation, synchronized state, pause
actions, state icons, tooltip arguments and redstone-conflict badges. Ordinary `ButtonElement`
and processor buttons share `ButtonControl` presentation. The menu performs generic packet
validation and dispatch; it has no processor-button branch.

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
`EnergyGaugeElement` owns the energy resource binding, validation, server snapshots and default
tooltip, whether created from XML or directly in Java. The host exposes its `EnergyBuffer` via
`screenEnergy()`; `BaseMachine` provides this automatically. Values preserve the buffer's full
`long` precision. The generic tooltip formatter still abbreviates large values and shows exact
numbers while Shift is held. Presentation remains customizable:

```xml
<energy-gauge id="reserve" length="4s" color="#D23838"
              tooltip="screen.example.reserve" tooltip-fallback="Reserve: {value} / {max} FE"/>
```

`orientation="horizontal"` fills from left to right. `length="fill"`
follows the ordinary gauge rules; explicit lengths must be at least 8 px. A custom
`tooltip` replaces the built-in tooltip; its fallback is optional.
Generic `interaction` and click handlers remain available. For an arbitrary pair of numeric
getters, use a coloured `gauge` or `meter`. The old energy `value`/`max` overrides and
`BaseMachine.getEnergy()`/`getEnergyCapacity()` getters have been replaced by the storage binding.

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

### Custom ingredient drop targets

Element behaviors may implement `gui.client.IngredientDropHandler`, directly or through
`BehaviorCapabilities`, to accept ghost ingredients. Register the behavior through
`ElementBehaviors` as usual. The capability supplies stable per-element target IDs, screen-space
rectangles and acceptance callbacks; it can expose several regions and choose which ingredient
types/values it accepts. Discovery is pure and runs for both hover previews and drag starts.

`IngredientType<T>` declares an ingredient's namespaced identity, Java class and defensive copier.
Reuse the same declaration throughout your integration. `IngredientTypes.ITEM` and `.FLUID` are
provided; mods can declare other types. `IngredientDrops` discovers registered capabilities,
copies viewer-owned data before handing it to providers, clips regions to the viewport and
rechecks visibility, eligibility and target geometry when a drop arrives. A stale hidden/moved
target is rejected. The JEI bridge also rejects callbacks after the screen closes or JEI shuts down.

For example, a mod's fluid-selection element can register this behavior (its common element must
declare the bounded `SELECT_FLUID` action and validate its payload on the server):

```java
class FluidSelectionBehavior extends DefaultElementBehavior implements IngredientDropHandler {
    FluidSelectionBehavior(ElementAccess access) { super(access); }

    public <T> List<Target<T>> targets(IngredientType<T> type, T ingredient) {
        if (!type.equals(IngredientTypes.FLUID) || access.pending(access.element()))
            return List.of();
        return List.of(new Target<>("selection", access.bounds(), value ->
            access.submit(SELECT_FLUID, FluidStack.class.cast(value))));
    }
}
ElementBehaviors.GLOBAL.register(FluidSelectionElement.class,
    (element, access) -> new FluidSelectionBehavior(access));
```

These contracts have no JEI dependency. In a mod's optional JEI plugin, register additional
viewer types once, before interaction, using
`client.jei.JeiIngredientTypes.GLOBAL.register(MY_JEI_TYPE, MY_INGREDIENT_TYPE)`.
The source is the mod's already-registered JEI `IIngredientType<T>`; the destination is its
shared `IngredientType<T>`. Duplicate source mappings/IDs and incompatible Java classes fail
explicitly; unmapped types advertise no targets. Item and NeoForge fluid mappings are registered
by default through the same API. Keep JEI references inside the optional plugin so the rest of
the mod remains loadable without JEI.

`SlotBehavior` delegates drop conversion, eligibility and action choice to the slot element,
sharing the ordinary acknowledged click path. Its default selection action is `SELECT_GHOST`. The JEI handler knows neither slots nor ingredient payload
classes. Ingredient drops represent selections; callbacks use typed actions with normal server
authorization and correction. These hooks introduce no wire-format changes.

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
  Highlight targets are optional: missing elements are skipped and do not prevent the menu opening.
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
  - Minecraft's own `Â§` formatting codes (`Â§c` red, `Â§l` bold, `Â§r` reset, ...) for colour
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
  Pop-out tabs are 22Ã—24 rectangles, coloured by kind: yellow information, amber warning,
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
  The default is Between 1â€“15. Dragging previews locally, release applies both endpoints atomically,
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
to `signal` is Minecraft's query direction, not the outward face. Forward the block's
`canConnectRedstone` hook to `redstone.connects(direction)` with that same query direction.
Dust connects to Read faces and to Emit faces with a selected, available output, including outputs
currently at zero. Ignore and unselected Emit faces do not connect. `updateOutputs()` refreshes
neighboring dust shapes when connections change. Include the controller tag in block-entity
client updates so client-side connection queries agree; `BaseMachine` handles this automatically.
The first server tick repairs adjacent dust geometry only; loading a machine does not issue
a blanket shape update that would pulse neighboring observers.
Stateless hosts can call
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
