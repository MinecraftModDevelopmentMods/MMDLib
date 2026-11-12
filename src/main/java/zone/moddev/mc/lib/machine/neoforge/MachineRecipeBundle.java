package zone.moddev.mc.lib.machine.neoforge;

import java.util.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.common.conditions.WithConditions;

import zone.moddev.mc.lib.machine.recipe.MachineRecipeInput;

/**
 * Authoring container expanded into ordinary machine recipes before publication
 * and sync.
 */
public record MachineRecipeBundle(
    Map<String, Optional<WithConditions<Recipe<?>>>> recipes)
    implements Recipe<MachineRecipeInput> {
    public static final ResourceLocation ID = ResourceLocation
        .parse("mmdlib:machine_recipes");
    public static final RecipeType<MachineRecipeBundle> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return ID.toString();
        }
    };
    private static final Codec<String> KEY = Codec.STRING
        .validate(key -> !key.isEmpty()
            && ResourceLocation.tryBuild("mmdlib", key) != null
                ? DataResult.success(key)
                : DataResult.error(() -> "Invalid recipe entry name: " + key));
    public static final MapCodec<MachineRecipeBundle> CODEC = Codec
        .unboundedMap(KEY, Recipe.CONDITIONAL_CODEC).fieldOf("recipes")
        .xmap(MachineRecipeBundle::new, MachineRecipeBundle::recipes);
    public static final RecipeSerializer<MachineRecipeBundle> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public MapCodec<MachineRecipeBundle> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MachineRecipeBundle> streamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        }
    };

    public MachineRecipeBundle {
        recipes = Collections.unmodifiableMap(new TreeMap<>(recipes));
    }

    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(MachineRecipeInput input,
        HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }
}
