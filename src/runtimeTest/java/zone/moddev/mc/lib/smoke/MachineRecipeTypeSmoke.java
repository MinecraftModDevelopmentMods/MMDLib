package zone.moddev.mc.lib.smoke;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonElement;

import com.mojang.serialization.JsonOps;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredRegister;

import io.netty.buffer.Unpooled;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.MachineRecipeTypes;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeInput;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;
import zone.moddev.mc.lib.machine.recipe.MatchResult;

/**
 * Registers the {@code mmdlib_smoke:grinding} machine recipe type and checks it against the recipe
 * files in {@code src/runtimeTest/resources/data/mmdlib_smoke/recipe/grinding/}: loading, lookup,
 * the network codec and the datagen builder.
 */
@EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD)
public final class MachineRecipeTypeSmoke {
  private static final String NAMESPACE = "mmdlib_smoke";
  private static final DeferredRegister<RecipeType<?>> TYPES =
      DeferredRegister.create(Registries.RECIPE_TYPE, NAMESPACE);
  private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
      DeferredRegister.create(Registries.RECIPE_SERIALIZER, NAMESPACE);

  /** The test recipe type; the GUI smoke test checks its JEI category. */
  public static final MachineRecipeType GRINDING =
      MachineRecipeTypes.register(TYPES, SERIALIZERS, "grinding");

  /** A second test type, for the processor smoke test. */
  public static final MachineRecipeType REFINING =
      MachineRecipeTypes.register(TYPES, SERIALIZERS, "refining");

  private static final ResourceLocation COPPER = id("grinding/copper");
  private static final ResourceLocation IRON = id("grinding/iron");

  private MachineRecipeTypeSmoke() {}

  @SubscribeEvent
  static void construct(FMLConstructModEvent event) {
    var bus = ModList.get().getModContainerById("mmdlib").orElseThrow().getEventBus();
    TYPES.register(bus);
    SERIALIZERS.register(bus);
  }

  /** The type and serializer are in the registries under the type's ID. */
  static void registered() {
    var id = id("grinding");
    require(GRINDING.id().equals(id), "type ID " + GRINDING.id());
    require(BuiltInRegistries.RECIPE_TYPE.get(id) == GRINDING, "type registered");
    require(
        BuiltInRegistries.RECIPE_SERIALIZER.get(id) == GRINDING.serializer(),
        "serializer registered");
    require(GRINDING.toString().equals("mmdlib_smoke:grinding"), "type name " + GRINDING);
  }

  /**
   * The four good files load as grinding recipes, two of them with inputs that name nothing; the one
   * without inputs is skipped.
   */
  static void recipesLoad(ServerLevel level) {
    var manager = level.getRecipeManager();
    var all = GRINDING.recipes(manager);
    require(
        all.size() == 4, "four grinding recipes, got " + all.stream().map(h -> h.id()).toList());
    var copper = recipe(level, COPPER);
    require(
        copper.time() == 200 && copper.inputs().size() == 3 && copper.outputs().size() == 2,
        "copper decoded");
    var iron = recipe(level, IRON);
    require(iron.outputs().get("water").size() == 1, "iron has a fluid output");
    require(manager.byKey(id("grinding/broken")).isEmpty(), "broken recipe skipped");
    var entry = (MachineRecipeEntry) manager.byKey(COPPER).orElseThrow().value();
    require(entry.getType() == GRINDING && entry.getSerializer() == GRINDING.serializer(), "type");
    require(entry.isSpecial(), "kept out of the recipe book");
    require(
        entry.getResultItem(level.registryAccess()).is(Items.RAW_COPPER)
            && entry.getResultItem(level.registryAccess()).getCount() == 2,
        "result item");
    require(
        entry.getIngredients().size() == 1 && entry.getIngredients().getFirst().test(ore(true)),
        "item ingredients");
  }

