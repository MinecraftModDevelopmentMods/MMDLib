package zone.moddev.mc.lib.common.runtime;

import net.minecraft.world.entity.EquipmentSlot;

import zone.moddev.mc.lib.common.types.MMDMaterial;

/**
 * Game values derived from a material's stats, using the BaseMetals formulas. Every value can be
 * overridden per material by a key in its {@code special} map, named after the method ({@code
 * tool_durability}, {@code armor_toughness}, ...); see {@code docs/runtime-behaviors.md}. There are
 * no numeric harvest levels: mining tiers come from tags.
 */
public record MaterialStats(MMDMaterial material) {
  /** Tool uses: {@code 32 * strength}, at least 1. Override: {@code tool_durability}. */
  public int toolDurability() {
    return integer(
        "tool_durability", Math.max(1, (int) (32 * material.properties().strength())), 1);
  }

  /** Mining speed: {@code hardness}. Override: {@code tool_efficiency}. */
  public float toolEfficiency() {
    return scalar("tool_efficiency", material.properties().hardness(), 0);
  }

  /** The tier's attack-damage bonus: {@code damage}. Override: {@code base_attack_damage}. */
  public float baseAttackDamage() {
    return scalar("base_attack_damage", material.properties().damage(), -Float.MAX_VALUE);
  }

  /** Enchantability: {@code floor(2.5 * magic)}. Override: {@code enchantability}. */
  public int enchantability() {
    return integer("enchantability", (int) (2.5 * material.properties().magic()), 0);
  }

  /**
   * Armor durability multiplier: {@code 2 * strength}, at least 1. Override: {@code
   * armor_durability_factor}.
   */
  public int armorDurabilityFactor() {
    return integer(
        "armor_durability_factor", Math.max(1, (int) (2 * material.properties().strength())), 1);
  }

  /**
   * Armor toughness: {@code hardness / 5} for materials harder than 10, otherwise 0. Override:
   * {@code armor_toughness}.
   */
  public float armorToughness() {
    return scalar(
        "armor_toughness",
        material.properties().hardness() > 10 ? (int) (material.properties().hardness() / 5) : 0,
        0);
  }

  /**
   * Armor points for one slot: {@code (1.25 * hardness + 5)} split 10% helmet, 40% chestplate, 35%
   * leggings, 15% boots, capped at 30. Override: {@code armor_head}, {@code armor_chest}, {@code
   * armor_legs}, {@code armor_feet}.
   */
  public int armorProtection(EquipmentSlot slot) {
    double fraction =
        switch (slot) {
          case HEAD -> .1;
          case CHEST -> .4;
          case LEGS -> .35;
          case FEET -> .15;
          default -> 0;
        };
    return integer(
        "armor_" + slot.getName(),
        Math.max(
            0,
            Math.min(
                30, (int) Math.round((1.25 * material.properties().hardness() + 5) * fraction))),
        0);
  }

  /**
   * Horse armor points: {@code hardness * 1.1}, capped at 30. Override: {@code
   * horse_armor_protection}.
   */
  public int horseArmorProtection() {
    return integer(
        "horse_armor_protection",
        Math.max(0, Math.min(30, (int) (material.properties().hardness() / 10 * 11))),
        0);
  }

  /**
   * Tool durability scaled by a data-supplied factor; the key, when present, overrides it per
   * material.
   */
  public int scaledDurability(String key, double factor) {
    return integer(key, Math.max(1, (int) (factor * toolDurability())), 1);
  }

  /**
   * Mining speed scaled by a data-supplied factor, at least {@code floor}; {@code key}, when
   * present, overrides it per material.
   */
  public float scaledEfficiency(String key, double factor, double floor) {
    return scalar(key, Math.max(floor, factor * toolEfficiency()), 0);
  }

  /**
   * Axe attack speed: {@code -3.5 + min(0.5, 0.05 * strength)}. Override: {@code axe_attack_speed}.
   */
  public float axeAttackSpeed() {
    return scalar(
        "axe_attack_speed",
        -3.5 + Math.min(.5, .05 * material.properties().strength()),
        -Float.MAX_VALUE);
  }

  /**
   * A whole-number stat: {@code special[key]} if present, otherwise {@code fallback}.
   *
   * @throws IllegalArgumentException if the value is below {@code minimum} or not a whole number
   */
  public int integer(String key, int fallback, int minimum) {
    double value = material.properties().special().getOrDefault(key, (double) fallback);
    if (value < minimum || value > Integer.MAX_VALUE || value != Math.rint(value))
      throw new IllegalArgumentException("Invalid integer material stat: " + key);
    return (int) value;
  }

  /**
   * A decimal stat: {@code special[key]} if present, otherwise {@code fallback}.
   *
   * @throws IllegalArgumentException if the value is not finite or below {@code minimum}
   */
  public float scalar(String key, double fallback, float minimum) {
    double value = material.properties().special().getOrDefault(key, fallback);
    if (!Double.isFinite(value) || value < minimum || value > Float.MAX_VALUE)
      throw new IllegalArgumentException("Invalid material stat: " + key);
    return (float) value;
  }
}
