package com.mcmoddev.lib;

import com.mcmoddev.lib.block.BlockMMDButton;
import com.mcmoddev.lib.block.BlockMMDDoor;
import com.mcmoddev.lib.block.BlockMMDLever;
import com.mcmoddev.lib.block.BlockMMDOre;
import com.mcmoddev.lib.block.BlockMMDPressurePlate;
import com.mcmoddev.lib.block.BlockMMDSlab;
import com.mcmoddev.lib.block.BlockMMDStairs;
import com.mcmoddev.lib.block.BlockMMDWall;
import com.mcmoddev.lib.block.BlockMoltenFluid;
import com.mcmoddev.lib.data.ConfigKeys;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.events.IRegAPI;
import com.mcmoddev.lib.events.MMDLibRegisterBlockTypes;
import com.mcmoddev.lib.events.MMDLibRegisterItemTypes;
import com.mcmoddev.lib.integration.plugins.IC2;
import com.mcmoddev.lib.integration.plugins.Mekanism;
import com.mcmoddev.lib.item.*;
import com.mcmoddev.lib.block.*;
import com.mcmoddev.lib.util.Config.Options;
import com.mcmoddev.lib.util.Oredicts;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=MMDLib.MODID)
public class TypeRegistrationEvents {

	public TypeRegistrationEvents() {
	}