  /** A lookup finds the recipe the inventory can run, switches when the input changes. */
  static void lookupFinds(ServerLevel level) {
    var lookup = GRINDING.lookup();
    var grinder = new Grinder();
    require(lookup.find(level, grinder.inventory).isEmpty(), "nothing to grind");

    grinder.items.insertResult(0, new ItemStack(Items.COPPER_ORE, 2), false);
    require(id(lookup.find(level, grinder.inventory)).equals(COPPER), "copper ore");
    require(id(lookup.find(level, grinder.inventory)).equals(COPPER), "copper again");

    grinder.items.setStackInSlot(0, new ItemStack(Items.IRON_ORE));
    require(id(lookup.find(level, grinder.inventory)).equals(IRON), "switched to iron");

    // A full tank is no obstacle: the recipe drains 100 mB from it before adding 50.
    grinder.fluids.tank(0).fill(new FluidStack(Fluids.WATER, 4_000), execute());
    require(id(lookup.find(level, grinder.inventory)).equals(IRON), "water makes its own room");

    // The item output can't fit: the iron recipe doesn't match.
    grinder.blockOutputs();
    require(lookup.find(level, grinder.inventory).isEmpty(), "no room for the raw iron");

    var input = new MachineRecipeInput(grinder.inventory);
    require(input.size() == 4 && input.getItem(0).is(Items.IRON_ORE), "item view");
    require(!input.isEmpty(), "a machine input is never empty to vanilla");
    grinder.unblockOutputs();
    require(
        level
            .getRecipeManager()
            .getRecipeFor(GRINDING, input, level)
            .map(RecipeHolder::id)
            .orElseThrow()
            .equals(IRON),
        "vanilla getRecipeFor");
  }

  /** What explain picks from the recipes, and that it doesn't disturb the lookup. */
  static void explainPicksTheClosest(ServerLevel level) {
    var lookup = GRINDING.lookup();
    var grinder = new Grinder();
    expectAttempt(lookup, level, grinder, COPPER, MatchResult.Reason.MISSING_INPUT);

    grinder.items.insertResult(0, new ItemStack(Items.IRON_ORE), false);
    expectAttempt(lookup, level, grinder, IRON, MatchResult.Reason.OK);
    require(id(lookup.find(level, grinder.inventory)).equals(IRON), "explain left find working");

    // The inputs are there but the raw iron has no room: closer than copper's missing ore.
    grinder.blockOutputs();
    expectAttempt(lookup, level, grinder, IRON, MatchResult.Reason.NO_ROOM);

    // Nothing to tell the recipes apart: the machine's last recipe wins over the lowest ID.
    grinder.items.setStackInSlot(0, ItemStack.EMPTY);
    expectAttempt(lookup, level, grinder, IRON, MatchResult.Reason.MISSING_INPUT);

    var noGroups = MachineInventory.builder().build();
    expectAttempt(GRINDING.lookup(), level, noGroups, COPPER, MatchResult.Reason.NO_GROUP);

    // Only energy left, which doesn't count as having begun on a recipe: no recipe to name.
    grinder.fluids.tank(0).drain(1_000, execute());
    expectNothing(lookup, level, grinder.inventory, "an empty machine");

    // Something no recipe wants is the same as nothing, not "missing copper ore".
    grinder.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
    expectNothing(lookup, level, grinder.inventory, "a machine holding only cobblestone");

    // Holding one input of a recipe makes that recipe's missing input worth reporting.
    grinder.items.setStackInSlot(0, new ItemStack(Items.IRON_ORE));
    expectAttempt(GRINDING.lookup(), level, grinder, IRON, MatchResult.Reason.MISSING_INPUT);

    // A recipe the machine doesn't suit isn't named over "nothing to work with" for one it does.
    var water = new FluidStorage();
    water.addTank(1_000, Role.INPUT);
    var waterOnly =
        MachineInventory.builder().group("water", StorageGroups.fluids(water, 0)).build();
    expectNothing(GRINDING.lookup(), level, waterOnly, "a machine with only an empty tank");
  }

  private static void expectNothing(
      zone.moddev.mc.lib.machine.recipe.MachineRecipeLookup lookup,
      ServerLevel level,
      MachineInventory inventory,
      String what) {
    var attempt = lookup.explain(level, inventory);
    require(attempt.isEmpty(), "nothing to report for " + what + ", got " + attempt);
  }

  private static void expectAttempt(
      zone.moddev.mc.lib.machine.recipe.MachineRecipeLookup lookup,
      ServerLevel level,
      Grinder grinder,
      ResourceLocation recipe,
      MatchResult.Reason reason) {
    expectAttempt(lookup, level, grinder.inventory, recipe, reason);
  }

