package zone.moddev.mc.lib.util.parse.function;

@FunctionalInterface
public interface IntObjBiFunction<T, R>
{
    R apply(int index, T obj);
}
