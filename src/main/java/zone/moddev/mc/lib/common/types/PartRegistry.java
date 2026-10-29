package zone.moddev.mc.lib.common.types;

import java.util.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.resources.ResourceLocation;

/**
 * An immutable set of parts, looked up by ID or alias. An alias belongs to its part's namespace,
 * and no alias may shadow another part's ID or alias.
 */
public final class PartRegistry {
  private final Map<ResourceLocation, MaterialPart> entries;
  private final Map<ResourceLocation, MaterialPart> names;

  /**
   * Indexes {@code parts}.
   *
   * @throws IllegalArgumentException for duplicate IDs, an alias in another namespace, or
   *     conflicting aliases
   */
  public PartRegistry(Collection<MaterialPart> parts) {
    var entries = new LinkedHashMap<ResourceLocation, MaterialPart>();
    for (var part : parts)
      if (entries.putIfAbsent(part.templateId(), part) != null)
        throw new IllegalArgumentException("Duplicate material part: " + part);
    var names = new HashMap<>(entries);
    for (var part : parts)
      for (String alias : part.metadata().aliases()) {
        ResourceLocation id =
            alias.contains(":")
                ? ResourceLocation.parse(alias)
                : ResourceLocation.fromNamespaceAndPath(part.templateId().getNamespace(), alias);
        if (!id.getNamespace().equals(part.templateId().getNamespace()))
          throw new IllegalArgumentException("Part alias must use its module namespace: " + alias);
        if (names.putIfAbsent(id, part) != null)
          throw new IllegalArgumentException("Conflicting material part alias: " + id);
      }
    this.entries = Map.copyOf(entries);
    this.names = Map.copyOf(names);
  }

  /** Every part by its ID (aliases not included). */
  public Map<ResourceLocation, MaterialPart> entries() {
    return entries;
  }

  /** Looks up a part by ID or alias; a name without a namespace means {@code mmdlib}. */
  public DataResult<MaterialPart> resolve(String name) {
    var id = ResourceLocation.tryParse(name.contains(":") ? name : "mmdlib:" + name);
    var part = id == null ? null : names.get(id);
    return part == null
        ? DataResult.error(() -> "Unknown material part: " + name)
        : DataResult.success(part);
  }

  /** Every item part. */
  public Set<MaterialPart> items() {
    return scope(true, false);
  }

  /** Every block part. */
  public Set<MaterialPart> blocks() {
    return scope(false, false);
  }

  /** Every part that needs a world entity. */
  public Set<MaterialPart> entities() {
    return scope(true, true);
  }

  private Set<MaterialPart> scope(boolean items, boolean entities) {
    return entries.values().stream()
        .filter(p -> entities ? p.needsEntity() : (p.kind() == MaterialPart.Kind.ITEM) == items)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  /** A codec reading a part name or alias and writing its short {@link MaterialPart#id()}. */
  public Codec<MaterialPart> codec() {
    return Codec.STRING.flatXmap(
        this::resolve,
        part ->
            entries.containsKey(part.templateId())
                ? DataResult.success(part.id())
                : DataResult.error(() -> "Unknown material part: " + part));
  }
}
