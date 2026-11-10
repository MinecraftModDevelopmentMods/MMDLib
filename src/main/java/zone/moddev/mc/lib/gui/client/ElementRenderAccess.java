package zone.moddev.mc.lib.gui.client;

import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;

/** Read-only client presentation services. No machine, menu, packets or storage are exposed. */
public interface ElementRenderAccess {
    Font font();

    Rect viewport();

    default Component text(Text text) {
        return ScreenText.component(text);
    }

    default Optional<Element> find(String id) {
        return Optional.empty();
    }

    default Optional<Element> parent(Element element) {
        return Optional.empty();
    }

    default Object value(Element element, boolean maximum) {
        return null;
    }

    /** Synchronized server-authored tooltip rows; no live host sampling on the client. */
    default java.util.List<Component> serverTooltip(Element element) {
        return java.util.List.of();
    }

    default Rect bounds(Element element) {
        throw new IllegalArgumentException("No bounds for element");
    }

    default boolean visible(Element element) {
        return false;
    }

    default ElementStateView state() {
        return ElementStateView.DEFAULTS;
    }

    default <T> Optional<T> capability(Element element, Class<T> type) {
        return Optional.empty();
    }

    /** A registered group caption, or the ordinary tooltip for elements without that capability. */
    default Optional<Text> groupLabel(Element element) {
        return capability(element, GroupLabelProvider.class)
            .map(GroupLabelProvider::groupLabel)
            .orElseGet(() -> element.common().tooltip());
    }

    /** Draw an element-owned highlight, falling back to an outline; hidden targets are ignored. */
    default void highlight(GuiGraphics graphics, Element element, ElementHighlight request) {
        if (!visible(element))
            return;
        var clip = viewport();
        graphics.pose().pushPose();
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            capability(element, HighlightPresentation.class)
                .orElse(HighlightPresentation.OUTLINE)
                .highlight(graphics, bounds(element), request);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            graphics.disableScissor();
            graphics.pose().popPose();
        }
    }

    /** Returned stacks are display copies, never mutable menu storage. */
    default ItemStack contents(SlotElement element) {
        return ItemStack.EMPTY;
    }

    default ItemStack hint(SlotElement element) {
        return ItemStack.EMPTY;
    }

    default <T> Optional<T> drawerPresentation(Element element, Class<T> type) {
        return Optional.empty();
    }
}
