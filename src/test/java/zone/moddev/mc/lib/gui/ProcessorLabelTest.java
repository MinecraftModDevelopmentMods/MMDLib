package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.elements.LabelElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

class ProcessorLabelTest {
    private static LabelElement label(String variable, boolean translate) {
        return new LabelElement(Common.empty(), null, Optional.of(variable),
            170, 66, translate);
    }

    @Test
    void translatesEachStringLineIndependentlyWithoutResolvingOnServer() {
        var label = label("$processors.courier.details", true);
        var result = label.displayLines("courier.occupied\ncourier.age.adult");
        var lines = result.getSiblings();
        assertEquals(Component.translatable("courier.occupied"), lines.get(0));
        assertEquals(Component.literal("\n"), lines.get(1));
        assertEquals(Component.translatable("courier.age.adult"), lines.get(2));
        assertEquals(Component.literal("courier.occupied\ncourier.age.adult"),
            label("$processors.courier.details", false)
                .displayLines("courier.occupied\ncourier.age.adult"));
    }

    @Test
    void componentLinesKeepIndependentArgumentsAndLiteralNames() {
        var label = label("$processors.courier.details", true);
        var name = Component.literal("Bessie");
        var health = Component.translatable("courier.health", "8.0", "10.0");
        var result = label.displayLines(List.of(name, health));
        assertEquals(name, result.getSiblings().get(0));
        assertEquals(health, result.getSiblings().get(2));
        name.append(" changed externally");
        assertEquals(Component.literal("Bessie"), result.getSiblings().get(0));
        assertThrows(IllegalArgumentException.class,
            () -> label.displayLines(List.of(List.of("nested"))));
    }

    @Test
    void storedLinesSurviveSaveAndRestoreAndRejectPrivateOrMissingVariables() {
        var processor = new Fixture();
        processor.initialize("courier", null);
        var label = label("$processors.courier.details", true);
        label.validateDefinition(Map.of("courier", processor));
        var host = new ScreenHost() {
            final FeatureHost features = new FeatureHost(() -> {
            }).add(processor);
            public net.neoforged.neoforge.items.IItemHandlerModifiable screenItems() {
                return null;
            }
            public FeatureHost screenFeatures() {
                return features;
            }
        };
        var details = List.of("courier.occupied", "courier.age.adult");
        processor.lines(details);
        var saved = processor.state();
        processor.lines(List.of("courier.armed"));
        processor.restore(saved);
        assertEquals(label.displayLines(details),
            label.snapshot(host).orElseThrow().value());
        assertThrows(IllegalArgumentException.class,
            () -> label("$processors.courier.privateValue", true)
                .validateDefinition(Map.of("courier", processor)));
        assertThrows(IllegalArgumentException.class,
            () -> label("$processors.courier.missing", true)
                .validateDefinition(Map.of("courier", processor)));
        assertThrows(IllegalArgumentException.class,
            () -> label("$processors.missing.details", true)
                .validateDefinition(Map.of("courier", processor)));
    }

    static class Fixture extends StoredProcessor {
        final ProcessorState.Key<List<String>> details = variables.define(
            "details", Codec.STRING.listOf(), List.of("courier.armed"),
            List::copyOf, false, true);
        final ProcessorState.Key<Integer> privateValue = variables.define(
            "privateValue", Codec.INT, 0,
            java.util.function.UnaryOperator.identity(), false, false);
        void lines(List<String> lines) {
            variables.update(() -> variables.set(details, lines));
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
