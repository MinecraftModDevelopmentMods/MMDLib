package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.common.util.FakePlayerFactory;

import zone.moddev.mc.lib.MMDLib;

/**
 * Standalone fluids: {@code test/syrup} from test resources'
 * {@code mmdlib/standalone/fluid/}, on the plain {@code mmdlib:fluid} module;
 * the test script {@code mmdlib_fluids.zs}'s {@code test/chocolate} on the same
 * module, and {@code test/plasma} from {@code mmdlib:molten} with no block.
 */
final class FluidSmoke {
    private FluidSmoke() {
    }

    /**
     * The data file's fluid family is registered, with its own values over the
     * module's, and its bucket places it and picks it back up.
     */
    static void fromData(ServerLevel level, BlockPos floor) {
        var syrup = fluid("mmdlib:test/syrup");
        var type = syrup.getFluidType();
        require(
            type.getViscosity() == 4000 && type.getDensity() == 1400
                && type.getTemperature() == 300 && type.canSwim(null),
            "syrup keeps the plain module's values under its own");
        require(fluid("mmdlib:test/flowing_syrup").isSame(syrup),
            "the flowing form is test/flowing_syrup");
        require(
            BuiltInRegistries.BLOCK
                .get(id("mmdlib:test/syrup")) instanceof LiquidBlock,
            "syrup has its liquid block");
        var bucket = (BucketItem) BuiltInRegistries.ITEM
            .get(id("mmdlib:test/syrup_bucket"));
        var player = FakePlayerFactory.getMinecraft(level);
        var pos = floor.above();
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        require(bucket.emptyContents(player, level, pos, null,
            new ItemStack(bucket)), "the syrup bucket places syrup");
        require(level.getFluidState(pos).getType() == syrup,
            "the placed fluid is syrup");
        require(MMDLib.standaloneFluids().size() == 3,
            "three standalone fluids: " + MMDLib.standaloneFluids().keySet());
    }

    /** The script's fluids, with the values it set. */
    static void fromScript() {
        var chocolate = fluid("mmdlib:test/chocolate").getFluidType();
        require(chocolate.getViscosity() == 3000 && !chocolate.canSwim(null),
            "chocolate has the script's viscosity and no swimming");
        require(BuiltInRegistries.ITEM.containsKey(
            id("mmdlib:test/chocolate_bucket")), "chocolate has a bucket");
        var plasma = fluid("mmdlib:test/plasma").getFluidType();
        require(plasma.getLightLevel() == 15 && plasma.getTemperature() == 1300,
            "plasma glows, at the molten module's temperature");
        require(!BuiltInRegistries.BLOCK.containsKey(id("mmdlib:test/plasma")),
            "plasma has no block");
        require(
            BuiltInRegistries.ITEM.containsKey(id("mmdlib:test/plasma_bucket")),
            "plasma keeps its bucket");
    }

    private static Fluid fluid(String name) {
        var location = id(name);
        require(BuiltInRegistries.FLUID.containsKey(location),
            "fluid " + name + " is registered");
        return BuiltInRegistries.FLUID.get(location);
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.parse(name);
    }

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Fluid smoke failed: " + what);
    }
}
