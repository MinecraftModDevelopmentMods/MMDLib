package com.mcmoddev.lib.events;

import javax.annotation.Nullable;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.data.Names;

import net.minecraft.item.Item;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;

public class MMDLibRegisterItemTypes extends Event implements IContextSetter {
	private ItemsAPI typeRegistry; 
	
	protected static final class ItemsAPI implements IRegAPI<Item> {
		@Override
		public void addType(Class<? extends Item> clazz, Names name, boolean enabled) {
			MMDLib.logger.debug("addType({}, {}, {})", clazz.getCanonicalName(), name.toString(), enabled);
			addType(clazz, name.toString(), enabled);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, Class<?> sortingMatch) {
			MMDLib.logger.debug("addType({}, {}, {})", clazz.getCanonicalName(), name.toString(), sortingMatch.getCanonicalName());
			addType(clazz, name, true, null, sortingMatch);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, @Nullable String oredict) {
			MMDLib.logger.debug("addType({}, {}, {})", clazz.getCanonicalName(), name.toString(), oredict);
			addType(clazz, name, true, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, boolean enabled, @Nullable String oredict) {
			MMDLib.logger.debug("addType({}, {}, {}, {})", clazz.getCanonicalName(), name.toString(), enabled, oredict);
			addType(clazz, name.toString(), enabled, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, @Nullable String oredict, @Nullable Class<?> sortingMatch) {
			MMDLib.logger.debug("addType({}, {}, {}, {})", clazz.getCanonicalName(), name.toString(), oredict==null?"NULL":oredict, sortingMatch==null?"NO MATCH":sortingMatch);
			if(sortingMatch != null)
				addType(clazz, name, true, oredict, sortingMatch);
			else
				addType(clazz, name, true, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name, boolean enabled, @Nullable String oredict, Class<?> sortingMatch) {
			MMDLib.logger.debug("addType({}, {}, {}, {}, {})", clazz.getCanonicalName(), name.toString(), enabled, oredict, sortingMatch);
			ItemsWrapper.addType(clazz, name.toString(), true, oredict, sortingMatch);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name) {
			MMDLib.logger.debug("addType({}, {})", clazz.getCanonicalName(), name);
			addType(clazz, name, true);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, Class<?> sortingMatch) {
			MMDLib.logger.debug("addType({}, {}, {})", clazz.getCanonicalName(), name);
			ItemsWrapper.addType(clazz, name, true, null, sortingMatch);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, @Nullable String oredict) {
			MMDLib.logger.debug("addType({}, {}, {})", clazz.getCanonicalName(), name, oredict);
			addType(clazz, name, true, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, @Nullable String oredict, Class<?> sortingMatch) {
			MMDLib.logger.debug("addType({}, {}, {}, {})", clazz.getCanonicalName(), name, oredict==null?"NULL":oredict, sortingMatch==null?"NO MATCH":sortingMatch);
			ItemsWrapper.addType(clazz, name, true, oredict, sortingMatch);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, boolean enabled, @Nullable String oredict) {
			MMDLib.logger.debug("addType({}, {}, {}, {})", clazz.getCanonicalName(), name, enabled, oredict);
			ItemsWrapper.addType(clazz, name, enabled, oredict);
		}

		@Override
		public void addType(Class<? extends Item> clazz, String name, boolean enabled) {
			MMDLib.logger.debug("addType({}, {}, {})", clazz.getCanonicalName(), name, enabled);
			ItemsWrapper.addType(clazz, name, enabled);
		}

		@Override
		public void addType(Class<? extends Item> clazz, Names name) {
			MMDLib.logger.debug("addType({}, {})", clazz.getCanonicalName(), name.toString());
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
			
			public static void addType(Class<? extends Item> clazz, String name, boolean enabled) {
				addItemType(name, clazz, enabled);
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
