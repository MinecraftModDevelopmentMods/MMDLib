package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.Holder;
import net.minecraft.world.item.*;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;
import java.util.Objects;

/** Actual specialized item subclasses, adapted from the supplied BMe implementations. */
public final class StandardItems {
    private StandardItems() {}
    public static class Pickaxe extends PickaxeItem implements MaterialBacked {
        private final MMDMaterial material;
        public Pickaxe(MaterialToolTier tier, Item.Properties properties) {
            super(tier, properties.attributes(PickaxeItem.createAttributes(tier, 1, -2.8F)));
            material = tier.material();
        }
        @Override public MMDMaterial material() { return material; }
    }
    public static class Axe extends AxeItem implements MaterialBacked {
        private final MMDMaterial material;
        public Axe(MaterialToolTier tier, Item.Properties properties) {
            super(tier, properties.attributes(AxeItem.createAttributes(tier, 4 + tier.getAttackDamageBonus(), new MaterialStats(tier.material()).axeAttackSpeed())));
            material = tier.material();
        }
        @Override public MMDMaterial material() { return material; }
    }
    public static class Shovel extends ShovelItem implements MaterialBacked {
        private final MMDMaterial material;
        public Shovel(MaterialToolTier tier, Item.Properties properties) {
            super(tier, properties.attributes(ShovelItem.createAttributes(tier, 1.5F, -3F)));
            material = tier.material();
        }
        @Override public MMDMaterial material() { return material; }
    }
    public static class Hoe extends HoeItem implements MaterialBacked {
        private final MMDMaterial material;
        public Hoe(MaterialToolTier tier, Item.Properties properties) {
            super(tier, properties.attributes(HoeItem.createAttributes(tier, 0, -1F)));
            material = tier.material();
        }
        @Override public MMDMaterial material() { return material; }
    }
    public static class Sword extends SwordItem implements MaterialBacked {
        private final MMDMaterial material;
        public Sword(MaterialToolTier tier, Item.Properties properties) {
            super(tier, properties.attributes(SwordItem.createAttributes(tier, 3, -2.4F)));
            material = tier.material();
        }
        @Override public MMDMaterial material() { return material; }
    }
    public static class Basic extends Item implements MaterialBacked {
        private final MMDMaterial material;
        private final java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair;
        public Basic(MMDMaterial material, java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair, Item.Properties properties) {
            super(properties); this.material = Objects.requireNonNull(material); this.repair = Objects.requireNonNull(repair);
        }
        @Override public MMDMaterial material() { return material; }
        @Override public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
            return repair.get().test(ingredient) || super.isValidRepairItem(damaged, ingredient);
        }
    }
    public static class Bow extends BowItem implements MaterialBacked {
        private final MMDMaterial material;
        private final java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair;
        public Bow(MMDMaterial material, java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair, Item.Properties properties) {
            super(properties); this.material = Objects.requireNonNull(material); this.repair = Objects.requireNonNull(repair);
        }
        @Override public MMDMaterial material() { return material; }
        @Override public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
            return repair.get().test(ingredient) || super.isValidRepairItem(damaged, ingredient);
        }
    }
    public static class Crossbow extends CrossbowItem implements MaterialBacked {
        private final MMDMaterial material;
        private final java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair;
        public Crossbow(MMDMaterial material, java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair, Item.Properties properties) {
            super(properties); this.material = Objects.requireNonNull(material); this.repair = Objects.requireNonNull(repair);
        }
        @Override public MMDMaterial material() { return material; }
        @Override public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
            return repair.get().test(ingredient) || super.isValidRepairItem(damaged, ingredient);
        }
    }
    public static class Shield extends ShieldItem implements MaterialBacked {
        private final MMDMaterial material;
        private final java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair;
        public Shield(MMDMaterial material, java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair, Item.Properties properties) {
            super(properties); this.material = Objects.requireNonNull(material); this.repair = Objects.requireNonNull(repair);
        }
        @Override public MMDMaterial material() { return material; }
        @Override public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
            return repair.get().test(ingredient) || super.isValidRepairItem(damaged, ingredient);
        }
    }
    public static class Shears extends ShearsItem implements MaterialBacked {
        private final MMDMaterial material;
        private final java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair;
        public Shears(MMDMaterial material, java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair, Item.Properties properties) {
            super(properties); this.material = Objects.requireNonNull(material); this.repair = Objects.requireNonNull(repair);
        }
        @Override public MMDMaterial material() { return material; }
        @Override public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
            return repair.get().test(ingredient) || super.isValidRepairItem(damaged, ingredient);
        }
    }
    public static class FishingRod extends FishingRodItem implements MaterialBacked {
        private final MMDMaterial material;
        private final java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair;
        public FishingRod(MMDMaterial material, java.util.function.Supplier<net.minecraft.world.item.crafting.Ingredient> repair, Item.Properties properties) {
            super(properties); this.material = Objects.requireNonNull(material); this.repair = Objects.requireNonNull(repair);
        }
        @Override public MMDMaterial material() { return material; }
        @Override public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
            return repair.get().test(ingredient) || super.isValidRepairItem(damaged, ingredient);
        }
    }
    public static class Armor extends ArmorItem implements MaterialBacked {
        private final MMDMaterial material;
        public Armor(MMDMaterial material, Holder<ArmorMaterial> armor, ArmorItem.Type slot, Item.Properties properties) {
            super(armor, slot, properties.durability(slot.getDurability(new MaterialStats(material).armorDurabilityFactor())));
            this.material = material;
        }
        @Override public MMDMaterial material() { return material; }
    }
    public static class HorseArmor extends AnimalArmorItem implements MaterialBacked {
        private final MMDMaterial material;
        public HorseArmor(MMDMaterial material, Holder<ArmorMaterial> armor, Item.Properties properties) {
            super(armor, AnimalArmorItem.BodyType.EQUESTRIAN, false, properties.stacksTo(1)); this.material = material;
        }
        @Override public MMDMaterial material() { return material; }
    }
    public static Class<? extends Item> itemClass(MaterialPart part) {
        return switch (part.factory().toString()) {
            case "mmdlib:pickaxe" -> Pickaxe.class; case "mmdlib:axe" -> Axe.class; case "mmdlib:shovel" -> Shovel.class;
            case "mmdlib:hoe" -> Hoe.class; case "mmdlib:sword" -> Sword.class; case "mmdlib:scythe" -> ScytheItem.class;
            case "mmdlib:crackhammer" -> CrackhammerItem.class; case "mmdlib:bow" -> Bow.class; case "mmdlib:crossbow" -> Crossbow.class;
            case "mmdlib:armor" -> Armor.class; case "mmdlib:horse_armor" -> HorseArmor.class;
            case "mmdlib:shears" -> Shears.class; case "mmdlib:shield" -> Shield.class; case "mmdlib:fishing_rod" -> FishingRod.class;
            case "mmdlib:basic_item" -> Basic.class;
            default -> throw new IllegalArgumentException("No item runtime implementation installed for " + part.factory());
        };
    }
    public static Item create(MaterialPart part, MaterialToolTier tier, Holder<ArmorMaterial> armor, Item.Properties properties) {
        var material = tier.material();
        return switch (part.factory().toString()) {
            case "mmdlib:pickaxe" -> new Pickaxe(tier, properties); case "mmdlib:axe" -> new Axe(tier, properties);
            case "mmdlib:shovel" -> new Shovel(tier, properties); case "mmdlib:hoe" -> new Hoe(tier, properties); case "mmdlib:sword" -> new Sword(tier, properties);
            case "mmdlib:scythe" -> new ScytheItem(material, tier, properties);
            case "mmdlib:crackhammer" -> new CrackhammerItem(material, tier, properties);
            case "mmdlib:armor" -> new Armor(material, Objects.requireNonNull(armor), part.factoryData()
                    .decode(ArmorItem.Type.CODEC.fieldOf("slot").codec()).getOrThrow(), properties);
            case "mmdlib:horse_armor" -> new HorseArmor(material, Objects.requireNonNull(armor), properties);
            case "mmdlib:bow" -> new Bow(material, tier::getRepairIngredient, properties.durability(tier.getUses()));
            case "mmdlib:crossbow" -> new Crossbow(material, tier::getRepairIngredient, properties.durability(tier.getUses()));
            case "mmdlib:shield" -> new Shield(material, tier::getRepairIngredient, properties.durability(
                    new MaterialStats(material).integer("shield_durability", Math.max(1, (int) (168 * material.properties().strength())), 1)));
            case "mmdlib:shears" -> new Shears(material, tier::getRepairIngredient, properties.durability(tier.getUses()));
            case "mmdlib:fishing_rod" -> new FishingRod(material, tier::getRepairIngredient, properties.durability(tier.getUses()));
            case "mmdlib:basic_item" -> new Basic(material, tier::getRepairIngredient, properties);
            default -> throw new IllegalArgumentException("No item runtime implementation installed for " + part.factory());
        };
    }
}
