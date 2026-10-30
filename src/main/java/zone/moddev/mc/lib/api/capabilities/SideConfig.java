package zone.moddev.mc.lib.api.capabilities;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The per-face {@link IoMode}s of one resource on one machine, stored relative to its front so they
 * follow the machine when it is rotated. Changing a mode never renumbers slots or tanks: every face
 * sees the same storage, and the mode only allows or refuses operations.
 */
public final class SideConfig {
  /**
   * A map from {@link RelativeFace} to {@link IoMode}; faces left out are {@link IoMode#DISABLED}.
   */
  public static final Codec<SideConfig> CODEC =
      Codec.unboundedMap(RelativeFace.CODEC, IoMode.CODEC)
          .xmap(SideConfig::new, config -> config.modes);

  private final EnumMap<RelativeFace, IoMode> modes = new EnumMap<>(RelativeFace.class);
  private Runnable onChange = () -> {};

  /** Every face set to {@code mode}. */
  public SideConfig(IoMode mode) {
    for (RelativeFace face : RelativeFace.values()) modes.put(face, mode);
  }

  /** The given faces; any face left out is {@link IoMode#DISABLED}. */
  public SideConfig(Map<RelativeFace, IoMode> modes) {
    this(IoMode.DISABLED);
    this.modes.putAll(modes);
  }

  /** Runs after every change, typically the block entity's {@code setChanged}. */
  public SideConfig onChange(Runnable onChange) {
    this.onChange = onChange;
    return this;
  }

  /** The mode of {@code face}. */
  public IoMode get(RelativeFace face) {
    return modes.get(face);
  }

  /** Sets the mode of {@code face}, running the change callback if it changed. */
  public SideConfig set(RelativeFace face, IoMode mode) {
    if (modes.put(face, mode) != mode) onChange.run();
    return this;
  }

  /** Advances one face to its next mode, as a side-configuration button does. */
  public IoMode cycle(RelativeFace face) {
    IoMode next = get(face).next();
    set(face, next);
    return next;
  }

  /** The mode on world side {@code side}; a null side (internal access) is {@link IoMode#BOTH}. */
  public IoMode mode(Direction facing, Direction side) {
    return side == null ? IoMode.BOTH : get(RelativeFace.of(facing, side));
  }

  /** As {@link #mode(Direction, Direction)}, taking the front from the block state. */
  public IoMode mode(BlockState state, Direction side) {
    return mode(RelativeFace.facing(state), side);
  }

  /** Replaces every mode with {@code other}'s, e.g. when loading; does not run the callback. */
  public void copyFrom(SideConfig other) {
    modes.putAll(other.modes);
  }
}
