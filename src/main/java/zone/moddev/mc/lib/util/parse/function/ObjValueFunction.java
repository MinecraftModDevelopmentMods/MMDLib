package zone.moddev.mc.lib.util.parse.function;

import zone.moddev.mc.lib.util.parse.value.ObjValue;

import java.util.function.Function;

@FunctionalInterface
public interface ObjValueFunction<T> extends Function<ObjValue, T>
{
}
