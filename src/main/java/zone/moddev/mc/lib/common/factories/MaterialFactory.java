package zone.moddev.mc.lib.common.factories;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.fml.ModList;

import zone.moddev.mc.lib.common.builders.*;
import zone.moddev.mc.lib.common.codecs.MMDMaterialCodec;
import zone.moddev.mc.lib.common.types.*;

/**
 * Turns material JSON into a planned material: decodes it against a {@link
 * zone.moddev.mc.lib.api.MaterialExtensions} catalog, gives it its ID, and plans every item, block,
 * entity and fluid it selects. Nothing is registered here.
 */
public final class MaterialFactory {
  /** A loaded material and everything planned for it. */
  public record Result(MMDMaterial material, MaterialContentPlan content) {}

  private final zone.moddev.mc.lib.api.MaterialExtensions extensions;
  private final Predicate<String> installed;
  private final UnaryOperator<MaterialContentPlanner> customize;

  /** A factory on the global catalog that asks NeoForge which mods are loaded. */
  public MaterialFactory() {
    this(id -> ModList.get().isLoaded(id));
  }

  /**
   * A factory on the global catalog.
   *
   * @param installed tests whether a mod is loaded
   */
  public MaterialFactory(Predicate<String> installed) {
    this(zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL, installed);
  }

  /**
   * A factory on {@code extensions}.
   *
   * @param installed tests whether a mod is loaded
   */
  public MaterialFactory(
      zone.moddev.mc.lib.api.MaterialExtensions extensions, Predicate<String> installed) {
    this(extensions, installed, UnaryOperator.identity());
  }

  private MaterialFactory(
      zone.moddev.mc.lib.api.MaterialExtensions extensions,
      Predicate<String> installed,
      UnaryOperator<MaterialContentPlanner> customize) {
    this.extensions = Objects.requireNonNull(extensions);
    this.installed = Objects.requireNonNull(installed);
    this.customize = customize;
  }

  /** Custom templates override the material defaults without mutating this factory. */
  public MaterialFactory withTemplates(UnaryOperator<MaterialContentPlanner> overrides) {
    Objects.requireNonNull(overrides);
    return new MaterialFactory(
        extensions, installed, planner -> overrides.apply(customize.apply(planner)));
  }

  /**
   * Decodes material JSON and names the material {@code id}, without planning content.
   *
   * @throws IllegalArgumentException if the JSON is invalid or names a different ID
   */
  public MMDMaterial decode(ResourceLocation id, Reader json) {
    var spec =
        MMDMaterialCodec.forExtensions(extensions)
            .parse(JsonOps.INSTANCE, JsonParser.parseReader(json))
            .getOrThrow(message -> new IllegalArgumentException("Material " + id + ": " + message));
    if (spec.name().isPresent() && !spec.name().get().equals(id))
      throw new IllegalArgumentException("Material name disagrees with resource ID: " + id);
    return MaterialBuilder.from(spec).setName(id).build();
  }

  /**
   * Decodes and plans a material.
   *
   * @throws IllegalArgumentException if the JSON is invalid or the material cannot be planned
   */
  public Result load(ResourceLocation id, Reader json) {
    return create(decode(id, json));
  }

  /**
   * Reads, decodes and plans a material file; error messages name the file.
   *
   * @throws IOException if the file cannot be read
   */
  public Result load(ResourceLocation id, Path file) throws IOException {
    try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return load(id, reader);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(file + ": " + ex.getMessage(), ex);
    }
  }

  /**
   * Plans an already-decoded, named material: validates its event bindings and plans each selected
   * part whose mod is installed.
   *
   * @throws IllegalArgumentException if the material is unnamed or cannot be planned; the message
   *     names the material
   */
  public Result create(MMDMaterial material) {
    var id =
        material
            .name()
            .orElseThrow(
                () -> new IllegalArgumentException("Material factory requires a named material"));
    try {
      extensions.events().validate(material.events());
      var planner =
          Objects.requireNonNull(
              customize.apply(extensions.planner(material, installed)), "custom planner");
      return new Result(
          material,
          extensions.extend(
              material,
              planner.plan(
                  id,
                  extensions.selection(material),
                  installed,
                  part -> extensions.available(part, installed)),
              installed));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Material " + id + ": " + ex.getMessage(), ex);
    }
  }
}
