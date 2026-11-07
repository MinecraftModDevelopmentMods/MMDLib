# CraftTweaker crushing recipes

MMDLib's shared `mmdlib:crushing` recipe type accepts one item ingredient and one
output stack. Its consumers include the crackhammer and crushing machines in
mods that use this type. Changes apply to every consumer of the shared recipe
manager; the integration does not install a separate recipe pool or processor.

Use ordinary CraftTweaker recipe scripts, with its default loader or the explicit
loader below. These scripts run after registries are available and participate in
CraftTweaker's normal recipe reload lifecycle. Startup content-definition scripts
using the `mmdlib` loader run too early for recipe edits.

```zenscript
#loader crafttweaker
var crusher = <recipetype:mmdlib:crushing>;
crusher.addRecipe("clay_to_diamonds", <item:minecraft:diamond> * 2, <item:minecraft:clay_ball>);
crusher.addRecipe("planks_to_nugget", <item:minecraft:gold_nugget>, <tag:item:minecraft:planks>);
```

`addRecipe(name, output, input)` uses CraftTweaker's normal recipe ID rules:
`"clay_to_diamonds"` becomes `crafttweaker:clay_to_diamonds`. Outputs retain their
count and item components through the existing recipe's defensive stack copy.
Inputs use CraftTweaker's vanilla ingredient conversion, including tags. The recipe
consumes one input item; multiplied input stacks, empty input/output, and crafting
ingredient transformers are rejected rather than silently changing semantics.
Machine processing time and energy usage are consumer behavior, not fields of this
shared recipe type.

The manager inherits CraftTweaker's existing operations:

```zenscript
crusher.removeByName("crafttweaker:clay_to_diamonds");
crusher.removeByInput(<item:minecraft:clay_ball>);
crusher.remove(<item:minecraft:diamond>); // remove by output
var recipe = crusher.getRecipeByName("crafttweaker:planks_to_nugget"); // null if absent
var recipes = crusher.allRecipes;
```

`getRecipesByOutput`, `removeByModid`, `removeByRegex`, `removeMatching`, and
`removeAll` also use the standard IRecipeManager API. Remove an existing recipe by
its full ID before adding its replacement. Adding the same ID twice without a
removal retains CraftTweaker's normal duplicate handling.

The existing JSON route remains available:

```zenscript
crusher.addJsonRecipe("shard_to_emerald", {
    ingredient: {item: "minecraft:amethyst_shard"},
    result: {id: "minecraft:emerald", count: 2}
});
```

## Implementation and verification

The manager and native recipe query type live in
`compat/CraftTweaker/crusher`, separate from the startup definition builders.
CraftTweaker discovers them through its standard annotations; there is no additional
MMDLib startup loader or direct recipe-manager mutation. CraftTweaker remains
optional. The core recipe class, serializer, recipe ID and consumers are unchanged.

The executable [recipe fixture](../src/runtimeTest/scripts/crusher_recipes.zs)
checks addition, queries, removal by name/input/output, replacement, JSON fallback,
and tag ingredients. [CrusherRecipeScriptSmoke](../src/runtimeTest/java/zone/moddev/mc/lib/smoke/CrusherRecipeScriptSmoke.java)
checks the live recipes, invalid input rejection, and actual crackhammer consumption
with normal durability cost. The isolated client smoke reloads server resources and
checks that additions/removals persist without duplicate IDs.

The separately packaged companion demo currently has its own
`mmdlib_companion:crushing` generic machine recipes (with time and resource groups).
Those are used by its demo crusher and dual-crusher and are not aliases for the
shared type. This integration targets `mmdlib:crushing`; it does not migrate the
companion demo or change those recipes.

Verified on the current working tree: `./gradlew --offline check build` passed
(190 unit tests, 89 MMDLib GameTests, 52 companion GameTests, packaging and static
assets). The real integrated client passed a server-resource reload with additions,
removals and unique IDs intact (MMDLIB_CT_RECIPES_RELOAD_PASS). A separate client
launch without CraftTweaker passed. The release JAR includes the recipe manager
and native query type and excludes smoke classes/scripts. EA3/SA3 machines were
not launched in this verification; the live consumer test uses the shared type
through the crackhammer.
