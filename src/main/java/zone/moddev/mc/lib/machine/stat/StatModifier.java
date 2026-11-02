package zone.moddev.mc.lib.machine.stat;

import java.util.Objects;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * A change to one stat from an installed part, an upgrade or a card, using vanilla's attribute
 * modifier operations.
 */
public record StatModifier(String stat, AttributeModifier.Operation op, double amount) {
  /**
   * @throws IllegalArgumentException if {@code amount} is not finite
   */
  public StatModifier {
    Objects.requireNonNull(stat, "stat");
    Objects.requireNonNull(op, "op");
    if (!Double.isFinite(amount))
      throw new IllegalArgumentException("Modifier amount for " + stat + " is not finite");
  }
}
