package zone.moddev.mc.lib.api;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import zone.moddev.mc.lib.common.factories.BlockEntityFactories;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;

/**
 * The block entity the runtime registrar queued for a block, handed to its {@link
 * BlockImplementation} through {@link BlockContext#blockEntity()}. The block must implement {@code
 * EntityBlock} and delegate {@code newBlockEntity} and {@code getTicker} to {@link #prepared()}, as
 * {@link MaterialEntityBlock} does; {@link #newBlock} builds one.
 *
 * @param prepared the block-entity factory with its decoded settings
 * @param type supplies the registered block-entity type; read it only once registration has run,
 *     e.g. when a block entity is created, never in the block's constructor
 * @param <E> the block-entity class
 */
public record BlockEntityBinding<E extends BlockEntity>(
    BlockEntityFactories.Prepared<E> prepared, Supplier<BlockEntityType<E>> type) {
  /** Requires both components. */
  public BlockEntityBinding {
    Objects.requireNonNull(prepared);
    Objects.requireNonNull(type);
  }

  /** The class every block entity of this binding has. */
  public Class<E> blockEntityClass() {
    return prepared.blockEntityClass();
  }

  /**
   * This binding typed for {@code expected}, for a block class written for one block-entity class.
   *
   * @throws IllegalArgumentException if the factory creates a different class
   */
  @SuppressWarnings("unchecked")
  public <T extends BlockEntity> BlockEntityBinding<T> as(Class<T> expected) {
    if (prepared.blockEntityClass() != expected)
      throw new IllegalArgumentException(
          "Block entity "
              + prepared.definition().name()
              + " is "
              + prepared.blockEntityClass().getName()
              + ", not "
              + expected.getName());
    return (BlockEntityBinding<T>) this;
  }

  /** A plain machine block with {@code properties} whose block entity comes from this binding. */
  public MaterialEntityBlock<E> newBlock(BlockBehaviour.Properties properties) {
    return new MaterialEntityBlock<>(properties, prepared, type);
  }
}
