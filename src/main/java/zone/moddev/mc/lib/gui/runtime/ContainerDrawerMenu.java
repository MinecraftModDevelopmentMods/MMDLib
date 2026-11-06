package zone.moddev.mc.lib.gui.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-viewer, server-authoritative access to an equipped container's generic I/O capability. */
public final class ContainerDrawerMenu {
  public static final int MAX_CONTAINERS = 16, MAX_SLOTS = 4096, MAX_PAGE = 54;
  public static final int TOGGLE = 0,
      SELECT = 1,
      PAGE = 2,
      PICKUP = 3,
      QUICK_TAKE = 4,
      QUICK_STORE = 5,
      SYNC = 6,
      DISTRIBUTE = 7,
      COLLECT = 8,
      SWAP = 9;

  private final MachineMenu menu;
  private final boolean equipment;
  private final Player owner;
  private final Supplier<List<EquippedContainers.Source>> discover;
  private final Runnable invalidateDiscovery;
  private List<EquippedContainers.Source> sources = List.of();
  private List<Integer> sourceSizes = List.of();
  private int selected,
      offset,
      pageSize = 27,
      lastRequest,
      nextRequest,
      pendingRequest,
      pendingTicks;
  private boolean open, pendingInventory;
  private long revision, actionTick = Long.MIN_VALUE;
  private int actions;
  private ContainerDrawerNetwork.State state;
  private boolean dirty;

  public ContainerDrawerMenu(MachineMenu menu, Player owner) {
    this(
        menu,
        owner,
        () -> menu.bound.screen().playerInventory() ? EquippedContainers.find(owner) : List.of());
  }

  /** A discovery seam for runtime tests and alternative equipment providers. */
  public ContainerDrawerMenu(
      MachineMenu menu, Player owner, Supplier<List<EquippedContainers.Source>> discover) {
    this(menu, owner, discover, false);
  }

  public ContainerDrawerMenu(
      MachineMenu menu,
      Player owner,
      Supplier<List<EquippedContainers.Source>> discover,
      boolean equipment) {
    this(menu, owner, discover, equipment, () -> {});
  }

