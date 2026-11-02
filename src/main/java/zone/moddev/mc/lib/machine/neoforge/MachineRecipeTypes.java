package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** Registers machine recipe types through a mod's own NeoForge registers. */
public final class MachineRecipeTypes {
  private MachineRecipeTypes() {}

  /**
   * A machine recipe type {@code <namespace>:<name>}, registered as both a recipe type and its
   * serializer. Call it from a static initializer; the registers must be attached to the mod bus.
   *
   * <pre>{@code
   * public static final MachineRecipeType CRUSHING =
   *     MachineRecipeTypes.register(RECIPE_TYPES, RECIPE_SERIALIZERS, "crushing");
   * }</pre>
   *
   * @throws IllegalArgumentException if the two registers have different namespaces
   */
  public static MachineRecipeType register(
      DeferredRegister<RecipeType<?>> types,
      DeferredRegister<RecipeSerializer<?>> serializers,
      String name) {
    if (!types.getNamespace().equals(serializers.getNamespace()))
      throw new IllegalArgumentException(
          "Recipe type and serializer registers differ: "
              + types.getNamespace()
              + ", "
              + serializers.getNamespace());
    var type =
        new MachineRecipeType(ResourceLocation.fromNamespaceAndPath(types.getNamespace(), name));
    types.register(name, () -> type);
    serializers.register(name, type::serializer);
    return type;
  }
}
