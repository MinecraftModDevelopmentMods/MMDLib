package com.mmd.lib.util.parse.function;

import com.mmd.lib.util.parse.value.MappedArrayValue;

import java.util.function.Function;

@FunctionalInterface
public interface MappedArrayValueFunction<V, T> extends Function<MappedArrayValue<V>, T>
{
}
