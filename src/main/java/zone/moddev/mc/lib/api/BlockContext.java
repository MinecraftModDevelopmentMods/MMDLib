package zone.moddev.mc.lib.api;

import java.util.function.Supplier;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;

/**
 * Everything the runtime registrar knows when it creates one material block. Passed to {@link
 * BlockImplementation#create(BlockContext)}; MMDLib's own blocks are built from exactly this.
 */
public interface BlockContext {
  /**
   * The material the block belongs to; for a standalone block, a stats-free host material named
   * after the block.
   */
  MMDMaterial material();

  /** The planned block definition: registry name, template properties and event bindings. */
  MMDBlock definition();

  /** The part being built, with its module ID and metadata. */
  MaterialPart part();

  /** The module's {@code factory_data}, merged with any per-use overrides. */
  default FactoryData factoryData() {
    return part().factoryData();
  }

  /**
   * Fresh properties from the definition and the material's runtime settings (sound, map color).
   */
  BlockBehaviour.Properties properties();

  /** {@code factory_data.block_set_type} when present, otherwise the material's block set type. */
  BlockSetType blockSetType();

  /** The material's own block set type, ignoring {@code factory_data}. */
  BlockSetType materialBlockSetType();

  /** The material's base block state, resolved lazily (e.g. for stairs). */
  Supplier<BlockState> baseBlock();

  /**
   * The event registry, for blocks that dispatch their definition's bindings (e.g. {@code
   * redstone-changed}).
   */
  MaterialEvents events();

  /**
   * The block entity queued for this block, when its part declares one ({@code part.block_entity}).
   * A block implementation for such a part must create an {@code EntityBlock} wired to it, e.g.
   * with {@link BlockEntityBinding#newBlock}.
   */
  default java.util.Optional<BlockEntityBinding<?>> blockEntity() {
    return java.util.Optional.empty();
  }
}
