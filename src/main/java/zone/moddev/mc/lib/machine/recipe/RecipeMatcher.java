package zone.moddev.mc.lib.machine.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *   <li>Item and fluid parts in one group share its slots and tanks through {@link InputClaims}:
 *       nothing counts twice, and a recipe matches whenever some sharing meets every part, whatever
 *       order they're listed in.
 *   <li>Fluids are totalled across the group's tanks of fluids in the part's set.
 *   <li>Energy only needs one tick's share to start, {@code ceil(amount / time)}, because it is
 *       drawn while the recipe runs.
 * </ul>
 */
public final class RecipeMatcher {
  private RecipeMatcher() {}

  /**
   * Whether {@code recipe} can start: every input present and room for every product once the
   * inputs are gone, counting chance products as certain. A product may land in the slot or tank
   * its own input empties. Changes nothing, and no storage's change callback runs.
   */
  public static MatchResult check(MachineRecipe recipe, MachineInventory inventory) {
    var taken = new HashMap<String, long[]>();
    for (var entry : recipe.inputs().entrySet()) {
      var key = entry.getKey();
      var group = inventory.group(key).orElse(null);
      if (group == null) return new MatchResult(MatchResult.Reason.NO_GROUP, key, -1);
      var result = checkInputs(key, group, entry.getValue(), recipe.time(), taken);
      if (!result.ok()) return result;
    }
    for (var entry : recipe.outputs().entrySet()) {
      var key = entry.getKey();
      var group = inventory.group(key).orElse(null);
      if (group == null) return new MatchResult(MatchResult.Reason.NO_GROUP, key, -1);
      var result = checkOutputs(key, group, entry.getValue(), taken.get(key));
      if (!result.ok()) return result;
    }
    return MatchResult.OK;
  }

  /**
   * Whether the machine holds at least some of one of {@code recipe}'s item or fluid inputs, which
   * is what makes a missing input worth reporting. Stored energy doesn't count, because a powered
   * machine with nothing in it hasn't begun on any recipe. A recipe that needs only energy always
   * passes. Changes nothing.
   */
  public static boolean holdsSomeInput(MachineRecipe recipe, MachineInventory inventory) {
    boolean energyOnly = true;
    for (var entry : recipe.inputs().entrySet()) {
      var group = inventory.group(entry.getKey()).orElse(null);
      for (var part : entry.getValue()) {
        if (part instanceof EnergyPart) continue;
        energyOnly = false;
        if (group == null) continue;
        long need = claim(new InputClaims(available(group)), group, part, recipe.time());
        if (need >= 0 && need < part.amount()) return true;
      }
    }
    return energyOnly;
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
        if (!take(group, entry.getValue(), tx)) return false;
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

  /** Checks one group's inputs, and records what {@link #take} would take from it. */
  private static MatchResult checkInputs(
      String key,
      SlotGroup group,
      List<MachineIngredient> ingredients,
      int time,
      Map<String, long[]> taken) {
    long[] held = available(group);
    var claims = new InputClaims(held);
    for (int i = 0; i < ingredients.size(); i++) {
      long need = claim(claims, group, ingredients.get(i), time);
      if (need < 0) return new MatchResult(MatchResult.Reason.WRONG_KIND, key, i);
      if (need > 0) return new MatchResult(MatchResult.Reason.MISSING_INPUT, key, i);
    }
    var claimed = new long[held.length];
    for (int entry = 0; entry < held.length; entry++) claimed[entry] = claims.claimed(entry);
    taken.put(key, claimed);
    return MatchResult.OK;
  }

  /** Checks one group's products; {@code taken} is what its inputs use, null when it has none. */
  private static MatchResult checkOutputs(
      String key, SlotGroup group, List<MachineProduct> products, long[] taken) {
    var stacks = new ArrayList<ItemStack>();
    var fluids = new ArrayList<FluidAmount>();
    for (int i = 0; i < products.size(); i++) {
      boolean fits;
      if (products.get(i) instanceof ItemProduct item && group instanceof ItemGroup items) {
        stacks.add(item.stack());
        fits = taken == null ? items.fits(stacks) : items.fits(stacks, taken);
      } else if (products.get(i) instanceof FluidProduct fluid
          && group instanceof FluidGroup tanks) {
        fluids.add(fluid.fluid());
        fits = taken == null ? tanks.fits(fluids) : tanks.fits(fluids, taken);
      } else return new MatchResult(MatchResult.Reason.WRONG_KIND, key, i);
      if (!fits) return new MatchResult(MatchResult.Reason.NO_ROOM, key, i);
    }
    return MatchResult.OK;
  }

  /** What each slot or tank of {@code group} has free, or the stored energy as one entry. */
  private static long[] available(SlotGroup group) {
    return switch (group) {
      case ItemGroup items -> {
        var counts = new long[items.size()];
        for (int slot = 0; slot < counts.length; slot++) counts[slot] = items.free(slot);
        yield counts;
      }
      case FluidGroup fluids -> {
        var amounts = new long[fluids.tanks()];
        for (int tank = 0; tank < amounts.length; tank++) amounts[tank] = fluids.free(tank);
        yield amounts;
      }
      case EnergyGroup energy -> new long[] {energy.stored()};
    };
  }

  /** Adds {@code ingredient} to {@code claims}; returns what's still needed, -1 if wrong kind. */
  private static long claim(
      InputClaims claims, SlotGroup group, MachineIngredient ingredient, int time) {
    return switch (ingredient) {
      case ItemPart part when group instanceof ItemGroup items ->
          claims.claim(part.count(), slot -> part.items().test(items.peek(slot)));
      case FluidPart part when group instanceof FluidGroup fluids ->
          claims.claim(
              part.millibuckets(), tank -> part.fluids().contains(fluids.peek(tank).fluid()));
      case EnergyPart part when group instanceof EnergyGroup ->
          claims.claim(Math.ceilDiv(part.amount(), time), any -> true);
      default -> -1;
    };
  }

  /** Takes what {@code ingredients} claim from {@code group}. Energy groups give nothing here. */
  private static boolean take(SlotGroup group, List<MachineIngredient> ingredients, MachineTx tx) {
    if (group instanceof EnergyGroup)
      return ingredients.stream().allMatch(EnergyPart.class::isInstance);
    long[] held = available(group);
    var claims = new InputClaims(held);
    for (var ingredient : ingredients) if (claim(claims, group, ingredient, 1) != 0) return false;
    for (int entry = 0; entry < held.length; entry++) {
      int amount = (int) claims.claimed(entry);
      if (amount == 0) continue;
      int taken =
          switch (group) {
            case ItemGroup items -> items.extract(entry, amount, tx);
            case FluidGroup fluids -> fluids.extract(entry, amount, tx);
            case EnergyGroup energy -> 0;
          };
      if (taken != amount) return false;
    }
    return true;
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
