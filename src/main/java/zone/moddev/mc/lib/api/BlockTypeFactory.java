package zone.moddev.mc.lib.api;

import net.minecraft.world.level.block.Block;

import zone.moddev.mc.lib.common.builders.BlockBuilder;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/**
 * A code-registered block type: a material part defined in Java instead of a data module.
 * Registered with {@link MaterialExtensions#registerBlockType}, it can then be selected in a
 * material's {@code blocks} section like any other part, and is created with {@link
 * MaterialExtensions#queueBlock}.
 *
 * <p>New content should normally prefer a data module with a {@link BlockImplementation}; this
 * interface is for parts whose template depends on code.
 *
 * @param <B> the block class created
 */
public interface BlockTypeFactory<B extends Block> extends BlockImplementation<B> {
  /** The concrete class every {@link #create} call returns; checked when queueing. */
  Class<B> blockClass();

  /**
   * The block definition template for {@code material}, before MMDLib sets its name, material and
   * type. May use the material's stats.
   */
  BlockBuilder template(MMDMaterial material);

  /**
   * Creates a fresh block for {@code material} from its planned {@code definition}. Called by the
   * deferred register supplier, once per registration.
   */
  B create(MMDMaterial material, MMDBlock definition);
}
