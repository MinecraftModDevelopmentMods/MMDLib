package zone.moddev.mc.lib.api;

import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import zone.moddev.mc.lib.common.runtime.MaterialProjectile;
import zone.moddev.mc.lib.common.runtime.MaterialToolTier;
import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;

/**
 * Everything the runtime registrar knows when it creates one material item. Passed to
 * {@link ItemImplementation#create(ItemContext)}; MMDLib's own items are built from exactly this.
 */
public interface ItemContext {
    MMDMaterial material();
    MMDItem definition();
    MaterialPart part();
    default FactoryData factoryData() { return part().factoryData(); }
    /** Fresh properties from the definition (stack size, durability, fire resistance, container). */
    Item.Properties properties();
    /** The material's tool tier: durability, speed, damage, enchantability, repair item, incorrect-block tag. */
    MaterialToolTier tier();
    /** The material's armor material; available only when the implementation reports {@link ItemImplementation#usesArmorMaterial()}. */
    Holder<ArmorMaterial> armor();
    /** The projectile type registered for this part's {@code entity_factory}; available only for parts that declare one. */
    Supplier<EntityType<MaterialProjectile>> projectile();
}
