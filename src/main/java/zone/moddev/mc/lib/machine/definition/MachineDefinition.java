package zone.moddev.mc.lib.machine.definition;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;

/**
 * Immutable, resolved startup configuration for a powered item-processing
 * machine.
 */
public record MachineDefinition(ResourceLocation id, String checksum,
    String name, float hardness, ResourceLocation mineable,
    boolean requiresTool, Map<String, String> textures, Power power,
    Fluids fluids, String itemStorageId, List<Slot> slots, List<Group> groups,
    RecipeType recipeType, String processor, String processorName,
    boolean redstone, Node screen) {
    public MachineDefinition {
        textures = Map.copyOf(textures);
        slots = List.copyOf(slots);
        groups = List.copyOf(groups);
    }

    public record Power(String id, long capacity, long input, long output,
        int color) {
    }

    /**
     * Stable names of the independent inventories, in legacy capability slot
     * order.
     */
    public List<String> inventoryNames() {
        var names = new java.util.ArrayList<>(
            java.util.Collections.nCopies(slots.size(), "items"));
        for (var group : groups)
            for (int member : group.members())
                names.set(member, group.id());
        return List.copyOf(names);
    }

    public List<Integer> inventorySlots(String name) {
        var names = inventoryNames();
        return java.util.stream.IntStream.range(0, names.size())
            .filter(i -> names.get(i).equals(name)).boxed().toList();
    }

    public record Slot(String id, Role role, int limit) {
    }

    public record Tank(String id, Role role, int capacity,
        ResourceLocation fluid) {
    }

    public record Fluids(String id, List<Tank> tanks, List<Group> groups) {
        public Fluids {
            tanks = List.copyOf(tanks);
            groups = List.copyOf(groups);
        }
    }

    public record Group(String id, List<Integer> members, IoMode mode,
        int color) {
        public Group {
            members = List.copyOf(members);
        }
    }

    public record RecipeType(ResourceLocation id, boolean declare, String name,
        Generator generator) {
    }

    public record Generator(String className, Map<String, String> properties,
        String source, int line, ClassLoader loader) {
        public Generator {
            properties = Map.copyOf(properties);
        }
    }
}
