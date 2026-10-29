package zone.moddev.mc.lib.client;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.google.gson.JsonParser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** Authored, precolored artwork must not receive the generic material tint a second time. */
public record StaticAssetRules(
    Set<ResourceLocation> untintedItems, Set<ResourceLocation> untintedBlocks) {
  private static final Codec<Set<ResourceLocation>> IDS =
      ResourceLocation.CODEC
          .listOf()
          .xmap(Set::copyOf, values -> values.stream().sorted().toList());
  public static final Codec<StaticAssetRules> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      IDS.optionalFieldOf("untinted_items", Set.of())
                          .forGetter(StaticAssetRules::untintedItems),
                      IDS.optionalFieldOf("untinted_blocks", Set.of())
                          .forGetter(StaticAssetRules::untintedBlocks))
                  .apply(i, StaticAssetRules::new));
  public static final StaticAssetRules INSTANCE = load();

  private static StaticAssetRules load() {
    try (var stream =
        StaticAssetRules.class.getResourceAsStream("/assets/mmdlib/imported_assets.json")) {
      if (stream == null) return new StaticAssetRules(Set.of(), Set.of());
      try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
      }
    } catch (java.io.IOException ex) {
      throw new java.io.UncheckedIOException(ex);
    }
  }
}
