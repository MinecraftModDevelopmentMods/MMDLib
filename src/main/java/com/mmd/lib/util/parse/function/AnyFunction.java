package com.mmd.lib.util.parse.function;

import com.mmd.lib.util.parse.value.Any;

import java.util.function.Function;

@FunctionalInterface
public interface AnyFunction<T> extends Function<Any, T>
{
}
