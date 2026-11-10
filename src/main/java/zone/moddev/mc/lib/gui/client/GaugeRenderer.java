package zone.moddev.mc.lib.gui.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import org.joml.Quaternionf;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.GaugeFill;
import zone.moddev.mc.lib.gui.model.Orientation;

/**
 * Graduated glass gauge, with clipped fluid tiles kept upright in both
 * orientations.
 */
final class GaugeRenderer {
    private GaugeRenderer() {
    }

    /**
     * Shared slim casing and medium-grey empty interior for fluid and energy
     * gauges.
     */
    static void background(GuiGraphics g, Rect r, boolean vertical) {
        var state = RenderState.capture();
        try {
            int length = vertical ? r.height() : r.width();
            shell(g, r, vertical, length, "body");
        } finally {
            try {
                g.flush();
            } finally {
                state.restore();
            }
        }
    }

    static void draw(GuiGraphics g, Rect r, Orientation orientation,
        GaugeFill fill, double ratio) {
        var state = RenderState.capture();
        try {
            g.flush();
            g.setColor(1, 1, 1, 1);
            drawGauge(g, r, orientation, fill, ratio);
        } finally {
            try {
                g.flush();
            } finally {
                state.restore();
            }
        }
    }

    private static void drawGauge(GuiGraphics g, Rect r,
        Orientation orientation, GaugeFill fill, double ratio) {
        boolean vertical = orientation == Orientation.VERTICAL;
        int length = vertical ? r.height() : r.width();
        shell(g, r, vertical, length, "body");
        int filled = (int) Math.round((length - 2) * ratio);
        FluidStack fluid = fill.fluid().orElse(null);
        int color = fill.colour().orElseGet(() -> IClientFluidTypeExtensions
            .of(fluid.getFluid()).getTintColor(fluid));
        for (int step = 0; step < filled; step++) {
            int row = length - 2 - step;
            int inset = (row == 1 || row == length - 2) ? 2 : 1;
            Rect strip = vertical
                ? new Rect(r.x() + inset, r.y() + row, 18 - 2 * inset, 1)
                : new Rect(r.x() + length - 1 - row, r.y() + inset, 1,
                    18 - 2 * inset);
            if (fluid != null)
                fluid(g, strip, r, fluid, color);
            else
                g.fill(strip.x(), strip.y(), strip.right(), strip.bottom(),
                    color | 0xff000000);
            if (vertical && step == filled - 1)
                g.fill(strip.x(), strip.y(), strip.right(), strip.bottom(),
                    0x4dffffff);
        }
        // Keep the shared empty background flat. Only the contents get curved
        // lighting.
        if (filled > 0) {
            if (vertical)
                g.enableScissor(r.x(), r.bottom() - 1 - filled, r.right(),
                    r.bottom() - 1);
            else
                g.enableScissor(r.x() + 1, r.y(), r.x() + 1 + filled,
                    r.bottom());
            try {
                shell(g, r, vertical, length, "glass");
            } finally {
                g.disableScissor();
            }
        }
        int inner = length - 2;
        for (int n = 1; n < 20; n++) {
            boolean major = n == 5 || n == 10 || n == 15;
            if (!major && ((inner / 20f < 3 && n % 2 != 0) || inner / 10f < 3))
                continue;
            int mark = major ? 7 : n % 2 == 0 ? 3 : 2;
            int row = 1 + inner - Math.round(inner * n / 20f);
            int x = vertical ? r.x() + 1 : r.x() + length - 1 - row,
                y = vertical ? r.y() + row : r.y() + 1;
            g.fill(x, y + 1, x + (vertical ? mark : 1),
                y + 1 + (vertical ? 1 : mark), 0x59000000);
            g.fill(x, y, x + (vertical ? mark : 1), y + (vertical ? 1 : mark),
                0xd9eef3f6);
        }
    }

    private static void shell(GuiGraphics g, Rect r, boolean vertical,
        int length, String layer) {
        // GuiGraphics fills may be buffered, and flushing them can disable
        // blending.
        // Drain them before configuring the immediate texture draws (especially
        // translucent glass).
        g.flush();
        g.setColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        try {
            if (vertical)
                g.pose().translate(r.x(), r.y(), 0);
            else {
                g.pose().translate(r.x() + length, r.y(), 0);
                g.pose().mulPose(
                    new Quaternionf().rotationZ((float) (Math.PI / 2)));
            }
            int cap = Math.min(5, length / 2);
            g.blit(
                ResourceLocation
                    .parse("mmdlib:textures/gui/gauge/" + layer + "_top.png"),
                0, 0, 0, 0, 18, cap, 18, 5);
            for (int row = cap; row < length - cap; row++)
                g.blit(
                    ResourceLocation.parse(
                        "mmdlib:textures/gui/gauge/" + layer + "_middle.png"),
                    0, row, 0, 0, 18, 1, 18, 1);
            g.blit(
                ResourceLocation.parse(
                    "mmdlib:textures/gui/gauge/" + layer + "_bottom.png"),
                0, length - cap, 0, 5 - cap, 18, cap, 18, 5);
        } finally {
            g.pose().popPose();
            RenderSystem.disableBlend();
        }
    }

    /**
     * Preserve caller state even when a buffered GUI fill or a fluid draw
     * changes it.
     */
    private record RenderState(boolean blend, boolean depth, int sourceRgb,
        int destinationRgb, int sourceAlpha, int destinationAlpha,
        float[] color) {
        static RenderState capture() {
            return new RenderState(GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                RenderSystem.getShaderColor().clone());
        }

        void restore() {
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb,
                sourceAlpha, destinationAlpha);
            if (blend)
                RenderSystem.enableBlend();
            else
                RenderSystem.disableBlend();
            if (depth)
                RenderSystem.enableDepthTest();
            else
                RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    private static void fluid(GuiGraphics g, Rect strip, Rect origin,
        FluidStack fluid, int tint) {
        var texture = IClientFluidTypeExtensions.of(fluid.getFluid())
            .getStillTexture(fluid);
        var sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        g.setColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f,
            (tint & 255) / 255f, ((tint >>> 24) & 255) / 255f);
        g.enableScissor(strip.x(), strip.y(), strip.right(), strip.bottom());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int x = origin.x(); x < origin.right(); x += 16)
            for (int y = origin.y(); y < origin.bottom(); y += 16) {
                if (x + 16 > strip.x() && x < strip.right()
                    && y + 16 > strip.y() && y < strip.bottom())
                    g.blit(x, y, 0, 16, 16, sprite);
            }
        g.disableScissor();
        RenderSystem.disableBlend();
        g.setColor(1, 1, 1, 1);
    }
}