  private static void expectAttempt(
      zone.moddev.mc.lib.machine.recipe.MachineRecipeLookup lookup,
      ServerLevel level,
      MachineInventory inventory,
      ResourceLocation recipe,
      MatchResult.Reason reason) {
    var attempt = lookup.explain(level, inventory).orElseThrow();
    require(
        attempt.recipe().id().equals(recipe) && attempt.result().reason() == reason,
        "expected " + recipe + " " + reason + ", got " + attempt);
  }

  /** Every loaded recipe survives the serializer's network codec. */
  static void streamCodecRoundTrips(ServerLevel level) {
    for (var holder : GRINDING.recipes(level.getRecipeManager())) {
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
      GRINDING.serializer().streamCodec().encode(buffer, holder.value());
      var decoded = GRINDING.serializer().streamCodec().decode(buffer);
      require(buffer.readableBytes() == 0, holder.id() + " read fully");
      require(decoded.getType() == GRINDING, holder.id() + " keeps its type");
      requireSame(holder.value().recipe(), decoded.recipe(), level, holder.id().toString());
    }
  }

  /** The builder writes the same recipe as the copper file, with the type's ID as "type". */
  static void builderMatchesFile(ServerLevel level) {
    var saved = new ArrayList<Recipe<?>>();
    var ids = new ArrayList<ResourceLocation>();
    var output =
        new RecipeOutput() {
          @Override
          public Advancement.Builder advancement() {
            return Advancement.Builder.recipeAdvancement();
          }

          @Override
          public void accept(
              ResourceLocation id,
              Recipe<?> recipe,
              AdvancementHolder advancement,
              ICondition... conditions) {
            ids.add(id);
            saved.add(recipe);
          }
        };
    var water = HolderSet.direct(Fluids.WATER.builtInRegistryHolder());
    MachineRecipeBuilder.recipe(GRINDING)
        .inputItems(
            "input",
            Ingredient.of(
                net.minecraft.tags.ItemTags.create(ResourceLocation.parse("c:ores/copper"))),
            1)
        .input("water", new MachineIngredient.FluidPart(water, 250))
        .inputEnergy("energy", 4_000)
        .outputItem("output", new ItemStack(Items.RAW_COPPER, 2))
        .outputItem("byproduct", new ItemStack(Items.GRAVEL), 0.25F)
        .time(200)
        .save(output, COPPER);
    require(ids.equals(List.of(COPPER)), "saved under its ID");
    var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    var json = Recipe.CODEC.encodeStart(ops, saved.getFirst()).getOrThrow().getAsJsonObject();
    require(json.get("type").getAsString().equals("mmdlib_smoke:grinding"), "type field " + json);
    var file = Recipe.CODEC.encodeStart(ops, recipeEntry(level, COPPER)).getOrThrow();
    require(json.equals(file), "builder JSON " + json + " vs file " + file);

    var fluidTag =
        level.registryAccess().lookupOrThrow(Registries.FLUID).getOrThrow(FluidTags.WATER);
    var iron =
        MachineRecipeBuilder.recipe(GRINDING)
            .inputItems("input", Ingredient.of(Items.IRON_ORE), 1)
            .inputFluids("water", fluidTag, 100)
            .inputEnergy("energy", 2_000)
            .outputItem("output", new ItemStack(Items.RAW_IRON, 2))
            .outputFluid("water", Fluids.WATER, 50)
            .time(100)
            .build();
    require(
        encode(ops, iron).equals(encode(ops, recipe(level, IRON))),
        "iron builder " + encode(ops, iron));

    try {
      MachineRecipeBuilder.recipe(GRINDING).inputEnergy("energy", 1).build();
      throw new AssertionError("A recipe without a time must be refused");
    } catch (IllegalStateException expected) {
    }
    try {
      MachineRecipeBuilder.recipe(GRINDING).time(10).build();
      throw new AssertionError("A recipe without inputs must be refused");
    } catch (IllegalStateException expected) {
    }
    try {
      MachineRecipeBuilder.recipe(GRINDING).inputEnergy("energy", 1).time(-5).build();
      throw new AssertionError("A negative time must be refused");
    } catch (IllegalStateException expected) {
    }

    var chance =
        MachineRecipeBuilder.recipe(GRINDING)
            .inputEnergy("energy", 1)
            .outputFluid("water", Fluids.WATER, 50, 0.25F)
            .time(1)
            .build();
    require(chance.outputs().get("water").getFirst().chance() == 0.25F, "fluid output chance");
  }

