package zone.moddev.mc.companion.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.companion.CreativePowerEntity;
import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor.Status;

/** Exercises the consumer's registration and wiring with the real recipe files and block types. */
@GameTestHolder(CompanionMod.ID)
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
  private static final BlockPos CENTER = new BlockPos(2, 2, 2);

  @GameTest(template = "empty")
  public static void registeredScreensAndRecipes(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    var player = helper.makeMockServerPlayerInLevel();
    for (var machine : CompanionMod.MACHINES) {
      var id = BuiltInRegistries.BLOCK.getKey(machine.block.get());
      require(Screens.get(id).isPresent(), "Missing screen " + id);
      level.setBlock(pos, machine.block.get().defaultBlockState(), 3);
      var entity = level.getBlockEntity(pos);
      require(entity.getType() == machine.type.get(), "Wrong block entity for " + id);
      var menu = new MachineMenu(41, player.getInventory(), entity);
      require(menu.bound.screen().id().equals(id), "Wrong menu for " + id);
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
    require(CompanionMod.CRUSHING.recipes(level.getRecipeManager()).size() == 5, "Five crusher recipes");
    require(CompanionMod.TRANSMUTING.recipes(level.getRecipeManager()).size() == 1, "One converter recipe");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativePowerFeedsAllFaces(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    level.setBlock(pos, CompanionMod.CREATIVE_POWER.block.get().defaultBlockState(), 3);
    var power = (CreativePowerEntity) level.getBlockEntity(pos);
    for (var side : Direction.values())
      level.setBlock(pos.relative(side), CompanionMod.CRUSHER.block.get().defaultBlockState(), 3);
    power.serverTick();
    for (var side : Direction.values()) {
      var machine = (ProcessorEntity) level.getBlockEntity(pos.relative(side));
      require(machine.energy.stored() == 1_000, "Power reaches " + side);
      var view = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(side), side.getOpposite());
      require(view.canReceive() && !view.canExtract(), "Processor energy is input-only");
      level.setBlock(pos.relative(side), Blocks.AIR.defaultBlockState(), 3);
    }
    var output = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.UP);
    require(output.canExtract() && !output.canReceive(), "Source energy is output-only");
    require(output.extractEnergy(2_000, false) == 1_000, "Source limits extraction per call");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void crusherUsesAdjacentPower(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    var crusher = place(helper, CompanionMod.CRUSHER);
    level.setBlock(pos.below(), CompanionMod.CREATIVE_POWER.block.get().defaultBlockState(), 3);
    var source = (CreativePowerEntity) level.getBlockEntity(pos.below());
    var input = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    require(input.insertItem(0, new ItemStack(Items.COPPER_ORE), false).isEmpty(), "Accepts ore");
    require(input.insertItem(1, new ItemStack(Items.RAW_COPPER), false).getCount() == 1, "Output refuses insertion");
    for (int i = 0; i <= 100; i++) {
      source.serverTick();
      crusher.serverTick();
    }
    require(crusher.items.getStackInSlot(0).isEmpty(), "Consumed one ore");
    require(crusher.items.getStackInSlot(1).is(Items.RAW_COPPER)
        && crusher.items.getStackInSlot(1).getCount() == 2, "Produced two raw copper");
    require(input.extractItem(1, 64, false).getCount() == 2, "Automation extracts output");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void oneSlotInterruptAndResume(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.TRANSMUTER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    var automation = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    require(automation.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false).getCount() == 63,
        "The converter accepts exactly one iron");
    machine.energy.setStored(100_000);
    tick(machine, 51);
    require(machine.lanes.getFirst().progress() == 50, "Halfway to interrupt");
    require(automation.extractItem(0, 1, false).isEmpty(), "Running input is reserved");
    require(automation.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
        "A hopper cannot refill the reserved slot");
    machine.energy.setStored(0);
    machine.serverTick();
    require(machine.lanes.getFirst().status() == Status.STALLED, "Stalls without power");

    var player = helper.makeMockServerPlayerInLevel();
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    var menu = new MachineMenu(42, player.getInventory(), machine);
    player.containerMenu = menu;
    int button = menu.index(menu.bound.screen().find("toggle_0").orElseThrow());
    int action = new ScreenNetwork.Action(42, button, 0, 0, 0, 0).pack();
    menu.clickMenuButton(player, action);
    require(machine.getPaused() && !machine.items.hasPlan(0), "The screen frees reservations without power");
    require(automation.extractItem(0, 1, true).getCount() == 1, "Paused iron can be taken");
    menu.clickMenuButton(player, action);
    machine.energy.setStored(100_000);
    tick(machine, 150);
    require(machine.items.getStackInSlot(0).is(Items.GOLD_INGOT), "Resume finishes from saved progress");
    require(automation.extractItem(0, 1, false).is(Items.GOLD_INGOT), "Gold can be extracted");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void sharedLanesSurviveSaveAndInterrupt(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.DUAL_CRUSHER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    machine.items.insertItem(0, new ItemStack(Items.COPPER_ORE, 2), false);
    machine.energy.setStored(100_000);
    tick(machine, 31);
    require(machine.items.plan(0).take() == 2, "Both lanes reserve the shared input");
    require(machine.items.plan(1).add().getCount() == 4, "Both lanes reserve the shared output");
    machine.toggle(0);
    require(machine.items.plan(0).take() == 1, "Interrupt releases only lane A's share");
    var saved = machine.saveWithFullMetadata(level.registryAccess());
    var copy = (ProcessorEntity) BlockEntity.loadStatic(pos, machine.getBlockState(), saved, level.registryAccess());
    copy.setLevel(level);
    require(copy.getPaused() && copy.lanes.get(1).status() == Status.RUNNING, "Independent lane states restored");
    require(copy.items.plan(0).take() == 1, "Lane B reservation restored");
    copy.toggle(0);
    tick(copy, 70);
    require(copy.items.getStackInSlot(0).isEmpty(), "Both lanes consumed their own ore");
    require(copy.items.getStackInSlot(1).getCount() == 4, "Both lanes produced exactly two copper");
    machine.clearContent();
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void commandReplacementClearsButDestroyDrops(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    for (String command : new String[] {"setblock", "fill"}) {
      for (String mode : new String[] {"replace", "destroy"}) {
        for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) drop.discard();
        var machine = place(helper, CompanionMod.TRANSMUTER);
        machine.items.insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        machine.energy.setStored(100_000);
        tick(machine, 5);
        String coordinates = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String arguments = command.equals("fill") ? coordinates + " " + coordinates : coordinates;
        level.getServer().getCommands().performPrefixedCommand(
            level.getServer().createCommandSourceStack().withLevel(level).withPermission(4),
            command + " " + arguments + " minecraft:air " + mode);
        int iron = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
            .filter(drop -> drop.getItem().is(Items.IRON_INGOT)).mapToInt(drop -> drop.getItem().getCount()).sum();
        require(iron == (mode.equals("destroy") ? 1 : 0), command + " " + mode + " dropped " + iron + " iron");
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void crusherTankRejectsMixingAndPersists(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    for (var side : Direction.values()) {
      var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
      require(handler != null && handler.getTanks() == 1 && handler.getTankCapacity(0) == 4_000,
          "Crusher exposes one 4-bucket tank on every face");
      require(handler.fill(new FluidStack(Fluids.WATER, 5_000), FluidAction.SIMULATE) == 4_000,
          "Simulated fill respects capacity");
      require(machine.getTankFluid().isEmpty(), "Simulation leaves the tank empty");
    }
    var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
    require(handler.fill(new FluidStack(Fluids.WATER, 5_000), FluidAction.EXECUTE) == 4_000,
        "Water is accepted up to capacity");
    require(handler.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0, "Full tank refuses overflow");
    handler.drain(3_999, FluidAction.EXECUTE);
    require(handler.fill(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE) == 0,
        "Even one mB of water prevents mixing with lava");
    require(handler.drain(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE).isEmpty(),
        "Draining the wrong fluid does not remove water");
    handler.drain(1, FluidAction.EXECUTE);
    require(handler.fill(new FluidStack(Fluids.LAVA, 2_000), FluidAction.EXECUTE) == 2_000,
        "An emptied tank accepts lava");
    require(handler.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE) == 0,
        "Water cannot mix into lava either");
    var saved = machine.saveWithFullMetadata(level.registryAccess());
    var copy = (ProcessorEntity) BlockEntity.loadStatic(pos, machine.getBlockState(), saved, level.registryAccess());
    require(copy.getTankFluid().getFluid() == Fluids.LAVA && copy.getTankFluid().getAmount() == 2_000,
        "Fluid type and amount survive save/load");
    saved.remove("fluids");
    var legacy = (ProcessorEntity) BlockEntity.loadStatic(pos, machine.getBlockState(), saved, level.registryAccess());
    require(legacy.getTankFluid().isEmpty(), "Older saves start with an empty tank");
    machine.clearContent();
    require(machine.getTankFluid().isEmpty(), "Clearing the machine clears its fluid");
    for (var other : java.util.List.of(CompanionMod.TRANSMUTER, CompanionMod.DUAL_CRUSHER)) {
      place(helper, other);
      require(level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP) == null,
          "Only the single Crusher has a fluid tank");
    }
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void crusherBucketsFillDrainAndRefuseMixing(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    var player = helper.makeMockServerPlayerInLevel();
    player.getAbilities().instabuild = false;
    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    var state = machine.getBlockState();
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
    require(state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit)
        == ItemInteractionResult.CONSUME, "Water bucket handled by the block");
    require(player.getMainHandItem().is(Items.BUCKET) && machine.getTankFluid().getAmount() == 1_000,
        "Filling consumes the water and returns an empty bucket");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
    require(state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit)
        == ItemInteractionResult.FAIL, "Mixed bucket stops before vanilla can place lava");
    require(player.getMainHandItem().is(Items.LAVA_BUCKET)
        && machine.getTankFluid().getFluid() == Fluids.WATER && machine.getTankFluid().getAmount() == 1_000,
        "Rejected transfer leaves the bucket and tank unchanged");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
    state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    require(player.getMainHandItem().is(Items.WATER_BUCKET) && machine.getTankFluid().isEmpty(),
        "An empty bucket drains water");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
    state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    require(player.getMainHandItem().is(Items.BUCKET) && machine.getTankFluid().getFluid() == Fluids.LAVA,
        "Lava can be put in after draining water");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
    state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    require(player.getMainHandItem().is(Items.LAVA_BUCKET) && machine.getTankFluid().isEmpty(),
        "An empty bucket drains lava");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  private static ProcessorEntity place(GameTestHelper helper, CompanionMod.Machine machine) {
    var pos = helper.absolutePos(CENTER);
    helper.getLevel().setBlock(pos, machine.block.get().defaultBlockState(), 3);
    return (ProcessorEntity) helper.getLevel().getBlockEntity(pos);
  }

  private static void tick(ProcessorEntity machine, int ticks) {
    for (int i = 0; i < ticks; i++) machine.serverTick();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
