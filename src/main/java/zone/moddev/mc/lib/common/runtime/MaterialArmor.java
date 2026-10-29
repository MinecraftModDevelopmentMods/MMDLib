package zone.moddev.mc.lib.common.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.common.types.MMDMaterial;

/** Builds a material's vanilla {@link ArmorMaterial} from its stats ({@link MaterialStats}). */
public final class MaterialArmor {
  private MaterialArmor() {}

  /**
   * Queues the material's armor material, named after the material, on {@code registry}.
   *
   * @param repair supplies the repair ingredient
   * @param textureLayer the armor texture layer
   * @throws IllegalArgumentException if the register's namespace differs from the material's
   */
  public static DeferredHolder<ArmorMaterial, ArmorMaterial> register(
      DeferredRegister<ArmorMaterial> registry,
      MMDMaterial material,
      Supplier<Ingredient> repair,
      ResourceLocation textureLayer) {
    var id = material.name().orElseThrow();
    if (!registry.getNamespace().equals(id.getNamespace()))
      throw new IllegalArgumentException("Armor registry namespace differs from material");
    return registry.register(id.getPath(), () -> create(material, repair, textureLayer));
  }

  /**
   * Creates the armor material: protection per slot (horse armor included), enchantability,
   * toughness and texture layer; no knockback resistance.
   */
  public static ArmorMaterial create(
      MMDMaterial material, Supplier<Ingredient> repair, ResourceLocation textureLayer) {
    var s = new MaterialStats(material);
    return new ArmorMaterial(
        Map.of(
            ArmorItem.Type.HELMET,
            s.armorProtection(EquipmentSlot.HEAD),
            ArmorItem.Type.CHESTPLATE,
            s.armorProtection(EquipmentSlot.CHEST),
            ArmorItem.Type.LEGGINGS,
            s.armorProtection(EquipmentSlot.LEGS),
            ArmorItem.Type.BOOTS,
            s.armorProtection(EquipmentSlot.FEET),
            ArmorItem.Type.BODY,
            s.horseArmorProtection()),
        s.enchantability(),
        SoundEvents.ARMOR_EQUIP_IRON,
        repair,
        List.of(new ArmorMaterial.Layer(textureLayer, "", material.runtime().tintedArmor())),
        s.armorToughness(),
        0);
  }
}