	@SubscribeEvent
	public static void ItemTypeRegistrationEvent(MMDLibRegisterItemTypes ev) {
		MMDLib.logger.fatal("MMDLibRegisterItemTypes");
		IRegAPI<Item> api = ev.getApi();

		api.addSorting(BlockMMDOre.class);
		
		api.addType(ItemMMDIngot.class, Names.CRYSTAL, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.CRYSTAL);
		api.addType(ItemMMDIngot.class, Names.GEM, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.GEM);
		api.addType(ItemMMDAnvilBlock.class, Names.ANVIL, Options.isThingEnabled(ConfigKeys.ANVIL));
		api.addType(ItemMMDArrow.class, Names.ARROW, Options.isThingEnabled(ConfigKeys.BOW_AND_ARROW), Oredicts.ARROW);
		api.addType(ItemMMDAxe.class, Names.AXE, Options.isThingEnabled(ConfigKeys.BASIC_TOOLS));
		api.addType(ItemMMDBolt.class, Names.BOLT, Options.isThingEnabled(ConfigKeys.CROSSBOW_AND_BOLT));
		api.addType(ItemMMDArmor.class, Names.BOOTS, Options.isThingEnabled(ConfigKeys.ARMOR));
		api.addType(ItemMMDBow.class, Names.BOW, Options.isThingEnabled(ConfigKeys.BOW_AND_ARROW));
		api.addType(ItemMMDArmor.class, Names.CHESTPLATE, Options.isThingEnabled(ConfigKeys.ARMOR));
		api.addType(ItemMMDCrackHammer.class, Names.CRACKHAMMER, Options.isThingEnabled(ConfigKeys.CRACKHAMMER));
		api.addType(ItemMMDCrossbow.class, Names.CROSSBOW, Options.isThingEnabled(ConfigKeys.CROSSBOW_AND_BOLT));
		api.addType(ItemMMDDoor.class, Names.DOOR, Options.isThingEnabled(ConfigKeys.DOOR));
		api.addType(ItemMMDFishingRod.class, Names.FISHING_ROD, Options.isThingEnabled(ConfigKeys.FISHING_ROD));
		api.addType(ItemMMDArmor.class, Names.HELMET, Options.isThingEnabled(ConfigKeys.ARMOR));
		api.addType(ItemMMDHoe.class, Names.HOE, Options.isThingEnabled(ConfigKeys.BASIC_TOOLS));
		api.addType(ItemMMDHorseArmor.class, Names.HORSE_ARMOR, Options.isThingEnabled(ConfigKeys.HORSE_ARMOR));
		api.addType(ItemMMDIngot.class, Names.INGOT, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.INGOT);
		api.addType(ItemMMDArmor.class, Names.LEGGINGS, Options.isThingEnabled(ConfigKeys.ARMOR));
		api.addType(ItemMMDNugget.class, Names.NUGGET, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.NUGGET);
		api.addType(ItemMMDPickaxe.class, Names.PICKAXE, Options.isThingEnabled(ConfigKeys.BASIC_TOOLS));
		api.addType(ItemMMDPowder.class, Names.POWDER, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.DUST);
		api.addType(ItemMMDBlend.class, Names.BLEND, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.DUST, ItemMMDPowder.class);
		api.addType(ItemMMDShears.class, Names.SHEARS, Options.isThingEnabled(ConfigKeys.SHEARS));
		api.addType(ItemMMDShield.class, Names.SHIELD, Options.isThingEnabled(ConfigKeys.SHIELD), Oredicts.SHIELD);
		api.addType(ItemMMDShovel.class, Names.SHOVEL, Options.isThingEnabled(ConfigKeys.BASIC_TOOLS));
		api.addType(ItemMMDSickle.class, Names.SCYTHE, Options.isThingEnabled(ConfigKeys.SCYTHE));
		api.addType(ItemMMDSlab.class, Names.SLAB, Options.isThingEnabled(ConfigKeys.SLAB), Oredicts.SLAB);
		api.addType(ItemMMDSmallPowder.class, Names.SMALLPOWDER, Options.isThingEnabled(ConfigKeys.SMALL_DUST), Oredicts.DUST_TINY);
		api.addType(ItemMMDSmallBlend.class, Names.SMALLBLEND, Options.isThingEnabled(ConfigKeys.SMALL_DUST), Oredicts.DUST_TINY, ItemMMDSmallPowder.class);
		api.addType(ItemMMDSword.class, Names.SWORD, Options.isThingEnabled(ConfigKeys.BASIC_TOOLS));
		api.addType(ItemMMDRod.class, Names.ROD, Options.isThingEnabled(ConfigKeys.ROD), Oredicts.ROD);
		api.addType(ItemMMDGear.class, Names.GEAR, Options.isThingEnabled(ConfigKeys.GEAR), Oredicts.GEAR);
		api.addSorting(ItemMMDDoor.class, BlockMMDDoor.class);
		api.addType(GenericMMDItem.class, Names.CASING, Options.enableModderSupportThings(), Oredicts.CASING);
		api.addType(GenericMMDItem.class, Names.DENSE_PLATE, Options.enableModderSupportThings(), Oredicts.PLATE_DENSE);
		api.addType(GenericMMDItem.class, Names.CRUSHED, Options.isModEnabled(IC2.PLUGIN_MODID) || Options.isThingEnabled(ConfigKeys.IC2ITEMS_WITHOUT_PLUGIN), Oredicts.CRUSHED);
		api.addType(GenericMMDItem.class, Names.CRUSHED_PURIFIED, Options.isModEnabled(IC2.PLUGIN_MODID) || Options.isThingEnabled(ConfigKeys.IC2ITEMS_WITHOUT_PLUGIN), Oredicts.CRUSHED_PURIFIED);
		api.addType(GenericMMDItem.class, Names.SHARD, Options.isModEnabled(Mekanism.PLUGIN_MODID) || Options.isThingEnabled(ConfigKeys.MEKITEMS_WITHOUT_PLUGIN), Oredicts.SHARD);
		api.addType(GenericMMDItem.class, Names.CLUMP, Options.isModEnabled(Mekanism.PLUGIN_MODID) || Options.isThingEnabled(ConfigKeys.MEKITEMS_WITHOUT_PLUGIN), Oredicts.CLUMP);
		api.addType(GenericMMDItem.class, Names.POWDER_DIRTY, Options.isModEnabled(Mekanism.PLUGIN_MODID) || Options.isThingEnabled(ConfigKeys.MEKITEMS_WITHOUT_PLUGIN), Oredicts.DUST_DIRTY);
		api.addSorting(BlockMMDButton.class);
		api.addSorting(BlockMMDSlab.class);
		api.addSorting(BlockMMDSlab.Double.class);
		api.addSorting(BlockMMDSlab.Half.class);
		api.addSorting(BlockMMDLever.class);
		api.addSorting(BlockMMDPressurePlate.class);
		api.addSorting(BlockMMDStairs.class);
		api.addSorting(BlockMMDWall.class);
		api.addSorting(BlockMoltenFluid.class);
		api.addSorting(ItemMMDSlab.class, BlockMMDSlab.class);

	}

