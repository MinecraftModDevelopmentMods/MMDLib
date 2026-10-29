package zone.moddev.mc.lib.common.parsers;

import com.google.gson.JsonObject;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.FluidBuilder;
import zone.moddev.mc.lib.common.codecs.FluidTemplate;
import zone.moddev.mc.lib.util.exceptions.ParseException;

/**
 * Decodes a fluid template into a {@link FluidBuilder}; a compatibility entry point over {@link
 * zone.moddev.mc.lib.common.codecs.FluidTemplate#CODEC}.
 */
public class FluidParser extends ParserBase<FluidBuilder, FluidBuilder> {
  private final ResourceLocation templateName;

  /** A parser for an unnamed fluid template. */
  public FluidParser(JsonObject data) {
    this(null, data);
  }

  /**
   * A parser for a fluid template whose ID is {@code templateName}; a name inside the JSON must
   * agree.
   */
  public FluidParser(ResourceLocation templateName, JsonObject data) {
    super(data, FluidBuilder::create);
    this.templateName = templateName;
  }

  @Override
  public FluidBuilder parse() {
    builder =
        FluidTemplate.CODEC
            .parse(JsonOps.INSTANCE, baseData)
            .getOrThrow(
                message ->
                    new ParseException("Invalid fluid template " + templateName + ": " + message))
            .toBuilder();
    if (templateName != null) {
      if (builder.getTemplateName() != null && !templateName.equals(builder.getTemplateName()))
        throw new ParseException(
            "Fluid template name disagrees with its resource ID: " + templateName);
      builder = builder.setName(templateName);
    }
    return builder;
  }

  @Override
  protected FluidBuilder parseKey(String key) {
    return builder = builder.setName(key);
  }
}
