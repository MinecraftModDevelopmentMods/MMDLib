package com.mcmoddev.lib.events;

import javax.annotation.Nullable;

import com.mcmoddev.lib.data.Names;

import net.minecraft.item.Item;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;

public class MMDLibRegisterItemTypes extends Event implements IContextSetter {
	private static ItemsAPI typeRegistry; 
	
	protected static final class ItemsAPI implements IRegAPI<Item> {
		@Override
		public void addType(Class<? extends Item> clazz, Names name, boolean enabled) {
			addType(clazz, name, enabled, null, null);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, Class<?> sortingMatch) {
			addType(clazz, name, true, null, sortingMatch);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, @Nullable String oredict) {
			addType(clazz, name, true, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, boolean enabled, @Nullable String oredict) {
			addType(clazz, name.toString(), enabled, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, @Nullable String oredict, @Nullable Class<?> sortingMatch) {
			if(sortingMatch != null)
				addType(clazz, name, true, oredict, sortingMatch);
			else
				addType(clazz, name, true, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, boolean enabled, @Nullable String oredict, Class<?> sortingMatch) {
			ItemsWrapper.addType(clazz, name.toString(), true, oredict, sortingMatch);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name) {
			addType(clazz, name, true);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, Class<?> sortingMatch) {
			ItemsWrapper.addType(clazz, name, true, null, sortingMatch);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, @Nullable String oredict) {
			addType(clazz, name, true, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, @Nullable String oredict, Class<?> sortingMatch) {
			ItemsWrapper.addType(clazz, name, true, oredict, sortingMatch);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, boolean enabled, @Nullable String oredict) {
			ItemsWrapper.addType(clazz, name, enabled, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, boolean enabled) {
			addType(clazz, name, enabled, null);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name) {
			addType(clazz, name, true);
		}

		@Override
		public void addSorting(Class<?> clazz) {
			ItemsWrapper.addSorting(clazz);
		}

		@Override
		public void addSorting(Class<?> clazz, Class<?> otherClazz) {
			ItemsWrapper.addSorting(clazz, otherClazz);
		}
		
		private static final class ItemsWrapper extends com.mcmoddev.lib.init.Items {
			public static void addType(Class<? extends Item> clazz, String name, boolean enabled, @Nullable String oredict) {
				addItemType(name, clazz, enabled, oredict);
				addClassSorting(clazz);
			}

			public static void addType(Class<? extends Item> clazz, String name, boolean enabled, @Nullable String oredict, Class<?> sortingMatch) {
				addItemType(name, clazz, enabled, oredict);
				addClassSorting(clazz, sortingMatch);
			}

			public static void addSorting(Class<?> clazz) {
				addClassSorting(clazz);
			}

			public static void addSorting(Class<?> clazz, Class<?> otherClazz) {
				addClassSorting(clazz, otherClazz);
			}
		}
	}

	public MMDLibRegisterItemTypes() {
		if(typeRegistry == null) typeRegistry  = new ItemsAPI();
	}

	public IRegAPI<Item> getApi() {
		return typeRegistry;
	}
}
