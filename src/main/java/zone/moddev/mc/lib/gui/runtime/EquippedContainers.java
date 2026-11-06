package zone.moddev.mc.lib.gui.runtime;

import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

/** Equipment discovery boundary. Storage access uses only the standard item capability. */
public final class EquippedContainers {
  private EquippedContainers() {}

  /** The live equipped stack, its stable equipment address, and its current capability. */
  public record Source(String key, ItemStack stack, IItemHandler items) {}

  /** Public slot presentation, independent of the optional Curios classes. */
  public record SlotInfo(
      String identifier,
      int index,
      boolean cosmetic,
      boolean available,
      net.minecraft.resources.ResourceLocation icon) {
    public SlotInfo {
      if (identifier.isBlank()
          || identifier.length() > 128
          || index < 0
          || index >= ContainerDrawerMenu.MAX_SLOTS)
        throw new IllegalArgumentException("Invalid equipment slot");
    }
  }

  public interface DescribedHandler extends IItemHandler {
    SlotInfo describe(int slot);

    default boolean quickInsert(int slot) {
      return !describe(slot).cosmetic();
    }
  }

  /** One equipment scan shared by both drawers. Handler contents remain live. */
  public record Snapshot(List<Source> containers, List<Source> equipment) {
    public Snapshot {
      containers = List.copyOf(containers);
      equipment = List.copyOf(equipment);
    }
  }

  /** Menu-local cache, discarded with its viewer; mutations always invalidate it. */
  public static final class Discovery {
    private final java.util.function.LongSupplier clock;
    private final java.util.function.Supplier<Snapshot> scan;
    private long tick = Long.MIN_VALUE;
    private Snapshot snapshot;

    public Discovery(Player player) {
      this(player.level()::getGameTime, scanner(player));
    }

    Discovery(java.util.function.LongSupplier clock, java.util.function.Supplier<Snapshot> scan) {
      this.clock = clock;
      this.scan = scan;
    }

    public Snapshot get() {
      long now = clock.getAsLong();
      if (snapshot == null || tick != now) {
        snapshot = scan.get();
        tick = now;
      }
      return snapshot;
    }

    public void invalidate() {
      snapshot = null;
    }

    private static java.util.function.Supplier<Snapshot> scanner(Player player) {
      var identity = new ItemStack(net.minecraft.world.item.Items.ENDER_EYE);
      return () -> discover(player, identity);
    }
  }

  private static Snapshot discover(Player player, ItemStack identity) {
    // Kept outside the optional adapter so a server without Curios never loads its API.
    if (!ModList.get().isLoaded("curios")) return new Snapshot(List.of(), List.of());
    return zone.moddev.mc.lib.compat.curios.CuriosEquipment.discover(player, identity);
  }

  public static List<Source> equipment(Player player, ItemStack identity) {
    if (!ModList.get().isLoaded("curios")) return List.of();
    return zone.moddev.mc.lib.compat.curios.CuriosEquipment.find(player, identity);
  }

  public static List<Source> find(Player player) {
    if (!ModList.get().isLoaded("curios")) return List.of();
    return zone.moddev.mc.lib.compat.curios.CuriosContainers.find(player);
  }
}
