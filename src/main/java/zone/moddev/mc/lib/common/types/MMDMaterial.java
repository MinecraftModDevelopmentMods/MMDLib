package zone.moddev.mc.lib.common.types;

import java.io.StringReader;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.MaterialContentPlanner;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.util.DataLoaders;

/**
 * A material definition, as decoded from a mod's {@code mmdlib/material/<name>.json}: its stats,
 * which parts it selects, event bindings and runtime settings. Immutable. A definition loaded from
 * a file gets its name from the file path when the factory loads it.
 *
 * @param name the material ID, such as {@code mmdlib:copper}; empty until the factory assigns it
 * @param parent the material this one inherits from, if any
 * @param properties the material's stats (hardness, durability, tool tier, armor, ...)
 * @param selection which item and block parts, entities and fluid the material uses
 * @param events the material's event bindings: event name to callback ID, used when an item has no
 *     binding of its own
 * @param runtime game-side settings such as sound, map color, block set type and base block
 */
public record MMDMaterial(
    Optional<ResourceLocation> name,
    Optional<ResourceLocation> parent,
    MaterialProperties properties,
    MaterialSelection selection,
    Map<String, ResourceLocation> events,
    MaterialRuntimeSettings runtime) {
  /** A material with default runtime settings. */
  public MMDMaterial(
      Optional<ResourceLocation> name,
      Optional<ResourceLocation> parent,
      MaterialProperties properties,
      MaterialSelection selection,
      Map<String, ResourceLocation> events) {
    this(name, parent, properties, selection, events, MaterialRuntimeSettings.DEFAULT);
  }

  /**
   * Requires every component and copies the event bindings.
   *
   * @throws IllegalArgumentException if an event name is blank
   */
  public MMDMaterial {
    java.util.Objects.requireNonNull(name);
    java.util.Objects.requireNonNull(parent);
    java.util.Objects.requireNonNull(properties);
    java.util.Objects.requireNonNull(selection);
    java.util.Objects.requireNonNull(runtime);
    events = Map.copyOf(events);
    if (events.keySet().stream().anyMatch(String::isBlank))
      throw new IllegalArgumentException("Event name must not be blank");
  }

  /** An unnamed material with the default parts and stats. */
  public MMDMaterial() {
    this(MaterialParts.DEFAULT);
  }

  /** An unnamed material with default stats that selects {@code parts}. */
  public MMDMaterial(MaterialParts parts) {
    this(
        Optional.empty(),
        Optional.empty(),
        MaterialProperties.DEFAULT,
        MaterialSelection.fromParts(parts),
        Map.of());
  }

  /** The parts this material selects. */
  public MaterialParts parts() {
    return selection.parts();
  }

  /**
   * Plans this material's content under {@code id} with the global catalog.
   *
   * @throws IllegalArgumentException if the material is already named something else
   */
  public MaterialContentPlan planContent(ResourceLocation id) {
    checkName(id);
    return new MaterialFactory()
        .create(zone.moddev.mc.lib.common.builders.MaterialBuilder.from(this).setName(id).build())
        .content();
  }

  /** Plans this material's content under {@code id} with {@code planner}. */
  public MaterialContentPlan planContent(ResourceLocation id, MaterialContentPlanner planner) {
    checkName(id);
    return planner.plan(id, parts());
  }

  /**
   * Plans this material's content under {@code id} with {@code planner}, leaving out parts whose
   * required mod {@code installed} reports missing.
   */
  public MaterialContentPlan planContent(
      ResourceLocation id, MaterialContentPlanner planner, Predicate<String> installed) {
    checkName(id);
    return planner.plan(id, parts(), installed);
  }

  private void checkName(ResourceLocation id) {
    if (name.isPresent() && !name.get().equals(id))
      throw new IllegalArgumentException("Material name disagrees with " + id);
  }

  /** Compatibility entrypoint: raw material JSON, not the old unfinished JSLT transformation. */
  public MMDMaterial load(ResourceLocation id) {
    return new MaterialFactory()
        .decode(id, new StringReader(DataLoaders.loadFile(id, "json", LocationType.MATERIAL)));
  }
}
