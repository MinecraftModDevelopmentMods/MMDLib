package com.mmd.lib.util.parse.function;

@FunctionalInterface
public interface IntObjBiFunction<T, R>
{
    R apply(int index, T obj);
}
