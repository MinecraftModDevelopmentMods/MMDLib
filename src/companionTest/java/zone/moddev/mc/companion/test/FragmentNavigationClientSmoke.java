package zone.moddev.mc.companion.test;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementFragment;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.layout.ElementFragmentLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Navigation;
import zone.moddev.mc.lib.gui.model.TooltipContext;
import zone.moddev.mc.lib.gui.model.ViewRef;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.ContextElement;
import zone.moddev.mc.lib.gui.model.containers.ViewElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementStates;

/** Stateful fragments use the same navigation, selection and input rules as machine screens. */
final class FragmentNavigationClientSmoke {
    private record Control(Common common) implements Element {
        public Optional<Navigation> navigation() {
            return Optional.of(new Navigation(new ViewRef("pages", "details")));
        }

        public int[] measure(TextMeasure text) {
            return new int[]{100, 18};
        }
    }

    private record Probe(Common common, int height) implements Element {
        public int[] measure(TextMeasure text) {
            return new int[]{100, height};
        }

        public void appendTooltip(TooltipContext context, List<Component> rows) {
            rows.add(Component.literal(common.id().orElseThrow()));
        }
    }

    private static Common named(String id) {
        var c = Common.empty();
        return new Common(
            Optional.of(id),
            c.value(),
            c.max(),
            c.tooltip(),
            c.tooltipShift(),
            c.clicks(),
            c.onHover(),
            c.hoverShows(),
            c.jeiRecipes(),
            0,
            0,
            0);
    }

    private static final Control control = new Control(named("navigate"));
    private static final Probe home = new Probe(named("home_probe"), 18),
        details = new Probe(named("details_probe"), 36);
    private static final ContextElement pages = new ContextElement(
        named("pages"),
        List.of(
            new ViewElement(named("home"), Optional.empty(), List.of(home)),
            new ViewElement(named("details"), Optional.empty(), List.of(details))));
    private static ElementFragment fragment;
    private static ElementStates state;
    private static int phase, submissions, cancellations, results, homeDraws, detailDraws, resizes;
    private static boolean focused, hidden;

    static void install() {
        ElementRenderers.GLOBAL.register(
            Control.class,
            (e, c) -> {
                var r = c.bounds();
                c.graphics().fill(r.x(), r.y(), r.right(), r.bottom(), 0xff406080);
                c.graphics().drawString(c.access().font(), "Switch view", r.x() + 2, r.y() + 4,
                    0xffffff);
            });
        ElementRenderers.GLOBAL.register(
            Probe.class,
            (e, c) -> {
                require(c.access().state() == state, "Renderer receives host state");
                if (e == home)
                    homeDraws++;
                else
                    detailDraws++;
                var r = c.bounds();
                c.graphics()
                    .fill(r.x(), r.y(), r.right(), r.bottom(), e == home ? 0xff408060 : 0xff805040);
                c.graphics()
                    .drawString(
                        c.access().font(), e.common().id().orElseThrow(), r.x() + 2, r.y() + 4,
                        0xffffff);
            });
        ElementBehaviors.GLOBAL.register(
            Probe.class,
            (e, access) -> new ElementBehavior() {
                public boolean focusable() {
                    return true;
                }

                public void setFocused(boolean value) {
                    focused = value;
                }

                public Click mouseClicked(double x, double y, int button) {
                    return Click.CAPTURE;
                }

                public void cancelInteraction() {
                    cancellations++;
                }

                public void resized() {
                    resizes++;
                }

                public void actionResult(boolean accepted) {
                    require(
                        !accepted && pages.selected(state).equals("home"),
                        "State applied before result");
                    results++;
                }
            });
    }

    static void render(GuiGraphics graphics) {
        if (fragment != null)
            fragment.render(graphics, -1, -1);
    }

    static boolean run() {
        if (phase == 0) {
            var root = new BoxElement(Common.empty(), true, 0, 4, Align.START,
                List.of(control, pages));
            state = new ElementStates(
                ElementFragmentLayout.of(root, 100, TextMeasure.NONE).elements(),
                Minecraft.getInstance().level.registryAccess(),
                () -> {
                });
            fragment = new ElementFragment(
                root,
                100,
                new ElementFragment.Host() {
                    public ResourceLocation screenId() {
                        return ResourceLocation.parse("mmdlib_companion:fragment_navigation");
                    }

                    public ElementStateView state() {
                        return state;
                    }

                    public boolean visible(Element e) {
                        return e != pages || !hidden;
                    }

                    public void navigate(String target, String selection) {
                        require(target.equals("pages"), "Host receives navigation target");
                        pages.select(state, selection);
                    }

                    public <T> boolean submit(Element e, ElementAction<T> action, T value) {
                        require(
                            e == control && action == Navigation.NAVIGATE,
                            "Typed fragment navigation");
                        submissions++;
                        return true;
                    }
                });
            fragment.place(5, 5, new Rect(5, 5, 100, 100));
            phase++;
            return false;
        }
        if (phase == 1) {
            if (homeDraws == 0 && detailDraws == 0)
                return false;
            require(homeDraws > 0 && detailDraws == 0, "Only selected view renders");
            int initialHeight = fragment.height();
            var r = fragment.bounds(home);
            fragment.click(r.x() + 2, r.y() + 2, 0);
            int before = cancellations, beforeResize = resizes;
            fragment.refresh();
            fragment.tick();
            fragment.height();
            require(
                cancellations == before && resizes == beforeResize && focused,
                "Unchanged layout preserves capture and focus");
            pages.select(state, "details");
            fragment.refresh();
            require(
                cancellations > before && !focused,
                "Hiding captured view cancels interaction and focus");
            require(fragment.height() > initialHeight, "Selection remeasures fragment");
            require(
                fragment.bounds(details).y() > fragment.bounds(pages).y(),
                "Layout preparation reserves context header");
            r = fragment.bounds(details);
            require(
                fragment.tooltip(r.x() + 2, r.y() + 2).stream()
                    .anyMatch(row -> row.getString().equals("details_probe")),
                "Selected child owns tooltip");
            hidden = true;
            require(
                fragment.tooltip(r.x() + 2, r.y() + 2).isEmpty()
                    && !fragment.click(r.x() + 2, r.y() + 2, 0),
                "Host-hidden ancestor hides descendant input and tooltip");
            hidden = false;
            pages.select(state, "home");
            fragment.actionResult(home, false);
            require(results == 1, "Fragment forwards action results");
            r = fragment.bounds(control);
            fragment.click(r.x() + 2, r.y() + 2, 0);
            fragment.refresh();
            require(
                submissions == 1 && pages.selected(state).equals("details"),
                "Generic control predicts via host navigation");
            homeDraws = detailDraws = 0;
            phase++;
            return false;
        }
        if (phase == 2) {
            if (homeDraws == 0 && detailDraws == 0)
                return false;
            require(homeDraws == 0 && detailDraws > 0, "Navigation changes rendered selection");
            var mc = Minecraft.getInstance();
            Screenshot.grab(
                mc.gameDirectory, "fragment-navigation.png", mc.getMainRenderTarget(), message -> {
                });
            pages.select(state, "home");
            fragment.actionResult(control, false);
            require(fragment.height() < 70, "Authoritative correction remeasures layout");
            fragment.removed();
            fragment = null;
            phase++;
        }
        return true;
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
