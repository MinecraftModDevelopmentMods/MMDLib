package zone.moddev.mc.lib.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import zone.moddev.mc.lib.common.runtime.MaterialBlockItem;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;

/**
 * The Java code behind a block data module. A module's {@code factory} names an implementation
 * registered with {@link MaterialExtensions#registerBlockImplementation}; the runtime registrar
 * calls {@link #create(BlockContext)} once for every material (or standalone block) that selects
 * the module.
 *
 * <p>Override {@link #create(BlockContext)} for new code. The older {@code create} overloads remain
 * for implementations written before contexts existed.
 *
 * @param <B> the block class created
 */
public interface BlockImplementation<B extends Block> {
  /** The concrete class every {@code create} call returns. */
  Class<B> blockClass();

  /**
   * Creates the block from its material and planned definition. Implementations that override
   * {@link #create(BlockContext)} may throw here instead.
   */
  B create(MMDMaterial material, MMDBlock definition);

  /**
   * As {@link #create(MMDMaterial, MMDBlock)}, with the module's {@code factory_data}. The default
   * refuses any non-empty data, so an implementation that takes settings must override this or
   * {@link #create(BlockContext)} and decode them.
   */
  default B create(
      MMDMaterial material, MMDBlock definition, zone.moddev.mc.lib.common.types.FactoryData data) {
    if (!data.equals(zone.moddev.mc.lib.common.types.FactoryData.EMPTY))
      throw new IllegalArgumentException("Implementation must explicitly decode its factory_data");
    return create(material, definition);
  }

  /**
   * Called by the runtime registrar; override to use the prepared properties, block set type, or
   * base block.
   */
  default B create(BlockContext context) {
    return create(context.material(), context.definition(), context.factoryData());
  }

  /**
   * Decode {@code factory_data} eagerly so malformed module data fails at load instead of registry
   * construction.
   */
  default void validate(MaterialPart part) {}

  /**
   * The block's item, when its definition has one; the default burns for the material's {@code
   * block_burn_time}.
   */
  default Item createItem(Block block, ItemContext context) {
    return new MaterialBlockItem(block, context.properties(), context.material());
  }
}
