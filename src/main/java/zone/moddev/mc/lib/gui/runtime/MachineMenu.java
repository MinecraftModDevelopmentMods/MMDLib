package zone.moddev.mc.lib.gui.runtime;

import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.*;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.load.*;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** One menu for all XML screens. Slot numbers retain the machine's storage numbering. */
public class MachineMenu extends AbstractContainerMenu {
  /** Slot plans sync under this key plus the slot, above every element key. */
  public static final int PLAN_KEYS = 1 << 20;

  private static final int TOOLTIP_KEYS = 1 << 22;

  /** Server-authored, viewer-specific lines; getters and fluid filters never run on the client. */
  public List<Component> tooltip(Element element) {
    var value = values.get(TOOLTIP_KEYS + index(element));
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream().filter(Component.class::isInstance).map(Component.class::cast).toList();
  }

  public Map<Integer, Object> tooltipSnapshot() {
    var snapshot = new HashMap<Integer, Object>();
    if (host != null)
      for (int i = 0; i < elements.size(); i++)
        if (elements.get(i) instanceof Element.Gauge)
          snapshot.put(
              TOOLTIP_KEYS + i,
              ScreenValues.snapshot(
                  host.screenTankTooltip(elements.get(i), getCarried().copy()),
                  blockEntity.getLevel().registryAccess()));
    return snapshot;
  }

  public final BlockEntity blockEntity;
  public final ScreenBinder.Bound bound;
  public final List<Element> elements;
  private final Map<String, String> views = new HashMap<>();
  private final Map<Element, Boolean> visibility = new IdentityHashMap<>();
  private final Map<Element, Integer> elementIndexes = new IdentityHashMap<>();
  private final Map<Integer, Object> values = new HashMap<>();
  private final ScreenStats stats;

  /** Server-synchronized stats for tick, second or minute rate display. */
  public List<Component> stats(int unit) {
    Object value = values.get(ScreenStats.KEY + Math.clamp(unit, 0, 2));
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream().filter(Component.class::isInstance).map(Component.class::cast).toList();
  }

  private final ScreenHost host;
  private final int machineSlots;
  private final IItemHandler items;
  @Nullable private final ReservedSlots reserved;
  private final Player owner;
  public final AutomationMenu automation;
  public final RedstoneMenu redstone;
  public final ContainerDrawerMenu containerDrawer;
  public final ContainerDrawerMenu curiosDrawer;
  private final int baseMachineSlots;
  private boolean initial = true;
  private boolean interacting;
  private long widgetSession = java.util.concurrent.ThreadLocalRandom.current().nextLong();
  private int nextWidgetRequest;
  private int lastWidgetRequest;
  private long widgetActionTick = Long.MIN_VALUE;
  private int widgetActionsThisTick;
  private final Map<Integer, Integer> pendingWidgets = new HashMap<>();
  private final Map<Integer, ScreenNetwork.WidgetResult> widgetResults = new HashMap<>();
  private final Set<Integer> widgetRefreshKeys = new HashSet<>();
  private final Map<Integer, Integer> widgetSentAt = new HashMap<>();
  private int widgetClientTicks;

