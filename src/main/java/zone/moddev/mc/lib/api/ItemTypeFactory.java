package zone.moddev.mc.lib.api;

import net.minecraft.world.item.Item;

import zone.moddev.mc.lib.common.builders.ItemBuilder;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/**
 * A code-registered item type: a material part defined in Java instead of a data module. Registered
 * with {@link MaterialExtensions#registerItemType}, it can then be selected in a material's {@code
 * items} section like any other part, and is created with {@link MaterialExtensions#queueItem}.
 *
 * <p>New content should normally prefer a data module with an {@link ItemImplementation}; this
 * interface is for parts whose template depends on code.
 *
 * @param <I> the item class created
 */
public interface ItemTypeFactory<I extends Item> extends ItemImplementation<I> {
  /** The concrete class every {@link #create} call returns; checked when queueing. */
  Class<I> itemClass();

  /**
   * The item definition template for {@code material}, before MMDLib sets its name, material and
   * type. May use the material's stats.
   */
  ItemBuilder template(MMDMaterial material);

  /**
   * Creates a fresh item for {@code material} from its planned {@code definition}. Called by the
   * deferred register supplier, once per registration.
   */
  I create(MMDMaterial material, MMDItem definition);
}
