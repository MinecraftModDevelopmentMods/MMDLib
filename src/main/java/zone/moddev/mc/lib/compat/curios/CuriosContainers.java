package zone.moddev.mc.lib.compat.curios;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.Capabilities;

import top.theillusivec4.curios.api.CuriosApi;

import zone.moddev.mc.lib.MMDLibConfig;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers.Source;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;

/** Loaded only after the Curios mod-presence check. Cosmetic slots are intentionally excluded. */
public final class CuriosContainers {
  private CuriosContainers() {}

  public static List<Source> find(Player player) {
    var result = new ArrayList<Source>();
    CuriosApi.getCuriosInventory(player)
        .ifPresent(
            inventory -> {
              for (String name : new LinkedHashSet<>(MMDLibConfig.CONTAINER_SLOTS.get())) {
                inventory
                    .getStacksHandler(name)
                    .ifPresent(
                        slots -> {
                          var stacks = slots.getStacks();
                          for (int i = 0;
                              i < stacks.getSlots()
                                  && result.size() < ContainerDrawerMenu.MAX_CONTAINERS;
                              i++) {
                            var stack = stacks.getStackInSlot(i);
                            if (stack.isEmpty() || !inventory.isSlotActive(name, i)) continue;
                            var handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
                            if (handler != null
                                && handler.getSlots() > 0
                                && handler.getSlots() <= ContainerDrawerMenu.MAX_SLOTS)
                              result.add(new Source(name + ":" + i, stack, handler));
                          }
                        });
              }
            });
    return List.copyOf(result);
  }
}
