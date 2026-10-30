package zone.moddev.mc.lib.common.factories;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.google.gson.JsonElement;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.common.types.MMDBlockEntity;

/**
 * A catalog of {@link BlockEntityFactory} implementations, keyed by the IDs block-entity
 * definitions name. {@code Registries.BLOCK_ENTITY_FACTORIES} is the shared catalog; a mod may keep
 * its own. See {@code docs/block-entities.md}.
 */
public final class BlockEntityFactories {
  private final ConcurrentHashMap<ResourceLocation, BlockEntityFactory<?, ?>> factories =
      new ConcurrentHashMap<>();

  /** An empty catalog. */
  public BlockEntityFactories() {}

  /**
   * Registers a factory under {@code id}.
   *
   * @throws IllegalArgumentException if the ID is taken
   */
  public <E extends BlockEntity, S> void register(
      ResourceLocation id, BlockEntityFactory<E, S> factory) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(factory);
    Objects.requireNonNull(factory.blockEntityClass());
    Objects.requireNonNull(factory.settingsCodec());
    if (factories.putIfAbsent(id, factory) != null)
      throw new IllegalArgumentException("Duplicate block entity factory: " + id);
  }

  /**
   * Looks up the factory a definition names and decodes its settings now, so bad data fails at
   * load. Nothing in Minecraft is constructed.
   *
   * @param expectedClass the block-entity class the caller expects; must match the factory's
   * @throws IllegalArgumentException if the factory is unknown, its class differs, or the settings
   *     are invalid
   */
  public <E extends BlockEntity> Prepared<E> prepare(
      MMDBlockEntity definition, Class<E> expectedClass) {
    return prepare(definition, expectedClass, JsonOps.INSTANCE);
  }

  /**
   * As {@link #prepare(MMDBlockEntity, Class)}, decoding the settings with {@code ops}. Pass {@code
   * RegistryOps} when the factory's settings refer to registry contents. Nothing in Minecraft is
   * constructed.
   *
   * @param expectedClass the block-entity class the caller expects; must match the factory's
   * @param ops the ops the settings are decoded with
   * @throws IllegalArgumentException if the factory is unknown, its class differs, or the settings
   *     are invalid
   */
  public <E extends BlockEntity> Prepared<E> prepare(
      MMDBlockEntity definition, Class<E> expectedClass, DynamicOps<JsonElement> ops) {
    var factory = factories.get(definition.factory());
    if (factory == null)
      throw new IllegalArgumentException("Unknown block entity factory: " + definition.factory());
    if (factory.blockEntityClass() != Objects.requireNonNull(expectedClass))
      throw new IllegalArgumentException("Wrong block entity class for " + definition.factory());
    return capture(definition, checked(factory, expectedClass), ops);
  }

  /**
   * As {@link #prepare(MMDBlockEntity, Class)}, for a caller that takes whatever class the named
   * factory declares, such as the runtime registrar.
   *
   * @throws IllegalArgumentException if the factory is unknown or the settings are invalid
   */
  public Prepared<? extends BlockEntity> prepare(MMDBlockEntity definition) {
    var factory = factories.get(definition.factory());
    if (factory == null)
      throw new IllegalArgumentException("Unknown block entity factory: " + definition.factory());
    return prepare(definition, factory.blockEntityClass());
  }

  @SuppressWarnings("unchecked")
  private static <E extends BlockEntity> BlockEntityFactory<E, ?> checked(
      BlockEntityFactory<?, ?> factory, Class<E> expected) {
    return (BlockEntityFactory<E, ?>) factory;
  }

  private static <E extends BlockEntity, S> Prepared<E> capture(
      MMDBlockEntity definition, BlockEntityFactory<E, S> factory, DynamicOps<JsonElement> ops) {
    var settings =
        definition
            .factoryData()
            .decode(factory.settingsCodec(), ops)
            .getOrThrow(
                message ->
                    new IllegalArgumentException(
                        definition.name() + " factory " + definition.factory() + ": " + message));
    return new Prepared<>(definition, factory, settings);
  }

  /**
   * Queues the block-entity type on the mod's register. The type is built when the register's
   * supplier runs; then {@code block} is resolved and checked to be the definition's block.
   *
   * @param registry a register in the definition's namespace
   * @param block supplies the owning block, which must implement {@code EntityBlock}
   * @throws IllegalArgumentException if the register's namespace differs from the definition's
   */
  public <E extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<E>> queue(
      DeferredRegister<BlockEntityType<?>> registry,
      Prepared<E> prepared,
      Supplier<? extends Block> block) {
    if (!registry.getNamespace().equals(prepared.definition().name().getNamespace()))
      throw new IllegalArgumentException(
          "Block entity definition belongs to another registry namespace");
    Objects.requireNonNull(block);
    return registry.register(
        prepared.definition().name().getPath(),
        () -> {
          var actual = Objects.requireNonNull(block.get());
          if (!Objects.equals(
              net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(actual),
              prepared.definition().block()))
            throw new IllegalArgumentException(
                "Block entity definition belongs to another block: "
                    + prepared.definition().block());
          return prepared.buildType(actual);
        });
  }

  /**
   * A block-entity factory with its decoded settings, ready to build the type and to create and
   * tick block entities. A block delegates its {@code newBlockEntity} and {@code getTicker} to
   * {@link #create} and {@link #ticker}, as {@code MaterialEntityBlock} does.
   *
   * @param <E> the block-entity class
   */
  public static final class Prepared<E extends BlockEntity> {
    private final MMDBlockEntity definition;
    private final Class<E> blockEntityClass;
    private final Constructor<E> constructor;
    private final java.util.function.Function<Level, BlockEntityTicker<E>> ticker;

    private <S> Prepared(MMDBlockEntity definition, BlockEntityFactory<E, S> factory, S settings) {
      this.definition = definition;
      this.blockEntityClass = factory.blockEntityClass();
      this.constructor =
          (type, position, state) -> factory.create(type, position, state, definition, settings);
      this.ticker = level -> factory.ticker(level, definition, settings);
    }

    /** The block-entity definition. */
    public MMDBlockEntity definition() {
      return definition;
    }

    /** The class every created block entity has. */
    public Class<E> blockEntityClass() {
      return blockEntityClass;
    }

    /**
     * Creates a block entity, for a block's {@code newBlockEntity}.
     *
     * @throws IllegalArgumentException if {@code state} is not a state of the type's block
     * @throws IllegalStateException if the factory ignored the given type, position or state
     */
    public E create(BlockEntityType<E> type, BlockPos position, BlockState state) {
      if (!type.isValid(state))
        throw new IllegalArgumentException("Unsupported block state for " + definition.name());
      var entity =
          blockEntityClass.cast(
              Objects.requireNonNull(
                  constructor.create(type, position, state), "Block entity factory returned null"));
      if (entity.getType() != type
          || !entity.getBlockPos().equals(position)
          || entity.getBlockState() != state)
        throw new IllegalStateException(
            "Block entity factory must use the supplied type, position and state: "
                + definition.factory());
      return entity;
    }

    /**
     * Builds the block-entity type for {@code block}; its block entities are created through {@link
     * #create}. Call only from the {@code BLOCK_ENTITY_TYPE} registration supplier, after blocks
     * exist; {@link BlockEntityFactories#queue} does this.
     *
     * @throws IllegalArgumentException if {@code block} does not implement {@code EntityBlock}
     */
    public BlockEntityType<E> buildType(Block block) {
      if (!(block instanceof EntityBlock))
        throw new IllegalArgumentException(
            "Block must implement EntityBlock: " + definition.block());
      var holder = new java.util.concurrent.atomic.AtomicReference<BlockEntityType<E>>();
      var type =
          BlockEntityType.Builder.<E>of(
                  (position, state) ->
                      create(Objects.requireNonNull(holder.get()), position, state),
                  block)
              .build(null);
      holder.set(type);
      return type;
    }

    /**
     * The ticker for a block's {@code getTicker}: the factory's {@link BlockEntityFactory#ticker}
     * for {@code level}, adapted to the requested type. Returns null when {@code requested} is not
     * {@code actual}, so the ticker is never installed for another block entity, or when the
     * factory has no ticker on this side.
     *
     * @param level the level asking; its side is passed to the factory
     * @param requested the type Minecraft asks a ticker for
     * @param actual this definition's registered type
     */
    public <T extends BlockEntity> BlockEntityTicker<T> ticker(
        Level level, BlockEntityType<T> requested, BlockEntityType<E> actual) {
      if (requested != actual) return null;
      var action = ticker.apply(level);
      return action == null
          ? null
          : (world, position, state, entity) ->
              action.tick(world, position, state, blockEntityClass.cast(entity));
    }
  }

  @FunctionalInterface
  private interface Constructor<E extends BlockEntity> {
    E create(BlockEntityType<E> type, BlockPos position, BlockState state);
  }
}
