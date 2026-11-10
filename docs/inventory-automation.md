# Inventory colours, sides and addons

Implement `ScreenHost.screenAutomation()` to opt a machine into the inventory drawer. Existing
screens need no new XML element. The host inserts a colour selector row above the player inventory,
reserves the first right drawer button, and places a second selector vertically inside the drawer.
Each selection configures one resource group. Elements provide associations through
`Element.associatedGroups`; `GroupOrder` orders their IDs by document traversal, once per group.
The default matches explicit anchors; `SlotElement` also matches unanchored item groups by named
inventory and local index. Mods can override these associations directly. Groups without a matching
element follow in declaration order. The reusable `ColourButtonBar<K>` receives only ordered IDs
and RGB colours; the drawer maps them to automation selections. The same
order drives the drawer selector and keyboard navigation, without renumbering saved groups or
menu slots. F6 focuses the selector; arrow keys change inventory. Overflow scrolls within the
colour bar instead of covering adjacent controls.

`Element.highlightedGroups` also supplies decoration and emphasis targets, defaulting to the
element's associations. Mods can override highlight participation independently of ordering;
the drawer no longer resolves highlight targets directly from resource-group anchor strings.

Place optional machine buttons in a top-level `<inventory-controls>` after the main screen body.
They align right on the same row as the left-aligned colour bar, against the player inventory's
edges. Layout reserves this row once and computes the selector's remaining width automatically.
Screens without machine controls give the colour bar the full inventory width.
The main inventory panel remains centred when drawers or filters open. Information drawers stay
on the left; inventory side configuration stays on the right. Filter controls occupy a framed,
scrollable region inside the configuration drawer below the addon slots. The arrow bar beneath
the installed filter shows/hides this region; it has no duplicate selected-filter header.
The drawer fits its
visible controls and grows below the main panel as needed to fit an open filter, stopping at
the screen margin or JEI toolbar limit; overflow scrolls within
the filter region. The colour selector keeps its own compact
height. All drawers share the same frame renderer and four-pixel top inset. The configuration
drawer can grow to the available screen height; inventory drawers retain the panel's bottom inset. Info drawers fit their text up to the inset bottom or the screen edge, whichever
comes first.

## Shared machine resource definitions

`machine.neoforge.MachineResources` defines group membership once for automation, recipes and
screen highlights. Construct it after defining the physical `ItemStorage`, `FluidStorage` and
`EnergyBuffer`; resource-group IDs are separate from stable slot/tank save IDs.

```java
var resources = new MachineResources(this, this::facing)
    .items(inputGroup, inventories.inventory("input"))
    .items(outputGroup, inventories.inventory("output"))
    .fluids(coolantGroup, fluids)
    .energy(energyGroup, energy)
    .recipeInputs(MyRecipes.PROCESSING, "input", "coolant")
    .build();
automation = resources.automation();
features.add(new RecipeProcessor("work", MyRecipes.PROCESSING, resources.recipes()));
```

Here the supplied groups have IDs `input`, `output`, `coolant` and `energy`. The resulting recipe
inventory uses those IDs as keys. `recipeGroup("combined", "left", "right")` can define an alternate
recipe mapping over item groups from separate or shared physical storages; it creates no extra
automation group. Each group's member indexes are local to its backing inventory. Independent
inventories can both have member zero. `ItemInventories` supplies an optional combined capability
view without merging their contents or reservation ownership. Addon/filter slots also have an
independent handler per resource group.
Finish all bindings/aliases/input links before the single `build()` call.

Validation rejects duplicate group IDs, empty/out-of-range membership, overlapping physical groups,
and role/IO contradictions. A group's active
transfer view is restricted to its own members. `recipes()` and `automation()` require a completed
build. `ScreenInventoryValidation.validate(this)` can validate after construction; menus repeat
validation on opening, checking real slot bounds, highlight IDs and processor-button targets.

`recipeInputs(type, keys...)` links admission rules only to specified input groups. Items/fluids
are accepted if an input ingredient of a compatible recipe allows them; matching current amounts,
available power and output room is not required just to insert an ingredient. Compatibility means
the machine exposes all referenced input/output groups with the required resource kinds.
Several types or overlapping recipe aliases accept their union; the result is intersected with
the storage's existing rules. Unlinked decorative tanks or general storage keep their own filters.
If there is no level or no compatible recipe, a linked input accepts nothing. Lookups consult the
current recipe manager, so server reloads and client recipe synchronization update admission rules.
Product insertion and save restoration do not pass through these external recipe-input checks.

