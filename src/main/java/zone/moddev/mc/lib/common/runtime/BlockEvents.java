package zone.moddev.mc.lib.common.runtime;

import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.capabilities.Capabilities;

import zone.moddev.mc.lib.api.MaterialEventContexts.BlockUse;

/**
 * Generic block handlers declared by {@code mmdlib/extension/blocks.json}. {@code mmdlib:open_menu}
 * opens the block's own menu provider (anvils, crafting-table-style blocks); blocks without one
 * pass.
 */
public final class BlockEvents {
  private BlockEvents() {}

  /**
   * Handler {@code mmdlib:open_menu} ({@code block-right-click}): opens the clicked block's menu on
   * the server and consumes the click; does nothing for blocks without a menu. A fluid container
   * held against a block with a fluid handler on the clicked face is left to the block's own item
   * use, so buckets fill and empty it instead of opening the screen.
   */
  public static void openMenu(Player player, BlockUse use) {
    if (use.stack().getCapability(Capabilities.FluidHandler.ITEM) != null
        && use.level()
                .getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    use.position(),
                    use.state(),
                    use.level().getBlockEntity(use.position()),
                    use.face())
            != null) return;
    if (zone.moddev.mc.lib.gui.runtime.ScreenRuntime.open(
        player, use.level().getBlockEntity(use.position()))) {
      use.consume();
      return;
    }
    var provider = use.state().getMenuProvider(use.level(), use.position());
    if (provider == null) return;
    // Consumed on both sides so the client does not go on to use the held item.
    use.consume();
    if (use.level().isClientSide) return;
    player.openMenu(provider);
    if (use.state().is(BlockTags.ANVIL)) player.awardStat(Stats.INTERACT_WITH_ANVIL);
  }
}
