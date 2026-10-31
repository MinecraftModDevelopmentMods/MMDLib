package zone.moddev.mc.lib.machine.internals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import zone.moddev.mc.lib.machine.expr.Expr;

/**
 * One stat change in a part file, with an amount that may depend on the part's material. JSON:
 * {@code {"stat": "speed", "op": "add_multiplied_base", "amount": "0.08 * $material.hardness"}}.
 */
public record PartModifier(String stat, AttributeModifier.Operation op, Expr amount) {
  /** The JSON form; {@code op} uses vanilla's attribute operation names. */
  public static final Codec<PartModifier> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.STRING.fieldOf("stat").forGetter(PartModifier::stat),
                      AttributeModifier.Operation.CODEC.fieldOf("op").forGetter(PartModifier::op),
                      Expr.CODEC.fieldOf("amount").forGetter(PartModifier::amount))
                  .apply(instance, PartModifier::new));
}
