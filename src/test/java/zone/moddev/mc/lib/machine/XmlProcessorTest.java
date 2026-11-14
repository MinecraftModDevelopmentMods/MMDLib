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
    void invalidActionAndVariableBindingsFailDuringLoading() {
        assertThrows(IllegalArgumentException.class,
            () -> load("$processors.courier.occupied", "test:missing"));
        assertThrows(IllegalArgumentException.class,
            () -> load("$processors.courier.missing", "test:release"));
        assertThrows(IllegalArgumentException.class,
            () -> load("$processors.courier.status", "test:release"));
    }
}
