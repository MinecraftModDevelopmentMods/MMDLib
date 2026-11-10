package zone.moddev.mc.lib.gui.runtime;

import java.util.function.Supplier;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/**
 * Per-menu target addressing. Wire bounds and ordinary player storage stay independent of UI
 * policy.
 */
public final class ContainerTargets {
  public static final int PLAYER_SLOTS = 36, HOTBAR_SLOTS = 9;
  public static final int MAX_TARGETS = ContainerDrawerMenu.MAX_PAGE + PLAYER_SLOTS;
  public static final int TARGET_LIMIT = ContainerDrawerMenu.MAX_SLOTS + PLAYER_SLOTS;
  private final Player owner;
  private final MenuSlots nativeSlots;
  private final Supplier<ContainerDrawerNetwork.State> state;
  private final IItemHandler playerItems;

  ContainerTargets(
      Player owner, MenuSlots nativeSlots, Supplier<ContainerDrawerNetwork.State> state) {
    this.owner = owner;
    this.nativeSlots = nativeSlots;
    this.state = state;
    playerItems = new InvWrapper(owner.getInventory());
  }

  /** A native hit must be registered, active and backed by this viewer's ordinary storage. */
  public int playerTarget(Slot hit) {
    if (hit == null || !hit.isActive()) return -1;
    var entry = nativeSlots.entry(hit.index);
    return entry != null
            && entry.slot() == hit
            && hit.container == owner.getInventory()
            && playerIndexValid(hit.getContainerSlot())
        ? ContainerDrawerMenu.MAX_SLOTS + hit.getContainerSlot()
        : -1;
  }

  public static boolean playerIndexValid(int index) {
    return index >= 0 && index < PLAYER_SLOTS;
  }

  public int playerIndex(int target) {
    int index = target - ContainerDrawerMenu.MAX_SLOTS;
    return playerIndexValid(index) ? index : -1;
  }

  public int maxTargets() {
    return MAX_TARGETS;
  }

  public boolean contains(int target) {
    var page = state.get();
    return playerIndex(target) >= 0
        || page.open()
            && target >= page.offset()
            && target < page.offset() + page.contents().size();
  }

  /** Detached contents for client gesture previews; invalid/stale page addresses are empty. */
  public ItemStack contents(int target) {
    if (!contains(target)) return ItemStack.EMPTY;
    int player = playerIndex(target);
    var page = state.get();
    return (player >= 0
            ? owner.getInventory().getItem(player)
            : page.contents().get(target - page.offset()))
        .copy();
  }

  record Resolved(IItemHandler items, int index) {}

  /** Server resolution rechecks the live source bounds and visible page. */
  Resolved resolve(int target, EquippedContainers.Source source) {
    int player = playerIndex(target);
    if (player >= 0) return new Resolved(playerItems, player);
    var page = state.get();
    return page.open()
            && target >= page.offset()
            && target < Math.min(page.offset() + page.pageSize(), source.items().getSlots())
        ? new Resolved(source.items(), target)
        : null;
  }
}
