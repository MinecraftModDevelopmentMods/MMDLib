package zone.moddev.mc.lib.common.types;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.MaterialBuilder;
import zone.moddev.mc.lib.common.codecs.ItemTemplate;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.DataModules;

/**
 * An item with its own registry ID and no material, such as a wrench or an upgrade card: the item
 * counterpart of {@link StandaloneBlock}. It is built by an item module's registered factory, like
 * a material item. {@code factory_data} is merged over the module's; {@code template} replaces the
 * module's template; {@code runtime} supplies what a material normally would (e.g. the repair
 * item). Material stats are not applied: an implementation that reads them sees the defaults.
 *
 * @param module the item module that builds the item
 * @param factoryData merged over the module's {@code factory_data}
 * @param template replaces the module's template when present
 * @param runtime the settings a material would supply
 */
public record StandaloneItem(
    ResourceLocation module,
    FactoryData factoryData,
    Optional<ItemTemplate> template,
    MaterialRuntimeSettings runtime) {
  /** Reads a standalone item file; every field but {@code module} is optional. */
  public static final Codec<StandaloneItem> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC.fieldOf("module").forGetter(StandaloneItem::module),
                      FactoryData.CODEC
                          .optionalFieldOf("factory_data", FactoryData.EMPTY)
                          .forGetter(StandaloneItem::factoryData),
                      ItemTemplate.CODEC
                          .optionalFieldOf("template")
                          .forGetter(StandaloneItem::template),
                      MaterialRuntimeSettings.CODEC
                          .optionalFieldOf("runtime", MaterialRuntimeSettings.DEFAULT)
                          .forGetter(StandaloneItem::runtime))
                  .apply(i, StandaloneItem::new));

  /**
   * Plans the item as a one-item content plan. Its owner is a host material named after the item,
   * carrying {@code runtime}; the host has no parts or stats and is not a loaded material.
   *
   * @throws IllegalArgumentException if the module has a world-entity companion (e.g. an arrow's
   *     projectile), or a {@code factory_data} is not an object
   */
  public MaterialFactory.Result resolve(ResourceLocation id, DataModules.Item data) {
    var host = MaterialBuilder.create().setName(id).setRuntime(runtime).build();
    FactoryData merged;
    try {
      merged = data.factoryData().overlay(factoryData);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Standalone factory_data must be an object: " + module);
    }
    var part =
        new MaterialPart(
            module,
            MaterialPart.Kind.ITEM,
            data.requiredMod(),
            data.part(),
            data.factory(),
            merged);
    if (part.needsEntity())
      throw new IllegalArgumentException(
          "Standalone items cannot use a module with a world entity: " + module);
    var item =
        template.orElse(data.template()).toBuilder()
            .setName(module)
            .setType(data.factory().toString())
            .forMaterial(id)
            .setRegistryName(id)
            .build();
    return new MaterialFactory.Result(
        host, new MaterialContentPlan(id, Map.of(part, item), Map.of(), Map.of(), null));
  }
}
