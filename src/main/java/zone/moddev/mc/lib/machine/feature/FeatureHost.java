package zone.moddev.mc.lib.machine.feature;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.DynamicOps;

import net.minecraft.world.level.Level;

/**
 * Runs and saves a machine's {@link MachineFeature}s. The block entity ticks it and saves it with
 * its own data; a feature calls {@link #markChanged} when its state changes.
 */
public final class FeatureHost {
  private final Runnable onChange;
  private final Map<String, MachineFeature<?>> features = new LinkedHashMap<>();
  @Nullable private Level level;

  /** A host that calls {@code onChange} when a feature says its state changed. */
  public FeatureHost(Runnable onChange) {
    this.onChange = onChange;
  }

  /**
   * Adds {@code feature}; features tick in the order added.
   *
   * @throws IllegalArgumentException if another feature has the same key
   */
  public FeatureHost add(MachineFeature<?> feature) {
    if (features.putIfAbsent(feature.key(), feature) != null)
      throw new IllegalArgumentException("Duplicate machine feature " + feature.key());
    return this;
  }

  /** Ticks every feature. Does nothing on the client. */
  public void tick(Level level) {
    if (level.isClientSide()) return;
    this.level = level;
    for (var feature : features.values()) feature.tick(this);
  }

  /** The level being ticked; only for use while a feature ticks. */
  public Level level() {
    if (level == null) throw new IllegalStateException("Not ticking");
    return level;
  }

  /** Tells the block entity that something worth saving changed. */
  public void markChanged() {
    onChange.run();
  }

  /** Every feature's state, as a map from key to the state its codec writes. */
  public <T> T save(DynamicOps<T> ops) {
    var map = new LinkedHashMap<T, T>();
    for (var feature : features.values())
      map.put(ops.createString(feature.key()), encode(ops, feature));
    return ops.createMap(map);
  }

  /**
   * Gives each feature the state saved under its key. A feature with no data, or data its codec
   * rejects, keeps its current state; data for a key no feature has is ignored.
   *
   * <p>Load the storages first: a feature checks the loaded contents against what it saved.
   */
  public <T> void load(DynamicOps<T> ops, T data) {
    var map = ops.getMap(data).result().orElse(null);
    if (map == null) return;
    for (var feature : features.values()) {
      var saved = map.get(feature.key());
      if (saved != null) decode(ops, feature, saved);
    }
  }

  private static <T, S> T encode(DynamicOps<T> ops, MachineFeature<S> feature) {
    return feature.codec().encodeStart(ops, feature.state()).getOrThrow();
  }

  private static <T, S> void decode(DynamicOps<T> ops, MachineFeature<S> feature, T saved) {
    feature.codec().parse(ops, saved).result().ifPresent(feature::restore);
  }
}
