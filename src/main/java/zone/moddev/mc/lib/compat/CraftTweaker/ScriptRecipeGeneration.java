package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;

/**
 * What a script recipe generator gets on each data reload: the recipe type, the
 * generator's properties from machine XML, the reload's item tags, and
 * {@link #recipe} to add recipes. Tag brackets are not usable during a reload;
 * read tags here instead.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.RecipeGeneration")
public final class ScriptRecipeGeneration {
    private final RecipeGenerationContext context;
    private final MachineRecipeOutput output;

    ScriptRecipeGeneration(RecipeGenerationContext context,
        MachineRecipeOutput output) {
        this.context = context;
        this.output = output;
    }

    /** The recipe type being generated, such as {@code example:crushing}. */
    @ZenCodeType.Getter("recipeType")
    public String getRecipeType() {
        return context.recipeType().id().toString();
    }

    /** The generator's attribute {@code name}, or {@code fallback}. */
    @ZenCodeType.Method
    public String property(String name,
        @ZenCodeType.OptionalString String fallback) {
        return context.properties().string(name, fallback);
    }

    /**
     * The generator's attribute {@code name} as a whole number, or
     * {@code fallback}.
     *
     * @throws IllegalArgumentException
     *             if it is not a whole number
     */
    @ZenCodeType.Method
    public int intProperty(String name, int fallback) {
        return context.properties().integer(name, fallback, Integer.MIN_VALUE,
            Integer.MAX_VALUE);
    }

    /**
     * The generator's attribute {@code name} as a number, or {@code fallback}.
     *
     * @throws IllegalArgumentException
     *             if it is not a number
     */
    @ZenCodeType.Method
    public double doubleProperty(String name, double fallback) {
        return context.properties().decimal(name, fallback, -Double.MAX_VALUE,
            Double.MAX_VALUE);
    }

    /**
     * The IDs of every item tag starting with {@code prefix}, such as
     * {@code "c:ores/"}, sorted.
     */
    @ZenCodeType.Method
    public String[] tagIds(String prefix) {
        return context.tags().items().keySet().stream()
            .map(ResourceLocation::toString).filter(id -> id.startsWith(prefix))
            .sorted().toArray(String[]::new);
    }

    /** Whether the item tag {@code id} has any items. */
    @ZenCodeType.Method
    public boolean hasTag(String id) {
        return !context.tags().members(ResourceLocation.parse(id)).isEmpty();
    }

    /** The items in the item tag {@code id}; empty if it has none. */
    @ZenCodeType.Method
    public IItemStack[] tagItems(String id) {
        return context.tags().members(ResourceLocation.parse(id)).stream()
            .map(holder -> IItemStack.of(new ItemStack(holder.value())))
            .toArray(IItemStack[]::new);
    }

    /**
     * Starts the recipe {@code <type namespace>:<name>}, added to this reload's
     * recipes by its {@code add()}.
     */
    @ZenCodeType.Method
    public ScriptGeneratedRecipe recipe(String name) {
        var type = context.recipeType();
        return new ScriptGeneratedRecipe(type, context.tags(), ResourceLocation
            .fromNamespaceAndPath(type.id().getNamespace(), name), output);
    }
}
