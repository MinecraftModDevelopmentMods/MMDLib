package com.mmd.lib.util.parse.function;

@FunctionalInterface
public
interface BooleanFunction<T>
{
    T apply(boolean b);
}
