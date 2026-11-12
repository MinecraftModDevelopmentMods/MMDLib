package zone.moddev.mc.lib.gui.layout;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

/**
 * Composes measured element trees, registered drawer tabs and element-owned
 * layout contributions. Coordinates are GUI pixels relative to the main panel's
 * top-left corner. Elements own their measurement and child placement;
 * contributions own footer and associated-root placement.
 */
public final class ScreenLayout {
    /** Margin around the panel's content, as vanilla screens. */
    public static final int PANEL_MARGIN = 7;

    /**
     * Gap between stacked top-level elements, and before the player inventory.
     */
    public static final int GAP = 4;

    private final Map<Element, Rect> bounds = new IdentityHashMap<>();
    private final LayoutContext text;
    private List<DrawerDefinition> drawers = List.of();

    public List<DrawerDefinition> drawers() {
        return drawers;
    }

    private Rect panel;
    private List<LayoutContribution> contributions = List.of();
    private final Map<LayoutContribution, Rect> footers = new IdentityHashMap<>();
    private final Map<ResourceLocation, Rect> regions = new HashMap<>();

    private ScreenLayout(LayoutContext text) {
        this.text = text;
    }

    /**
     * Lays out {@code screen}, measuring text with {@code text}.
     *
     * @throws IllegalArgumentException
     *             if the tabs on one edge do not fit the panel's height
     */
    public static ScreenLayout of(Screen screen, TextMeasure text) {
        return of(screen, text, false);
    }

    /**
     * Places the inventory toolbar beside the helmet, directly above the
     * storage slots.
     */
    public static ScreenLayout of(Screen screen, TextMeasure text,
        boolean automation) {
        return of(screen, text, automation, Map.of());
    }

    /**
     * Measures selected context views; omitted selections use each context's
     * default view.
     */
    public static ScreenLayout of(Screen screen, TextMeasure text,
        boolean automation, Map<String, String> views) {
        return of(screen, text, automation, false, views);
    }

    /**
     * Includes runtime drawer tabs when sizing the panel and placing its XML
     * tabs.
     */
    public static ScreenLayout of(Screen screen, TextMeasure text,
        boolean automation, boolean redstone, Map<String, String> views) {
        var context = new LayoutContext(text, views);
        return arrange(screen, context, DrawerRegistry.GLOBAL.resolve(
            DrawerRegistry.legacy(screen, context, automation, redstone)));
    }

    /**
     * Layout consumes typed state without knowing which elements declare it.
     */
    public static ScreenLayout of(Screen screen, TextMeasure text,
        boolean automation, boolean redstone,
        zone.moddev.mc.lib.gui.sync.ElementStateView state) {
        var context = new LayoutContext(text, state);
        return arrange(screen, context, DrawerRegistry.GLOBAL.resolve(
            DrawerRegistry.legacy(screen, context, automation, redstone)));
    }

    public static ScreenLayout of(Screen screen, TextMeasure text,
        zone.moddev.mc.lib.gui.runtime.ScreenHost host,
        zone.moddev.mc.lib.gui.sync.ElementStateView state) {
        var context = new LayoutContext(text, state);
        return arrange(screen, context, DrawerRegistry.GLOBAL
            .resolve(new DrawerRegistry.Context(screen, context, host)));
    }

    public static ScreenLayout of(Screen screen, TextMeasure text,
        List<DrawerDefinition> drawers,
        zone.moddev.mc.lib.gui.sync.ElementStateView state) {
        return arrange(screen, new LayoutContext(text, state),
            List.copyOf(drawers));
    }

    private static ScreenLayout arrange(Screen screen, LayoutContext text,
        List<DrawerDefinition> drawers) {
        var layout = new ScreenLayout(text);
        layout.drawers = drawers;
        var context = new LayoutContribution.Context(screen, text, drawers);
        layout.contributions = screen.elements().stream()
            .flatMap(element -> element.layoutContribution(context).stream())
            .toList();
        layout.place(screen);
        var placement = new LayoutContribution.Panel() {
            public Rect bounds(Element element) {
                return layout.bounds(element);
            }

            public void place(Element element, Rect r) {
                layout.placeAt(element, r.x(), r.y(), r.width(), r.height());
            }

            public void shift(Element element, int dx, int dy) {
                layout.shift(element, dx, dy);
            }

            public void shiftBelow(int y, int amount) {
                for (var element : screen.body())
                    layout.shiftBelow(element, y, amount);
                for (var element : screen.implicitElements())
                    layout.shiftBelow(element, y, amount);
                layout.footers.replaceAll((plan,
                    rect) -> rect.y() >= y ? rect.offset(0, amount) : rect);
            }

            public void grow(int amount) {
                layout.panel = new Rect(0, 0, layout.panel.width(),
                    layout.panel.height() + amount);
            }
        };
        for (var plan : layout.contributions) {
            var footer = layout.footers.remove(plan);
            if (footer != null)
                plan.footer(footer);
            plan.finish(placement);
        }
        layout.placeTabs();
        layout.contributions.forEach(plan -> plan.beforeOffsets(placement));
        layout.applyOffsets(screen);
        layout.contributions.forEach(plan -> plan.resolveOffsets(placement));
        for (var plan : layout.contributions)
            plan.regions().forEach((id, rect) -> {
                if (layout.regions.putIfAbsent(id, rect) != null)
                    throw new IllegalArgumentException(
                        "Duplicate layout region: " + id);
            });
        return layout;
    }

