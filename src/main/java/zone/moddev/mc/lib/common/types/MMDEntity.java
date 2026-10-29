package zone.moddev.mc.lib.common.types;

import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

/**
 * A world-entity definition belonging to one material, such as the projectile entity of its arrows.
 * Only a definition: no entity type is created or registered here.
 *
 * @param name the entity type's registry ID
 * @param template the part that needs the entity
 * @param material the owning material
 * @param parent an optional parent definition, may be null
 * @param factory the entity factory that creates the type
 * @param category the mob category
 * @param width the hitbox width in blocks
 * @param height the hitbox height in blocks
 * @param trackingRange how far, in chunks, clients track the entity
 * @param updateInterval ticks between position updates sent to clients
 * @param save whether the entity is saved with the chunk
 * @param summon whether {@code /summon} may create it
 * @param fireImmune whether fire and lava harm it
 * @param factoryData the entity factory's settings
 * @param events event bindings for the entity
 */
public record MMDEntity(
    ResourceLocation name,
    ResourceLocation template,
    ResourceLocation material,
    ResourceLocation parent,
    ResourceLocation factory,
    MobCategory category,
    float width,
    float height,
    int trackingRange,
    int updateInterval,
    boolean save,
    boolean summon,
    boolean fireImmune,
    FactoryData factoryData,
    Map<String, ResourceLocation> events) {
  public MMDEntity {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(template, "template");
    Objects.requireNonNull(material, "material");
    Objects.requireNonNull(factory, "factory");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(factoryData, "factoryData");
    validateDimensions(width, height);
    if (trackingRange < 0 || updateInterval < 1)
      throw new IllegalArgumentException(
          "Tracking range must be nonnegative and update interval positive");
    events = Map.copyOf(events);
    if (events.keySet().stream().anyMatch(String::isBlank))
      throw new IllegalArgumentException("Entity event names must not be blank");
  }

  /**
   * Checks that a hitbox size is finite and not negative.
   *
   * @throws IllegalArgumentException otherwise
   */
  public static void validateDimensions(float width, float height) {
    if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0)
      throw new IllegalArgumentException("Entity dimensions must be finite and nonnegative");
  }
}
