package zone.moddev.mc.lib.smoke;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.material.Fluids;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MatchResult;
import zone.moddev.mc.lib.machine.recipe.MatchResult.Reason;
import zone.moddev.mc.lib.machine.recipe.RecipeMatcher;

/** Checks recipe matching, consuming and producing with the crusher recipe from recipes.md. */
final class MachineRecipeSmoke {
  private static final String CRUSHER =
      """
      {
        "inputs": {
          "input":  [{ "items": { "tag": "c:ores/copper" }, "count": 1 }],
          "water":  [{ "fluids": "minecraft:water", "amount": 250 }],
          "energy": [{ "energy": 4000 }]
        },
        "outputs": {
          "output":    [{ "item": { "id": "minecraft:raw_copper", "count": 2 } }],
          "byproduct": [{ "item": { "id": "minecraft:gravel" }, "chance": 0.25 }]
        },
        "time": 200
      }
      """;

  private MachineRecipeSmoke() {}

  /** A crusher's storages: ore in slot 0, output in 1 and 2, byproduct in 3. */
  private static final class Crusher {
    final int[] changes = new int[1];
    final ItemStorage items =
        new ItemStorage(4)
            .onChange(() -> changes[0]++)
            .role(Role.INPUT, 0)
            .role(Role.OUTPUT, 1, 2, 3);
    final FluidStorage fluids = new FluidStorage().onChange(() -> changes[0]++);
    final EnergyBuffer energy =
        new EnergyBuffer(10_000_000_000L, 1_000).onChange(() -> changes[0]++);
    final MachineInventory inventory;

    Crusher(int ore, int water, long fe, boolean withWater) {
      fluids.addTank(4_000, Role.INPUT);
      items.insertResult(0, new ItemStack(Items.COPPER_ORE, ore), false);
      fluids
          .tank(0)
          .fill(
              new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, water),
              net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
      energy.setStored(fe);
      var builder =
          MachineInventory.builder()
              .group("input", StorageGroups.items(items, 0))
              .group("output", StorageGroups.items(items, 1, 2))
              .group("byproduct", StorageGroups.items(items, 3))
              .group("energy", StorageGroups.energy(energy));
      if (withWater) builder.group("water", StorageGroups.fluids(fluids, 0));
      inventory = builder.build();
    }
  }

