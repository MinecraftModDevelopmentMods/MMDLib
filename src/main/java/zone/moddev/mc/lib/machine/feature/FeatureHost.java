package zone.moddev.mc.lib.machine.feature;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;

import net.minecraft.world.level.Level;

/**
 * Runs and saves a machine's {@link MachineFeature}s. The block entity ticks it and saves it with
 * its own data; a feature calls {@link #markChanged} when its state changes.
 */
public final class FeatureHost {
  private static final Logger LOGGER = LogUtils.getLogger();

  /** The fewest ticks between two sends to clients. */
  public static final int SYNC_INTERVAL = 10;

  private final Runnable onChange;
  private final Runnable onSync;
  private final Map<String, MachineFeature<?>> features = new LinkedHashMap<>();
  @Nullable private Level level;
  private boolean syncs;
  private boolean syncPending;
  private int sinceSync = SYNC_INTERVAL;

  /** A host that calls {@code onChange} when a feature says its state changed; it never sends. */
  public FeatureHost(Runnable onChange) {
    this(onChange, () -> {});
  }

  /**
   * A host that also calls {@code onSync} to send the state of the features that sync, at most once
   * per {@link #SYNC_INTERVAL} ticks.
   */
  public FeatureHost(Runnable onChange, Runnable onSync) {
    this.onChange = onChange;
    this.onSync = onSync;
  }

  /**
   * Adds {@code feature}; features tick in the order added.
   *
   * @throws IllegalArgumentException if another feature has the same key
   */
  public FeatureHost add(MachineFeature<?> feature) {
    if (features.putIfAbsent(feature.key(), feature) != null)
      throw new IllegalArgumentException("Duplicate machine feature " + feature.key());
    syncs |= feature.syncsToClient();
    return this;
  }

  /**
   * Ticks every feature, then sends if a feature marked a sync and {@link #SYNC_INTERVAL} ticks
   * have passed since the last send. Does nothing on the client.
   */
  public void tick(Level level) {
    if (level.isClientSide()) return;
    this.level = level;
    for (var feature : features.values()) feature.tick(this);
    if (sinceSync < SYNC_INTERVAL) sinceSync++;
    if (syncPending && sinceSync >= SYNC_INTERVAL) {
      syncPending = false;
      sinceSync = 0;
      onSync.run();
    }
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

  /**
   * Asks for the state of the features that sync to be sent on the next tick that may. Does nothing
   * if no feature syncs.
   */
  public void markSync() {
    if (syncs) syncPending = true;
  }

  /** Every feature's state, as a map from key to the state its codec writes. */
  public <T> T save(DynamicOps<T> ops) {
    var map = new LinkedHashMap<T, T>();
    for (var feature : features.values()) {
      var encoded = encode(ops, feature, false);
      if (encoded != null) map.put(ops.createString(feature.key()), encoded);
    }
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

  /** The {@link MachineFeature#clientState} of the features that sync, written as {@link #save}. */
  public <T> T saveForClient(DynamicOps<T> ops) {
    var map = new LinkedHashMap<T, T>();
    for (var feature : features.values()) {
      if (!feature.syncsToClient()) continue;
      var encoded = encode(ops, feature, true);
      if (encoded != null) map.put(ops.createString(feature.key()), encoded);
    }
    return ops.createMap(map);
  }

  /**
   * Gives each feature that syncs the state {@link #saveForClient} wrote, through {@link
   * MachineFeature#restoreOnClient}. As in {@link #load}, a feature with no data or bad data keeps
   * its state.
   */
  public <T> void loadOnClient(DynamicOps<T> ops, T data) {
    var map = ops.getMap(data).result().orElse(null);
    if (map == null) return;
    for (var feature : features.values()) {
      var saved = feature.syncsToClient() ? map.get(feature.key()) : null;
      if (saved != null) decodeOnClient(ops, feature, saved);
    }
  }

  @Nullable
  private static <T, S> T encode(DynamicOps<T> ops, MachineFeature<S> feature, boolean client) {
    var result = feature.codec().encodeStart(ops, client ? feature.clientState() : feature.state());
    if (result.error().isPresent())
      LOGGER.error(
          "Machine feature {} could not be saved: {}",
          feature.key(),
          result.error().get().message());
    return result.result().orElse(null);
  }

  private static <T, S> void decode(DynamicOps<T> ops, MachineFeature<S> feature, T saved) {
    var result = feature.codec().parse(ops, saved);
    result.result().ifPresent(feature::restore);
    result.error().ifPresent(e -> logBad(feature, e.message()));
  }

  private static <T, S> void decodeOnClient(
      DynamicOps<T> ops, MachineFeature<S> feature, T saved) {
    var result = feature.codec().parse(ops, saved);
    result.result().ifPresent(feature::restoreOnClient);
    result.error().ifPresent(e -> logBad(feature, e.message()));
  }

  private static void logBad(MachineFeature<?> feature, String message) {
    LOGGER.error(
        "Machine feature {} has bad saved data, keeping its state: {}", feature.key(), message);
  }
}
