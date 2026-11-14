package zone.moddev.mc.companion.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.neoforge.MachineItemInteractions;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class ItemReceiverTests {
    private static DefinedMachineEntity tank(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            MachineContent
                .block(
                    ResourceLocation.parse("mmdlib_companion:test/fluid_tank"))
                .defaultBlockState(),
            3);
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
    }

    private static UseOnContext context(Player player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    @GameTest(template = "empty")
    public static void bucketsAndPartialContainersExchangeThroughTheSameReceiver(
        GameTestHelper h) {
        var machine = tank(h);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(MachineItemInteractions
            .interact(context(player, machine.getBlockPos())).consumesAction(),
            "Water bucket accepted");
        h.assertTrue(
            player.getMainHandItem().is(Items.BUCKET)
                && machine.fluids.getFluidInTank(0).getAmount() == 1000,
            "Fluid transferred once and empty bucket returned");
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(Items.BUCKET, 2));
        h.assertTrue(MachineItemInteractions
            .interact(context(player, machine.getBlockPos())).consumesAction(),
            "Stacked empty bucket fills");
        h.assertTrue(
            player.getMainHandItem().getCount() == 1
                && machine.fluids.getFluidInTank(0).isEmpty(),
            "Only one container is exchanged");
        h.assertTrue(player.getInventory().countItem(Items.WATER_BUCKET) == 1,
            "Filled remainder is stowed");
        machine.fluids.fill(new FluidStack(Fluids.WATER, 350),
            FluidAction.EXECUTE);
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(Items.GLASS_BOTTLE));
        h.assertTrue(MachineItemInteractions
            .interact(context(player, machine.getBlockPos())).consumesAction(),
            "Capability-backed container accepted");
        h.assertTrue(
            FluidUtil.getFluidHandler(player.getMainHandItem()).orElseThrow()
                .getFluidInTank(0).getAmount() == 350,
            "Modded container keeps its partially filled result");
        h.assertTrue(machine.fluids.getFluidInTank(0).isEmpty(),
            "Partial fluid amount conserved");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedTransfersAreUnchangedAndCreativeKeepsFluidContainers(
        GameTestHelper h) {
        var machine = tank(h);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        machine.fluids.fill(new FluidStack(Fluids.WATER, 4000),
            FluidAction.EXECUTE);
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(Items.LAVA_BUCKET));
        h.assertTrue(
            MachineItemInteractions.interact(context(player,
                machine.getBlockPos())) == InteractionResult.FAIL,
            "Incompatible fluid rejected");
        h.assertTrue(
            player.getMainHandItem().is(Items.LAVA_BUCKET)
                && machine.fluids.getFluidInTank(0).getAmount() == 4000,
            "Rejected exchange changes neither source nor destination");
        h.assertTrue(
            MachineItemInteractions
                .useOnBlock(context(player, machine.getBlockPos()))
                .consumesAction(),
            "Rejection owns the click rather than spilling lava");
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(Vec3.atCenterOf(machine.getBlockPos()),
            Direction.UP, machine.getBlockPos(), false);
        var event = new PlayerInteractEvent.RightClickBlock(player,
            InteractionHand.MAIN_HAND, machine.getBlockPos(), hit);
        MachineItemInteractions.secondaryUse(event);
        h.assertTrue(event.getUseBlock().isTrue(),
            "Sneaking containers use the same block receiver");
        event.setUseBlock(TriState.FALSE);
        MachineItemInteractions.secondaryUse(event);
        h.assertTrue(event.getUseBlock().isFalse(),
            "Explicit interaction denial is preserved");
        var creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(Items.BUCKET));
        h.assertTrue(MachineItemInteractions
            .interact(context(creative, machine.getBlockPos()))
            .consumesAction(), "Creative extraction succeeds");
        h.assertTrue(
            creative.getMainHandItem().is(Items.BUCKET)
                && machine.fluids.getFluidInTank(0).getAmount() == 3000,
            "Creative keeps its container while applying the fluid transfer");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryDropsTheContainerRemainderInsteadOfDeletingIt(
        GameTestHelper h) {
        var machine = tank(h);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(machine.getBlockPos().above(2)));
        for (int slot = 0; slot < 36; slot++)
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(Items.BUCKET, 2));
        machine.fluids.fill(new FluidStack(Fluids.WATER, 1000),
            FluidAction.EXECUTE);
        MachineItemInteractions
            .interact(context(player, machine.getBlockPos()));
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(player.blockPosition()).inflate(3));
        h.assertTrue(
            player.getMainHandItem().getCount() == 1 && drops.stream()
                .anyMatch(e -> e.getItem().is(Items.WATER_BUCKET)),
            "One filled bucket is returned even when all inventory slots are full");
        h.succeed();
    }
}
