package zone.moddev.mc.lib.machine.neoforge.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import zone.moddev.mc.lib.api.entity.EntityPreviewCache;
import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.api.entity.EntitySnapshotPolicy;
import zone.moddev.mc.lib.gui.client.EntityDisplayRendering;
import zone.moddev.mc.lib.gui.client.EntityPreviewPolicies;
import zone.moddev.mc.lib.machine.definition.EntityDisplay;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.PortableVariable;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/**
 * Automatic registration for XML block/item entity displays, including resource
 * reloads.
 */
@EventBusSubscriber(modid = "mmdlib", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MachineEntityDisplays {
    private static final List<java.lang.ref.WeakReference<EntityPreviewCache<?>>> CACHES = new ArrayList<>();

    private record Binding(EntityDisplay display, PortableVariable<?> reference,
        Optional<EntitySnapshotPolicy<?>> policy) {
        static Binding of(MachineDefinition definition) {
            var display = definition.behavior().entityDisplay();
            var processor = definition.behavior().processor()
                .create(definition.processor(), null);
            var reference = processor
                .portableVariable(display.variable().name()).orElse(null);
            return new Binding(display, reference,
                reference == null
                    ? Optional.empty()
                    : reference.entityPolicy());
        }

        EntitySnapshot read(ItemStack stack) {
            return reference != null
                && reference.read(stack) instanceof EntitySnapshot snapshot
                    ? snapshot
                    : EntitySnapshot.EMPTY;
        }

        EntityPreviewCache<LivingEntity> cache() {
            var cache = new EntityPreviewCache<LivingEntity>(64,
                (snapshot, level) -> EntityPreviewPolicies.create(snapshot,
                    level, policy));
            CACHES.add(new java.lang.ref.WeakReference<>(cache));
            return cache;
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(
            () -> MachineContent.definitions().values().forEach(definition -> {
                var display = definition.behavior().entityDisplay();
                if (display != null && display.itemProperty() != null) {
                    var binding = Binding.of(definition);
                    ItemProperties.register(
                        MachineContent.item(definition.id()),
                        display.itemProperty(), (stack, level, entity,
                            seed) -> binding.read(stack).isEmpty() ? 0 : 1);
                }
            }));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var definition : MachineContent.definitions().values())
            if (definition.behavior().entityDisplay() != null
                && definition.behavior().entityDisplay().block())
                event.registerBlockEntityRenderer(
                    MachineContent.entityType(definition.id()),
                    context -> new BlockDisplay(Binding.of(definition)));
    }

    @SubscribeEvent
    public static void items(RegisterClientExtensionsEvent event) {
        for (var definition : MachineContent.definitions().values())
            if (definition.behavior().entityDisplay() != null
                && definition.behavior().entityDisplay().item()) {
                var binding = Binding.of(definition);
                event.registerItem(new IClientItemExtensions() {
                    private ItemDisplay renderer;

                    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        if (renderer == null)
                            renderer = new ItemDisplay(binding);
                        return renderer;
                    }
                }, MachineContent.item(definition.id()));
            }
    }

    @SubscribeEvent
    public static void models(ModelEvent.ModifyBakingResult event) {
        clear();
        for (var definition : MachineContent.definitions().values())
            if (definition.behavior().entityDisplay() != null
                && definition.behavior().entityDisplay().item())
                event.getModels().computeIfPresent(
                    ModelResourceLocation.inventory(definition.id()),
                    (id, model) -> new EntityDisplayItemModel(model));
    }

    private static void clear() {
        CACHES.removeIf(reference -> {
            var cache = reference.get();
            if (cache == null)
                return true;
            cache.clear();
            return false;
        });
    }

    @EventBusSubscriber(modid = "mmdlib", value = Dist.CLIENT)
    public static final class WorldLifecycle {
        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            clear();
        }
    }

    private static final class BlockDisplay
        implements BlockEntityRenderer<DefinedMachineEntity> {
        private final Binding binding;
        private final EntityPreviewCache<LivingEntity> cache;

        BlockDisplay(Binding binding) {
            this.binding = binding;
            cache = binding.cache();
        }

        public void render(DefinedMachineEntity machine, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var display = binding.display();
            if (!(display.variable()
                .read(machine) instanceof EntitySnapshot snapshot))
                return;
            var entity = cache.get(machine.getLevel(), snapshot);
            if (entity == null)
                return;
            var facing = machine.getBlockState()
                .getValue(BlockStateProperties.HORIZONTAL_FACING);
            var offset = display.offset(facing);
            entity.setPos(machine.getBlockPos().getX() + offset.x,
                machine.getBlockPos().getY() + offset.y,
                machine.getBlockPos().getZ() + offset.z);
            pose.pushPose();
            try {
                pose.translate(offset.x, offset.y, offset.z);
                EntityDisplayRendering.render(entity, pose, buffers, light,
                    partialTick, display.width(), display.height(),
                    facing.toYRot() + display.yaw());
            } finally {
                pose.popPose();
            }
        }
    }

    private static final class ItemDisplay
        extends
            BlockEntityWithoutLevelRenderer {
        private final Binding binding;
        private final EntityPreviewCache<LivingEntity> cache;

        ItemDisplay(Binding binding) {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
            this.binding = binding;
            cache = binding.cache();
        }

        public void renderByItem(ItemStack stack, ItemDisplayContext context,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var minecraft = Minecraft.getInstance();
            var items = minecraft.getItemRenderer();
            var model = items.getModel(stack, minecraft.level, null, 0);
            if (model instanceof EntityDisplayItemModel wrapped)
                model = wrapped.geometry();
            for (var pass : model.getRenderPasses(stack, true))
                for (var type : pass.getRenderTypes(stack, true))
                    items.renderModelLists(pass, stack, light, overlay, pose,
                        ItemRenderer.getFoilBufferDirect(buffers, type, true,
                            stack.hasFoil()));
            var entity = cache.get(minecraft.level, binding.read(stack));
            if (entity == null)
                return;
            var display = binding.display();
            pose.pushPose();
            try {
                pose.translate(display.x(), display.y(), display.z());
                EntityDisplayRendering.render(entity, pose, buffers, light,
                    minecraft.getTimer().getGameTimeDeltaPartialTick(false),
                    display.width(), display.height(), display.itemYaw());
            } finally {
                pose.popPose();
            }
        }
    }
}
