package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Locale;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;

import net.minecraft.world.entity.LivingEntity;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.ItemUse;

/**
 * What {@code onUseStart}, {@code onUseTick}, {@code onUseStop} and
 * {@code onUseFinish} handlers receive. Runs on both sides; check
 * {@code client} before changing the world.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ItemUseContext")
public final class ScriptItemUseContext {
    private final ItemUse use;

    ScriptItemUseContext(ItemUse use) {
        this.use = use;
    }

    /** {@code start}, {@code tick}, {@code stop} or {@code finish}. */
    @ZenCodeType.Getter("phase")
    public String getPhase() {
        return use.phase().name().toLowerCase(Locale.ROOT);
    }

    /** Who is using the item. */
    @ZenCodeType.Getter("entity")
    public LivingEntity getEntity() {
        return use.entity();
    }

    @ZenCodeType.Getter("client")
    public boolean isClient() {
        return use.entity().level().isClientSide();
    }

    @ZenCodeType.Getter("stack")
    public IItemStack getStack() {
        return IItemStack.of(use.stack());
    }

    /**
     * The total ticks of use at start; the ticks left later. Set it only in
     * {@code onUseStart}.
     */
    @ZenCodeType.Getter("duration")
    public int getDuration() {
        return use.duration();
    }

    @ZenCodeType.Setter("duration")
    public void setDuration(int duration) {
        use.setDuration(duration);
    }

    /**
     * The stack left in hand when use finishes. Set it only in
     * {@code onUseFinish}.
     */
    @ZenCodeType.Getter("result")
    public IItemStack getResult() {
        return IItemStack.of(use.result());
    }

    @ZenCodeType.Setter("result")
    public void setResult(IItemStack result) {
        use.setResult(result.getInternal());
    }

    /** Stops the use; only in {@code onUseStart} and {@code onUseTick}. */
    @ZenCodeType.Method
    public void cancel() {
        use.cancel();
    }
}
