package com.mmd.lib.util.parse.value;

import com.mmd.lib.util.parse.function.FloatFunction;
import it.unimi.dsi.fastutil.floats.FloatConsumer;

public interface FloatValue
{
    void handle(FloatConsumer value);

    FloatValue min(float min);

    FloatValue range(float min, float maxExclusive);

    float getAsFloat();

    default <T> MappedValue<T> map(FloatFunction<T> mapping)
    {
        return MappedValue.of(mapping.apply(getAsFloat()));
    }
}
