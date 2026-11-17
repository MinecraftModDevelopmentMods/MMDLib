package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement;
import zone.moddev.mc.lib.gui.model.elements.UpgradeSlotElement;
import zone.moddev.mc.lib.gui.model.elements.WorkingAreaButtonElement;
import zone.moddev.mc.lib.gui.sync.ElementStates;

class UpgradesDrawerTest {
    @Test
    void xmlCreatesARegisteredDrawerWithStableHiddenSlotsAndAnAreaControl()
        throws Exception {
        String xml = """
            <screen xmlns="https://moddev.zone/mmdlib/screen" title="test.title"
                    block-entity="zone.moddev.mc.lib.machine.neoforge.BaseMachine">
              <vbox padding="8"><spacer/></vbox>
              <inventory-controls id="controls"><working-area-button id="area" processor-key="farm"/></inventory-controls>
              <upgrades-drawer id="upgrades">
                <inventory storage="upgrades" columns="1" spacing="2">
                  <upgrade-slot id="first" index="0" item-tag="test:upgrades"/>
                  <upgrade-slot id="second" index="1" item-tag="test:upgrades" max-stack-size="8"/>
                </inventory>
              </upgrades-drawer>
            </screen>
            """;
        var screen = ScreenReader.read(ResourceLocation.parse("test:farm"),
            "farm.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertInstanceOf(WorkingAreaButtonElement.class,
            screen.find("area").orElseThrow());
        var drawer = assertInstanceOf(UpgradesDrawerElement.class,
            screen.tabs().getFirst());
        var slot = assertInstanceOf(UpgradeSlotElement.class,
            screen.find("first").orElseThrow());
        assertEquals(ResourceLocation.parse("test:upgrades"),
            slot.acceptedItems().location());
        assertEquals(1, slot.maxStackSize());
        assertEquals(8,
            ((UpgradeSlotElement) screen.find("second").orElseThrow())
                .maxStackSize());
        var state = new ElementStates(screen.elements(), RegistryAccess.EMPTY,
            () -> {
            });
        var closed = ScreenLayout.of(screen, TextMeasure.NONE, false, false,
            state);
        assertFalse(drawer.childVisible(drawer.children().getFirst(), state));
        assertEquals(1, closed.drawers().stream()
            .filter(d -> d.type().equals(BuiltinDrawers.UPGRADES)).count());
        assertTrue(
            closed.drawers().stream()
                .filter(d -> d.type().equals(BuiltinDrawers.UPGRADES))
                .findFirst().orElseThrow().placement().order() < 0,
            "Upgrades precedes side configuration and redstone at the top");
        state.set(drawer, UpgradesDrawerElement.OPEN, true);
        assertTrue(drawer.childVisible(drawer.children().getFirst(), state));
        var open = ScreenLayout.of(screen, TextMeasure.NONE, false, false,
            state);
        assertEquals(closed.panel(), open.panel());
        assertEquals(closed.bounds(slot), open.bounds(slot));
        assertTrue(open.bounds(slot).x() >= open.bounds(drawer).right());
        // A different tab order must move only the tab, never the native slots.
        var definition = open.drawers().stream()
            .filter(d -> d.type().equals(BuiltinDrawers.UPGRADES)).findFirst()
            .orElseThrow();
        var movedDefinition = new zone.moddev.mc.lib.gui.drawer.DrawerDefinition(
            definition.type(), definition.id(),
            zone.moddev.mc.lib.gui.drawer.DrawerPlacement
                .top(zone.moddev.mc.lib.gui.model.Edge.RIGHT, 40),
            definition.element(), definition.toolbarWidth(),
            definition.interactive());
        var preceding = new zone.moddev.mc.lib.gui.drawer.DrawerDefinition(
            ResourceLocation.parse("test:preceding"), "preceding",
            zone.moddev.mc.lib.gui.drawer.DrawerPlacement
                .top(zone.moddev.mc.lib.gui.model.Edge.RIGHT, 0));
        var reordered = ScreenLayout.of(screen, TextMeasure.NONE,
            java.util.List.of(preceding, movedDefinition), state);
        var expected = drawer.body(reordered.panel(), reordered.bounds(drawer),
            Integer.MAX_VALUE, TextMeasure.NONE);
        assertEquals(zone.moddev.mc.lib.gui.layout.SideDrawerLayout
            .top(reordered.panel()), expected.y());
        assertEquals(expected.x() + 6, reordered.bounds(slot).x());
        assertEquals(expected.y() + 6, reordered.bounds(slot).y());
        assertTrue(reordered.bounds(drawer).y() > open.bounds(drawer).y());
        assertEquals(open.bounds(slot), reordered.bounds(slot));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            slot.itemPosition(open.bounds(slot)),
            slot.itemPosition(reordered.bounds(slot)));
        state.set(drawer, UpgradesDrawerElement.OPEN, false);
        assertFalse(drawer.childVisible(drawer.children().getFirst(), state));
    }
}
