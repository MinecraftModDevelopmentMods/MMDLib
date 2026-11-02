package zone.moddev.mc.lib.machine.internals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.machine.expr.Expr;

/**
 * A machine part file from {@code data/<ns>/mmdlib/machine_part/}. It names either a material part
 * ({@code "part": "mmdlib:gear"}, a template for that part of every material) or one item ({@code
 * "item": "example:diamond_coated_gear"}), the socket the part goes into, its stat modifiers, and
 * optionally a durability. Amounts may use {@code $material.hardness}, {@code strength}, {@code
 * magic}, {@code damage} and {@code $material.special.<key>}.
 *
 * @param target the material part ID on the left, or the item ID on the right
 * @param durability wear points before the part breaks; empty for a part that never wears
 */
public record PartDefinition(
    Either<ResourceLocation, ResourceLocation> target,
    String socket,
    List<PartModifier> modifiers,
    Optional<Expr> durability) {
  private static final Set<String> MATERIAL_VARIABLES =
      Set.of("material.hardness", "material.strength", "material.magic", "material.damage");
  private static final String SPECIAL = "material.special.";

  /**
   * The JSON form; exactly one of {@code part} or {@code item}, only material variables, and no
   * other keys.
   */
  public static final Codec<PartDefinition> CODEC =
      StrictKeys.of(
          RecordCodecBuilder.<Raw>create(
                  instance ->
                      instance
                          .group(
                              ResourceLocation.CODEC.optionalFieldOf("part").forGetter(Raw::part),
                              ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Raw::item),
                              Codec.STRING.fieldOf("socket").forGetter(Raw::socket),
                              PartModifier.CODEC
                                  .listOf()
                                  .fieldOf("modifiers")
                                  .forGetter(Raw::modifiers),
                              Expr.CODEC.optionalFieldOf("durability").forGetter(Raw::durability))
                          .apply(instance, Raw::new))
              .comapFlatMap(Raw::toDefinition, Raw::of),
          "part",
          "item",
          "socket",
          "modifiers",
          "durability");

  /** Copies the modifier list. */
  public PartDefinition {
    modifiers = List.copyOf(modifiers);
  }

  /** Every variable the amounts and durability use, without {@code $}. */
  public Set<String> variables() {
    var names = new TreeSet<String>();
    modifiers.forEach(modifier -> names.addAll(modifier.amount().variables()));
    durability.ifPresent(expr -> names.addAll(expr.variables()));
    return names;
  }

  private record Raw(
      Optional<ResourceLocation> part,
      Optional<ResourceLocation> item,
      String socket,
      List<PartModifier> modifiers,
      Optional<Expr> durability) {
    static Raw of(PartDefinition definition) {
      return new Raw(
          definition.target.left(),
          definition.target.right(),
          definition.socket,
          definition.modifiers,
          definition.durability);
    }

    DataResult<PartDefinition> toDefinition() {
      if (part.isPresent() == item.isPresent())
        return DataResult.error(() -> "A machine part file needs exactly one of 'part' or 'item'");
      if (socket.isBlank()) return DataResult.error(() -> "A machine part file needs a socket");
      var definition =
          new PartDefinition(
              part.<Either<ResourceLocation, ResourceLocation>>map(Either::left)
                  .orElseGet(() -> Either.right(item.orElseThrow())),
              socket,
              modifiers,
              durability);
      for (var name : definition.variables())
        if (!MATERIAL_VARIABLES.contains(name)
            && !(name.startsWith(SPECIAL) && name.length() > SPECIAL.length()))
          return DataResult.error(
              () ->
                  "Unknown variable $"
                      + name
                      + "; part files may use $material.hardness, strength, magic, damage and"
                      + " special.<key>");
      return DataResult.success(definition);
    }
  }
}
