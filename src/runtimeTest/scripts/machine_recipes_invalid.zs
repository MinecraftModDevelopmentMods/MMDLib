// The machine has no group "missing": the typed builder refuses the recipe, as
// a datapack recipe reload would. Kept apart so the error ends only this file.
import mods.mmdlib.MachineRecipeBuilder;

new MachineRecipeBuilder("mmdlib:test/script_machine")
    .inputItems("missing", <item:minecraft:flint>)
    .outputItem("output", <item:minecraft:gravel>, 1.0)
    .time(2)
    .build("unknown_group");
