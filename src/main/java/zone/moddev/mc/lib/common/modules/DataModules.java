package zone.moddev.mc.lib.common.modules;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.*;
import zone.moddev.mc.lib.common.codecs.*;
import zone.moddev.mc.lib.common.types.*;

/**
 * The JSON schemas of item, block and fluid modules (files under a mod's {@code mmdlib/item},
 * {@code mmdlib/block} and {@code mmdlib/fluid}). The same schemas are used for MMDLib's bundled
 * modules and for modules from other mods and packs. See {@code docs/data-modules.md}.
 */
public final class DataModules {
  private DataModules() {}

  private static final Codec<String> MOD =
      Codec.STRING.validate(
          value ->
              value.matches("[a-z][a-z0-9_]{1,63}")
                  ? DataResult.success(value)
                  : DataResult.error(() -> "Invalid mod ID: " + value));

  /**
   * An item module: one selectable item form, such as a pickaxe.
   *
   * @param factory the ID of the {@link zone.moddev.mc.lib.api.ItemImplementation} that creates it
   * @param requiredMod the mod it needs, if any ({@code requires_mod})
   * @param factoryData settings for that implementation
   * @param template the item's definition template
   * @param part aliases and companion definitions
   */
  public record Item(
      ResourceLocation factory,
      Optional<String> requiredMod,
      FactoryData factoryData,
      ItemTemplate template,
      PartMetadata part) {
    public static final Codec<Item> CODEC =
        RecordCodecBuilder.<Item>create(
                i ->
                    i.group(
                            ResourceLocation.CODEC.fieldOf("factory").forGetter(Item::factory),
                            MOD.optionalFieldOf("requires_mod").forGetter(Item::requiredMod),
                            FactoryData.CODEC
                                .optionalFieldOf("factory_data", FactoryData.EMPTY)
                                .forGetter(Item::factoryData),
                            ItemTemplate.CODEC.fieldOf("template").forGetter(Item::template),
                            PartMetadata.CODEC
                                .optionalFieldOf("part", PartMetadata.EMPTY)
                                .forGetter(Item::part))
                        .apply(i, Item::new))
            .validate(
                value ->
                    value.part().blockEntity().isEmpty()
                        ? DataResult.success(value)
                        : DataResult.error(() -> "Block entities require a block module"));

    /** This module as a part with ID {@code id}. */
    public MaterialPart definition(ResourceLocation id) {
      return new MaterialPart(id, MaterialPart.Kind.ITEM, requiredMod, part, factory, factoryData);
    }

    /** The module's item template, named {@code id}, ready to be bound to {@code material}. */
    public ItemBuilder builder(ResourceLocation id, MMDMaterial material) {
      return template.toBuilder().setName(id).setType(factory.toString());
    }
  }

  /**
   * A block module: one selectable block form, such as a door.
   *
   * @param factory the ID of the {@link zone.moddev.mc.lib.api.BlockImplementation} that creates it
   * @param requiredMod the mod it needs, if any ({@code requires_mod})
   * @param materialDefaults whether the material's stats set hardness, blast resistance, harvest
   *     tags and correct-tool drops
   * @param factoryData settings for that implementation
   * @param template the block's definition template
   * @param part aliases and companion definitions (block entity, world entity)
   */
  public record Block(
      ResourceLocation factory,
      Optional<String> requiredMod,
      boolean materialDefaults,
      FactoryData factoryData,
      BlockTemplate template,
      PartMetadata part) {
    public static final Codec<Block> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        ResourceLocation.CODEC.fieldOf("factory").forGetter(Block::factory),
                        MOD.optionalFieldOf("requires_mod").forGetter(Block::requiredMod),
                        Codec.BOOL
                            .optionalFieldOf("material_defaults", true)
                            .forGetter(Block::materialDefaults),
                        FactoryData.CODEC
                            .optionalFieldOf("factory_data", FactoryData.EMPTY)
                            .forGetter(Block::factoryData),
                        BlockTemplate.CODEC.fieldOf("template").forGetter(Block::template),
                        PartMetadata.CODEC
                            .optionalFieldOf("part", PartMetadata.EMPTY)
                            .forGetter(Block::part))
                    .apply(i, Block::new));

    /** This module as a part with ID {@code id}. */
    public MaterialPart definition(ResourceLocation id) {
      return new MaterialPart(id, MaterialPart.Kind.BLOCK, requiredMod, part, factory, factoryData);
    }

    /**
     * The module's block template, named {@code id}, with the material's defaults applied when
     * {@code material_defaults} is on: hardness from {@code special.block_hardness} or {@code
     * hardness}, blast resistance from {@code special.blast_resistance} or twice the hardness, and
     * the material's harvest tags.
     */
    public BlockBuilder builder(ResourceLocation id, MMDMaterial material) {
      var b = template.toBuilder().setName(id).setType(factory.toString());
      if (materialDefaults) {
        var p = material.properties();
        var tags = new java.util.HashSet<>(template.tags());
        tags.addAll(p.harvestTags());
        b =
            b.setStrength(
                    (float) p.special().getOrDefault("block_hardness", p.hardness()).doubleValue(),
                    (float)
                        p.special()
                            .getOrDefault("blast_resistance", 2 * p.hardness())
                            .doubleValue())
                .setBlockTags(tags)
                .setRequiresCorrectToolForDrops(
                    template.properties().requiresCorrectTool() || !p.harvestTags().isEmpty());
      }
      return b;
    }
  }

  /**
   * A fluid module: how a material's fluid is built.
   *
   * @param factory the ID of the fluid implementation
   * @param requiredMod the mod it needs, if any ({@code requires_mod})
   * @param materialDefaults whether the material's color and {@code special} temperature, density
   *     and viscosity apply
   * @param factoryData settings for that implementation
   * @param template the fluid's definition template
   */
  public record Fluid(
      ResourceLocation factory,
      Optional<String> requiredMod,
      boolean materialDefaults,
      FactoryData factoryData,
      FluidTemplate template) {
    public static final Codec<Fluid> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        ResourceLocation.CODEC.fieldOf("factory").forGetter(Fluid::factory),
                        MOD.optionalFieldOf("requires_mod").forGetter(Fluid::requiredMod),
                        Codec.BOOL
                            .optionalFieldOf("material_defaults", true)
                            .forGetter(Fluid::materialDefaults),
                        FactoryData.CODEC
                            .optionalFieldOf("factory_data", FactoryData.EMPTY)
                            .forGetter(Fluid::factoryData),
                        FluidTemplate.CODEC.fieldOf("template").forGetter(Fluid::template))
                    .apply(i, Fluid::new));

    /**
     * The module's fluid template, named {@code id}, with the material's defaults applied when
     * {@code material_defaults} is on.
     *
     * @throws IllegalArgumentException if a {@code special} fluid value is not a whole number
     */
    public FluidBuilder builder(ResourceLocation id, MMDMaterial material) {
      var b = template.toBuilder().setName(id).setFactory(factory).setFactoryData(factoryData);
      if (materialDefaults) {
        var p = material.properties();
        b = b.setTintColor(p.color());
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
