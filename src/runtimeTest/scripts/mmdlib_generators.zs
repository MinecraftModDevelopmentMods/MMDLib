#loader mmdlib
// A recipe generator for the test machine test/generated_machine: for every
// c:ingots/<metal> with a c:nuggets/<metal> tag, an ingot makes nuggets. The
// machine XML sets the nugget count and time.
import mods.mmdlib.RecipeGenerators;

RecipeGenerators.register("test/ingot_nuggets", (gen) => {
    for tag in gen.tagIds("c:ingots/") {
        val metal = tag[9 .. tag.length];
        if gen.hasTag("c:nuggets/" + metal) {
            gen.recipe("ingot_nuggets/" + metal)
                .inputTag("input", tag)
                .outputItem("output",
                    gen.tagItems("c:nuggets/" + metal)[0] * gen.intProperty("nuggets", 9))
                .time(gen.intProperty("time", 100))
                .add();
        }
    }
});
