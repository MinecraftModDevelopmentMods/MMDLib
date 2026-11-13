package zone.moddev.mc.lib.machine.definition;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.machine.presentation.BuiltinVisualVariables;
import zone.moddev.mc.lib.machine.presentation.ConditionalFaceTexture;
import zone.moddev.mc.lib.machine.presentation.ConditionalModelPart;

/**
 * Immutable, resolved startup configuration. Item storage, power and processing
 * are optional. A processor is either recipe-backed or a mod-owned declaration.
 */
public record MachineDefinition(ResourceLocation id, String checksum,
    String name, float hardness, ResourceLocation mineable,
    boolean requiresTool, Map<String, String> textures,
    ResourceLocation baseModel, List<ConditionalModelPart> modelParts,
    List<ConditionalFaceTexture> faceTextures, Power power, Fluids fluids,
    String itemStorageId, List<Slot> slots, List<Group> groups,
    RecipeType recipeType, String processor, String processorName,
    boolean redstone, Node screen, RecipeSelection recipeSelection,
    List<Ghost> ghosts, MachineBehavior behavior,
    Map<String, String> groupNames) {
    public MachineDefinition(ResourceLocation id, String checksum, String name,
        float hardness, ResourceLocation mineable, boolean requiresTool,
        Map<String, String> textures, ResourceLocation baseModel,
        List<ConditionalModelPart> modelParts,
        List<ConditionalFaceTexture> faceTextures, Power power, Fluids fluids,
        String itemStorageId, List<Slot> slots, List<Group> groups,
        RecipeType recipeType, String processor, String processorName,
        boolean redstone, Node screen, RecipeSelection recipeSelection,
        List<Ghost> ghosts, MachineBehavior behavior) {
        this(id, checksum, name, hardness, mineable, requiresTool, textures,
            baseModel, modelParts, faceTextures, power, fluids, itemStorageId,
            slots, groups, recipeType, processor, processorName, redstone,
            screen, recipeSelection, ghosts, behavior, Map.of());
    }
    public MachineDefinition(ResourceLocation id, String checksum, String name,
        float hardness, ResourceLocation mineable, boolean requiresTool,
        Map<String, String> textures, ResourceLocation baseModel,
        List<ConditionalModelPart> modelParts,
        List<ConditionalFaceTexture> faceTextures, Power power, Fluids fluids,
        String itemStorageId, List<Slot> slots, List<Group> groups,
        RecipeType recipeType, String processor, String processorName,
        boolean redstone, Node screen, RecipeSelection recipeSelection,
        List<Ghost> ghosts) {
        this(id, checksum, name, hardness, mineable, requiresTool, textures,
            baseModel, modelParts, faceTextures, power, fluids, itemStorageId,
            slots, groups, recipeType, processor, processorName, redstone,
            screen, recipeSelection, ghosts, MachineBehavior.DEFAULT);
    }

    public record Ghost(String id, int count, ResourceLocation defaultItem,
        String name) {
        public Ghost(String id, int count, ResourceLocation defaultItem) {
            this(id, count, defaultItem, id);
        }
    }

    /**
     * Inputs that are ghost samples on every XML machine using this recipe
     * type. Shared recipe displays must not label a real input as a sample when
     * machines disagree about its storage.
     */
    public static Set<String> ghostInputsFor(
        Collection<MachineDefinition> definitions,
        ResourceLocation recipeType) {
        Set<String> result = null;
        for (var machine : definitions) {
            if (!machine.processes(recipeType))
                continue;
            var ghosts = new HashSet<String>();
            machine.ghosts().forEach(ghost -> ghosts.add(ghost.id()));
            if (result == null)
                result = ghosts;
            else
                result.retainAll(ghosts);
        }
        return result == null ? Set.of() : Set.copyOf(result);
    }

    public MachineDefinition(ResourceLocation id, String checksum, String name,
        float hardness, ResourceLocation mineable, boolean requiresTool,
        Map<String, String> textures, ResourceLocation baseModel,
        List<ConditionalModelPart> modelParts,
        List<ConditionalFaceTexture> faceTextures, Power power, Fluids fluids,
        String itemStorageId, List<Slot> slots, List<Group> groups,
        RecipeType recipeType, String processor, String processorName,
        boolean redstone, Node screen, RecipeSelection recipeSelection) {
        this(id, checksum, name, hardness, mineable, requiresTool, textures,
            baseModel, modelParts, faceTextures, power, fluids, itemStorageId,
            slots, groups, recipeType, processor, processorName, redstone,
            screen, recipeSelection, List.of());
    }
    public record RecipeSelection(ResourceLocation defaultRecipe) {
    }

    public MachineDefinition(ResourceLocation id, String checksum, String name,
        float hardness, ResourceLocation mineable, boolean requiresTool,
        Map<String, String> textures, ResourceLocation baseModel,
        List<ConditionalModelPart> modelParts,
        List<ConditionalFaceTexture> faceTextures, Power power, Fluids fluids,
        String itemStorageId, List<Slot> slots, List<Group> groups,
        RecipeType recipeType, String processor, String processorName,
        boolean redstone, Node screen) {
        this(id, checksum, name, hardness, mineable, requiresTool, textures,
            baseModel, modelParts, faceTextures, power, fluids, itemStorageId,
            slots, groups, recipeType, processor, processorName, redstone,
            screen, null);
    }
    /**
     * Original 2.0.x constructor, with automatic recipes and no optional
     * visuals or ghosts.
     */
    public MachineDefinition(ResourceLocation id, String checksum, String name,
        float hardness, ResourceLocation mineable, boolean requiresTool,
        Map<String, String> textures, Power power, Fluids fluids,
        String itemStorageId, List<Slot> slots, List<Group> groups,
        RecipeType recipeType, String processor, String processorName,
        boolean redstone, Node screen) {
        this(id, checksum, name, hardness, mineable, requiresTool, textures,
            null, List.of(), List.of(), power, fluids, itemStorageId, slots,
            groups, recipeType, processor, processorName, redstone, screen,
            null, List.of());
    }

    /** Cube-front configuration with conditional parts and textures. */
    public MachineDefinition(ResourceLocation id, String checksum, String name,
        float hardness, ResourceLocation mineable, boolean requiresTool,
        Map<String, String> textures, List<ConditionalModelPart> modelParts,
        List<ConditionalFaceTexture> faceTextures, Power power, Fluids fluids,
        String itemStorageId, List<Slot> slots, List<Group> groups,
        RecipeType recipeType, String processor, String processorName,
        boolean redstone, Node screen) {
        this(id, checksum, name, hardness, mineable, requiresTool, textures,
            null, modelParts, faceTextures, power, fluids, itemStorageId, slots,
            groups, recipeType, processor, processorName, redstone, screen);
    }

    public MachineDefinition {
        if (behavior.processor() != null
            && (recipeType != null || processor == null || processor.isBlank()))
            throw new IllegalArgumentException(
                "Custom processors require an ID and no recipe type");
        if (behavior.processor() == null
            && (recipeType == null) != (processor == null))
            throw new IllegalArgumentException(
                "recipeType and processor must be supplied together");
        ghosts = List.copyOf(ghosts);
        textures = Map.copyOf(textures);
        modelParts = List.copyOf(modelParts);
        faceTextures = List.copyOf(faceTextures);
        slots = List.copyOf(slots);
        groups = List.copyOf(groups);
        // Derived from the immutable declarations, including for canonical
        // callers.
        var names = new LinkedHashMap<String, String>();
        groups.forEach(group -> names.put(group.id(), group.name()));
        ghosts.forEach(ghost -> names.put(ghost.id(), ghost.name()));
        if (power != null)
            names.put(power.id(), power.name());
        if (fluids != null)
            fluids.groups()
                .forEach(group -> names.put(group.id(), group.name()));
        groupNames = Collections.unmodifiableMap(names);
    }

    public boolean hasProcessor() {
        return processor != null;
    }

    /**
     * Optional processing type; prefer this over dereferencing the nullable
     * record accessor.
     */
    public Optional<RecipeType> processingType() {
        return Optional.ofNullable(recipeType);
    }

    public boolean processes(ResourceLocation type) {
        return recipeType != null && recipeType.id().equals(type);
    }

    public Map<String, Object> visualDefaults() {
        return visualDefaults(null);
    }

    public Map<String, Object> visualDefaults(
        zone.moddev.mc.lib.machine.feature.Processor<?> prototype) {
        return BuiltinVisualVariables
            .forMachine(this, prototype, null, null, null).defaults();
    }

    public boolean hasConditionalAppearance() {
        return !modelParts.isEmpty() || !faceTextures.isEmpty();
    }

    public List<String> visualConditions() {
        return java.util.stream.Stream
            .concat(modelParts.stream().map(ConditionalModelPart::when),
                faceTextures.stream().map(ConditionalFaceTexture::when))
            .toList();
    }

    public record Power(String id, long capacity, long input, long output,
        int color, String name) {
        public Power(String id, long capacity, long input, long output,
            int color) {
            this(id, capacity, input, output, color, "Energy");
        }
    }

    /** Stable localization key, independent of the English display name. */
    public String groupTranslationKey(String group) {
        return id + ".group." + group;
    }

    /** Keep the component translatable until it reaches the viewer. */
    public Component groupName(String group) {
        return Component.translatableWithFallback(groupTranslationKey(group),
            groupNames().getOrDefault(group, group));
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
        int color, boolean automation, String name) {
        public Group(String id, List<Integer> members, IoMode mode, int color,
            boolean automation) {
            this(id, members, mode, color, automation, id);
        }
        public Group(String id, List<Integer> members, IoMode mode, int color) {
            this(id, members, mode, color, true);
        }
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