	@SubscribeEvent
	public static void BlockTypeRegistrationEvent(MMDLibRegisterBlockTypes ev) {
		MMDLib.logger.fatal("MMDLibRegisterBlockTypes");
		IRegAPI<Block> api = ev.getApi();
		api.addType(BlockMMDAnvil.class, Names.ANVIL, Options.isThingEnabled(ConfigKeys.ANVIL));
		api.addType(BlockMMDBars.class, Names.BARS, Options.isThingEnabled(ConfigKeys.BARS), Oredicts.BARS);
		api.addType(BlockMMDBlock.class, Names.BLOCK, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.BLOCK);
		api.addType(BlockMMDBookshelf.class, Names.BOOKSHELF, Options.isThingEnabled(ConfigKeys.BOOKSHELF));
		api.addType(BlockMMDButton.class, Names.BUTTON, Options.isThingEnabled(ConfigKeys.BUTTON), Oredicts.BUTTON);
		api.addType(BlockMMDDoor.class, Names.DOOR, Options.isThingEnabled(ConfigKeys.DOOR));
		api.addType(BlockMMDSlab.Double.class, Names.DOUBLE_SLAB, Options.isThingEnabled(ConfigKeys.SLAB));
		api.addType(BlockMMDFlowerPot.class, Names.FLOWER_POT, Options.isThingEnabled(ConfigKeys.FLOWERPOT));
		api.addType(BlockMMDLadder.class, Names.LADDER, Options.isThingEnabled(ConfigKeys.LADDER));
		api.addType(BlockMMDLever.class, Names.LEVER, Options.isThingEnabled(ConfigKeys.LEVER), Oredicts.LEVER);
		api.addType(BlockMMDPlate.class, Names.PLATE, Options.isThingEnabled(ConfigKeys.PLATE), Oredicts.PLATE);
		api.addType(BlockMMDPressurePlate.class, Names.PRESSURE_PLATE, Options.isThingEnabled(ConfigKeys.PRESSURE_PLATE));
		api.addType(BlockMMDSlab.Half.class, Names.SLAB, Options.isThingEnabled(ConfigKeys.SLAB));
		api.addType(BlockMMDStairs.class, Names.STAIRS, Options.isThingEnabled(ConfigKeys.STAIRS), Oredicts.STAIRS);
		api.addType(BlockMMDTrapDoor.class, Names.TRAPDOOR, Options.isThingEnabled(ConfigKeys.TRAPDOOR), Oredicts.TRAPDOOR);
		api.addType(BlockMMDTripWireHook.class, Names.TRIPWIRE_HOOK, Options.isThingEnabled(ConfigKeys.TRIPWIRE_HOOK));
		api.addType(BlockMMDWall.class, Names.WALL, Options.isThingEnabled(ConfigKeys.WALL), Oredicts.WALL);
		api.addType(BlockMMDFence.class, Names.FENCE, Options.isThingEnabled(ConfigKeys.WALL));
		api.addType(BlockMMDFenceGate.class, Names.FENCE_GATE, Options.isThingEnabled(ConfigKeys.WALL));
		api.addType(BlockMMDOre.class, Names.ENDORE, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.ORE_END);
		api.addType(BlockMMDNetherOre.class, Names.NETHERORE, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.ORE_NETHER);
		api.addType(BlockMMDOre.class, Names.ORE, Options.isThingEnabled(ConfigKeys.BASICS), Oredicts.ORE);
	}
}