Use `MachineInventoryLifecycle(owner, features, automation, energy)` to compose persistence and
recovery. Register storage through `.items(saveKey, items)`, `.fluids(saveKey, fluids)` and
`.energy(saveKey, energy)`. Save keys must be unique, cannot be `features` or `automation`, and
each physical storage is registered once. `BasePoweredMachine` supplies this integration and
guarantees its own `energy` binding; see [block entities](block-entities.md#basepoweredmachine).

The lifecycle saves storages, automation and features together. Loading resets old feature plans,
loads contents, then automation and feature state. Its `tick(processingAllowed, paused, policy)`
can pause processing only, transfers only, both or neither; the separate processing gate always
applies. Owed energy refunds settle even during a pause. Destruction hands off displaced storage
and escrow before clearing ownership, releases plans and drops ordinary item contents. Normal
fluid/buffer destruction remains the machine's policy. Explicit `clear()` discards all registered
contents and resets features, including manual pause and resumable progress.

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
their raw saved content. Call `automation.dropAll(recoverResources)` from the block's contents-drop
hook. Addons, filters and pending item transfers drop as items. The required callback must preserve
pending fluid/energy transfers, unpaid energy refunds and custom resource payloads in a dropped
container. It receives a compound with a `resources` list (`type` resource ID plus transport-owned
`data`) and an optional long `refund`. Ownership is handed to the callback before pending data is
cleared. Do not also drop the same addon slots separately. This hook handles transfer escrow;
normal tank/buffer contents remain the host's separate break policy.

The companion drops a **Recovery Capsule**, a companion item carrying this data. Use it on a
companion machine to restore displaced items into accepting slots, fluids into accepting tanks
and energy into available buffer space.
Partial restoration leaves the remainder in the same capsule; a fully restored capsule is consumed,
including in creative mode. Unknown resource payloads remain preserved. No recovery item is
registered by MMDLib itself.

## Addon item capabilities

Register `AddonCapabilities.ADDON` for any item through `RegisterCapabilitiesEvent`. Its
`InventoryAddon` returns an `AddonSpec`: stable type ID, resource type, required operations,
batch size, interval, RF cost per unit and filter support. Acceptance requires **both** the
resource type and supported inventory operations. Disabling every face does not invalidate an addon.

Each group has four addon slots. A filter-capable addon exposes a smaller child slot. Register
`AddonCapabilities.FILTER` for filter items; `acceptsFilter` can further restrict compatible filters.
Each transfer role may be installed only once per group, regardless of item ID or tier: one input
addon and one output addon can coexist when the inventory supports both. A bidirectional addon
occupies both roles. Separate inventories may each have their own addons. Storage insertion,
direct assignment and client menu prediction enforce the same rule. Loading an older save keeps
the first addon for each role and queues duplicates with their attached filters for recovery.
The parent and child are real fixed menu slots. Ghost entries in editors are not real item slots.
All vanilla interaction paths use the same slot validation, including shift-click and hotbar swap.
Removing/replacing a parent returns its configured filter to the acting player's inventory, or
drops it beside the machine when full. Programmatic removals queue recovery; call `recover(player)`
after the operation, or `recover(null)` to drop queued items. Recovery is persisted across saves.

Unknown or incompatible installed items remain removable. An installed filter whose capability is
missing blocks its module; it does not become an unfiltered transfer. Empty optional filter slots
are intentionally unfiltered.
Validation is dirty-driven: normal slot edits and loading invalidate the affected groups. Call
`automation.invalidateAddons()` after an external addon-capability/definition change; this also
invalidates editor generations. Use the slot setters for attachment changes rather than mutating
installed stacks without notification.

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

Use nested `Box` controls for rows/columns, `Toggle` for flat icon buttons, `Text` for bounded text/tag
input, `Number` for bounded numeric values, and `Samples` for a wrapped ghost grid. Width is supplied
by the drawer; tall content scrolls within the available screen space. Counts, nesting, IDs and
text lengths are bounded. A sample comes from the **server's cursor stack**, without consuming it.
Standard item samples match item IDs; fluid samples use a fluid container's contents. A filter
may override sample extraction and validation for stricter matching. Ghost item previews use their slot appearance (42% opacity by default). A toggle may provide separate icons and tooltips for its false/true states; the
three-argument constructor retains one shared icon and tooltip. The companion's basic item filter
provides eight ghost cells and white/black list cards for its mode toggle.

`FilterControl` is an open `Element` interface. Controls own their measurement and server-side
`edit(FilterControl.Edit)` validation. Its `Box` accepts ordinary elements alongside filter controls;
the shared `FlowLayout` handles wrapping, and `ElementFragmentLayout` traverses `childLayout` without
switching on control types. Width-dependent elements can override `measure(text, availableWidth)`.
`FilterPanelLayout` remains a named-bounds view for integrations.

`FilterControl.Samples` is extensible without another registration. Its inherited renderer and
behavior call these common-side hooks:

- `sampleDisplay(configuration, index)` chooses the displayed item stack. Defaults map item IDs to
  items and fluid IDs to buckets; subclasses may interpret other resource types or configuration.
- `sampleTooltip(configuration, index)` supplies any number of rows. The default names the stack
  selected by `sampleDisplay`, or shows the empty-sample hint.
- `sampleSlot(index)` supplies a `SlotElement` for its background, empty icon, item size and ghost
  opacity. This is presentation only; it does not install a native inventory slot.
- `cellSize()` supplies the grid pitch (18 pixels by default). `sampleBounds(bounds, index)` can
  override placement, spacing and cell dimensions. Measurement, rendering, tooltip targeting and
  clicks share those bounds. During measurement the outer height is zero; base placement on width
  and the cell index. `sampleIndex(bounds, x, y)` is also overridable; the default checks cells in
  reverse drawing order, excludes gaps, and clips hits to the control.

For example, a subclass can add a second tooltip row while retaining default sampling and drawing:

```java
public class DescribedSamples extends FilterControl.Samples {
    public DescribedSamples(String id, int count, ResourceType<?> resource) {
        super(id, count, resource);
    }

    @Override
    public List<Component> sampleTooltip(CompoundTag configuration, int index) {
        var rows = new ArrayList<>(super.sampleTooltip(configuration, index));
        rows.add(Component.translatable("example.sample_help"));
        return List.copyOf(rows);
    }
}
```

Treat synchronized configuration as read-only. These presentation hooks do not change server-side
sample extraction or edit validation; inherited clicks still submit the selected index and the
`sample`/`clear` operation through `FilterEdits`.

On the client, filter elements use `ElementRenderers`, `ElementBehaviors` and `ElementInput`, just
like screen elements. `ElementFragment` hosts the tree in the drawer's clipped, scrollable viewport.
The drawer owns attachment changes and transport; individual behaviors own drafts, ghost-grid
interaction and tooltips. Text and custom-widget state survives ordinary configuration updates.
Changing the attachment/tree removes old behaviors; moving or resizing the viewport cancels focus
and capture. Mouse release reaches the capturing control even outside its bounds.

For a new mod-defined control, implement `FilterControl` and register its class with the two client
registries. In its behavior, submit through the host service:

```java
FilterEdits.submit(access, element, entryIndex, proposedValue);
```

`access.value()` is a configuration copy. `FilterEdits` checks pending state, and the host verifies
that the control belongs to the current fragment. The server resolves the installed control and
calls its `edit` method on a candidate configuration, followed by the filter's whole-configuration
validation. The default control implementation refuses edits. No new drawer, layout or network
type switch is required. Edit strings remain limited to 256 characters.

For the existing typed-widget API, register a common `FilterWidgetType<T>` with a bounded action codec and
a handler that updates only a candidate configuration. Include `FilterControl.Custom` in the tree.
On the client, `FilterWidgets.register(type, factory)` supplies a fresh instance per open panel.
Its access object exposes measured bounds, configuration copies, pending state and a typed
submitter. The host clips rendering and routes focus, keyboard, pointer dragging, release and
scroll events through `FilterCustomBehavior`, an adapter to the same element input/render path.
There is no separate custom-widget input dispatcher in the drawer. `cancel`/`removed` let controls discard drafts and capture when their attachment,
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
in persistent escrow for later delivery. Inbound escrow belongs to the machine already and finishes
locally without querying the old source, even if that neighbour is removed or external sides are
disabled. Outbound escrow first tries its previous destination, then other enabled output faces;
with no accepting destination it remains saved. It retains its original direction and cost even
if the module is removed, and cannot be reused as a new transfer with different settings.
Refunds that do not fit the power buffer remain persisted and are retried when room is available.
Capability implementations must obey the normal
simulation/count contracts; no API can recover reliably from an external handler throwing after
an undocumented partial mutation.

The companion uses paid mechanical pullers/pushers and fluid pumps. Its energy relays are lossless
and have no auxiliary RF fee, so importing can bootstrap an empty buffer. Pending refund ownership
is independent of buffer capacity, including when handlers refill the buffer during a transfer.

Register future content with `ResourceType.register(id, capabilityInterface)` and
`AutomationTransfers.register(type, transport)`. Custom transports implement `move` for a fresh
transfer and `deliver` for an already-extracted payload; `deliver` must not consult or extract from
the source. They own their bounded persistent escrow schema and must preserve unaccepted resources.
Override `recovery` to provide a resource-only payload for the host's break container; the default
preserves the whole pending state. Resource types are not a closed enum; gas
support can add a capability and transport without changing the drawer.

The optional companion's Logistics Workbench demonstrates seven groups: two item inventories,
four independent fluid tanks and energy. Its output tanks can be filled by the owning machine/test
setup; passive external insertion correctly refuses them.

Neighbour capability lookups use per-machine NeoForge caches that follow invalidation, replacement
and chunk loading. Resource membership uses indexed lookups, and menus construct sync snapshots
only when their revision or viewer state changes. `AutomationLayout` is the shared source for drawer
frames, real slot coordinates and filter hit bounds. Unsided companion energy queries retain the
buffer's normal insert/extract limits; sided queries additionally obey inventory side settings.
