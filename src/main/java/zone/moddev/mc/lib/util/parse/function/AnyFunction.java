package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Function;

import zone.moddev.mc.lib.util.parse.value.Any;

/** Maps any JSON value. */
@FunctionalInterface
public interface AnyFunction<T> extends Function<Any, T> {}
