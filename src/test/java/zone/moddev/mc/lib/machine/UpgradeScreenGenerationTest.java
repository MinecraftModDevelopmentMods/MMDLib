package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.load.ScreenLoader;
import zone.moddev.mc.lib.gui.model.InventoryScope;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.model.elements.UpgradeSlotElement;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.definition.MachineScreenGenerators;

class UpgradeScreenGenerationTest {
    @TempDir
    Path directory;

    private Screen load(String upgrades, String screenAttributes, String fills)
        throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(directory.resolve("machine/upgrades.xml"),
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1"
                         extends="mmdlib:base_powered_machine" name="Upgrades">
                  <appearance preset="model" model="test:block/base"/>
                  <processor id="courier" class="zone.moddev.mc.lib.machine.XmlProcessorTest$Courier"/>
                  <storage><items id="items">
                    <slots id="upgrades" count="9" role="manual" limit="1" automation="false" item-tag="mmdlib:range_upgrades"/>
                    <slots id="tools" count="1" role="manual" automation="false"/>
                  </items></storage>
                  %s
                  <screen xmlns="https://moddev.zone/mmdlib/screen" %s>
                    <fill name="info"/>
                    %s
                  </screen>
                </machine>
                """
                .formatted(upgrades, screenAttributes, fills));
        var sources = List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
        var id = ResourceLocation.parse("test:upgrades");
        var definitions = MachineDefinitions.load(sources);
        var definition = definitions.get(id);
        var screen = ScreenLoader
            .load(sources, Map.of(id, definition.screen()),
                MachineScreenGenerators.forMachines(definitions))
            .get(id).screen();
        MachineScreenGenerators.validate(definition, screen);
        assertEquals(1, count(screen, "tools"));
        return screen;
    }

    private long count(Screen screen, String inventory) {
        return screen.elements().stream().filter(SlotElement.class::isInstance)
            .map(SlotElement.class::cast)
            .filter(slot -> !slot.playerInventory())
            .filter(slot -> InventoryScope.name(screen, slot).equals(inventory))
            .count();
    }

    @Test
    void explicitComponentsLeaveOtherSlotsInTheGeneratedBody()
        throws Exception {
        var screen = load(
            "<upgrades gearbox-slot=\"upgrades/4\" capacitor-slot=\"upgrades/5\"/>",
            "", "");
        assertEquals(9, count(screen, "upgrades"));
        assertTrue(
            screen.find("gearbox").orElseThrow() instanceof UpgradeSlotElement);
        assertFalse(screen.find("slot_4").isPresent());
        assertFalse(screen.find("slot_5").isPresent());
        assertTrue(screen.find("slot_0").isPresent());
    }

    @Test
    void authoredDrawerOwnsItsExtrasWhileDefaultProcessingStillRendersTools()
        throws Exception {
        var screen = load("<upgrades range-storage=\"upgrades\"/>", "", """
            <fill name="upgrades"><upgrades-drawer id="upgrades">
              <machine-upgrades storage="upgrades"/>
            </upgrades-drawer></fill>
            """);
        assertEquals(9, count(screen, "upgrades"));
        assertEquals(11, screen.elements().stream()
            .filter(UpgradeSlotElement.class::isInstance).count());
        assertFalse(screen.find("slot_0").isPresent());
    }

    @Test
    void inheritedUpgradeLayoutSuppressesDefaultDrawerAfterComposition()
        throws Exception {
        Files.createDirectories(directory.resolve("screen"));
        Files.writeString(directory.resolve("screen/custom.xml"),
            """
                <screen xmlns="https://moddev.zone/mmdlib/screen" abstract="true" extends="mmdlib:base_powered_machine">
                  <fill name="heading"><machine-upgrades storage="upgrades"/></fill>
                </screen>
                """);
        var screen = load("<upgrades range-storage=\"upgrades\"/>",
            "extends=\"test:custom\"", "");
        assertEquals(9, count(screen, "upgrades"));
        assertEquals(0, screen.elements().stream()
            .filter(UpgradesDrawerElement.class::isInstance).count());
    }

    @Test
    void explicitDrawerReservesOnlyItsPlacedSlots() throws Exception {
        var screen = load("", "",
            """
                <fill name="upgrades"><upgrades-drawer id="upgrades">
                  <inventory storage="upgrades"><upgrade-slot index="2" item-tag="mmdlib:range_upgrades"/></inventory>
                </upgrades-drawer></fill>
                """);
        assertEquals(9, count(screen, "upgrades"));
        assertFalse(screen.find("slot_2").isPresent());
        assertTrue(screen.find("slot_1").isPresent());
        assertEquals(1, count(screen, "mmdlib_gearbox"));
        assertEquals(1, count(screen, "mmdlib_capacitor"));
    }

    @Test
    void removingUpgradeDrawerDoesNotHideItsStorage() throws Exception {
        var screen = load("", "", "<fill name=\"upgrades\"/>");
        assertEquals(1, count(screen, "mmdlib_gearbox"));
        assertEquals(1, count(screen, "mmdlib_capacitor"));
    }
}
