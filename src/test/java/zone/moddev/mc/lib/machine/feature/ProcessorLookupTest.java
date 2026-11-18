package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.runtime.ScreenHost;

class ProcessorLookupTest {
    @Test
    void primarySelectionKeepsExplicitAndIndexedOrderIndependent() {
        var first = new ProcessorStateTest.Fixture();
        first.initialize("first", null);
        var second = new ProcessorStateTest.Fixture();
        second.initialize("second", null);
        var features = new FeatureHost(() -> {
        }).add(first).add(second);
        assertSame(first,
            features.findProcessor(java.util.Optional.empty()).orElseThrow());
        features.primaryProcessor("second");
        assertSame(second,
            features.findProcessor(java.util.Optional.empty()).orElseThrow());
        assertSame(first, features.findProcessor(java.util.Optional.of("first"))
            .orElseThrow());
        assertSame(first, features.allProcessors().getFirst());
        assertTrue(features.processor(java.util.Optional.empty()).isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> features.primaryProcessor("missing"));
    }

    @Test
    void lookupAcceptsGenericHostsAndRejectsMissingOrMismatchedProcessors() {
        var processor = new ProcessorStateTest.Fixture();
        processor.initialize("work", null);
        var features = new FeatureHost(() -> {
        }).add(processor);
        ScreenHost screen = new ScreenHost() {
            public net.neoforged.neoforge.items.IItemHandlerModifiable screenItems() {
                return null;
            }
            public FeatureHost screenFeatures() {
                return features;
            }
        };
        assertSame(processor, Processors
            .find(features, "work", StoredProcessor.class).orElseThrow());
        assertSame(processor, Processors
            .find(screen, "work", StoredProcessor.class).orElseThrow());
        assertTrue(
            Processors.find(screen, "work", RecipeProcessor.class).isEmpty());
        assertTrue(Processors.find(screen, "missing", StoredProcessor.class)
            .isEmpty());
        assertTrue(
            Processors.find(null, "work", StoredProcessor.class).isEmpty());
    }
}
