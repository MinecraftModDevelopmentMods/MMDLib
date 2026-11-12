package zone.moddev.mc.lib.common.types;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.MaterialBuilder;
import zone.moddev.mc.lib.common.codecs.FluidTemplate;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.DataModules;

/**
 * A fluid with its own registry ID and no material, such as a liquid chocolate.
 * It is built from a fluid module, like a material's fluid, with the same
 * family: the fluid type and source fluid {@code <id>}, the flowing fluid
 * {@code flowing_<name>} (as {@code test/flowing_syrup} for
 * {@code test/syrup}), the liquid block {@code <id>} and the bucket
 * {@code <id>_bucket}, each only as the template selects. {@code template}
 * replaces the module's template; the other fields change single values of the
 * module's (or that) template, as a material's color and stats would.
 *
 * @param module
 *            the fluid module that builds the fluid
 * @param template
 *            replaces the module's template when present
 * @param tint
 *            an ARGB tint over the textures, replacing the template's
 * @param temperature
 *            replaces the template's temperature, in kelvin
 * @param density
 *            replaces the template's density
 * @param viscosity
 *            replaces the template's viscosity
 * @param runtime
 *            the settings a material would supply to the liquid block
 */
public record StandaloneFluid(ResourceLocation module,
    Optional<FluidTemplate> template, Optional<Integer> tint,
    Optional<Integer> temperature, Optional<Integer> density,
    Optional<Integer> viscosity, MaterialRuntimeSettings runtime) {
    /**
     * Reads a standalone fluid file; every field but {@code module} is
     * optional.
     */
    public static final Codec<StandaloneFluid> CODEC = RecordCodecBuilder
        .create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("module")
                .forGetter(StandaloneFluid::module),
            FluidTemplate.CODEC.optionalFieldOf("template")
                .forGetter(StandaloneFluid::template),
            Codec.INT.optionalFieldOf("tint").forGetter(StandaloneFluid::tint),
            Codec.INT.optionalFieldOf("temperature")
                .forGetter(StandaloneFluid::temperature),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("density")
                .forGetter(StandaloneFluid::density),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("viscosity")
                .forGetter(StandaloneFluid::viscosity),
            MaterialRuntimeSettings.CODEC
                .optionalFieldOf("runtime", MaterialRuntimeSettings.DEFAULT)
                .forGetter(StandaloneFluid::runtime))
            .apply(i, StandaloneFluid::new));

    /** A standalone fluid using {@code module} unchanged. */
    public StandaloneFluid(ResourceLocation module) {
        this(module, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(),
            MaterialRuntimeSettings.DEFAULT);
    }

    /**
     * Plans the fluid family as a one-fluid content plan. Its owner is a host
     * material named after the fluid, carrying {@code runtime}; the host has no
     * parts or stats and is not a loaded material.
     *
     * @throws IllegalArgumentException
     *             if the module's factory is not a fluid implementation, or a
     *             value is out of range
     */
    public MaterialFactory.Result resolve(ResourceLocation id,
        DataModules.Fluid data) {
        var host = MaterialBuilder.create().setName(id).setRuntime(runtime)
            .build();
        var builder = template.orElse(data.template()).toBuilder()
            .setName(module).setRegistryName(id).setFactory(data.factory())
            .setFactoryData(data.factoryData());
        if (tint.isPresent())
            builder = builder.setTintColor(tint.get());
        if (temperature.isPresent())
            builder = builder.setTemperature(temperature.get());
        if (density.isPresent())
            builder = builder.setDensity(density.get());
        if (viscosity.isPresent())
            builder = builder.setViscosity(viscosity.get());
        var fluid = builder.buildFor(id);
        return new MaterialFactory.Result(host, new MaterialContentPlan(id,
            Map.of(), Map.of(), Map.of(), fluid, Map.of(), Map.of(), Map.of()));
    }
}