  /** A grinder's storages: ore in slot 0, outputs in 1 and 2, byproduct in 3, a water tank. */
  private static final class Grinder {
    final ItemStorage items = new ItemStorage(4).role(Role.INPUT, 0).role(Role.OUTPUT, 1, 2, 3);
    final FluidStorage fluids = new FluidStorage();
    final EnergyBuffer energy = new EnergyBuffer(100_000, 1_000);
    final MachineInventory inventory;

    Grinder() {
      fluids.addTank(4_000, Role.INPUT);
      fluids.tank(0).fill(new FluidStack(Fluids.WATER, 1_000), execute());
      energy.setStored(10_000);
      inventory =
          MachineInventory.builder()
              .group("input", StorageGroups.items(items, 0))
              .group("output", StorageGroups.items(items, 1, 2))
              .group("byproduct", StorageGroups.items(items, 3))
              .group("water", StorageGroups.fluids(fluids, 0))
              .group("energy", StorageGroups.energy(energy))
              .build();
    }

    void blockOutputs() {
      items.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
      items.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 64));
    }

    void unblockOutputs() {
      items.setStackInSlot(1, ItemStack.EMPTY);
      items.setStackInSlot(2, ItemStack.EMPTY);
    }
  }

  /**
   * Same groups, counts, products and time. Item ingredients compare by their item lists, because
   * the network sends a tag as the items in it.
   */
  private static void requireSame(
      MachineRecipe expected, MachineRecipe actual, ServerLevel level, String what) {
    var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    require(expected.time() == actual.time(), what + " time");
    require(encodeOutputs(ops, expected).equals(encodeOutputs(ops, actual)), what + " outputs");
    require(expected.inputs().keySet().equals(actual.inputs().keySet()), what + " input keys");
    expected
        .inputs()
        .forEach(
            (key, parts) -> {
              var other = actual.inputs().get(key);
              require(parts.size() == other.size(), what + " " + key + " size");
              for (int i = 0; i < parts.size(); i++) {
                var a = parts.get(i);
                var b = other.get(i);
                require(a.amount() == b.amount(), what + " " + key + " amount");
                switch (a) {
                  case MachineIngredient.ItemPart part ->
                      require(
                          b instanceof MachineIngredient.ItemPart same
                              && itemIds(part.items()).equals(itemIds(same.items())),
                          what + " " + key + " items");
                  case MachineIngredient.FluidPart part ->
                      require(
                          b instanceof MachineIngredient.FluidPart same
                              && part.fluids().stream()
                                  .toList()
                                  .equals(same.fluids().stream().toList()),
                          what + " " + key + " fluids");
                  case MachineIngredient.EnergyPart part ->
                      require(
                          b instanceof MachineIngredient.EnergyPart, what + " " + key + " energy");
                }
              }
            });
  }

  private static List<ResourceLocation> itemIds(Ingredient ingredient) {
    return Arrays.stream(ingredient.getItems())
        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
        .toList();
  }

  private static JsonElement encodeOutputs(RegistryOps<JsonElement> ops, MachineRecipe recipe) {
    return encode(ops, recipe).getAsJsonObject().get("outputs");
  }

  private static JsonElement encode(RegistryOps<JsonElement> ops, MachineRecipe recipe) {
    return MachineRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
  }

  private static MachineRecipe recipe(ServerLevel level, ResourceLocation id) {
    return recipeEntry(level, id).recipe();
  }

  private static MachineRecipeEntry recipeEntry(ServerLevel level, ResourceLocation id) {
    var holder =
        level
            .getRecipeManager()
            .byKey(id)
            .orElseThrow(() -> new AssertionError("Recipe not loaded: " + id));
    if (!(holder.value() instanceof MachineRecipeEntry entry))
      throw new AssertionError("Not a machine recipe: " + id);
    return entry;
  }

  private static ItemStack ore(boolean copper) {
    return new ItemStack(copper ? Items.COPPER_ORE : Items.IRON_ORE);
  }

  private static ResourceLocation id(java.util.Optional<RecipeHolder<MachineRecipeEntry>> holder) {
    return holder.orElseThrow(() -> new AssertionError("No recipe found")).id();
  }

  private static IFluidHandler.FluidAction execute() {
    return IFluidHandler.FluidAction.EXECUTE;
  }

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Machine recipe type: " + what);
  }
}
