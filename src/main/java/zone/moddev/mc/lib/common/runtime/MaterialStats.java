package zone.moddev.mc.lib.common.runtime;

import net.minecraft.world.entity.EquipmentSlot;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/** Legacy BMe stat formulas with explicit special-property overrides; no numeric harvest tiers. */
public record MaterialStats(MMDMaterial material) {
    public int toolDurability() { return integer("tool_durability", Math.max(1, (int) (32 * material.properties().strength())), 1); }
    public float toolEfficiency() { return scalar("tool_efficiency", material.properties().hardness(), 0); }
    public float baseAttackDamage() { return scalar("base_attack_damage", material.properties().damage(), -Float.MAX_VALUE); }
    public int enchantability() { return integer("enchantability", (int) (2.5 * material.properties().magic()), 0); }
    public int armorDurabilityFactor() { return integer("armor_durability_factor", Math.max(1, (int) (2 * material.properties().strength())), 1); }
    public float armorToughness() { return scalar("armor_toughness", material.properties().hardness() > 10 ? (int) (material.properties().hardness() / 5) : 0, 0); }
    public int armorProtection(EquipmentSlot slot) {
        double fraction = switch (slot) { case HEAD -> .1; case CHEST -> .4; case LEGS -> .35; case FEET -> .15; default -> 0; };
        return integer("armor_" + slot.getName(), Math.max(0, Math.min(30, (int) Math.round((1.25 * material.properties().hardness() + 5) * fraction))), 0);
    }
    public int horseArmorProtection() { return integer("horse_armor_protection", Math.max(0, Math.min(30, (int) (material.properties().hardness() / 10 * 11))), 0); }
    /** Tool durability scaled by a data-supplied factor; the key, when present, overrides it per material. */
    public int scaledDurability(String key, double factor) { return integer(key, Math.max(1, (int) (factor * toolDurability())), 1); }
    public float scaledEfficiency(String key, double factor, double floor) { return scalar(key, Math.max(floor, factor * toolEfficiency()), 0); }
    public float axeAttackSpeed() { return scalar("axe_attack_speed", -3.5 + Math.min(.5, .05 * material.properties().strength()), -Float.MAX_VALUE); }
    public int integer(String key, int fallback, int minimum) {
        double value = material.properties().special().getOrDefault(key, (double) fallback);
        if (value < minimum || value > Integer.MAX_VALUE || value != Math.rint(value))
            throw new IllegalArgumentException("Invalid integer material stat: " + key);
        return (int) value;
    }
    public float scalar(String key, double fallback, float minimum) {
        double value = material.properties().special().getOrDefault(key, fallback);
        if (!Double.isFinite(value) || value < minimum || value > Float.MAX_VALUE)
            throw new IllegalArgumentException("Invalid material stat: " + key);
        return (float) value;
    }
}
