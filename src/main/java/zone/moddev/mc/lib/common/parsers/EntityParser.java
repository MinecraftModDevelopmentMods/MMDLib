package zone.moddev.mc.lib.common.parsers;

import com.google.gson.JsonObject;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.EntityBuilder;
import zone.moddev.mc.lib.common.codecs.EntityTemplate;
import zone.moddev.mc.lib.util.exceptions.ParseException;

/**
 * Decodes an entity template into an {@link EntityBuilder}; a compatibility entry point over {@link
 * zone.moddev.mc.lib.common.codecs.EntityTemplate#CODEC}.
 */
public class EntityParser extends ParserBase<EntityBuilder, EntityBuilder> {
  private final ResourceLocation templateName;

  /** A parser for an unnamed entity template. */
  public EntityParser(JsonObject data) {
    this(null, data);
  }

  /**
   * A parser for an entity template whose ID is {@code templateName}; a name inside the JSON must
   * agree.
   */
  public EntityParser(ResourceLocation templateName, JsonObject data) {
    super(data, EntityBuilder::create);
    this.templateName = templateName;
  }

  @Override
  public EntityBuilder parse() {
    builder =
        EntityTemplate.CODEC
            .parse(JsonOps.INSTANCE, baseData)
            .getOrThrow(
                message ->
                    new ParseException("Invalid entity template " + templateName + ": " + message))
            .toBuilder();
    if (templateName != null) {
      if (builder.getTemplateName() != null && !templateName.equals(builder.getTemplateName()))
        throw new ParseException(
            "Entity template name disagrees with its resource ID: " + templateName);
      builder = builder.setName(templateName);
    }
    return builder;
  }

  @Override
  protected EntityBuilder parseKey(String key) {
    return builder = builder.setName(key);
  }
}
