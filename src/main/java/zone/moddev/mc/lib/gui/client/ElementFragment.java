package zone.moddev.mc.lib.gui.client;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.layout.ElementFragmentLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * Hosts an ordinary element tree in a clipped viewport using the shared rendering/input registries.
 */
public final class ElementFragment {
    public interface Host {
        ResourceLocation screenId();

        /** Original XML source when available; Java-built fragments default to the host ID. */
        default String source(Element element) {
            return screenId().toString();
        }

        default Object value(Element element, boolean maximum) {
            return null;
        }

        /**
         * Current synchronized or local state, also used for layout and selective child visibility.
         */
        default ElementStateView state() {
            return ElementStateView.DEFAULTS;
        }

        /** Host-owned navigation/prediction; the fragment does not mutate server storage. */
        default void navigate(String context, String selection) {
        }

        /** Additional host policy, composed with every ancestor's visibility. */
        default boolean visible(Element element) {
            return true;
        }

        /** Interaction geometry independent of layout/render bounds, in screen coordinates. */
        default Rect hitBounds(Element element, Rect frame) {
            return frame;
        }

        /** Host-level input lock, independent of display visibility and tooltip composition. */
        default boolean inputEnabled() {
            return true;
        }

        default boolean pending(Element element) {
            return false;
        }

        default <T> boolean submit(Element element, ElementAction<T> action, T value) {
            return false;
        }

        default <T> Optional<T> service(Class<T> type) {
            return Optional.empty();
        }

        /** Optional host-side notification, also used to bridge declarative callbacks. */
        default void hoverChanged(Element element, boolean entered) {
        }

        /** Detached native-slot presentation supplied by the owning menu. */
        default ItemStack contents(SlotElement element) {
            return ItemStack.EMPTY;
        }

        default ItemStack hint(SlotElement element) {
            return ItemStack.EMPTY;
        }

        default SlotPlan reservation(SlotElement element) {
            return SlotPlan.NONE;
        }

        default List<Component> serverTooltip(Element element) {
            return List.of();
        }

        default ItemStack carried() {
            return ItemStack.EMPTY;
        }
    }

    private ElementFragmentLayout layout;
    private final Element root;
    private final int width;
    private Map<Element, Boolean> shown;
    private boolean refreshing, initialized;
    private final Host host;
    private final ElementInput<Element, ElementBehavior> input;
    private Rect viewport = new Rect(0, 0, 0, 0);
    private int x, y;
    private final long started = System.nanoTime();
    private final ElementRenderAccess presentation = new ElementRenderAccess() {
        public Font font() {
            return Minecraft.getInstance().font;
        }

        public Rect viewport() {
            return viewport;
        }

        public Rect bounds(Element element) {
            return ElementFragment.this.bounds(element);
        }

        public boolean visible(Element element) {
            return ElementFragment.this.visible(element);
        }

        public ElementStateView state() {
            return host.state();
        }

        public Object value(Element element, boolean maximum) {
            return host.value(element, maximum);
        }

        public ItemStack contents(SlotElement element) {
            return host.contents(element).copy();
        }

        public ItemStack hint(SlotElement element) {
            return host.hint(element).copy();
        }

        public List<Component> serverTooltip(Element element) {
            return host.serverTooltip(element).stream()
                .map(Component::copy)
                .map(Component.class::cast)
                .toList();
        }

        public Optional<Element> find(String id) {
            return layout.elements().stream()
                .filter(e -> e.common().id().filter(id::equals).isPresent())
                .findFirst();
        }

        public Optional<Element> parent(Element child) {
            return layout.elements().stream()
                .filter(e -> e.children().stream().anyMatch(c -> c == child))
                .findFirst();
        }

        public <T> Optional<T> capability(Element element, Class<T> type) {
            return BehaviorCapabilities.find(input.instance(element), type);
        }
    };

    public ElementFragment(Element root, int width, Host host) {
        this.host = Objects.requireNonNull(host);
        this.root = Objects.requireNonNull(root);
        this.width = width;
        layout = measure();
        shown = layout.visibility(host.state(), host::visible);
        ElementRenderers.GLOBAL.validate(layout.elements(), host::source);
        input = new ElementInput<>(
            layout.elements(),
            e -> ElementBehaviors.GLOBAL.create(access(e)),
            this::visible,
            e -> host.hitBounds(e, bounds(e)),
            (px, py) -> viewport.contains(px, py),
            host::hoverChanged);
    }

    private ElementFragmentLayout measure() {
        return ElementFragmentLayout.of(
            root,
            width,
            text -> Minecraft.getInstance().font.width(ScreenText.component(text)),
            host.state());
    }

    /** Reconcile host state with layout and visibility without recreating element behaviors. */
    public void refresh() {
        if (refreshing)
            return;
        refreshing = true;
        try {
            var next = measure();
            if (next.elements().size() != layout.elements().size())
                throw new IllegalStateException(
                    "Replace the fragment when its element tree changes");
            for (int i = 0; i < next.elements().size(); i++)
                if (next.elements().get(i) != layout.elements().get(i))
                    throw new IllegalStateException(
                        "Replace the fragment when its element tree changes");
            boolean resized = next.elements().stream()
                .anyMatch(
                    element -> !next.bounds().get(element).equals(layout.bounds().get(element)));
            layout = next;
            shown = layout.visibility(host.state(), host::visible);
            input.update();
            if (!host.inputEnabled())
                input.cancelInteraction();
            if (resized)
                input.resized();
        } finally {
            refreshing = false;
        }
    }

    /** Deliver completion after the host has applied authoritative values/state. */
    public void actionResult(Element element, boolean accepted) {
        refresh();
        var behavior = input.instance(element);
        if (behavior == null)
            throw new IllegalArgumentException("Element is not in this fragment");
        behavior.actionResult(accepted);
    }

