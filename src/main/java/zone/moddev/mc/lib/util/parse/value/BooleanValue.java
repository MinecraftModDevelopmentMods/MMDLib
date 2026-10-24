package zone.moddev.mc.lib.util.parse.value;

import zone.moddev.mc.lib.util.parse.function.BooleanFunction;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

public interface BooleanValue
{
    void handle(BooleanConsumer value);

    boolean getAsBoolean();

    default <T> MappedValue<T> map(BooleanFunction<T> mapping)
    {
        return MappedValue.of(mapping.apply(getAsBoolean()));
    }
}
