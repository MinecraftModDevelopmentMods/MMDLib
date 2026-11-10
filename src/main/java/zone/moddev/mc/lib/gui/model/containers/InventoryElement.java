package zone.moddev.mc.lib.gui.model.containers;

import java.util.*;
import java.util.function.*;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.InventoryScope;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.ClientMenuItems;
import zone.moddev.mc.lib.gui.runtime.InventoryBinding;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/** A named inventory view containing independently typed slots, with shared grid geometry. */
public class InventoryElement implements InventoryScope {
  private final Common common;
  private final String inventory;
  private final int columns;
  private final int spacing;
  private final List<Element> children;

  public InventoryElement(
      Common common, String inventory, int columns, int spacing, List<Element> children) {
    Objects.requireNonNull(common);
    if (inventory == null || !inventory.matches("[a-z0-9_.-]{1,64}"))
      throw new IllegalArgumentException("Invalid inventory name");
    if (columns < 1 || spacing < 0) throw new IllegalArgumentException("Invalid inventory layout");
    children = List.copyOf(children);
    if (children.stream().anyMatch(child -> !(child instanceof SlotElement)))
      throw new IllegalArgumentException("An inventory contains only item or ghost slots");

    this.common = common;
    this.inventory = inventory;
    this.columns = columns;
    this.spacing = spacing;
    this.children = children;
  }

  public List<SlotElement> slots() {
    return children.stream().map(SlotElement.class::cast).toList();
  }

  public Rect slotBounds(int index) {
    var slot = slots().get(index);
    return positions().get(slot).offset(slot.common().offsetX(), slot.common().offsetY());
  }

  public Map<SlotElement, Rect> positions() {
    var slots = slots();
    int[] widths = new int[Math.min(columns, slots.size())];
    int[] heights = new int[(slots.size() + columns - 1) / columns];
    for (int i = 0; i < slots.size(); i++) {
      widths[i % columns] = Math.max(widths[i % columns], slots.get(i).extent());
      heights[i / columns] = Math.max(heights[i / columns], slots.get(i).extent());
    }
    var result = new IdentityHashMap<SlotElement, Rect>();
    for (int i = 0; i < slots.size(); i++) {
      int x = 0, y = 0;
      for (int col = 0; col < i % columns; col++) x += widths[col] + spacing;
      for (int row = 0; row < i / columns; row++) y += heights[row] + spacing;
      var slot = slots.get(i);
      result.put(slot, new Rect(x, y, slot.extent(), slot.extent()));
    }
    return result;
  }

  public int[] dimensions() {
    var bounds = positions().values();
    return new int[] {
      bounds.stream().mapToInt(Rect::right).max().orElse(0),
      bounds.stream().mapToInt(Rect::bottom).max().orElse(0)
    };
  }

  /** Menu-local assembly of independently backed inventory views. */
  public static final class Session {
    private final ScreenHost host;
    private final List<SlotElement> playerElements;
    private final Predicate<Element> visible;
    private final List<Slot> slots;
    private final Map<SlotElement, Integer> indexes = new IdentityHashMap<>();
    private final Map<SlotElement, String> names = new IdentityHashMap<>();
    private final Map<Integer, SlotElement> elements = new HashMap<>();
    private final Map<Integer, Integer> legacyIndexes = new HashMap<>();
    private final Map<ItemInventories.Address, Integer> addresses = new HashMap<>();
    private final Map<Integer, Slot> playerSlots = new LinkedHashMap<>();

