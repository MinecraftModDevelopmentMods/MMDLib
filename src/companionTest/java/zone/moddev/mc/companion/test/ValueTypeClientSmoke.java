package zone.moddev.mc.companion.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.ValueRef;
import zone.moddev.mc.lib.gui.runtime.ScreenValues;

/**
 * Custom getter snapshots traverse real delta packets and accepted/rejected
 * action replies.
 */
final class ValueTypeClientSmoke {
    private static int phase, drawn, accepted, rejected;
    private static ValueTypeFixture.Reading first;

    static void install() {
        ElementRenderers.GLOBAL.register(ValueTypeFixture.Display.class,
            (e, c) -> {
                var value = (ValueTypeFixture.Reading) c.access().value(e,
                    false);
                if (value != null)
                    drawn++;
                c.graphics().drawString(c.access().font(),
                    "Mod reading: " + (value == null ? "--" : value.amount),
                    c.bounds().x(), c.bounds().y(), 0xffffff);
            });
        ElementBehaviors.GLOBAL.register(ValueTypeFixture.Display.class,
            (e, access) -> new ElementBehavior() {
                public void actionResult(boolean ok) {
                    var value = (ValueTypeFixture.Reading) access.value();
                    require(value != null && value.amount == 7,
                        "Action result did not restore the custom value before callbacks");
                    if (ok)
                        accepted++;
                    else
                        rejected++;
                }
            });
        var id = ResourceLocation.parse("mmdlib_companion:crusher");
        var old = Screens.get(id).orElseThrow().screen();
        var c = Common.empty();
        var binding = new Common(Optional.of("mod_reading"),
            Optional.of(
                ValueRef.parse(ValueTypeFixture.class.getName() + "#reading")),
            c.max(), c.tooltip(), c.tooltipShift(), c.clicks(), c.onHover(),
            c.hoverShows(), c.jeiRecipes(), 0, 0, 0);
        var body = new ArrayList<>(old.body());
        body.add(new ValueTypeFixture.Display(binding));
        var screen = new Screen(old.id(), old.source(), old.title(),
            old.blockEntity(), old.playerInventory(), body, old.tabs());
        var screens = new HashMap<>(Screens.all());
        screens.put(id, ScreenBinder.bind(screen,
            ValueTypeClientSmoke.class.getClassLoader()));
        Screens.install(screens);
    }

    static boolean run(MachineScreen screen) {
        var menu = screen.getMenu();
        var element = menu.bound.screen().find("mod_reading").orElseThrow();
        var value = (ValueTypeFixture.Reading) menu.value(element, false);
        if (menu.elementPending(element))
            return false;
        switch (phase) {
            case 0 -> {
                if (value == null || drawn == 0)
                    return false;
                require(value.amount == 3 && value != ValueTypeFixture.live,
                    "Initial custom snapshot is not detached");
                first = value;
                require(
                    ScreenValues
                        .changed(Map.of(0, value),
                            Map.of(0, new ValueTypeFixture.Reading(3)), false)
                        .isEmpty(),
                    "Registered content equality sends unchanged values");
                require(screen.submitElementAction(element,
                    ValueTypeFixture.EDIT, 7), "Custom action was not sent");
            }
            case 1 -> {
                if (accepted == 0)
                    return false;
                require(value.amount == 7 && first.amount == 3,
                    "Mutable getter corrupted its previous snapshot");
                menu.apply(Map.of(menu.index(element) * 2,
                    new ValueTypeFixture.Reading(99)));
                require(screen.submitElementAction(element,
                    ValueTypeFixture.EDIT, -1), "Rejected action was not sent");
            }
            case 2 -> {
                if (rejected == 0)
                    return false;
                require(value.amount == 7,
                    "Rejected action did not correct custom value");
                Minecraft.getInstance().getSingleplayerServer()
                    .execute(() -> ValueTypeFixture.live.amount = 11);
            }
            case 3 -> {
                if (value == null || value.amount != 11)
                    return false;
                var mc = Minecraft.getInstance();
                Screenshot.grab(mc.gameDirectory, "custom-value.png",
                    mc.getMainRenderTarget(), ignored -> {
                    });
                mc.getSingleplayerServer()
                    .execute(() -> ValueTypeFixture.live = null);
            }
            case 4 -> {
                if (value != null)
                    return false;
                Minecraft.getInstance().getSingleplayerServer().execute(
                    () -> ValueTypeFixture.live = new ValueTypeFixture.Reading(
                        13));
            }
            case 5 -> {
                if (value == null || value.amount != 13)
                    return false;
                require(
                    menu.value(menu.bound.screen().find("energy").orElseThrow(),
                        false) instanceof java.math.BigInteger,
                    "Built-in gauge values stopped synchronizing beside custom values");
                return true;
            }
            default ->
                throw new AssertionError("Unexpected value-type smoke phase");
        }
        phase++;
        return false;
    }

    private static void require(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }
}
