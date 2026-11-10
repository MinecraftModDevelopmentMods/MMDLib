package zone.moddev.mc.lib.gui.client;

import java.util.Objects;
import java.util.function.UnaryOperator;

import net.minecraft.resources.ResourceLocation;

/** Client ingredient identity and defensive copying, independent of any recipe viewer. */
public record IngredientType<T>(ResourceLocation id, Class<T> valueClass, UnaryOperator<T> copy) {
  public IngredientType {
    Objects.requireNonNull(id);
    Objects.requireNonNull(valueClass);
    Objects.requireNonNull(copy);
  }

  /** A detached ingredient; providers must never receive the viewer's mutable original. */
  public T snapshot(T value) {
    return valueClass.cast(Objects.requireNonNull(copy.apply(valueClass.cast(value))));
  }
}
