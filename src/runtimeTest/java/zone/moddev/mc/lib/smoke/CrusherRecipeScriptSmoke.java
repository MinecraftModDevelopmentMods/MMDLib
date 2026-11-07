package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

/** Exercise the normal recipe loader and its live recipes, not the startup content loader. */
final class CrusherRecipeScriptSmoke {
  static void check(GameTestHelper helper, BlockPos relative) {
    var level = helper.getLevel();
    var manager = level.getRecipeManager();
    for (String absent : new String[] {"removed", "input_removed", "output_removed", "query_failed"}) {
      require(manager.byKey(id("ct_crusher_" + absent)).isEmpty(), "recipe should be absent: " + absent);
    }
    var added = manager.byKey(id("ct_crusher_added")).orElseThrow().value();
    require(added instanceof CrushingRecipe && added.getType() == CrushingRecipe.TYPE.get(), "typed recipe registered");
    require(((CrushingRecipe) added).matches(new SingleRecipeInput(new ItemStack(Items.CLAY_BALL)), level), "input matches");
    var result = added.getResultItem(level.registryAccess());
    require(result.is(Items.DIAMOND) && result.getCount() == 2, "output count preserved");
    result.shrink(1);
    require(added.getResultItem(level.registryAccess()).getCount() == 2, "output is defensive copy");
    var replaced = manager.byKey(id("ct_crusher_replaced")).orElseThrow().value().getResultItem(level.registryAccess());
    require(replaced.is(Items.IRON_NUGGET) && replaced.getCount() == 3, "remove then re-add replaces recipe");
    var json = manager.byKey(id("ct_crusher_json")).orElseThrow().value().getResultItem(level.registryAccess());
    require(json.is(Items.EMERALD) && json.getCount() == 2, "inherited JSON recipe API retained");

    var tagged = (CrushingRecipe) manager.byKey(id("ct_crusher_tag")).orElseThrow().value();
    require(tagged.matches(new SingleRecipeInput(new ItemStack(Items.OAK_PLANKS)), level)
        && tagged.matches(new SingleRecipeInput(new ItemStack(Items.BIRCH_PLANKS)), level), "tag ingredients work");
    var typed = new zone.moddev.mc.lib.compat.CraftTweaker.crusher.CrushingRecipeManager();
    var output = com.blamejared.crafttweaker.api.item.IItemStack.of(new ItemStack(Items.DIAMOND));
    var single = com.blamejared.crafttweaker.api.item.IItemStack.of(new ItemStack(Items.CLAY_BALL));
    rejected(() -> typed.addRecipe("bad_amount", output,
        com.blamejared.crafttweaker.api.item.IItemStack.of(new ItemStack(Items.CLAY_BALL, 2))));
    rejected(() -> typed.addRecipe("bad_output",
        com.blamejared.crafttweaker.api.item.IItemStack.of(ItemStack.EMPTY), single));
    rejected(() -> typed.addRecipe("bad_input", output,
        com.blamejared.crafttweaker.api.ingredient.IIngredient.fromIngredient(net.minecraft.world.item.crafting.Ingredient.EMPTY)));

    var floor = helper.absolutePos(relative);
    level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
    level.getEntitiesOfClass(ItemEntity.class, new AABB(floor.above()).inflate(2)).forEach(Entity::discard);
    var input = new ItemEntity(level, floor.getX() + .5, floor.getY() + 1.1, floor.getZ() + .5,
        new ItemStack(Items.CLAY_BALL));
    level.addFreshEntity(input);
    var player = helper.makeMockServerPlayerInLevel();
    player.getAbilities().instabuild = false;
    var hammer = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mmdlib:iron_crackhammer")));
    player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
    player.setPos(input.getX(), input.getY() + .1 - player.getEyeHeight(), input.getZ() - 2);
    player.setYRot(0); player.setXRot(0);
    CommonHooks.onItemRightClick(player, InteractionHand.MAIN_HAND);
    var outputs = level.getEntitiesOfClass(ItemEntity.class, new AABB(floor.above()).inflate(1),
        entity -> entity.getItem().is(Items.DIAMOND));
    require(!input.isAlive() && outputs.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 2
        && hammer.getDamageValue() == 1, "crackhammer consumes the script recipe with normal wear");
    helper.succeed();
  }

  private static void rejected(Runnable operation) {
    try { operation.run(); }
    catch (IllegalArgumentException expected) { return; }
    throw new AssertionError("Invalid crushing recipe was accepted");
  }

  private static ResourceLocation id(String path) { return ResourceLocation.parse("crafttweaker:" + path); }
  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
