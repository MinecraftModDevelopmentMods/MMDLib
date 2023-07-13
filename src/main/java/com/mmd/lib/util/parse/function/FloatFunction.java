package com.mmd.lib.util.parse.function;

@FunctionalInterface
public interface FloatFunction<T>
{
    T apply(float value);
}
