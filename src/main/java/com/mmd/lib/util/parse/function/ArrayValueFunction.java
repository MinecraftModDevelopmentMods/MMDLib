package com.mmd.lib.util.parse.function;

import com.mmd.lib.util.parse.value.ArrayValue;

import java.util.function.Function;

@FunctionalInterface
public interface ArrayValueFunction<T> extends Function<ArrayValue, T>
{
}

