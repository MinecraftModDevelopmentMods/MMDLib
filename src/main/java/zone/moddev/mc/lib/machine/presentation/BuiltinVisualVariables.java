package zone.moddev.mc.lib.machine.presentation;

import java.util.List;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.api.capabilities.ItemStore;
import zone.moddev.mc.lib.api.util.ChangeSignal;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;

/**
 * Reusable component adapters. Null components declare the same schema with
 * idle defaults.
 */
public final class BuiltinVisualVariables {
    private BuiltinVisualVariables() {
    }

    public static VisualVariableProvider processor(RecipeProcessor processor) {
        return vars -> vars
            .observedBy(processor == null
                ? new ChangeSignal[0]
                : new ChangeSignal[]{processor.activityChanges()})
            .add("running", false,
                () -> processor != null && processor.processing())
            .add("status", "idle",
                () -> processor == null
                    ? "idle"
                    : processor.clientState().status()
                        .orElse(RecipeProcessor.Status.IDLE).name()
                        .toLowerCase(java.util.Locale.ROOT))
            .add("paused", false, () -> processor != null && processor.paused())
            .observedBy(processor == null
                ? new ChangeSignal[0]
                : new ChangeSignal[]{processor.progressChanges()})
            .add("progress", 0.0, () -> processorProgress(processor));
    }

    private static double processorProgress(RecipeProcessor processor) {
        if (processor == null)
            return 0;
        var state = processor.clientState();
        int duration = state.cycle().map(cycle -> cycle.time())
            .orElse(state.resumeTime());
        return duration <= 0
            ? 0
            : Math.min(1.0, (double) state.progress() / duration);
    }

    public static VisualVariableProvider items(ItemStore items,
        List<Integer> slots) {
        var members = List.copyOf(slots);
        var sources = items == null
            ? new ChangeSignal[0]
            : members.stream().map(items::storage).distinct()
                .map(store -> store.changes()).toArray(ChangeSignal[]::new);
        return vars -> vars.observedBy(sources)
            .add("count", 0.0, () -> items == null
                ? 0.0
                : members.stream()
                    .mapToDouble(i -> items.getStackInSlot(i).getCount()).sum())
            .add("fill", 0.0, () -> itemFill(items, members))
            .add("empty", true,
                () -> items == null || members.stream()
                    .allMatch(i -> items.getStackInSlot(i).isEmpty()))
            .add("full", false,
                () -> items != null && itemFill(items, members) >= 1.0);
    }

    private static double itemFill(ItemStore items, List<Integer> slots) {
        if (items == null || slots.isEmpty())
            return 0;
        return slots.stream().mapToDouble(i -> {
            var stack = items.getStackInSlot(i);
            int limit = Math.min(items.getSlotLimit(i),
                stack.getMaxStackSize());
            return stack.isEmpty() || limit <= 0
                ? 0
                : Math.min(1.0, (double) stack.getCount() / limit);
        }).average().orElse(0);
    }

    public static VisualVariableProvider tank(FluidStorage fluids, int tank) {
        return vars -> vars
            .observedBy(fluids == null
                ? new ChangeSignal[0]
                : new ChangeSignal[]{fluids.changes()})
            .add("amount", 0.0,
                () -> fluids == null
                    ? 0.0
                    : fluids.getFluidInTank(tank).getAmount())
            .add("capacity", 0.0,
                () -> fluids == null ? 0.0 : fluids.getTankCapacity(tank))
            .add("fill", 0.0,
                () -> fluids == null || fluids.getTankCapacity(tank) <= 0
                    ? 0.0
                    : Math.min(1.0,
                        (double) fluids.getFluidInTank(tank).getAmount()
                            / fluids.getTankCapacity(tank)))
            .add("empty", true,
                () -> fluids == null || fluids.getFluidInTank(tank).isEmpty());
    }

    public static VisualVariableProvider energy(EnergyBuffer energy) {
        return vars -> vars
            .observedBy(energy == null
                ? new ChangeSignal[0]
                : new ChangeSignal[]{energy.changes()})
            .add("stored", 0.0, () -> energy == null ? 0.0 : energy.stored())
            .add("capacity", 0.0,
                () -> energy == null ? 0.0 : energy.capacity())
            .add("fill", 0.0, () -> energy == null || energy.capacity() <= 0
                ? 0.0
                : Math.min(1.0, (double) energy.stored() / energy.capacity()));
    }

    /**
     * XML machine assembly uses the same providers for load-time validation and
     * server sampling.
     */
    public static VisualVariables forMachine(MachineDefinition definition,
        RecipeProcessor processor, ItemInventories items, FluidStorage fluids,
        EnergyBuffer energy) {
        var vars = new VisualVariables.Builder();
        if (definition.processor() != null)
            vars.component("processors." + definition.processor(),
                processor == null ? processor(null) : processor);
        for (var group : definition.groups()) {
            var indexes = definition.inventorySlots(group.id());
            vars.component("inventories." + group.id(),
                items(items == null ? null : items.inventory(group.id()),
                    group.members().stream().map(indexes::indexOf).toList()));
        }
        if (definition.fluids() != null)
            for (int i = 0; i < definition.fluids().tanks().size(); i++)
                vars.component(
                    "tanks." + definition.fluids().tanks().get(i).id(),
                    tank(fluids, i));
        if (definition.power() != null)
            vars.component("energy." + definition.power().id(), energy(energy));
        return vars.build();
    }
}
