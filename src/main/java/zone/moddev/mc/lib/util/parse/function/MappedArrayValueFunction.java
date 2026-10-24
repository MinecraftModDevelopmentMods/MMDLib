package zone.moddev.mc.lib.util.parse.function;

import zone.moddev.mc.lib.util.parse.value.MappedArrayValue;

import java.util.function.Function;

@FunctionalInterface
public interface MappedArrayValueFunction<V, T> extends Function<MappedArrayValue<V>, T>
{
}
