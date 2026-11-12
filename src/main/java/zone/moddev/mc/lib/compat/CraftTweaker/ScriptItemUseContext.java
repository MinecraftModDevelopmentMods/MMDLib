package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Locale;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.world.entity.LivingEntity;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.ItemUse;

/**
 * What {@code onUseStart}, {@code onUseTick}, {@code onUseStop} and {@code
 * onUseFinish} handlers receive. Runs on both sides; check {@code client}
 * before changing the world.
 *
 * @docParam this context
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/ItemUseContext")
@ZenCodeType.Name("mods.mmdlib.ItemUseContext")
public final class ScriptItemUseContext {
    private final ItemUse use;

    ScriptItemUseContext(ItemUse use) {
        this.use = use;
    }

    /**
     * The stage of use: {@code start}, {@code tick}, {@code stop} or {@code
     * finish}.
     *
     * @return the phase
     */
    @ZenCodeType.Getter("phase")
    public String getPhase() {
        return use.phase().name().toLowerCase(Locale.ROOT);
    }

    /**
     * Who is using the item.
     *
     * @return the entity
     */
    @ZenCodeType.Getter("entity")
    public LivingEntity getEntity() {
        return use.entity();
    }

    /**
     * Whether this runs on the client; change the world only on the server.
     *
     * @return true on the client
     */
    @ZenCodeType.Getter("client")
    public boolean isClient() {
        return use.entity().level().isClientSide();
    }

    /**
     * The item being used.
     *
     * @return the item
     */
    @ZenCodeType.Getter("stack")
    public IItemStack getStack() {
        return IItemStack.of(use.stack());
    }

    /**
     * The total ticks of use at start; the ticks left later. Set it only in
     * {@code onUseStart}.
     *
     * @return the ticks
     */
    @ZenCodeType.Getter("duration")
    public int getDuration() {
        return use.duration();
    }

    /**
     * Sets how many ticks the use lasts; only in {@code onUseStart}.
     *
     * @param duration the ticks
     * @docParam duration 40
     */
    @ZenCodeType.Setter("duration")
    public void setDuration(int duration) {
        use.setDuration(duration);
    }

    /**
     * The stack left in hand when use finishes. Set it only in {@code
     * onUseFinish}.
     *
     * @return the resulting stack
     */
    @ZenCodeType.Getter("result")
    public IItemStack getResult() {
        return IItemStack.of(use.result());
    }

    /**
     * Sets the stack left in hand when use finishes; only in {@code
     * onUseFinish}.
     *
     * @param result the stack to leave
     * @docParam result <item:minecraft:bowl>
     */
    @ZenCodeType.Setter("result")
    public void setResult(IItemStack result) {
        use.setResult(result.getInternal());
    }

    /**
     * Stops the use; only in {@code onUseStart} and {@code onUseTick}.
     */
    @ZenCodeType.Method
    public void cancel() {
        use.cancel();
    }
}
