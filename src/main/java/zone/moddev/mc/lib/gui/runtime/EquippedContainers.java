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

  public static List<Source> equipment(Player player, ItemStack identity) {
    if (!ModList.get().isLoaded("curios")) return List.of();
    return zone.moddev.mc.lib.compat.curios.CuriosEquipment.find(player, identity);
  }

  public static List<Source> find(Player player) {
    if (!ModList.get().isLoaded("curios")) return List.of();
    return zone.moddev.mc.lib.compat.curios.CuriosContainers.find(player);
  }
}
