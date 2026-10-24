package zone.moddev.mc.lib.common.runtime;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import java.util.Objects;
import java.util.function.Supplier;

/** The caller supplies the actual 1.21 incorrect-blocks tag and repair ingredient. */
public final class MaterialToolTier implements Tier {
    private final MaterialStats stats;
    private final TagKey<Block> incorrectBlocks;
    private final Supplier<Ingredient> repair;
    public MaterialToolTier(MMDMaterial material, TagKey<Block> incorrectBlocks, Supplier<Ingredient> repair) {
        stats = new MaterialStats(Objects.requireNonNull(material));
        this.incorrectBlocks = Objects.requireNonNull(incorrectBlocks); this.repair = Objects.requireNonNull(repair);
    }
    public MMDMaterial material() { return stats.material(); }
    @Override public int getUses() { return stats.toolDurability(); }
    @Override public float getSpeed() { return stats.toolEfficiency(); }
    @Override public float getAttackDamageBonus() { return stats.baseAttackDamage(); }
    @Override public int getEnchantmentValue() { return stats.enchantability(); }
    @Override public TagKey<Block> getIncorrectBlocksForDrops() { return incorrectBlocks; }
    @Override public Ingredient getRepairIngredient() { return Objects.requireNonNull(repair.get()); }
}
