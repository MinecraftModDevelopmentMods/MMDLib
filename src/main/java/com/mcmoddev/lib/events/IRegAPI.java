package com.mcmoddev.lib.events;

import javax.annotation.Nullable;

import com.mcmoddev.lib.data.Names;

public interface IRegAPI<T>{
	void addType(Class<? extends T> clazz, Names name);
	void addType(Class<? extends T> clazz, Names name, boolean enabled);
	void addType(Class<? extends T> clazz, Names name, @Nullable String oredict);
	void addType(Class<? extends T> clazz, Names name, boolean enabled, @Nullable String oredict);
	void addType(Class<? extends T> clazz, String name);
	void addType(Class<? extends T> clazz, String name, boolean enabled);
	void addType(Class<? extends T> clazz, String name, @Nullable String oredict);
	void addType(Class<? extends T> clazz, String name, boolean enabled, @Nullable String oredict);
	default void addSorting(Class<?> clazz) {};
	default void addSorting(Class<?> clazz, Class<?> otherClazz) {};
	default void addType(Class<? extends T> clazz, String name, @Nullable String oredict, Class<?> sortingMatch) {};
	default void addType(Class<? extends T> clazz, String name, Class<?> sortingMatch) {};
	default void addType(Class<? extends T> clazz, Names name, boolean enabled, Class<?> sortingMatch) {};
	default void addType(Class<? extends T> clazz, Names name, Class<?> sortingMatch) {};
	default void addType(Class<? extends T> clazz, Names name, boolean enabled, @Nullable String oredict, Class<?> sortingMatch) {};
	default void addType(Class<? extends T> clazz, Names name, @Nullable String oredict, Class<?> sortingMatch) {};
}
