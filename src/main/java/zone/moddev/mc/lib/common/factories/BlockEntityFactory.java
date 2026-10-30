package zone.moddev.mc.lib.common.factories;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.common.types.MMDBlockEntity;

/**
 * The Java side of a data-defined machine: one implementation, registered once in a {@link
 * BlockEntityFactories} catalog, serves every block-entity definition that names its ID. Each
 * definition carries its own settings, decoded with {@link #settingsCodec()} when definitions load,
 * so several data variants can share one implementation. See {@code docs/block-entities.md}.
 *
 * <p>Implementations should be stateless; per-machine state belongs in the block entity, and
 * per-variant configuration in the settings.
 *
 * @param <E> the block-entity class this factory creates
 * @param <S> the decoded settings type, shared by every block entity of one definition
 */
public interface BlockEntityFactory<E extends BlockEntity, S> {
  /**
   * The exact class {@link #create} returns. {@link BlockEntityFactories#prepare} refuses a caller
   * that expects a different class, and every created block entity is cast to it.
   *
   * @return the block-entity class; never null
   */
  Class<E> blockEntityClass();

  /**
   * Decodes a definition's {@code factory_data} into settings. It runs when definitions load,
   * before any registry exists, so invalid data fails at startup naming the definition. Settings
   * that refer to registry contents can be decoded through {@code RegistryOps} with {@link
   * BlockEntityFactories#prepare(MMDBlockEntity, Class, com.mojang.serialization.DynamicOps)}.
   *
   * @return the settings codec; never null
   */
  Codec<S> settingsCodec();

  /**
   * Creates a block entity, for its block's {@code newBlockEntity}. The result must be constructed
   * with exactly the given {@code type}, {@code position} and {@code state}; {@link
   * BlockEntityFactories.Prepared#create} checks this and throws if it was not.
   *
   * @param type the registered block-entity type built for {@code definition}
   * @param position where the block entity is being placed
   * @param state the block state at {@code position}; always valid for {@code type}
   * @param definition the block-entity definition being created
   * @param settings the settings decoded from {@code definition}
   * @return a new block entity; never null
   */
  E create(
      BlockEntityType<E> type,
      BlockPos position,
      BlockState state,
      MMDBlockEntity definition,
      S settings);

  /**
   * The ticker for block entities of this definition in {@code level}. It is called whenever
   * Minecraft asks the block for a ticker: when a block entity is added to a loaded chunk, or its
   * block state changes. Each logical side asks separately, so it can return a different ticker, or
   * none, for the client and the server; check {@code level.isClientSide()} to pick the side. The
   * default does not tick.
   *
   * <p>The returned ticker is wrapped so it is only installed for this definition's own type and
   * receives block entities already cast to {@code E}.
   *
   * @param level the level the block entity is in; its side decides which ticker to return
   * @param definition the block-entity definition
   * @param settings the settings decoded from {@code definition}
   * @return the ticker, or null for a machine that does not tick on this side
   */
  default BlockEntityTicker<E> ticker(Level level, MMDBlockEntity definition, S settings) {
    return null;
  }
}
