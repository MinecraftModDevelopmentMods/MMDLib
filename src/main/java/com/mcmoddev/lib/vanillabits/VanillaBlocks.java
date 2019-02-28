package com.mcmoddev.lib.vanillabits;

import com.mcmoddev.lib.data.VanillaMaterialNames;
import com.mcmoddev.lib.events.MMDLibRegisterBlocks;
import com.mcmoddev.lib.init.Blocks;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=MMDLib.MODID)
public class VanillaBlocks extends Blocks {

	public VanillaBlocks() {
		// TODO Auto-generated constructor stub
	}

	@SubscribeEvent(priority=EventPriority.HIGHEST)
	private static void registerVanilla(MMDLibRegisterBlocks ev) {
		// Vanilla Materials get their Ore and Block always
		final MMDMaterial charcoal = Materials.getMaterialByName(VanillaMaterialNames.CHARCOAL);
		final MMDMaterial coal = Materials.getMaterialByName(VanillaMaterialNames.COAL);
		final MMDMaterial diamond = Materials.getMaterialByName(VanillaMaterialNames.DIAMOND);
		final MMDMaterial emerald = Materials.getMaterialByName(VanillaMaterialNames.EMERALD);
		final MMDMaterial gold = Materials.getMaterialByName(VanillaMaterialNames.GOLD);
		final MMDMaterial iron = Materials.getMaterialByName(VanillaMaterialNames.IRON);
		final MMDMaterial lapis = Materials.getMaterialByName(VanillaMaterialNames.LAPIS);
		final MMDMaterial obsidian = Materials.getMaterialByName(VanillaMaterialNames.OBSIDIAN);
		final MMDMaterial quartz = Materials.getMaterialByName(VanillaMaterialNames.QUARTZ);
		final MMDMaterial redstone = Materials.getMaterialByName(VanillaMaterialNames.REDSTONE);

		coal.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.COAL_BLOCK);
		coal.addNewBlock(Names.ORE, net.minecraft.init.Blocks.COAL_ORE);

		diamond.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.DIAMOND_BLOCK);
		diamond.addNewBlock(Names.ORE, net.minecraft.init.Blocks.DIAMOND_ORE);

		emerald.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.EMERALD_BLOCK);
		emerald.addNewBlock(Names.ORE, net.minecraft.init.Blocks.EMERALD_ORE);

		gold.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.GOLD_BLOCK);
		gold.addNewBlock(Names.ORE, net.minecraft.init.Blocks.GOLD_ORE);
		gold.addNewBlock(Names.PRESSURE_PLATE,
				net.minecraft.init.Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);

		iron.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.IRON_BLOCK);
		iron.addNewBlock(Names.ORE, net.minecraft.init.Blocks.IRON_ORE);
		iron.addNewBlock(Names.BARS, net.minecraft.init.Blocks.IRON_BARS);
		iron.addNewBlock(Names.DOOR, net.minecraft.init.Blocks.IRON_DOOR);
		iron.addNewBlock(Names.TRAPDOOR, net.minecraft.init.Blocks.IRON_TRAPDOOR);
		iron.addNewBlock(Names.PRESSURE_PLATE,
				net.minecraft.init.Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);

		lapis.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.LAPIS_BLOCK);
		lapis.addNewBlock(Names.ORE, net.minecraft.init.Blocks.LAPIS_ORE);

		obsidian.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.OBSIDIAN);

		quartz.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.QUARTZ_BLOCK);
		quartz.addNewBlock(Names.ORE, net.minecraft.init.Blocks.QUARTZ_ORE);
		quartz.addNewBlock(Names.STAIRS, net.minecraft.init.Blocks.QUARTZ_STAIRS);

		redstone.addNewBlock(Names.BLOCK, net.minecraft.init.Blocks.REDSTONE_BLOCK);
		redstone.addNewBlock(Names.ORE, net.minecraft.init.Blocks.REDSTONE_ORE);

		if (Materials.hasMaterial(VanillaMaterialNames.CHARCOAL)) {
			create(Names.BLOCK, charcoal);
		}

		if (Materials.hasMaterial(VanillaMaterialNames.DIAMOND)) {
			create(Names.BARS, diamond);
			create(Names.DOOR, diamond);
			create(Names.TRAPDOOR, diamond);

			create(Names.BUTTON, diamond);
			create(Names.SLAB, diamond);
			create(Names.DOUBLE_SLAB, diamond);
			create(Names.LEVER, diamond);
			create(Names.PRESSURE_PLATE, diamond);
			create(Names.STAIRS, diamond);
			create(Names.WALL, diamond);
		}

		if (Materials.hasMaterial(VanillaMaterialNames.EMERALD)) {
			create(Names.BARS, emerald);
			create(Names.DOOR, emerald);
			create(Names.TRAPDOOR, emerald);

			create(Names.BUTTON, emerald);
			create(Names.SLAB, emerald);
			create(Names.DOUBLE_SLAB, emerald);
			create(Names.LEVER, emerald);
			create(Names.PRESSURE_PLATE, emerald);
			create(Names.STAIRS, emerald);
			create(Names.WALL, emerald);
		}

		if (Materials.hasMaterial(VanillaMaterialNames.GOLD)) {
			create(Names.PLATE, gold);
			create(Names.BARS, gold);
			create(Names.DOOR, gold);
			create(Names.TRAPDOOR, gold);

			create(Names.BUTTON, gold);
			create(Names.SLAB, gold);
			create(Names.DOUBLE_SLAB, gold);
			create(Names.LEVER, gold);
			create(Names.PRESSURE_PLATE, gold);
			create(Names.STAIRS, gold);
			create(Names.WALL, gold);
		}

		if (Materials.hasMaterial(VanillaMaterialNames.IRON)) {
			create(Names.PLATE, iron);

			create(Names.BUTTON, iron);
			create(Names.SLAB, iron);
			create(Names.DOUBLE_SLAB, iron);
			create(Names.LEVER, iron);
			create(Names.PRESSURE_PLATE, iron);
			create(Names.STAIRS, iron);
			create(Names.WALL, iron);
		}

		if (Materials.hasMaterial(VanillaMaterialNames.OBSIDIAN)) {
			create(Names.BARS, obsidian);
			create(Names.DOOR, obsidian);
			create(Names.TRAPDOOR, obsidian);

			create(Names.BUTTON, obsidian);
			create(Names.SLAB, obsidian);
			create(Names.DOUBLE_SLAB, obsidian);
			create(Names.LEVER, obsidian);
			create(Names.PRESSURE_PLATE, obsidian);
			create(Names.STAIRS, obsidian);
			create(Names.WALL, obsidian);
		}

		if (Materials.hasMaterial(VanillaMaterialNames.QUARTZ)) {
			create(Names.BARS, quartz);
			create(Names.DOOR, quartz);
			create(Names.TRAPDOOR, quartz);

			create(Names.BUTTON, quartz);
			create(Names.LEVER, quartz);
			create(Names.PRESSURE_PLATE, quartz);
			create(Names.WALL, quartz);
		}

		if (Materials.hasMaterial(VanillaMaterialNames.STONE)) {
			// Stub
		}

		if (Materials.hasMaterial(VanillaMaterialNames.WOOD)) {
			// Stub
		}
	}
}
