package zone.moddev.mc.lib.common.runtime;

import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;

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
   * the server and consumes the click; does nothing for blocks without a menu.
   */
  public static void openMenu(Player player, BlockUse use) {
    var provider = use.state().getMenuProvider(use.level(), use.position());
    if (provider == null) return;
    // Consumed on both sides so the client does not go on to use the held item.
    use.consume();
    if (use.level().isClientSide) return;
    player.openMenu(provider);
    if (use.state().is(BlockTags.ANVIL)) player.awardStat(Stats.INTERACT_WITH_ANVIL);
  }
}
