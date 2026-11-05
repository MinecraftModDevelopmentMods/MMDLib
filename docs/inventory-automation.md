# Inventory colours, sides and addons

Implement `ScreenHost.screenAutomation()` to opt a machine into the inventory drawer. Existing
screens need no new XML element. The host inserts a colour selector row above the player inventory,
reserves the first right drawer button, and places a second selector vertically inside the drawer.
Each selection configures one resource group. F6 focuses the selector; arrow keys change inventory.

## Define independent resource groups

A `ResourceGroup<T>` declares a stable save ID, translated name, RGB colour, `ResourceType<T>`,
supported `IoMode`, member indices, optional screen element IDs to highlight, and `SideConfig`.
Bind its facing supplier and capability view, then create one persistent `AutomationInventory`
containing the groups. Use the same definitions on client and server.

```java
var input = new ResourceGroup<>("input", Component.translatable("example.input"),
    0x568bd8, ResourceType.ITEMS, IoMode.INPUT, List.of(0, 1), List.of("input_slots"),
    new SideConfig(IoMode.INPUT));
input.bind(this::facing, side -> items.view(slot ->
    input.contains(slot) ? input.mode(side) : IoMode.DISABLED));
automation = new AutomationInventory(this, List.of(input));
```

Register passive item/fluid capabilities using `storage.view(index -> automation.mode(type,index,side))`.
Use `energy.view(() -> automation.mode(ResourceType.ENERGY,0,side))` for energy. Every view retains
all original slot/tank indices; permissions refuse operations instead of renumbering storage.
The group's **active transfer port must restrict access to that group alone**, as above.
Both views preserve storage reservations. Null-side views still honour the declared group role.

Groups are saved by ID, independently of UI ordering. Call `automation.save(registries)` and
`automation.load(tag, registries)` in the block entity's persistence hooks. Missing groups preserve
their raw saved content. Call `automation.dropAll()` from the block's contents-drop hook. Item
transfer remainders are dropped too; fluid and energy buffers follow the usual machine-break
semantics and are discarded. Hosts that retain fluids/energy on break should preserve the saved
automation data alongside their other storage. Do not also drop the same addon slots separately.

## Addon item capabilities

Register `AddonCapabilities.ADDON` for any item through `RegisterCapabilitiesEvent`. Its
`InventoryAddon` returns an `AddonSpec`: stable type ID, resource type, required operations,
batch size, interval, RF cost per unit and filter support. Acceptance requires **both** the
resource type and supported inventory operations. Disabling every face does not invalidate an addon.

Each group has four addon slots. A filter-capable addon exposes a smaller child slot. Register
`AddonCapabilities.FILTER` for filter items; `acceptsFilter` can further restrict compatible filters.
The parent and child are real fixed menu slots. Ghost entries in editors are not real item slots.
All vanilla interaction paths use the same slot validation, including shift-click and hotbar swap.
Removing/replacing a parent returns its configured filter to the acting player's inventory, or
drops it beside the machine when full. Programmatic removals queue recovery; call `recover(player)`
after the operation, or `recover(null)` to drop queued items. Recovery is persisted across saves.

Unknown or incompatible installed items remain removable. An installed filter whose capability is
missing blocks its module; it does not become an unfiltered transfer. Empty optional filter slots
are intentionally unfiltered.

## Item-provided filter panels

`InventoryFilter` supplies a resource type, editor ID, control tree, matching predicate and
configuration validation. The default configuration adapter stores only the `mmdlib_filter`
compound in the stack's custom data and preserves unrelated components.

```java
public FilterControl controls(ItemStack stack) {
  return new FilterControl.Box("panel", false, List.of(
      new FilterControl.Toggle("deny", MY_ICON, Component.translatable("example.deny")),
      new FilterControl.Samples("entries", 18, ResourceType.ITEMS)));
}
```

Use nested `Box` controls for rows/columns, `Toggle` for icon buttons, `Text` for bounded text/tag
input, `Number` for bounded numeric values, and `Samples` for a wrapped ghost grid. Width is supplied
by the drawer; tall content scrolls within the available screen space. Counts, nesting, IDs and
text lengths are bounded. A sample comes from the **server's cursor stack**, without consuming it.
Standard item samples match item IDs; fluid samples use a fluid container's contents. A filter
may override sample extraction and validation for stricter matching.

For specialised controls, register a common `FilterWidgetType<T>` with a bounded action codec and
a handler that updates only a candidate configuration. Include `FilterControl.Custom` in the tree.
On the client, `FilterWidgets.register(type, factory)` supplies a fresh instance per open panel.
Its access object exposes measured bounds, configuration copies, pending state and a typed
submitter. The host clips rendering and routes focus, keyboard, pointer dragging, release and
scroll events. `cancel`/`removed` let controls discard drafts and capture when their attachment,
layout or selection changes. Client registrations must stay in client-only classes.

The server resolves the current installed item and declared control before any edit. Menu session,
attachment generation and configuration revision reject stale/replayed edits and concurrent
overwrites. The candidate is committed only after full filter validation. Authoritative stack sync
updates all viewers. Return `false` from validation to keep the original configuration unchanged.
Custom action JSON is limited to 256 characters; choose codecs with bounded collections and values.

See `CompanionAddons` and `CompanionWidgetsClient`: the basic/expanded item filters use different
grid sizes, the fluid filter accepts fluid samples, and the tag filter supplies a text field plus
a separately registered custom allow/deny button. None requires machine-specific screen XML.

## Active transfers

Call `AutomationTransfers.tick(automation, energy)` once per server tick, after recipe processing.
The scheduler rotates module and face priorities, observes enabled sides and installed predicates,
and charges RF only for actual delivery. It never force-loads a neighbour's chunk. Built-in item,
fluid and energy transports simulate before extracting and retain unexpected insertion remainders
in persistent escrow for later delivery. Escrow retains its original direction, face and cost even
if the module is removed; disabling that face pauses its completion. It cannot be silently reused
as a new transfer with different settings. Capability implementations must obey the normal
simulation/count contracts; no API can recover reliably from an external handler throwing after
an undocumented partial mutation.

The companion uses paid mechanical pullers/pushers and fluid pumps. Its energy relays are lossless
and have no auxiliary RF fee, so importing can bootstrap an empty buffer. Paid transports should
use a power buffer separate from the resource being moved to avoid sharing refund capacity.

Register future content with `ResourceType.register(id, capabilityInterface)` and
`AutomationTransfers.register(type, transport)`. Custom transports own their bounded persistent
escrow schema and must preserve unaccepted resources. Resource types are not a closed enum; gas
support can add a capability and transport without changing the drawer.

The optional companion's Logistics Workbench demonstrates seven groups: two item inventories,
four independent fluid tanks and energy. Its output tanks can be filled by the owning machine/test
setup; passive external insertion correctly refuses them.
