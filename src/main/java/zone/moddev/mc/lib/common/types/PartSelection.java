package zone.moddev.mc.lib.common.types;

import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Set;
import java.util.stream.Collectors;

/** Includes/excludes for one section; exclusions take precedence regardless of order. */
public final class PartSelection {
    private PartSelection() {}
    public static final Set<MaterialPart> ITEMS = scope(true, false);
    public static final Set<MaterialPart> BLOCKS = scope(false, false);
    public static final Set<MaterialPart> ENTITIES = scope(true, true);
    private static Set<MaterialPart> scope(boolean items, boolean entities) {
        var result = new java.util.HashSet<MaterialPart>();
        for (var part : ModuleCatalog.bundled().parts().entries().values()) {
            if (entities ? part.needsEntity() : (part.kind() == MaterialPart.Kind.ITEM) == items) result.add(part);
        }
        return Set.copyOf(result);
    }
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
                MaterialPart part = new PartRegistry(available).resolve(name).getOrThrow(message -> new IllegalArgumentException(message));
                if (!available.contains(part)) throw new IllegalArgumentException("Part '" + name + "' does not belong to this section");
                target.add(part);
            }
        }
        included.removeAll(excluded);
        return Set.copyOf(included);
    }
    public static Codec<Set<MaterialPart>> codec(Set<MaterialPart> available) {
        Codec<String> expression = Codec.withAlternative(Codec.STRING, Codec.STRING.listOf(), tokens -> String.join(" ", tokens));
        return expression.flatXmap(value -> {
            try { return DataResult.success(parse(value, available)); }
            catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
        }, values -> {
            if (!available.containsAll(values)) return DataResult.error(() -> "Selection contains a part from another section");
            return DataResult.success(values.equals(available) ? "all" : values.stream().sorted()
                    .map(MaterialPart::id).collect(Collectors.joining(" ")));
        });
    }
}
