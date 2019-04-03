package com.mcmoddev.lib.events;

import javax.annotation.Nullable;

import com.mcmoddev.lib.data.Names;

import net.minecraft.block.Block;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;

public class MMDLibRegisterBlockTypes extends Event implements IContextSetter {

	private BlocksAPI api; 

	public MMDLibRegisterBlockTypes() {
		this.api = new BlocksAPI();
	}

	public IRegAPI<Block> getApi() {
		return this.api;
	}
	
	private static final class BlocksAPI implements IRegAPI<Block>{
		public BlocksAPI() {
		}
		
		private static final class BlocksWrapper extends com.mcmoddev.lib.init.Blocks {
			private static void addType(Class<? extends Block> clazz, String name, boolean enabled, @Nullable String oredict) {
				addBlockType(name, clazz, enabled, oredict);
			}
		}
		
		@Override
		public void addType(Class<? extends Block> clazz, Names name) {
			addType(clazz, name, true);
		}
		
		@Override
		public void addType(Class<? extends Block> clazz, Names name, boolean enabled) {
			addType(clazz, name.toString(), enabled);
		}
		
		@Override
		public void addType(Class<? extends Block> clazz, Names name, @Nullable String oredict) {
			addType(clazz, name, true, oredict);
		}

		@Override
		public void addType(Class<? extends Block> clazz, Names name, boolean enabled, @Nullable String oredict) {
			addType(clazz, name.toString(), enabled, oredict);
		}
		
		@Override
		public void addType(Class<? extends Block> clazz, String name) {
			addType(clazz, name, true);
		}
		
		@Override
		public void addType(Class<? extends Block> clazz, String name, @Nullable String oredict) {
			addType(clazz, name, true, oredict);
		}
		
		@Override
		public void addType(Class<? extends Block> clazz, String name, boolean enabled) {
			addType(clazz, name, enabled, null);
		}
		
		@Override
		public void addType(Class<? extends Block> clazz, String name, boolean enabled, @Nullable String oredict) {
			BlocksWrapper.addType(clazz, name, enabled, oredict);
		}
		
	}
}
