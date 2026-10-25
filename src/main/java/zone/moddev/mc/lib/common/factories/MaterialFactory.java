package zone.moddev.mc.lib.common.factories;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import zone.moddev.mc.lib.common.builders.*;
import zone.moddev.mc.lib.common.codecs.MMDMaterialCodec;
import zone.moddev.mc.lib.common.types.*;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Decode material data, bind its identity, then apply it to immutable part templates. */
public final class MaterialFactory {
    public record Result(MMDMaterial material, MaterialContentPlan content) {}
    private final zone.moddev.mc.lib.api.MaterialExtensions extensions;
    private final Predicate<String> installed;
    private final UnaryOperator<MaterialContentPlanner> customize;
    public MaterialFactory() { this(id -> ModList.get().isLoaded(id)); }
    public MaterialFactory(Predicate<String> installed) { this(zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL, installed); }
    public MaterialFactory(zone.moddev.mc.lib.api.MaterialExtensions extensions, Predicate<String> installed) {
        this(extensions, installed, UnaryOperator.identity());
    }
    private MaterialFactory(zone.moddev.mc.lib.api.MaterialExtensions extensions, Predicate<String> installed, UnaryOperator<MaterialContentPlanner> customize) {
        this.extensions = Objects.requireNonNull(extensions);
        this.installed = Objects.requireNonNull(installed); this.customize = customize;
    }
    /** Custom templates override the material defaults without mutating this factory. */
    public MaterialFactory withTemplates(UnaryOperator<MaterialContentPlanner> overrides) {
        Objects.requireNonNull(overrides);
        return new MaterialFactory(extensions, installed, planner -> overrides.apply(customize.apply(planner)));
    }
    public MMDMaterial decode(ResourceLocation id, Reader json) {
        var spec = MMDMaterialCodec.forExtensions(extensions).parse(JsonOps.INSTANCE, JsonParser.parseReader(json))
                .getOrThrow(message -> new IllegalArgumentException("Material " + id + ": " + message));
        if (spec.name().isPresent() && !spec.name().get().equals(id))
            throw new IllegalArgumentException("Material name disagrees with resource ID: " + id);
        return MaterialBuilder.from(spec).setName(id).build();
    }
    public Result load(ResourceLocation id, Reader json) { return create(decode(id, json)); }
    public Result load(ResourceLocation id, Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { return load(id, reader); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(file + ": " + ex.getMessage(), ex); }
    }
    public Result create(MMDMaterial material) {
        var id = material.name().orElseThrow(() -> new IllegalArgumentException("Material factory requires a named material"));
        try {
            extensions.events().validate(material.events());
            var planner = Objects.requireNonNull(customize.apply(extensions.planner(material, installed)), "custom planner");
            return new Result(material, extensions.extend(material, planner.plan(id, extensions.selection(material), installed, part -> extensions.available(part, installed)), installed));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Material " + id + ": " + ex.getMessage(), ex);
        }
    }
}
