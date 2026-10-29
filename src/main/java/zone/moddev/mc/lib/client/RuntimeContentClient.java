package zone.moddev.mc.lib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;

import zone.moddev.mc.lib.common.runtime.*;

/**
 * Client-side setup for content registered by every {@link RuntimeContentRegistrar}: item and block
 * tints from material colors, projectile renderers, fluid textures, render layers, and the bow,
 * crossbow, shield and fishing-rod model properties copied from vanilla.
 */
@EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RuntimeContentClient {
  private RuntimeContentClient() {}

  /** Tints material items with their material's color. */
  @SubscribeEvent
  public static void items(RegisterColorHandlersEvent.Item event) {
    for (var registrar : RuntimeContentRegistrar.instances())
      registrar
          .itemMaterials()
          .forEach(
              (id, material) -> {
                var item = registrar.items().get(id).get();
                // Fluid container models obtain their tint from the fluid type, not the bucket
                // shell.
                if (!(item instanceof BucketItem)
                    && !StaticAssetRules.INSTANCE.untintedItems().contains(id))
                  event.register(
                      (stack, layer) -> layer == 0 ? material.properties().color() : -1, item);
              });
  }

  /** Tints material blocks with their material's color. */
  @SubscribeEvent
  public static void blocks(RegisterColorHandlersEvent.Block event) {
    for (var registrar : RuntimeContentRegistrar.instances())
      registrar
          .blockMaterials()
          .forEach(
              (id, material) -> {
                if (!StaticAssetRules.INSTANCE.untintedBlocks().contains(id))
                  event.register(
                      (state, level, pos, tint) -> material.properties().color(),
                      registrar.blocks().get(id).get());
              });
  }

  /** Registers arrow renderers for material projectiles. */
  @SubscribeEvent
  public static void entities(EntityRenderersEvent.RegisterRenderers event) {
    for (var registrar : RuntimeContentRegistrar.instances())
      registrar
          .projectiles()
          .values()
          .forEach(
              type ->
                  event.registerEntityRenderer(
                      type.get(),
                      context ->
                          new ArrowRenderer<MaterialProjectile>(context) {
                            @Override
                            public ResourceLocation getTextureLocation(MaterialProjectile entity) {
                              return ResourceLocation.parse(
                                  "minecraft:textures/entity/projectiles/arrow.png");
                            }
                          }));
  }

  /** Registers fluid textures and tints, and the shield's item renderer. */
  @SubscribeEvent
  public static void extensions(RegisterClientExtensionsEvent event) {
    for (var registrar : RuntimeContentRegistrar.instances()) {
      for (var family : registrar.fluidFamilies()) {
        var appearance = family.definition.appearance();
        event.registerFluidType(
            new IClientFluidTypeExtensions() {
              @Override
              public ResourceLocation getStillTexture() {
                return appearance.still();
              }

              @Override
              public ResourceLocation getFlowingTexture() {
                return appearance.flowing();
              }

              @Override
              public ResourceLocation getOverlayTexture() {
                return appearance.overlay().orElse(null);
              }

              @Override
              public int getTintColor() {
                return appearance.tint();
              }
            },
            family.type.get());
      }
      registrar
          .items()
          .values()
          .forEach(
              holder -> {
                if (holder.get() instanceof ShieldItem
                    && !StaticAssetRules.INSTANCE.untintedItems().contains(holder.getId()))
                  event.registerItem(
                      new IClientItemExtensions() {
                        private BlockEntityWithoutLevelRenderer renderer;

                        @Override
                        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                          if (renderer == null) {
                            var minecraft = Minecraft.getInstance();
                            renderer =
                                new BlockEntityWithoutLevelRenderer(
                                    minecraft.getBlockEntityRenderDispatcher(),
                                    minecraft.getEntityModels()) {
                                  @Override
                                  public void renderByItem(
                                      ItemStack stack,
                                      ItemDisplayContext context,
                                      com.mojang.blaze3d.vertex.PoseStack pose,
                                      MultiBufferSource buffers,
                                      int light,
                                      int overlay) {
                                    var vanilla = new ItemStack(Items.SHIELD);
                                    vanilla.applyComponents(stack.getComponentsPatch());
                                    Minecraft.getInstance()
                                        .getItemRenderer()
                                        .getBlockEntityRenderer()
                                        .renderByItem(
                                            vanilla, context, pose, buffers, light, overlay);
                                  }
                                };
                          }
                          return renderer;
                        }
                      },
                      holder.get());
              });
    }
  }

  /**
   * Sets cutout layers for non-occluding blocks, translucent layers for fluids, and copies vanilla
   * item model properties.
   */
  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () -> {
          for (var registrar : RuntimeContentRegistrar.instances()) {
            registrar
                .blocks()
                .values()
                .forEach(
                    holder -> {
                      var block = holder.get();
                      if (!(block instanceof net.minecraft.world.level.block.LiquidBlock)
                          && !block.defaultBlockState().canOcclude())
                        ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout());
                    });
            for (var family : registrar.fluidFamilies()) {
              ItemBlockRenderTypes.setRenderLayer(family.source.get(), RenderType.translucent());
              ItemBlockRenderTypes.setRenderLayer(family.flowing.get(), RenderType.translucent());
            }
            registrar
                .items()
                .values()
                .forEach(
                    holder -> {
                      var item = holder.get();
                      if (item instanceof BowItem)
                        copyProperties(item, Items.BOW, "pull", "pulling");
                      if (item instanceof CrossbowItem)
                        copyProperties(
                            item, Items.CROSSBOW, "pull", "pulling", "charged", "firework");
                      if (item instanceof FishingRodItem)
                        copyProperties(item, Items.FISHING_ROD, "cast");
                      if (item instanceof ShieldItem)
                        copyProperties(item, Items.SHIELD, "blocking");
                    });
          }
        });
  }

  private static void copyProperties(Item target, Item vanilla, String... names) {
    for (String name : names) {
      var id = ResourceLocation.withDefaultNamespace(name);
      var property = ItemProperties.getProperty(new ItemStack(vanilla), id);
      if (property instanceof ClampedItemPropertyFunction clamped)
        ItemProperties.register(target, id, clamped);
    }
  }
}
