package zone.moddev.mc.lib.util.parse.function;

import zone.moddev.mc.lib.util.parse.value.Any;

import java.util.function.Function;

@FunctionalInterface
public interface AnyFunction<T> extends Function<Any, T>
{
}
