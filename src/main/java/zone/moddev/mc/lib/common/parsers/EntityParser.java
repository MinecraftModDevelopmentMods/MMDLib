package zone.moddev.mc.lib.common.parsers;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.builders.EntityBuilder;
import zone.moddev.mc.lib.common.codecs.EntityTemplate;
import zone.moddev.mc.lib.util.exceptions.ParseException;

public class EntityParser extends ParserBase<EntityBuilder, EntityBuilder> {
    private final ResourceLocation templateName;
    public EntityParser(JsonObject data) { this(null, data); }
    public EntityParser(ResourceLocation templateName, JsonObject data) {
        super(data, EntityBuilder::create);
        this.templateName = templateName;
    }
    @Override public EntityBuilder parse() {
        builder = EntityTemplate.CODEC.parse(JsonOps.INSTANCE, baseData).getOrThrow(message ->
                new ParseException("Invalid entity template " + templateName + ": " + message)).toBuilder();
        if (templateName != null) {
            if (builder.getTemplateName() != null && !templateName.equals(builder.getTemplateName()))
                throw new ParseException("Entity template name disagrees with its resource ID: " + templateName);
            builder = builder.setName(templateName);
        }
        return builder;
    }
    @Override protected EntityBuilder parseKey(String key) { return builder = builder.setName(key); }
}
