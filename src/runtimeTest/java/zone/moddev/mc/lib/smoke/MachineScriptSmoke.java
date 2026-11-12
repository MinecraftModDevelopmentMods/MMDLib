package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * The test machine {@code mmdlib:test/script_machine} (test resources), whose
 * processor names the test script {@code mmdlib_machines.zs}'s handlers. The
 * start handler copies the glass under the machine one block above it, the
 * finish handler two above, each only for the expected machine and recipe.
 */
final class MachineScriptSmoke {
    private MachineScriptSmoke() {
    }

    static void cycleHandlers(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos,
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("mmdlib:test/script_machine"))
                .defaultBlockState(),
            3);
        var machine = (DefinedMachineEntity) level.getBlockEntity(pos);
        machine.items.insertItem(0, new ItemStack(Items.STICK), false);
        machine.serverTick();
        require(level.getBlockState(pos.above()).is(Blocks.GLASS),
            "the start handler ran, for the expected machine and recipe");
        require(level.getBlockState(pos.above(2)).isAir(),
            "the finish handler waits for the cycle to finish");
        for (int i = 0; i < 4; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(1).is(Items.APPLE),
            "the cycle finished: " + machine.items.getStackInSlot(1));
        require(level.getBlockState(pos.above(2)).is(Blocks.GLASS),
            "the finish handler ran, for the expected machine and recipe");
    }

    /**
     * The recipe script {@code machine_recipes.zs}: CraftTweaker's generic
     * manager for the machine's XML-declared recipe type added dirt to diamond
     * and removed the datapack recipe glass to sand, and the typed
     * {@code MachineRecipeBuilder} added two cobblestone to three gravel.
     */
    static void scriptRecipes(ServerLevel level, BlockPos pos) {
        var machine = place(level, pos);
        machine.items.insertItem(0, new ItemStack(Items.DIRT), false);
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(1).is(Items.DIAMOND),
            "the script's recipe ran: " + machine.items.getStackInSlot(1));
        machine.items.extractItem(1, 64, false);
        machine.items.extractItem(1, 64, false);
        machine.items.insertItem(0, new ItemStack(Items.COBBLESTONE, 2), false);
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(
            machine.items.getStackInSlot(1).is(Items.GRAVEL)
                && machine.items.getStackInSlot(1).getCount() == 3
                && machine.items.getStackInSlot(0).isEmpty(),
            "the typed recipe took two cobblestone and made three gravel: "
                + machine.items.getStackInSlot(1));
        machine.items.extractItem(1, 64, false);
        // With no recipe using glass, the input filter refuses it outright.
        var refused = machine.items.insertItem(0, new ItemStack(Items.GLASS),
            false);
        require(refused.is(Items.GLASS),
            "the input no longer accepts glass, since no recipe uses it");
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(1).isEmpty(),
            "the removed recipe did not run: "
                + machine.items.getStackInSlot(1));
        reusableAndInvalid(level, machine);
    }

    /**
     * The typed builder's reusable input keeps the bone, and its recipe with an
     * unknown group was refused.
     */
    private static void reusableAndInvalid(ServerLevel level,
        DefinedMachineEntity machine) {
        machine.items.insertItem(0, new ItemStack(Items.BONE), false);
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(
            machine.items.getStackInSlot(1).is(Items.BONE_MEAL)
                && machine.items.getStackInSlot(0).is(Items.BONE),
            "the reusable bone stayed and made bone meal: "
                + machine.items.getStackInSlot(0) + " -> "
                + machine.items.getStackInSlot(1));
        require(level.getRecipeManager()
            .byKey(ResourceLocation.parse("crafttweaker:unknown_group"))
            .isEmpty(), "the recipe with an unknown group was refused");
    }

    private static DefinedMachineEntity place(ServerLevel level, BlockPos pos) {
        level.setBlock(pos,
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("mmdlib:test/script_machine"))
                .defaultBlockState(),
            3);
        return (DefinedMachineEntity) level.getBlockEntity(pos);
    }

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Machine script smoke failed: " + what);
    }
}
