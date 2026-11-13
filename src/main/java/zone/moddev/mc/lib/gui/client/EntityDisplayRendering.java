package zone.moddev.mc.lib.gui.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;

/** Shared static-pose entity overlay. Does not spawn or tick entities. */
public final class EntityDisplayRendering {
    private static boolean rendering;

    private EntityDisplayRendering() {
    }

    public static void render(LivingEntity entity, PoseStack pose,
        MultiBufferSource buffers, int light, float partialTick, float width,
        float height, float yaw) {
        if (rendering)
            return; // A displayed entity may itself carry another
                    // entity-display item.
        var size = entity.getDimensions(entity.getPose());
        float scale = Math.min(1,
            Math.min(width / size.width(), height / size.height()));
        entity.walkAnimation.setSpeed(0);
        entity.tickCount = (int) entity.level().getGameTime();
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = yaw;
        entity.setXRot(0);
        entity.xRotO = 0;
        rendering = true;
        pose.pushPose();
        try {
            pose.scale(scale, scale, scale);
            Minecraft.getInstance().getEntityRenderDispatcher()
                .getRenderer(entity)
                .render(entity, yaw, partialTick, pose, buffers, light);
        } finally {
            pose.popPose();
            rendering = false;
        }
    }
}
