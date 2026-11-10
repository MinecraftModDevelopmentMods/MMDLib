package zone.moddev.mc.companion.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import zone.moddev.mc.companion.CompanionAddons;
import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.companion.RecoveryCapsuleItem;
import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.automation.AutomationTransfers;
import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.capabilities.SideConfig;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

@GameTestHolder(CompanionMod.ID)
@PrefixGameTestTemplate(false)
public final class AutomationRecoveryTests {
    private static void require(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }

    private static ProcessorEntity place(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlock(pos,
            CompanionMod.LOGISTICS.block.get().defaultBlockState(), 3);
        return (ProcessorEntity) h.getLevel().getBlockEntity(pos);
    }

    private static ProcessorEntity place(GameTestHelper h) {
        return place(h, h.absolutePos(new BlockPos(2, 2, 2)));
    }

    private static CompoundTag pending(AutomationInventory a, int group,
        boolean input) {
        var state = a.transferState(a.group(group).id(), 0);
        state.putBoolean("input", input);
        state.putInt("face", RelativeFace.FRONT.ordinal());
        state.putInt("batch", 4096);
        state.putLong("cost", 0);
        return state;
    }

    /**
     * Stage real extractions into handlers that accept simulation but refuse
     * execution.
     */
    private static void stage(GameTestHelper h, AutomationInventory a,
        boolean input) {
        var items = new ItemStackHandler(1);
        items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        var refusedItems = new ItemStackHandler(1) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack,
                boolean simulate) {
                return simulate ? ItemStack.EMPTY : stack;
            }
        };
        require(
            AutomationTransfers.transport(ResourceType.ITEMS).orElseThrow()
                .move(items, refusedItems, 8, value -> true,
                    pending(a, input ? 0 : 1, input),
                    h.getLevel().registryAccess()) == 0,
            "Item delivery refused after extraction");
        require(items.getStackInSlot(0).isEmpty(),
            "Pending items are owned only by escrow");
        var fluids = new FluidTank(1000);
        fluids.setFluid(new FluidStack(Fluids.WATER, 750));
        var refusedFluids = new FluidTank(1000) {
            @Override
            public int fill(FluidStack stack, FluidAction action) {
                return action.simulate() ? stack.getAmount() : 0;
            }
        };
        require(
            AutomationTransfers.transport(ResourceType.FLUIDS).orElseThrow()
                .move(fluids, refusedFluids, 750, value -> true,
                    pending(a, input ? 2 : 4, input),
                    h.getLevel().registryAccess()) == 0,
            "Fluid delivery refused after extraction");
        require(fluids.isEmpty(), "Pending fluid is owned only by escrow");
        var energy = new EnergyBuffer(1000, 1000);
        energy.setStored(750);
        var refusedEnergy = new EnergyBuffer(1000, 1000) {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                return simulate ? amount : 0;
            }
        };
        require(
            AutomationTransfers.transport(ResourceType.ENERGY).orElseThrow()
                .move(energy, refusedEnergy, 750, value -> true,
                    pending(a, 6, input), h.getLevel().registryAccess()) == 0,
            "Energy delivery refused after extraction");
        require(energy.stored() == 0, "Pending energy is owned only by escrow");
    }

    @GameTest(template = "empty")
    public static void cachesInvalidateAndUnchangedInventoriesSkipRevalidation(
        GameTestHelper h) {
        var machine = place(h);
        var a = machine.automation;
        var pos = machine.getBlockPos().north();
        h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        TestAutomationCapabilities.LOOKUPS.remove(pos);
        for (int i = 0; i < 100; i++)
            require(a.neighbour(TestAutomationCapabilities.ITEMS,
                Direction.NORTH) != null, "Cached neighbour exists");
        require(TestAutomationCapabilities.LOOKUPS.get(pos) == 1,
            "Repeated queries resolve provider only once");
        h.getLevel().invalidateCapabilities(pos);
        a.neighbour(TestAutomationCapabilities.ITEMS, Direction.NORTH);
        require(TestAutomationCapabilities.LOOKUPS.get(pos) == 2,
            "Explicit invalidation refreshes provider");
        h.getLevel().destroyBlock(pos, false);
        require(
            a.neighbour(TestAutomationCapabilities.ITEMS,
                Direction.NORTH) == null,
            "Removal invalidates cached neighbour");
        h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        require(
            a.neighbour(TestAutomationCapabilities.ITEMS,
                Direction.NORTH) != null
                && TestAutomationCapabilities.LOOKUPS.get(pos) == 3,
            "Replacement supplies a fresh capability");
        a.slots().setStackInSlot(0, new ItemStack(Items.BLAZE_ROD));
        a.revalidate();
        int specifications = TestAutomationCapabilities.SPECIFICATIONS.get();
        long revision = a.viewRevision();
        for (int i = 0; i < 100; i++)
            a.revalidate();
        require(
            TestAutomationCapabilities.SPECIFICATIONS.get() == specifications
                && a.viewRevision() == revision,
            "Unchanged inventory does not rescan addon capabilities or change its view revision");
        a.invalidateAddons();
        a.revalidate();
        require(
            TestAutomationCapabilities.SPECIFICATIONS.get() > specifications,
            "Explicit invalidation rechecks definitions");
        a.group(0).sides().set(RelativeFace.FRONT, IoMode.DISABLED);
        require(
            a.viewRevision() > revision
                && a.mode(ResourceType.ITEMS, "input", 0,
                    Direction.NORTH) == IoMode.DISABLED
                && a.mode(ResourceType.ITEMS, "output", 0,
                    Direction.NORTH) == IoMode.OUTPUT,
            "Indexed membership preserves independent modes and notifies menu revision");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownRecoveryPayloadAndFailedHandoffAreLossless(
        GameTestHelper h) {
        var machine = place(h);
        var a = machine.automation;
        var state = a.transferState("removed_group", 0);
        state.putString("resource", "absent_mod:gas");
        state.putInt("amount", 1234);
        state.put("item",
            new ItemStack(Items.DIAMOND).save(h.getLevel().registryAccess()));
        var before = a.save(h.getLevel().registryAccess());
        try {
            a.dropAll(payload -> {
                throw new IllegalStateException("Recovery sink refused");
            });
            throw new AssertionError("Expected refused handoff");
        } catch (IllegalStateException expected) {
            require(a.save(h.getLevel().registryAccess()).equals(before),
                "Failed handoff leaves pending data intact");
        }
        var captured = new java.util.concurrent.atomic.AtomicReference<CompoundTag>();
        a.dropAll(captured::set);
        var entry = captured.get().getList("resources", Tag.TAG_COMPOUND)
            .getCompound(0);
        require(
            entry.getString("type").equals("absent_mod:gas")
                && entry.getCompound("data").equals(state),
            "Unknown transport schema is preserved verbatim");
        require(
            h.getLevel()
                .getEntitiesOfClass(ItemEntity.class,
                    new AABB(machine.getBlockPos()).inflate(2))
                .isEmpty(),
            "A custom payload's item field must not also be interpreted as a vanilla item drop");
        var capsule = RecoveryCapsuleItem.create(captured.get());
        require(
            !RecoveryCapsuleItem.restore(capsule, machine)
                && !capsule.isEmpty(),
            "Unsupported resources stay in the recovery capsule");
        a.dropAll(payload -> {
            throw new AssertionError("Recovery payload handed off twice");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inboundEscrowCompletesAfterSourceRemovalAndReload(
        GameTestHelper h) {
        var machine = place(h);
        var source = machine.getBlockPos().north();
        h.getLevel().setBlock(source, Blocks.CHEST.defaultBlockState(), 3);
        stage(h, machine.automation, true);
        h.getLevel().destroyBlock(source, false);
        var a = machine.automation;
        a.load(a.save(h.getLevel().registryAccess()),
            h.getLevel().registryAccess());
        for (var group : a.groups())
            for (var face : RelativeFace.values())
                group.sides().set(face, IoMode.DISABLED);
        AutomationTransfers.tick(a, machine.energy);
        AutomationTransfers.tick(a, machine.energy);
        require(machine.items.getStackInSlot(0).getCount() == 8,
            "Owned inbound items need no source or enabled external side");
        require(machine.fluids.getFluidInTank(0).getAmount() == 750,
            "Inbound fluid completes without original neighbour");
        require(machine.energy.stored() == 750,
            "Inbound energy completes once without original neighbour");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void outboundEscrowReroutesToAnotherEnabledNeighbour(
        GameTestHelper h) {
        var machine = place(h);
        var a = machine.automation;
        var original = machine.getBlockPos().north();
        place(h, original);
        stage(h, a, false);
        // Populate caches before breaking the destination.
        require(a.neighbour(Capabilities.ItemHandler.BLOCK,
            Direction.NORTH) != null, "Original neighbour cached");
        h.getLevel().destroyBlock(original, false);
        AutomationTransfers.tick(a, machine.energy);
        require(a.transferState("tank_2", 0).contains("fluid"),
            "No destination retains pending fluid");
        a.load(a.save(h.getLevel().registryAccess()),
            h.getLevel().registryAccess());
        var destination = place(h, machine.getBlockPos().east());
        for (int g : new int[]{1, 4, 6}) {
            for (var face : RelativeFace.values())
                a.group(g).sides().set(face, IoMode.DISABLED);
            a.group(g).sides().set(
                RelativeFace.of(Direction.NORTH, Direction.EAST),
                IoMode.OUTPUT);
        }
        AutomationTransfers.tick(a, machine.energy);
        AutomationTransfers.tick(a, machine.energy);
        require(destination.items.getStackInSlot(0).getCount() == 8,
            "Pending items reroute exactly once");
        require(destination.fluids.getFluidInTank(0).getAmount() == 750,
            "Pending fluid reroutes exactly once");
        require(destination.energy.stored() == 750,
            "Pending energy reroutes exactly once");
        require(machine.items.getStackInSlot(1).isEmpty(),
            "Rerouting never extracts a new batch");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void clippedRefundSurvivesSaveAndReturnsExactlyOnce(
        GameTestHelper h) {
        var machine = place(h);
        var power = new EnergyBuffer(1000, 1000);
        power.setStored(1000);
        var destination = new ItemStackHandler(1) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack,
                boolean simulate) {
                if (!simulate)
                    power.setStored(1000); // Reentrant handler fills the
                                           // reserved refund space.
                return stack;
            }
        };
        var group = new ResourceGroup<>("input", Component.literal("Input"), 0,
            ResourceType.ITEMS, IoMode.INPUT, List.of(0), List.of(),
            new SideConfig(IoMode.INPUT));
        group.bind(() -> Direction.NORTH, side -> destination);
        var a = new AutomationInventory(machine, List.of(group));
        var state = pending(a, 0, true);
        state.put("item", new ItemStack(Items.DIAMOND, 8)
            .save(h.getLevel().registryAccess()));
        state.putInt("batch", 8);
        state.putLong("cost", 4);
        AutomationTransfers.tick(a, power);
        require(power.stored() == 1000 && a.pendingRefund() == 32,
            "Rejected refund is retained, not clipped");
        a.load(a.save(h.getLevel().registryAccess()),
            h.getLevel().registryAccess());
        require(a.pendingRefund() == 32, "Refund persists through save/reload");
        power.extract(100, false);
        a.settleRefund(power);
        a.settleRefund(power);
        require(power.stored() == 932 && a.pendingRefund() == 0,
            "Refund returns once when room becomes available");
        require(power.inserted().signum() == 0 && power.used().signum() == 0,
            "Reserved and refunded transfer charges never count as input or processor use");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingPreservesFluidsEnergyRefundsAndPartialCapsuleRecovery(
        GameTestHelper h) {
        var machine = place(h);
        stage(h, machine.automation, false);
        machine.energy.setStored(machine.energy.capacity());
        machine.automation.refund(machine.energy, 50);
        machine.automation.load(
            machine.automation.save(h.getLevel().registryAccess()),
            h.getLevel().registryAccess());
        h.getLevel().destroyBlock(machine.getBlockPos(), true);
        machine.dropContents(h.getLevel(), machine.getBlockPos());
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(machine.getBlockPos()).inflate(2));
        var capsules = drops.stream()
            .filter(e -> e.getItem().is(CompanionAddons.RECOVERY_CAPSULE.get()))
            .toList();
        require(
            capsules.size() == 1
                && capsules.getFirst().getItem().getCount() == 1,
            "Exactly one recovery capsule");
        require(
            drops.stream().filter(e -> e.getItem().is(Items.DIAMOND))
                .mapToInt(e -> e.getItem().getCount()).sum() == 8,
            "Pending items still drop normally exactly once");
        var capsule = ItemStack.parseOptional(h.getLevel().registryAccess(),
            (CompoundTag) capsules.getFirst().getItem()
                .saveOptional(h.getLevel().registryAccess()));
        var target = place(h);
        target.energy.setStored(target.energy.capacity());
        for (int i = 0; i < 2; i++)
            target.fluids.tank(i).setFluid(new FluidStack(Fluids.WATER, 4000));
        var before = RecoveryCapsuleItem.contents(capsule);
        require(
            !RecoveryCapsuleItem.restore(capsule, target)
                && before.equals(RecoveryCapsuleItem.contents(capsule)),
            "Full destination leaves recovery item unchanged");
        target.fluids.tank(0).drain(250, FluidAction.EXECUTE);
        target.energy.extract(125, false);
        require(
            RecoveryCapsuleItem.restore(capsule, target) && !capsule.isEmpty(),
            "Partial recovery retains remainder");
        var remaining = RecoveryCapsuleItem.contents(capsule);
        require(remaining.getLong("refund") == 50,
            "Refund waits behind recovered energy without loss");
        int fluid = 0, energy = 0;
        for (var entry : remaining.getList("resources", Tag.TAG_COMPOUND)) {
            var data = ((CompoundTag) entry).getCompound("data");
            fluid += FluidStack.parseOptional(h.getLevel().registryAccess(),
                data.getCompound("fluid")).getAmount();
            energy += data.getInt("energy");
        }
        require(fluid == 500 && energy == 625,
            "Partial capsule contains exact fluid and energy remainders");
        target.fluids.tank(0).setFluid(FluidStack.EMPTY);
        target.fluids.tank(1).setFluid(FluidStack.EMPTY);
        target.energy.setStored(0);
        require(
            RecoveryCapsuleItem.restore(capsule, target) && capsule.isEmpty(),
            "Fully restored capsule is consumed");
        require(
            target.fluids.getFluidInTank(0).getAmount() == 500
                && target.energy.stored() == 675,
            "All remaining fluid, transferred energy and refund are recoverable");
        require(!RecoveryCapsuleItem.restore(capsule, target),
            "Consumed capsule cannot duplicate resources");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unsidedEnergyIgnoresFaceModesAndSnapshotsExcludeAddonSlots(
        GameTestHelper h) throws ReflectiveOperationException {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            CompanionMod.CRUSHER.block.get().defaultBlockState(), 3);
        var machine = (ProcessorEntity) h.getLevel().getBlockEntity(pos);
        machine.energy.setRates(1000, 1000);
        machine.energy.setStored(1000);
        var unsided = h.getLevel()
            .getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        var sided = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
            pos, Direction.NORTH);
        require(
            unsided.extractEnergy(100, false) == 100
                && sided.extractEnergy(100, false) == 0,
            "Unsided energy preserves buffer operations while sided access honours input role");
        // Exercise a host with more underlying storage than its three displayed
        // slots.
        var field = ProcessorEntity.class.getDeclaredField("items");
        field.setAccessible(true);
        var original = machine.items;
        try {
            field.set(machine,
                zone.moddev.mc.lib.api.capabilities.ItemInventories
                    .partition(java.util.List.of("input", "output", "output",
                        "hidden", "hidden")));
            var player = h.makeMockServerPlayerInLevel();
            var menu = new MachineMenu(91, player.getInventory(), machine);
            menu.components().snapshot();
            require(menu.components().get(BuiltinMenuComponents.AUTOMATION)
                .slotStart() == 3
                && menu.components().state(
                    menu.components()
                        .require(BuiltinMenuComponents.RESERVATIONS),
                    zone.moddev.mc.lib.gui.runtime.slot.ReservationMenuComponent.PLANS)
                    .keySet().equals(java.util.Set.of(0, 1, 2)),
                "Reservation snapshot includes only displayed base slots, not hidden underlying slots or"
                    + " addon indices");
        } finally {
            field.set(machine, original);
        }
        h.succeed();
    }
}
