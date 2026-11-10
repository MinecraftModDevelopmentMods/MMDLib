package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.elements.GhostSlotElement;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SideConfigElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;

class ElementLimitsTest {
    private record ModSlot(Common common, int index, Appearance appearance) implements SlotElement {
        public SlotKind kind() {
            return SlotKind.NORMAL;
        }
    }

    private static List<SlotElement> slots(int index) {
        var appearance = SlotElement.Appearance.standard(SlotSize.NORMAL, Optional.empty(),
            Optional.empty());
        return List.of(
            new ItemSlotElement(Common.empty(), index, SlotKind.NORMAL, appearance),
            new GhostSlotElement(Common.empty(), index, appearance),
            new ModSlot(Common.empty(), index, appearance));
    }

    private static Screen screen(Element element) {
        return new Screen(
            ResourceLocation.parse("test:limits"),
            "limits.java",
            new Text("test", Optional.empty()),
            "net.minecraft.world.level.block.entity.BlockEntity",
            false,
            List.of(element),
            List.of(),
            Map.of(element.common(), "inherited.xml"));
    }

    @Test
    void itemGhostAndModSlotsOwnTheirIndexBounds() {
        for (int index : new int[]{0, 4095})
            for (var slot : slots(index))
                assertDoesNotThrow(
                    () -> {
                        slot.validate();
                    });
        for (int index : new int[]{-1, 4096})
            for (var slot : slots(index)) {
                assertThrows(IllegalArgumentException.class, slot::validate);
                var error = assertThrows(
                    ScreenException.class,
                    () -> ScreenBinder.bind(screen(slot), getClass().getClassLoader()));
                assertTrue(error.getMessage().contains("inherited.xml"));
                assertTrue(error.getMessage().contains("0..4095"));
            }
    }

    @Test
    void sideConfigurationOwnsItsGroupLimitBeforeBindingOrRuntimeStartup() {
        assertDoesNotThrow(
            () -> {
                new SideConfigElement(Common.empty(), Collections.nCopies(682, "group")).validate();
            });
        var tooMany = new SideConfigElement(Common.empty(), Collections.nCopies(683, "group"));
        assertThrows(IllegalArgumentException.class, tooMany::validate);
        var error = assertThrows(
            ScreenException.class,
            () -> ScreenBinder.bind(screen(tooMany), getClass().getClassLoader()));
        assertTrue(error.getMessage().contains("Too many side-config groups"));
        assertTrue(error.getMessage().contains("inherited.xml"));
    }

    @Test
    void xmlLoadingRejectsElementLimitsWithoutRuntimeValidation() {
        for (var body : List.of(
            "<item-slot index=\"4096\"/>",
            "<ghost-slot index=\"4096\"/>",
            "<side-config groups=\""
                + String.join(" ", Collections.nCopies(683, "group"))
                + "\"/>")) {
            var xml = "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\""
                + " block-entity=\"net.minecraft.world.level.block.entity.BlockEntity\">"
                + body
                + "</screen>";
            var error = assertThrows(
                ScreenException.class,
                () -> ScreenReader.read(
                    ResourceLocation.parse("test:limits"),
                    "limits.xml",
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
            assertTrue(error.getMessage().contains("limits.xml"));
        }
    }
}
