package zone.moddev.mc.lib.machine.neoforge;

import java.util.*;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.machine.inventory.*;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** One definition of resource membership, adapted to automation and recipe access. */
public final class MachineResources {
  private record Binding(ResourceGroup<?> group, Object storage) {}

  private final BlockEntity owner;
  private final Supplier<Direction> facing;
  private final Map<String, Binding> bindings = new LinkedHashMap<>();
  private final Map<String, List<Binding>> recipeBindings = new LinkedHashMap<>();
  private final Map<String, Set<MachineRecipeType>> inputTypes = new LinkedHashMap<>();
  private MachineInventory recipes;
  private AutomationInventory automation;

  public MachineResources(BlockEntity owner, Supplier<Direction> facing) {
    this.owner = Objects.requireNonNull(owner);
    this.facing = Objects.requireNonNull(facing);
  }

  public MachineResources items(ResourceGroup<IItemHandler> group, ItemStorage storage) {
    check(group, storage.getSlots(), storage::role);
    add(group, storage);
    group.bind(
        facing,
        side -> storage.view(index -> group.contains(index) ? group.mode(side) : IoMode.DISABLED));
    return this;
  }

  public MachineResources fluids(ResourceGroup<IFluidHandler> group, FluidStorage storage) {
    check(group, storage.getTanks(), storage::role);
    add(group, storage);
    group.bind(
        facing,
        side -> storage.view(index -> group.contains(index) ? group.mode(side) : IoMode.DISABLED));
    return this;
  }

  public MachineResources energy(ResourceGroup<IEnergyStorage> group, EnergyBuffer storage) {
    check(group, 1, index -> Role.STORAGE);
    add(group, storage);
    group.bind(facing, side -> storage.view(() -> group.mode(side)));
    return this;
  }

  private void check(ResourceGroup<?> group, int size, java.util.function.IntFunction<Role> role) {
    ensureOpen();
    if (group.members().isEmpty()) throw invalid(group.id(), "no members");
    for (int index : group.members()) {
      if (index >= size)
        throw invalid(group.id(), "member " + index + " outside storage of size " + size);
      var allowed = role.apply(index);
      if ((group.supported().canInsert() && !allowed.canInsert())
          || (group.supported().canExtract() && !allowed.canExtract()))
        throw invalid(
            group.id(),
            "member " + index + " role " + allowed + " conflicts with " + group.supported());
    }
  }

  private void add(ResourceGroup<?> group, Object storage) {
    if (recipeBindings.containsKey(group.id())) throw invalid(group.id(), "duplicate ID");
    // Membership is indexed by resource type, so a type must share one physical index space.
    for (var binding : bindings.values()) {
      if (binding.group.type() != group.type()) continue;
      if (binding.storage != storage)
        throw invalid(group.id(), "use a shared storage for this resource type");
      if (group.members().stream().anyMatch(binding.group::contains))
        throw invalid(group.id(), "overlapping members");
    }
    var binding = new Binding(group, storage);
    bindings.put(group.id(), binding);
    recipeBindings.put(group.id(), List.of(binding));
  }

  /** Derives an alternate recipe group, optionally combining several automation groups. */
  public MachineResources recipeGroup(String key, String... groupIds) {
    ensureOpen();
    if (key == null || key.isBlank() || groupIds.length == 0)
      throw invalid(key, "empty recipe mapping");
    if (recipeBindings.containsKey(key)) throw invalid(key, "duplicate recipe binding");
    var selected = new ArrayList<Binding>();
    for (String id : groupIds) {
      var binding = bindings.get(id);
      if (binding == null) throw invalid(key, "unknown resource group " + id);
      if (selected.contains(binding)) throw invalid(key, "duplicate resource group " + id);
      if (!selected.isEmpty() && selected.getFirst().storage != binding.storage)
        throw invalid(key, "recipe group must use one storage");
      selected.add(binding);
    }
    recipeBindings.put(key, List.copyOf(selected));
    return this;
  }

  /** Links only input groups; several recipe types form a union, intersected with storage rules. */
  public MachineResources recipeInputs(MachineRecipeType type, String... keys) {
    ensureOpen();
    for (String key : keys) {
      if (!recipeBindings.containsKey(key)) throw invalid(key, "unknown recipe input group");
      inputTypes.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(type);
    }
    return this;
  }

  /** Freezes the definitions and installs reload-aware recipe input filters. */
  public MachineResources build() {
    ensureOpen();
    var builder = MachineInventory.builder();
    recipeBindings.forEach(
        (key, values) -> {
          var storage = values.getFirst().storage;
          int[] members =
              values.stream()
                  .flatMap(b -> b.group.members().stream())
                  .mapToInt(Integer::intValue)
                  .toArray();
          SlotGroup group =
              storage instanceof ItemStorage items
                  ? StorageGroups.items(items, members)
                  : storage instanceof FluidStorage fluids
                      ? StorageGroups.fluids(fluids, members)
                      : StorageGroups.energy((EnergyBuffer) storage);
          builder.group(key, group);
        });
    recipes = builder.build();
    automation =
        new AutomationInventory(owner, bindings.values().stream().map(Binding::group).toList());
    var filters = new RecipeInputFilters(() -> owner.getLevel(), recipes);
    // Collect by physical slot before installing: overlapping recipe aliases accept the union.
    var itemRules = new IdentityHashMap<ItemStorage, Map<Integer, List<RecipeInputFilters.Link>>>();
    var fluidRules =
        new IdentityHashMap<FluidStorage, Map<Integer, List<RecipeInputFilters.Link>>>();
    inputTypes.forEach(
        (key, types) -> {
          for (var binding : recipeBindings.get(key)) {
            for (int member : binding.group.members()) {
              var links =
                  types.stream().map(type -> new RecipeInputFilters.Link(type, key)).toList();
              if (binding.storage instanceof ItemStorage items)
                itemRules
                    .computeIfAbsent(items, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(member, ignored -> new ArrayList<>())
                    .addAll(links);
              else if (binding.storage instanceof FluidStorage fluids)
                fluidRules
                    .computeIfAbsent(fluids, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(member, ignored -> new ArrayList<>())
                    .addAll(links);
              else throw invalid(key, "recipe input filtering requires items or fluids");
            }
          }
        });
    itemRules.forEach(
        (storage, rules) ->
            rules.forEach(
                (slot, links) ->
                    storage.restrict(stack -> filters.acceptsItem(links, stack), slot)));
    fluidRules.forEach(
        (storage, rules) ->
            rules.forEach(
                (tank, links) ->
                    storage.restrict(stack -> filters.acceptsFluid(links, stack), tank)));
    return this;
  }

  public MachineInventory recipes() {
    requireBuilt();
    return recipes;
  }

  public AutomationInventory automation() {
    requireBuilt();
    return automation;
  }

  private void ensureOpen() {
    if (recipes != null) throw new IllegalStateException("Resource definitions already built");
  }

  private void requireBuilt() {
    if (recipes == null) throw new IllegalStateException("Build resource definitions first");
  }

  private IllegalArgumentException invalid(String group, String detail) {
    return new IllegalArgumentException(
        owner.getClass().getSimpleName() + " resource group '" + group + "': " + detail);
  }
}
