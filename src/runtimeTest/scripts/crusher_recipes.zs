#loader crafttweaker
// Ordinary reloadable recipes, separate from startup content definitions.
var crusher = <recipetype:mmdlib:crushing>;
crusher.addRecipe("ct_crusher_added", <item:minecraft:diamond> * 2, <item:minecraft:clay_ball>);
if (crusher.getRecipeByName("crafttweaker:ct_crusher_added") == null) {
    crusher.addRecipe("ct_crusher_query_failed", <item:minecraft:paper>, <item:minecraft:feather>);
}
crusher.addRecipe("ct_crusher_removed", <item:minecraft:paper>, <item:minecraft:feather>);
crusher.removeByName("crafttweaker:ct_crusher_removed");
crusher.addRecipe("ct_crusher_input_removed", <item:minecraft:paper>, <item:minecraft:snowball>);
crusher.removeByInput(<item:minecraft:snowball>);
crusher.addRecipe("ct_crusher_output_removed", <item:minecraft:carrot>, <item:minecraft:flint>);
crusher.remove(<item:minecraft:carrot>);
crusher.addRecipe("ct_crusher_replaced", <item:minecraft:gold_nugget>, <item:minecraft:rotten_flesh>);
crusher.removeByName("crafttweaker:ct_crusher_replaced");
crusher.addRecipe("ct_crusher_replaced", <item:minecraft:iron_nugget> * 3, <item:minecraft:rotten_flesh>);
crusher.addJsonRecipe("ct_crusher_json", {
    ingredient: {item: "minecraft:amethyst_shard"},
    result: {id: "minecraft:emerald", count: 2}
});
crusher.addRecipe("ct_crusher_tag", <item:minecraft:gold_nugget>, <tag:item:minecraft:planks>);
