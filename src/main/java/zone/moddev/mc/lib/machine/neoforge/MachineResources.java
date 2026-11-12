package zone.moddev.mc.lib.machine.neoforge;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStore;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.SlotGroup;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** One definition of resource membership, adapted to automation and recipe access. */
public final class MachineResources {
    private record Binding(ResourceGroup<?> group, Object storage) {
    }

    private final BlockEntity owner;
    private final Supplier<Direction> facing;
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Map<String, List<Binding>> recipeBindings = new LinkedHashMap<>();
    private final Map<String, Set<MachineRecipeType>> inputTypes = new LinkedHashMap<>();
    private final Map<String, SlotGroup> recipeOnly = new LinkedHashMap<>();
    private MachineInventory recipes;
    private AutomationInventory automation;

    public MachineResources(BlockEntity owner, Supplier<Direction> facing) {
        this.owner = Objects.requireNonNull(owner);
        this.facing = Objects.requireNonNull(facing);
    }

    public MachineResources items(ResourceGroup<IItemHandler> group, ItemStore storage) {
        check(group, storage.getSlots(), storage::role);
        add(group, storage);
        group.bind(
            facing,
            side -> storage
                .view(index -> group.contains(index) ? group.mode(side) : IoMode.DISABLED));
        return this;
    }

    public MachineResources fluids(ResourceGroup<IFluidHandler> group, FluidStorage storage) {
        check(group, storage.getTanks(), storage::role);
        add(group, storage);
        group.bind(
            facing,
            side -> storage
                .view(index -> group.contains(index) ? group.mode(side) : IoMode.DISABLED));
        return this;
    }

    public MachineResources energy(ResourceGroup<IEnergyStorage> group, EnergyBuffer storage) {
        check(group, 1, index -> Role.STORAGE);
        add(group, storage);
        group.bind(facing, side -> storage.view(() -> group.mode(side)));
        return this;
    }

    private void check(ResourceGroup<?> group, int size,
        java.util.function.IntFunction<Role> role) {
        ensureOpen();
        if (group.members().isEmpty())
            throw invalid(group.id(), "no members");
        for (int index : group.members()) {
            if (index >= size)
                throw invalid(group.id(), "member " + index + " outside storage of size " + size);
            var allowed = role.apply(index);
            if ((group.supported().canInsert() && !allowed.canInsert())
                || (group.supported().canExtract() && !allowed.canExtract()))
                throw invalid(
                    group.id(),
                    "member " + index + " role " + allowed + " conflicts with "
                        + group.supported());
        }
    }

    private void add(ResourceGroup<?> group, Object storage) {
        if (recipeBindings.containsKey(group.id()))
            throw invalid(group.id(), "duplicate ID");
        String inventory = group.inventory().isEmpty() ? group.id() : group.inventory();
        for (var binding : bindings.values()) {
            if (binding.group.type() != group.type())
                continue;
            if (storage instanceof ItemStore items
                && binding.storage instanceof ItemStore other
                && group.members().stream()
                    .anyMatch(
                        member -> binding.group.members().stream()
                            .anyMatch(
                                previous -> items.storage(member) == other.storage(previous)
                                    && items.localSlot(member) == other.localSlot(previous))))
                throw invalid(group.id(), "overlapping members");
            if (binding.storage != storage)
                continue;
            inventory = binding.group.inventory();
            if (group.members().stream().anyMatch(binding.group::contains))
                throw invalid(group.id(), "overlapping members");
        }
        if (group.type() == ResourceType.ITEMS)
            group.inventory(inventory);
        var binding = new Binding(group, storage);
        bindings.put(group.id(), binding);
        recipeBindings.put(group.id(), List.of(binding));
    }

    /** Derives an alternate recipe group, optionally combining several automation groups. */
    public MachineResources recipeGroup(String key, String... groupIds) {
        ensureOpen();
        if (key == null || key.isBlank() || groupIds.length == 0)
            throw invalid(key, "empty recipe mapping");
        if (recipeBindings.containsKey(key))
            throw invalid(key, "duplicate recipe binding");
        var selected = new ArrayList<Binding>();
        for (String id : groupIds) {
            var binding = bindings.get(id);
            if (binding == null)
                throw invalid(key, "unknown resource group " + id);
            if (selected.contains(binding))
                throw invalid(key, "duplicate resource group " + id);
            if (!selected.isEmpty()
                && selected.getFirst().storage != binding.storage
                && !(selected.getFirst().storage instanceof ItemStore
                    && binding.storage instanceof ItemStore))
                throw invalid(key, "recipe group must use one storage");
            selected.add(binding);
        }
        recipeBindings.put(key, List.copyOf(selected));
        return this;
    }

