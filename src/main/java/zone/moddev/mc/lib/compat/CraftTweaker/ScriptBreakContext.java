package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.BlockBreak;

/**
 * What {@code onBreakBlock} (items) and {@code onBroken} (blocks) handlers receive: a player about
 * to break a block. Server side.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.BreakContext")
public final class ScriptBreakContext {
  private final BlockBreak broken;

  ScriptBreakContext(BlockBreak broken) {
    this.broken = broken;
  }

  @ZenCodeType.Getter("player")
  public Player getPlayer() {
    return broken.player();
  }

  /** The item in the player's main hand. */
  @ZenCodeType.Getter("tool")
  public IItemStack getTool() {
    return IItemStack.of(broken.tool());
  }

  /** The block's ID, such as {@code minecraft:stone}. */
  @ZenCodeType.Getter("block")
  public String getBlock() {
    return BuiltInRegistries.BLOCK.getKey(broken.state().getBlock()).toString();
  }

  @ZenCodeType.Getter("x")
  public int getX() {
    return broken.position().getX();
  }

  @ZenCodeType.Getter("y")
  public int getY() {
    return broken.position().getY();
  }

  @ZenCodeType.Getter("z")
  public int getZ() {
    return broken.position().getZ();
  }

  /** The face being mined, such as {@code up}; empty when it is not known. */
  @ZenCodeType.Getter("face")
  public String getFace() {
    return broken.face() == null ? "" : broken.face().getSerializedName();
  }

  /** Keeps the block: it is not broken and drops nothing. */
  @ZenCodeType.Method
  public void cancel() {
    broken.cancel();
  }

  /**
   * Breaks the block at {@code x, y, z} as this player would with their tool: drops, experience and
   * tool wear apply. Air, unbreakable blocks and blocks the tool cannot harvest are skipped, and
   * the extra breaks do not run the item's handler again.
   *
   * @return whether the block was broken
   */
  @ZenCodeType.Method
  public boolean mine(int x, int y, int z) {
    return broken.mine(new BlockPos(x, y, z));
  }
}
