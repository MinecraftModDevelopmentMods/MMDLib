package zone.moddev.mc.companion.test;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.model.elements.SideConfigElement;
import zone.moddev.mc.lib.gui.sync.ElementSync;

/** A plain Java element uses the same capture and acknowledged action path as custom widgets. */
final class ElementBehaviorClientSmoke {
    private static volatile int serverValue;
    private static int phase, drags, releases, accepted, cancelled;
    private static int enters, leaves;
    static final ElementSync<Integer> PROBE_STATE = ElementSync.field(
        ResourceLocation.parse("mmdlib_companion:probe_state"),
        StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
        5,
        element -> 0)
        .sample((source, previous) -> serverValue)
        .validate((element, value) -> value >= 0 && value <= 7)
        .build();
    private static int initialSideMode;
    static final ElementAction<Integer> PROBE = new ElementAction<>(
        ResourceLocation.parse("mmdlib_companion:behavior_probe"),
        StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
        5,
        (context, value) -> {
            if (value != 7)
                return false;
            serverValue = value;
            return true;
        });

    static void install() {
        ElementRendererClientSmoke.install();
        NavigationClientSmoke.install();
        ElementBehaviors.GLOBAL.register(
            ElementRendererClientSmoke.ModElement.class,
            (element, access) -> new ElementBehavior() {
                public void hoverChanged(boolean hovered) {
                    if (hovered)
                        enters++;
                    else
                        leaves++;
                }

                public Click mouseClicked(double x, double y, int button) {
                    return Click.CAPTURE;
                }

                public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
                    drags++;
                    return true;
                }

                public boolean mouseReleased(double x, double y, int button) {
                    releases++;
                    if (!access.submit(PROBE, 7))
                        throw new AssertionError("Plain element action was refused locally");
                    return true;
                }

                public void actionResult(boolean ok) {
                    if (!ok)
                        throw new AssertionError("Plain element action was refused on the server");
                    if (access.presentation().state().get(element, PROBE_STATE) != 7)
                        throw new AssertionError("Typed state was not applied before actionResult");
                    accepted++;
                }

                public void cancelInteraction() {
                    cancelled++;
                }

                public List<Component> tooltip(int x, int y) {
                    return List.of(Component.literal("Plain element behavior"));
                }
            });
    }

    static boolean run(MachineScreen screen) {
        var element = screen.getMenu().bound.screen().find("renderer_probe").orElseThrow();
        var bounds = screen.bounds(element);
        if (phase == 0) {
            screen.mouseMoved(-1, -1);
            enters = leaves = 0;
            screen.mouseMoved(bounds.x() + 2, bounds.y() + 2);
            screen.mouseMoved(bounds.x() + 3, bounds.y() + 3);
            if (enters != 1 || leaves != 0)
                throw new AssertionError("Screen hover must enter once");
            screen.mouseMoved(-1, -1);
            screen.mouseMoved(-1, -1);
            if (enters != 1 || leaves != 1)
                throw new AssertionError("Screen hover must leave once");
            if (screen.tooltipLines(element, bounds.x() + 2, bounds.y() + 2).stream()
                .noneMatch(line -> line.getString().equals("Plain element behavior")))
                throw new AssertionError("Plain element tooltip missing");
            if (!screen.mouseClicked(bounds.x() + 2, bounds.y() + 2, 0))
                throw new AssertionError("Plain element did not capture");
            screen.mouseDragged(bounds.right() + 100, bounds.bottom() + 100, 0, 100, 100);
            screen.mouseReleased(bounds.right() + 100, bounds.bottom() + 100, 0);
            phase++;
            return false;
        }
        if (phase == 1) {
            if (accepted == 0)
                return false;
            if (serverValue != 7 || drags != 1 || releases != 1)
                throw new AssertionError("Plain element capture/action did not complete");
            int previous = cancelled;
            screen.mouseClicked(bounds.x() + 2, bounds.y() + 2, 0);
            screen.resize(net.minecraft.client.Minecraft.getInstance(), screen.width,
                screen.height);
            screen.mouseReleased(bounds.x() + 2, bounds.y() + 2, 0);
            if (cancelled <= previous || releases != 1 || screen.getMenu().elementPending(element))
                throw new AssertionError("Resize committed a cancelled plain-element capture");
            phase++;
        }
        var sides = (SideConfigElement) screen.getMenu().bound.screen().find("renderer_sides")
            .orElseThrow();
        if (phase == 2) {
            initialSideMode = screen.getMenu().elementStates().get(sides, SideConfigElement.MODES)
                .getFirst();
            var face = sides.face(screen.bounds(sides), 0);
            screen.mouseClicked(face.x() + 2, face.y() + 2, 0);
            phase++;
            return false;
        }
        if (phase == 3) {
            if (screen.getMenu().elementPending(sides))
                return false;
            if (screen.getMenu().elementStates().get(sides, SideConfigElement.MODES)
                .getFirst() == initialSideMode)
                throw new AssertionError("Inline side mode did not synchronize");
            phase++;
        }
        return NavigationClientSmoke.run(screen) && CompanionWidgetSmoke.run(screen);
    }
}
