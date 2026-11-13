package zone.moddev.mc.lib.machine.feature;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Namespaced processor payloads; unrelated item components and processors
 * survive writes.
 */
public final class PortableData {
    private static final String ROOT = "mmdlib:processors";

    private PortableData() {
    }

    public static CompoundTag read(ItemStack stack, String processor) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag().getCompound(ROOT).getCompound(processor).copy();
    }

    public static <T> T read(ItemStack stack, String processor, String variable,
        Codec<T> codec, T fallback) {
        var tag = read(stack, processor);
        return tag.contains(variable)
            ? codec.parse(NbtOps.INSTANCE, tag.get(variable)).result()
                .orElse(fallback)
            : fallback;
    }

    public static void write(ItemStack stack, String processor,
        CompoundTag state) {
        var tag = stack
            .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag();
        var processors = tag.getCompound(ROOT);
        processors.put(processor, state.copy());
        tag.put(ROOT, processors);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static <T> void write(ItemStack stack, String processor,
        String variable, Codec<T> codec, T value) {
        var tag = read(stack, processor);
        tag.put(variable,
            codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow());
        write(stack, processor, tag);
    }
}