    public Session(
        Screen screen,
        ScreenHost host,
        ScreenLayout layout,
        boolean client,
        Predicate<Element> visible,
        BooleanSupplier interacting,
        Runnable changed) {
      this.host = host;
      this.visible = visible;
      playerElements =
          screen.elements().stream()
              .filter(SlotElement.class::isInstance)
              .map(SlotElement.class::cast)
              .filter(SlotElement::playerInventory)
              .sorted(Comparator.comparingInt(SlotElement::playerSlotOrder))
              .toList();
      var handlers = new IdentityHashMap<IItemHandlerModifiable, IItemHandlerModifiable>();
      var boundSlots = new IdentityHashMap<IItemHandlerModifiable, Set<Integer>>();
      var pending = new TreeMap<Integer, Slot>();
      var bindings = new IdentityHashMap<SlotElement, InventoryBinding>();
      int next = host == null ? 0 : host.screenLegacySlotCount();
      if (next < 0 || next > 32767) throw new IllegalArgumentException("Invalid legacy slot count");
      // Resolve first so automatically numbered slots cannot occupy a later legacy binding.
      for (var e : screen.elements()) {
        if (!(e instanceof SlotElement slot) || slot.playerInventory()) continue;
        boolean standalone = InventoryScope.owner(screen, slot).isEmpty();
        if (slot.ghost()) {
          if (standalone) next = Math.max(next, slot.index() + 1);
        } else {
          if (host == null) throw new IllegalArgumentException("Item slots require a ScreenHost");
          var binding =
              Objects.requireNonNull(
                  host.screenInventoryBinding(
                      InventoryScope.name(screen, slot), slot.index(), standalone));
          bindings.put(slot, binding);
          next = Math.max(next, binding.legacyIndex() + 1);
        }
      }
      for (var element : screen.elements())
        if (element instanceof SlotElement slot && !slot.playerInventory()) {
          String name = InventoryScope.name(screen, slot);
          boolean standalone = InventoryScope.owner(screen, slot).isEmpty();
          names.put(slot, name);
          var binding = bindings.get(slot);
          int preferred = binding == null ? standalone ? slot.index() : -1 : binding.legacyIndex();
          int number = preferred >= 0 ? preferred : next++;
          if (number >= 32767) throw new IllegalArgumentException("Too many native menu slots");
          Slot nativeSlot;
          List<ItemInventories.Address> aliases;
          if (slot.ghost()) {
            nativeSlot =
                slot.createSlot(
                    null,
                    slot.index(),
                    layout.bounds(slot),
                    () -> visible.test(slot),
                    interacting,
                    changed);
            aliases =
                host == null
                    ? List.of(new ItemInventories.Address(name, slot.index()))
                    : host.screenSlotAddresses(name, slot.index(), standalone);
          } else {
            var physical = binding.storage();
            int local = binding.slot();
            if (!boundSlots.computeIfAbsent(physical, key -> new HashSet<>()).add(local))
              throw new IllegalArgumentException(
                  "A physical inventory slot is displayed more than once: "
                      + name
                      + "/"
                      + slot.index());
            IItemHandlerModifiable menuStorage =
                client
                    ? handlers.computeIfAbsent(
                        physical, key -> new ClientMenuItems(key.getSlots(), key))
                    : physical;
            nativeSlot =
                slot.createSlot(
                    menuStorage,
                    local,
                    layout.bounds(slot),
                    () -> visible.test(slot),
                    interacting,
                    changed);
            if (binding.legacyIndex() >= 0) legacyIndexes.put(binding.legacyIndex(), number);
            aliases = binding.addresses();
          }
          if (pending.putIfAbsent(number, nativeSlot) != null)
            throw new IllegalArgumentException(
                "A storage slot is displayed more than once: " + name + "/" + slot.index());
          indexes.put(slot, number);
          elements.put(number, slot);
          for (var address : aliases) {
            var existing = addresses.putIfAbsent(address, number);
            if (existing != null && existing != number)
              throw new IllegalArgumentException(
                  "An inventory address is displayed more than once: " + address);
          }
        }
      int count = pending.isEmpty() ? 0 : pending.lastKey() + 1;
      var assembled = new ArrayList<Slot>();
      for (int i = 0; i < count; i++) {
        Slot slot = pending.get(i);
        if (slot == null)
          slot =
              new Slot(new net.minecraft.world.SimpleContainer(1), 0, -10000, -10000) {
                public boolean isActive() {
                  return false;
                }

                public boolean mayPlace(ItemStack stack) {
                  return false;
                }

                public boolean mayPickup(Player player) {
                  return false;
                }
              };
        assembled.add(slot);
      }
      slots = List.copyOf(assembled);
    }

