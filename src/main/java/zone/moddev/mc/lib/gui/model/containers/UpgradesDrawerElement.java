package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;
import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.LayoutContribution;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;

/**
 * A collapsible inventory dock; contents use ordinary slot binding and
 * synchronization.
 */
public class UpgradesDrawerElement
    implements SynchronizedElement, SelectiveContainer {
    public static final ElementSync<Boolean> OPEN = ElementSync
        .field(ResourceLocation.parse("mmdlib:upgrades_open"),
            ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast(), 1,
            element -> false)
        .affectsLayout().build();
    public static final ElementAction<Boolean> SET_OPEN = new ElementAction<>(
        ResourceLocation.parse("mmdlib:upgrades_open"),
        ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast(), 1,
        (context, open) -> {
            var session = context.menu().components()
                .require(BuiltinMenuComponents.UPGRADES);
            if (session.element() != context.element())
                return false;
            session.open(open);
            return true;
        });
    private final Common common;
    private final List<Element> children;

    public UpgradesDrawerElement(Common common, List<Element> children) {
        this.common = common;
        this.children = List.copyOf(children);
    }
    public Common common() {
        return common;
    }
    public List<Element> children() {
        return children;
    }
    public List<ElementSync<?>> synchronizedValues() {
        return List.of(OPEN);
    }
    public List<? extends ElementAction<?>> actions() {
        return List.of(SET_OPEN);
    }
    public boolean childVisible(Element child, ElementStateView state) {
        return state.get(this, OPEN);
    }
    public int[] measure(TextMeasure text) {
        return new int[]{26, 24};
    }

    public int[] bodySize(TextMeasure text) {
        var size = ElementLayouts.stack(children, 2, text);
        return new int[]{size[0] + 12, Math.max(24, size[1] + 12)};
    }
    /**
     * Uses the same panel anchor as client drawers, independent of tab order.
     */
    public Rect body(Rect panel, Rect tab, int bottom, TextMeasure text) {
        var size = bodySize(text);
        return SideDrawerLayout.topBody(panel, bottom,
            zone.moddev.mc.lib.gui.model.Edge.RIGHT, tab, size[0], size[1],
            SideDrawerLayout.HeightLimit.SCREEN);
    }
    public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
        // The contribution places children after all tabs and the panel exist.
        return ElementLayouts.stackPositions(children, bounds, 2, text);
    }
    @Override
    public java.util.Optional<LayoutContribution> layoutContribution(
        LayoutContribution.Context context) {
        return java.util.Optional.of(new LayoutContribution() {
            public void beforeOffsets(Panel panel) {
                var body = body(panel.bounds(),
                    panel.bounds(UpgradesDrawerElement.this), Integer.MAX_VALUE,
                    context.text());
                ElementLayouts
                    .stackPositions(children,
                        new Rect(body.x() + 6, body.y() + 6, body.width() - 12,
                            body.height() - 12),
                        2, context.text())
                    .forEach(panel::place);
            }
        });
    }
    public void validate() {
        SynchronizedElement.super.validate();
        if (common.id().isEmpty() || children.isEmpty())
            throw new IllegalArgumentException(
                "Upgrades drawer needs an id and content");
    }
    public void validateScreen(zone.moddev.mc.lib.gui.model.Screen screen) {
        if (screen.elements().stream()
            .filter(UpgradesDrawerElement.class::isInstance).count() != 1)
            throw new IllegalArgumentException(
                "Only one upgrades drawer is supported per screen");
    }
    public String toString() {
        return "UpgradesDrawer[" + common + "," + children + "]";
    }
}
