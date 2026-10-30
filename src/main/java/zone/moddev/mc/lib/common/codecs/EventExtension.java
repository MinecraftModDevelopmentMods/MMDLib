package zone.moddev.mc.lib.common.codecs;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * An extension file ({@code mmdlib/extension/*.json}): a list of Java methods to register as event
 * handlers. Each handler's {@code id} is what material and module {@code event} maps refer to.
 */
public record EventExtension(List<Handler> events) {
  /**
   * One handler entry.
   *
   * @param type the event name, such as {@code tool-right-click}
   * @param handler the method, as {@code fully.qualified.Class#method}; must be {@code public
   *     static void} with the event's parameters
   * @param id the callback ID that JSON bindings use
   * @param name an optional readable name for logs
   */
  public record Handler(String type, String handler, ResourceLocation id, String name) {
    private static final Codec<String> NONBLANK =
        Codec.STRING.validate(
            value ->
                value.isBlank()
                    ? DataResult.error(() -> "Value must not be blank")
                    : DataResult.success(value));
    private static final Codec<String> METHOD =
        NONBLANK.validate(
            value -> {
              int separator = value.indexOf('#');
              return separator > 0
                      && separator < value.length() - 1
                      && separator == value.lastIndexOf('#')
                      && value.chars().noneMatch(Character::isWhitespace)
                  ? DataResult.success(value)
                  : DataResult.error(() -> "Expected fully.qualified.Class#method: " + value);
            });
    public static final Codec<Handler> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        NONBLANK.fieldOf("type").forGetter(Handler::type),
                        METHOD.fieldOf("handler").forGetter(Handler::handler),
                        ResourceLocation.CODEC.fieldOf("id").forGetter(Handler::id),
                        Codec.STRING.optionalFieldOf("name", "").forGetter(Handler::name))
                    .apply(i, Handler::new));

    /** The name for logs: {@code name}, or the ID when no name is given. */
    public String label() {
      return name.isBlank() ? id.toString() : name;
    }
  }

  public static final Codec<EventExtension> CODEC =
      RecordCodecBuilder.<EventExtension>create(
              i ->
                  i.group(
                          Handler.CODEC
                              .listOf()
                              .fieldOf("events")
                              .forGetter(EventExtension::events))
                      .apply(i, EventExtension::new))
          .validate(
              value -> {
                var ids = new java.util.HashSet<ResourceLocation>();
                for (var handler : value.events())
                  if (!ids.add(handler.id()))
                    return DataResult.error(() -> "Duplicate event handler ID: " + handler.id());
                return DataResult.success(value);
              });

  /** Copies the handler list. */
  public EventExtension {
    events = List.copyOf(events);
  }
}
