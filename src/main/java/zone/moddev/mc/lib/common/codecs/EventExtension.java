package zone.moddev.mc.lib.common.codecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** An extension file declares callable Java methods; IDs are assigned after method resolution. */
public record EventExtension(List<Handler> events) {
    public record Handler(String type, String handler, ResourceLocation id, String name) {
        private static final Codec<String> NONBLANK = Codec.STRING.validate(value -> value.isBlank()
                ? DataResult.error(() -> "Value must not be blank") : DataResult.success(value));
        private static final Codec<String> METHOD = NONBLANK.validate(value -> {
            int separator = value.indexOf('#');
            return separator > 0 && separator < value.length() - 1 && separator == value.lastIndexOf('#')
                    && value.chars().noneMatch(Character::isWhitespace)
                    ? DataResult.success(value) : DataResult.error(() -> "Expected fully.qualified.Class#method: " + value);
        });
        public static final Codec<Handler> CODEC = RecordCodecBuilder.create(i -> i.group(
                NONBLANK.fieldOf("type").forGetter(Handler::type),
                METHOD.fieldOf("handler").forGetter(Handler::handler),
                ResourceLocation.CODEC.fieldOf("id").forGetter(Handler::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Handler::name)
        ).apply(i, Handler::new));
        public String label() { return name.isBlank() ? id.toString() : name; }
    }
    public static final Codec<EventExtension> CODEC = RecordCodecBuilder.<EventExtension>create(i -> i.group(
            Handler.CODEC.listOf().fieldOf("events").forGetter(EventExtension::events)
    ).apply(i, EventExtension::new)).validate(value -> {
        var ids = new java.util.HashSet<ResourceLocation>();
        for (var handler : value.events()) if (!ids.add(handler.id()))
            return DataResult.error(() -> "Duplicate event handler ID: " + handler.id());
        return DataResult.success(value);
    });
    public EventExtension { events = List.copyOf(events); }
}
