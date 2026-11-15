package zone.moddev.mc.lib.machine.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import zone.moddev.mc.lib.machine.definition.ItemDisplay;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Shared front-face overlay driven by a declared tracking-player variable. */
final class MachineItemDisplay
    implements BlockEntityRenderer<DefinedMachineEntity> {
    private final ItemDisplay display;

    MachineItemDisplay(ItemDisplay display) {
        this.display = display;
    }

    static ItemStack item(Object value) {
        if (value instanceof ItemStack stack)
            return stack.copyWithCount(1);
        if (value instanceof String name && !name.isEmpty()) {
            var id = ResourceLocation.tryParse(name);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id))
                return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void render(DefinedMachineEntity machine, float partialTick,
        PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var stack = item(display.variable().read(machine));
        if (stack.isEmpty())
            return;
        var facing = machine.getBlockState()
            .getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (machine.getLevel() != null)
            light = LevelRenderer.getLightColor(machine.getLevel(),
                machine.getBlockPos().relative(facing));
        pose.pushPose();
        try {
            var offset = display.offset(facing);
            pose.translate(offset.x, offset.y, offset.z);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.scale(display.width(), display.height(), .001f);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                ItemDisplayContext.GUI, light, overlay, pose, buffers,
                machine.getLevel(), 0);
        } finally {
            pose.popPose();
        }
    }
}
