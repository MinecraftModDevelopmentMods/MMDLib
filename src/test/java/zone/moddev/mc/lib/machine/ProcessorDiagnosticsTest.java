package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.feature.PortableStoredProcessor;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

class ProcessorDiagnosticsTest {
    @TempDir
    Path directory;
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    public static final class Fixture extends PortableStoredProcessor {
        public Fixture() {
            CONSTRUCTIONS.incrementAndGet();
            variables.bool("occupied", false).visual().define();
        }
        public boolean canStart(ProcessorState state,
            ProcessorMachine machine) {
            return false;
        }
        public ProcessState process(ProcessorState state,
            ProcessorMachine machine, int delta) {
            return ProcessState.IDLE;
        }
    }

    private void load(String bounds, String condition, String type,
        boolean portable) throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(directory.resolve("machine/diagnostic.xml"),
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="mmdlib:base_machine" name="Test">
                  <block portable="%s" on-secondary-use="mmdlib:pickup"/>
                  <appearance preset="model" model="test:block/base"/>
                  <storage/>
                  <shapes><shape id="cage" bounds="%s" when="%s"/></shapes>
                  <processor id="test" class="%s"/>
                </machine>
                """
                .formatted(portable, bounds, condition, type));
        MachineDefinitions.load(List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader())));
    }

    @Test
    void invalidShapesAndClassesRetainTheirSourceLines() {
        var type = Fixture.class.getName();
        for (var bounds : List.of("0 0 0 16 x 16", "0 0 0 17 16 16")) {
            var failure = assertThrows(ScreenException.class,
                () -> load(bounds, "$processors.test.occupied", type, true));
            assertTrue(failure.getMessage().contains("diagnostic.xml:5:"),
                failure.getMessage());
        }
        var missing = assertThrows(ScreenException.class,
            () -> load("0 0 0 16 16 16", "$processors.test.missing", type,
                true));
        assertTrue(missing.getMessage().contains("diagnostic.xml:5:"),
            missing.getMessage());
        var badClass = assertThrows(ScreenException.class,
            () -> load("0 0 0 16 16 16", "true", "missing.Processor", true));
        assertTrue(badClass.getMessage().contains("diagnostic.xml:6:"),
            badClass.getMessage());
        var notPortable = assertThrows(ScreenException.class,
            () -> load("0 0 0 16 16 16", "true",
                XmlProcessorTest.Courier.class.getName(), true));
        assertTrue(notPortable.getMessage().contains("diagnostic.xml:2:"),
            notPortable.getMessage());
    }

    @Test
    void onePrototypeValidatesActionsAndVisualDefaults() throws Exception {
        CONSTRUCTIONS.set(0);
        load("0 0 0 16 16 16", "$processors.test.occupied",
            Fixture.class.getName(), true);
        assertEquals(1, CONSTRUCTIONS.get());
    }
}
