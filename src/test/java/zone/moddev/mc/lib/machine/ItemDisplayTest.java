package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

class ItemDisplayTest {
    @TempDir
    Path directory;

    public static final class Fixture extends StoredProcessor {
        public Fixture() {
            variables.string("product", "").synced().define();
            variables.string("private_product", "").define();
            variables.string("invalid", "not an item id").synced().define();
            variables.bool("flag", false).visual().define();
            variables.entity("occupant").synced().define();
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

    private MachineDefinition load(String attributes, String extra)
        throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(directory.resolve("machine/display.xml"),
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="mmdlib:base_machine" name="Display">
                  <appearance preset="model" model="test:block/machine">
                    <item-display %s/>
                    %s
                  </appearance>
                  <storage/>
                  <processor id="test" class="zone.moddev.mc.lib.machine.ItemDisplayTest$Fixture"/>
                  <screen xmlns="https://moddev.zone/mmdlib/screen"><fill name="machine"/><fill name="info"/></screen>
                </machine>
                """
                .formatted(attributes, extra));
        return loadMachine("display");
    }

    private MachineDefinition loadMachine(String name) {
        return MachineDefinitions
            .load(List.of(
                new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                    getClass().getClassLoader()),
                new SourceRoot("test", directory, getClass().getClassLoader())))
            .get(ResourceLocation.parse("test:" + name));
    }

    @Test
    void bindsWithoutItemRegistryLookupAndCoexistsWithEntityDisplays()
        throws Exception {
        var definition = load(
            "variable='$processors.test.product' x='0.25' y='0.7' width='0.4' height='0.6'",
            "<entity-display variable='$processors.test.occupant' item='false'/>");
        var display = definition.behavior().itemDisplay();
        assertNotNull(definition.behavior().entityDisplay());
        assertEquals("product", display.variable().name());
        assertEquals(.4f, display.width());
        assertEquals(.6f, display.height());
        assertEquals(new Vec3(.75, .7, -.002), display.offset(Direction.NORTH));
        assertEquals(new Vec3(1.002, .7, .75), display.offset(Direction.EAST));
        assertEquals(new Vec3(.25, .7, 1.002), display.offset(Direction.SOUTH));
        assertEquals(new Vec3(-.002, .7, .25), display.offset(Direction.WEST));
        assertThrows(IllegalArgumentException.class,
            () -> display.offset(Direction.UP));
        var defaults = load("variable='$processors.test.product'", "")
            .behavior().itemDisplay();
        assertEquals(.5, defaults.x());
        assertEquals(.5, defaults.y());
        assertEquals(.5f, defaults.width());
    }

    @Test
    void nestedDisplaysInheritAndOverrideAlongsideConditionalParts()
        throws Exception {
        load("variable='$processors.test.product' width='0.4' height='0.6'",
            "<entity-display variable='$processors.test.occupant' item='false' width='0.7'/>"
                + "<part id='overlay' model='test:block/overlay' when='$processors.test.flag'/>");
        Files.writeString(directory.resolve("machine/child.xml"),
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="test:display" name="Child">
                  <appearance>
                    <item-display width="0.8"/>
                    <entity-display height="0.9"/>
                  </appearance>
                </machine>
                """);
        var child = loadMachine("child");
        assertEquals(.8f, child.behavior().itemDisplay().width());
        assertEquals(.6f, child.behavior().itemDisplay().height());
        assertEquals("product",
            child.behavior().itemDisplay().variable().name());
        assertEquals(.7f, child.behavior().entityDisplay().width());
        assertEquals(.9f, child.behavior().entityDisplay().height());
        assertEquals(1, child.modelParts().size());
        Files.writeString(directory.resolve("machine/grandchild.xml"),
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="test:child" name="Grandchild">
                  <appearance preset="cube-front" front="test:block/front" side="test:block/side" top="test:block/top"/>
                </machine>
                """);
        var grandchild = loadMachine("grandchild");
        assertEquals(child.behavior().itemDisplay(),
            grandchild.behavior().itemDisplay());
        assertEquals(child.behavior().entityDisplay(),
            grandchild.behavior().entityDisplay());
        assertEquals(child.modelParts(), grandchild.modelParts());
    }

    @Test
    void displaysAreOnlyAllowedInsideAppearance() throws Exception {
        for (var display : List.of(
            "<item-display variable='$processors.test.product'/>",
            "<entity-display variable='$processors.test.occupant' item='false'/>")) {
            load("variable='$processors.test.product'", "");
            var file = directory.resolve("machine/display.xml");
            Files.writeString(file, Files.readString(file).replace("</machine>",
                display + "</machine>"));
            var error = assertThrows(IllegalArgumentException.class,
                () -> loadMachine("display"));
            assertTrue(
                error.getMessage().contains("Unexpected child of machine"));
        }
    }

    @Test
    void invalidBindingsBoundsAndDuplicatesFailAtLoad() {
        for (var attrs : List.of("variable='$processors.test.flag'",
            "variable='$processors.test.private_product'",
            "variable='$processors.test.missing'",
            "variable='$processors.missing.product'", "variable='product'",
            "variable='$processors.test.invalid'",
            "variable='$processors.test.product' width='0'",
            "variable='$processors.test.product' height='Infinity'",
            "variable='$processors.test.product' x='NaN'",
            "variable='$processors.test.product' y='-0.1'",
            "variable='$processors.test.product' width='2'"))
            assertThrows(IllegalArgumentException.class, () -> load(attrs, ""));
        assertThrows(IllegalArgumentException.class,
            () -> load("variable='$processors.test.product'",
                "<item-display variable='$processors.test.product'/>"));
        assertThrows(IllegalArgumentException.class, () -> load(
            "variable='$processors.test.product'",
            "<entity-display variable='$processors.test.occupant' item='false'/>"
                .repeat(2)));
    }
}
