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
    private final Predicate<String> installed;
    private final UnaryOperator<MaterialContentPlanner> customize;
    public MaterialFactory() { this(id -> ModList.get().isLoaded(id)); }
    public MaterialFactory(Predicate<String> installed) { this(installed, UnaryOperator.identity()); }
    private MaterialFactory(Predicate<String> installed, UnaryOperator<MaterialContentPlanner> customize) {
        this.installed = Objects.requireNonNull(installed); this.customize = customize;
    }
    /** Custom templates override the material defaults without mutating this factory. */
    public MaterialFactory withTemplates(UnaryOperator<MaterialContentPlanner> overrides) {
        Objects.requireNonNull(overrides);
        return new MaterialFactory(installed, planner -> overrides.apply(customize.apply(planner)));
    }
    public MMDMaterial decode(ResourceLocation id, Reader json) {
        var spec = MMDMaterialCodec.MMD_MATERIAL_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(json))
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
            var p = material.properties();
            var planner = new MaterialContentPlanner();
            float hardness = (float) p.hardness();
            float resistance = (float) p.special().getOrDefault("blast_resistance", 2 * p.hardness()).doubleValue();
            for (MaterialPart part : material.parts().parts()) {
                if (part.kind() != MaterialPart.Kind.ITEM) planner = planner.withBlockTemplate(part,
                        BlockBuilder.create().setType(part.id()).setStrength(hardness, resistance)
                                .setBlockTags(p.harvestTags()).setRequiresCorrectToolForDrops(!p.harvestTags().isEmpty())
                                .setHasItem(part != MaterialPart.DOUBLE_SLAB));
            }
            if (material.parts().hasFluid()) {
                var fluid = FluidBuilder.create().setName("mmdlib:molten").setTintColor(p.color());
                if (p.special().containsKey("temperature")) fluid = fluid.setTemperature(integer(p, "temperature"));
                if (p.special().containsKey("density")) fluid = fluid.setDensity(integer(p, "density"));
                if (p.special().containsKey("viscosity")) fluid = fluid.setViscosity(integer(p, "viscosity"));
                planner = planner.withFluidTemplate(fluid);
            }
            planner = Objects.requireNonNull(customize.apply(planner), "custom planner");
            return new Result(material, planner.plan(id, material.parts(), installed));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Material " + id + ": " + ex.getMessage(), ex);
        }
    }
    private static int integer(MaterialProperties properties, String key) {
        double value = properties.special().get(key);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Property '" + key + "' must be an integer");
        return (int) value;
    }
}
