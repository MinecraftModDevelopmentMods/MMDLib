package zone.moddev.mc.lib.common.parsers;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.builders.FluidBuilder;
import zone.moddev.mc.lib.common.codecs.FluidTemplate;
import zone.moddev.mc.lib.util.exceptions.ParseException;

public class FluidParser extends ParserBase<FluidBuilder, FluidBuilder> {
    private final ResourceLocation templateName;
    public FluidParser(JsonObject data) { this(null, data); }
    public FluidParser(ResourceLocation templateName, JsonObject data) {
        super(data, FluidBuilder::create);
        this.templateName = templateName;
    }
    @Override public FluidBuilder parse() {
        builder = FluidTemplate.CODEC.parse(JsonOps.INSTANCE, baseData).getOrThrow(message ->
                new ParseException("Invalid fluid template " + templateName + ": " + message)).toBuilder();
        if (templateName != null) {
            if (builder.getTemplateName() != null && !templateName.equals(builder.getTemplateName()))
                throw new ParseException("Fluid template name disagrees with its resource ID: " + templateName);
            builder = builder.setName(templateName);
        }
        return builder;
    }
    @Override protected FluidBuilder parseKey(String key) { return builder = builder.setName(key); }
}