    /**
     * Links only input groups; several recipe types form a union, intersected with storage rules.
     */
    public MachineResources recipeInputs(MachineRecipeType type, String... keys) {
        ensureOpen();
        for (String key : keys) {
            if (!recipeBindings.containsKey(key))
                throw invalid(key, "unknown recipe input group");
            inputTypes.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(type);
        }
        return this;
    }

    /** Adds an internal recipe group without exposing physical automation slots. */
    public MachineResources recipeOnlyGroup(String key, SlotGroup group) {
        ensureOpen();
        if (key == null || key.isBlank() || recipeBindings.containsKey(key)
            || recipeOnly.putIfAbsent(key, Objects.requireNonNull(group)) != null)
            throw invalid(key, "duplicate or empty recipe-only group");
        return this;
    }

    /** Freezes the definitions and installs reload-aware recipe input filters. */
    public MachineResources build() {
        ensureOpen();
        var builder = MachineInventory.builder();
        recipeBindings.forEach(
            (key, values) -> {
                var storage = values.getFirst().storage;
                int[] members = values.stream()
                    .flatMap(b -> b.group.members().stream())
                    .mapToInt(Integer::intValue)
                    .toArray();
                SlotGroup group = storage instanceof ItemStore
                    ? itemGroup(values)
                    : storage instanceof FluidStorage fluids
                        ? StorageGroups.fluids(fluids, members)
                        : StorageGroups.energy((EnergyBuffer) storage);
                builder.group(key, group);
            });
        recipeOnly.forEach(builder::group);
        recipes = builder.build();
        automation = new AutomationInventory(owner,
            bindings.values().stream().map(Binding::group).toList());
        var filters = new RecipeInputFilters(() -> owner.getLevel(), recipes);
        // Collect by physical slot before installing: overlapping recipe aliases accept the union.
        var itemRules = new IdentityHashMap<ItemStore, Map<Integer, List<RecipeInputFilters.Link>>>();
        var fluidRules = new IdentityHashMap<FluidStorage, Map<Integer, List<RecipeInputFilters.Link>>>();
        inputTypes.forEach(
            (key, types) -> {
                for (var binding : recipeBindings.get(key)) {
                    for (int member : binding.group.members()) {
                        var links = types.stream()
                            .map(type -> new RecipeInputFilters.Link(type, key)).toList();
                        if (binding.storage instanceof ItemStore items)
                            itemRules
                                .computeIfAbsent(items, ignored -> new LinkedHashMap<>())
                                .computeIfAbsent(member, ignored -> new ArrayList<>())
                                .addAll(links);
                        else if (binding.storage instanceof FluidStorage fluids)
                            fluidRules
                                .computeIfAbsent(fluids, ignored -> new LinkedHashMap<>())
                                .computeIfAbsent(member, ignored -> new ArrayList<>())
                                .addAll(links);
                        else
                            throw invalid(key, "recipe input filtering requires items or fluids");
                    }
                }
            });
        itemRules.forEach(
            (storage, rules) -> rules.forEach(
                (slot, links) -> storage.restrict(stack -> filters.acceptsItem(links, stack),
                    slot)));
        fluidRules.forEach(
            (storage, rules) -> rules.forEach(
                (tank, links) -> storage.restrict(stack -> filters.acceptsFluid(links, stack),
                    tank)));
        return this;
    }

    public MachineInventory recipes() {
        requireBuilt();
        return recipes;
    }

    private static ItemGroup itemGroup(List<Binding> bindings) {
        var names = new IdentityHashMap<ItemStorage, String>();
        var physical = new LinkedHashMap<String, ItemStorage>();
        for (var binding : bindings) {
            var store = (ItemStore) binding.storage;
            for (int member : binding.group.members()) {
                var storage = store.storage(member);
                if (!names.containsKey(storage)) {
                    String name = "inventory_" + names.size();
                    names.put(storage, name);
                    physical.put(name, storage);
                }
            }
        }
        var combined = new ItemInventories(physical);
        var members = new ArrayList<Integer>();
        for (var binding : bindings) {
            var store = (ItemStore) binding.storage;
            for (int member : binding.group.members())
                members
                    .add(combined.index(names.get(store.storage(member)), store.localSlot(member)));
        }
        return StorageGroups.items(combined,
            members.stream().mapToInt(Integer::intValue).toArray());
    }

    public AutomationInventory automation() {
        requireBuilt();
        return automation;
    }

    private void ensureOpen() {
        if (recipes != null)
            throw new IllegalStateException("Resource definitions already built");
    }

    private void requireBuilt() {
        if (recipes == null)
            throw new IllegalStateException("Build resource definitions first");
    }

    private IllegalArgumentException invalid(String group, String detail) {
        return new IllegalArgumentException(
            owner.getClass().getSimpleName() + " resource group '" + group + "': " + detail);
    }
}