  static void check(ServerLevel level) {
    var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    var crusher = recipe(ops, CRUSHER);
    require(
        crusher.time() == 200 && crusher.inputs().size() == 3 && crusher.outputs().size() == 2,
        "decoded");

    require(
        RecipeMatcher.check(crusher, new Crusher(3, 1_000, 20, true).inventory).ok(),
        "matches on one tick's energy");
    expect(
        RecipeMatcher.check(crusher, new Crusher(3, 1_000, 19, true).inventory),
        Reason.MISSING_INPUT,
        "energy",
        0);
    expect(
        RecipeMatcher.check(crusher, new Crusher(0, 1_000, 20, true).inventory),
        Reason.MISSING_INPUT,
        "input",
        0);
    expect(
        RecipeMatcher.check(crusher, new Crusher(3, 249, 20, true).inventory),
        Reason.MISSING_INPUT,
        "water",
        0);
    expect(
        RecipeMatcher.check(crusher, new Crusher(3, 1_000, 20, false).inventory),
        Reason.NO_GROUP,
        "water",
        -1);

    var full = new Crusher(3, 1_000, 20, true);
    full.items.insertResult(3, new ItemStack(Items.COBBLESTONE, 64), false);
    int before = full.changes[0];
    expect(RecipeMatcher.check(crusher, full.inventory), Reason.NO_ROOM, "byproduct", 0);
    require(full.changes[0] == before, "check changed nothing");

    var run = new Crusher(3, 1_000, 20, true);
    require(RecipeMatcher.consume(crusher, run.inventory), "consume");
    require(run.items.getStackInSlot(0).getCount() == 2, "one ore taken");
    require(run.fluids.getFluidInTank(0).getAmount() == 750, "250 mB taken");
    require(run.energy.stored() == 20, "energy not taken by consume");
    require(
        !RecipeMatcher.consume(crusher, new Crusher(0, 1_000, 20, true).inventory),
        "nothing to consume");
    var partial = new Crusher(3, 100, 20, true);
    require(!RecipeMatcher.consume(crusher, partial.inventory), "not enough water");
    require(partial.items.getStackInSlot(0).getCount() == 3, "ore put back when water is short");

    // A full byproduct slot and a hit on the 25%: nothing is produced at all.
    full.items.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64));
    require(!RecipeMatcher.produce(crusher, full.inventory, fixed(0F)), "blocked");
    require(
        full.items.getStackInSlot(1).isEmpty() && full.items.getStackInSlot(2).isEmpty(),
        "rolled back");
    // A miss: only the copper.
    require(RecipeMatcher.produce(crusher, full.inventory, fixed(.99F)), "produced on a miss");
    require(
        full.items.getStackInSlot(1).is(Items.RAW_COPPER)
            && full.items.getStackInSlot(1).getCount() == 2,
        "copper");
    require(full.items.getStackInSlot(3).is(Items.COBBLESTONE), "byproduct untouched");
    require(RecipeMatcher.produce(crusher, run.inventory, fixed(0F)), "produced on a hit");
    require(run.items.getStackInSlot(3).is(Items.GRAVEL), "gravel on a hit");

    // Items are claimed once: 2 iron ingots and 2 #c:ingots need 4 ingots, not 3.
    var claims =
        recipe(
            ops,
            """
            {"inputs": {"input": [{"items": {"item": "minecraft:iron_ingot"}, "count": 2},
                                  {"items": {"tag": "c:ingots"}, "count": 2}]}, "time": 10}
            """);
    var ingots = new Crusher(0, 0, 0, true);
    ingots.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
    expect(RecipeMatcher.check(claims, ingots.inventory), Reason.MISSING_INPUT, "input", 1);
    ingots.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
    require(RecipeMatcher.check(claims, ingots.inventory).ok(), "four ingots are enough");

    // Energy costs above 2^31: 6,000,000,000 FE over 2 ticks needs 3,000,000,000 to start.
    var big = recipe(ops, "{\"inputs\": {\"energy\": [{\"energy\": 6000000000}]}, \"time\": 2}");
    require(
        RecipeMatcher.check(big, new Crusher(0, 0, 3_000_000_000L, true).inventory).ok(),
        "big energy");
    expect(
        RecipeMatcher.check(big, new Crusher(0, 0, 2_999_999_999L, true).inventory),
        Reason.MISSING_INPUT,
        "energy",
        0);

    var wrong =
        recipe(
            ops,
            "{\"inputs\": {\"water\": [{\"items\": {\"item\": \"minecraft:stone\"}}]}, \"time\":"
                + " 1}");
    expect(
        RecipeMatcher.check(wrong, new Crusher(0, 0, 0, true).inventory),
        Reason.WRONG_KIND,
        "water",
        0);
    var fluidOut =
        recipe(
            ops,
            "{\"inputs\": {\"energy\": [{\"energy\": 1}]}, \"outputs\": {\"water\": [{\"fluid\":"
                + " {\"id\": \"minecraft:lava\", \"amount\": 10}}]}, \"time\": 1}");
    expect(
        RecipeMatcher.check(fluidOut, new Crusher(0, 500, 1, true).inventory),
        Reason.NO_ROOM,
        "water",
        0);
    require(
        RecipeMatcher.check(fluidOut, new Crusher(0, 0, 1, true).inventory).ok(),
        "lava fits an empty tank");

    var both =
        MachineRecipe.CODEC
            .codec()
            .parse(
                ops,
                JsonParser.parseString(
                    "{\"inputs\": {\"input\": [{\"items\": {\"item\": \"minecraft:stone\"},"
                        + " \"energy\": 5}]}, \"time\": 1}"));
    require(
        both.error()
            .map(error -> error.message().contains("'items', 'fluids' or 'energy'"))
            .orElse(false),
        "both keys refused");
    var bothOut =
        MachineRecipe.CODEC
            .codec()
            .parse(
                ops,
                JsonParser.parseString(
                    "{\"inputs\": {\"energy\": [{\"energy\": 1}]}, \"outputs\": {\"output\":"
                        + " [{\"item\": {\"id\": \"minecraft:stone\"}, \"fluid\": {\"id\":"
                        + " \"minecraft:water\", \"amount\": 10}}]}, \"time\": 1}"));
    require(
        bothOut.error().map(error -> error.message().contains("'item' or 'fluid'")).orElse(false),
        "item and fluid on one output refused, got " + bothOut);
    var neitherOut =
        MachineRecipe.CODEC
            .codec()
            .parse(
                ops,
                JsonParser.parseString(
                    "{\"inputs\": {\"energy\": [{\"energy\": 1}]}, \"outputs\": {\"output\":"
                        + " [{\"chance\": 0.5}]}, \"time\": 1}"));
    require(
        neitherOut
            .error()
            .map(error -> error.message().contains("'item' or 'fluid'"))
            .orElse(false),
        "output with neither key refused, got " + neitherOut);
    mixedOutputs(ops);
    require(
        MachineRecipe.CODEC
            .codec()
            .parse(ops, JsonParser.parseString("{\"inputs\": {}, \"time\": 1}"))
            .error()
            .isPresent(),
        "no inputs refused");
    require(
        new FluidAmount(Fluids.WATER.builtInRegistryHolder(), DataComponentPatch.EMPTY, 1)
            .sameFluid(
                new FluidAmount(Fluids.WATER.builtInRegistryHolder(), DataComponentPatch.EMPTY, 9)),
        "same fluid");
  }

  /** One recipe that makes an item and a fluid: both land, or neither does. */
  private static void mixedOutputs(RegistryOps<com.google.gson.JsonElement> ops) {
    var mixed =
        recipe(
            ops,
            """
            {
              "inputs":  { "input": [{ "items": { "item": "minecraft:copper_ore" } }] },
              "outputs": {
                "output": [{ "item":  { "id": "minecraft:raw_copper", "count": 2 } }],
                "water":  [{ "fluid": { "id": "minecraft:water", "amount": 100 } }]
              },
              "time": 10
            }
            """);
    var run = new Crusher(1, 1_000, 0, true);
    require(RecipeMatcher.check(mixed, run.inventory).ok(), "item and fluid outputs match");
    require(RecipeMatcher.produce(mixed, run.inventory, fixed(.5F)), "item and fluid produced");
    require(
        run.items.getStackInSlot(1).is(Items.RAW_COPPER)
            && run.items.getStackInSlot(1).getCount() == 2,
        "item output");
    require(run.fluids.getFluidInTank(0).getAmount() == 1_100, "fluid output");

    // The tank is full: the item would fit, but nothing is made.
    var full = new Crusher(1, 4_000, 0, true);
    expect(RecipeMatcher.check(mixed, full.inventory), Reason.NO_ROOM, "water", 0);
    require(!RecipeMatcher.produce(mixed, full.inventory, fixed(.5F)), "blocked by the tank");
    require(full.items.getStackInSlot(1).isEmpty(), "item rolled back when the fluid can't fit");
    require(full.fluids.getFluidInTank(0).getAmount() == 4_000, "tank untouched");
  }

  private static MachineRecipe recipe(RegistryOps<com.google.gson.JsonElement> ops, String json) {
    return MachineRecipe.CODEC.codec().parse(ops, JsonParser.parseString(json)).getOrThrow();
  }

  /** A random whose nextFloat() is always {@code value}: 0 always hits a chance, 0.99 misses. */
  private static LegacyRandomSource fixed(float value) {
    return new LegacyRandomSource(0) {
      @Override
      public float nextFloat() {
        return value;
      }
    };
  }

  private static void expect(MatchResult result, Reason reason, String group, int index) {
    require(
        result.reason() == reason && group.equals(result.group()) && result.index() == index,
        "expected " + reason + " " + group + "[" + index + "], got " + result);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Machine recipe smoke failed: " + what);
  }
}
