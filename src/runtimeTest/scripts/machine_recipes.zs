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

// A reusable input: the bone stays, and each cycle makes bone meal.
new MachineRecipeBuilder("mmdlib:test/script_machine")
    .inputItems("input", <item:minecraft:bone>, 1, false)
    .outputItem("output", <item:minecraft:bone_meal>, 1.0)
    .time(2)
    .build("bone_to_bone_meal");

// CraftTweaker's ingredient replacement takes machine recipes apart and
// rebuilds them: the datapack recipe test/flint_to_gunpowder now takes clay.
import crafttweaker.api.ingredient.IIngredient;
import crafttweaker.api.recipe.replacement.Replacer;
import crafttweaker.api.recipe.replacement.type.NameFilteringRule;

Replacer.create()
    .filter(NameFilteringRule.anyOf("mmdlib:test/flint_to_gunpowder"))
    .replace<IIngredient>(<recipecomponent:crafttweaker:input/ingredients>,
        <item:minecraft:flint>, <item:minecraft:clay_ball>)
    .execute();
