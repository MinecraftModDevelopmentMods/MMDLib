package zone.moddev.mc.lib.smoke;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import zone.moddev.mc.lib.api.capabilities.*;

/** Checks the capability wrappers' rules; needs a bootstrapped game for stacks and NBT. */
final class CapabilitySmoke {
  private CapabilitySmoke() {}

  static void check(ServerLevel level) {
    var provider = level.registryAccess();
    var changes = new int[1];
    var items =
        new ItemStorage(4)
            .onChange(() -> changes[0]++)
            .role(Role.INPUT, 0)
            .role(Role.OUTPUT, 1)
            .role(Role.MANUAL, 3)
            .filter(stack -> stack.is(Items.IRON_INGOT), 0)
            .limit(1, 3);
    var face = items.view(IoMode.BOTH);
    require(face.getSlots() == 4, "all slots on every face");
    require(face.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "filter");
    require(face.insertItem(0, new ItemStack(Items.IRON_INGOT, 5), false).isEmpty(), "input");
    require(face.extractItem(0, 1, false).isEmpty(), "input slot refuses extraction");
    require(!items.extractItem(0, 1, true).isEmpty(), "owner/menu can take from input");
    require(face.insertItem(1, new ItemStack(Items.STONE), false).getCount() == 1, "output");
    require(items.insertItem(1, new ItemStack(Items.STONE), false).getCount() == 1, "player out");
    require(items.insertResult(1, new ItemStack(Items.STONE, 3), false).isEmpty(), "result");
    require(face.extractItem(1, 2, false).getCount() == 2, "output extraction");
    require(face.insertItem(3, new ItemStack(Items.STONE), false).getCount() == 1, "manual");
    require(items.insertItem(3, new ItemStack(Items.STONE, 2), false).getCount() == 1, "limit");
    require(face.getStackInSlot(99).isEmpty(), "out-of-range read");
    require(face.insertItem(-1, new ItemStack(Items.STONE), false).getCount() == 1, "oob insert");
    require(face.extractItem(99, 1, false).isEmpty(), "out-of-range extract");
    var mode = new IoMode[] {IoMode.INPUT};
    var live = items.view(() -> mode[0]);
    require(live.extractItem(1, 1, false).isEmpty(), "input face refuses extraction");
    mode[0] = IoMode.DISABLED;
    require(live.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1, "off");
    require(changes[0] > 0, "change callback");

    var copy = new ItemStorage(4);
    copy.deserializeNBT(provider, items.serializeNBT(provider));
    require(copy.getStackInSlot(0).getCount() == 5 && copy.getSlots() == 4, "item NBT");
    var small = new ItemStorage(2);
    small.deserializeNBT(provider, items.serializeNBT(provider));
    require(small.getSlots() == 2, "saved size does not resize");

    var target = new ItemStorage(2);
    require(Transfer.items(face, target.view(IoMode.BOTH), 64) == 1, "transfer from output");
    require(target.getStackInSlot(0).is(Items.STONE), "transfer target");

    var fluids = new FluidStorage();
    int in = fluids.addTank(1000, Role.INPUT, fluid -> fluid.is(Fluids.WATER));
    int out = fluids.addTank(1000, Role.OUTPUT);
    var tankFace = fluids.view(IoMode.BOTH);
    require(tankFace.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE) == 0, "filter");
    require(tankFace.fill(new FluidStack(Fluids.WATER, 1500), FluidAction.EXECUTE) == 1000, "in");
    require(tankFace.drain(100, FluidAction.EXECUTE).isEmpty(), "input tank refuses drain");
    fluids.tank(out).fill(new FluidStack(Fluids.LAVA, 300), FluidAction.EXECUTE);
    require(tankFace.drain(500, FluidAction.EXECUTE).getAmount() == 300, "output drain");
    require(
        fluids.view(IoMode.OUTPUT).fill(new FluidStack(Fluids.WATER, 1), FluidAction.SIMULATE) == 0,
        "output face refuses fill");
    var fluidCopy = new FluidStorage();
    fluidCopy.addTank(1000, Role.INPUT);
    fluidCopy.addTank(1000, Role.OUTPUT);
    fluidCopy.deserializeNBT(provider, fluids.serializeNBT(provider));
    require(fluidCopy.getFluidInTank(in).getAmount() == 1000, "fluid NBT");

    var energy = new EnergyBuffer(10_000_000_000L, 1000);
    require(energy.insert(5_000_000_000L, false) == 5_000_000_000L, "long insert");
    require(energy.getEnergyStored() == Integer.MAX_VALUE, "int clamp");
    require(energy.view(IoMode.BOTH).receiveEnergy(5000, false) == 1000, "rate limit");
    require(energy.view(IoMode.INPUT).extractEnergy(10, false) == 0, "input face");
    require(energy.consume(1000) && !energy.consume(Long.MAX_VALUE), "consume");
    var energyCopy = new EnergyBuffer(10_000_000_000L, 1000);
    energyCopy.deserializeNBT(provider, energy.serializeNBT(provider));
    require(energyCopy.stored() == energy.stored(), "energy NBT");

    var sides = new SideConfig(IoMode.INPUT).set(RelativeFace.FRONT, IoMode.OUTPUT);
    for (var facing : Direction.values())
      for (var face2 : RelativeFace.values())
        require(RelativeFace.of(facing, face2.toWorld(facing)) == face2, "face round trip");
    require(sides.mode(Direction.EAST, Direction.EAST) == IoMode.OUTPUT, "front follows facing");
    require(RelativeFace.LEFT.toWorld(Direction.NORTH) == Direction.EAST, "left from the front");
    require(sides.mode(Direction.EAST, null) == IoMode.BOTH, "unsided");
    var sidesCopy =
        SideConfig.CODEC
            .parse(
                net.minecraft.nbt.NbtOps.INSTANCE,
                SideConfig.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, sides).getOrThrow())
            .getOrThrow();
    require(sidesCopy.get(RelativeFace.FRONT) == IoMode.OUTPUT, "side config codec");
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Capability wrapper check failed: " + what);
  }
}
