package zone.moddev.mc.companion.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.companion.AdmissionDemo;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class PortingHooksTests {
    static DefinedMachineEntity place(GameTestHelper h, String name) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("mmdlib_companion:" + name))
                .defaultBlockState(),
            3);
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
    }

    @GameTest(template = "empty")
    public static void typedResourceTransactionsRollbackBlockedOutput(
        GameTestHelper h) {
        var machine = place(h, "porting_workshop");
        machine.items.insertItem(0, new ItemStack(Items.COBBLESTONE, 2), false);
        machine.fluids.fill(new FluidStack(Fluids.WATER, 100),
            FluidAction.EXECUTE);
        machine.items.insertResult(1, new ItemStack(Items.DIRT, 64), false);
        machine.serverTick();
        h.assertTrue(machine.items.getStackInSlot(0).getCount() == 2,
            "Blocked output rolls back item input");
        h.assertTrue(machine.fluids.getFluidInTank(0).getAmount() == 100,
            "Blocked output rolls back fluid input");
        machine.items.extractItem(1, 64, false);
        machine.serverTick();
        h.assertTrue(machine.items.getStackInSlot(1).is(Items.STONE),
            "Custom processor inserts its product");
        h.assertTrue(machine.items.getStackInSlot(0).getCount() == 1,
            "One input consumed");
        h.assertTrue(machine.fluids.getFluidInTank(0).getAmount() == 75,
            "Fluid consumed in same transaction");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void admissionRulesRejectWrongInputsAndFollowConfig(
        GameTestHelper h) {
        var machine = place(h, "admission_demo");
        h.assertTrue(machine.items
            .insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1,
            "Fuel slot refuses nonfuel");
        h.assertTrue(machine.items
            .insertItem(0, new ItemStack(Items.COAL), true).isEmpty(),
            "Fuel simulation accepts coal");
        h.assertTrue(machine.items.getStackInSlot(0).isEmpty(),
            "Simulation does not insert");
        h.assertTrue(machine.items
            .insertItem(1, new ItemStack(Items.RAW_IRON), false).isEmpty(),
            "Smelting input accepted");
        h.assertTrue(machine.items
            .insertItem(2, new ItemStack(Items.STONE), false).getCount() == 1,
            "Item tag enforced");
        h.assertTrue(
            !machine.fluids.isFluidValid(0, new FluidStack(Fluids.LAVA, 100)),
            "Fluid tag enforced");
        var old = AdmissionDemo.fuelTag;
        try {
            AdmissionDemo.fuelTag = ResourceLocation.parse("minecraft:lava");
            h.assertTrue(
                machine.fluids.isFluidValid(1,
                    new FluidStack(Fluids.LAVA, 100)),
                "Configured tag accepts lava");
            h.assertTrue(machine.fluids.fill(new FluidStack(Fluids.LAVA, 100),
                FluidAction.EXECUTE) == 100, "Fuel inserted");
            AdmissionDemo.fuelTag = ResourceLocation.parse("minecraft:water");
            h.assertTrue(
                !machine.fluids.isFluidValid(1,
                    new FluidStack(Fluids.LAVA, 100)),
                "Live config changes admission");
            h.assertTrue(machine.fluids.getFluidInTank(1).getAmount() == 100,
                "Existing contents retained");
            h.assertTrue(
                machine.fluids.isFluidValid(2,
                    new FluidStack(Fluids.LAVA, 100)),
                "Custom recipe lookup accepts recipe fluid");
            h.assertTrue(
                !machine.fluids.isFluidValid(2,
                    new FluidStack(Fluids.WATER, 100)),
                "Recipe lookup rejects unrelated fluid");
        } finally {
            AdmissionDemo.fuelTag = old;
        }
        h.succeed();
    }
}
