package zone.moddev.mc.lib.machine.recipe;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * A vanilla recipe type whose recipes are {@link MachineRecipe}s, with its own
 * serializer. Recipe files name the type's ID as {@code "type"}, so datapacks,
 * {@code /reload} and recipe viewers work as for any recipe. Register one with
 * the loader's {@code MachineRecipeTypes.register}, which registers it under
 * its ID as both a recipe type and a recipe serializer.
 */
public final class MachineRecipeType implements RecipeType<MachineRecipeEntry> {
    private final ResourceLocation id;
    private final RecipeSerializer<MachineRecipeEntry> serializer;

    /** A type with the ID it will be registered under. */
    public MachineRecipeType(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
        MapCodec<MachineRecipeEntry> codec = MachineRecipe.CODEC
            .xmap(this::entry, MachineRecipeEntry::recipe);
        StreamCodec<RegistryFriendlyByteBuf, MachineRecipeEntry> streamCodec = MachineRecipe.STREAM_CODEC
            .map(this::entry, MachineRecipeEntry::recipe);
        serializer = new RecipeSerializer<>() {
            @Override
            public MapCodec<MachineRecipeEntry> codec() {
                return codec;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, MachineRecipeEntry> streamCodec() {
                return streamCodec;
            }
        };
    }

    /** The ID the type and its serializer are registered under. */
    public ResourceLocation id() {
        return id;
    }

    /** The serializer, registered under the same ID. */
    public RecipeSerializer<MachineRecipeEntry> serializer() {
        return serializer;
    }

    /** Every loaded recipe of this type. */
    public List<RecipeHolder<MachineRecipeEntry>> recipes(
        RecipeManager manager) {
        return manager.getAllRecipesFor(this);
    }

    /**
     * A new lookup for one machine; it remembers that machine's last recipe.
     */
    public MachineRecipeLookup lookup() {
        return new MachineRecipeLookup(this);
    }

    private MachineRecipeEntry entry(MachineRecipe recipe) {
        return new MachineRecipeEntry(this, recipe);
    }

    /** The ID, as vanilla's own recipe types print themselves. */
    @Override
    public String toString() {
        return id.toString();
    }
}
