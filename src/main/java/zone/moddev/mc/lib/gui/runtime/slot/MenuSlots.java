package zone.moddev.mc.lib.gui.runtime.slot;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Native slot assembly and transfer routing, independent of concrete inventory/drawer types. */
public final class MenuSlots {

  public enum Role {
    MACHINE,
    AUXILIARY,
    PLAYER_STORAGE,
    EQUIPMENT
  }

  public record Entry(Slot slot, SlotElement element, Role role, String inventory) {
    public Entry {
      Objects.requireNonNull(inventory);
    }

    public Entry(Slot slot, SlotElement element, Role role) {
      this(slot, element, role, element != null && element.playerInventory() ? "player" : "items");
    }

    public SlotPlan plan() {
      return slot instanceof SlotAdapter adapter ? adapter.plan() : SlotPlan.NONE;
    }

    public boolean reservations() {
      return slot instanceof SlotAdapter
          && (element == null ? role == Role.MACHINE : element.reservationSync(this));
    }

    public boolean overlay() {
      return element != null && element.reservationOverlay(this);
    }
  }

  public interface Registrar {
    int nextIndex();

    default void add(Slot slot, SlotElement element, Role role) {
      add(slot, element, role, element != null && element.playerInventory() ? "player" : "items");
    }

    /** Supply the logical inventory name used when sampling element-owned slot hints. */
    void add(Slot slot, SlotElement element, Role role, String inventory);
  }

  @FunctionalInterface
  public interface Move {
    boolean into(ItemStack stack, int start, int end, boolean reverse);
  }

  private final List<Entry> entries = new ArrayList<>();
  private final Map<SlotElement, Slot> elements = new IdentityHashMap<>();
  private final Set<Slot> identities = Collections.newSetFromMap(new IdentityHashMap<>());

  public Registrar registrar(Consumer<Slot> add) {
    return new Registrar() {
      public int nextIndex() {
        return entries.size();
      }

      public void add(Slot slot, SlotElement element, Role role, String inventory) {
        Objects.requireNonNull(slot);
        Objects.requireNonNull(role);
        Objects.requireNonNull(inventory);
        if (!identities.add(slot) || element != null && elements.containsKey(element))
          throw new IllegalArgumentException("Duplicate native slot contribution");
        if (entries.size() >= 32767)
          throw new IllegalArgumentException("Too many native menu slots");
        add.accept(slot);
        if (slot.index != entries.size())
          throw new IllegalArgumentException("Native slot registrar changed slot numbering");
        entries.add(new Entry(slot, element, role, inventory));
        if (element != null) elements.put(element, slot);
      }
    };
  }

  public List<Entry> entries() {
    return List.copyOf(entries);
  }

  public Entry entry(int index) {
    return index < 0 || index >= entries.size() ? null : entries.get(index);
  }

  public Slot slot(SlotElement element) {
    return Objects.requireNonNull(elements.get(element), "Element has no native slot");
  }

  public SlotElement element(int index) {
    var entry = entry(index);
    return entry == null ? null : entry.element();
  }

  public SlotPlan plan(int index) {
    var entry = entry(index);
    return entry == null ? SlotPlan.NONE : entry.plan();
  }

  public boolean overlay(int index) {
    var entry = entry(index);
    return entry != null && entry.overlay();
  }

  /**
   * Server-only sampling for all native contributors, using registered identity and inventory
   * scope.
   */
  public Map<Integer, ItemStack> hints(ScreenHost host) {
    var result = new LinkedHashMap<Integer, ItemStack>();
    for (var entry : entries) {
      if (entry.element() == null) continue;
      var hint = entry.element().hint(host, entry.inventory()).copy();
      if (!hint.isEmpty()) result.put(entry.slot().index, hint);
    }
    return Collections.unmodifiableMap(result);
  }

  public void endInteraction() {
    for (var entry : entries)
      if (entry.slot() instanceof SlotAdapter adapter) adapter.endInteraction();
  }

  public ItemStack quickMove(
      Player player, int index, Function<ItemStack, List<Slot>> destinations, Move mover) {
    var entry = entry(index);
    if (entry == null) return ItemStack.EMPTY;
    var source = entry.slot();
    if (!source.isActive() || !source.mayPickup(player) || !source.hasItem())
      return ItemStack.EMPTY;
    java.util.function.Predicate<ItemStack> move =
        stack -> {
          var context = new SlotTransferContext(entry, entries(), stack, destinations);
          var targets =
              entry.element() == null
                  ? SlotElement.defaultQuickMoveTargets(context)
                  : entry.element().quickMoveTargets(context);
          var seen = Collections.newSetFromMap(new IdentityHashMap<Slot, Boolean>());
          var filtered =
              targets.stream()
                  .filter(identities::contains)
                  .filter(slot -> slot != source && slot.isActive() && slot.mayPlace(stack))
                  .filter(seen::add)
                  .toList();
          int before = stack.getCount();
          for (var slot : filtered)
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(slot.getItem(), stack))
              mover.into(stack, slot.index, slot.index + 1, false);
          for (var slot : filtered)
            if (!stack.isEmpty() && !slot.hasItem())
              mover.into(stack, slot.index, slot.index + 1, false);
          return stack.getCount() != before;
        };
    if (source instanceof SlotAdapter adapter) {
      var handled = adapter.quickMove(player, move);
      if (handled.isPresent()) return handled.get();
    }
    var stack = source.getItem();
    var original = stack.copy();
    if (!move.test(stack)) return ItemStack.EMPTY;
    if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY, original);
    else source.setChanged();
    source.onTake(player, stack);
    return original;
  }
}
