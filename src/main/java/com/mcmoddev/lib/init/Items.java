package com.mcmoddev.lib.init;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.data.SharedStrings;
import com.mcmoddev.lib.integration.plugins.Mekanism;
import com.mcmoddev.lib.item.GenericMMDItem;
import com.mcmoddev.lib.item.ItemMMDArmor;
import com.mcmoddev.lib.material.IMMDObject;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.material.MMDMaterialType.MaterialType;
import com.mcmoddev.lib.util.Config.Options;
import com.mcmoddev.lib.util.Oredicts;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

/**
 * This class initializes all items in Base Metals and provides some utility methods for looking up
 * items.
 *
 * @author Jasmine Iwanek
 *
 */
public abstract class Items {

	private static final BiMap<String, Item> itemRegistry = HashBiMap.create(34);
	private static final Map<MMDMaterial, List<Item>> itemsByMaterial = new HashMap<>();

	private static final Map<String, Class<? extends Item>> nameToClass = new HashMap<>();
	private static final Map<String, String> nameToOredict = new HashMap<>();
	private static final Map<String, Boolean> nameToEnabled = new HashMap<>();

	private static final Map<Class<?>, Integer> classSortingValues = new HashMap<>();
	private static final Map<MMDMaterial, Integer> materialSortingValues = new HashMap<>();

	// public static UniversalBucket universal_bucket; // now automatically added by
	// Forge
	protected static final int BLOCK_BURN_TIME = 16000;
	protected static final int INGOT_BURN_TIME = 1600;
	protected static final int NUGGET_BURN_TIME = 200;

	private static int ss = 0;

