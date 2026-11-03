package zone.moddev.mc.lib.machine.feature;

import com.mojang.serialization.Codec;

/**
 * A piece of machine behaviour that ticks and keeps state, such as a {@link RecipeProcessor}. A
 * {@link FeatureHost} runs a machine's features and saves each one under its key.
 *
 * @param <S> the state that is saved
 */
public interface MachineFeature<S> {
  /** The name this feature's state is saved under; unique within a host. */
  String key();

  /** How {@link #state} is saved. */
  Codec<S> codec();

  /** The state to save. */
  S state();

  /** Takes on loaded state. */
  void restore(S state);

  /** Runs once a tick on the server. */
  default void tick(FeatureHost host) {}
}
