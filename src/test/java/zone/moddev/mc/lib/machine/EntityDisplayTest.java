package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.api.entity.EntitySnapshotPolicy;
import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.feature.PortableStoredProcessor;
import zone.moddev.mc.lib.machine.feature.PortableVariable;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

class EntityDisplayTest {
    @TempDir
    Path directory;

    public static final class Fixture extends PortableStoredProcessor {
        static final EntitySnapshotPolicy<Mob> POLICY = new EntitySnapshotPolicy<>(
            Mob.class, mob -> true, (mob, data) -> {
            }, (mob, data) -> {
            });
        public Fixture() {
            PortableVariable.entity(() -> {
                throw new AssertionError(
                    "Item registry resolved during schema loading");
            }, "courier", "occupant", POLICY).declare(variables).synced()
                .define();
            variables.entity("raw").synced().define();
            variables.bool("flag", false).synced().define();
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

    private MachineDefinition load(String attributes, boolean portable)
        throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(directory.resolve("machine/display.xml"),
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="mmdlib:base_machine" name="Display">
                  <block portable="%s"/>
                  <appearance preset="model" model="test:block/cage"/>
                  <storage/>
                  <entity-display %s/>
                  <processor id="courier" class="zone.moddev.mc.lib.machine.EntityDisplayTest$Fixture"/>
                  <screen xmlns="https://moddev.zone/mmdlib/screen"><fill name="machine"/><fill name="info"/></screen>
                </machine>
                """
                .formatted(portable, attributes));
        return MachineDefinitions
            .load(List.of(
                new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                    getClass().getClassLoader()),
                new SourceRoot("test", directory, getClass().getClassLoader())))
            .get(ResourceLocation.parse("test:display"));
    }

    @Test
    void displayResolvesPortableMetadataWithoutRegistryAccessAndRotatesOffsets()
        throws Exception {
        var definition = load(
            "variable='$processors.courier.occupant' offset='0.25 0.2 0.75' width='0.62' height='0.45' item-property='test:occupied'",
            true);
        var display = definition.behavior().entityDisplay();
        assertTrue(display.block() && display.item());
        assertEquals(new Vec3(.25, .2, .75), display.offset(Direction.NORTH));
        assertEquals(new Vec3(.25, .2, .25), display.offset(Direction.EAST));
        assertEquals(new Vec3(.75, .2, .25), display.offset(Direction.SOUTH));
        assertEquals(new Vec3(.75, .2, .75), display.offset(Direction.WEST));
        var processor = definition.behavior().processor().create("courier",
            null);
        assertEquals(Fixture.POLICY, processor.portableVariable("occupant")
            .orElseThrow().entityPolicy().orElseThrow());
        assertTrue(processor.portableVariable("raw").isEmpty());
        assertEquals(180, display.itemYaw());
        assertTrue(
            load("variable='$processors.courier.raw' item='false'", false)
                .behavior().entityDisplay().block());
    }

    @Test
    void invalidSourcesDimensionsAndTargetsFailAtLoad() {
        for (var attributes : List.of("variable='$processors.courier.flag'",
            "variable='$processors.courier.missing'",
            "variable='$processors.courier.raw'",
            "variable='$processors.other.occupant'",
            "variable='$processors.courier.occupant' offset='NaN 0 0'",
            "variable='$processors.courier.occupant' offset='0 0'",
            "variable='$processors.courier.occupant' width='0'",
            "variable='$processors.courier.occupant' height='Infinity'",
            "variable='$processors.courier.occupant' block='false' item='false'",
            "variable='$processors.courier.occupant' item='false' item-property='test:occupied'"))
            assertThrows(IllegalArgumentException.class,
                () -> load(attributes, true));
        assertThrows(IllegalArgumentException.class,
            () -> load("variable='$processors.courier.occupant'", false));
    }
}
