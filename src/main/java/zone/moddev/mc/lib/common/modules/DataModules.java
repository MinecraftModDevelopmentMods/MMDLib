package zone.moddev.mc.lib.common.modules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.codecs.*;
import zone.moddev.mc.lib.common.builders.*;
import zone.moddev.mc.lib.common.types.*;
import java.util.Optional;

/** Source-independent schemas shared by bundled modules and mod/pack modules. */
public final class DataModules {
    private DataModules() {}
    private static final Codec<String> MOD = Codec.STRING.validate(value -> value.matches("[a-z][a-z0-9_]{1,63}")
            ? DataResult.success(value) : DataResult.error(() -> "Invalid mod ID: " + value));
    public record Item(ResourceLocation factory, Optional<String> requiredMod, FactoryData factoryData, ItemTemplate template, PartMetadata part) {
        public static final Codec<Item> CODEC = RecordCodecBuilder.<Item>create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("factory").forGetter(Item::factory),
                MOD.optionalFieldOf("requires_mod").forGetter(Item::requiredMod),
                FactoryData.CODEC.optionalFieldOf("factory_data", FactoryData.EMPTY).forGetter(Item::factoryData),
                ItemTemplate.CODEC.fieldOf("template").forGetter(Item::template),
                PartMetadata.CODEC.optionalFieldOf("part", PartMetadata.EMPTY).forGetter(Item::part)
        ).apply(i, Item::new)).validate(value -> value.part().blockEntity().isEmpty()
                ? DataResult.success(value) : DataResult.error(() -> "Block entities require a block module"));
        public MaterialPart definition(ResourceLocation id) {
            return new MaterialPart(id, MaterialPart.Kind.ITEM, requiredMod, part, factory, factoryData);
        }
        public ItemBuilder builder(ResourceLocation id, MMDMaterial material) {
            return template.toBuilder().setName(id).setType(factory.toString());
        }
    }
    public record Block(ResourceLocation factory, Optional<String> requiredMod, boolean materialDefaults, FactoryData factoryData, BlockTemplate template, PartMetadata part) {
        public static final Codec<Block> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("factory").forGetter(Block::factory),
                MOD.optionalFieldOf("requires_mod").forGetter(Block::requiredMod),
                Codec.BOOL.optionalFieldOf("material_defaults", true).forGetter(Block::materialDefaults),
                FactoryData.CODEC.optionalFieldOf("factory_data", FactoryData.EMPTY).forGetter(Block::factoryData),
                BlockTemplate.CODEC.fieldOf("template").forGetter(Block::template),
                PartMetadata.CODEC.optionalFieldOf("part", PartMetadata.EMPTY).forGetter(Block::part)
        ).apply(i, Block::new));
        public MaterialPart definition(ResourceLocation id) {
            return new MaterialPart(id, MaterialPart.Kind.BLOCK, requiredMod, part, factory, factoryData);
        }
        public BlockBuilder builder(ResourceLocation id, MMDMaterial material) {
            var b = template.toBuilder().setName(id).setType(factory.toString());
            if (materialDefaults) {
                var p = material.properties();
                var tags = new java.util.HashSet<>(template.tags()); tags.addAll(p.harvestTags());
                b = b.setStrength((float) p.special().getOrDefault("block_hardness", p.hardness()).doubleValue(), (float) p.special().getOrDefault("blast_resistance", 2 * p.hardness()).doubleValue())
                        .setBlockTags(tags).setRequiresCorrectToolForDrops(template.properties().requiresCorrectTool() || !p.harvestTags().isEmpty());
            }
            return b;
        }
    }
    public record Fluid(ResourceLocation factory, Optional<String> requiredMod, boolean materialDefaults, FactoryData factoryData, FluidTemplate template) {
        public static final Codec<Fluid> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("factory").forGetter(Fluid::factory),
                MOD.optionalFieldOf("requires_mod").forGetter(Fluid::requiredMod),
                Codec.BOOL.optionalFieldOf("material_defaults", true).forGetter(Fluid::materialDefaults),
                FactoryData.CODEC.optionalFieldOf("factory_data", FactoryData.EMPTY).forGetter(Fluid::factoryData),
                FluidTemplate.CODEC.fieldOf("template").forGetter(Fluid::template)
        ).apply(i, Fluid::new));
        public FluidBuilder builder(ResourceLocation id, MMDMaterial material) {
            var b = template.toBuilder().setName(id).setFactory(factory).setFactoryData(factoryData);
            if (materialDefaults) {
                var p = material.properties(); b = b.setTintColor(p.color());
                if (p.special().containsKey("temperature")) b = b.setTemperature(integer(p, "temperature"));
                if (p.special().containsKey("density")) b = b.setDensity(integer(p, "density"));
                if (p.special().containsKey("viscosity")) b = b.setViscosity(integer(p, "viscosity"));
            }
            return b;
        }
    }
    private static int integer(MaterialProperties p, String key) {
        double v = p.special().get(key);
        if (v != Math.rint(v) || v < Integer.MIN_VALUE || v > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Property must be an integer: " + key);
        return (int) v;
    }
}
