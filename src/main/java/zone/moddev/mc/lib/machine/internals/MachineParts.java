package zone.moddev.mc.lib.machine.internals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.google.gson.JsonElement;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;

import zone.moddev.mc.lib.machine.stat.StatModifier;

/**
 * Every loaded machine part file, and what each item is as a part. Built from the datapack on each
 * reload and {@linkplain #install installed}; machines read {@link #current()}.
 *
 * <p>An {@code item} file beats a {@code part} template for that item. When two files claim the
 * same item or part, the lower resource ID wins and an error names both. A file that doesn't decode
 * is logged and skipped. An item whose amounts can't be worked out (no material for {@code
 * $material.*}, a result that isn't finite, a durability below 1) is not a part, logged once.
 */
public final class MachineParts {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final AtomicInteger GENERATIONS = new AtomicInteger();

  /** No part files. */
  public static final MachineParts NONE =
      new MachineParts(Map.of(), Map.of(), item -> Optional.empty());

  private static volatile MachineParts current = NONE;

  private final Map<ResourceLocation, Located> byItem;
  private final Map<ResourceLocation, Located> byPart;
  private final Function<ResourceLocation, Optional<PartSubject>> subjects;
  private final Map<ResourceLocation, Optional<ResolvedPart>> resolved = new ConcurrentHashMap<>();
  private volatile int generation;

  private record Located(ResourceLocation file, PartDefinition definition) {}

  private MachineParts(
      Map<ResourceLocation, Located> byItem,
      Map<ResourceLocation, Located> byPart,
      Function<ResourceLocation, Optional<PartSubject>> subjects) {
    this.byItem = byItem;
    this.byPart = byPart;
    this.subjects = subjects;
  }

  /**
   * Decodes part files, by resource ID.
   *
   * @param subjects what each item is; an item it doesn't know is treated as having no part and no
   *     material
   */
  public static MachineParts parse(
      Map<ResourceLocation, JsonElement> files,
      Function<ResourceLocation, Optional<PartSubject>> subjects) {
    var byItem = new HashMap<ResourceLocation, Located>();
    var byPart = new HashMap<ResourceLocation, Located>();
    new TreeMap<>(files)
        .forEach(
            (file, json) ->
                PartDefinition.CODEC
                    .parse(JsonOps.INSTANCE, json)
                    .ifError(
                        error ->
                            LOGGER.error(
                                "Skipping machine part file {}: {}", file, error.message()))
                    .ifSuccess(
                        definition -> {
                          var located = new Located(file, definition);
                          var target = definition.target();
                          var index = target.left().isPresent() ? byPart : byItem;
                          var key = target.map(part -> part, item -> item);
                          var old = index.putIfAbsent(key, located);
                          if (old != null)
                            LOGGER.error(
                                "Machine part files {} and {} both define {}; using {}",
                                old.file(),
                                file,
                                key,
                                old.file());
                        }));
    return new MachineParts(Map.copyOf(byItem), Map.copyOf(byPart), subjects);
  }

  /** The installed part files; {@link #NONE} until the first datapack load. */
  public static MachineParts current() {
    return current;
  }

  /** Makes {@code parts} current, with a new {@link #generation()}. */
  public static void install(MachineParts parts) {
    parts.generation = GENERATIONS.incrementAndGet();
    current = parts;
  }

  /** Changes on every {@link #install}, so machines know to recompute their stats. */
  public int generation() {
    return generation;
  }

  /** What {@code item} is as a part, or empty if it isn't one. */
  public Optional<ResolvedPart> resolve(ResourceLocation item) {
    return resolved.computeIfAbsent(item, this::compute);
  }

  private Optional<ResolvedPart> compute(ResourceLocation item) {
    var subject =
        subjects.apply(item).orElse(new PartSubject(item, Optional.empty(), Optional.empty()));
    var located = byItem.get(item);
    if (located == null && subject.part().isPresent()) located = byPart.get(subject.part().get());
    if (located == null) return Optional.empty();
    var definition = located.definition();
    Function<String, OptionalDouble> vars = name -> variable(subject, name);
    try {
      var modifiers = new ArrayList<StatModifier>();
      for (var modifier : definition.modifiers())
        modifiers.add(
            new StatModifier(modifier.stat(), modifier.op(), modifier.amount().eval(vars)));
      var durability = OptionalInt.empty();
      if (definition.durability().isPresent()) {
        double points = definition.durability().get().eval(vars);
        if (!Double.isFinite(points) || points < 1)
          throw new IllegalArgumentException("durability " + points + " is below 1");
        durability = OptionalInt.of((int) Math.min(Integer.MAX_VALUE, Math.floor(points)));
      }
      return Optional.of(new ResolvedPart(definition.socket(), modifiers, durability));
    } catch (IllegalArgumentException ex) {
      LOGGER.error(
          "Machine part file {} can't apply to {}: {}", located.file(), item, ex.getMessage());
      return Optional.empty();
    }
  }

  private static OptionalDouble variable(PartSubject subject, String name) {
    if (subject.material().isEmpty()) return OptionalDouble.empty();
    var material = subject.material().get();
    return switch (name) {
      case "material.hardness" -> OptionalDouble.of(material.hardness());
      case "material.strength" -> OptionalDouble.of(material.strength());
      case "material.magic" -> OptionalDouble.of(material.magic());
      case "material.damage" -> OptionalDouble.of(material.damage());
      default ->
          name.startsWith("material.special.")
              ? OptionalDouble.of(
                  material.special().getOrDefault(name.substring("material.special.".length()), 0D))
              : OptionalDouble.empty();
    };
  }
}
