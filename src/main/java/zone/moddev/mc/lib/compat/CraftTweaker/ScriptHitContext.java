package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.world.entity.LivingEntity;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.ItemHit;

/**
 * What an {@code onHit} handler receives: a melee hit that dealt damage. Server
 * side.
 *
 * @docParam this context
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/HitContext")
@ZenCodeType.Name("mods.mmdlib.HitContext")
public final class ScriptHitContext {
    private final ItemHit hit;

    ScriptHitContext(ItemHit hit) {
        this.hit = hit;
    }

    /**
     * The entity that hit.
     *
     * @return the attacker
     */
    @ZenCodeType.Getter("attacker")
    public LivingEntity getAttacker() {
        return hit.attacker();
    }

    /**
     * The entity that was hit.
     *
     * @return the target
     */
    @ZenCodeType.Getter("target")
    public LivingEntity getTarget() {
        return hit.target();
    }

    /**
     * The item that hit.
     *
     * @return the item
     */
    @ZenCodeType.Getter("stack")
    public IItemStack getStack() {
        return IItemStack.of(hit.stack());
    }

    /**
     * The damage dealt, after armor and other reductions.
     *
     * @return the damage
     */
    @ZenCodeType.Getter("damage")
    public float getDamage() {
        return hit.damage();
    }
}
