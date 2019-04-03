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
	/**
	 * Can be implemented where needed, sole user right now is the Item Type registration code
	 * @param clazz Class getting sorted
	 */
	default void addSorting(Class<?> clazz) {
		// empty to make it so people don't have to implement it if it isn't in use
	};
	/**
	 * Can be implemented where needed, sole user right now is the Item Type registration code
	 * @param clazz Class getting sorted
	 * @param otherClazz Class to sort to the same value as
	 */
	default void addSorting(Class<?> clazz, Class<?> otherClazz) {
		// empty to make it so people don't have to implement it if it isn't in use
	};
	/**
	 * Some systems might want to have class sorting values added automatically, this does it
	 * @param clazz Class to instantiate on request
	 * @param name Name of the item/block
	 * @param oredict Possible ore-dictionary prefix
	 * @param sortingMatch Class to set the sorting value equal to
	 */
	default void addType(Class<? extends T> clazz, String name, @Nullable String oredict, Class<?> sortingMatch) {
		// empty to make it so people don't have to implement it if it isn't in use
	};
	/**
	 * Some systems might want to have class sorting values added automatically, this does it
	 * @param clazz Class to instantiate on request
	 * @param name Name of the item/block
	 * @param sortingMatch Class to set the sorting value equal to
	 */
	default void addType(Class<? extends T> clazz, String name, Class<?> sortingMatch) {
		// empty to make it so people don't have to implement it if it isn't in use
	};
	/**
	 * Some systems might want to have class sorting values added automatically, this does it
	 * @param clazz Class to instantiate on request
	 * @param name Name of the item/block
	 * @param enabled Is this type actually enabled in config ?
	 * @param sortingMatch Class to set the sorting value equal to
	 */
	default void addType(Class<? extends T> clazz, Names name, boolean enabled, Class<?> sortingMatch) {
		// empty to make it so people don't have to implement it if it isn't in use
	};
	/**
	 * Some systems might want to have class sorting values added automatically, this does it
	 * @param clazz Class to instantiate on request
	 * @param name Name of the item/block
	 * @param sortingMatch Class to set the sorting value equal to
	 */
	default void addType(Class<? extends T> clazz, Names name, Class<?> sortingMatch) {
		// empty to make it so people don't have to implement it if it isn't in use
	};
	/**
	 * Some systems might want to have class sorting values added automatically, this does it
	 * @param clazz Class to instantiate on request
	 * @param name Name of the item/block
	 * @param enabled Is this type actually enabled in config ?
	 * @param oredict Possible ore-dictionary prefix
	 * @param sortingMatch Class to set the sorting value equal to
	 */
	default void addType(Class<? extends T> clazz, Names name, boolean enabled, @Nullable String oredict, Class<?> sortingMatch) {
		// empty to make it so people don't have to implement it if it isn't in use
	};
	/**
	 * Some systems might want to have class sorting values added automatically, this does it
	 * @param clazz Class to instantiate on request
	 * @param name Name of the item/block
	 * @param oredict Possible ore-dictionary prefix
	 * @param sortingMatch Class to set the sorting value equal to
	 */
	default void addType(Class<? extends T> clazz, Names name, @Nullable String oredict, Class<?> sortingMatch) {
		// empty to make it so people don't have to implement it if it isn't in use
	};
}
