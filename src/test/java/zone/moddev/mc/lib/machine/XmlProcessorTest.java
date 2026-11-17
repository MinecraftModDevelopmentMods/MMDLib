package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.load.ScreenLoader;
import zone.moddev.mc.lib.gui.model.elements.BoundButtonElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.definition.MachineScreenGenerators;
import zone.moddev.mc.lib.machine.feature.ProcessorAction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

public class XmlProcessorTest {
    @TempDir
    Path directory;
    @Test
    void poweredBaseInheritsComponentsAndExactlyOneDrawer() throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        var file = directory.resolve("machine/powered.xml");
        var sources = List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
        var id = ResourceLocation.parse("test:powered");
        for (boolean processor : List.of(false, true)) {
            Files.writeString(file,
                """
                    <machine xmlns="https://moddev.zone/mmdlib/machine" version="1"
                             extends="mmdlib:base_powered_machine" name="Powered">
                      <appearance preset="model" model="test:block/base"/>
                      %s
                      <screen xmlns="https://moddev.zone/mmdlib/screen"><fill name="info"/></screen>
                    </machine>
                    """
                    .formatted(processor
                        ? "<processor id=\"courier\" class=\"zone.moddev.mc.lib.machine.XmlProcessorTest$Courier\"/>"
                        : ""));
            var definitions = MachineDefinitions.load(sources);
            var definition = definitions.get(id);
            assertEquals(processor ? "mmdlib_gearbox/0" : null,
                definition.behavior().gearboxSlot());
            assertEquals("mmdlib_capacitor/0",
                definition.behavior().capacitorSlot());
            var screen = ScreenLoader
                .load(sources, Map.of(id, definition.screen()),
                    MachineScreenGenerators.forMachines(definitions))
                .get(id).screen();
            MachineScreenGenerators.validate(definition, screen);
            assertEquals(1, screen.elements().stream().filter(
                zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement.class::isInstance)
                .count());
            assertEquals(processor ? 2 : 1, screen.elements().stream().filter(
                zone.moddev.mc.lib.gui.model.elements.UpgradeSlotElement.class::isInstance)
                .count());
            assertEquals(processor, screen.find("gearbox").isPresent());
            assertTrue(screen.find("capacitor").isPresent());
        }
    }

    @Test
    void sharedComponentsFollowCapabilitiesAndExtrasStayMachineOwned()
        throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        var file = directory.resolve("machine/components.xml");
        var sources = List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
        var id = ResourceLocation.parse("test:components");
        for (boolean power : List.of(false, true))
            for (boolean processor : List.of(false, true)) {
                Files.writeString(file,
                    """
                        <machine xmlns="https://moddev.zone/mmdlib/machine" version="1"
                                 extends="mmdlib:base_machine" name="Components">
                          <appearance preset="model" model="test:block/base"/>
                          <upgrades range-storage="extra"/>
                          <storage>
                            %s
                            <items id="items"><slots id="extra" count="9" role="manual" automation="false"
                             item-tag="test:custom_upgrades"/></items>
                          </storage>
                          %s
                          <screen xmlns="https://moddev.zone/mmdlib/screen">
                            <fill name="machine"><machine-upgrades storage="extra"/></fill>
                            <fill name="info"/>
                          </screen>
                        </machine>
                        """
                        .formatted(power
                            ? "<energy id=\"energy\" capacity=\"1000\" input-rate=\"100\" output-rate=\"100\"/>"
                            : "",
                            processor
                                ? "<processor id=\"courier\" class=\"zone.moddev.mc.lib.machine.XmlProcessorTest$Courier\"/>"
                                : ""));
                var definitions = MachineDefinitions.load(sources);
                var definition = definitions.get(id);
                assertEquals(processor,
                    definition.behavior().gearboxSlot() != null);
                assertEquals(power,
                    definition.behavior().capacitorSlot() != null);
                assertEquals(9, definition.inventorySlots("extra").size());
                assertEquals(9 + (power ? 1 : 0) + (processor ? 1 : 0),
                    definition.slots().size());
                assertTrue(definition.groups().stream()
                    .noneMatch(group -> group.automation()));
                var screen = ScreenLoader
                    .load(sources, Map.of(id, definition.screen()),
                        MachineScreenGenerators.forMachines(definitions))
                    .get(id).screen();
                MachineScreenGenerators.validate(definition, screen);
                assertEquals(processor, screen.find("gearbox").isPresent());
                assertEquals(power, screen.find("capacitor").isPresent());
                for (int i = 0; i < 9; i++) {
                    var slot = (zone.moddev.mc.lib.gui.model.elements.UpgradeSlotElement) screen
                        .find("extra_" + i).orElseThrow();
                    assertEquals("extra",
                        zone.moddev.mc.lib.gui.model.InventoryScope.name(screen,
                            slot));
                    assertEquals(i, slot.index());
                    assertEquals(ResourceLocation.parse("test:custom_upgrades"),
                        slot.acceptedItems().location());
                }
                for (String kind : List.of("gearbox", "capacitor"))
                    screen.find(kind).ifPresent(element -> {
                        var slot = (zone.moddev.mc.lib.gui.model.elements.UpgradeSlotElement) element;
                        assertEquals("mmdlib_" + kind,
                            zone.moddev.mc.lib.gui.model.InventoryScope
                                .name(screen, slot));
                        assertEquals(0, slot.index());
                    });
            }
    }

    @Test
    void automaticUpgradeSocketsUseDeclaredAddressesAndSharedPresentation()
        throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        var file = directory.resolve("machine/upgrades.xml");
        String xml = """
            <machine xmlns="https://moddev.zone/mmdlib/machine" version="1"
                     extends="mmdlib:base_powered_machine" name="Upgrades">
              <appearance preset="model" model="test:block/base"/>
              <upgrades gearbox-slot="upgrades/4" capacitor-slot="upgrades/5"
                        range-slots="upgrades/0 upgrades/1 upgrades/2 upgrades/3 upgrades/6 upgrades/7 upgrades/8 upgrades/9 upgrades/10"/>
              <storage><items id="items">
                <slots id="other" count="2" role="manual" automation="false"/>
                <slots id="upgrades" count="11" role="manual" automation="false"/>
              </items></storage>
              <screen xmlns="https://moddev.zone/mmdlib/screen">
                <fill name="machine"><machine-upgrades storage="upgrades"/></fill>
                <fill name="info"/>
              </screen>
            </machine>
            """;
        var sources = List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
        var id = ResourceLocation.parse("test:upgrades");
        for (boolean customFilter : List.of(false, true)) {
            Files.writeString(file, customFilter
                ? xml.replace("id=\"upgrades\" count=\"11\"",
                    "id=\"upgrades\" count=\"11\" item-tag=\"test:allowed\"")
                : xml);
            var definitions = MachineDefinitions.load(sources);
            var definition = definitions.get(id);
            var screen = ScreenLoader
                .load(sources, Map.of(id, definition.screen()),
                    MachineScreenGenerators.forMachines(definitions))
                .get(id).screen();
            MachineScreenGenerators.validate(definition, screen);
            var slots = screen.elements().stream().filter(
                zone.moddev.mc.lib.gui.model.elements.UpgradeSlotElement.class::isInstance)
                .map(
                    zone.moddev.mc.lib.gui.model.elements.UpgradeSlotElement.class::cast)
                .toList();
            assertEquals(List.of(4, 5, 0, 1, 2, 3, 6, 7, 8, 9, 10),
                slots.stream().map(slot -> slot.index()).toList());
            for (int index = 0; index < slots.size(); index++) {
                String kind = index == 0
                    ? "gearbox"
                    : index == 1 ? "capacitor" : "range";
                var slot = slots.get(index);
                assertEquals(
                    ResourceLocation.parse(customFilter
                        ? "test:allowed"
                        : "mmdlib:" + kind + "_upgrades"),
                    slot.acceptedItems().location());
                assertEquals(18, slot.extent());
                if (index < 2) {
                    assertEquals(
                        ResourceLocation.parse("mmdlib:socket_" + kind),
                        slot.icon().orElseThrow());
                    assertEquals("screen.mmdlib.upgrades." + kind,
                        slot.common().tooltip().orElseThrow().key());
                    assertEquals(slot, screen.find(kind).orElseThrow());
                }
            }
        }
        for (String invalid : List.of(
            xml.replace("storage=\"upgrades\"", "storage=\"other\""),
            xml.replace("count=\"11\"", "count=\"12\""),
            xml.replace("gearbox-slot=\"upgrades/4\"",
                "gearbox-slot=\"other/0\""),
            xml.replace(" upgrades/10\"", "\""))) {
            Files.writeString(file, invalid);
            var definitions = MachineDefinitions.load(sources);
            assertThrows(Exception.class,
                () -> ScreenLoader.load(sources,
                    Map.of(id, definitions.get(id).screen()),
                    MachineScreenGenerators.forMachines(definitions)));
        }
    }

    @Test
    void automaticInventoryCountsComeFromStorageAfterTemplateComposition()
        throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        var file = directory.resolve("machine/inventories.xml");
        String xml = """
            <machine xmlns="https://moddev.zone/mmdlib/machine" version="1"
                     extends="mmdlib:base_machine" name="Inventories">
              <appearance preset="model" model="test:block/base"/>
              <storage><items id="items">
                <slots id="seeds" count="4" role="input" item-tag="minecraft:hoes"/>
                <slots id="output" count="9" role="output"/>
              </items></storage>
                  <processor id="courier" class="zone.moddev.mc.lib.machine.XmlProcessorTest$Courier"/>
              <screen xmlns="https://moddev.zone/mmdlib/screen">
                <fill name="machine"><hbox>
                  <inventory id="seed" storage="seeds" columns="2" spacing="2" slot-tooltip="test.seeds"/>
                  <inventory id="output" storage="output" columns="3" spacing="2"/>
                </hbox></fill>
                <fill name="info"/>
              </screen>
            </machine>
            """;
        var sources = List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
        for (int count : List.of(4, 6)) {
            Files.writeString(file,
                xml.replace("count=\"4\"", "count=\"" + count + "\""));
            var definitions = MachineDefinitions.load(sources);
            var id = ResourceLocation.parse("test:inventories");
            var definition = definitions.get(id);
            var screen = ScreenLoader
                .load(sources, Map.of(id, definition.screen()),
                    MachineScreenGenerators.forMachines(definitions))
                .get(id).screen();
            MachineScreenGenerators.validate(definition, screen);
            var seeds = (zone.moddev.mc.lib.gui.model.containers.InventoryElement) screen
                .find("seed").orElseThrow();
            var output = (zone.moddev.mc.lib.gui.model.containers.InventoryElement) screen
                .find("output").orElseThrow();
            assertEquals(count, seeds.slots().size());
            assertEquals(9, output.slots().size());
            assertEquals(ResourceLocation.parse("minecraft:hoes"),
                definition.slots().getFirst().itemTag());
            assertEquals(zone.moddev.mc.lib.api.capabilities.Role.OUTPUT,
                definition.slots().getLast().role());
        }
        Files.writeString(file,
            xml.replace("storage=\"seeds\"", "storage=\"missing\""));
        var definitions = MachineDefinitions.load(sources);
        var id = ResourceLocation.parse("test:inventories");
        assertThrows(Exception.class,
            () -> ScreenLoader.load(sources,
                Map.of(id, definitions.get(id).screen()),
                MachineScreenGenerators.forMachines(definitions)));
    }
    public static final class Courier extends StoredProcessor {
        final ProcessorState.Key<zone.moddev.mc.lib.api.entity.EntitySnapshot> occupant = variables
            .define("occupant",
                zone.moddev.mc.lib.api.entity.EntitySnapshot.CODEC,
                zone.moddev.mc.lib.api.entity.EntitySnapshot.EMPTY,
                java.util.function.UnaryOperator.identity(), false, true);
        final ProcessorState.Key<Boolean> occupied = variables
            .define("occupied", Codec.BOOL, false);
        public boolean canStart(ProcessorState state,
            ProcessorMachine context) {
            return !state.get(occupied);
        }
        public ProcessState process(ProcessorState state,
            ProcessorMachine context, int deltaTicks) {
            return ProcessState.IDLE;
        }
        public Map<ResourceLocation, ProcessorAction> actions() {
            return Map.of(ResourceLocation.parse("test:release"),
                new ProcessorAction((s, c) -> s.get(occupied), (s, c) -> {
                    s.set(occupied, false);
                    return ProcessorAction.Result.success();
                }));
        }
    }
    private BoundButtonElement load(String condition, String action)
        throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(directory.resolve("machine/courier.xml"),
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="mmdlib:base_machine" name="Courier">
                  <block on-secondary-use="%s"/>
                  <appearance preset="model" model="test:block/base">
                    <part id="cage" model="test:block/cage" when="$processors.courier.occupied"/>
                  </appearance>
                  <storage/>
                  <shapes><shape id="cage" bounds="0 0 0 16 16 16" when="$processors.courier.occupied"/></shapes>
                  <processor id="courier" class="zone.moddev.mc.lib.machine.XmlProcessorTest$Courier"/>
                  <screen xmlns="https://moddev.zone/mmdlib/screen">
                    <fill name="machine"><button id="release" processor="courier" on-click="%s"
                      enabled-when="%s" icon="test:release" tooltip="test:release"/>
                      <label id="status" variable="$processors.courier.status" width="170" height="66" translate-lines="1"/>
                      <entity-portrait id="portrait" variable="$processors.courier.occupant" width="72" height="72"/>
                    </fill>
                    <fill name="info"/>
                  </screen>
                </machine>
                """
                .formatted(action, action, condition));
        var sources = List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
        var definitions = MachineDefinitions.load(sources);
        var id = ResourceLocation.parse("test:courier");
        var definition = definitions.get(id);
        assertTrue(definition.hasProcessor());
        assertTrue(definition.processingType().isEmpty());
        assertEquals(1, definition.behavior().shapes().size());
        assertEquals(ResourceLocation.parse(action),
            definition.behavior().secondaryAction());
        var screen = ScreenLoader
            .load(sources, Map.of(id, definition.screen()),
                MachineScreenGenerators.forMachines(definitions))
            .get(id).screen();
        MachineScreenGenerators.validate(definition, screen);
        var validated = new java.util.concurrent.atomic.AtomicBoolean();
        var custom = new zone.moddev.mc.lib.gui.model.Element() {
            public zone.moddev.mc.lib.gui.model.Common common() {
                return zone.moddev.mc.lib.gui.model.Common.empty();
            }
            public void validateDefinition(
                Map<String, zone.moddev.mc.lib.machine.feature.Processor<?>> processors,
                Map<String, Object> defaults) {
                assertTrue(processors.containsKey("courier"));
                assertTrue(defaults.containsKey("processors.courier.occupied"));
                validated.set(true);
            }
        };
        MachineScreenGenerators.validate(definition,
            new zone.moddev.mc.lib.gui.model.Screen(screen.id(),
                screen.source(), screen.title(), screen.blockEntity(), false,
                List.of(custom), List.of()));
        assertTrue(validated.get(),
            "Custom elements own processor-schema validation");
        assertInstanceOf(
            zone.moddev.mc.lib.gui.model.elements.EntityPortraitElement.class,
            screen.find("portrait").orElseThrow());
        var label = assertInstanceOf(
            zone.moddev.mc.lib.gui.model.elements.LabelElement.class,
            screen.find("status").orElseThrow());
        assertEquals(
            net.minecraft.network.chat.Component.empty()
                .append(net.minecraft.network.chat.Component
                    .translatable("example.key")),
            label.displayLines("example.key"));
        return assertInstanceOf(BoundButtonElement.class,
            screen.find("release").orElseThrow());
    }
    @Test
    void customProcessorNeedsNoRecipeAndButtonStartsDisabledUntilSynchronized()
        throws Exception {
        var button = load("$processors.courier.occupied", "test:release");
        assertEquals("courier", button.processor().orElseThrow());
        assertFalse(button.enabled(ElementStateView.DEFAULTS));
    }
    @Test
    void customProcessorsRejectRecipeCycleSoundsAndHandlers() throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        var sources = List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
        for (var attribute : List.of("running-sound", "finish-sound",
            "on-start", "on-finish")) {
            Files.writeString(directory.resolve("machine/courier.xml"),
                """
                    <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="mmdlib:base_machine" name="Courier">
                      <appearance preset="model" model="test:block/base"/>
                      <storage/>
                      <processor id="courier" class="zone.moddev.mc.lib.machine.XmlProcessorTest$Courier" %s="test:cycle"/>
                    </machine>
                    """
                    .formatted(attribute));
            var failure = assertThrows(IllegalArgumentException.class,
                () -> MachineDefinitions.load(sources));
            assertTrue(
                failure.getMessage().contains("cannot take " + attribute),
                failure.getMessage());
        }
    }
    @Test
    void invalidActionAndVariableBindingsFailDuringLoading() {
        assertThrows(IllegalArgumentException.class,
            () -> load("$processors.courier.occupied", "test:missing"));
        assertThrows(IllegalArgumentException.class,
            () -> load("$processors.courier.missing", "test:release"));
        assertThrows(IllegalArgumentException.class,
            () -> load("$processors.courier.status", "test:release"));
    }
}
