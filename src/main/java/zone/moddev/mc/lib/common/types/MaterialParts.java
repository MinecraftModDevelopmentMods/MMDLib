package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
// import java.util.List;
import java.util.Set;
import zone.moddev.mc.lib.common.modules.ModuleCatalog;

/**
 * The parts a material selects, plus whether it has a fluid; independent of how the parts' modules
 * were supplied.
 */
public record MaterialParts(Set<MaterialPart> parts, boolean hasFluid) {
  public static final MaterialParts DEFAULT =
      new MaterialParts(Set.copyOf(ModuleCatalog.bundled().parts().entries().values()), true);

  /** Copies the parts. */
  public MaterialParts {
    parts = Set.copyOf(parts);
  }

  /**
   * A codec for the older {@code {"parts": [...], "has_fluid": ...}} form, resolving part names and
   * aliases in {@code registry}. A missing {@code parts} list means every part.
   */
  public static Codec<MaterialParts> forRegistry(PartRegistry registry) {
    Codec<Set<MaterialPart>> parts =
        registry
            .codec()
            .listOf()
            .comapFlatMap(
                values -> {
                  Set<MaterialPart> unique = Set.copyOf(values);
                  return unique.size() == values.size()
                      ? DataResult.success(unique)
                      : DataResult.error(() -> "Duplicate material part (including aliases)");
                },
                values -> values.stream().sorted().toList());
    return RecordCodecBuilder.create(
        i ->
            i.group(
                    parts
                        .optionalFieldOf("parts", Set.copyOf(registry.entries().values()))
                        .forGetter(MaterialParts::parts),
                    Codec.BOOL
                        .optionalFieldOf("has_fluid", true)
                        .forGetter(MaterialParts::hasFluid))
                .apply(i, MaterialParts::new));
  }

  public static final Codec<MaterialParts> CODEC = forRegistry(ModuleCatalog.bundled().parts());
}