    private void shiftBelow(Element element, int start, int dy) {
        var rect = bounds.get(element);
        if (rect != null && rect.y() >= start)
            bounds.put(element, rect.offset(0, dy));
        for (var child : element.children())
            shiftBelow(child, start, dy);
    }

    /** The main panel, at (0, 0). */
    public Rect panel() {
        return panel;
    }

    /** A final region published by an element's layout contribution. */
    public Optional<Rect> region(ResourceLocation id) {
        return Optional.ofNullable(regions.get(id));
    }

    /** Where {@code element} is drawn. */
    public Rect bounds(Element element) {
        var rect = bounds.get(element);
        if (rect == null)
            throw new IllegalArgumentException(
                "Not on this screen: " + element);
        return rect;
    }

    private int[] size(Element element) {
        return ElementLayouts.size(element, text);
    }

    private int[] stack(List<Element> children, int gap) {
        return ElementLayouts.stack(children, gap, text);
    }

    private void place(Screen screen) {
        screen.forEach(element -> element.prepareLayout(text));
        var claimed = java.util.Collections
            .newSetFromMap(new IdentityHashMap<Element, Boolean>());
        for (var plan : contributions)
            for (var root : plan.claimedRoots()) {
                if (screen.body().stream()
                    .noneMatch(element -> element == root))
                    throw new IllegalArgumentException(
                        "Layout contribution must claim a body root");
                if (!claimed.add(root))
                    throw new IllegalArgumentException(
                        "Body root claimed twice");
            }
        var bodyElements = screen.body().stream()
            .filter(element -> !claimed.contains(element)).toList();
        int[] body = stack(bodyElements, GAP);
        var footers = contributions.stream().map(LayoutContribution::footerSize)
            .toList();
        int width = Math.max(body[0],
            text.width(screen.title()) + 2 * PANEL_MARGIN);
        int titleHeight = PANEL_MARGIN + TextMeasure.LINE_HEIGHT + GAP;
        int height = body[1] + titleHeight;
        for (var footer : footers)
            if (footer.isPresent()) {
                width = Math.max(width,
                    footer.get().width() + 2 * PANEL_MARGIN);
                height += GAP + footer.get().height();
            }
        if (footers.stream().anyMatch(Optional::isPresent))
            height += PANEL_MARGIN;
        int y = titleHeight;
        for (var element : bodyElements) {
            int[] size = size(element);
            // Top-level boxes and context areas span the panel, as a vbox's
            // children do.
            int w = element.stretchToPanel() ? width : size[0];
            placeAt(element, 0, y, w, size[1]);
            y += size[1] + GAP;
        }
        panel = new Rect(0, 0, width, height);
        int footerY = body[1] + titleHeight;
        for (int i = 0; i < contributions.size(); i++) {
            var footer = footers.get(i);
            if (footer.isEmpty())
                continue;
            var size = footer.get();
            footerY += GAP;
            this.footers.put(contributions.get(i),
                new Rect((width - size.width()) / 2, footerY, size.width(),
                    size.height()));
            footerY += size.height();
        }
        if (drawers.stream().anyMatch(d -> d.element().isEmpty()))
            panel = new Rect(0, 0, width,
                Math.max(height, DrawerTabs.minimumHeight(drawers)));
    }

    private void placeAt(Element element, int x, int y, int width, int height) {
        bounds.put(element, new Rect(x, y, width, height));
        element.childLayout(new Rect(0, 0, width, height), text)
            .forEach((child, rect) -> placeAt(child, x + rect.x(), y + rect.y(),
                rect.width(), rect.height()));
    }

    private void placeTabs() {
        var tabs = DrawerTabs.place(drawers, panel, panel.bottom(),
            definition -> true);
        tabs.forEach((definition, rect) -> {
            if (rect.bottom() > panel.height())
                throw new IllegalArgumentException("The tabs on the "
                    + definition.placement().edge().name()
                        .toLowerCase(java.util.Locale.ROOT)
                    + " edge need more panel height");
            definition.element().ifPresent(element -> placeAt(element, rect.x(),
                rect.y(), rect.width(), rect.height()));
        });
    }

    /** Nudges each offset element together with everything inside it. */
    private void applyOffsets(Screen screen) {
        for (var element : screen.elements()) {
            var c = element.common();
            if (c.offsetX() != 0 || c.offsetY() != 0)
                shift(element, c.offsetX(), c.offsetY());
        }
    }

    private void shift(Element element, int dx, int dy) {
        bounds.put(element, bounds.get(element).offset(dx, dy));
        for (var child : element.children())
            shift(child, dx, dy);
    }
}
