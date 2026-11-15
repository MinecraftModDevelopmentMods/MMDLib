package zone.moddev.mc.lib.gui.client.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.gui.layout.Rect;

/**
 * Viewer-independent recipe drawing. JEI supplies native ingredient slots;
 * drawers draw them here.
 */
public interface RecipePresentation {
    enum Role {
        INPUT, OUTPUT, CATALYST, RENDER_ONLY
    }

    record Item(int x, int y, ItemStack stack, Role role,
        List<Component> tooltip) {
        public Item {
            stack = stack.copy();
            tooltip = List.copyOf(tooltip);
        }
    }

    /**
     * Searchable fluids, including catalysts; their custom drawing belongs to
     * the background.
     */
    record Fluid(FluidStack stack, Role role) {
        public Fluid {
            stack = stack.copy();
        }
    }

    record Tooltip(Rect bounds, List<Component> lines) {
        public Tooltip {
            lines = List.copyOf(lines);
        }
    }

    int width();
    int height();
    List<Item> items();
    default List<Fluid> fluids() {
        return List.of();
    }
    default List<Tooltip> tooltips() {
        return List.of();
    }

    /** Coordinates are local to this presentation; excludes item rendering. */
    void drawBackground(GuiGraphics graphics);

    default void draw(GuiGraphics graphics) {
        drawBackground(graphics);
        for (var item : items()) {
            graphics.renderItem(item.stack(), item.x(), item.y());
            graphics.renderItemDecorations(Minecraft.getInstance().font,
                item.stack(), item.x(), item.y());
        }
    }

    default List<Component> tooltip(double x, double y) {
        for (var area : tooltips())
            if (area.bounds().contains(x, y))
                return area.lines();
        for (var item : items())
            if (new Rect(item.x(), item.y(), 16, 16).contains(x, y)) {
                var lines = new ArrayList<Component>();
                lines.add(item.stack().getHoverName());
                lines.addAll(item.tooltip());
                return List.copyOf(lines);
            }
        return List.of();
    }
}
