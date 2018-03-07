package com.mcmoddev.lib.init;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.block.BlockMMDAnvil;
import com.mcmoddev.lib.block.BlockMMDBars;
import com.mcmoddev.lib.block.BlockMMDBlock;
import com.mcmoddev.lib.block.BlockMMDBookshelf;
import com.mcmoddev.lib.block.BlockMMDButton;
import com.mcmoddev.lib.block.BlockMMDDoor;
import com.mcmoddev.lib.block.BlockMMDFence;
import com.mcmoddev.lib.block.BlockMMDFenceGate;
import com.mcmoddev.lib.block.BlockMMDFlowerPot;
import com.mcmoddev.lib.block.BlockMMDLadder;
import com.mcmoddev.lib.block.BlockMMDLever;
import com.mcmoddev.lib.block.BlockMMDNetherOre;
import com.mcmoddev.lib.block.BlockMMDOre;
import com.mcmoddev.lib.block.BlockMMDPlate;
import com.mcmoddev.lib.block.BlockMMDPressurePlate;
import com.mcmoddev.lib.block.BlockMMDSlab;
import com.mcmoddev.lib.block.BlockMMDStairs;
import com.mcmoddev.lib.block.BlockMMDTrapDoor;
import com.mcmoddev.lib.block.BlockMMDTripWireHook;
import com.mcmoddev.lib.block.BlockMMDWall;
import com.mcmoddev.lib.data.ConfigKeys;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.data.SharedStrings;
import com.mcmoddev.lib.item.ItemMMDBlock;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.util.ConfigBase.Options;
import com.mcmoddev.lib.util.Oredicts;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockSlab;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;

/**
 * This class initializes all blocks in Base Metals and provides some utility
 * methods for looking up blocks.
 *
 * @author Jasmine Iwanek
 *
 */
public abstract class Blocks {

	private static final BiMap<String, Block> blockRegistry = HashBiMap.create(16);
	private static final Map<MMDMaterial, List<Block>> blocksByMaterial = new HashMap<>();

	private static final EnumMap<Names, Class<? extends Block>> nameToClass = new EnumMap<>(Names.class);
	private static final EnumMap<Names, String> nameToOredict = new EnumMap<>(Names.class);
	private static final EnumMap<Names, Boolean> nameToEnabled = new EnumMap<>(Names.class);

