package zone.moddev.mc.lib.machine.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.EnergyPart;
import zone.moddev.mc.lib.machine.inventory.EnergyGroup;
import zone.moddev.mc.lib.machine.inventory.FluidGroup;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.MachineTx;
import zone.moddev.mc.lib.machine.inventory.SlotGroup;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.inventory.TankPlan;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.FluidProduct;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;

/**
 * One running recipe cycle: what it takes from which slots and tanks, where its products will go,
 * and the energy it needs. It holds everything it needs to finish, so a reload that changes or
 * removes the recipe can't change a cycle that has begun. Groups are named by key and slots and
 * tanks by index within the group, as in {@link RecipeMatcher}.
 *
 * <p>Reserving and restoring work from these fields alone, so a plan may be built by hand.
 *
 * @param recipe the recipe it was made from
 * @param time ticks the cycle takes
 * @param items item inputs, by slot
 * @param fluids fluid inputs, by tank
 * @param products each product with where it lands; chance products are placed as if certain
 * @param energy the energy each energy group must supply over the cycle
 */
public record CyclePlan(
    ResourceLocation recipe,
    int time,
    List<ItemTake> items,
    List<FluidTake> fluids,
    List<Placed> products,
    Map<String, Long> energy) {
  /** The save form. */
  public static final Codec<CyclePlan> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      ResourceLocation.CODEC.fieldOf("recipe").forGetter(CyclePlan::recipe),
                      Codec.intRange(1, Integer.MAX_VALUE)
                          .fieldOf("time")
                          .forGetter(CyclePlan::time),
                      ItemTake.CODEC
                          .listOf()
                          .optionalFieldOf("items", List.of())
                          .forGetter(CyclePlan::items),
                      FluidTake.CODEC
                          .listOf()
                          .optionalFieldOf("fluids", List.of())
                          .forGetter(CyclePlan::fluids),
                      Placed.CODEC
                          .listOf()
                          .optionalFieldOf("products", List.of())
                          .forGetter(CyclePlan::products),
                      Codec.unboundedMap(Codec.STRING, Codec.LONG)
                          .optionalFieldOf("energy", Map.of())
                          .forGetter(CyclePlan::energy))
                  .apply(instance, CyclePlan::new));

  /** What the cycle takes from one item slot. */
  public record ItemTake(String group, int slot, ItemStack stack) {
    static final Codec<ItemTake> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        Codec.STRING.fieldOf("group").forGetter(ItemTake::group),
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .fieldOf("slot")
                            .forGetter(ItemTake::slot),
                        ItemStack.CODEC.fieldOf("stack").forGetter(ItemTake::stack))
                    .apply(instance, ItemTake::new));

    /** Copies the stack. */
    public ItemTake {
      stack = stack.copy();
    }

    /** A copy of the stack. */
    @Override
    public ItemStack stack() {
      return stack.copy();
    }
  }

  /** What the cycle takes from one tank. */
  public record FluidTake(String group, int tank, FluidAmount fluid) {
    static final Codec<FluidTake> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        Codec.STRING.fieldOf("group").forGetter(FluidTake::group),
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .fieldOf("tank")
                            .forGetter(FluidTake::tank),
                        FluidAmount.CODEC.fieldOf("fluid").forGetter(FluidTake::fluid))
                    .apply(instance, FluidTake::new));
  }

  /** A product and where it goes: how much into each slot or tank of the group. */
  public record Placed(String group, MachineProduct product, List<At> at) {
    static final Codec<Placed> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        Codec.STRING.fieldOf("group").forGetter(Placed::group),
                        MachineProduct.CODEC.fieldOf("product").forGetter(Placed::product),
                        At.CODEC.listOf().fieldOf("at").forGetter(Placed::at))
                    .apply(instance, Placed::new));

    /** Copies the list. */
    public Placed {
      at = List.copyOf(at);
    }
  }

  /** {@code amount} items or mB into slot or tank {@code index} of a group. */
  public record At(int index, int amount) {
    static final Codec<At> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .fieldOf("index")
                            .forGetter(At::index),
                        Codec.intRange(1, Integer.MAX_VALUE)
                            .fieldOf("amount")
                            .forGetter(At::amount))
                    .apply(instance, At::new));
  }

  /** Copies the lists and the map. */
  public CyclePlan {
    items = List.copyOf(items);
    fluids = List.copyOf(fluids);
    products = List.copyOf(products);
    energy = Map.copyOf(energy);
  }

  /** One slot or tank of a group. */
  private record Spot(String group, int index) {}

  /** This cycle's plan for each slot and tank it touches, items and fluids combined per spot. */
  private record Plans(Map<Spot, SlotPlan> items, Map<Spot, TankPlan> tanks) {}

  /**
   * What running {@code recipe} would reserve now, ignoring energy; empty if it can't. Changes
   * nothing. Chance products are placed as if certain.
   */
  public static Optional<CyclePlan> of(
      ResourceLocation id, MachineRecipe recipe, MachineInventory inventory) {
    var items = new ArrayList<ItemTake>();
    var fluids = new ArrayList<FluidTake>();
    var energy = new LinkedHashMap<String, Long>();
    var taken = new HashMap<String, long[]>();
    for (var entry : recipe.inputs().entrySet()) {
      var key = entry.getKey();
      var group = inventory.group(key).orElse(null);
      if (group == null) return Optional.empty();
      if (group instanceof EnergyGroup) {
        long total = 0;
        for (var part : entry.getValue()) {
          if (!(part instanceof EnergyPart energyPart)) return Optional.empty();
          total += energyPart.amount();
        }
        energy.put(key, total);
        continue;
      }
      var held = RecipeMatcher.available(group);
      var claims = new InputClaims(held);
      for (var ingredient : entry.getValue())
        if (RecipeMatcher.claim(claims, group, ingredient, recipe.time()) != 0)
          return Optional.empty();
      var claimed = new long[held.length];
      for (int at = 0; at < held.length; at++) {
        claimed[at] = claims.claimed(at);
        int amount = (int) claimed[at];
        if (amount == 0) continue;
        if (group instanceof ItemGroup slots)
          items.add(new ItemTake(key, at, slots.peek(at).copyWithCount(amount)));
        else if (group instanceof FluidGroup tanks)
          fluids.add(new FluidTake(key, at, tanks.peek(at).withAmount(amount)));
      }
      taken.put(key, claimed);
    }
    var products = new ArrayList<Placed>();
    for (var entry : recipe.outputs().entrySet()) {
      var key = entry.getKey();
      var group = inventory.group(key).orElse(null);
      if (group == null) return Optional.empty();
      var list = entry.getValue();
      var placed = place(group, list, taken.getOrDefault(key, new long[size(group)]));
      if (placed == null) return Optional.empty();
      for (int i = 0; i < list.size(); i++) {
        var at = new ArrayList<At>();
        for (int slot = 0; slot < placed[i].length; slot++)
          if (placed[i][slot] > 0) at.add(new At(slot, placed[i][slot]));
        products.add(new Placed(key, list.get(i), at));
      }
    }
    return Optional.of(new CyclePlan(id, recipe.time(), items, fluids, products, energy));
  }

  /**
   * Reserves the inputs and the space for the products, all or nothing: if any slot or tank
   * refuses, nothing is reserved. Call it on the inventory {@link #of} looked at, before another
   * cycle reserves, or it may be refused.
   *
   * @return whether everything was reserved
   */
  public boolean reserve(MachineInventory inventory) {
    var plans = plans();
    if (plans == null) return false;
    try (var tx = inventory.begin()) {
      for (var entry : plans.items.entrySet()) {
        var spot = entry.getKey();
        var group = items(inventory, spot.group);
        if (group == null || !group.reserve(spot.index, entry.getValue(), tx)) return false;
      }
      for (var entry : plans.tanks.entrySet()) {
        var spot = entry.getKey();
        var group = fluids(inventory, spot.group);
        if (group == null || !group.reserve(spot.index, entry.getValue(), tx)) return false;
      }
      tx.commit();
      return true;
    }
  }

  /**
   * Puts this plan back on storages that already hold what it reserved, for loading a saved cycle.
   * Nothing is checked beyond that the inputs are still there; the room was reserved when it was
   * saved. All or nothing.
   *
   * @return false, with no plan left behind, if a group, slot or tank is gone or no longer holds
   *     the reserved inputs
   */
  public boolean restore(MachineInventory inventory) {
    var plans = plans();
    if (plans == null) return false;
    for (var entry : plans.items.entrySet()) {
      var spot = entry.getKey();
      var group = items(inventory, spot.group);
      if (group == null || group.free(spot.index) < entry.getValue().take()) return false;
    }
    for (var entry : plans.tanks.entrySet()) {
      var spot = entry.getKey();
      var group = fluids(inventory, spot.group);
      if (group == null || group.free(spot.index) < entry.getValue().take()) return false;
    }
    for (var take : items) {
      var group = items(inventory, take.group);
      if (group == null) return false;
      if (!ItemStack.isSameItemSameComponents(group.peek(take.slot), take.stack)) return false;
    }
    for (var take : fluids) {
      var group = fluids(inventory, take.group);
      if (group == null || !group.peek(take.tank).sameFluid(take.fluid)) return false;
    }
    try (var tx = inventory.begin()) {
      for (var entry : plans.items.entrySet()) {
        var spot = entry.getKey();
        if (!items(inventory, spot.group).restore(spot.index, entry.getValue(), tx)) return false;
      }
      for (var entry : plans.tanks.entrySet()) {
        var spot = entry.getKey();
        if (!fluids(inventory, spot.group).restore(spot.index, entry.getValue(), tx)) return false;
      }
      tx.commit();
      return true;
    }
  }

  /** Gives back everything {@link #reserve} or {@link #restore} reserved. */
  public void release(MachineInventory inventory) {
    try (var tx = inventory.begin()) {
      release(inventory, tx);
      tx.commit();
    }
  }

  /**
   * Rolls each product's chance, then in one transaction releases this plan, takes the inputs and
   * inserts the hits where they were placed. A product that misses leaves its space free, and
   * nothing is kept of the roll, so a retry rolls again.
   *
   * @return false, with the plan still in place and nothing changed, if an input is gone or a
   *     product no longer fits where it was placed
   */
  public boolean finish(MachineInventory inventory, RandomSource random) {
    if (plans() == null) return false;
    var hits = new ArrayList<Placed>();
    for (var placed : products)
      if (placed.product.chance() >= 1 || random.nextFloat() < placed.product.chance())
        hits.add(placed);
    if (!canFinish(inventory, hits)) return false;
    try (var tx = inventory.begin()) {
      release(inventory, tx);
      for (var take : items) {
        var group = items(inventory, take.group);
        if (group == null) return false;
        var held = group.peek(take.slot);
        int count = take.stack.getCount();
        if (!ItemStack.isSameItemSameComponents(held, take.stack) || held.getCount() < count)
          return false;
        if (group.extract(take.slot, count, tx) != count) return false;
      }
      for (var take : fluids) {
        var group = fluids(inventory, take.group);
        if (group == null) return false;
        var held = group.peek(take.tank);
        if (!held.sameFluid(take.fluid) || held.amount() < take.fluid.amount()) return false;
        if (group.extract(take.tank, take.fluid.amount(), tx) != take.fluid.amount()) return false;
      }
      for (var placed : hits)
        for (var at : placed.at)
          if (!insert(inventory, placed, at, tx)) return false;
      tx.commit();
      return true;
    }
  }

  /**
   * Whether the inputs are still there and the rolled {@code hits} could still go where they were
   * placed, looking only at what the slots and tanks hold, with this cycle's takes gone. Changes
   * nothing and fires nothing, so a cycle that can't finish costs no transaction. A tank's
   * capacity and a slot's limit are left to the transaction.
   */
  private boolean canFinish(MachineInventory inventory, List<Placed> hits) {
    var left = new HashMap<Spot, Integer>();
    for (var take : items) {
      var group = items(inventory, take.group);
      if (group == null) return false;
      var held = group.peek(take.slot);
      var spot = new Spot(take.group, take.slot);
      int rest = left.getOrDefault(spot, held.getCount()) - take.stack.getCount();
      if (!ItemStack.isSameItemSameComponents(held, take.stack) || rest < 0) return false;
      left.put(spot, rest);
    }
    for (var take : fluids) {
      var group = fluids(inventory, take.group);
      if (group == null) return false;
      var held = group.peek(take.tank);
      var spot = new Spot(take.group, take.tank);
      int rest = left.getOrDefault(spot, held.amount()) - take.fluid.amount();
      if (!held.sameFluid(take.fluid) || rest < 0) return false;
      left.put(spot, rest);
    }
    var added = new HashMap<Spot, Integer>();
    for (var placed : hits)
      for (var at : placed.at) {
        var spot = new Spot(placed.group, at.index);
        int total = added.merge(spot, at.amount, Integer::sum);
        switch (placed.product) {
          case ItemProduct item -> {
            var group = items(inventory, placed.group);
            if (group == null) return false;
            var held = group.peek(at.index);
            int remaining = left.getOrDefault(spot, held.getCount());
            var stack = item.stack();
            if (remaining > 0 && !ItemStack.isSameItemSameComponents(held, stack)) return false;
            if (remaining + total > stack.getMaxStackSize()) return false;
          }
          case FluidProduct fluid -> {
            var group = fluids(inventory, placed.group);
            if (group == null) return false;
            var held = group.peek(at.index);
            int remaining = left.getOrDefault(spot, held.amount());
            if (remaining > 0 && !held.sameFluid(fluid.fluid())) return false;
          }
        }
      }
    return true;
  }

  private boolean insert(MachineInventory inventory, Placed placed, At at, MachineTx tx) {
    return switch (placed.product) {
      case ItemProduct item -> {
        var group = items(inventory, placed.group);
        yield group != null
            && group.insert(at.index, item.stack().copyWithCount(at.amount), tx) == at.amount;
      }
      case FluidProduct fluid -> {
        var group = fluids(inventory, placed.group);
        yield group != null
            && group.insert(at.index, fluid.fluid().withAmount(at.amount), tx) == at.amount;
      }
    };
  }

  private void release(MachineInventory inventory, MachineTx tx) {
    var plans = plans();
    if (plans == null) return;
    plans.items.forEach(
        (spot, plan) -> {
          var group = items(inventory, spot.group);
          if (group != null) group.release(spot.index, plan, tx);
        });
    plans.tanks.forEach(
        (spot, plan) -> {
          var group = fluids(inventory, spot.group);
          if (group != null) group.release(spot.index, plan, tx);
        });
  }

  /** The takes and placements summed per slot and tank, or null if two of them clash. */
  @Nullable
  private Plans plans() {
    var itemPlans = new LinkedHashMap<Spot, SlotPlan>();
    var tankPlans = new LinkedHashMap<Spot, TankPlan>();
    for (var take : items) {
      var plan = new SlotPlan(take.stack.getCount(), ItemStack.EMPTY);
      if (!add(itemPlans, new Spot(take.group, take.slot), plan)) return null;
    }
    for (var take : fluids) {
      var plan = new TankPlan(take.fluid.amount(), FluidAmount.EMPTY);
      if (!add(tankPlans, new Spot(take.group, take.tank), plan)) return null;
    }
    for (var placed : products)
      for (var at : placed.at) {
        var spot = new Spot(placed.group, at.index);
        boolean merged =
            switch (placed.product) {
              case ItemProduct item ->
                  add(itemPlans, spot, new SlotPlan(0, item.stack().copyWithCount(at.amount)));
              case FluidProduct fluid ->
                  add(tankPlans, spot, new TankPlan(0, fluid.fluid().withAmount(at.amount)));
            };
        if (!merged) return null;
      }
    return new Plans(itemPlans, tankPlans);
  }

  private static boolean add(Map<Spot, SlotPlan> plans, Spot spot, SlotPlan plan) {
    var sum = plans.containsKey(spot) ? plans.get(spot).plus(plan) : plan;
    if (sum == null) return false;
    plans.put(spot, sum);
    return true;
  }

  private static boolean add(Map<Spot, TankPlan> plans, Spot spot, TankPlan plan) {
    var sum = plans.containsKey(spot) ? plans.get(spot).plus(plan) : plan;
    if (sum == null) return false;
    plans.put(spot, sum);
    return true;
  }

  /** Where {@code products} land in {@code group}; null if they don't fit or are the wrong kind. */
  @Nullable
  private static int[][] place(SlotGroup group, List<MachineProduct> products, long[] taken) {
    if (group instanceof ItemGroup slots) {
      var stacks = new ArrayList<ItemStack>();
      for (var product : products) {
        if (!(product instanceof ItemProduct item)) return null;
        stacks.add(item.stack());
      }
      return slots.place(stacks, taken);
    }
    if (group instanceof FluidGroup tanks) {
      var amounts = new ArrayList<FluidAmount>();
      for (var product : products) {
        if (!(product instanceof FluidProduct fluid)) return null;
        amounts.add(fluid.fluid());
      }
      return tanks.place(amounts, taken);
    }
    return null;
  }

  private static int size(SlotGroup group) {
    return switch (group) {
      case ItemGroup slots -> slots.size();
      case FluidGroup tanks -> tanks.tanks();
      case EnergyGroup energy -> 0;
    };
  }

  @Nullable
  private static ItemGroup items(MachineInventory inventory, String key) {
    return inventory.group(key).orElse(null) instanceof ItemGroup group ? group : null;
  }

  @Nullable
  private static FluidGroup fluids(MachineInventory inventory, String key) {
    return inventory.group(key).orElse(null) instanceof FluidGroup group ? group : null;
  }
}
