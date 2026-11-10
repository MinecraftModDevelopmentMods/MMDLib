package zone.moddev.mc.lib.gui.client;

import java.util.*;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import zone.moddev.mc.lib.gui.layout.Rect;

/**
 * Shared neighbour model, tint and direction badges for face configuration
 * drawers.
 */
final class FaceConfigRenderer {
    private final Minecraft mc = Minecraft.getInstance();
    private final Map<RenderType, RenderType> fadedNeighbours = new IdentityHashMap<>();

    Component render(GuiGraphics g, Rect r, BlockEntity entity,
        Direction direction, boolean input, boolean output) {
        boolean disabled = !input && !output;
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1,
            disabled ? 0xbf8b8b8b : 0xff8b8b8b);
        // Tint the background before drawing the neighbour so its model keeps
        // its own colours.
        int green = 0xff30e050, orange = 0xffff8a00;
        int greenWash = 0x3830e050, orangeWash = 0x38ff8a00;
        if (input && output) {
            // Pixel-aligned diagonal from top-right to bottom-left, with no
            // overlapping tint seam.
            for (int row = 0; row < r.height() - 2; row++) {
                int split = r.right() - 1 - row;
                int y = r.y() + 1 + row;
                g.fill(r.x() + 1, y, split, y + 1, greenWash);
                g.fill(split, y, r.right() - 1, y + 1, orangeWash);
            }
            g.fill(r.x(), r.y(), r.right() - 1, r.y() + 1, green);
            g.fill(r.x(), r.y() + 1, r.x() + 1, r.bottom() - 1, green);
            g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), orange);
            g.fill(r.x(), r.bottom() - 1, r.right() - 1, r.bottom(), orange);
        } else {
            if (!disabled)
                g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1,
                    input ? greenWash : orangeWash);
            g.renderOutline(r.x(), r.y(), r.width(), r.height(),
                disabled ? 0xbf555555 : input ? green : orange);
        }
        var level = entity.getLevel();
        var pos = entity.getBlockPos().relative(direction);
        Component neighbour = Component
            .translatable("screen.mmdlib.automation.unloaded");
        if (level != null && level.hasChunkAt(pos)) {
            var state = level.getBlockState(pos);
            neighbour = state.isAir()
                ? Component.translatable("screen.mmdlib.automation.empty")
                : state.getBlock().getName();
            if (!state.isAir()) {
                g.pose().pushPose();
                try {
                    g.flush();
                    g.pose().translate(r.x() + 9, r.y() + 9, 150);
                    g.pose().scale(7.5f, -7.5f, 7.5f);
                    g.pose().mulPose(Axis.XP.rotationDegrees(30));
                    g.pose().mulPose(Axis.YP.rotationDegrees(225));
                    g.pose().translate(-.5, -.5, -.5);
                    mc.getBlockRenderer().renderSingleBlock(state, g.pose(),
                        disabled
                            ? type -> g.bufferSource()
                                .getBuffer(fadedNeighbours.computeIfAbsent(type,
                                    original -> GhostItems.fade(original,
                                        .75f)))
                            : g.bufferSource(),
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    g.flush();
                } catch (RuntimeException unsupported) {
                    // A missing special renderer must not break inventory
                    // interaction.
                } finally {
                    g.pose().popPose();
                }
            }
        }
        // Keep the corner icons above the neighbour model.
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (input)
            g.blitSprite(ResourceLocation.parse("mmdlib:side_mode_input"),
                r.x() + 1, r.y() + 1, 7, 7);
        if (output)
            g.blitSprite(ResourceLocation.parse("mmdlib:side_mode_output"),
                r.right() - 8, r.bottom() - 8, 7, 7);
        g.pose().popPose();
        return neighbour;
    }
}
