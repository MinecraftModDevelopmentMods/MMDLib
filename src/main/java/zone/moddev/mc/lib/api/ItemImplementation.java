package zone.moddev.mc.lib.api;

import net.minecraft.world.item.Item;

import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;

/**
 * The Java code behind an item data module. A module's {@code factory} names an implementation
 * registered with {@link MaterialExtensions#registerItemImplementation}; the runtime registrar
 * calls {@link #create(ItemContext)} once for every material that selects the module.
 *
 * <p>Override {@link #create(ItemContext)} for new code. The older {@code create} overloads remain
 * for implementations written before contexts existed.
 *
 * @param <I> the item class created
 */
public interface ItemImplementation<I extends Item> {
  /** The concrete class every {@code create} call returns. */
  Class<I> itemClass();

  /**
   * Creates the item from its material and planned definition. Implementations that override {@link
   * #create(ItemContext)} may throw here instead.
   */
  I create(MMDMaterial material, MMDItem definition);

  /**
   * As {@link #create(MMDMaterial, MMDItem)}, with the module's {@code factory_data}. The default
   * refuses any non-empty data, so an implementation that takes settings must override this or
   * {@link #create(ItemContext)} and decode them.
   */
  default I create(
      MMDMaterial material, MMDItem definition, zone.moddev.mc.lib.common.types.FactoryData data) {
    if (!data.equals(zone.moddev.mc.lib.common.types.FactoryData.EMPTY))
      throw new IllegalArgumentException("Implementation must explicitly decode its factory_data");
    return create(material, definition);
  }

  /**
   * Called by the runtime registrar; override to use the material's tier, armor, projectile, or
   * prepared properties.
   */
  default I create(ItemContext context) {
    return create(context.material(), context.definition(), context.factoryData());
  }

  /**
   * Decode {@code factory_data} eagerly so malformed module data fails at load instead of registry
   * construction.
   */
  default void validate(MaterialPart part) {}

  /**
   * Whether {@link ItemContext#armor()} is needed; the material's armor material is registered only
   * when some part asks.
   */
  default boolean usesArmorMaterial() {
    return false;
  }
}
