package zone.moddev.mc.lib.machine.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.EnergyPart;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.FluidPart;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart;
import zone.moddev.mc.lib.machine.inventory.EnergyGroup;
import zone.moddev.mc.lib.machine.inventory.FluidGroup;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.MachineTx;
import zone.moddev.mc.lib.machine.inventory.SlotGroup;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.FluidProduct;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;

/**
 * Matches {@link MachineRecipe}s against a {@link MachineInventory}, and runs their inputs and
 * outputs all or nothing. Groups are joined by key.
 *
 * <ul>
 *   <li>Item parts in one group claim matching items in list order; a later part can't count what
 *       an earlier one claimed.
 *   <li>Fluids are totalled across the group's tanks of fluids in the part's set.
 *   <li>Energy only needs one tick's share to start, {@code ceil(amount / time)}, because it is
 *       drawn while the recipe runs.
 * </ul>
 */
public final class RecipeMatcher {
  private RecipeMatcher() {}

  /**
   * Whether {@code recipe} can start: every input present and room for every product, counting
   * chance products as certain. Changes nothing.
   */
  public static MatchResult check(MachineRecipe recipe, MachineInventory inventory) {
    for (var entry : recipe.inputs().entrySet()) {
      var key = entry.getKey();
      var group = inventory.group(key).orElse(null);
      if (group == null) return new MatchResult(MatchResult.Reason.NO_GROUP, key, -1);
      var result = checkInputs(key, group, entry.getValue(), recipe.time());
      if (!result.ok()) return result;
    }
    for (var entry : recipe.outputs().entrySet()) {
      var key = entry.getKey();
      var group = inventory.group(key).orElse(null);
      if (group == null) return new MatchResult(MatchResult.Reason.NO_GROUP, key, -1);
      var result = checkOutputs(key, group, entry.getValue());
      if (!result.ok()) return result;
    }
    return MatchResult.OK;
  }

  /**
   * Takes the item and fluid inputs in one transaction, all or nothing. Energy is not taken; it is
   * drawn per tick while the recipe runs.
   *
   * @return whether every input was taken
   */
  public static boolean consume(MachineRecipe recipe, MachineInventory inventory) {
    try (var tx = inventory.begin()) {
      for (var entry : recipe.inputs().entrySet()) {
        var group = inventory.group(entry.getKey()).orElse(null);
        if (group == null) return false;
        for (var ingredient : entry.getValue()) if (!take(group, ingredient, tx)) return false;
      }
      tx.commit();
      return true;
    }
  }

  /**
   * Rolls each product's chance and inserts the hits in one transaction, all or nothing.
   *
   * @return whether everything rolled fitted; if not, nothing was inserted
   */
  public static boolean produce(
      MachineRecipe recipe, MachineInventory inventory, RandomSource random) {
    try (var tx = inventory.begin()) {
      for (var entry : recipe.outputs().entrySet()) {
        var group = inventory.group(entry.getKey()).orElse(null);
        if (group == null) return false;
        for (var product : entry.getValue()) {
          if (product.chance() < 1 && random.nextFloat() >= product.chance()) continue;
          if (!put(group, product, tx)) return false;
        }
      }
      tx.commit();
      return true;
    }
  }

  private static MatchResult checkInputs(
      String key, SlotGroup group, List<MachineIngredient> ingredients, int time) {
    long[] available = available(group);
    for (int i = 0; i < ingredients.size(); i++) {
      var ingredient = ingredients.get(i);
      long need =
          switch (ingredient) {
            case ItemPart part when group instanceof ItemGroup items ->
                claim(available, part.count(), slot -> part.items().test(items.peek(slot)));
            case FluidPart part when group instanceof FluidGroup fluids ->
                claim(
                    available,
                    part.millibuckets(),
                    tank -> part.fluids().contains(fluids.peek(tank).fluid()));
            case EnergyPart part when group instanceof EnergyGroup ->
                claim(available, Math.ceilDiv(part.amount(), time), any -> true);
            default -> -1;
          };
      if (need < 0) return new MatchResult(MatchResult.Reason.WRONG_KIND, key, i);
      if (need > 0) return new MatchResult(MatchResult.Reason.MISSING_INPUT, key, i);
    }
    return MatchResult.OK;
  }

  private static MatchResult checkOutputs(
      String key, SlotGroup group, List<MachineProduct> products) {
    var stacks = new ArrayList<ItemStack>();
    var fluids = new ArrayList<FluidAmount>();
    for (int i = 0; i < products.size(); i++) {
      boolean fits;
      if (products.get(i) instanceof ItemProduct item && group instanceof ItemGroup items) {
        stacks.add(item.stack());
        fits = items.fits(stacks);
      } else if (products.get(i) instanceof FluidProduct fluid
          && group instanceof FluidGroup tanks) {
        fluids.add(fluid.fluid());
        fits = tanks.fits(fluids);
      } else return new MatchResult(MatchResult.Reason.WRONG_KIND, key, i);
      if (!fits) return new MatchResult(MatchResult.Reason.NO_ROOM, key, i);
    }
    return MatchResult.OK;
  }

  /** What each slot or tank of {@code group} holds, or the stored energy as one entry. */
  private static long[] available(SlotGroup group) {
    return switch (group) {
      case ItemGroup items -> {
        var counts = new long[items.size()];
        for (int slot = 0; slot < counts.length; slot++) counts[slot] = items.peek(slot).getCount();
        yield counts;
      }
      case FluidGroup fluids -> {
        var amounts = new long[fluids.tanks()];
        for (int tank = 0; tank < amounts.length; tank++)
          amounts[tank] = fluids.peek(tank).amount();
        yield amounts;
      }
      case EnergyGroup energy -> new long[] {energy.stored()};
    };
  }

  /**
   * Claims up to {@code need} from matching entries of {@code available}; returns what's still
   * needed.
   */
  private static long claim(long[] available, long need, IntPredicate matches) {
    for (int i = 0; i < available.length && need > 0; i++) {
      if (available[i] <= 0 || !matches.test(i)) continue;
      long taken = Math.min(available[i], need);
      available[i] -= taken;
      need -= taken;
    }
    return need;
  }

  private static boolean take(SlotGroup group, MachineIngredient ingredient, MachineTx tx) {
    return switch (ingredient) {
      case ItemPart part when group instanceof ItemGroup items -> {
        int need = part.count();
        for (int slot = 0; slot < items.size() && need > 0; slot++)
          if (part.items().test(items.peek(slot))) need -= items.extract(slot, need, tx);
        yield need == 0;
      }
      case FluidPart part when group instanceof FluidGroup fluids -> {
        int need = part.millibuckets();
        for (int tank = 0; tank < fluids.tanks() && need > 0; tank++) {
          var held = fluids.peek(tank);
          if (!held.isEmpty() && part.fluids().contains(held.fluid()))
            need -= fluids.extract(tank, need, tx);
        }
        yield need == 0;
      }
      case EnergyPart part when group instanceof EnergyGroup -> true;
      default -> false;
    };
  }

  private static boolean put(SlotGroup group, MachineProduct product, MachineTx tx) {
    return switch (product) {
      case ItemProduct item when group instanceof ItemGroup items -> {
        var stack = item.stack();
        yield items.insert(stack, tx) == stack.getCount();
      }
      case FluidProduct fluid when group instanceof FluidGroup tanks ->
          tanks.insert(fluid.fluid(), tx) == fluid.fluid().amount();
      default -> false;
    };
  }
}
