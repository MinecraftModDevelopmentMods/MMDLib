package zone.moddev.mc.lib.common.types;

import java.util.Set;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import zone.moddev.mc.lib.common.modules.ModuleCatalog;

/**
 * Parses selection expressions such as {@code "all !scythe !mmdlib:horse_armor"}: space-separated
 * names or aliases, {@code all} for every part in the section, and {@code !} to exclude. Exclusions
 * win whatever their order. JSON may also give the tokens as a list.
 */
public final class PartSelection {
  private PartSelection() {}

  /** Every bundled item part. */
  public static final Set<MaterialPart> ITEMS = scope(true, false);

  /** Every bundled block part. */
  public static final Set<MaterialPart> BLOCKS = scope(false, false);

  /** Every bundled part that needs a world entity. */
  public static final Set<MaterialPart> ENTITIES = scope(true, true);

  private static Set<MaterialPart> scope(boolean items, boolean entities) {
    var result = new java.util.HashSet<MaterialPart>();
    for (var part : ModuleCatalog.bundled().parts().entries().values()) {
      if (entities ? part.needsEntity() : (part.kind() == MaterialPart.Kind.ITEM) == items)
        result.add(part);
    }
    return Set.copyOf(result);
  }

  /**
   * Parses an expression against the parts {@code available} to one section.
   *
   * @throws IllegalArgumentException for unknown names or names from another section
   */
  public static Set<MaterialPart> parse(String expression, Set<MaterialPart> available) {
    var included = new java.util.HashSet<MaterialPart>();
    var excluded = new java.util.HashSet<MaterialPart>();
    if (expression.isBlank()) return Set.of();
    for (String token : expression.strip().split("\\s+")) {
      boolean negate = token.startsWith("!");
      String name = negate ? token.substring(1) : token;
      var target = negate ? excluded : included;
      if (name.equals("all")) target.addAll(available);
      else {
        MaterialPart part =
            new PartRegistry(available)
                .resolve(name)
                .getOrThrow(message -> new IllegalArgumentException(message));
        if (!available.contains(part))
          throw new IllegalArgumentException("Part '" + name + "' does not belong to this section");
        target.add(part);
      }
    }
    included.removeAll(excluded);
    return Set.copyOf(included);
  }

  /**
   * A codec for one section, reading a string or list and writing {@code all} or a sorted list of
   * short IDs.
   */
  public static Codec<Set<MaterialPart>> codec(Set<MaterialPart> available) {
    Codec<String> expression =
        Codec.withAlternative(
            Codec.STRING, Codec.STRING.listOf(), tokens -> String.join(" ", tokens));
    return expression.flatXmap(
        value -> {
          try {
            return DataResult.success(parse(value, available));
          } catch (IllegalArgumentException ex) {
            return DataResult.error(ex::getMessage);
          }
        },
        values -> {
          if (!available.containsAll(values))
            return DataResult.error(() -> "Selection contains a part from another section");
          return DataResult.success(
              values.equals(available)
                  ? "all"
                  : values.stream()
                      .sorted()
                      .map(MaterialPart::id)
                      .collect(Collectors.joining(" ")));
        });
  }
}
