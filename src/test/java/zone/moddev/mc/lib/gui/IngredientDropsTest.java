package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.BehaviorCapabilities;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementRenderAccess;
import zone.moddev.mc.lib.gui.client.IngredientDropHandler;
import zone.moddev.mc.lib.gui.client.IngredientDrops;
import zone.moddev.mc.lib.gui.client.IngredientType;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;

class IngredientDropsTest {
    static final IngredientType<StringBuilder> TYPE = new IngredientType<>(
        ResourceLocation.parse("test:text"),
        StringBuilder.class,
        value -> new StringBuilder(value.toString()));

    record Display(Common common) implements Element {
    }

    static final class Provider implements ElementBehavior, IngredientDropHandler {
        boolean enabled = true, duplicate;
        int received;
        Rect bounds = new Rect(5, 5, 20, 20);

        public <T> List<Target<T>> targets(IngredientType<T> type, T value) {
            if (!enabled || !type.equals(TYPE))
                return List.of();
            ((StringBuilder) value).append(" discovery copy");
            var target = new Target<T>(
                "primary",
                bounds,
                next -> {
                    assertEquals("hello", next.toString());
                    ((StringBuilder) next).append(" received copy");
                    received++;
                    return true;
                });
            return duplicate ? List.of(target, target) : List.of(target);
        }
    }

    static final class Access implements ElementRenderAccess {
        boolean shown = true;
        ElementBehavior behavior;

        public Font font() {
            return null;
        }

        public Rect viewport() {
            return new Rect(0, 0, 20, 20);
        }

        public boolean visible(Element element) {
            return shown;
        }

        public <T> Optional<T> capability(Element element, Class<T> type) {
            return BehaviorCapabilities.find(behavior, type);
        }
    }

    private final Element element = new Display(Common.empty());
    private final StringBuilder original = new StringBuilder("hello");
    private final Provider provider = new Provider();
    private final Access access = new Access();

    IngredientDropsTest() {
        access.behavior = provider;
    }

    private List<IngredientDropHandler.Target<StringBuilder>> targets() {
        return IngredientDrops.targets(access, List.of(element), TYPE, original);
    }

    @Test
    void registeredModBehaviorSuppliesNonItemDropsWithoutMutatingViewerValues() {
        var registry = new ElementBehaviors();
        registry.register(Display.class, (e, a) -> provider);
        access.behavior = registry.create(new ElementBehaviorsTest.Access(element));
        var targets = targets();
        assertEquals(1, targets.size());
        assertEquals(new Rect(5, 5, 15, 15), targets.getFirst().bounds());
        assertEquals("hello", original.toString());
        assertEquals(0, provider.received);
        assertTrue(targets.getFirst().accept().test(original));
        assertEquals(1, provider.received);
        assertEquals("hello", original.toString());
    }

    @Test
    void hiddenMovedAndNewlyDisabledTargetsRejectStaleDrops() {
        var old = targets().getFirst();
        access.shown = false;
        assertTrue(targets().isEmpty());
        assertFalse(old.accept().test(original));
        access.shown = true;
        provider.bounds = new Rect(6, 5, 20, 20);
        assertFalse(old.accept().test(original));
        var moved = targets().getFirst();
        provider.enabled = false;
        assertFalse(moved.accept().test(original));
        assertEquals(0, provider.received);
    }

    @Test
    void differentIngredientIdentitiesAndMissingCapabilitiesProduceNoTargets() {
        var other = new IngredientType<>(
            ResourceLocation.parse("test:other"), StringBuilder.class, TYPE.copy());
        assertTrue(IngredientDrops.targets(access, List.of(element), other, original).isEmpty());
        access.behavior = new ElementBehavior() {
        };
        assertTrue(targets().isEmpty());
    }

    @Test
    void adaptersCanExposeTheCapabilityAndDuplicateTargetIdsFailExplicitly() {
        class Adapter implements ElementBehavior, BehaviorCapabilities {
            public <T> Optional<T> capability(Class<T> type) {
                return type == IngredientDropHandler.class
                    ? Optional.of(type.cast(provider))
                    : Optional.empty();
            }
        }
        access.behavior = new Adapter();
        assertEquals(1, targets().size());
        provider.duplicate = true;
        assertThrows(IllegalArgumentException.class, this::targets);
    }

    @Test
    void targetsOutsideViewportAreNotAdvertised() {
        provider.bounds = new Rect(40, 40, 20, 20);
        assertTrue(targets().isEmpty());
    }
}
