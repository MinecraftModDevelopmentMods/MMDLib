package zone.moddev.mc.lib.gui.runtime;

import java.util.*;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.component.*;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/** The screen's inventory elements own their native slots, hints and insertion destinations. */
public final class InventoryMenuComponent
    implements MenuSlotContributor, MenuSnapshot, InventoryAccess {
  public static final ComponentSync<Map<Integer, ItemStack>> HINTS =
      new ComponentSync<>(
          net.minecraft.resources.ResourceLocation.parse("mmdlib:slot_hints"),
          2,
          net.minecraft.network.codec.StreamCodec.of(
              (buffer, values) -> {
                if (values.size() > 32767)
                  throw new IllegalArgumentException("Too many slot hints");
                buffer.writeVarInt(values.size());
                new TreeMap<>(values)
                    .forEach(
                        (index, stack) -> {
                          buffer.writeVarInt(index);
                          ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
                        });
              },
              buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > 32767)
                  throw new IllegalArgumentException("Too many slot hints");
                var result = new HashMap<Integer, ItemStack>();
                for (int i = 0; i < count; i++) {
                  int index = buffer.readVarInt();
                  if (index < 0
                      || index >= 32767
                      || result.putIfAbsent(index, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer))
                          != null) throw new IllegalArgumentException("Invalid slot hint index");
                }
                return Map.copyOf(result);
              }),
          65536,
          Map::of,
          values ->
              values.size() <= 32767
                  && values.keySet().stream().allMatch(index -> index >= 0 && index < 32767));
  private final MenuComponentRegistry.Context context;
  private final InventoryElement.Session session;
  private Map<Integer, ItemStack> hints = Map.of();

  public InventoryMenuComponent(MenuComponentRegistry.Context context) {
    this.context = context;
    var menu = context.menu();
    session =
        new InventoryElement.Session(
            menu.bound.screen(),
            context.host(),
            menu.layout(),
            context.client(),
            menu::visible,
            menu::interacting,
            () -> {
              if (!context.client()) menu.blockEntity.setChanged();
            });
  }

  public InventoryElement.Session session() {
    return session;
  }

  public int menuSlot(int index) {
    int local = session.legacyIndex(index);
    return local < 0 ? -1 : session.slots().get(local).index;
  }

  public int menuSlot(zone.moddev.mc.lib.api.capabilities.ItemInventories.Address address) {
    int local = session.index(address);
    return local < 0 ? -1 : session.slots().get(local).index;
  }

  public boolean ghost(
      SlotElement element, net.minecraft.world.entity.player.Player player, ItemStack stack) {
    return session.ghost(element, player, stack);
  }

  public void addSlots(MenuSlots.Registrar slots, ScreenLayout layout) {
    for (int i = 0; i < session.slots().size(); i++) {
      var element = session.element(i);
      slots.add(
          session.slots().get(i),
          element,
          element == null ? MenuSlots.Role.MACHINE : element.role(),
          element == null ? "items" : session.inventory(element));
    }
  }

  public void finishSlots(MenuSlots.Registrar slots, ScreenLayout layout) {
    for (var slot : session.playerSlots(slots.nextIndex(), context.inventory(), layout)) {
      var element = session.element(slots.nextIndex());
      slots.add(slot, element, element.role(), session.inventory(element));
    }
  }

  public void position(ScreenLayout layout) {
    session.position(layout);
  }

  public List<Slot> destinations(ItemStack stack) {
    return session.destinations(stack).stream().map(index -> session.slots().get(index)).toList();
  }

  public void beforeBroadcast() {
    if (!context.client()) session.synchronizeGhosts();
  }

  public void snapshot(ComponentSnapshot values) {
    values.put(HINTS, context.menu().nativeSlots().hints(context.host()));
  }

  public ItemStack hint(SlotElement slot) {
    return hints.getOrDefault(context.menu().slotIndex(slot), ItemStack.EMPTY).copy();
  }

  public void applied(ComponentStateView values) {
    hints = values.get(HINTS);
  }
}
