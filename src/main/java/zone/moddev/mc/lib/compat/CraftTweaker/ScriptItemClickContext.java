package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.AirTarget;
import zone.moddev.mc.lib.api.MaterialEventContexts.BlockTarget;
import zone.moddev.mc.lib.api.MaterialEventContexts.EntityTarget;
import zone.moddev.mc.lib.api.MaterialEventContexts.ToolUse;

/**
 * What an item's {@code onRightClick} handler receives: a right-click with the
 * item on air, a block or an entity. Runs on both sides, alongside the item's
 * own use.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ItemClickContext")
public final class ScriptItemClickContext {
    private final ToolUse use;

    ScriptItemClickContext(ToolUse use) {
        this.use = use;
    }

    @ZenCodeType.Getter("player")
    public Player getPlayer() {
        return use.player();
    }

    @ZenCodeType.Getter("client")
    public boolean isClient() {
        return use.player().level().isClientSide();
    }

    @ZenCodeType.Getter("stack")
    public IItemStack getStack() {
        return IItemStack.of(use.target().stack());
    }

    /** What was clicked: {@code air}, {@code block} or {@code entity}. */
    @ZenCodeType.Getter("target")
    public String getTarget() {
        return switch (use.target()) {
            case BlockTarget block -> "block";
            case EntityTarget entity -> "entity";
            case AirTarget air -> "air";
        };
    }

    /** The clicked entity; null unless {@code target} is {@code entity}. */
    @ZenCodeType.Nullable
    @ZenCodeType.Getter("entity")
    public Entity getEntity() {
        return use.target() instanceof EntityTarget target
            ? target.entity()
            : null;
    }

    /**
     * The clicked block's position; 0 unless {@code target} is {@code block}.
     */
    @ZenCodeType.Getter("x")
    public int getX() {
        return use.target() instanceof BlockTarget block
            ? block.position().getX()
            : 0;
    }

    @ZenCodeType.Getter("y")
    public int getY() {
        return use.target() instanceof BlockTarget block
            ? block.position().getY()
            : 0;
    }

    @ZenCodeType.Getter("z")
    public int getZ() {
        return use.target() instanceof BlockTarget block
            ? block.position().getZ()
            : 0;
    }

    /** The clicked face; empty unless {@code target} is {@code block}. */
    @ZenCodeType.Getter("face")
    public String getFace() {
        return use.target() instanceof BlockTarget block
            ? block.face().getSerializedName()
            : "";
    }
}
