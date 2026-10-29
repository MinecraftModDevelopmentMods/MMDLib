package zone.moddev.mc.lib.common.runtime;

import javax.annotation.Nullable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** AbstractArrow owns and serializes the exact pickup stack, including components. */
public final class MaterialProjectile extends AbstractArrow {
  /** A projectile created from saved data or by the entity type. */
  public MaterialProjectile(EntityType<? extends MaterialProjectile> type, Level level) {
    super(type, level);
  }

  /** A projectile fired by {@code shooter}; picking it up returns one of {@code ammunition}. */
  public MaterialProjectile(
      EntityType<? extends MaterialProjectile> type,
      Level level,
      LivingEntity shooter,
      ItemStack ammunition,
      @Nullable ItemStack weapon) {
    super(type, shooter, level, ammunition.copyWithCount(1), weapon);
  }

  @Override
  protected ItemStack getDefaultPickupItem() {
    return ItemStack.EMPTY;
  }
}
