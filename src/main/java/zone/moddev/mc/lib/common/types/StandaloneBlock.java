package zone.moddev.mc.lib.common.types;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.MaterialBuilder;
import zone.moddev.mc.lib.common.codecs.BlockEntityTemplate;
import zone.moddev.mc.lib.common.codecs.BlockTemplate;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.DataModules;

/**
 * A block with its own registry ID and no material, as MMDLib 1.0 allowed (e.g. the player-only
 * plate {@code mmdlib:human_detector}). It is built by a block module's registered factory, like a
 * material block. {@code factory_data} is merged over the module's; {@code template} replaces the
 * module's template; {@code runtime} supplies what a material normally would (block set type,
 * sound, map color, base block). When the module declares a block entity, it is planned too, with
 * the block's ID, and {@code block_entity_data} is merged over its {@code factory_data}.
 *
 * @param module the block module that builds the block
 * @param factoryData merged over the module's {@code factory_data}
 * @param template replaces the module's template when present
 * @param runtime the settings a material would supply
 * @param blockEntityData merged over the module's block-entity {@code factory_data}
 */
public record StandaloneBlock(
    ResourceLocation module,
    FactoryData factoryData,
    Optional<BlockTemplate> template,
    MaterialRuntimeSettings runtime,
    FactoryData blockEntityData) {
  /** Reads a standalone block file; every field but {@code module} is optional. */
  public static final Codec<StandaloneBlock> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC.fieldOf("module").forGetter(StandaloneBlock::module),
                      FactoryData.CODEC
                          .optionalFieldOf("factory_data", FactoryData.EMPTY)
                          .forGetter(StandaloneBlock::factoryData),
                      BlockTemplate.CODEC
                          .optionalFieldOf("template")
                          .forGetter(StandaloneBlock::template),
                      MaterialRuntimeSettings.CODEC
                          .optionalFieldOf("runtime", MaterialRuntimeSettings.DEFAULT)
                          .forGetter(StandaloneBlock::runtime),
                      FactoryData.CODEC
                          .optionalFieldOf("block_entity_data", FactoryData.EMPTY)
                          .forGetter(StandaloneBlock::blockEntityData))
                  .apply(i, StandaloneBlock::new));

  /** A standalone block without block-entity overrides. */
  public StandaloneBlock(
      ResourceLocation module,
      FactoryData factoryData,
      Optional<BlockTemplate> template,
      MaterialRuntimeSettings runtime) {
    this(module, factoryData, template, runtime, FactoryData.EMPTY);
  }

  /**
   * Plans the block, and its block entity if the module has one, as a one-block content plan. Its
   * owner is a host material named after the block, carrying {@code runtime}; the host has no parts
   * or stats and is not a loaded material.
   *
   * @throws IllegalArgumentException if the module has a world-entity companion, a {@code
   *     factory_data} is not an object, or {@code block_entity_data} is set but the module has no
   *     block entity
   */
  public MaterialFactory.Result resolve(ResourceLocation id, DataModules.Block data) {
    var host = MaterialBuilder.create().setName(id).setRuntime(runtime).build();
    var part =
        new MaterialPart(
            module,
            MaterialPart.Kind.BLOCK,
            data.requiredMod(),
            data.part(),
            data.factory(),
            merged(data.factoryData(), factoryData));
    if (part.needsEntity())
      throw new IllegalArgumentException(
          "Standalone blocks cannot use a module with a world entity: " + module);
    if (!part.needsBlockEntity() && !blockEntityData.equals(FactoryData.EMPTY))
      throw new IllegalArgumentException(
          "block_entity_data needs a module with part.block_entity: " + module);
    var block =
        template.orElse(data.template()).toBuilder()
            .setName(module)
            .setType(data.factory().toString())
            .forMaterial(id)
            .setRegistryName(id)
            .build();
    var blockEntities = new HashMap<MaterialPart, MMDBlockEntity>();
    part.metadata()
        .blockEntity()
        .ifPresent(
            entity ->
                blockEntities.put(
                    part,
                    new BlockEntityTemplate(
                            entity.factory(), merged(entity.factoryData(), blockEntityData))
                        .bind(block)));
    return new MaterialFactory.Result(
        host,
        new MaterialContentPlan(
            id,
            block.item() == null ? Map.of() : Map.of(part, block.item()),
            Map.of(part, block),
            Map.of(),
            null,
            Map.of(),
            Map.of(),
            blockEntities));
  }

  private FactoryData merged(FactoryData base, FactoryData overrides) {
    try {
      return base.overlay(overrides);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Standalone factory_data must be an object: " + module);
    }
  }
}
