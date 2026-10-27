package zone.moddev.mc.lib.common.runtime;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class MaterialAmmoItem extends ArrowItem implements MaterialBacked {
    private final MMDMaterial material;
    private final Supplier<EntityType<MaterialProjectile>> type;
    public MaterialAmmoItem(MMDMaterial material, Supplier<EntityType<MaterialProjectile>> type, Properties properties) {
        super(properties); this.material = material; this.type = type;
    }
    @Override public MMDMaterial material() { return material; }
    @Override public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter, @Nullable ItemStack weapon) {
        var projectile = new MaterialProjectile(type.get(), level, shooter, stack, weapon);
        projectile.setBaseDamage(1 + new MaterialStats(material).baseAttackDamage());
        return projectile;
    }
}
