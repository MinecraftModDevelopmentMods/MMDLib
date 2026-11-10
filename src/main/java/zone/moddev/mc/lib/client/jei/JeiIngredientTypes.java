package zone.moddev.mc.lib.client.jei;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.client.IngredientType;
import zone.moddev.mc.lib.gui.client.IngredientTypes;

/**
 * Optional JEI bridge. Mods register mappings in their JEI plugin before GUI
 * interaction.
 */
public final class JeiIngredientTypes {
    public static final JeiIngredientTypes GLOBAL = builtins();
    private final Map<IIngredientType<?>, IngredientType<?>> types = new HashMap<>();
    private final Set<ResourceLocation> ids = new HashSet<>();

    private static JeiIngredientTypes builtins() {
        var registry = new JeiIngredientTypes();
        registry.register(VanillaTypes.ITEM_STACK, IngredientTypes.ITEM);
        registry.register(NeoForgeTypes.FLUID_STACK, IngredientTypes.FLUID);
        return registry;
    }

    public synchronized <T> void register(IIngredientType<T> source,
        IngredientType<T> target) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        if (!target.valueClass().isAssignableFrom(source.getIngredientClass()))
            throw new IllegalArgumentException(
                "Incompatible ingredient classes: " + target.id());
        if (types.containsKey(source) || ids.contains(target.id()))
            throw new IllegalArgumentException(
                "Duplicate JEI ingredient mapping: " + target.id());
        types.put(source, target);
        ids.add(target.id());
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> Optional<IngredientType<T>> find(
        IIngredientType<T> source) {
        return Optional.ofNullable((IngredientType<T>) types.get(source));
    }
}
