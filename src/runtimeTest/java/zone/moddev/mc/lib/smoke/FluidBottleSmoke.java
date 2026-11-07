package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import zone.moddev.mc.lib.common.fluid.FluidBottleItem;
import zone.moddev.mc.lib.common.fluid.FluidBottles;

/**
 * Glass bottles and {@code #mmdlib:bottleable}, which test resources fill with molten copper only.
 */
final class FluidBottleSmoke {
  private FluidBottleSmoke() {}

  private static Fluid fluid(String id) {
    return BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
  }

  /** A player standing above {@code source}, looking straight down at it, holding two bottles. */
  private static ServerPlayer above(GameTestHelper helper, BlockPos source) {
    var player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    player.moveTo(source.getX() + .5, source.getY() + 1, source.getZ() + .5, 0, 90);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
    return player;
  }

  /** A bottle used on molten copper fills; the source stays; untagged fluids are refused. */
  static void world(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(center);
    var copper = fluid("mmdlib:copper_molten");
    level.setBlock(pos, copper.defaultFluidState().createLegacyBlock(), 3);
    var player = above(helper, pos);
    player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
    require(player.getMainHandItem().is(Items.GLASS_BOTTLE) && player.getMainHandItem().getCount() == 1,
        "one bottle used");
    var filled = player.getInventory().items.stream().filter(s -> s.is(FluidBottles.BOTTLE.get())).findFirst();
    require(filled.isPresent(), "a fluid bottle was given");
    var contents = FluidBottleItem.contents(filled.get());
    require(contents.is(copper) && contents.getAmount() == FluidBottles.CAPACITY, "it holds 333 mB of copper");
    require(level.getFluidState(pos).isSource(), "the source is left in place");

    var iron = fluid("mmdlib:iron_molten");
    level.setBlock(pos, iron.defaultFluidState().createLegacyBlock(), 3);
    var other = above(helper, pos);
    other.gameMode.useItem(other, level, other.getMainHandItem(), InteractionHand.MAIN_HAND);
    require(other.getMainHandItem().getCount() == 2, "untagged molten iron is not bottled");
    helper.succeed();
  }

  /** Water stays vanilla's: a water potion, not a fluid bottle. */
  static void water(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(center);
    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
    var player = above(helper, pos);
    player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
    require(player.getInventory().items.stream().anyMatch(s -> s.is(Items.POTION)), "vanilla made a water potion");
    require(player.getInventory().items.stream().noneMatch(s -> s.is(FluidBottles.BOTTLE.get())), "no fluid bottle");
    helper.succeed();
  }

  /** Tanks fill glass bottles with 333 mB of a bottleable fluid, and take it back. */
  static void tanks(GameTestHelper helper) {
    var copper = fluid("mmdlib:copper_molten");
    var tank = new FluidTank(1000);
    tank.fill(new FluidStack(copper, 1000), FluidTank.FluidAction.EXECUTE);
    var filled = FluidUtil.tryFillContainer(new ItemStack(Items.GLASS_BOTTLE), tank, 1000, null, true);
    require(filled.isSuccess() && filled.getResult().is(FluidBottles.BOTTLE.get()), "a tank fills a glass bottle");
    require(tank.getFluidAmount() == 1000 - FluidBottles.CAPACITY, "the tank gave 333 mB");

    var iron = new FluidTank(1000);
    iron.fill(new FluidStack(fluid("mmdlib:iron_molten"), 1000), FluidTank.FluidAction.EXECUTE);
    require(!FluidUtil.tryFillContainer(new ItemStack(Items.GLASS_BOTTLE), iron, 1000, null, true).isSuccess(),
        "untagged fluids are refused");
    var water = new FluidTank(1000);
    water.fill(new FluidStack(Fluids.WATER, 1000), FluidTank.FluidAction.EXECUTE);
    require(!FluidUtil.tryFillContainer(new ItemStack(Items.GLASS_BOTTLE), water, 1000, null, true).isSuccess(),
        "water stays vanilla's");

    var empty = new FluidTank(1000);
    var poured = FluidUtil.tryEmptyContainer(filled.getResult(), empty, 1000, null, true);
    require(poured.isSuccess() && poured.getResult().is(Items.GLASS_BOTTLE), "emptying leaves a glass bottle");
    require(empty.getFluidAmount() == FluidBottles.CAPACITY && empty.getFluid().is(copper), "333 mB poured");
    helper.succeed();
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError(what);
  }
}
