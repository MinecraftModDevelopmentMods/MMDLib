package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;

import net.minecraft.world.entity.LivingEntity;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.ItemHit;

/** What an {@code onHit} handler receives: a melee hit that dealt damage. Server side. */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.HitContext")
public final class ScriptHitContext {
  private final ItemHit hit;

  ScriptHitContext(ItemHit hit) {
    this.hit = hit;
  }

  @ZenCodeType.Getter("attacker")
  public LivingEntity getAttacker() {
    return hit.attacker();
  }

  @ZenCodeType.Getter("target")
  public LivingEntity getTarget() {
    return hit.target();
  }

  /** The item that hit. */
  @ZenCodeType.Getter("stack")
  public IItemStack getStack() {
    return IItemStack.of(hit.stack());
  }

  /** The damage dealt, after armor and other reductions. */
  @ZenCodeType.Getter("damage")
  public float getDamage() {
    return hit.damage();
  }
}
