package zone.moddev.mc.lib.api.capabilities;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A face named relative to a machine's front. Left and right are as seen by a player looking at the
 * front. For a machine facing up or down, the top is the north side.
 */
public enum RelativeFace implements StringRepresentable {
  /** The side the machine faces. */
  FRONT,
  /** The side opposite the front. */
  BACK,
  /** The left side, as seen looking at the front. */
  LEFT,
  /** The right side, as seen looking at the front. */
  RIGHT,
  /** The top; north for a machine facing up or down. */
  TOP,
  /** The bottom; south for a machine facing up or down. */
  BOTTOM;

  /** Reads and writes the lower-case name, e.g. {@code "front"}. */
  public static final Codec<RelativeFace> CODEC =
      StringRepresentable.fromEnum(RelativeFace::values);

  /** The world side this face is on for a machine whose front points {@code facing}. */
  public Direction toWorld(Direction facing) {
    if (facing.getAxis().isVertical()) {
      // Seen from the front: north is up; looking down (facing up) east is on the right.
      Direction left = facing == Direction.UP ? Direction.WEST : Direction.EAST;
      return switch (this) {
        case FRONT -> facing;
        case BACK -> facing.getOpposite();
        case LEFT -> left;
        case RIGHT -> left.getOpposite();
        case TOP -> Direction.NORTH;
        case BOTTOM -> Direction.SOUTH;
      };
    }
    return switch (this) {
      case FRONT -> facing;
      case BACK -> facing.getOpposite();
      case LEFT -> facing.getClockWise();
      case RIGHT -> facing.getCounterClockWise();
      case TOP -> Direction.UP;
      case BOTTOM -> Direction.DOWN;
    };
  }

  /** The face on world side {@code side} of a machine whose front points {@code facing}. */
  public static RelativeFace of(Direction facing, Direction side) {
    for (RelativeFace face : values()) if (face.toWorld(facing) == side) return face;
    throw new IllegalStateException(
        "No face for " + side); // unreachable: the mapping is a bijection
  }

  /**
   * The front of a block state: its {@code facing} or {@code horizontal_facing} property, or north
   * when it has neither.
   */
  public static Direction facing(BlockState state) {
    if (state.hasProperty(BlockStateProperties.FACING))
      return state.getValue(BlockStateProperties.FACING);
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
      return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
    return Direction.NORTH;
  }

  @Override
  public String getSerializedName() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
