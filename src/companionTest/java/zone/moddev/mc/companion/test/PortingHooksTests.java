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

@GameTestHolder("mmdlib_porting")
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
    public static void recipeLanesAndChargingRunTogether(GameTestHelper h) {
        var machine = place(h, "parallel_workshop");
        h.assertTrue(machine.features.allProcessors().size() == 3,
            "Recipe and custom processors coexist");
        machine.items.inventory("left_input").insertItem(0,
            new ItemStack(Items.COBBLESTONE), false);
        machine.items.inventory("right_input").insertItem(0,
            new ItemStack(Items.GRAVEL), false);
        machine.fluids.fill(new FluidStack(Fluids.WATER, 200),
            FluidAction.EXECUTE);
        for (int tick = 0; tick < 102; tick++)
            machine.serverTick();
        h.assertTrue(machine.items.inventory("left_output").getStackInSlot(0)
            .is(Items.GRAVEL), "Left lane output");
        h.assertTrue(machine.items.inventory("right_output").getStackInSlot(0)
            .is(Items.SAND), "Right lane output");
        h.assertTrue(machine.energy.stored() > 0,
            "Charge pump supplies shared energy beside recipes");
        var player = h.makeMockServerPlayerInLevel();
        new zone.moddev.mc.lib.gui.runtime.MachineMenu(1, player.getInventory(),
            machine);
        var saved = machine.saveWithFullMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(
            machine.features.processor("left").orElseThrow().completedCycles()
                .intValue() == 1
                && machine.features.processor("right").orElseThrow()
                    .completedCycles().intValue() == 1,
            "Lanes persist independently");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedInputCannotBeReservedTwice(GameTestHelper h) {
        var machine = place(h, "shared_workshop");
        machine.energy.setStored(10000);
        machine.items.inventory("left_input").insertItem(0,
            new ItemStack(Items.COBBLESTONE), false);
        for (int tick = 0; tick < 105; tick++)
            machine.serverTick();
        int products = machine.items.inventory("left_output").getStackInSlot(0)
            .getCount()
            + machine.items.inventory("right_output").getStackInSlot(0)
                .getCount();
        h.assertTrue(products == 1,
            "Two lanes sharing physical input produce only one result");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void comparatorsReadSharedSourcesWithoutEmittingFaces(
        GameTestHelper h) {
        var machine = place(h, "porting_workshop");
        var origin = machine.getBlockPos();
        var comparator = origin.east(2);
        h.getLevel().setBlock(origin.east(),
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
            3);
        h.getLevel().setBlock(comparator.below(),
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
            3);
        h.getLevel().setBlock(comparator,
            net.minecraft.world.level.block.Blocks.COMPARATOR
                .defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                    net.minecraft.core.Direction.WEST),
            3);
        machine.items.insertResult(1, new ItemStack(Items.STONE, 32), false);
        machine.serverTick();
        h.assertTrue(machine.getBlockState().hasAnalogOutputSignal(),
            "XML block opts into comparator readout");
        h.assertTrue(machine.getBlockState().getAnalogOutputSignal(h.getLevel(),
            origin) == 7, "Readout samples existing source");
        h.assertTrue(
            machine.screenRedstone()
                .signal(net.minecraft.core.Direction.WEST) == 0,
            "No emitting face needed");
        h.runAfterDelay(4, () -> {
            var reader = (net.minecraft.world.level.block.entity.ComparatorBlockEntity) h
                .getLevel().getBlockEntity(comparator);
            h.assertTrue(reader.getOutputSignal() == 7,
                "Comparator reads through a solid block");
            machine.items.extractItem(1, 32, false);
            machine.serverTick();
            h.runAfterDelay(4, () -> {
                h.assertTrue(reader.getOutputSignal() == 0,
                    "Storage change notifies comparator");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void placementHooksWaitForAcceptanceAndKeepOwner(
        GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(
            net.minecraft.world.level.GameType.CREATIVE);
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(
            net.minecraft.world.phys.Vec3.atCenterOf(pos.offset(3, 0, 0)));
        player.setShiftKeyDown(true);
        h.getLevel().setBlock(pos.below(),
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
            3);
        var item = zone.moddev.mc.lib.machine.neoforge.MachineContent
            .item(ResourceLocation.parse("mmdlib_companion:porting_workshop"));
        var stack = new ItemStack(item);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            stack);
        var hit = new net.minecraft.world.phys.BlockHitResult(
            net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),
            net.minecraft.core.Direction.UP, pos.below(), false);
        DefinedMachineEntity[] captured = {null};
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getEntity() == player && event.getPos().equals(pos)) {
                captured[0] = (DefinedMachineEntity) h.getLevel()
                    .getBlockEntity(pos);
                event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        try {
            player.gameMode.useItemOn(player, h.getLevel(), stack,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        h.assertTrue(captured[0] != null,
            "Cancellation saw the candidate machine");
        h.assertTrue(
            captured[0].features.findProcessor("workshop").orElseThrow()
                .variable("placements").equals(0),
            "Rejected placement never calls placed");
        player.gameMode.useItemOn(player, h.getLevel(), stack,
            net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        var processor = machine.features.findProcessor("workshop")
            .orElseThrow();
        h.assertTrue(machine.facing() == net.minecraft.core.Direction.UP,
            "Custom placement chooses clicked face");
        h.assertTrue(
            processor.variable("owner").equals(player.getUUID().toString()),
            "Placer ownership recorded");
        machine.onLoad();
        h.assertTrue(processor.variable("placements").equals(1),
            "Loading does not replay placement");
        h.succeed();
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
