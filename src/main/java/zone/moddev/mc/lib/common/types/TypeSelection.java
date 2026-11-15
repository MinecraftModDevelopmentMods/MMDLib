package zone.moddev.mc.lib.common.types;

import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.resources.ResourceLocation;

// import java.util.stream.Collectors;

/**
 * One section's selection split into bundled parts and code-registered type
 * IDs. {@code all} selects every code-registered type and every bundled part
 * that {@link MaterialPart#inAll() all selects}; {@code !all} excludes every
 * one.
 */
public record TypeSelection(Set<MaterialPart> standard,
    Set<ResourceLocation> custom) {
    /** Copies both sets. */
    public TypeSelection {
        standard = Set.copyOf(standard);
        custom = Set.copyOf(custom);
    }

    /**
     * Parses a selection expression (see {@link PartSelection}) over both
     * bundled parts and code-registered type IDs.
     *
     * @throws IllegalArgumentException for unknown or invalid names
     */
    public static TypeSelection parse(String expression,
        Set<MaterialPart> standard, Set<ResourceLocation> custom) {
        var included = new java.util.HashSet<ResourceLocation>();
        var excluded = new java.util.HashSet<ResourceLocation>();
        var every = new java.util.HashSet<ResourceLocation>(custom);
        standard.forEach(part -> every.add(part.templateId()));
        var all = new java.util.HashSet<ResourceLocation>(custom);
        PartSelection.inAll(standard)
            .forEach(part -> all.add(part.templateId()));
        if (!expression.isBlank())
            for (String token : expression.strip().split("\\s+")) {
                boolean negated = token.startsWith("!");
                String name = negated ? token.substring(1) : token;
                var target = negated ? excluded : included;
                if (name.equals("all")) {
                    target.addAll(negated ? every : all);
                    continue;
                }
                ResourceLocation id;
                var resolved = new PartRegistry(standard).resolve(name)
                    .result();
                if (resolved.isPresent())
                    id = resolved.get().templateId();
                else {
                    id = ResourceLocation
                        .tryParse(name.contains(":") ? name : "mmdlib:" + name);
                    if (id == null)
                        throw new IllegalArgumentException(
                            "Invalid type ID: " + name);
                }
                if (!every.contains(id))
                    throw new IllegalArgumentException(
                        "Unknown type or wrong section: " + name);
                target.add(id);
            }
        included.removeAll(excluded);
        var builtins = new java.util.HashSet<MaterialPart>();
        for (var part : standard)
            if (included.remove(part.templateId()))
                builtins.add(part);
        return new TypeSelection(builtins, included);
    }

    /** A codec for one section over bundled parts and code-registered types. */
    public static Codec<TypeSelection> codec(Set<MaterialPart> standard,
        Set<ResourceLocation> custom) {
        Codec<String> expressions = Codec.withAlternative(Codec.STRING,
            Codec.STRING.listOf(), values -> String.join(" ", values));
        return expressions.flatXmap(value -> {
            try {
                return DataResult.success(parse(value, standard, custom));
            } catch (IllegalArgumentException ex) {
                return DataResult.error(ex::getMessage);
            }
        }, value -> {
            if (!standard.containsAll(value.standard)
                || !custom.containsAll(value.custom))
                return DataResult
                    .error(() -> "Selection references unknown types");
            var all = PartSelection.inAll(standard);
            var whole = value.custom.equals(custom)
                && value.standard.containsAll(all)
                && !(all.isEmpty() && custom.isEmpty());
            var names = new java.util.TreeSet<String>();
            value.standard.stream().filter(p -> !whole || !all.contains(p))
                .forEach(part -> names.add(part.id()));
            if (!whole)
                value.custom.forEach(id -> names.add(id.toString()));
            var listed = String.join(" ", names);
            return DataResult.success(
                !whole ? listed : listed.isEmpty() ? "all" : "all " + listed);
        });
    }
}
