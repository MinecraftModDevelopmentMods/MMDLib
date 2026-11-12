package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.BlockBreak;

/**
 * What {@code onBreakBlock} (items) and {@code onBroken} (blocks) handlers
 * receive: a player about to break a block. Server side.
 *
 * @docParam this context
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/BreakContext")
@ZenCodeType.Name("mods.mmdlib.BreakContext")
public final class ScriptBreakContext {
    private final BlockBreak broken;

    ScriptBreakContext(BlockBreak broken) {
        this.broken = broken;
    }

    /**
     * The player breaking the block.
     *
     * @return the player
     */
    @ZenCodeType.Getter("player")
    public Player getPlayer() {
        return broken.player();
    }

    /**
     * The item in the player's main hand.
     *
     * @return the tool
     */
    @ZenCodeType.Getter("tool")
    public IItemStack getTool() {
        return IItemStack.of(broken.tool());
    }

    /**
     * The block's ID, such as {@code minecraft:stone}.
     *
     * @return the block ID
     */
    @ZenCodeType.Getter("block")
    public String getBlock() {
        return BuiltInRegistries.BLOCK.getKey(broken.state().getBlock())
            .toString();
    }

    /**
     * The block's x coordinate.
     *
     * @return x
     */
    @ZenCodeType.Getter("x")
    public int getX() {
        return broken.position().getX();
    }

    /**
     * The block's y coordinate.
     *
     * @return y
     */
    @ZenCodeType.Getter("y")
    public int getY() {
        return broken.position().getY();
    }

    /**
     * The block's z coordinate.
     *
     * @return z
     */
    @ZenCodeType.Getter("z")
    public int getZ() {
        return broken.position().getZ();
    }

    /**
     * The face being mined, such as {@code up}; empty when it is not known.
     *
     * @return the face, or an empty string
     */
    @ZenCodeType.Getter("face")
    public String getFace() {
        return broken.face() == null ? "" : broken.face().getSerializedName();
    }

    /**
     * Keeps the block: it is not broken and drops nothing.
     */
    @ZenCodeType.Method
    public void cancel() {
        broken.cancel();
    }

    /**
     * Breaks another block as this player would with their tool: drops,
     * experience and tool wear apply. Air, unbreakable blocks and blocks the
     * tool cannot harvest are skipped, and the extra breaks do not run the
     * item's handler again.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return whether the block was broken
     * @docParam x context.x + 1
     * @docParam y context.y
     * @docParam z context.z
     */
    @ZenCodeType.Method
    public boolean mine(int x, int y, int z) {
        return broken.mine(new BlockPos(x, y, z));
    }
}
