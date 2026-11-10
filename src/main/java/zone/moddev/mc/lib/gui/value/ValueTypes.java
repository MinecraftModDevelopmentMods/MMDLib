package zone.moddev.mc.lib.gui.value;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.gui.sync.ElementStateValue;
import zone.moddev.mc.lib.gui.sync.ElementSync;

/** Built-in types use the same registration and bounded codecs as mod-owned values. */
public final class ValueTypes {
    private ValueTypes() {
    }

    public static final ValueType<BigInteger> WHOLE = ValueType.<BigInteger>builder(
        id("whole"),
        BigInteger.class,
        () -> StreamCodec.of(
            (b, v) -> b.writeByteArray(v.toByteArray()),
            b -> new BigInteger(b.readByteArray(ValueType.MAX_BYTES))),
        ValueType.MAX_BYTES)
        .javaTypes(
            byte.class,
            Byte.class,
            short.class,
            Short.class,
            int.class,
            Integer.class,
            long.class,
            Long.class)
        .normalize(ValueTypes::whole)
        .immutable()
        .description("a whole number")
        .build();

    public static final ValueType<Double> DECIMAL = ValueType.<Double>builder(
        id("decimal"),
        Double.class,
        () -> StreamCodec.of((b, v) -> b.writeDouble(v), RegistryFriendlyByteBuf::readDouble),
        8)
        .javaTypes(double.class, float.class, Float.class)
        .normalize(v -> ((Number) v).doubleValue())
        .validate(Double::isFinite)
        .immutable()
        .description("a decimal number")
        .build();

    public static final ValueType<Number> NUMBER = ValueType.<Number>builder(
        id("number"),
        Number.class,
        () -> StreamCodec.of(
            (b, v) -> {
                boolean decimal = v instanceof Double;
                b.writeBoolean(decimal);
                if (decimal)
                    b.writeDouble(v.doubleValue());
                else
                    b.writeByteArray(((BigInteger) v).toByteArray());
            },
            b -> b.readBoolean()
                ? b.readDouble()
                : new BigInteger(b.readByteArray(ValueType.MAX_BYTES))),
        ValueType.MAX_BYTES)
        .normalize(
            v -> v instanceof Float || v instanceof Double ? DECIMAL.normalize(v) : whole(v))
        .subtypes(false)
        .immutable()
        .description("a declared Number")
        .build();

    public static final ValueType<Boolean> BOOLEAN = ValueType.<Boolean>builder(
        id("boolean"),
        Boolean.class,
        () -> StreamCodec.of((b, v) -> b.writeBoolean(v), RegistryFriendlyByteBuf::readBoolean),
        1)
        .javaTypes(boolean.class)
        .immutable()
        .description("a boolean")
        .build();

    public static final ValueType<FluidStack> FLUID = ValueType.<FluidStack>builder(
        id("fluid"),
        FluidStack.class,
        () -> FluidStack.OPTIONAL_STREAM_CODEC,
        ValueType.MAX_BYTES)
        .copy((r, v) -> v.copy())
        .equality(FluidStack::matches)
        .description("a FluidStack")
        .build();

    public static final ValueType<Component> TEXT = ValueType.<Component>builder(
        id("text"),
        Component.class,
        () -> ComponentSerialization.STREAM_CODEC,
        ValueType.MAX_BYTES)
        .description("a Component")
        .build();

    public static final ValueType<List<Component>> TEXT_LIST = ValueType.<List<Component>>builder(
        id("text_list"),
        List.class,
        () -> StreamCodec.of(
            (b, values) -> {
                b.writeVarInt(values.size());
                for (var value : values)
                    ComponentSerialization.STREAM_CODEC.encode(b, value);
            },
            b -> {
                int count = b.readVarInt();
                if (count < 0 || count > 4096)
                    throw new IllegalArgumentException("GUI text list too large");
                var values = new ArrayList<Component>();
                for (int i = 0; i < count; i++)
                    values.add(ComponentSerialization.STREAM_CODEC.decode(b));
                return List.copyOf(values);
            }),
        ValueType.MAX_BYTES)
        .javaTypes(Collection.class)
        .normalize(v -> ((Collection<?>) v).stream().map(Component.class::cast).toList())
        .validate(v -> v.size() <= 4096)
        .description("a collection of Components")
        .build();

    public static final ValueType<ItemStack> ITEM = ValueType.<ItemStack>builder(
        id("item"),
        ItemStack.class,
        () -> ItemStack.OPTIONAL_STREAM_CODEC,
        ValueType.MAX_BYTES)
        .copy((r, v) -> v.copy())
        .equality(ItemStack::matches)
        .description("an ItemStack")
        .build();

    public static final ValueType<ElementStateValue> STATE = ValueType.<ElementStateValue>builder(
        id("state"),
        ElementStateValue.class,
        () -> StreamCodec.of(
            (b, v) -> b.writeByteArray(v.data()),
            b -> new ElementStateValue(b.readByteArray(ElementSync.MAX_BYTES))),
        ElementSync.MAX_BYTES + 5)
        .bindable(false)
        .immutable()
        .build();

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mmdlib", path);
    }

    static void register(ValueTypeRegistry registry) {
        for (var type : List.of(WHOLE, DECIMAL, NUMBER, BOOLEAN, FLUID, TEXT, TEXT_LIST, ITEM,
            STATE))
            registry.register(type);
    }

    /**
     * Whole-number normalization retains precision and rejects fractional or unknown Number types.
     */
    public static BigInteger whole(Object value) {
        return switch (value) {
            case BigInteger big -> big;
            case Long n -> BigInteger.valueOf(n);
            case Integer n -> BigInteger.valueOf(n);
            case Short n -> BigInteger.valueOf(n);
            case Byte n -> BigInteger.valueOf(n);
            default -> throw new IllegalArgumentException("Not a whole number: " + value);
        };
    }
}
