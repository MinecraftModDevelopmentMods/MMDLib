package zone.moddev.mc.lib.common.types;

import java.util.*;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;

/**
 * One selectable form a material can take, such as {@code mmdlib:pickaxe} or {@code mmdlib:door}: a
 * loaded item or block module, or a code-registered type. Two parts are equal when their IDs are,
 * so a part is the same whichever material uses it.
 */
public final class MaterialPart implements Comparable<MaterialPart> {
  /** Whether a part produces an item or a block (whose item, if any, comes with it). */
  public enum Kind {
    /** The part produces an item. */
    ITEM,
    /** The part produces a block, and its item if it has one. */
    BLOCK
  }

  private final ResourceLocation name;
  private final Kind kind;
  private final ResourceLocation factory;
  private final Optional<String> requiredMod;
  private final PartMetadata metadata;
  private final FactoryData factoryData;

  /** A part whose factory ID is its own ID, with no {@code factory_data}. */
  public MaterialPart(
      ResourceLocation name, Kind kind, Optional<String> requiredMod, PartMetadata metadata) {
    this(name, kind, requiredMod, metadata, name);
  }

  /** A part with no {@code factory_data}. */
  public MaterialPart(
      ResourceLocation name,
      Kind kind,
      Optional<String> requiredMod,
      PartMetadata metadata,
      ResourceLocation factory) {
    this(name, kind, requiredMod, metadata, factory, FactoryData.EMPTY);
  }

  /**
   * A part.
   *
   * @param name the part's ID
   * @param kind item or block
   * @param requiredMod the mod the part needs, if any
   * @param metadata aliases, entity and block-entity companions
   * @param factory the ID of the Java implementation that creates it
   * @param factoryData the settings passed to that implementation
   * @throws IllegalArgumentException if {@code entity_data} is given without {@code
   *     entity_factory}, or an item part declares a block entity
   */
  public MaterialPart(
      ResourceLocation name,
      Kind kind,
      Optional<String> requiredMod,
      PartMetadata metadata,
      ResourceLocation factory,
      FactoryData factoryData) {
    this.name = Objects.requireNonNull(name);
    this.kind = Objects.requireNonNull(kind);
    this.factoryData = Objects.requireNonNull(factoryData);
    this.factory = Objects.requireNonNull(factory);
    this.requiredMod = Objects.requireNonNull(requiredMod);
    this.metadata = Objects.requireNonNull(metadata);
    if (metadata.entityFactory().isEmpty() && !metadata.entityData().equals(FactoryData.EMPTY))
      throw new IllegalArgumentException("entity_data requires entity_factory: " + name);
    if (kind == Kind.ITEM && metadata.blockEntity().isPresent())
      throw new IllegalArgumentException("Block entities require a block module: " + name);
  }

  /** The ID of the Java implementation that creates this part. */
  public ResourceLocation factory() {
    return factory;
  }

  /** The settings passed to that implementation. */
  public FactoryData factoryData() {
    return factoryData;
  }

  /** Whether this part is an item or a block. */
  public Kind kind() {
    return kind;
  }

  /** The mod this part needs, if any. */
  public Optional<String> requiredMod() {
    return requiredMod;
  }

  /** Aliases and companion definitions. */
  public PartMetadata metadata() {
    return metadata;
  }

  /**
   * Whether the part can be built: it needs no mod, or {@code installed} reports its mod loaded.
   */
  public boolean available(Predicate<String> installed) {
    return requiredMod.isEmpty() || installed.test(requiredMod.get());
  }

  /** Whether the part has a world-entity companion, such as an arrow's projectile entity. */
  public boolean needsEntity() {
    return metadata.entityFactory().isPresent();
  }

  /** The world-entity factory ID, when the part has one. */
  public Optional<ResourceLocation> entityFactory() {
    return metadata.entityFactory();
  }

  /** Whether the part has a block-entity companion. */
  public boolean needsBlockEntity() {
    return metadata.blockEntity().isPresent();
  }

  /**
   * The short form used in selections and generated names: the path for {@code mmdlib} parts,
   * otherwise the full ID.
   */
  public String id() {
    return name.getNamespace().equals("mmdlib") ? name.getPath() : name.toString();
  }

  /** The part's full ID. */
  public ResourceLocation templateId() {
    return name;
  }

  @Override
  public int compareTo(MaterialPart other) {
    return name.compareTo(other.name);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MaterialPart part && name.equals(part.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return name.toString();
  }
}
