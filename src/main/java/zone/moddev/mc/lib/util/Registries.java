package zone.moddev.mc.lib.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDLibEventCallback;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/** Definition registries, registered with NeoForge before content registration. */
public final class Registries {
  /** The key of {@link #MATERIALS_REGISTRY}: {@code mmdlib:materials}. */
  public static final ResourceKey<Registry<MMDMaterial>> MATERIALS_KEY =
      createKey("mmdlib:materials");

  /** The key of {@link #ITEMS_REGISTRY}: {@code mmdlib:items}. */
  public static final ResourceKey<Registry<MMDItem>> ITEMS_KEY = createKey("mmdlib:items");

  /** The key of {@link #BLOCKS_REGISTRY}: {@code mmdlib:blocks}. */
  public static final ResourceKey<Registry<MMDBlock>> BLOCKS_KEY = createKey("mmdlib:blocks");

  /** The key of {@link #EVENT_CALLBACK_REGISTRY}: {@code mmdlib:callbacks/events}. */
  public static final ResourceKey<Registry<MMDLibEventCallback<?>>> EVENT_CALLBACK_KEY =
      createKey("mmdlib:callbacks/events");

  /** Every loaded material definition, by material ID. */
  public static final Registry<MMDMaterial> MATERIALS_REGISTRY =
      new RegistryBuilder<>(MATERIALS_KEY).create();

  /** Every item definition, by item ID. */
  public static final Registry<MMDItem> ITEMS_REGISTRY = new RegistryBuilder<>(ITEMS_KEY).create();

  /** Every block definition, by block ID. */
  public static final Registry<MMDBlock> BLOCKS_REGISTRY =
      new RegistryBuilder<>(BLOCKS_KEY).create();

  /** Every event callback, by handler ID. */
  public static final Registry<MMDLibEventCallback<?>> EVENT_CALLBACK_REGISTRY =
      new RegistryBuilder<>(EVENT_CALLBACK_KEY).create();

  /** The key of {@link #ENTITIES_REGISTRY}: {@code mmdlib:entities}. */
  public static final ResourceKey<Registry<zone.moddev.mc.lib.common.types.MMDEntity>>
      ENTITIES_KEY = createKey("mmdlib:entities");

  /** Every world-entity definition, by entity ID. */
  public static final Registry<zone.moddev.mc.lib.common.types.MMDEntity> ENTITIES_REGISTRY =
      new RegistryBuilder<>(ENTITIES_KEY).create();

  /** The shared catalog of world-entity factories. */
  public static final zone.moddev.mc.lib.common.factories.EntityFactories ENTITY_FACTORIES =
      new zone.moddev.mc.lib.common.factories.EntityFactories();

  /** The key of {@link #FLUIDS_REGISTRY}: {@code mmdlib:fluids}. */
  public static final ResourceKey<Registry<zone.moddev.mc.lib.common.types.MMDFluid>> FLUIDS_KEY =
      createKey("mmdlib:fluids");

  /** Every fluid definition, by fluid ID. */
  public static final Registry<zone.moddev.mc.lib.common.types.MMDFluid> FLUIDS_REGISTRY =
      new RegistryBuilder<>(FLUIDS_KEY).create();

  /** The key of {@link #BLOCK_ENTITIES_REGISTRY}: {@code mmdlib:block_entities}. */
  public static final ResourceKey<Registry<zone.moddev.mc.lib.common.types.MMDBlockEntity>>
      BLOCK_ENTITIES_KEY = createKey("mmdlib:block_entities");

  /** Every block-entity definition, by block-entity ID. */
  public static final Registry<zone.moddev.mc.lib.common.types.MMDBlockEntity>
      BLOCK_ENTITIES_REGISTRY = new RegistryBuilder<>(BLOCK_ENTITIES_KEY).create();

  /** The shared catalog of block-entity factories. */
  public static final zone.moddev.mc.lib.common.factories.BlockEntityFactories
      BLOCK_ENTITY_FACTORIES = new zone.moddev.mc.lib.common.factories.BlockEntityFactories();

  private Registries() {}

  private static <T> ResourceKey<Registry<T>> createKey(String name) {
    return ResourceKey.createRegistryKey(ResourceLocation.parse(name));
  }

  /**
   * Registers MMDLib's definition registries; added to the mod bus by {@link
   * zone.moddev.mc.lib.MMDLib}.
   */
  public static void register(NewRegistryEvent event) {
    event.register(MATERIALS_REGISTRY);
    event.register(ITEMS_REGISTRY);
    event.register(BLOCKS_REGISTRY);
    event.register(EVENT_CALLBACK_REGISTRY);
    event.register(ENTITIES_REGISTRY);
    event.register(BLOCK_ENTITIES_REGISTRY);
    event.register(FLUIDS_REGISTRY);
  }
}
