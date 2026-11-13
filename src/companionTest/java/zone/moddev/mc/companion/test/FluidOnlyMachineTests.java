package zone.moddev.mc.companion.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class FluidOnlyMachineTests {
    private static DefinedMachineEntity place(GameTestHelper h, String name) {
        var id = ResourceLocation.parse("mmdlib_companion:test/" + name);
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            BuiltInRegistries.BLOCK.get(id).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        require(machine != null, "XML-only fixture registered and placed");
        require(machine.items.getSlots() == 0, "No dummy item slots");
        require(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos,
            null) == null, "No empty item capability advertised");
        var player = h.makeMockServerPlayerInLevel();
        new MachineMenu(1, player.getInventory(), machine);
        return machine;
    }

    @GameTest(template = "empty")
    public static void fluidOnlyProcessorConsumesAndProducesFluid(
        GameTestHelper h) {
        var machine = place(h, "fluid_still");
        require(
            machine.fluids.fill(new FluidStack(Fluids.WATER, 250),
                FluidAction.EXECUTE) == 250,
            "Recipe-linked fluid input accepts water without item storage");
        for (int i = 0; i < 4; i++)
            machine.serverTick();
        require(machine.fluids.getFluidInTank(0).isEmpty(),
            "Fluid input consumed");
        require(
            machine.fluids.getFluidInTank(1).is(Fluids.LAVA)
                && machine.fluids.getFluidInTank(1).getAmount() == 100,
            "Fluid product produced");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void plainTankAutomatesOpensAndPersistsWithoutProcessor(
        GameTestHelper h) {
        var machine = place(h, "fluid_tank");
        require(machine.definition.recipeType() == null,
            "No recipe type fabricated");
        var screen = Screens.get(machine.definition.id()).orElseThrow()
            .screen();
        require(
            screen.find("generated_progress").isEmpty()
                && screen.find("toggle_0").isEmpty(),
            "No processor UI generated for a tank");
        var handler = h.getLevel().getCapability(
            Capabilities.FluidHandler.BLOCK, machine.getBlockPos(), null);
        require(
            handler != null && handler.fill(new FluidStack(Fluids.WATER, 1000),
                FluidAction.EXECUTE) == 1000,
            "Tank accepts fluid without recipe filtering");
        machine.serverTick();
        var saved = machine.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = new DefinedMachineEntity(machine.getType(),
            machine.getBlockPos(), machine.getBlockState(), machine.definition,
            null);
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        require(restored.fluids.getFluidInTank(0).getAmount() == 1000,
            "Tank contents survive reload even with fluid storage named items");
        require(
            restored.fluids.drain(250, FluidAction.EXECUTE).getAmount() == 250,
            "Tank contents remain extractable");
        h.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
