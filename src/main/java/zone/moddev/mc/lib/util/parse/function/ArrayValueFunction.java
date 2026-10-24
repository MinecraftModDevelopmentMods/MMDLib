package zone.moddev.mc.lib.util.parse.function;

import zone.moddev.mc.lib.util.parse.value.ArrayValue;

import java.util.function.Function;

@FunctionalInterface
public interface ArrayValueFunction<T> extends Function<ArrayValue, T>
{
}