    private ElementAccess access(Element element) {
        return new ElementAccess() {
            public Element element() {
                return element;
            }

            public Rect bounds() {
                return ElementFragment.this.bounds(element);
            }

            public Object value() {
                return host.value(element, false);
            }

            public Object maximum() {
                return host.value(element, true);
            }

            public ElementRenderAccess presentation() {
                return presentation;
            }

            public List<Element> elements() {
                return layout.elements();
            }

            public ResourceLocation screenId() {
                return host.screenId();
            }

            public boolean pending(Element e) {
                return host.pending(e);
            }

            public <T> boolean submit(Element e, ElementAction<T> action, T value) {
                return visible(e)
                    && e.action(action.id()).orElse(null) == action
                    && !host.pending(e)
                    && host.submit(e, action, value);
            }

            public <T> Optional<T> service(Class<T> type) {
                return host.service(type);
            }

            public void navigate(String context, String view) {
                host.navigate(context, view);
            }

            public ItemStack carried() {
                return host.carried().copy();
            }

            public SlotPlan reservation(SlotElement slot) {
                return host.reservation(slot);
            }

            public boolean showRecipes() {
                return false;
            }
        };
    }

    public int height() {
        refresh();
        return layout.height();
    }

    public Rect bounds(Element element) {
        return layout.bounds().get(element).offset(x, y);
    }

    private boolean visible(Element element) {
        if (!Boolean.TRUE.equals(shown.get(element)))
            return false;
        var r = bounds(element);
        return viewport.width() > 0
            && viewport.height() > 0
            && r.right() > viewport.x()
            && r.x() < viewport.right()
            && r.bottom() > viewport.y()
            && r.y() < viewport.bottom();
    }

    public void place(int x, int y, Rect viewport) {
        if (this.x != x || this.y != y || !this.viewport.equals(viewport))
            input.cancelInteraction();
        this.x = x;
        this.y = y;
        this.viewport = viewport;
        refresh();
        if (!initialized) {
            initialized = true;
            input.initialized();
            refresh();
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        refresh();
        input.hover(mouseX, mouseY);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            for (var pass : ElementRenderers.Pass.values()) {
                if (pass == ElementRenderers.Pass.FOREGROUND)
                    ElementHover.preview(
                        input.hovered(), presentation,
                        e -> renderPreview(graphics, e, mouseX, mouseY));
                for (var e : layout.elements())
                    if (visible(e)) {
                        ElementRenderers.GLOBAL.render(
                            pass,
                            new ElementRenderers.Context(
                                graphics,
                                e,
                                bounds(e),
                                host.value(e, false),
                                host.value(e, true),
                                viewport.contains(mouseX, mouseY) ? mouseX : Integer.MIN_VALUE,
                                viewport.contains(mouseX, mouseY) ? mouseY : Integer.MIN_VALUE,
                                0,
                                (System.nanoTime() - started) / 50000000d,
                                presentation));
                    }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderPreview(GuiGraphics graphics, Element element, int mouseX, int mouseY) {
        ElementRenderers.GLOBAL.render(
            new ElementRenderers.Context(
                graphics,
                element,
                bounds(element),
                host.value(element, false),
                host.value(element, true),
                mouseX,
                mouseY,
                0,
                (System.nanoTime() - started) / 50000000d,
                presentation));
    }

    public void mouseMoved(double x, double y) {
        refresh();
        if (host.inputEnabled())
            input.mouseMoved(x, y);
    }

    public Element hit(double x, double y) {
        refresh();
        return input.hit(x, y);
    }

    public boolean keyReleased(int key, int scan, int modifiers) {
        refresh();
        return host.inputEnabled() && input.keyReleased(key, scan, modifiers);
    }

    public Element hovered(int x, int y) {
        refresh();
        input.hover(x, y);
        return input.hovered();
    }

    public List<Component> tooltip(int x, int y) {
        var hovered = hovered(x, y);
        return hovered == null ? List.of() : tooltip(hovered, x, y);
    }

    /** Compose a registered fragment element selected by an external native hit-test. */
    public List<Component> tooltip(Element element, int x, int y) {
        refresh();
        if (!layout.bounds().containsKey(element) || !viewport.contains(x, y))
            return List.of();
        return ElementTooltips.collect(
            element,
            presentation,
            input::instance,
            x,
            y,
            net.minecraft.client.gui.screens.Screen.hasShiftDown(),
            net.minecraft.client.gui.screens.Screen.hasControlDown(),
            net.minecraft.client.gui.screens.Screen.hasAltDown());
    }

    public boolean click(double x, double y, int button) {
        refresh();
        return host.inputEnabled() && input.click(x, y, button);
    }

    public boolean key(int key, int scan, int modifiers) {
        refresh();
        return host.inputEnabled() && input.keyPressed(key, scan, modifiers);
    }

    public boolean character(char c, int modifiers) {
        refresh();
        return host.inputEnabled() && input.charTyped(c, modifiers);
    }

    public boolean drag(double x, double y, int button, double dx, double dy) {
        refresh();
        return host.inputEnabled() && input.drag(x, y, button, dx, dy);
    }

    public boolean release(double x, double y, int button) {
        refresh();
        return host.inputEnabled() && input.release(x, y, button);
    }

    public boolean scroll(double x, double y, double amount) {
        refresh();
        return host.inputEnabled() && input.scroll(x, y, 0, amount);
    }

    public void tick() {
        refresh();
        if (host.inputEnabled())
            input.tick();
    }

    public void cancelInteraction() {
        input.cancelInteraction();
    }

    public void removed() {
        input.removed();
    }
}
