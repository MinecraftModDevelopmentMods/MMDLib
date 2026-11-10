package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;

class ResourceGroupsTest {
    private <T> ResourceGroup<T> group(ResourceType<T> type, IoMode supported) {
        return new ResourceGroup<>("test", Component.literal("Test"), 0x336699,
            type, supported, List.of(0), List.of("input"),
            new SideConfig(IoMode.DISABLED));
    }

    @Test
    void filtersCyclesBySupportedOperation() {
        var input = group(ResourceType.ITEMS, IoMode.INPUT);
        assertEquals(IoMode.INPUT, input.next(IoMode.DISABLED, false));
        assertEquals(IoMode.DISABLED, input.next(IoMode.INPUT, false));
        var output = group(ResourceType.ITEMS, IoMode.OUTPUT);
        assertEquals(IoMode.OUTPUT, output.next(IoMode.DISABLED, false));
        assertEquals(IoMode.DISABLED, output.next(IoMode.OUTPUT, false));
        var storage = group(ResourceType.ITEMS, IoMode.BOTH);
        assertEquals(IoMode.OUTPUT, storage.next(IoMode.INPUT, false));
        assertEquals(IoMode.BOTH, storage.next(IoMode.OUTPUT, false));
        assertEquals(IoMode.DISABLED, storage.next(IoMode.BOTH, false));
        assertEquals(IoMode.BOTH, storage.next(IoMode.DISABLED, true));
    }

    @Test
    void compatibilityRequiresDirectionAndContentNotEnabledFaces() {
        var pull = new AddonSpec(ResourceLocation.parse("test:pull"),
            ResourceType.ITEMS, IoMode.INPUT, 8, 10, 10, true);
        assertTrue(pull.accepts(group(ResourceType.ITEMS, IoMode.INPUT)));
        assertTrue(pull.accepts(group(ResourceType.ITEMS, IoMode.BOTH)));
        assertFalse(pull.accepts(group(ResourceType.ITEMS, IoMode.OUTPUT)));
        assertFalse(pull.accepts(group(ResourceType.FLUIDS, IoMode.INPUT)));
        assertFalse(pull.accepts(group(ResourceType.ENERGY, IoMode.INPUT)));
        var gas = ResourceType.register("test:gas", Object.class);
        assertFalse(pull.accepts(group(gas, IoMode.INPUT)));
        assertTrue(new AddonSpec(ResourceLocation.parse("test:gas_pump"), gas,
            IoMode.INPUT, 100, 10, 1, false).accepts(group(gas, IoMode.INPUT)));
    }

    @Test
    void transferRolesConflictAcrossItemIdsAndTiers() {
        var input = new AddonSpec(ResourceLocation.parse("test:puller"),
            ResourceType.ITEMS, IoMode.INPUT, 8, 20, 4, true);
        var faster = new AddonSpec(ResourceLocation.parse("test:fast_puller"),
            ResourceType.ITEMS, IoMode.INPUT, 64, 5, 1, true);
        var output = new AddonSpec(ResourceLocation.parse("test:pusher"),
            ResourceType.ITEMS, IoMode.OUTPUT, 8, 20, 4, true);
        var both = new AddonSpec(ResourceLocation.parse("test:both"),
            ResourceType.ITEMS, IoMode.BOTH, 8, 20, 4, true);
        assertTrue(input.conflictsWith(faster));
        assertTrue(faster.conflictsWith(input));
        assertFalse(input.conflictsWith(output));
        assertTrue(both.conflictsWith(input));
        assertTrue(both.conflictsWith(output));
        assertTrue(both.conflictsWith(both));
    }

    @Test
    void rejectsStaleCyclesAndFollowsRotation() {
        var input = group(ResourceType.ITEMS, IoMode.INPUT);
        input.bind(() -> Direction.EAST, side -> null);
        assertTrue(input.cycle(RelativeFace.FRONT, IoMode.DISABLED, false));
        assertFalse(input.cycle(RelativeFace.FRONT, IoMode.DISABLED, false));
        assertEquals(IoMode.INPUT, input.mode(Direction.EAST));
        assertEquals(IoMode.DISABLED, input.mode(Direction.WEST));
        assertEquals(IoMode.INPUT, input.mode(null));
    }
}
