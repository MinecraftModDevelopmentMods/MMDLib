package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;
//import java.util.stream.Collectors;

/** One section's resolved standard and extension type identifiers. */
public record TypeSelection(Set<MaterialPart> standard, Set<ResourceLocation> custom) {
    public TypeSelection { standard = Set.copyOf(standard); custom = Set.copyOf(custom); }
    public static TypeSelection parse(String expression, Set<MaterialPart> standard, Set<ResourceLocation> custom) {
        var included = new java.util.HashSet<ResourceLocation>(); var excluded = new java.util.HashSet<ResourceLocation>();
        var all = new java.util.HashSet<ResourceLocation>(custom);
        standard.forEach(part -> all.add(part.templateId()));
        if (!expression.isBlank()) for (String token : expression.strip().split("\\s+")) {
            boolean negated = token.startsWith("!"); String name = negated ? token.substring(1) : token;
            var target = negated ? excluded : included;
            if (name.equals("all")) { target.addAll(all); continue; }
            ResourceLocation id;
            var resolved = new PartRegistry(standard).resolve(name).result();
            if (resolved.isPresent()) id = resolved.get().templateId();
            else {
                id = ResourceLocation.tryParse(name.contains(":") ? name : "mmdlib:" + name);
                if (id == null) throw new IllegalArgumentException("Invalid type ID: " + name);
            }
            if (!all.contains(id)) throw new IllegalArgumentException("Unknown type or wrong section: " + name);
            target.add(id);
        }
        included.removeAll(excluded);
        var builtins = new java.util.HashSet<MaterialPart>();
        for (var part : standard) if (included.remove(part.templateId())) builtins.add(part);
        return new TypeSelection(builtins, included);
    }
    public static Codec<TypeSelection> codec(Set<MaterialPart> standard, Set<ResourceLocation> custom) {
        Codec<String> expressions = Codec.withAlternative(Codec.STRING, Codec.STRING.listOf(), values -> String.join(" ", values));
        return expressions.flatXmap(value -> {
            try { return DataResult.success(parse(value, standard, custom)); }
            catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
        }, value -> {
            if (!standard.containsAll(value.standard) || !custom.containsAll(value.custom))
                return DataResult.error(() -> "Selection references unknown types");
            if (value.standard.equals(standard) && value.custom.equals(custom)) return DataResult.success("all");
            var names = new java.util.TreeSet<String>();
            value.standard.forEach(part -> names.add(part.id())); value.custom.forEach(id -> names.add(id.toString()));
            return DataResult.success(String.join(" ", names));
        });
    }
}
