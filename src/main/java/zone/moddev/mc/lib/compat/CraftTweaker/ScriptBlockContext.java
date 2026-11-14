package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Locale;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.runtime.RedstoneBlock;

/**
 * The block a script handler runs for: where it is, the power reaching each
 * side, and the power it emits. Sides are {@code up}, {@code down},
 * {@code north}, {@code south}, {@code east} and {@code
 * west}, and for blocks that face a way also {@code front}, {@code back},
 * {@code left} and {@code
 * right}.
 *
 * @docParam this context
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/BlockContext")
@ZenCodeType.Name("mods.mmdlib.BlockContext")
public abstract class ScriptBlockContext {
    private final Level level;
    private final BlockPos pos;

    ScriptBlockContext(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    /**
     * Whether this runs on the client; outputs can only change on the server.
     *
     * @return true on the client
     */
    @ZenCodeType.Getter("client")
    public boolean isClient() {
        return level.isClientSide();
    }

    /**
     * The block's x coordinate.
     *
     * @return x
     */
    @ZenCodeType.Getter("x")
    public int getX() {
        return pos.getX();
    }

    /**
     * The block's y coordinate.
     *
     * @return y
     */
    @ZenCodeType.Getter("y")
    public int getY() {
        return pos.getY();
    }

    /**
     * The block's z coordinate.
     *
     * @return z
     */
    @ZenCodeType.Getter("z")
    public int getZ() {
        return pos.getZ();
    }

    /**
     * The way the block faces, such as {@code north}; empty for blocks that do
     * not face a way.
     *
     * @return the facing, or an empty string
     */
    @ZenCodeType.Getter("facing")
    public String getFacing() {
        var state = level.getBlockState(pos);
        return state.hasProperty(RedstoneBlock.FACING)
            ? state.getValue(RedstoneBlock.FACING).getSerializedName()
            : "";
    }

    /**
     * The power, 0 to 15, reaching one side of the block now. Naming
     * {@code front}, {@code back}, {@code left} or {@code right} on a block
     * that does not face a way is an error.
     *
     * @param side a world side, such as {@code "north"}, or one relative to the
     *            block's facing, such as {@code "front"}
     * @return the power, 0 to 15
     * @docParam side "north"
     */
    @ZenCodeType.Method
    public int input(String side) {
        return RedstoneBlock.signal(level, pos, direction(side));
    }

    /**
     * The power the block emits, 0 to 15.
     *
     * @return the power
     */
    @ZenCodeType.Getter("output")
    public int getOutput() {
        var state = level.getBlockState(pos);
        if (state.hasProperty(RedstoneBlock.OUTPUT))
            return state.getValue(RedstoneBlock.OUTPUT);
        return state.getBlock() instanceof RedstoneBlock block
            ? block.settings().constantOutput().orElse(0)
            : 0;
    }

    /**
     * Sets the power the block emits, clamped to 0 to 15. Ignored on the
     * client; the block needs {@code redstoneOutput = true}.
     *
     * @param power the power, 0 to 15
     * @docParam power 15
     */
    @ZenCodeType.Setter("output")
    public void setOutput(int power) {
        if (!level.isClientSide())
            RedstoneBlock.setOutput(level, pos, power);
    }

    /** {@code side} as a world direction. */
    Direction direction(String side) {
        var name = side.toLowerCase(Locale.ROOT);
        var state = level.getBlockState(pos);
        Direction local = switch (name) {
            case "front" -> Direction.NORTH;
            case "back" -> Direction.SOUTH;
            case "left" -> Direction.WEST;
            case "right" -> Direction.EAST;
            default -> null;
        };
        if (local == null) {
            var world = Direction.byName(name);
            if (world == null)
                throw new IllegalArgumentException("Unknown side: " + side);
            return world;
        }
        if (!state.hasProperty(RedstoneBlock.FACING))
            throw new IllegalArgumentException("Side '" + side
                + "' needs a block that faces a way (directional = true)");
        return RedstoneBlock.rotate(local,
            state.getValue(RedstoneBlock.FACING));
    }
}
