#loader mmdlib
import mods.mmdlib.MachineHandlers;

// Cycle handlers for the test machine mmdlib:test/script_machine, which names them
// in its <processor>. Each copies the block under the machine to a spot above it,
// but only for the expected machine and recipe, for the game test to check.
MachineHandlers.onStart("test/machine_start", (cycle) => {
    if (cycle.machineId == "mmdlib:test/script_machine" && cycle.recipe == "mmdlib:test/stick_to_apple" && cycle.time == 2) {
        cycle.level.setBlockAndUpdate(cycle.pos.above(1), cycle.level.getBlockState(cycle.pos.below()));
    }
});

MachineHandlers.onFinish("test/machine_finish", (cycle) => {
    if (cycle.machineId == "mmdlib:test/script_machine" && cycle.recipe == "mmdlib:test/stick_to_apple") {
        cycle.level.setBlockAndUpdate(cycle.pos.above(2), cycle.level.getBlockState(cycle.pos.below()));
    }
});
