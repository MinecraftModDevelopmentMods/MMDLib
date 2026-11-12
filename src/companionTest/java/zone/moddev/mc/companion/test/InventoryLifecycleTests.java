package zone.moddev.mc.companion.test;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;

import net.neoforged.neoforge.gametest.*;

import zone.moddev.mc.companion.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.machine.feature.*;
import zone.moddev.mc.lib.machine.neoforge.MachineInventoryLifecycle.PausePolicy;

@GameTestHolder(CompanionMod.ID)
@PrefixGameTestTemplate(false)
public final class InventoryLifecycleTests {
    private static ProcessorEntity place(GameTestHelper h, boolean logistics) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            (logistics ? CompanionMod.LOGISTICS : CompanionMod.CRUSHER).block
                .get().defaultBlockState(),
            3);
        return (ProcessorEntity) h.getLevel().getBlockEntity(pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    @GameTest(template = "empty")
    public static void pausePolicySeparatesProcessingTransfersAndRefunds(
        GameTestHelper h) {
        var machine = place(h, true);
        int[] ticks = {0};
        machine.features.add(new MachineFeature<Integer>() {
            public String key() {
                return "policy_probe";
            }

            public com.mojang.serialization.Codec<Integer> codec() {
                return com.mojang.serialization.Codec.INT;
            }

            public Integer state() {
                return ticks[0];
            }

            public void restore(Integer value) {
                ticks[0] = value;
            }

            public void tick(FeatureHost host) {
                ticks[0]++;
            }
        });
        var state = machine.automation.transferState("input", 0);
        state.putBoolean("input", true);
        state.putInt("batch", 8);
        state.putLong("cost", 0);
        state.put("item", new ItemStack(Items.DIAMOND, 3)
            .save(h.getLevel().registryAccess()));
        machine.energy.setStored(machine.energy.capacity());
        machine.automation.refund(machine.energy, 5);
        machine.energy.setStored(machine.energy.capacity() - 5);
        machine.inventoryLifecycle.tick(true, true, PausePolicy.BOTH);
        require(ticks[0] == 0 && machine.items.getStackInSlot(0).isEmpty(),
            "Both policy suspends processing and transfers");
        require(
            machine.automation.pendingRefund() == 0
                && machine.energy.stored() == machine.energy.capacity(),
            "pause still returns already owed energy");
        machine.inventoryLifecycle.tick(true, true, PausePolicy.TRANSFERS_ONLY);
        require(ticks[0] == 1 && machine.items.getStackInSlot(0).isEmpty(),
            "Transfer-only policy leaves processing enabled");
        machine.inventoryLifecycle.tick(true, true,
            PausePolicy.PROCESSING_ONLY);
        require(
            ticks[0] == 1 && machine.items.getStackInSlot(0).getCount() == 3,
            "Processing-only policy still delivers owned escrow");
        machine.inventoryLifecycle.tick(true, true, PausePolicy.NEITHER);
        require(ticks[0] == 2, "Neither policy ignores pause");
        machine.inventoryLifecycle.tick(false, false, PausePolicy.NEITHER);
        require(ticks[0] == 2, "Independent processing gate always applies");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lifecycleLoadsStorageBeforeRestoringReservations(
        GameTestHelper h) {
        var machine = place(h, false);
        machine.items.insertItem(0, new ItemStack(Items.COPPER_ORE, 3), false);
        machine.energy.setStored(100000);
        machine.serverTick();
        require(machine.items.hasPlan(0), "recipe started and reserved input");
        var saved = new CompoundTag();
        machine.inventoryLifecycle.save(saved, h.getLevel().registryAccess());
        for (int i = 0; i < 3; i++) {
            machine.items.restoreStack(0, ItemStack.EMPTY);
            machine.inventoryLifecycle.load(saved,
                h.getLevel().registryAccess());
            require(
                machine.items.getStackInSlot(0).getCount() == 3
                    && machine.items.plan(0).take() == 1,
                "load restores contents before plan and never accumulates reservations");
        }
        machine.inventoryLifecycle.load(new CompoundTag(),
            h.getLevel().registryAccess());
        require(!machine.items.hasPlan(0)
            && machine.lanes.getFirst().status() == RecipeProcessor.Status.IDLE,
            "missing feature save does not retain previous running cycle");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void displacedItemsSurviveBreakAndCapsuleRestoration(
        GameTestHelper h) {
        var machine = place(h, true);
        var old = new ItemStorage(3).slotId(0, "input").slotId(1, "output_1")
            .slotId(2, "removed");
        old.restoreStack(2, new ItemStack(Items.DIAMOND, 9));
        machine.items.deserializeNBT(h.getLevel().registryAccess(),
            old.serializeNBT(h.getLevel().registryAccess()));
        var payloads = new ArrayList<CompoundTag>();
        machine.inventoryLifecycle.dropContents(payloads::add);
        machine.inventoryLifecycle.dropContents(payloads::add);
        require(payloads.size() == 1,
            "removed slot transfers to host once on destruction");
        var capsule = RecoveryCapsuleItem.create(payloads.getFirst());
        require(RecoveryCapsuleItem.restore(capsule, machine),
            "recovery capsule now restores displaced item resources");
        require(
            capsule.isEmpty()
                && machine.items.getStackInSlot(0).getCount() == 9,
            "recovery conserves every item and consumes capsule");
        machine.items.deserializeNBT(h.getLevel().registryAccess(),
            old.serializeNBT(h.getLevel().registryAccess()));
        machine.clearContent();
        require(machine.items.recoveryContents().isEmpty(),
            "explicit clear also removes displaced contents");
        h.succeed();
    }
}
