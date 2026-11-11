// An ordinary recipe script: XML machines' recipe types work with CraftTweaker's
// generic recipe managers, like any recipe type.
<recipetype:mmdlib:test/script_machine>.addJsonRecipe("dirt_to_diamond", {
    inputs: { input: [{ items: { item: "minecraft:dirt" } }] },
    outputs: { output: [{ item: { id: "minecraft:diamond", count: 1 } }] },
    time: 2
});

// Removes the datapack recipe test/glass_to_sand.
<recipetype:mmdlib:test/script_machine>.removeByName("mmdlib:test/glass_to_sand");

// The same, typed: two cobblestone make three gravel.
import mods.mmdlib.MachineRecipeBuilder;

new MachineRecipeBuilder("mmdlib:test/script_machine")
    .inputItems("input", <item:minecraft:cobblestone>, 2)
    .outputItem("output", <item:minecraft:gravel> * 3, 1.0)
    .time(2)
    .build("cobble_to_gravel");
