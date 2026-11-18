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
    public static void loadingRepairsInvalidFacingWithoutReplacingTheEntity(
        GameTestHelper h) {
        var machine = place(h, "facing_up");
        machine.items.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
        var property = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        h.getLevel().setBlock(machine.getBlockPos(),
            machine.getBlockState().setValue(property,
                net.minecraft.core.Direction.DOWN),
            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        machine.onLoad();
        h.assertTrue(machine.facing() == net.minecraft.core.Direction.UP,
            "Load repairs disallowed facing");
        h.assertTrue(
            h.getLevel().getBlockEntity(machine.getBlockPos()) == machine
                && machine.items.getStackInSlot(0).getCount() == 3,
            "Facing repair preserves entity identity and inventory");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void keylessBindingsSelectTheDeclaredPrimary(
        GameTestHelper h) {
        var machine = place(h, "parallel_workshop");
        var left = machine.features.processor("left").orElseThrow();
        var right = machine.features.processor("right").orElseThrow();
        machine.energy.setStored(10000);
        machine.items.inventory("left_input").insertItem(0,
            new ItemStack(Items.COBBLESTONE), false);
        machine.serverTick();
        left.tick(machine.features, 4);
        h.assertTrue(left.progress() > 0 && right.progress() == 0,
            "Primary recipe has distinct progress");
        h.assertTrue(
            machine.getProgress() == left.progress()
                && machine.getTime() == left.time(),
            "Keyless recipe getters use primary");
        h.assertTrue(
            machine.features.findProcessor(java.util.Optional.empty())
                .orElseThrow() == left,
            "Keyless controls select primary even after a custom processor");
        left.setPaused(true);
        h.assertTrue(machine.getPaused(),
            "Keyless pause uses primary, not the preceding charge processor");
        h.assertTrue(
            machine.getProgress(0) == 0
                && machine.getProgress(1) == left.progress(),
            "Indexed bindings keep declaration order");
        machine.features.primaryProcessor("right");
        h.assertTrue(
            machine.getProgress() == right.progress()
                && machine.getTime() == right.time() && !machine.getPaused(),
            "Selecting the second recipe lane changes every keyless getter");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void operatingCostIsAtomicAndTracksAcceleratedWork(
        GameTestHelper h) {
        var machine = place(h, "paid_workshop");
        machine.energy.setStored(10000);
        machine.items.inventory("left_input").insertItem(0,
            new ItemStack(Items.COBBLESTONE), false);
        machine.serverTick();
        var lane = machine.features.processor("left").orElseThrow();
        h.assertTrue(lane.progress() == 0 && machine.energy.stored() == 10000,
            "Starting is free");
        machine.energy.setStored(24);
        machine.serverTick();
        h.assertTrue(
            lane.progress() == 0 && machine.energy.stored() == 24
                && machine.energy.used().signum() == 0,
            "Combined 20 recipe plus 5 operating FE cannot partially debit");
        machine.energy.setStored(75);
        lane.tick(machine.features, 2);
        h.assertTrue(
            lane.progress() == 2 && machine.energy.stored() == 25
                && machine.energy.used().intValue() == 50,
            "Accelerated steps each pay recipe plus operating cost");
        lane.setPaused(true);
        lane.tick(machine.features, 2);
        h.assertTrue(machine.energy.stored() == 25 && lane.progress() == 2,
            "Pause is free");
        lane.setPaused(false);
        machine.energy.setStored(10000);
        lane.tick(machine.features, 98);
        h.assertTrue(
            machine.energy.stored() == 7550
                && machine.energy.used().intValue() == 2500,
            "Full cycle costs recipe 2000 plus operating 500");
        h.assertTrue(machine.items.inventory("left_output").getStackInSlot(0)
            .is(Items.GRAVEL), "Cycle completed");
        lane.tick(machine.features, 5);
        h.assertTrue(machine.energy.stored() == 7550, "Idle steps are free");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedLastStepRollsBackOperatingAndRecipeEnergy(
        GameTestHelper h) {
        var machine = place(h, "paid_workshop");
        machine.energy.setStored(10000);
        machine.items.inventory("left_input").insertItem(0,
            new ItemStack(Items.COBBLESTONE), false);
        machine.serverTick();
        var lane = machine.features.processor("left").orElseThrow();
        lane.tick(machine.features, 99);
        long before = machine.energy.stored();
        var used = machine.energy.used();
        machine.items.inventory("left_output").restoreStack(0,
            new ItemStack(Items.DIRT, 64));
        lane.tick(machine.features, 3);
        h.assertTrue(
            lane.progress() == 99 && machine.energy.stored() == before
                && machine.energy.used().equals(used),
            "Blocked final step rolls back both charges, progress and telemetry");
        machine.items.inventory("left_output").restoreStack(0, ItemStack.EMPTY);
        lane.tick(machine.features, 1);
        h.assertTrue(
            machine.energy.stored() == before - 25 && machine.items
                .inventory("left_output").getStackInSlot(0).is(Items.GRAVEL),
            "Unblocking charges only once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customProcessorOperatingCostRefundsFailedWork(
        GameTestHelper h) {
        var machine = place(h, "paid_workshop");
        machine.fluids.fill(new FluidStack(Fluids.WATER, 10),
            FluidAction.EXECUTE);
        machine.energy.setStored(2);
        machine.serverTick();
        h.assertTrue(
            machine.energy.stored() == 2
                && machine.fluids.getFluidInTank(0).getAmount() == 10,
            "Custom processor stalls before work without operating power");
        machine.energy.setStored(3);
        machine.serverTick();
        h.assertTrue(
            machine.energy.stored() == 100
                && machine.fluids.getFluidInTank(0).getAmount() == 9
                && machine.energy.used().intValue() == 3,
            "Custom work pays overhead then produces energy");
        machine.energy.setStored(machine.energy.capacity());
        var used = machine.energy.used();
        machine.serverTick();
        h.assertTrue(
            machine.energy.stored() == machine.energy.capacity()
                && machine.energy.used().equals(used)
                && machine.fluids.getFluidInTank(0).getAmount() == 9,
            "Blocked custom work refunds overhead and telemetry");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customEnergyHasOneOwnerAndLiveCapabilityPolicy(
        GameTestHelper h) {
        var machine = place(h, "battery_workshop");
        var bank = machine.features.findProcessor("battery",
            zone.moddev.mc.companion.BatteryBank.class).orElseThrow();
        h.assertTrue(
            bank.buffer() == machine.energy
                && machine.screenEnergy() == machine.energy,
            "Provider, recipes, persistence and screen share one buffer");
        var capability = h.getLevel().getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
            machine.getBlockPos(), null);
        h.assertTrue(
            capability != null && capability.receiveEnergy(2000, true) == 2000,
            "Custom capability simulates");
        h.assertTrue(machine.energy.stored() == 0,
            "Simulation leaves buffer empty");
        capability.receiveEnergy(2000, false);
        h.assertTrue(machine.resources().energy("energy").stored() == 2000,
            "Transactions see capability input");
        var east = h.getLevel().getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
            machine.getBlockPos(), net.minecraft.core.Direction.EAST);
        h.assertTrue(east != null && east.receiveEnergy(100, false) == 0,
            "Provider side policy enforced");
        bank.setPaused(true);
        h.assertTrue(
            capability.extractEnergy(100, false) == 0
                && capability.receiveEnergy(100, false) == 0,
            "Cached view observes dynamic provider availability");
        bank.setPaused(false);
        h.assertTrue(capability.extractEnergy(100, false) == 100,
            "Provider permits export");
        capability.receiveEnergy(2000, false);
        var saved = machine.saveWithFullMetadata(h.getLevel().registryAccess());
        machine.energy.setStored(0);
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(capability.getEnergyStored() == 3900,
            "Same capability sees restored authoritative energy");
        machine.items.inventory("left_input").insertItem(0,
            new ItemStack(Items.COBBLESTONE), false);
        for (int tick = 0; tick < 102; tick++)
            machine.serverTick();
        h.assertTrue(
            machine.items.inventory("left_output").getStackInSlot(0)
                .is(Items.GRAVEL) && machine.energy.stored() == 1900,
            "Recipe uses provider energy without a second buffer");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void callbacksKeepEveryAcceleratedTransition(
        GameTestHelper h) {
        var machine = place(h, "parallel_workshop");
        machine.energy.setStored(100000);
        machine.items.inventory("left_input").insertItem(0,
            new ItemStack(Items.COBBLESTONE, 3), false);
        machine.serverTick();
        var lane = machine.features.processor("left").orElseThrow();
        var observer = machine.features.findProcessor("charge").orElseThrow();
        lane.tick(machine.features, 303);
        h.assertTrue(
            observer.variable("starts").equals(3)
                && observer.variable("finishes").equals(3),
            "Every start and finish delivered during one accelerated tick");
        h.assertTrue(observer.variable("last_lane").equals("left"),
            "Event identifies its lane");
        var saved = machine.saveWithFullMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        machine.serverTick();
        h.assertTrue(
            observer.variable("starts").equals(3)
                && observer.variable("finishes").equals(3),
            "Loading and idle work do not replay callbacks");
        h.succeed();
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