	protected Blocks() {
		throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);
	}

	/**
	 *
	 */
	public static void init() {
		addBlockType(Names.ANVIL, BlockMMDAnvil.class, Options.isThingEnabled(ConfigKeys.ANVIL), null);
		addBlockType(Names.BARS, BlockMMDBars.class, Options.isThingEnabled(ConfigKeys.BARS), Oredicts.BARS);
		addBlockType(Names.BLOCK, BlockMMDBlock.class, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.BLOCK);
		addBlockType(Names.BOOKSHELF, BlockMMDBookshelf.class, Options.isThingEnabled(ConfigKeys.BOOKSHELF), null);
		addBlockType(Names.BUTTON, BlockMMDButton.class, Options.isThingEnabled(ConfigKeys.BUTTON), Oredicts.BUTTON);
		addBlockType(Names.DOOR, BlockMMDDoor.class, Options.isThingEnabled(ConfigKeys.DOOR), null);
		addBlockType(Names.DOUBLE_SLAB, BlockMMDSlab.Double.class, Options.isThingEnabled(ConfigKeys.SLAB), null);
		addBlockType(Names.FLOWER_POT, BlockMMDFlowerPot.class, Options.isThingEnabled(ConfigKeys.FLOWERPOT), null);
		addBlockType(Names.LADDER, BlockMMDLadder.class, Options.isThingEnabled(ConfigKeys.LADDER), null);
		addBlockType(Names.LEVER, BlockMMDLever.class, Options.isThingEnabled(ConfigKeys.LEVER), Oredicts.LEVER);
		addBlockType(Names.PLATE, BlockMMDPlate.class, Options.isThingEnabled(ConfigKeys.PLATE), Oredicts.PLATE);
		addBlockType(Names.PRESSURE_PLATE, BlockMMDPressurePlate.class, Options.isThingEnabled(ConfigKeys.PRESSURE_PLATE), null);
		addBlockType(Names.SLAB, BlockMMDSlab.Half.class, Options.isThingEnabled(ConfigKeys.SLAB), null);
		addBlockType(Names.STAIRS, BlockMMDStairs.class, Options.isThingEnabled(ConfigKeys.STAIRS), Oredicts.STAIRS);
		addBlockType(Names.TRAPDOOR, BlockMMDTrapDoor.class, Options.isThingEnabled(ConfigKeys.TRAPDOOR), Oredicts.TRAPDOOR);
		addBlockType(Names.TRIPWIRE_HOOK, BlockMMDTripWireHook.class, Options.isThingEnabled(ConfigKeys.TRIPWIRE_HOOK), null);
		addBlockType(Names.WALL, BlockMMDWall.class, Options.isThingEnabled(ConfigKeys.WALL), Oredicts.WALL);
		addBlockType(Names.FENCE, BlockMMDFence.class, Options.isThingEnabled(ConfigKeys.WALL), null);
		addBlockType(Names.FENCE_GATE, BlockMMDFenceGate.class, Options.isThingEnabled(ConfigKeys.WALL), null);
		addBlockType(Names.ENDORE, BlockMMDOre.class, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.ORE_END);
		addBlockType(Names.NETHERORE, BlockMMDNetherOre.class, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.ORE_NETHER);
		addBlockType(Names.ORE, BlockMMDOre.class, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.ORE);
	}

	@Nullable
	protected static Block create(@Nonnull final Names name, @Nonnull final String materialName) {
		return create(name, Materials.getMaterialByName(materialName), ItemGroups.getTab(SharedStrings.TAB_BLOCKS));
	}

	@Nullable
	protected static Block create(@Nonnull final Names name, @Nonnull final MMDMaterial material) {
		return create(name, material, ItemGroups.getTab(SharedStrings.TAB_BLOCKS));
	}

	@Nullable
	protected static Block create(@Nonnull final Names name, @Nonnull final String materialName, final CreativeTabs tab) {
		return create(name, Materials.getMaterialByName(materialName), false, tab);
	}

	/**
	 *
	 * @param name
	 *            Name of the requested block type
	 * @param material
	 *            The material this is made from
	 * @param tab
	 *            which creative tab is it on
	 * @return the block this function created
	 */
	@Nullable
	protected static Block create(@Nonnull final Names name, @Nonnull final MMDMaterial material, final CreativeTabs tab) {
		return create(name, material, false, tab);
	}

	@Nullable
	protected static Block create(@Nonnull final Names name, @Nonnull final String materialName, @Nonnull final boolean glow, final CreativeTabs tab) {
		return create(name, Materials.getMaterialByName(materialName), glow, tab);
	}

	/**
	 *
	 * @param name
	 *            Name of the requested block type
	 * @param material
	 *            The material this is made from
	 * @param glows
	 *            Does it have a glow ?
	 * @param tab
	 *            which creative tab is it on
	 * @return the block this function created
	 */
	@Nullable
	protected static Block create(@Nonnull final Names name, @Nonnull final MMDMaterial material, @Nonnull final boolean glows, final CreativeTabs tab) {
		if (material.hasBlock(name)) {
			return material.getBlock(name);
		}

		// Used to decide when NOT to create, Currently won't create when:
		// 1) Blocks which need Names.BLOCK if it's not found
		// 2) Ores when the material isn't supposed to have an ore
		if (((doesThisNeedBlock(name)) && (!material.hasBlock(Names.BLOCK)))  || ((doesThisNeedOre(name)) && (!material.hasOre()))) {
			return null;
		}

		final Block block = createBlock(material, name.toString(), getClassFromName(name), isNameEnabled(name), tab);

		final String oredict = getOredictFromName(name);
		if ((oredict != null) && (block != null)) {
			Oredicts.registerOre(oredict + material.getCapitalizedName(), block);
		}

		if ((block != null) && glows) {
			block.setLightLevel(0.5f);
		}

		return block;
	}

	private static boolean doesThisNeedBlock(final Names name) {
		return ((name.equals(Names.FENCE)) || (name.equals(Names.FENCE_GATE)) || (name.equals(Names.FLOWER_POT))
				|| (name.equals(Names.LADDER)) || (name.equals(Names.STAIRS)) || (name.equals(Names.TRIPWIRE_HOOK))
				|| (name.equals(Names.WALL)));
	}

	private static boolean doesThisNeedOre(final Names name) {
		return ((name.equals(Names.ORE)) || (name.equals(Names.ENDORE)) || (name.equals(Names.NETHERORE)));
	}

	@Nullable
	protected static Block addBlock(@Nonnull final Block block, @Nonnull final Names name, final CreativeTabs tab) {
		return addBlock(block, name.toString(), Materials.DEFAULT, tab);
	}

	@Nullable
	protected static Block addBlock(@Nonnull final Block block, @Nonnull final String name, final CreativeTabs tab) {
		return addBlock(block, name, Materials.DEFAULT, tab);
	}

	/**
	 *
	 * @param block
	 *            the block of interest
	 * @param name
	 *            name of the thing
	 * @param material
	 *            the material the thing is made from
	 * @param tab
	 *            which creative tab is it on
	 * @return a new block
	 */
	@Nullable
	protected static Block addBlock(@Nonnull final Block block, @Nonnull final Names name, @Nonnull final MMDMaterial material, final CreativeTabs tab) {
		return addBlock(block, name.toString(), material, tab);
	}

	/**
	 *
	 * @param block
	 *            the block of interest
	 * @param name
	 *            name of the thing
	 * @param material
	 *            the material the thing is made from
	 * @param tab
	 *            which creative tab is it on
	 * @return a new block
	 */
	@Nullable
	protected static Block addBlock(@Nonnull final Block block, @Nonnull final String name, @Nonnull final MMDMaterial material, final CreativeTabs tab) {
		final String fullName = getBlockFullName(block, material, name);

		block.setRegistryName(fullName);
		block.setUnlocalizedName(block.getRegistryName().getResourceDomain() + "." + fullName);

		maybeMakeItemBlock(block, material, fullName);

		if (tab != null) {
			block.setCreativeTab(tab);
		}

		if (!material.isEmpty()) {
			blocksByMaterial.computeIfAbsent(material, (MMDMaterial g) -> new ArrayList<>());
			blocksByMaterial.get(material).add(block);
		}

		blockRegistry.put(fullName, block);
		if (material.isDefault()) {
			material.addNewBlock(fullName, block);
		}
		
		return block;
	}

	private static void maybeMakeItemBlock(final Block block, final MMDMaterial material, final String fullName) {
		if (!(block instanceof BlockAnvil) && !(block instanceof BlockDoor) && !(block instanceof BlockSlab) && (!material.isEmpty())) {
			final ItemBlock itemBlock = new ItemMMDBlock(material, block);
			itemBlock.setRegistryName(block.getRegistryName());
			itemBlock.setUnlocalizedName(block.getRegistryName().getResourceDomain() + "." + fullName);
			material.addNewItem("ItemBlock_" + fullName, itemBlock);
		}
	}

	private static String getBlockFullName(@Nonnull final Block block, final MMDMaterial material, @Nonnull final String name) {
		if (block instanceof BlockMMDSlab.Double) {
			return String.format("double_%s_%s", material.getName(), Names.SLAB);
		} else if ((name.startsWith("nether")) || (name.startsWith("end"))) {
			String neededBit = name.substring(0, name.length() - 3);
			return String.format("%s_%s_%s", neededBit, material.getName(), Names.ORE);
		} else if ((!material.isEmpty()) && (!material.isDefault())) {
			return String.format("%s_%s", material.getName(), name);
		} else {
			return name;
		}
	}

	@Nullable
	private static Block createBlock(@Nonnull final MMDMaterial material, @Nonnull final String name, @Nonnull final Class<? extends Block> clazz, @Nonnull final boolean enabled, final CreativeTabs tab) {
		if (enabled) {
			Constructor<?> ctor = null;
			Block inst = null;
			try {
				ctor = clazz.getConstructor(material.getClass());
			} catch (Exception ex) {
				MMDLib.logger.fatal("Class for Block named %s does not have an accessible constructor or another exception occurred", name, ex);
				return null;
			}

			try {
				inst = (Block) ctor.newInstance(material);
			} catch (Exception ex) {
				MMDLib.logger.fatal("Unable to create Block named %s for material %s", name, material.getCapitalizedName(), ex);
			}

			if (inst != null) {
				material.addNewBlock(name, addBlock(inst, name, material, tab));
				return inst;
			}
		}

		return null;
	}

	protected static Block createBookshelf(@Nonnull final MMDMaterial material, @Nonnull final boolean fullBlock, final CreativeTabs tab) {

		BlockMMDBookshelf bs = (BlockMMDBookshelf) create(Names.BOOKSHELF, material, tab);
		if (bs != null) {
			bs.setFullBlock(fullBlock);
		}
		return bs;
	}

	protected static Class<? extends Block> getClassFromName(@Nonnull final Names name) {
		if (nameToClass.containsKey(name)) {
			return nameToClass.get(name);
		}
		return net.minecraft.block.Block.class;
	}

	@Nullable
	protected static String getOredictFromName(@Nonnull final Names name) {
		if (nameToOredict.containsKey(name)) {
			return nameToOredict.get(name);
		}
		return null;
	}

	protected static boolean isNameEnabled(@Nonnull final Names name) {
		if (nameToEnabled.containsKey(name)) {
			return nameToEnabled.get(name);
		}
		return false;
	}

	protected static void addBlockType(@Nonnull final Names name, @Nonnull final Class<? extends Block> clazz, @Nonnull final Boolean enabled) {
		addBlockType(name, clazz, enabled, null);
	}

	protected static void addBlockType(@Nonnull final Names name, @Nonnull final Class<? extends Block> clazz, @Nonnull final Boolean enabled, @Nullable final String oredict) {
		if (!nameToClass.containsKey(name)) {
			nameToClass.put(name, clazz);
		}

		if (!nameToEnabled.containsKey(name)) {
			nameToEnabled.put(name, enabled);
		}

		if ((oredict != null) && (!"".equals(oredict)) && (!nameToOredict.containsKey(name))) {
			nameToOredict.put(name, oredict);
		}
	}

	/**
	 * Gets an block by its name. The name is the name as it is registered in
	 * the GameRegistry, not its unlocalized name (the unlocalized name is the
	 * registered name plus the prefix "basemetals.")
	 *
	 * @param name
	 *            The name of the block in question
	 * @return The block matching that name, or null if there isn't one
	 */
	@Nullable
	public static Block getBlockByName(@Nonnull final String name) {
		return blockRegistry.get(name);
	}

	/**
	 * This is the reverse of the getBlockByName(...) method, returning the
	 * registered name of an block instance (Base Metals blocks only).
	 *
	 * @param block
	 *            The block in question
	 * @return The name of the block, or null if the item is not a Base Metals
	 *         block.
	 */
	@Nullable
	public static String getNameOfBlock(@Nonnull final Block block) {
		return blockRegistry.inverse().get(block);
	}

	public static Map<String, Block> getBlockRegistry() {
		return Collections.unmodifiableMap(blockRegistry);
	}

	/**
	 * Gets a map of all blocks added, sorted by material.
	 *
	 * @return An unmodifiable map of added items categorized by material
	 */
	public static Map<MMDMaterial, List<Block>> getBlocksByMaterial() {
		return Collections.unmodifiableMap(blocksByMaterial);
	}
}
