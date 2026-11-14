package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker_annotations.annotations.Document;

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
 *
 * @docParam this context
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/ItemClickContext")
@ZenCodeType.Name("mods.mmdlib.ItemClickContext")
public final class ScriptItemClickContext {
    private final ToolUse use;

    ScriptItemClickContext(ToolUse use) {
        this.use = use;
    }

    /**
     * The player who clicked.
     *
     * @return the player
     */
    @ZenCodeType.Getter("player")
    public Player getPlayer() {
        return use.player();
    }

    /**
     * Whether this runs on the client; change the world only on the server.
     *
     * @return true on the client
     */
    @ZenCodeType.Getter("client")
    public boolean isClient() {
        return use.player().level().isClientSide();
    }

    /**
     * The item clicked with.
     *
     * @return the item
     */
    @ZenCodeType.Getter("stack")
    public IItemStack getStack() {
        return IItemStack.of(use.target().stack());
    }

    /**
     * What was clicked: {@code air}, {@code block} or {@code entity}.
     *
     * @return "air", "block" or "entity"
     */
    @ZenCodeType.Getter("target")
    public String getTarget() {
        return switch (use.target()) {
            case BlockTarget block -> "block";
            case EntityTarget entity -> "entity";
            case AirTarget air -> "air";
        };
    }

    /**
     * The clicked entity; null unless {@code target} is {@code entity}.
     *
     * @return the entity, or null
     */
    @ZenCodeType.Nullable
    @ZenCodeType.Getter("entity")
    public Entity getEntity() {
        return use.target() instanceof EntityTarget target
            ? target.entity()
            : null;
    }

    /**
     * The clicked block's x coordinate; 0 unless {@code target} is {@code
     * block}.
     *
     * @return x
     */
    @ZenCodeType.Getter("x")
    public int getX() {
        return use.target() instanceof BlockTarget block
            ? block.position().getX()
            : 0;
    }

    /**
     * The clicked block's y coordinate; 0 unless {@code target} is {@code
     * block}.
     *
     * @return y
     */
    @ZenCodeType.Getter("y")
    public int getY() {
        return use.target() instanceof BlockTarget block
            ? block.position().getY()
            : 0;
    }

    /**
     * The clicked block's z coordinate; 0 unless {@code target} is {@code
     * block}.
     *
     * @return z
     */
    @ZenCodeType.Getter("z")
    public int getZ() {
        return use.target() instanceof BlockTarget block
            ? block.position().getZ()
            : 0;
    }

    /**
     * The clicked face; empty unless {@code target} is {@code block}.
     *
     * @return the face, or an empty string
     */
    @ZenCodeType.Getter("face")
    public String getFace() {
        return use.target() instanceof BlockTarget block
            ? block.face().getSerializedName()
            : "";
    }
}