	protected Items() {
		throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);
	}

	protected static int getMaxSortingValue() {
		return ss;
	}
	
	protected static void addClassSorting(Class<?> clazz) {
		classSortingValues.put(clazz, ++ss * 10000);		
	}
	
	protected static void addClassSorting(Class<?> clazz, Class<?> otherClazz) {
		classSortingValues.put(clazz, classSortingValues.get(otherClazz));
	}

	/**
	 *
	 */
	public static void init() {
		try {
			expandCombatArrays(net.minecraft.item.ItemAxe.class);
		} catch (IllegalAccessException | NoSuchFieldException ex) {
			MMDLib.logger.error("Error modifying item classes", ex);
		}
	}

	public static void addToMetList() {
		final List<MMDMaterial> metlist = new ArrayList<>(Materials.getAllMaterials().size());
		metlist.addAll(Materials.getAllMaterials());
		metlist.sort((final MMDMaterial a, final MMDMaterial b) -> a.getName()
				.compareToIgnoreCase(b.getName()));
		for (int i = 0; i < metlist.size(); i++) {
			materialSortingValues.put(metlist.get(i), i * 100);
		}
	}

	@Nullable
	protected static Item create(@Nonnull final Names name, @Nonnull final String materialName) {
		return create(name, Materials.getMaterialByName(materialName));
	}

	@Nullable
	protected static Item create(@Nonnull final Names name, @Nonnull final MMDMaterial material) {
		CreativeTabs tab;

		if ((name.equals(Names.DOOR)) || (name.equals(Names.SLAB))) {
			tab = ItemGroups.getTab(SharedStrings.TAB_BLOCKS);
		} else if ((name.equals(Names.SWORD)) || (name.equals(Names.BOW))
				|| (name.equals(Names.CROSSBOW)) || (name.equals(Names.BOLT))
				|| (name.equals(Names.ARROW)) || (name.equals(Names.SHIELD))
				|| (name.equals(Names.HELMET)) || (name.equals(Names.CHESTPLATE))
				|| (name.equals(Names.LEGGINGS)) || (name.equals(Names.BOOTS))
				|| (name.equals(Names.HORSE_ARMOR))) {
			tab = ItemGroups.getTab(SharedStrings.TAB_COMBAT);
		} else if ((name.equals(Names.SHEARS)) || (name.equals(Names.FISHING_ROD))
				|| (name.equals(Names.HOE)) || (name.equals(Names.AXE))
				|| (name.equals(Names.SHOVEL)) || (name.equals(Names.PICKAXE))
				|| (name.equals(Names.CRACKHAMMER))) {
			tab = ItemGroups.getTab(SharedStrings.TAB_TOOLS);
		} else {
			tab = ItemGroups.getTab(SharedStrings.TAB_ITEMS);
		}

		return create(name, material, tab);
	}

	@Nullable
	protected static Item create(@Nonnull final Names name, @Nonnull final String materialName,
			final CreativeTabs tab) {
		return create(name, Materials.getMaterialByName(materialName), tab);
	}

	/**
	 *
	 * @param name
	 *            Name of the requested item type
	 * @param material
	 *            The material this is made from
	 * @param tab
	 *            which creative tab is it on
	 * @return the block this function created
	 */
	@Nullable
	protected static Item create(@Nonnull final Names name, @Nonnull final MMDMaterial material,
			final CreativeTabs tab) {
		if (sanityCheck(name, material)) {
			return null;
		}

		if (material.hasItem(name)) {
			return material.getItem(name);
		}

		if (isArmor(name)) {
			return createArmorItem(name, material, tab);
		}

		final Item item = createItem(material, name.toString(), getClassFromName(name),
				isNameEnabled(name), tab);

		setupOredict(item, getOredictFromName(name), name, material);

		return item;
	}

	private static boolean sanityCheck(final Names name, final MMDMaterial material) {
		return ((material.isEmpty()) || (name == null) || isWrongThingToMake(name, material));
	}

	private static void setupOredict(@Nullable final Item item, @Nullable final String oredict, final Names name,
			final MMDMaterial material) {
		if (item != null) {
			if (oredict != null) {
				Oredicts.registerOre(oredict + material.getCapitalizedName(), item);
				if (name.equals(Names.ROD)) {
					Oredicts.registerOre(Oredicts.STICK + material.getCapitalizedName(), item);
					Oredicts.registerOre(Oredicts.ROD, item);
				} else if (name.equals(Names.GEAR)) {
					Oredicts.registerOre(Oredicts.GEAR, item);
				}
			}
			if (name.equals(Names.DOOR)) {
				Oredicts.registerOre(Oredicts.DOOR, item);
			}
		}
	}

	private static boolean isWrongThingToMake(final Names name, final MMDMaterial material) {
		return ((((name.equals(Names.BLEND)) || (name.equals(Names.SMALLBLEND)))
				&& (!material.hasBlend()))
				|| (name.equals(Names.ANVIL) && (!material.hasBlock(Names.ANVIL)))
				|| (name.equals(Names.DOOR) && (!material.hasBlock(Names.DOOR)))
				|| (name.equals(Names.SLAB) && (!material.hasBlock(Names.SLAB)
						&& (!material.hasBlock(Names.DOUBLE_SLAB)))));
	}

	private static boolean isArmor(final Names name) {
		return ((name.equals(Names.HELMET)) || (name.equals(Names.CHESTPLATE))
				|| (name.equals(Names.LEGGINGS)) || (name.equals(Names.BOOTS)));
	}

	@Nullable
	protected static Item addItem(@Nonnull final Item item, @Nonnull final Names name,
			final CreativeTabs tab) {
		return addItem(item, name.toString(), Materials.DEFAULT, tab);
	}

	@Nullable
	protected static Item addItem(@Nonnull final Item item, @Nonnull final String name,
			final CreativeTabs tab) {
		return addItem(item, name, Materials.DEFAULT, tab);
	}

	/**
	 *
	 * @param item
	 *            The item to add
	 * @param name
	 *            Name of the item
	 * @param material
	 *            Material it is made from
	 * @param tab
	 *            which creative tab it is in
	 * @return the item that was added
	 */
	@Nullable
	protected static Item addItem(@Nonnull final Item item, @Nonnull final String name,
			final MMDMaterial material, @Nullable final CreativeTabs tab) {
		String fullName;
		if ((!material.isEmpty()) && (!material.isDefault())) {
			if (material.hasItem(name)) {
				return material.getItem(name);
			}
			fullName = material.getName() + "_" + name;
		} else {
			fullName = name;
		}

		item.setRegistryName(new ResourceLocation(Loader.instance().activeModContainer().getModId(), fullName));
		item.setTranslationKey(item.getRegistryName().getNamespace() + "." + fullName);

		if (tab != null) {
			item.setCreativeTab(tab);
		}

		if (!material.isEmpty()) {
			itemsByMaterial.computeIfAbsent(material, (final MMDMaterial g) -> new ArrayList<>());
			itemsByMaterial.get(material).add(item);
		}

		itemRegistry.put(fullName, item);

		return item;
	}

	@Nullable
	private static Item createItem(@Nonnull final MMDMaterial material, @Nonnull final String name,
			@Nonnull final Class<? extends Item> clazz, @Nonnull final boolean enabled,
			final CreativeTabs tab) {
		if (material.hasItem(name)) {
			return material.getItem(name);
		}

		if (enabled) {
			Constructor<?> ctor = null;
			Item inst = null;

			try {
				ctor = clazz.getConstructor(material.getClass());
			} catch (NoSuchMethodException | SecurityException ex) {
				MMDLib.logger.error(
						"Class for Item named %s does not have the correct constructor", name, ex);
				return null;
			}

			try {
				inst = (Item) ctor.newInstance(material);
			} catch (IllegalAccessException | IllegalArgumentException | InstantiationException
					| InvocationTargetException | ExceptionInInitializerError ex) {
				MMDLib.logger.error(
						"Unable to create new instance of Item class for item name %s of material %s",
						name, material.getCapitalizedName(), ex);
				return null;
			} catch (final Exception ex) {
				MMDLib.logger.error("Unable to create Item named %s for material %s", name,
						material.getCapitalizedName(), ex);
				return null;
			}

			if (inst != null) {
				addItem(inst, name, material, tab);
				material.addNewItem(name, inst);
				return inst;
			}
		}
		return null;
	}

	@Nullable
	private static Item createArmorItem(@Nonnull final Names name,
			@Nonnull final MMDMaterial material, final CreativeTabs tab) {
		if (!(isNameEnabled(name))) {
			return null;
		}

		if (material.hasItem(name)) {
			return material.getItem(name);
		}

		final Item item = ItemMMDArmor.createArmor(material, name);

		if (item == null) {
			return null;
		}
		final Item finalItem = addItem(item, name.toString(), material, tab);
		material.addNewItem(name, finalItem);
		return finalItem;
	}

	/**
	 *
	 * @param material
	 *            The material base of this item
	 * @param tab
	 *            which creative tab it is in
	 * @return the item this function created
	 */
	@Nullable
	protected static Item createMekCrystal(@Nonnull final MMDMaterial material,
			final CreativeTabs tab) {
		if (material.hasItem(Names.CRYSTAL)) {
			return material.getItem(Names.CRYSTAL);
		}

		final Item item = createItem(material, Names.CRYSTAL.toString(), GenericMMDItem.class,
				(Options.isModEnabled(Mekanism.PLUGIN_MODID)
						&& (material.getType() != MaterialType.CRYSTAL)),
				tab);
		if (item != null) {
			Oredicts.registerOre(Oredicts.CRYSTAL + material.getCapitalizedName(), item);
			return item;
		}

		return null;
	}

	/**
	 * Uses reflection to expand the size of the combat damage and attack speed arrays to prevent
	 * initialization index-out-of-bounds errors.
	 *
	 * @param itemClass
	 *            The class to modify
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 */
	private static void expandCombatArrays(@Nonnull final Class<?> itemClass)
			throws IllegalAccessException, NoSuchFieldException {
		// WARNING: this method contains black magic
		final int expandedSize = 256;
		final Field[] fields = itemClass.getDeclaredFields();
		for (final Field field : fields) {
			if (Modifier.isStatic(field.getModifiers()) && field.getType().isArray()
					&& field.getType().getComponentType().equals(float.class)) {
				MMDLib.logger.info("{}: Expanding array variable {}.{} to size {}",
						Thread.currentThread().getStackTrace()[0].toString(),
						itemClass.getSimpleName(), field.getName(), expandedSize);
				field.setAccessible(true); // bypass 'private' key word
				final Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL); // bypass
																						// 'final'
																						// keyword
				final float[] newArray = new float[expandedSize];
				Arrays.fill(newArray, 0F);
				System.arraycopy(field.get(null), 0, newArray, 0, Array.getLength(field.get(null)));
				field.set(null, newArray);
			}
		}
	}

	/**
	 *
	 * @param itemStack
	 *            The ItemStack
	 * @return The output
	 */
	public static int getSortingValue(@Nonnull final ItemStack itemStack) {
		int classVal = 990000;
		int materialVal = 9900;
		final Item item = itemStack.getItem();
		if ((item instanceof ItemBlock)
				&& (((ItemBlock) item).getBlock() instanceof IMMDObject)) {
			final Block block = ((ItemBlock) item).getBlock();
			classVal = classSortingValues.computeIfAbsent(
					block.getClass(),
					(final Class<?> clazz) -> 990000);
			materialVal = materialSortingValues.computeIfAbsent(
					((IMMDObject) block).getMMDMaterial(),
					(final MMDMaterial material) -> 9900);
		} else if (item instanceof IMMDObject) {
			classVal = classSortingValues.computeIfAbsent(item.getClass(),
					(final Class<?> clazz) -> 990000);
			materialVal = materialSortingValues.computeIfAbsent(
					((IMMDObject) item).getMMDMaterial(),
					(final MMDMaterial material) -> 9900);
		}
		return classVal + materialVal + (itemStack.getMetadata() % 100);
	}

	protected static Class<? extends Item> getClassFromName(@Nonnull final Names name) {
		if (nameToClass.containsKey(name.toString())) {
			return nameToClass.get(name.toString());
		}
		return net.minecraft.item.Item.class;
	}

	@Nullable
	protected static String getOredictFromName(@Nonnull final Names name) {
		if (nameToOredict.containsKey(name.toString())) {
			return nameToOredict.get(name.toString());
		}
		return null;
	}

	protected static boolean isNameEnabled(@Nonnull final Names name) {
		if (nameToEnabled.containsKey(name.toString())) {
			return nameToEnabled.get(name.toString());
		}
		return false;
	}

	protected static void addItemType(@Nonnull final Names name,
			@Nonnull final Class<? extends Item> clazz, @Nonnull final Boolean enabled) {
		addItemType(name, clazz, enabled, null);
	}

	protected static void addItemType(@Nonnull final Names name,
			@Nonnull final Class<? extends Item> clazz, @Nonnull final Boolean enabled,
			@Nullable final String oredict) {
		addItemType(name.toString(), clazz, enabled, oredict);
	}
	
	protected static void addItemType(@Nonnull final String name,
			@Nonnull final Class<? extends Item> clazz, @Nonnull final Boolean enabled) {
		addItemType(name, clazz, enabled, null);
	}
	
	protected static void addItemType(@Nonnull final String name,
			@Nonnull final Class<? extends Item> clazz, @Nonnull final Boolean enabled,
			@Nullable final String oredict) {
		com.mcmoddev.lib.MMDLib.logger.debug("Register Item Type: {}/{}/{}/{}", name, clazz.getName(), enabled, oredict==null?"null":"".contentEquals(oredict)?"EMPTY":oredict);
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
	 * Gets an item by its name. The name is the name as it is registered in the GameRegistry, not
	 * its unlocalized name (the unlocalized name is the registered name plus the prefix
	 * "basemetals.")
	 *
	 * @param name
	 *            The name of the item in question
	 * @return The item matching that name, or null if there isn't one
	 */
	@Nullable
	public static Item getItemByName(@Nonnull final String name) {
		return itemRegistry.get(name);
	}

	/**
	 * This is the reverse of the getItemByName(...) method, returning the registered name of an
	 * item instance (Base Metals items only).
	 *
	 * @param item
	 *            The item in question
	 * @return The name of the item, or null if the item is not a Base Metals item.
	 */
	@Nullable
	public static String getNameOfItem(@Nonnull final Item item) {
		return itemRegistry.inverse().get(item);
	}

	public static Map<String, Item> getItemRegistry() {
		return Collections.unmodifiableMap(itemRegistry);
	}

	/**
	 * Gets a map of all items added, sorted by material.
	 *
	 * @return An unmodifiable map of added items categorized by metal material
	 */
	public static Map<MMDMaterial, List<Item>> getItemsByMaterial() {
		return Collections.unmodifiableMap(itemsByMaterial);
	}
}
