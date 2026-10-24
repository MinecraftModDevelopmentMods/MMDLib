package zone.moddev.mc.lib.util.parse.function;

@FunctionalInterface
public
interface BooleanFunction<T>
{
    T apply(boolean b);
}
