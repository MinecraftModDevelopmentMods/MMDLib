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
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.machine.neoforge.FluidStacks;

/**
 * Graduated glass gauge, with clipped fluid tiles kept upright in both
 * orientations.
 */
final class GaugeRenderer {
    private static final float GHOST_OPACITY = .45f;

    private record Reservation(double input, double output, GaugeFill fill) {
        static final Reservation NONE = new Reservation(0, 0, null);
    }

    private enum BandWidth {
        FULL, INPUT_HALF, OUTPUT_HALF
    }

    private GaugeRenderer() {
    }

    static void tank(FluidTankElement element, ElementRenderers.Context c) {
        var plan = c.tankPlan(element.tank());
        var reservation = Reservation.NONE;
        if (!plan.isNone() && c.maximum() instanceof Number capacity
            && capacity.doubleValue() > 0) {
            int held = c.value() instanceof FluidStack fluid
                ? fluid.getAmount()
                : 0;
            var output = plan.add();
            reservation = new Reservation(
                Math.min(held, plan.take()) / capacity.doubleValue(),
                output.isEmpty() ? 0 : output.amount() / capacity.doubleValue(),
                output.isEmpty()
                    ? null
                    : element.fill(FluidStacks.stack(output)));
        }
        draw(c.graphics(), c.bounds(), element.orientation(),
            element.fill(c.value()), c.ratio(), c.groupColour(), reservation);
    }

    /**
     * Shared slim casing and medium-grey empty interior for fluid and energy
     * gauges.
     */
    static void background(GuiGraphics g, Rect r, boolean vertical) {
        background(g, r, vertical, java.util.Optional.empty());
    }

    static void background(GuiGraphics g, Rect r, boolean vertical,
        java.util.Optional<Integer> groupColour) {
        var state = RenderState.capture();
        try {
            int length = vertical ? r.height() : r.width();
            shell(g, r, vertical, length, "body", 1,
                ElementDrawing.backgroundTint(0xffffff, groupColour));
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
        draw(g, r, orientation, fill, ratio, java.util.Optional.empty());
    }

    static void draw(GuiGraphics g, Rect r, Orientation orientation,
        GaugeFill fill, double ratio, java.util.Optional<Integer> groupColour) {
        draw(g, r, orientation, fill, ratio, groupColour, Reservation.NONE);
    }

    private static void draw(GuiGraphics g, Rect r, Orientation orientation,
        GaugeFill fill, double ratio, java.util.Optional<Integer> groupColour,
        Reservation reservation) {
        var state = RenderState.capture();
        try {
            g.flush();
            g.setColor(1, 1, 1, 1);
            drawGauge(g, r, orientation, fill, ratio, groupColour, reservation);
        } finally {
            try {
                g.flush();
            } finally {
                state.restore();
            }
        }
    }

    private static void drawGauge(GuiGraphics g, Rect r,
        Orientation orientation, GaugeFill fill, double ratio,
        java.util.Optional<Integer> groupColour, Reservation reservation) {
        boolean vertical = orientation == Orientation.VERTICAL;
        int length = vertical ? r.height() : r.width();
        shell(g, r, vertical, length, "body", 1,
            ElementDrawing.backgroundTint(0xffffff, groupColour));
        int inner = length - 2;
        int filled = Math.max(reservation.input() > 0 && ratio > 0 ? 1 : 0,
            (int) Math.round(inner * ratio));
        double freeRatio = Math.max(0, ratio - reservation.input());
        int free = reservation.input() > 0 && filled > 0
            ? Math.min(filled - 1, (int) Math.round(inner * freeRatio))
            : filled;
        int preview = reservation.output() > 0
            ? Math.min(inner,
                Math.max(free + 1,
                    (int) Math.round(
                        inner * Math.min(1, freeRatio + reservation.output()))))
            : free;
        // A tank can reserve both a withdrawal and a deposit in the same space.
        boolean split = free < filled && preview > free;
        band(g, r, vertical, fill, 0, free, 1, BandWidth.FULL);
        band(g, r, vertical, fill, free, filled, GHOST_OPACITY,
            split ? BandWidth.INPUT_HALF : BandWidth.FULL);
        if (preview > free)
            band(g, r, vertical, reservation.fill(), free, preview,
                GHOST_OPACITY, split ? BandWidth.OUTPUT_HALF : BandWidth.FULL);
        // Continue the same curvature above the fluid with a softer reflection.
        // Clip the two strengths separately so filled pixels keep their
        // lighting.
        if (filled < length - 2) {
            if (vertical)
                g.enableScissor(r.x(), r.y(), r.right(),
                    r.bottom() - 1 - filled);
            else
                g.enableScissor(r.x() + 1 + filled, r.y(), r.right(),
                    r.bottom());
            try {
                shell(g, r, vertical, length, "glass", .35f);
            } finally {
                g.disableScissor();
            }
        }
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

    private static void band(GuiGraphics g, Rect r, boolean vertical,
        GaugeFill fill, int from, int to, float opacity, BandWidth width) {
        if (from >= to)
            return;
        int length = vertical ? r.height() : r.width();
        var stack = fill.fluid().orElse(null);
        int color = fill.colour().orElseGet(() -> IClientFluidTypeExtensions
            .of(stack.getFluid()).getTintColor(stack));
        int alpha = stack == null ? 255 : color >>> 24;
        int tint = (Math.round(alpha * opacity) << 24) | (color & 0xffffff);
        for (int step = from; step < to; step++) {
            int row = length - 2 - step;
            int inset = row == 1 || row == length - 2 ? 2 : 1;
            int start = width == BandWidth.OUTPUT_HALF ? 9 : inset;
            int end = width == BandWidth.INPUT_HALF ? 9 : 18 - inset;
            Rect strip = vertical
                ? new Rect(r.x() + start, r.y() + row, end - start, 1)
                : new Rect(r.x() + 1 + step, r.y() + start, 1, end - start);
            if (stack != null)
                fluid(g, strip, r, stack, tint);
            else
                g.fill(strip.x(), strip.y(), strip.right(), strip.bottom(),
                    tint);
            if (vertical && step == to - 1)
                g.fill(strip.x(), strip.y(), strip.right(), strip.bottom(),
                    (Math.round(0x4d * opacity) << 24) | 0xffffff);
        }
    }

    private static void shell(GuiGraphics g, Rect r, boolean vertical,
        int length, String layer) {
        shell(g, r, vertical, length, layer, 1);
    }

    private static void shell(GuiGraphics g, Rect r, boolean vertical,
        int length, String layer, float alpha) {
        shell(g, r, vertical, length, layer, alpha, 0xffffff);
    }

    private static void shell(GuiGraphics g, Rect r, boolean vertical,
        int length, String layer, float alpha, int tint) {
        // GuiGraphics fills may be buffered, and flushing them can disable
        // blending.
        // Drain them before configuring the immediate texture draws (especially
        // translucent glass).
        g.flush();
        g.setColor((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f,
            (tint & 255) / 255f, alpha);
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
            g.setColor(1, 1, 1, 1);
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
