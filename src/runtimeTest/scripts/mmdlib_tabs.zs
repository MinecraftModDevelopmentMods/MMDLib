#loader mmdlib
import mods.mmdlib.CreativeTabBuilder;

// A new tab: an apple, then the gold material; the stick is added and removed again.
var tab = new CreativeTabBuilder("script_tab");
tab.titleFallback = "Script Tab";
tab.addItems("minecraft:apple", "minecraft:stick");
tab.addMaterials("mmdlib:gold");
tab.removeItems("minecraft:stick");
tab.before = ["minecraft:combat"];
tab.build();

// The JSON test tab, test/workshop, with an apple added at the end.
var workshop = CreativeTabBuilder.edit("mmdlib:test/workshop");
workshop.addItems("minecraft:apple");
workshop.build();
