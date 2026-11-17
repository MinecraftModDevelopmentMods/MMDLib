package zone.moddev.mc.companion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/** A small transactional consumer of typed host resources; no entity cast. */
public final class WorkshopProcessor extends StoredProcessor
    implements zone.moddev.mc.lib.api.redstone.IRedstoneOutput {
    private final ProcessorState.Key<Integer> placements = variables.define(
        "placements", com.mojang.serialization.Codec.INT, 0,
        java.util.function.UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<String> owner = variables.define("owner",
        com.mojang.serialization.Codec.STRING, "",
        java.util.function.UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<String> team = variables.define("team",
        com.mojang.serialization.Codec.STRING, "",
        java.util.function.UnaryOperator.identity(), false, true);

    @Override
    public java.util.List<zone.moddev.mc.lib.api.redstone.RedstoneOutput> redstoneOutputs() {
        return java.util.List
            .of(new zone.moddev.mc.lib.api.redstone.RedstoneOutput(
                "workshop/output",
                net.minecraft.network.chat.Component.literal("Workshop output"),
                net.minecraft.network.chat.Component
                    .literal("Stone output fullness"),
                0xc07040, java.util.Optional.empty(),
                () -> machine == null
                    ? 0
                    : zone.moddev.mc.lib.api.redstone.RedstoneControl.level(
                        machine.resources().items("output").peek(0).getCount()
                            / 64.0)));
    }

    @Override
    public net.minecraft.core.Direction placementFacing(
        net.minecraft.world.item.context.BlockPlaceContext context,
        net.minecraft.core.Direction suggested) {
        // Sneak placement demonstrates a consumer choosing the clicked face.
        return context.getPlayer() != null
            && context.getPlayer().isShiftKeyDown()
                ? context.getClickedFace()
                : suggested;
    }

    @Override
    protected void onPlaced(
        zone.moddev.mc.lib.machine.feature.MachinePlacement placement) {
        variables.set(placements, variables.get(placements) + 1);
        var player = placement.placer();
        variables.set(owner, player == null ? "" : player.getUUID().toString());
        variables.set(team,
            player == null || player.getTeam() == null
                ? ""
                : player.getTeam().getName());
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        var resources = host.resources();
        return resources.items("input").peek(0).is(Items.COBBLESTONE)
            && resources.items("input").free(0) > 0
            && resources.fluids("water").peek(0).fluid().value() == Fluids.WATER
            && resources.fluids("water").free(0) >= 25;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host,
        int ticks) {
        var resources = host.resources();
        for (int step = 0; step < ticks; step++) {
            if (!canStart(state, host))
                return ProcessState.IDLE;
            try (var tx = resources.begin()) {
                if (resources.items("input").extract(0, 1, tx) != 1
                    || resources.fluids("water").extract(0, 25, tx) != 25
                    || resources.items("output")
                        .insert(new ItemStack(Items.STONE), tx) != 1)
                    return ProcessState.BLOCKED;
                tx.commit();
            }
        }
        return ProcessState.RUNNING;
    }
}
