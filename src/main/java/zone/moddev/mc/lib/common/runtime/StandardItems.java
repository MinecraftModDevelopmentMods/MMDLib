package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.Holder;
import net.minecraft.world.item.*;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;
import java.util.Objects;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

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
    /**
     * Data-configured tier tool; factory_data supplies its mining tags, attributes, scaled stats, and abilities.
     * Behavior beyond mining (e.g. right-click) comes only from the item definition's event bindings.
     */
    public static class Tool extends TieredItem implements MaterialBacked {
        private final MMDMaterial material;
        private final java.util.Set<ItemAbility> abilities;
        public Tool(MaterialToolTier tier, ToolSettings settings, Item.Properties properties) {
            super(tier, settings.apply(tier, properties));
            material = tier.material();
            abilities = settings.abilities();
        }
        @Override public MMDMaterial material() { return material; }
        @Override public boolean canPerformAction(ItemStack stack, ItemAbility action) { return abilities.contains(action); }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) { return true; }
        @Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
        }
    }
    /** Stat-backed value: tool_factor scales the tier's tool stat (not below minimum); stat names a per-material override. */
    public record ScaledStat(Optional<String> stat, double toolFactor, double minimum) {
        public static final Codec<ScaledStat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("stat").forGetter(ScaledStat::stat),
                Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("tool_factor", 1D).forGetter(ScaledStat::toolFactor),
                Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("minimum", 0D).forGetter(ScaledStat::minimum)
        ).apply(i, ScaledStat::new));
        public static final ScaledStat TOOL = new ScaledStat(Optional.empty(), 1, 0);
    }
    /**
     * mmdlib:tool factory_data. Blocks in mineable are correct for drops; blocks in fast_on (default: mineable)
     * also mine at the scaled speed. Blocks in the tier's incorrect tag never drop and mine at base speed.
     */
    public record ToolSettings(TagKey<Block> mineable, Optional<TagKey<Block>> fastOn, float attackDamage, float attackSpeed,
            ScaledStat durability, ScaledStat speed, java.util.Set<ItemAbility> abilities) {
        private static final Codec<java.util.Set<ItemAbility>> ABILITIES = Codec.STRING.listOf().xmap(names -> {
            var result = new java.util.HashSet<ItemAbility>();
            for (String name : names) switch (name) {
                case "pickaxe" -> result.addAll(ItemAbilities.DEFAULT_PICKAXE_ACTIONS);
                case "axe" -> result.addAll(ItemAbilities.DEFAULT_AXE_ACTIONS);
                case "shovel" -> result.addAll(ItemAbilities.DEFAULT_SHOVEL_ACTIONS);
                case "hoe" -> result.addAll(ItemAbilities.DEFAULT_HOE_ACTIONS);
                case "sword" -> result.addAll(ItemAbilities.DEFAULT_SWORD_ACTIONS);
                case "shears" -> result.addAll(ItemAbilities.DEFAULT_SHEARS_ACTIONS);
                default -> result.add(ItemAbility.get(name));
            }
            return java.util.Set.copyOf(result);
        }, abilities -> abilities.stream().map(ItemAbility::name).sorted().toList());
        public static final Codec<ToolSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.codec(Registries.BLOCK).fieldOf("mineable").forGetter(ToolSettings::mineable),
                TagKey.codec(Registries.BLOCK).optionalFieldOf("fast_on").forGetter(ToolSettings::fastOn),
                Codec.FLOAT.optionalFieldOf("attack_damage", 0F).forGetter(ToolSettings::attackDamage),
                Codec.FLOAT.optionalFieldOf("attack_speed", -2.8F).forGetter(ToolSettings::attackSpeed),
                ScaledStat.CODEC.optionalFieldOf("durability", ScaledStat.TOOL).forGetter(ToolSettings::durability),
                ScaledStat.CODEC.optionalFieldOf("speed", ScaledStat.TOOL).forGetter(ToolSettings::speed),
                ABILITIES.optionalFieldOf("abilities", java.util.Set.of()).forGetter(ToolSettings::abilities)
        ).apply(i, ToolSettings::new));
        public static ToolSettings of(MaterialPart part) {
            return part.factoryData().decode(CODEC).getOrThrow(message ->
                    new IllegalArgumentException("Invalid mmdlib:tool factory_data for " + part.templateId() + ": " + message));
        }
        Item.Properties apply(MaterialToolTier tier, Item.Properties properties) {
            var stats = new MaterialStats(tier.material());
            int uses = durability.stat().map(key -> stats.scaledDurability(key, durability.toolFactor()))
                    .orElseGet(() -> Math.max(1, (int) (durability.toolFactor() * stats.toolDurability())));
            uses = Math.max(uses, (int) durability.minimum());
            float miningSpeed = speed.stat().map(key -> stats.scaledEfficiency(key, speed.toolFactor(), speed.minimum()))
                    .orElseGet(() -> (float) Math.max(speed.minimum(), speed.toolFactor() * stats.toolEfficiency()));
            var rules = new java.util.ArrayList<net.minecraft.world.item.component.Tool.Rule>();
            // Blocks above the tier neither drop nor mine faster than bare hands: the tool cannot mine them.
            rules.add(new net.minecraft.world.item.component.Tool.Rule(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getOrCreateTag(tier.getIncorrectBlocksForDrops()), Optional.of(1.0F), Optional.of(false)));
            rules.add(net.minecraft.world.item.component.Tool.Rule.minesAndDrops(fastOn.orElse(mineable), miningSpeed));
            if (fastOn.isPresent()) rules.add(new net.minecraft.world.item.component.Tool.Rule(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOrCreateTag(mineable), Optional.empty(), Optional.of(true)));
            return properties.durability(uses)
                    .component(net.minecraft.core.component.DataComponents.TOOL, new net.minecraft.world.item.component.Tool(rules, 1.0F, 1))
                    .attributes(DiggerItem.createAttributes(tier, attackDamage, attackSpeed));
        }
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
    /** A plain material item (ingot, dust, nugget, ...) that burns for the given ticks; 0 means not a fuel. */
    public static class Fuel extends Basic {
        private final int burn;
        public Fuel(MMDMaterial material, int burn, Item.Properties properties) { super(material, () -> net.minecraft.world.item.crafting.Ingredient.EMPTY, properties); this.burn = burn; }
        @Override public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> recipe) { return burn; }
    }
}
