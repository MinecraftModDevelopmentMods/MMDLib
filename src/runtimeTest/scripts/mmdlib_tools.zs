#loader mmdlib
import mods.mmdlib.MaterialBuilder;
import mods.mmdlib.ItemModule;
import mods.mmdlib.ToolSettings;

var module = new ItemModule("phase2_tool", "mmdlib:crackhammer");
var settings = new ToolSettings();
settings.mineable = "minecraft:mineable/pickaxe";
settings.fastOn = "minecraft:mineable/pickaxe";
settings.attackDamage = 4;
settings.attackSpeed = -2.5;
settings.abilities("pickaxe");
settings.durability(0.5, 1, "");
settings.speed(0.5, 1, "");
module.configureTool(settings);
module.build();

var material = new MaterialBuilder("phase2");
material.removeItems("all").addItems("phase2_tool");
material.removeBlocks("all");
material.hasFluid = false;
material.repairItem = "minecraft:diamond";
material.incorrectBlocks = "minecraft:incorrect_for_wooden_tool";
material.baseBlock = "minecraft:stone";
material.blockSetType = "minecraft:oak";
material.blockSound = "wood";
material.mapColor = "gold";
material.toolDurability(200).toolEfficiency(8).attackDamage(3).enchantability(17);
material.build();
