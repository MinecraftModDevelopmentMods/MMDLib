package zone.moddev.mc.lib.machine.feature;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.api.entity.EntitySnapshotPolicy;

/**
 * One portable schema shared by processor declarations and detached item
 * access.
 */
public final class PortableVariable<T> {
    private final Supplier<? extends Item> item;
    private final String processor, name;
    private final Codec<T> codec;
    private final T initial;
    private final UnaryOperator<T> copy;
    private final EntitySnapshotPolicy<?> entityPolicy;

    public PortableVariable(Supplier<? extends Item> item, String processor,
        String name, Codec<T> codec, T initial, UnaryOperator<T> copy) {
        this(item, processor, name, codec, initial, copy, null);
    }

    private PortableVariable(Supplier<? extends Item> item, String processor,
        String name, Codec<T> codec, T initial, UnaryOperator<T> copy,
        EntitySnapshotPolicy<?> entityPolicy) {
        this.item = Objects.requireNonNull(item);
        this.processor = Objects.requireNonNull(processor);
        this.name = Objects.requireNonNull(name);
        if (processor.isBlank() || !name.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
            throw new IllegalArgumentException(
                "Invalid portable variable address");
        this.codec = Objects.requireNonNull(codec);
        this.copy = Objects.requireNonNull(copy);
        this.initial = copy.apply(Objects.requireNonNull(initial));
        this.entityPolicy = entityPolicy;
    }

    public static PortableVariable<EntitySnapshot> entity(
        Supplier<? extends Item> item, String processor, String name) {
        return new PortableVariable<>(item, processor, name,
            EntitySnapshot.CODEC, EntitySnapshot.EMPTY,
            UnaryOperator.identity());
    }

    public String processor() {
        return processor;
    }

    public static PortableVariable<EntitySnapshot> entity(
        Supplier<? extends Item> item, String processor, String name,
        EntitySnapshotPolicy<?> policy) {
        return new PortableVariable<>(item, processor, name,
            EntitySnapshot.CODEC, EntitySnapshot.EMPTY,
            UnaryOperator.identity(), Objects.requireNonNull(policy));
    }

    public Optional<EntitySnapshotPolicy<?>> entityPolicy() {
        return Optional.ofNullable(entityPolicy);
    }

    /**
     * Declare separately for each processor instance; sync and visual flags
     * remain configurable.
     */
    public ProcessorState.Declaration<T> declare(ProcessorState state) {
        return state.value(name, codec, copy.apply(initial), copy).portable()
            .reference(this);
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.is(item.get());
    }

    /**
     * Wrong items and missing or malformed values return a detached default.
     */
    public T read(ItemStack stack) {
        return copy.apply(matches(stack)
            ? PortableData.read(stack, processor, name, codec,
                copy.apply(initial))
            : initial);
    }

    public boolean hasContent(ItemStack stack) {
        return !Objects.equals(read(stack), initial);
    }

    /**
     * Preserve other variables/components; refuse to attach this payload to
     * another item.
     */
    public void write(ItemStack stack, T value) {
        if (!matches(stack))
            throw new IllegalArgumentException(
                "Wrong item for portable variable");
        PortableData.write(stack, processor, name, codec,
            copy.apply(Objects.requireNonNull(value)));
    }

    public ItemStack stack(T value) {
        var stack = new ItemStack(item.get());
        write(stack, value);
        return stack;
    }

    /**
     * Owner-scoped reader; immutable component identity avoids decoding
     * unchanged payloads.
     */
    public Reader reader() {
        return new Reader();
    }

    public final class Reader {
        private CustomData component;
        private T value = copy.apply(initial);

        private Reader() {
        }

        public T read(ItemStack stack) {
            var next = matches(stack)
                ? stack.getOrDefault(DataComponents.CUSTOM_DATA,
                    CustomData.EMPTY)
                : CustomData.EMPTY;
            if (component != next) {
                value = PortableVariable.this.read(stack);
                component = next;
            }
            return copy.apply(value);
        }

        public void clear() {
            component = null;
            value = copy.apply(initial);
        }
    }
}
