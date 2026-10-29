package zone.moddev.mc.lib.common.parsers;

import com.google.gson.JsonObject;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.ItemBuilder;
import zone.moddev.mc.lib.common.codecs.ItemTemplate;
import zone.moddev.mc.lib.util.exceptions.ItemParseException;

/** Compatibility entrypoint; DFU owns field decoding and validation. */
public class ItemParser extends ParserBase<ItemBuilder, ItemBuilder> {
  private final ResourceLocation templateName;

  /** A parser for an unnamed item template. */
  public ItemParser(JsonObject data) {
    this(null, data);
  }

  /**
   * A parser for an item template whose ID is {@code templateName}; a name inside the JSON must
   * agree.
   */
  public ItemParser(ResourceLocation templateName, JsonObject data) {
    super(data, ItemBuilder::create);
    this.templateName = templateName;
  }

  @Override
  public ItemBuilder parse() {
    builder =
        ItemTemplate.CODEC
            .parse(JsonOps.INSTANCE, baseData)
            .getOrThrow(
                message ->
                    new ItemParseException(
                        "Invalid item template " + templateName + ": " + message))
            .toBuilder();
    if (templateName != null) {
      if (builder.getTemplateName() != null && !templateName.equals(builder.getTemplateName()))
        throw new ItemParseException(
            "Item template name disagrees with its resource ID: " + templateName);
      builder = builder.setName(templateName);
    }
    return builder;
  }

  @Override
  protected ItemBuilder parseKey(String key) {
    return builder = builder.setName(key);
  }
}
