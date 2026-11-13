package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.EntityPortraitData;
import zone.moddev.mc.lib.gui.model.elements.EntityPortraitElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

class EntityPortraitTest {
    private static EntityPortraitElement element(String variable) {
        return new EntityPortraitElement(Common.empty(), variable, 72, 72,
            Optional.empty());
    }

    @Test
    void dataCopiesPayloadAndRoundTripsPersistenceAndElementTransport() {
        var tag = new CompoundTag();
        tag.putString("id", "minecraft:cow");
        var nested = new CompoundTag();
        nested.putString("name", "Bessie");
        tag.put("custom", nested);
        var portrait = new EntityPortraitData(tag);
        nested.putString("name", "changed");
        portrait.data().getCompound("custom").putString("name", "also changed");
        assertEquals("Bessie",
            portrait.data().getCompound("custom").getString("name"));
        var saved = EntityPortraitData.CODEC
            .encodeStart(NbtOps.INSTANCE, portrait).getOrThrow();
        assertEquals(portrait, EntityPortraitData.CODEC
            .parse(NbtOps.INSTANCE, saved).getOrThrow());
        var element = element("$processors.courier.occupant");
        var encoded = EntityPortraitElement.ENTITY.encode(RegistryAccess.EMPTY,
            element, portrait);
        assertEquals(portrait, EntityPortraitElement.ENTITY
            .decode(RegistryAccess.EMPTY, element, encoded));
        assertTrue(ElementStateView.DEFAULTS
            .get(element, EntityPortraitElement.ENTITY).isEmpty());
    }

    @Test
    void bindingRequiresDeclaredClientVisibleEntityData() {
        var processor = new Fixture();
        processor.initialize("courier", null);
        var declarations = Map.<String, zone.moddev.mc.lib.machine.feature.Processor<?>>of(
            "courier", processor);
        element("$processors.courier.occupant")
            .validateDefinition(declarations);
        for (var variable : new String[]{"missing", "hidden", "label"})
            assertThrows(IllegalArgumentException.class,
                () -> element("$processors.courier." + variable)
                    .validateDefinition(declarations));
        assertThrows(IllegalArgumentException.class,
            () -> element("$processors.missing.occupant")
                .validateDefinition(declarations));
        assertThrows(IllegalArgumentException.class,
            () -> element("not_a_variable"));
        var tag = new CompoundTag();
        tag.putString("id", "missing_mod:animal");
        processor.occupant(new EntityPortraitData(tag));
        var saved = processor.state();
        processor.occupant(EntityPortraitData.EMPTY);
        processor.restore(saved);
        assertFalse(
            ((EntityPortraitData) processor.variable("occupant")).isEmpty());
        assertEquals(tag,
            ((EntityPortraitData) processor.variable("occupant")).data());
    }

    private static class Fixture extends StoredProcessor {
        final ProcessorState.Key<EntityPortraitData> occupant = variables
            .define("occupant", EntityPortraitData.CODEC,
                EntityPortraitData.EMPTY, UnaryOperator.identity(), false,
                true);
        final ProcessorState.Key<EntityPortraitData> hidden = variables.define(
            "hidden", EntityPortraitData.CODEC, EntityPortraitData.EMPTY,
            UnaryOperator.identity(), false, false);
        final ProcessorState.Key<String> label = variables.define("label",
            Codec.STRING, "Cow");
        void occupant(EntityPortraitData value) {
            variables.update(() -> variables.set(occupant, value));
        }
        public boolean canStart(ProcessorState state,
            ProcessorMachine machine) {
            return false;
        }
        public ProcessState process(ProcessorState state,
            ProcessorMachine machine, int deltaTicks) {
            return ProcessState.IDLE;
        }
    }
}
