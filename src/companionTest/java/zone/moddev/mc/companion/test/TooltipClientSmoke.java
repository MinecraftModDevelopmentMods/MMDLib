package zone.moddev.mc.companion.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.client.DefaultElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementFragment;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.TooltipContext;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/**
 * Real leaf/ancestor composition plus existing occupied/empty native slot
 * regression checks.
 */
final class TooltipClientSmoke {
    record Leaf(Common common) implements Element {
        public int[] measure(TextMeasure text) {
            return new int[]{140, 14};
        }

        public Optional<ElementSnapshot> snapshot(ScreenHost host) {
            return Optional.of(new ElementSnapshot(7, 9));
        }

        public List<Component> tooltip(ScreenHost host, ItemStack carried) {
            return List.of(Component.literal("server leaf"));
        }
    }

    record Group(Common common, Element child, String label)
        implements Element {
        public List<Element> children() {
            return List.of(child);
        }

        public int[] measure(TextMeasure text) {
            return child.measure(text);
        }

        public Map<Element, Rect> childLayout(Rect r, TextMeasure text) {
            return Map.of(child, new Rect(0, 0, r.width(), r.height()));
        }

        public List<Component> tooltip(ScreenHost host, ItemStack carried) {
            return List.of(Component.literal("server " + label));
        }

        public void appendChildTooltip(TooltipContext c, List<Component> rows) {
            rows.addAll(c.serverLines());
            rows.add(Component
                .literal(
                    label + " for " + c.target().common().id().orElse("slot"))
                .withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean checked;
    private static boolean captureRequested, captured;

    static void render(MachineScreen screen,
        net.minecraft.client.gui.GuiGraphics graphics) {
        if (!captureRequested || captured)
            return;
        var leaf = screen.getMenu().bound.screen().find("tooltip_probe")
            .orElseThrow();
        var r = screen.bounds(leaf);
        screen.render(graphics, r.x() + 2, r.y() + 2, 0);
        graphics.flush();
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "tooltip-composition.png",
            mc.getMainRenderTarget(), ignored -> {
            });
        captured = true;
    }

    static void install() {
        ContributedHintClientSmoke.install();
        ElementRenderers.GLOBAL.register(Group.class, (e, c) -> {
        });
        ElementRenderers.GLOBAL.register(Leaf.class,
            (e, c) -> c.graphics().drawString(c.access().font(),
                "Hover for composed tooltip", c.bounds().x(), c.bounds().y(),
                0x404040, false));
        ElementBehaviors.GLOBAL.register(Group.class,
            (e, a) -> new DefaultElementBehavior(a) {
                public void appendChildTooltip(TooltipContext c,
                    List<Component> rows) {
                    rows.add(Component.literal("behavior " + e.label()));
                }
            });
        ElementBehaviors.GLOBAL.register(Leaf.class,
            (e, a) -> new DefaultElementBehavior(a) {
                public List<Component> tooltip(int x, int y) {
                    return List.of(Component.literal("behavior leaf"));
                }
            });
        var id = ResourceLocation.parse("mmdlib_companion:crusher");
        var old = Screens.get(id).orElseThrow().screen();
        var c = Common.empty();
        var common = new Common(Optional.of("tooltip_probe"), c.value(),
            c.max(),
            Optional.of(
                new Text("test.tooltip", Optional.of("Leaf {value}/{max}"))),
            c.tooltipShift(), c.clicks(), c.onHover(), c.hoverShows(),
            c.jeiRecipes(), 0, 0, 0);
        var body = new ArrayList<Element>();
        body.add(new Leaf(common));
        body.addAll(old.body());
        var panel = new BoxElement(Common.empty(), true, 0, 4, Align.START,
            body);
        var wrapper = new Group(Common.empty(),
            new Group(Common.empty(), panel, "inner"), "outer");
        var screen = new Screen(old.id(), old.source(), old.title(),
            old.blockEntity(), old.playerInventory(), List.of(wrapper),
            old.tabs());
        var screens = new HashMap<>(Screens.all());
        screens.put(id, ScreenBinder.bind(screen,
            TooltipClientSmoke.class.getClassLoader()));
        Screens.install(screens);
    }

    static boolean run(MachineScreen screen) {
        if (!ContributedHintClientSmoke.run(screen))
            return false;
        if (!checked) {
            var leaf = screen.getMenu().bound.screen().find("tooltip_probe")
                .orElseThrow();
            var r = screen.bounds(leaf);
            var rows = screen.tooltipLines(leaf, r.x() + 2, r.y() + 2).stream()
                .map(Component::getString).toList();
            if (!rows.contains("server outer"))
                return false;
            var expected = List.of("server leaf", "Leaf 7/9", "behavior leaf",
                "server inner", "inner for tooltip_probe", "behavior inner",
                "server outer", "outer for tooltip_probe", "behavior outer");
            if (!rows.equals(expected))
                throw new AssertionError("Tooltip composition: " + rows);
            if (!captured) {
                captureRequested = true;
                return false;
            }
            var fragment = new ElementFragment(
                new Group(Common.empty(), leaf, "fragment"), 140,
                new ElementFragment.Host() {
                    public ResourceLocation screenId() {
                        return screen.getMenu().bound.screen().id();
                    }

                    public Object value(Element element, boolean maximum) {
                        return maximum ? 9 : 7;
                    }
                });
            try {
                fragment.place(10, 20, new Rect(10, 20, 100, 14));
                var fragmentRows = fragment.tooltip(12, 22).stream()
                    .map(Component::getString).toList();
                if (!fragmentRows.equals(List.of("Leaf 7/9", "behavior leaf",
                    "fragment for tooltip_probe", "behavior fragment")))
                    throw new AssertionError(
                        "Fragment tooltip composition: " + fragmentRows);
                if (!fragment.tooltip(120, 22).isEmpty())
                    throw new AssertionError(
                        "Tooltip outside clipped fragment");
            } finally {
                fragment.removed();
            }
            checked = true;
        }
        return ReservationClientSmoke.run(screen);
    }
}
