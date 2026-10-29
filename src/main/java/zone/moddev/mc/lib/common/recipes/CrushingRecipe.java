package zone.moddev.mc.lib.common.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.MMDLib;

/**
 * The {@code mmdlib:crushing} recipe: one ingredient crushed into a result, as the crackhammer does
 * to blocks and dropped items.
 */
public final class CrushingRecipe implements Recipe<SingleRecipeInput> {
  public static final DeferredRegister<RecipeType<?>> TYPES =
      DeferredRegister.create(Registries.RECIPE_TYPE, MMDLib.MODID);
  public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
      DeferredRegister.create(Registries.RECIPE_SERIALIZER, MMDLib.MODID);
  public static final DeferredHolder<RecipeType<?>, RecipeType<CrushingRecipe>> TYPE =
      TYPES.<RecipeType<CrushingRecipe>>register("crushing", CrushingRecipeType::new);
  public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CrushingRecipe>>
      SERIALIZER = SERIALIZERS.register("crushing", Serializer::new);
  private final Ingredient ingredient;
  private final ItemStack result;

  /**
   * A recipe turning {@code ingredient} into {@code result}.
   *
   * @throws IllegalArgumentException if {@code result} is empty
   */
  public CrushingRecipe(Ingredient ingredient, ItemStack result) {
    if (result.isEmpty()) throw new IllegalArgumentException("Crushing result must not be empty");
    this.ingredient = ingredient;
    this.result = result.copy();
  }

  @Override
  public boolean matches(SingleRecipeInput input, Level level) {
    return ingredient.test(input.item());
  }

  @Override
  public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
    return result.copy();
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 1;
  }

  @Override
  public ItemStack getResultItem(HolderLookup.Provider registries) {
    return result.copy();
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return SERIALIZER.get();
  }

  @Override
  public RecipeType<?> getType() {
    return TYPE.get();
  }

  @Override
  public NonNullList<Ingredient> getIngredients() {
    return NonNullList.of(Ingredient.EMPTY, ingredient);
  }

  @Override
  public boolean isSpecial() {
    return true;
  }

  private static final class CrushingRecipeType implements RecipeType<CrushingRecipe> {
    @Override
    public String toString() {
      return MMDLib.MODID + ":crushing";
    }
  }

  /**
   * Reads and writes crushing recipes as JSON ({@code ingredient}, {@code result}) and over the
   * network.
   */
  public static final class Serializer implements RecipeSerializer<CrushingRecipe> {
    // Accept the existing generated results using "item", as well as the
    // 1.21 stack format using "id" and optional data components.
    private static final Codec<ItemStack> LEGACY_RESULT_CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        BuiltInRegistries.ITEM
                            .byNameCodec()
                            .fieldOf("item")
                            .forGetter(ItemStack::getItem),
                        Codec.intRange(1, 99)
                            .optionalFieldOf("count", 1)
                            .forGetter(ItemStack::getCount))
                    .apply(instance, ItemStack::new));

    static final Codec<ItemStack> RESULT_CODEC =
        Codec.withAlternative(ItemStack.CODEC, LEGACY_RESULT_CODEC);

    private static final MapCodec<CrushingRecipe> CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance
                    .group(
                        Ingredient.CODEC_NONEMPTY
                            .fieldOf("ingredient")
                            .forGetter(recipe -> recipe.ingredient),
                        RESULT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
                    .apply(instance, CrushingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> STREAM_CODEC =
        StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.ingredient,
            ItemStack.STREAM_CODEC,
            recipe -> recipe.result,
            CrushingRecipe::new);

    @Override
    public MapCodec<CrushingRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> streamCodec() {
      return STREAM_CODEC;
    }
  }
}