  public ContainerDrawerMenu(
      MachineMenu menu,
      Player owner,
      Supplier<List<EquippedContainers.Source>> discover,
      boolean equipment,
      Runnable invalidateDiscovery) {
    this.invalidateDiscovery = invalidateDiscovery;
    this.equipment = equipment;
    if (equipment) pageSize = 20;
    this.menu = menu;
    this.owner = owner;
    this.discover = discover;
    state =
        new ContainerDrawerNetwork.State(
            menu.containerId,
            equipment,
            menu.widgetSession(),
            0,
            0,
            false,
            0,
            0,
            pageSize,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
  }

  public ContainerDrawerNetwork.State state() {
    return state;
  }

  public boolean pending() {
    return pendingRequest != 0;
  }

  /** Only item-changing requests need to serialize vanilla inventory gestures. */
  public boolean pendingInventory() {
    return pending() && pendingInventory;
  }

  public Optional<ContainerDrawerNetwork.Request> request(int kind, int argument, int size) {
    return request(kind, argument, size, List.of());
  }

  public Optional<ContainerDrawerNetwork.Request> request(
      int kind, int argument, int size, List<Integer> slots) {
    if (pending() || nextRequest == Integer.MAX_VALUE) return Optional.empty();
    pendingInventory |=
        kind == PICKUP
            || kind == QUICK_TAKE
            || kind == QUICK_STORE
            || kind == DISTRIBUTE
            || kind == COLLECT
            || kind == SWAP;
    pendingRequest = ++nextRequest;
    pendingTicks = 0;
    return Optional.of(
        new ContainerDrawerNetwork.Request(
            menu.containerId,
            equipment,
            menu.widgetSession(),
            state.revision(),
            pendingRequest,
            kind,
            argument,
            size,
            menu.getCarried().copy(),
            slots));
  }

  public void receive(ContainerDrawerNetwork.State update) {
    if (update.equipment() != equipment
        || update.menu() != menu.containerId
        || update.session() != menu.widgetSession()
        || update.revision() < state.revision()) return;
    state = update;
    nextRequest = Math.max(nextRequest, update.acknowledged());
    if (update.acknowledged() >= pendingRequest) {
      pendingRequest = 0;
      pendingInventory = false;
    }
  }

  public void tickClient() {
    if (pending() && ++pendingTicks >= 20) {
      pendingRequest = 0;
      request(SYNC, 0, state.pageSize()).ifPresent(PacketDistributor::sendToServer);
    }
  }

  /** Re-resolves equipment and capabilities; replacing a stack invalidates the open session. */
  public void refresh() {
    var found =
        discover.get().stream()
            .limit(MAX_CONTAINERS)
            .filter(
                s ->
                    !s.stack().isEmpty()
                        && s.items().getSlots() > 0
                        && s.items().getSlots() <= MAX_SLOTS)
            .toList();
    boolean replaced = found.size() != sources.size();
    if (!replaced)
      for (int i = 0; i < found.size(); i++) {
        if (!found.get(i).key().equals(sources.get(i).key())
            || found.get(i).stack() != sources.get(i).stack()
            || found.get(i).items().getSlots() != sourceSizes.get(i)) {
          replaced = true;
          break;
        }
      }
    // Capabilities may be replaced without replacing the equipped stack. Always use the live one.
    sources = found;
    if (replaced) {
      sourceSizes = found.stream().map(s -> s.items().getSlots()).toList();
      open = false;
      offset = 0;
      selected = 0;
      revision++;
      dirty = true;
    }
    boolean iconsChanged = found.size() != state.icons().size();
    if (!iconsChanged)
      for (int i = 0; i < found.size(); i++)
        if (!ItemStack.isSameItemSameComponents(found.get(i).stack(), state.icons().get(i))) {
          iconsChanged = true;
          break;
        }
    IItemHandler items = open && !found.isEmpty() ? found.get(selected).items() : null;
    if (items != null) offset = Math.clamp(offset, 0, Math.max(0, items.getSlots() - 1));
    int count = items == null ? 0 : Math.min(pageSize, items.getSlots() - offset);
    var described = items instanceof EquippedContainers.DescribedHandler d ? d : null;
    boolean contentsChanged = count != state.contents().size();
    boolean descriptionsChanged = (described == null ? 0 : count) != state.descriptions().size();
    for (int i = 0; i < count; i++) {
      if (!contentsChanged
          && !ItemStack.matches(items.getStackInSlot(offset + i), state.contents().get(i)))
        contentsChanged = true;
      if (!descriptionsChanged
          && described != null
          && !described.describe(offset + i).equals(state.descriptions().get(i)))
        descriptionsChanged = true;
    }
    // Only changes to interaction identity/layout invalidate a gesture. Live contents and
    // icon components (which may include the backpack's contents) still sync every tick.
    if (descriptionsChanged
        || open != state.open()
        || selected != state.selected()
        || offset != state.offset()
        || pageSize != state.pageSize()) {
      revision++;
      dirty = true;
    }
    // Scan live handlers, but copy immutable packet data only when something actually changed.
    if (iconsChanged || contentsChanged) dirty = true;
    if (revision == state.revision()
        && lastRequest == state.acknowledged()
        && !iconsChanged
        && !contentsChanged) return;
    var icons =
        iconsChanged ? found.stream().map(s -> s.stack().copyWithCount(1)).toList() : state.icons();
    List<ItemStack> contents = state.contents();
    if (contentsChanged) {
      contents = new ArrayList<>(count);
      for (int i = 0; i < count; i++) contents.add(items.getStackInSlot(offset + i).copy());
    }
    List<EquippedContainers.SlotInfo> descriptions = state.descriptions();
    if (descriptionsChanged) {
      descriptions = new ArrayList<>();
      if (described != null)
        for (int i = 0; i < count; i++) descriptions.add(described.describe(offset + i));
    }
    state =
        new ContainerDrawerNetwork.State(
            menu.containerId,
            equipment,
            menu.widgetSession(),
            revision,
            lastRequest,
            open,
            selected,
            offset,
            pageSize,
            icons,
            replaced
                ? found.stream().map(s -> equipment ? "curios" : s.key()).toList()
                : state.keys(),
            sourceSizes,
            contents,
            descriptions);
  }

  public void broadcast() {
    if (owner.level().isClientSide) return;
    refresh();
    if (dirty
        && owner instanceof ServerPlayer player
        && player.connection.hasChannel(ContainerDrawerNetwork.State.TYPE)) {
      PacketDistributor.sendToPlayer(player, state);
      dirty = false;
    }
  }

  public void action(Player player, ContainerDrawerNetwork.Request request) {
    if (request.equipment() != equipment
        || player != owner
        || player.containerMenu != menu
        || !menu.stillValid(player)
        || request.menu() != menu.containerId
        || request.session() != menu.widgetSession()
        || request.request() <= lastRequest) return;
    lastRequest = request.request();
    invalidateDiscovery.run();
    refresh();
    long tick = player.level().getGameTime();
    if (tick != actionTick) {
      actionTick = tick;
      actions = 0;
    }
    if (++actions <= 32 && request.revision() == revision) {
      if (request.kind() == TOGGLE && !sources.isEmpty()) {
        open = !open;
        if (open) (equipment ? menu.containerDrawer : menu.curiosDrawer).closeServer();
      } else if (open && !sources.isEmpty()) {
        var source = sources.get(selected);
        int argument = request.argument();
        switch (request.kind()) {
          case SELECT -> {
            if (argument >= 0 && argument < sources.size()) {
              selected = argument;
              offset = 0;
            }
          }
          case PAGE -> {
            if (request.pageSize() >= 1
                && request.pageSize() <= MAX_PAGE
                && (!equipment || request.pageSize() % 2 == 0 && argument % 2 == 0)
                && argument >= 0
                && argument < source.items().getSlots()) {
              pageSize = request.pageSize();
              offset = argument;
            }
          }
          case PICKUP, QUICK_TAKE -> {
            int slot = argument / 2;
            if (argument >= 0
                && slot >= offset
                && slot < Math.min(offset + pageSize, source.items().getSlots())
                && ItemStack.matches(request.carried(), menu.getCarried())) {
              if (request.kind() == QUICK_TAKE) takeToPlayer(source.items(), slot);
              else click(source, slot, argument % 2 == 1);
            }
          }
          case QUICK_STORE -> {
            if (argument >= 0
                && argument < 36
                && ItemStack.matches(request.carried(), menu.getCarried()))
              storeFromPlayer(source, argument);
          }
          case DISTRIBUTE -> {
            if (!equipment
                && argument <= 1
                && validTargets(request.slots(), source)
                && ItemStack.matches(request.carried(), menu.getCarried()))
              distribute(source, request.slots(), argument == 1);
          }
          case COLLECT -> {
            if (!equipment
                && visibleSlot(argument, source)
                && ItemStack.matches(request.carried(), menu.getCarried())) collect(source);
          }
          case SWAP -> {
            if (!equipment
                && argument < 9
                && request.slots().size() == 1
                && visibleSlot(request.slots().getFirst(), source)
                && ItemStack.matches(request.carried(), menu.getCarried()))
              swap(source, request.slots().getFirst(), argument);
          }
          default -> {}
        }
      }
    }
    revision++;
    dirty = true;
    // Vanilla slot/cursor changes precede the acknowledgment, so pending input can resume safely.
    if (request.kind() == SYNC) menu.sendAllDataToRemote();
    invalidateDiscovery.run();
    menu.broadcastChanges();
    broadcast();
  }

  public void closeServer() {
    if (open) {
      open = false;
      revision++;
      dirty = true;
    }
  }

  private boolean visibleSlot(int slot, EquippedContainers.Source source) {
    return slot >= offset && slot < Math.min(offset + pageSize, source.items().getSlots());
  }

  private boolean validTargets(List<Integer> slots, EquippedContainers.Source source) {
    return !slots.isEmpty()
        && slots.stream().distinct().count() == slots.size()
        && slots.stream()
            .allMatch(i -> visibleSlot(i, source) || i >= MAX_SLOTS && i < MAX_SLOTS + 36);
  }

  private void distribute(EquippedContainers.Source source, List<Integer> slots, boolean right) {
    var carried = menu.getCarried();
    if (carried.isEmpty() || ItemStack.isSameItemSameComponents(carried, source.stack())) return;
    var inventory = new net.neoforged.neoforge.items.wrapper.InvWrapper(owner.getInventory());
    var eligible =
        slots.stream()
            .filter(
                i -> {
                  var handler = i >= MAX_SLOTS ? inventory : source.items();
                  int slot = i >= MAX_SLOTS ? i - MAX_SLOTS : i;
                  return handler.insertItem(slot, carried.copyWithCount(1), true).isEmpty();
                })
            .toList();
    if (eligible.isEmpty()) return;
    int amount = right ? 1 : carried.getCount() / eligible.size();
    if (amount == 0) return;
    var remaining = carried.copy();
    for (int i : eligible) {
      var handler = i >= MAX_SLOTS ? inventory : source.items();
      int slot = i >= MAX_SLOTS ? i - MAX_SLOTS : i;
      int count = Math.min(amount, remaining.getCount());
      var rest = handler.insertItem(slot, remaining.copyWithCount(count), false);
      remaining.shrink(count - rest.getCount());
      if (remaining.isEmpty()) break;
    }
    menu.setCarried(remaining);
  }

  private void collect(EquippedContainers.Source source) {
    var carried = menu.getCarried().copy();
    if (carried.isEmpty()) return;
    var inventory = new net.neoforged.neoforge.items.wrapper.InvWrapper(owner.getInventory());
    // Vanilla collects partial stacks first. Include all storage pages and ordinary player slots.
    for (int pass = 0; pass < 2; pass++) {
      for (var handler : List.of(source.items(), inventory)) {
        int count = handler == inventory ? 36 : handler.getSlots();
        for (int slot = 0; slot < count && carried.getCount() < carried.getMaxStackSize(); slot++) {
          var stored = handler.getStackInSlot(slot);
          if (!ItemStack.isSameItemSameComponents(stored, carried)
              || pass == 0 && stored.getCount() >= stored.getMaxStackSize()) continue;
          var taken =
              handler.extractItem(slot, carried.getMaxStackSize() - carried.getCount(), false);
          if (ItemStack.isSameItemSameComponents(taken, carried)) carried.grow(taken.getCount());
          else recover(handler, slot, taken);
        }
      }
    }
    menu.setCarried(carried);
  }

  private void swap(EquippedContainers.Source source, int slot, int hotbar) {
    var handler = source.items();
    var incoming = owner.getInventory().getItem(hotbar).copy();
    var outgoing = handler.getStackInSlot(slot).copy();
    if (!incoming.isEmpty()
        && (ItemStack.isSameItemSameComponents(incoming, source.stack())
            || !handler.isItemValid(slot, incoming)
            || incoming.getCount()
                > Math.min(handler.getSlotLimit(slot), incoming.getMaxStackSize()))) return;
    if (!outgoing.isEmpty()
        && (outgoing.getCount() > outgoing.getMaxStackSize()
            || !ItemStack.matches(handler.extractItem(slot, outgoing.getCount(), true), outgoing)))
      return;
    var taken = handler.extractItem(slot, outgoing.getCount(), false);
    if (!ItemStack.matches(taken, outgoing)) {
      recover(handler, slot, taken);
      return;
    }
    owner.getInventory().setItem(hotbar, ItemStack.EMPTY);
    var remainder =
        incoming.isEmpty() ? ItemStack.EMPTY : handler.insertItem(slot, incoming, false);
    if (!incoming.isEmpty() && ItemStack.matches(remainder, incoming)) {
      owner.getInventory().setItem(hotbar, incoming);
      recover(handler, slot, taken);
    } else {
      owner.getInventory().setItem(hotbar, remainder.isEmpty() ? taken : remainder);
      if (!remainder.isEmpty()) giveToPlayer(taken);
    }
    owner.getInventory().setChanged();
  }

  private void recover(IItemHandler handler, int slot, ItemStack stack) {
    if (!stack.isEmpty()) giveToPlayer(handler.insertItem(slot, stack, false));
  }

  private void giveToPlayer(ItemStack stack) {
    insertIntoPlayer(stack);
    if (!stack.isEmpty()) owner.drop(stack, false);
  }

  private void insertIntoPlayer(ItemStack stack) {
    // Inventory.add can discard overflow in creative mode or merge into the offhand.
    // Recovery must preserve every item and use only ordinary player storage.
    for (int pass = 0; pass < 2; pass++)
      for (int i = 0; i < 36 && !stack.isEmpty(); i++) {
        var existing = owner.getInventory().getItem(i);
        if (!(pass == 0 ? ItemStack.isSameItemSameComponents(existing, stack) : existing.isEmpty()))
          continue;
        int room =
            Math.max(
                0,
                Math.min(owner.getInventory().getMaxStackSize(), stack.getMaxStackSize())
                    - existing.getCount());
        int amount = Math.min(room, stack.getCount());
        if (amount > 0) {
          owner.getInventory().setItem(i, stack.copyWithCount(existing.getCount() + amount));
          stack.shrink(amount);
        }
      }
    owner.getInventory().setChanged();
  }

  private void click(EquippedContainers.Source source, int slot, boolean right) {
    var handler = source.items();
    var carried = menu.getCarried();
    if (carried.isEmpty()) {
      var shown = handler.getStackInSlot(slot);
      if (shown.isEmpty()) return;
      int amount =
          Math.min(
              shown.getMaxStackSize(),
              right ? shown.getCount() / 2 + shown.getCount() % 2 : shown.getCount());
      menu.setCarried(handler.extractItem(slot, amount, false));
    } else if (equipment || !ItemStack.isSameItemSameComponents(carried, source.stack())) {
      int amount = right ? 1 : carried.getCount();
      var remainder = handler.insertItem(slot, carried.copyWithCount(amount), false);
      int inserted = amount - remainder.getCount();
      if (inserted > 0) menu.setCarried(carried.copyWithCount(carried.getCount() - inserted));
    }
  }

  private void storeFromPlayer(EquippedContainers.Source source, int index) {
    var original = owner.getInventory().getItem(index);
    if (original.isEmpty()
        || !equipment && ItemStack.isSameItemSameComponents(original, source.stack())) return;
    var remaining = original.copy();
    for (int pass = 0; pass < 2; pass++)
      for (int slot = 0; slot < source.items().getSlots() && !remaining.isEmpty(); slot++) {
        if (source.items() instanceof EquippedContainers.DescribedHandler described
            && !described.quickInsert(slot)) continue;
        var stored = source.items().getStackInSlot(slot);
        if (pass == 0 ? ItemStack.isSameItemSameComponents(stored, remaining) : stored.isEmpty())
          remaining = source.items().insertItem(slot, remaining, false);
      }
    owner.getInventory().setItem(index, remaining);
    owner.getInventory().setChanged();
  }

  private void takeToPlayer(IItemHandler handler, int slot) {
    var shown = handler.getStackInSlot(slot);
    if (shown.isEmpty()) return;
    var simulated = handler.extractItem(slot, shown.getMaxStackSize(), true);
    if (simulated.isEmpty()) return;
    int room = 0;
    for (int i = 0; i < 36; i++) {
      var existing = owner.getInventory().getItem(i);
      if (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, simulated))
        room +=
            Math.max(
                0,
                Math.min(owner.getInventory().getMaxStackSize(), simulated.getMaxStackSize())
                    - existing.getCount());
    }
    if (room == 0) return;
    var taken = handler.extractItem(slot, Math.min(room, simulated.getCount()), false);
    insertIntoPlayer(taken);
    // Preserve items even if a third-party extraction callback unexpectedly changed the
    // destination.
    if (!taken.isEmpty()) {
      var leftover = handler.insertItem(slot, taken, false);
      if (!leftover.isEmpty()) owner.drop(leftover, false);
    }
  }
}
