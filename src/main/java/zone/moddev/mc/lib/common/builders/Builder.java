package zone.moddev.mc.lib.common.builders;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * Base of MMDLib's immutable definition builders. Every setter returns a new builder and leaves the
 * receiver unchanged, so a builder can be shared as a template; callers must keep the returned
 * value.
 *
 * @param <T> the definition built
 * @param <X> the concrete builder type, returned by the setters
 */
public abstract class Builder<T, X> {
  /**
   * Builds the definition.
   *
   * @throws IllegalStateException if a required value is missing
   */
  public abstract T build();

  /** Sets the template name: the part or module ID the definition comes from. */
  public abstract X setName(ResourceLocation name);

  /** As {@link #setName(ResourceLocation)}, parsing {@code name}. */
  public abstract X setName(String name);

  /** The registry ID the definition will have, or null while it cannot be known yet. */
  public abstract ResourceLocation getRegistryName();

  /** Sets the parent definition this one inherits from. */
  public abstract X setParent(ResourceLocation parent);

  /** Sets the event bindings: event name to callback ID. */
  public abstract X setEventMap(Map<String, ResourceLocation> events);
}
