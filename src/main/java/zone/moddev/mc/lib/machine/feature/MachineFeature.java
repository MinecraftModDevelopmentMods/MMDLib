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

  /** Releases transient ownership before replacing storage contents or destroying the host. */
  default void reset() {}

  /**
   * Whether nearby clients should hold this feature's state too, so a renderer or tooltip can read
   * it. The block entity sends it with {@link FeatureHost#saveForClient}.
   */
  default boolean syncsToClient() {
    return false;
  }

  /**
   * The state to send to clients; by default what {@link #state} saves. A feature may differ for a
   * moment that clients should not see, as long as {@link #state} stays what is right to save.
   */
  default S clientState() {
    return state();
  }

  /**
   * Takes on state sent to a client. It never saves, so it takes on only what the client can use;
   * by default that is all of it, as {@link #restore}.
   */
  default void restoreOnClient(S state) {
    restore(state);
  }

  /** Runs once a tick on the server. */
  default void tick(FeatureHost host) {}

  /**
   * Called instead of tick when the host holds processing (for example, redstone or reserve gates).
   */
  default void suspended(FeatureHost host) {}
}