    public List<Slot> slots() {
      return slots;
    }

    public int index(SlotElement element) {
      return indexes.getOrDefault(element, -1);
    }

    public Slot slot(SlotElement element) {
      int index = index(element);
      return playerSlots.containsKey(index) ? playerSlots.get(index) : slots.get(index);
    }

    public SlotElement element(int menuIndex) {
      return elements.get(menuIndex);
    }

    public int legacyIndex(int storageIndex) {
      return legacyIndexes.getOrDefault(storageIndex, -1);
    }

    public int index(ItemInventories.Address address) {
      return addresses.getOrDefault(address, -1);
    }

    public List<Integer> destinations(ItemStack stack) {
      if (host == null) return List.of();
      var result = new LinkedHashSet<Integer>();
      for (var destination : host.screenItemDestinations(stack)) {
        int index = index(destination);
        if (index >= 0) result.add(index);
      }
      return List.copyOf(result);
    }

    public void position(ScreenLayout layout) {
      indexes.forEach(
          (element, index) -> {
            var bounds = layout.bounds(element);
            var xy = element.itemPosition(bounds);
            var slot = slot(element);
            slot.x = xy[0];
            slot.y = xy[1];
          });
    }

    public List<Slot> playerSlots(
        int start, net.minecraft.world.entity.player.Inventory inventory, ScreenLayout layout) {
      for (var element : playerElements) {
        int index = start + playerSlots.size();
        indexes.put(element, index);
        names.put(element, "player");
        elements.put(index, element);
        playerSlots.put(
            index,
            element.createPlayerSlot(
                inventory, layout.bounds(element), () -> visible.test(element)));
      }
      return List.copyOf(playerSlots.values());
    }

    public List<SlotElement> playerElements() {
      return playerSlots.keySet().stream().map(elements::get).toList();
    }

    public void synchronizeGhosts() {
      if (host != null)
        indexes.forEach(
            (element, index) -> {
              if (element.ghost()) slots.get(index).set(element.read(host, names.get(element)));
            });
    }

    public boolean ghost(SlotElement element, Player player, ItemStack stack) {
      var index = indexes.get(element);
      return host != null
          && element.ghost()
          && index != null
          && index < slots.size()
          && slots.get(index).isActive()
          && element.accept(host, names.get(element), player, stack);
    }

    public String inventory(SlotElement element) {
      return Objects.requireNonNull(names.get(element), "Element has no inventory binding");
    }

    public ItemStack hint(SlotElement element) {
      return element.hint(host, names.get(element)).copy();
    }
  }

  public int[] measure(TextMeasure text) {
    return dimensions();
  }

  public java.util.Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
    var result = new java.util.IdentityHashMap<Element, Rect>();
    positions().forEach((slot, rect) -> result.put(slot, rect));
    return result;
  }

  public Common common() {
    return common;
  }

  public String inventory() {
    return inventory;
  }

  public int columns() {
    return columns;
  }

  public int spacing() {
    return spacing;
  }

  public List<Element> children() {
    return children;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var other = (InventoryElement) object;
    return java.util.Objects.equals(common, other.common)
        && java.util.Objects.equals(inventory, other.inventory)
        && java.util.Objects.equals(columns, other.columns)
        && java.util.Objects.equals(spacing, other.spacing)
        && java.util.Objects.equals(children, other.children);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash = 31 * hash + java.util.Objects.hashCode(common);
    hash = 31 * hash + java.util.Objects.hashCode(inventory);
    hash = 31 * hash + java.util.Objects.hashCode(columns);
    hash = 31 * hash + java.util.Objects.hashCode(spacing);
    hash = 31 * hash + java.util.Objects.hashCode(children);
    return hash;
  }

  @Override
  public String toString() {
    return "InventoryElement[common="
        + common
        + ", inventory="
        + inventory
        + ", columns="
        + columns
        + ", spacing="
        + spacing
        + ", children="
        + children
        + "]";
  }
}
