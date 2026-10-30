package zone.moddev.mc.lib.gui.runtime;

import java.util.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.*;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.load.*;
import zone.moddev.mc.lib.gui.model.Element;

/** One menu for all XML screens. Slot numbers retain the machine's storage numbering. */
public class MachineMenu extends AbstractContainerMenu {
  public final BlockEntity blockEntity;
  public final ScreenBinder.Bound bound;
  public final List<Element> elements;
  private final Map<String, String> views = new HashMap<>();
  private final Map<Element, Integer> elementIndexes = new IdentityHashMap<>();
  private final Map<Integer, Object> values = new HashMap<>();
  private final ScreenHost host;
  private final int machineSlots;
  private final Player owner;
  private boolean initial = true;

  public MachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
    this(id, inventory, readEntity(inventory, data));
  }

  private static BlockEntity readEntity(Inventory inventory, RegistryFriendlyByteBuf data) {
    var entity =
        Objects.requireNonNull(
            inventory.player.level().getBlockEntity(data.readBlockPos()),
            "Screen block entity missing");
    String expected = data.readUtf(64);
    var screen =
        Screens.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType())).orElseThrow();
    if (!ScreenRuntime.fingerprint(screen.screen()).equals(expected))
      throw new IllegalStateException(
          "Client/server screen definitions differ: " + screen.screen().id());
    return entity;
  }

  public MachineMenu(int id, Inventory inventory, BlockEntity entity) {
    super(ScreenRuntime.MENUS.get(), id);
    owner = inventory.player;
    blockEntity = entity;
    bound = Screens.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType())).orElseThrow();
    if (!bound.blockEntityClass().isInstance(entity))
      throw new IllegalArgumentException("Wrong screen block entity");
    elements = bound.screen().elements();
    for (int i = 0; i < elements.size(); i++) elementIndexes.put(elements.get(i), i);
    if (elements.size() > 4096)
      throw new IllegalArgumentException("A screen may have at most 4096 elements");
    for (var element : elements)
      if (element instanceof Element.SideConfig config && config.groups().size() > 682)
        throw new IllegalArgumentException("Too many side configuration groups");
    host = entity instanceof ScreenHost h ? h : null;
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
    IItemHandler items =
        owner.level().isClientSide
            ? new ItemStackHandler(count)
            : host == null ? new ItemStackHandler(0) : host.screenItems();
    machineSlots = count;
    var layout =
        ScreenLayout.of(
            bound.screen(),
            owner.level().isClientSide ? ScreenRuntime.textMeasure : TextMeasure.NONE);
    for (int n = 0; n < count; n++) {
      final int index = n;
      var element =
          elements.stream()
              .filter(e -> e instanceof Element.Slot s && s.index() == index)
              .map(e -> (Element.Slot) e)
              .findFirst()
              .orElse(null);
      if (element != null && element.kind() != Element.SlotKind.GHOST && index >= items.getSlots())
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
            new SlotItemHandler(items, index, xy[0], xy[1]) {
              public boolean isActive() {
                return visible(element);
              }

              public boolean mayPlace(ItemStack stack) {
                return isActive() && super.mayPlace(stack);
              }

              public boolean mayPickup(Player player) {
                return isActive() && super.mayPickup(player);
              }

              public int getMaxStackSize() {
                return element.kind() == Element.SlotKind.UPGRADE ? 1 : super.getMaxStackSize();
              }

              public int getMaxStackSize(ItemStack stack) {
                return Math.min(getMaxStackSize(), super.getMaxStackSize(stack));
              }
            });
    }
    layout
        .inventory()
        .ifPresent(
            r -> {
              for (int row = 0; row < 3; row++)
                for (int col = 0; col < 9; col++)
                  addSlot(
                      new Slot(
                          inventory,
                          9 + row * 9 + col,
                          r.x() + col * 18 + 1,
                          r.y() + 11 + row * 18 + 1));
              for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col, r.x() + col * 18 + 1, r.y() + 11 + 58 + 1));
            });
  }

  public Map<String, String> views() {
    return Map.copyOf(views);
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

  public void apply(Map<Integer, Object> delta) {
    values.putAll(delta);
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
    for (var root : bound.screen().body())
      if (visibleIn(root, element, true) != null)
        return Boolean.TRUE.equals(visibleIn(root, element, true));
    for (var root : bound.screen().tabs())
      if (visibleIn(root, element, true) != null)
        return Boolean.TRUE.equals(visibleIn(root, element, true));
    return false;
  }

  private Boolean visibleIn(Element root, Element target, boolean shown) {
    if (root == target) return shown;
    for (var child : root.children()) {
      boolean next = shown;
      if (root instanceof Element.Context c)
        next &= child.common().id().orElse("").equals(views.get(c.common().id().orElse("")));
      Boolean found = visibleIn(child, target, next);
      if (found != null) return found;
    }
    return null;
  }

  public void navigate(String context, String view) {
    var e = bound.screen().find(context).orElse(null);
    if (e instanceof Element.Context c
        && c.views().stream().anyMatch(v -> v.common().id().orElse("").equals(view)))
      views.put(context, view);
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
    super.clicked(slot, button, type, player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    var source = slots.get(index);
    if (!source.isActive() || !source.mayPickup(player) || !source.hasItem())
      return ItemStack.EMPTY;
    var stack = source.getItem();
    var original = stack.copy();
    if (index < machineSlots) {
      if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
    } else {
      if (host == null) return ItemStack.EMPTY;
      for (int destination : host.screenDestinations(stack))
        if (destination >= 0
            && destination < machineSlots
            && slots.get(destination).isActive()
            && slots.get(destination).mayPlace(stack))
          moveItemStackTo(stack, destination, destination + 1, false);
      if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
    else source.setChanged();
    source.onTake(player, stack);
    return original;
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
    super.broadcastChanges();
    if (!(owner instanceof net.minecraft.server.level.ServerPlayer player) || !stillValid(owner))
      return;
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
    var delta = ScreenValues.changed(values, snapshot, initial);
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