  public MachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
    this(id, inventory, readEntity(inventory, data));
    widgetSession = data.readLong();
  }

  public MachineMenu(int id, Inventory inventory, BlockEntity entity, long session) {
    this(id, inventory, entity);
    widgetSession = session;
  }

  public long widgetSession() {
    return widgetSession;
  }

  /** Client-side: at most one in-flight edit per widget. */
  public <T> Optional<ScreenNetwork.Widget> prepareWidgetAction(
      Element.Custom widget, WidgetAction<T> action, T value) {
    int index = index(widget);
    if (!visible(widget)
        || pendingWidgets.containsKey(index)
        || widgetResults.containsKey(index)
        || nextWidgetRequest == Integer.MAX_VALUE) return Optional.empty();
    if (WidgetTypes.GLOBAL.require(widget.type()).action(action.id()).orElse(null) != action)
      throw new IllegalArgumentException("Action is not registered for " + widget.type());
    byte[] data;
    try {
      data = action.encode(owner.registryAccess(), value);
    } catch (RuntimeException failure) {
      zone.moddev.mc.lib.MMDLib.LOGGER.error(
          "Could not encode widget action: {}", action.id(), failure);
      return Optional.empty();
    }
    int request = ++nextWidgetRequest;
    pendingWidgets.put(index, request);
    widgetSentAt.put(index, widgetClientTicks);
    return Optional.of(
        new ScreenNetwork.Widget(containerId, widgetSession, index, request, action.id(), data));
  }

  public boolean widgetPending(Element.Custom widget) {
    return pendingWidgets.containsKey(index(widget));
  }

  public void receiveWidgetResult(ScreenNetwork.WidgetResult result) {
    if (result.menu() != containerId
        || result.session() != widgetSession
        || !Objects.equals(pendingWidgets.get(result.element()), result.request())) return;
    pendingWidgets.remove(result.element());
    widgetSentAt.remove(result.element());
    values.put(result.element() * 2, result.value());
    values.put(result.element() * 2 + 1, result.maximum());
    widgetResults.put(result.element(), result);
  }

  public ScreenNetwork.WidgetResult takeWidgetResult(Element.Custom widget) {
    return widgetResults.remove(index(widget));
  }

  /** Client-side timeout prevents an unconfirmed edit from permanently disabling a control. */
  public void tickWidgetActions() {
    widgetClientTicks++;
    for (var entry : new HashMap<>(widgetSentAt).entrySet())
      if (widgetClientTicks - entry.getValue() >= 100) {
        int index = entry.getKey();
        receiveWidgetResult(
            new ScreenNetwork.WidgetResult(
                containerId,
                widgetSession,
                index,
                pendingWidgets.get(index),
                false,
                values.get(index * 2),
                values.get(index * 2 + 1)));
      }
  }

  /** Server-side authorization precedes decoding. Excess edits are rejected without dispatch. */
  public ScreenNetwork.WidgetResult widgetAction(
      net.minecraft.server.level.ServerPlayer player, ScreenNetwork.Widget packet) {
    if (player != owner
        || player.containerMenu != this
        || !stillValid(player)
        || packet.menu() != containerId
        || packet.session() != widgetSession
        || packet.request() <= lastWidgetRequest
        || packet.element() >= elements.size()) return null;
    long tick = player.level().getGameTime();
    if (widgetActionTick != tick) {
      widgetActionTick = tick;
      widgetActionsThisTick = 0;
    }
    lastWidgetRequest = packet.request();
    if (!(elements.get(packet.element()) instanceof Element.Custom widget)) return null;
    if (++widgetActionsThisTick > 8)
      // Use the last sent snapshot: rate-limited requests must not invoke codecs or getters.
      return widgetResult(packet, false, values);
    boolean accepted = false;
    var action = WidgetTypes.GLOBAL.require(widget.type()).action(packet.action());
    if (visible(widget) && action.isPresent()) {
      try {
        accepted =
            action.get().dispatch(new WidgetAction.Context(player, this, widget), packet.data());
      } catch (RuntimeException failure) {
        zone.moddev.mc.lib.MMDLib.LOGGER.error(
            "Widget action failed: {}", packet.action(), failure);
      }
    }
    if (accepted) blockEntity.setChanged();
    ScreenSnapshots.invalidate(blockEntity);
    var snapshot = ScreenSnapshots.read(blockEntity, bound);
    return widgetResult(packet, accepted, snapshot);
  }

  private ScreenNetwork.WidgetResult widgetResult(
      ScreenNetwork.Widget packet, boolean accepted, Map<Integer, Object> snapshot) {
    var result =
        new ScreenNetwork.WidgetResult(
            containerId,
            widgetSession,
            packet.element(),
            packet.request(),
            accepted,
            snapshot.get(packet.element() * 2),
            snapshot.get(packet.element() * 2 + 1));
    // The acknowledgment changes the client's values too. Include it in the delta baseline so
    // a later return to the pre-action value is sent even if no regular broadcast saw the edit.
    values.put(packet.element() * 2, result.value());
    values.put(packet.element() * 2 + 1, result.maximum());
    // A client may already have timed out and ignored this response. Also send the next normal
    // snapshot for these keys so its confirmed values converge independently of acknowledgments.
    widgetRefreshKeys.add(packet.element() * 2);
    widgetRefreshKeys.add(packet.element() * 2 + 1);
    return result;
  }

  private static BlockEntity readEntity(Inventory inventory, RegistryFriendlyByteBuf data) {
    var entity =
        Objects.requireNonNull(
            inventory.player.level().getBlockEntity(data.readBlockPos()),
            "Screen block entity missing");
    String expected = data.readUtf(64);
    var screen =
        Screens.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType())).orElseThrow();
    if (!ScreenRuntime.fingerprint(screen.screen(), entity).equals(expected))
      throw new IllegalStateException(
          "Client/server screen or machine definitions differ: " + screen.screen().id());
    return entity;
  }

  public MachineMenu(int id, Inventory inventory, BlockEntity entity) {
    this(id, inventory, entity, inventory.player.level().isClientSide);
  }

  /**
   * For tests only.
   *
   * @param client whether the slots are the client's copy, as on a client; the menu still reads the
   *     level's side for layout, so this is only meaningful for running both against one server
   */
  protected MachineMenu(int id, Inventory inventory, BlockEntity entity, boolean client) {
    super(ScreenRuntime.MENUS.get(), id);
    owner = inventory.player;
    blockEntity = entity;
    bound = Screens.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType())).orElseThrow();
    if (!bound.blockEntityClass().isInstance(entity))
      throw new IllegalArgumentException("Wrong screen block entity");
    elements = bound.screen().elements();
    var equipmentDiscovery = new EquippedContainers.Discovery(owner);
    containerDrawer =
        new ContainerDrawerMenu(
            this,
            owner,
            () ->
                bound.screen().playerInventory()
                    ? equipmentDiscovery.get().containers()
                    : List.of(),
            false,
            equipmentDiscovery::invalidate);
    curiosDrawer =
        new ContainerDrawerMenu(
            this,
            owner,
            () ->
                bound.screen().playerInventory()
                    ? equipmentDiscovery.get().equipment()
                    : java.util.List.of(),
            true,
            equipmentDiscovery::invalidate);
    for (int i = 0; i < elements.size(); i++) elementIndexes.put(elements.get(i), i);
    if (elements.size() > 4096)
      throw new IllegalArgumentException("A screen may have at most 4096 elements");
    for (var element : elements)
      if (element instanceof Element.SideConfig config && config.groups().size() > 682)
        throw new IllegalArgumentException("Too many side configuration groups");
    host = entity instanceof ScreenHost h ? h : null;
    if (host != null) ScreenInventoryValidation.validate(bound.screen(), host);
    stats = client || host == null ? new ScreenStats() : ScreenStats.forHost(host);
    automation =
        host != null && host.screenAutomation() != null
            ? new AutomationMenu(this, host.screenAutomation(), owner, client)
            : null;
    redstone =
        host != null && host.screenRedstone() != null
            ? new RedstoneMenu(this, owner, host.screenRedstone(), client)
            : null;
    elements.forEach(
        e -> {
          if (e instanceof Element.Context c)
            views.put(c.common().id().orElseThrow(), c.defaultView().common().id().orElseThrow());
        });
    int count =
        elements.stream()
            .filter(e -> e instanceof Element.Slot)
            .mapToInt(e -> ((Element.Slot) e).index() + 1)
            .max()
            .orElse(0);
    if (client)
      items =
          host == null
              ? new ItemStackHandler(count)
              : new ClientMenuItems(count, host.screenItems());
    else items = host == null ? new ItemStackHandler(0) : host.screenItems();
    baseMachineSlots = count;
    machineSlots = count + (automation == null ? 0 : automation.slotCount());
    reserved = items instanceof ReservedSlots slots ? slots : null;
    layout =
        ScreenLayout.of(
            bound.screen(),
            owner.level().isClientSide ? ScreenRuntime.textMeasure : TextMeasure.NONE,
            automation != null,
            redstone != null,
            views);
    for (int n = 0; n < count; n++) {
      final int index = n;
      var element =
          elements.stream()
              .filter(e -> e instanceof Element.Slot s && s.index() == index)
              .map(e -> (Element.Slot) e)
              .findFirst()
              .orElse(null);
      if (host == null
          && element != null
          && element.kind() != Element.SlotKind.GHOST
          && index >= items.getSlots())
        throw new IllegalArgumentException("Screen slot outside storage: " + index);
      int[] xy =
          element == null
              ? new int[] {-10000, -10000}
              : ScreenLayout.itemPosition(layout.bounds(element));
      if (element == null || element.kind() == Element.SlotKind.GHOST) {
        var dummy = new net.minecraft.world.SimpleContainer(1);
        addSlot(
            new Slot(dummy, 0, xy[0], xy[1]) {
              public boolean mayPlace(ItemStack stack) {
                return false;
              }

              public boolean mayPickup(Player player) {
                return false;
              }

              public boolean isActive() {
                return element != null && visible(element);
              }
            });
      } else
        addSlot(
            new ReservationSlot(items, index, xy[0], xy[1], () -> interacting) {
              @Override
              public void setChanged() {
                super.setChanged();
                // Vanilla shift-click merges mutate the stack directly, bypassing handler
                // callbacks.
                if (!client) {
                  if (items instanceof ItemStorage storage) storage.markChanged();
                  blockEntity.setChanged();
                }
              }

              public boolean isActive() {
                return visible(element);
              }

              public boolean mayPlace(ItemStack stack) {
                return isActive() && super.mayPlace(stack);
              }

              public boolean mayPickup(Player player) {
                // A planned slot lets a player merge into it even when nothing is free to take.
                return isActive() && (planned(index) || super.mayPickup(player));
              }

              public int getMaxStackSize() {
                return element.kind() == Element.SlotKind.UPGRADE ? 1 : super.getMaxStackSize();
              }

              public int getMaxStackSize(ItemStack stack) {
                return Math.min(getMaxStackSize(), super.getMaxStackSize(stack));
              }
            });
    }
    if (automation != null)
      automation.addSlots(this::addSlot, count, layout.panel().width() + AutomationLayout.RAIL);
    layout
        .inventory()
        .ifPresent(
            r -> {
              // Keep the ordinary 36 slots contiguous and in their existing menu order.
              for (int n = 0; n < 36; n++) {
                int index = (n + 9) % 36;
                int[] xy = ScreenLayout.itemPosition(ScreenLayout.playerSlot(r, index));
                addSlot(new Slot(inventory, index, xy[0], xy[1]));
              }
              for (int index : new int[] {39, 38, 37, 36, 40}) {
                int[] xy = ScreenLayout.itemPosition(ScreenLayout.playerSlot(r, index));
                addSlot(new PlayerEquipmentSlot(inventory, index, xy[0], xy[1]));
              }
            });
  }

  public Map<String, String> views() {
    return Map.copyOf(views);
  }

  private ScreenLayout layout;

  /** Current geometry, shared by the screen and its persistent menu slots. */
  public ScreenLayout layout() {
    return layout;
  }

  private void relayout() {
    layout =
        ScreenLayout.of(
            bound.screen(),
            owner.level().isClientSide ? ScreenRuntime.textMeasure : TextMeasure.NONE,
            automation != null,
            redstone != null,
            views);
    for (var element : elements) {
      if (element instanceof Element.Slot slot) {
        int[] xy = ScreenLayout.itemPosition(layout.bounds(element));
        slots.get(slot.index()).x = xy[0];
        slots.get(slot.index()).y = xy[1];
      }
    }
    if (automation != null) {
      for (int i = 0; i < automation.slotCount(); i++) {
        var r =
            AutomationLayout.item(i % AutomationInventory.SLOTS_PER_GROUP)
                .offset(layout.panel().width() + AutomationLayout.RAIL, AutomationLayout.TOP);
        var slot = slots.get(baseMachineSlots + i);
        slot.x = r.x();
        slot.y = r.y();
      }
    }
    layout
        .inventory()
        .ifPresent(
            r -> {
              for (int i = machineSlots; i < slots.size(); i++) {
                var slot = slots.get(i);
                int[] xy =
                    ScreenLayout.itemPosition(ScreenLayout.playerSlot(r, slot.getSlotIndex()));
                slot.x = xy[0];
                slot.y = xy[1];
              }
            });
  }

  public int index(Element element) {
    var index = elementIndexes.get(element);
    if (index == null) throw new IllegalArgumentException("Element is not on this screen");
    return index;
  }

  public Object value(Element element, boolean maximum) {
    return values.get(index(element) * 2 + (maximum ? 1 : 0));
  }

  public Object wireValue(int key) {
    return values.get(key);
  }

  public boolean processorConflict(Element.Button button) {
    return button.processorControl()
        && redstone != null
        && zone.moddev.mc.lib.gui.model.ProcessorButtonState.fromWire(value(button, false))
            .map(
                state ->
                    redstone.conflicts(
                        state == zone.moddev.mc.lib.gui.model.ProcessorButtonState.PAUSED))
            .orElse(false);
  }

  public void apply(Map<Integer, Object> delta) {
    values.putAll(delta);
    if (items instanceof ClientMenuItems client)
      delta.forEach(
          (key, value) -> {
            if (key >= PLAN_KEYS && value instanceof SlotPlan plan)
              client.setPlan(key - PLAN_KEYS, plan);
          });
    for (int i = 0; i < elements.size(); i++)
      if (elements.get(i) instanceof Element.Context context
          && delta.get(Integer.MIN_VALUE + i) instanceof java.math.BigInteger selection) {
        int view = selection.intValue();
        if (view >= 0 && view < context.views().size())
          navigate(
              context.common().id().orElseThrow(),
              context.views().get(view).common().id().orElseThrow());
      }
  }

  public boolean visible(Element element) {
    if (visibility.isEmpty()) {
      for (var root : bound.screen().body()) cacheVisibility(root, true);
      for (var root : bound.screen().tabs()) cacheVisibility(root, true);
    }
    return Boolean.TRUE.equals(visibility.get(element));
  }

  private void cacheVisibility(Element root, boolean shown) {
    visibility.put(root, shown);
    for (var child : root.children()) {
      boolean next = shown;
      if (root instanceof Element.Context c)
        next &= child.common().id().orElse("").equals(views.get(c.common().id().orElse("")));
      cacheVisibility(child, next);
    }
  }

  public void navigate(String context, String view) {
    var e = bound.screen().find(context).orElse(null);
    if (e instanceof Element.Context c
        && c.views().stream().anyMatch(v -> v.common().id().orElse("").equals(view))
        && !Objects.equals(views.get(context), view)) {
      views.put(context, view);
      visibility.clear();
      relayout();
    }
  }

  @Override
  public boolean stillValid(Player player) {
    return !blockEntity.isRemoved()
        && player.level() == blockEntity.getLevel()
        && player.level().getBlockEntity(blockEntity.getBlockPos()) == blockEntity
        && player.canInteractWithBlock(blockEntity.getBlockPos(), 4.0);
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (slot >= 0 && slot < slots.size() && !slots.get(slot).isActive()) return;
    boolean previous = interacting;
    if (!previous && automation != null) automation.beforeInteraction();
    interacting = true;
    try {
      super.clicked(slot, button, type, player);
    } finally {
      endInteraction(previous);
    }
  }

  private void endInteraction(boolean previous) {
    interacting = previous;
    if (!previous)
      for (var slot : slots)
        if (slot instanceof ReservationSlot reservedSlot) reservedSlot.endInteraction();
    if (!previous && automation != null) automation.afterInteraction();
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    boolean previous = interacting;
    if (!previous && automation != null) automation.beforeInteraction();
    interacting = true;
    try {
      return moveStack(player, index);
    } finally {
      endInteraction(previous);
    }
  }

  private ItemStack moveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    var source = slots.get(index);
    if (!source.isActive() || !source.mayPickup(player) || !source.hasItem())
      return ItemStack.EMPTY;
    var stack = source.getItem();
    var original = stack.copy();
    if (index < machineSlots && planned(index)) return quickMovePlanned(player, source);
    if (index < machineSlots || index >= machineSlots + 36) {
      // Outputs go into storage/hotbar; shift-clicking equipment unequips it into that range.
      if (!moveItemStackTo(stack, machineSlots, playerStorageEnd(), true)) return ItemStack.EMPTY;
    } else {
      if (host == null) return ItemStack.EMPTY;
      var destinations = new ArrayList<Slot>();
      if (automation != null)
        for (int destination = baseMachineSlots; destination < machineSlots; destination++)
          if (slots.get(destination).isActive() && slots.get(destination).mayPlace(stack))
            destinations.add(slots.get(destination));
      for (int destination : host.screenDestinations(stack))
        if (destination >= 0
            && destination < baseMachineSlots
            && slots.get(destination).isActive()
            && slots.get(destination).mayPlace(stack)) destinations.add(slots.get(destination));
      // Like vanilla's moveItemStackTo: top up matching stacks first, then fill empty slots.
      for (var slot : destinations)
        if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(slot.getItem(), stack))
          moveItemStackTo(stack, slot.index, slot.index + 1, false);
      for (var slot : destinations)
        if (!stack.isEmpty() && !slot.hasItem())
          moveItemStackTo(stack, slot.index, slot.index + 1, false);
      if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY, original);
    else source.setChanged();
    source.onTake(player, stack);
    return original;
  }

  /** Moves only what the storage says is free, then takes exactly that much out of the slot. */
  private ItemStack quickMovePlanned(Player player, Slot source) {
    // Ask the storage what it would give: a subclass may give less than the plan frees.
    int free = items.extractItem(source.index, reserved.freeToTake(source.index), true).getCount();
    if (free <= 0) return ItemStack.EMPTY;
    var moving = source.getItem().copyWithCount(free);
    var original = moving.copy();
    if (!moveItemStackTo(moving, machineSlots, playerStorageEnd(), true)) return ItemStack.EMPTY;
    int moved = free - moving.getCount();
    var taken = source.remove(moved);
    source.onTake(player, taken);
    return original.copyWithCount(moved);
  }

  /** Whether a menu index represents a real machine item slot, excluding filters and drawers. */
  public boolean isMachineItemSlot(int index) {
    return index >= 0 && index < baseMachineSlots && slots.get(index) instanceof SlotItemHandler;
  }

  private boolean planned(int index) {
    return reserved != null && isMachineItemSlot(index) && reserved.hasPlan(index);
  }

  /** The synchronized plan for a real machine slot; other slots have no reservation overlay. */
  public SlotPlan reservationPlan(int index) {
    return planned(index) ? reserved.plan(index) : SlotPlan.NONE;
  }

  private int playerStorageEnd() {
    return Math.min(machineSlots + 36, slots.size());
  }

  @Override
  public boolean canDragTo(Slot slot) {
    return slot.isActive() && super.canDragTo(slot);
  }

  @Override
  public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
    return slot.isActive() && slot.mayPickup(owner) && super.canTakeItemForPickAll(stack, slot);
  }

  @Override
  public void broadcastChanges() {
    if (automation != null) automation.beforeBroadcast();
    super.broadcastChanges();
    containerDrawer.broadcast();
    curiosDrawer.broadcast();
    if (automation != null) automation.broadcast();
    if (redstone != null) redstone.broadcast();
    if (!(owner instanceof net.minecraft.server.level.ServerPlayer player)
        || !stillValid(owner)
        || !player.connection.hasChannel(ScreenNetwork.Values.TYPE)) return;
    var snapshot = new HashMap<>(ScreenSnapshots.read(blockEntity, bound));
    for (int i = 0; i < elements.size(); i++)
      if (elements.get(i) instanceof Element.Context context) {
        int selected = 0;
        for (int view = 0; view < context.views().size(); view++)
          if (context
              .views()
              .get(view)
              .common()
              .id()
              .orElseThrow()
              .equals(views.get(context.common().id().orElseThrow()))) selected = view;
        snapshot.put(Integer.MIN_VALUE + i, java.math.BigInteger.valueOf(selected));
      }
    snapshot.putAll(reservationSnapshot());
    snapshot.putAll(tooltipSnapshot());
    snapshot.putAll(stats.read(host, blockEntity.getLevel().getGameTime()));
    var delta = ScreenValues.changed(values, snapshot, initial);
    for (int key : widgetRefreshKeys) delta.put(key, snapshot.get(key));
    widgetRefreshKeys.clear();
    if (!delta.isEmpty()) {
      values.putAll(delta);
      PacketDistributor.sendToPlayer(player, new ScreenNetwork.Values(containerId, delta));
    }
    if (host != null)
      for (var e : elements)
        if (e instanceof Element.Slot s && s.kind() == Element.SlotKind.GHOST)
          slots.get(s.index()).set(host.screenGhost(s.index()).copy());
    initial = false;
  }

  /** Reservation snapshots from any handler implementing the menu's reservation contract. */
  public Map<Integer, Object> reservationSnapshot() {
    var snapshot = new HashMap<Integer, Object>();
    if (reserved != null)
      for (int slot = 0; slot < Math.min(baseMachineSlots, items.getSlots()); slot++)
        snapshot.put(PLAN_KEYS + slot, reserved.plan(slot));
    return snapshot;
  }

  @Override
  public boolean clickMenuButton(Player player, int packed) {
    if ((packed & 0xc0000000) != 0) return false;
    action(player, ScreenNetwork.Action.unpack(containerId, packed));
    return true;
  }

  public void action(Player player, ScreenNetwork.Action action) {
    if (action.menu() != containerId
        || player != owner
        || player.containerMenu != this
        || !stillValid(player)
        || action.element() < 0
        || action.element() >= elements.size()) return;
    var element = elements.get(action.element());
    if (!visible(element)
        || action.button() < 0
        || action.button() > 1
        || (action.modifiers() & ~7) != 0) return;
    if (action.kind() == 1 && element instanceof Element.Button b && b.shows().isPresent()) {
      var ref = b.shows().get();
      var context = (Element.Context) bound.screen().find(ref.context()).orElseThrow();
      navigate(
          ref.context(),
          views.get(ref.context()).equals(ref.view())
              ? context.defaultView().common().id().orElseThrow()
              : ref.view());
      return;
    }
    if (action.kind() == 2 && element instanceof Element.Context c) {
      navigate(c.common().id().orElseThrow(), c.defaultView().common().id().orElseThrow());
      return;
    }
    if (action.kind() == 3 && element instanceof Element.SideConfig config && host != null) {
      int group = action.option() / 6, face = action.option() % 6;
      if (action.option() < 0 || group >= config.groups().size()) return;
      var sides = host.screenSides().get(config.groups().get(group));
      if (sides != null) {
        sides.cycle(RelativeFace.values()[face]);
        blockEntity.setChanged();
      }
      return;
    }
    if (action.kind() != 0) return;
    if (element instanceof Element.Button button && button.processorControl()) {
      if (action.button() != 0
          || action.option() != 0
          || host == null
          || host.screenFeatures() == null) return;
      var features = host.screenFeatures();
      var key = button.processorKey();
      features
          .processor(key)
          .ifPresent(
              processor -> {
                if (features.setProcessorPaused(processor.key(), !processor.paused())) {
                  ScreenSnapshots.invalidate(blockEntity);
                  broadcastChanges();
                }
              });
      return;
    }
    var clicks = element.common().clicks();
    boolean shift = (action.modifiers() & 1) != 0;
    var normal = action.button() == 0 ? clicks.click() : clicks.rightClick();
    var shifted = action.button() == 0 ? clicks.shiftClick() : clicks.shiftRightClick();
    int option = action.option();
    if (element instanceof Element.RadioGroup r) {
      if (option < 0 || option >= r.options().size()) return;
    } else if (option != 0) return;
    (shift ? shifted.or(() -> normal) : normal)
        .ifPresent(
            handler ->
                MaterialExtensions.GLOBAL
                    .events()
                    .dispatch(
                        handler,
                        MaterialEventTypes.GUI_CLICK,
                        new ScreenClick(
                            player,
                            this,
                            element,
                            action.button(),
                            shift,
                            (action.modifiers() & 2) != 0,
                            (action.modifiers() & 4) != 0,
                            option)));
  }

  public boolean ghost(Player player, int elementIndex, ItemStack stack) {
    if (player != owner
        || player.containerMenu != this
        || !stillValid(player)
        || host == null
        || elementIndex < 0
        || elementIndex >= elements.size()) return false;
    var e = elements.get(elementIndex);
    if (!(e instanceof Element.Slot s) || s.kind() != Element.SlotKind.GHOST || !visible(e))
      return false;
    boolean accepted =
        host.setScreenGhost(player, s.index(), stack.copyWithCount(stack.isEmpty() ? 0 : 1));
    if (accepted) blockEntity.setChanged();
    return accepted;
  }
}
