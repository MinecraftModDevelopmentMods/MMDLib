package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.world.entity.player.Player;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.BlockClick;

/**
 * What an {@code onRightClick} handler receives: the block, who clicked it and on which face. The
 * handler runs on both sides; the click is used up, so the held item does nothing.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ClickContext")
public final class ScriptClickContext extends ScriptBlockContext {
  private final BlockClick click;

  ScriptClickContext(BlockClick click) {
    super(click.use().level(), click.use().position());
    this.click = click;
  }

  /** The player who clicked. */
  @ZenCodeType.Getter("player")
  public Player getPlayer() {
    return click.player();
  }

  /** Whether the player was sneaking. */
  @ZenCodeType.Getter("sneaking")
  public boolean isSneaking() {
    return click.player().isShiftKeyDown();
  }

  /** The clicked face, such as {@code up}. */
  @ZenCodeType.Getter("face")
  public String getFace() {
    return click.use().face().getSerializedName();
  }
}
