package com.mmd.lib.util.parse.function;

import com.mmd.lib.util.parse.value.ObjValue;

import java.util.function.Function;

@FunctionalInterface
public interface ObjValueFunction<T> extends Function<ObjValue, T>
{
}
