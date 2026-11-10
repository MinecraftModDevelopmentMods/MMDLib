package zone.moddev.mc.lib.gui.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/**
 * Adapts native slot drawing and hit bounds to the owning slot element's
 * presentation.
 */
public final class NativeSlotRenderer {
    private final ReservationOverlay reservations = new ReservationOverlay();

    private boolean ownsTooltip(MenuSlots slots, Slot hovered,
        Element element) {
        if (hovered == null || element == null)
            return false;
        var entry = slots.entry(hovered.index);
        return entry != null && entry.slot() == hovered
            && entry.element() == element;
    }

    /** Filled native slots append element lines to vanilla's item tooltip. */
    public boolean usesItemTooltip(MenuSlots slots, Slot hovered,
        Element element) {
        return ownsTooltip(slots, hovered, element) && hovered.hasItem();
    }

    /**
     * Merge only the element belonging to the hovered native slot, leaving
     * other tooltips alone.
     */
    public List<Component> itemTooltip(MenuSlots slots, Slot hovered,
        Element element, List<Component> vanilla,
        Function<Element, List<Component>> additional) {
        if (!ownsTooltip(slots, hovered, element))
            return vanilla;
        var slotElement = slots.entry(hovered.index).element();
        return itemTooltip(slotElement, hovered.getItem(), vanilla,
            additional.apply(element));
    }

    /**
     * Shared composition for native adapters and virtual cells; parents survive
     * overrides.
     */
    public static List<Component> itemTooltip(
        zone.moddev.mc.lib.gui.model.elements.SlotElement element,
        ItemStack stack, List<Component> vanilla, List<Component> additional) {
        var input = vanilla.stream().map(Component::copy)
            .map(Component.class::cast).toList();
        var lines = new ArrayList<>(element.nativeTooltip(stack.copy(), input));
        lines.addAll(additional);
        return lines.stream().map(Component::copy).map(Component.class::cast)
            .toList();
    }

    private static Rect vanillaBounds(Slot slot) {
        return new Rect(slot.x, slot.y, 16, 16);
    }

    public void render(GuiGraphics graphics, Slot slot, MenuSlots.Entry entry,
        Runnable vanilla) {
        var element = entry == null ? null : entry.element();
        if (element != null && !element.renderNative())
            return;
        var original = vanillaBounds(slot);
        var bounds = element == null
            ? original
            : element.nativeBounds(original);
        if (bounds.equals(original)) {
            vanilla.run();
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(bounds.x(), bounds.y(), 0);
            graphics.pose().scale(bounds.width() / 16f, bounds.height() / 16f,
                1);
            graphics.pose().translate(-slot.x, -slot.y, 0);
            vanilla.run();
        } finally {
            graphics.pose().popPose();
        }
    }

    public void highlight(GuiGraphics graphics, Slot slot,
        MenuSlots.Entry entry, int colour, Runnable vanilla) {
        var original = vanillaBounds(slot);
        var bounds = entry == null || entry.element() == null
            ? original
            : entry.element().highlightBounds(original);
        if (bounds.equals(original)) {
            vanilla.run();
            return;
        }
        if (slot.isHighlightable())
            graphics.fillGradient(RenderType.guiOverlay(), bounds.x(),
                bounds.y(), bounds.right(), bounds.bottom(), colour, colour, 0);
    }

    public void contents(GuiGraphics graphics, Font font, Slot slot,
        MenuSlots.Entry entry, ItemStack displayed, String count,
        Consumer<String> vanilla) {
        if (entry == null || !entry.overlay() || entry.element() == null) {
            vanilla.accept(count);
            return;
        }
        vanilla.accept("");
        reservations.render(graphics, font, slot, entry.element(), displayed,
            entry.plan(), count);
    }

    /**
     * First active native hit, using vanilla's one-pixel tolerance and menu
     * ordering.
     */
    public Slot hit(MenuSlots slots, double x, double y) {
        return hit(slots, x, y, true);
    }

    /**
     * Geometry-only hits are for blocking pending gestures, never authorizing
     * interaction.
     */
    public Slot hit(MenuSlots slots, double x, double y, boolean activeOnly) {
        for (var entry : slots.entries()) {
            if (activeOnly && !entry.slot().isActive())
                continue;
            var bounds = hitBounds(entry);
            if (x >= bounds.x() - 1 && x < bounds.right() + 1
                && y >= bounds.y() - 1 && y < bounds.bottom() + 1)
                return entry.slot();
        }
        return null;
    }

    public Rect hitBounds(MenuSlots.Entry entry) {
        var bounds = vanillaBounds(entry.slot());
        return entry.element() == null
            ? bounds
            : entry.element().hitBounds(bounds);
    }

    public Rect hitBounds(MenuSlots slots, int x, int y, int width,
        int height) {
        if (width == 16 && height == 16)
            for (var entry : slots.entries())
                if (entry.slot().isActive() && entry.slot().x == x
                    && entry.slot().y == y)
                    return hitBounds(entry);
        return new Rect(x, y, width, height);
    }
}
