package zone.moddev.mc.lib.api;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;

import net.neoforged.neoforge.registries.*;

import zone.moddev.mc.lib.common.factories.BlockEntityFactories;

/**
 * Prepared machine type. Its owning mod explicitly supplies the register; MMDLib wires the result.
 */
public final class BlockEntityHandle<E extends BlockEntity>
    implements Supplier<BlockEntityType<E>> {
  private final BlockEntityFactories factories;
  private final BlockEntityFactories.Prepared<E> prepared;
  private final Supplier<? extends Block> block;
  private DeferredHolder<BlockEntityType<?>, BlockEntityType<E>> holder;

  public BlockEntityHandle(
      BlockEntityFactories factories,
      BlockEntityFactories.Prepared<E> prepared,
      Supplier<? extends Block> block) {
    this.factories = Objects.requireNonNull(factories);
    this.prepared = Objects.requireNonNull(prepared);
    this.block = Objects.requireNonNull(block);
  }

  public BlockEntityFactories.Prepared<E> prepared() {
    return prepared;
  }

  public synchronized DeferredHolder<BlockEntityType<?>, BlockEntityType<E>> register(
      DeferredRegister<BlockEntityType<?>> register) {
    if (holder != null)
      throw new IllegalStateException(
          "Block entity already registered: " + prepared.definition().name());
    holder = factories.queue(register, prepared, block);
    return holder;
  }

  public Optional<DeferredHolder<BlockEntityType<?>, BlockEntityType<E>>> holder() {
    return Optional.ofNullable(holder);
  }

  public void verifyRegistered() {
    if (holder == null || !holder.isBound())
      throw new IllegalStateException(
          "Unregistered block entity handle: "
              + prepared.definition().name()
              + "; pass the owning mod's block-entity register to handle.register(register)");
  }

  @Override
  public BlockEntityType<E> get() {
    verifyRegistered();
    return holder.get();
  }

  public BlockEntityBinding<E> binding() {
    return new BlockEntityBinding<>(prepared, this);
  }
}
