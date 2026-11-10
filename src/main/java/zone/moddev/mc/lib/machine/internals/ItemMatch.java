package zone.moddev.mc.lib.machine.internals;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which items fit somewhere, written as one string: {@code #ns:path} for an
 * item tag, otherwise an item ID. Decoding needs no registry access, so it can
 * run at startup.
 */
public record ItemMatch(Either<TagKey<Item>, ResourceLocation> value) {
    /** The string form; an empty or malformed ID is a decode error. */
    public static final Codec<ItemMatch> CODEC = Codec.STRING
        .comapFlatMap(ItemMatch::read, ItemMatch::toString);

    /** Whether {@code stack} is in the tag, or is the item. */
    public boolean test(ItemStack stack) {
        return value.map(stack::is, id -> !stack.isEmpty()
            && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id));
    }

    @Override
    public String toString() {
        return value.map(tag -> "#" + tag.location(),
            ResourceLocation::toString);
    }

    private static DataResult<ItemMatch> read(String text) {
        boolean tag = text.startsWith("#");
        var id = tag ? text.substring(1) : text;
        if (id.isEmpty())
            return DataResult
                .error(() -> "Empty item or tag ID: '" + text + "'");
        return ResourceLocation.read(id)
            .map(location -> new ItemMatch(tag
                ? Either.left(TagKey.create(Registries.ITEM, location))
                : Either.right(location)));
    }
}
