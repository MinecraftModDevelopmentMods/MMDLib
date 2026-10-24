package zone.moddev.mc.lib.util.parse.function;

@FunctionalInterface
public interface IntObjBiConsumer<T>
{
    void accept(int index, T obj);
}
