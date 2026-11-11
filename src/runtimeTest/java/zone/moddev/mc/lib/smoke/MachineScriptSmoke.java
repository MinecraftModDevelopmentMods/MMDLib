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

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Machine script smoke failed: " + what);
    }
}
