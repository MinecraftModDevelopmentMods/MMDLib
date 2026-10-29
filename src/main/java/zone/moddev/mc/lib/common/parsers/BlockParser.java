package zone.moddev.mc.lib.common.parsers;

import com.google.gson.JsonObject;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.BlockBuilder;
import zone.moddev.mc.lib.common.codecs.BlockTemplate;
import zone.moddev.mc.lib.util.exceptions.ParseException;

/** DFU decoding entrypoint for material-owned block templates. */
public class BlockParser extends ParserBase<BlockBuilder, BlockBuilder> {
  private final ResourceLocation templateName;

  /** A parser for an unnamed block template. */
  public BlockParser(JsonObject data) {
    this(null, data);
  }

  /**
   * A parser for a block template whose ID is {@code templateName}; a name inside the JSON must
   * agree.
   */
  public BlockParser(ResourceLocation templateName, JsonObject data) {
    super(data, BlockBuilder::create);
    this.templateName = templateName;
  }

  @Override
  public BlockBuilder parse() {
    builder =
        BlockTemplate.CODEC
            .parse(JsonOps.INSTANCE, baseData)
            .getOrThrow(
                message ->
                    new ParseException("Invalid block template " + templateName + ": " + message))
            .toBuilder();
    if (templateName != null) {
      if (builder.getTemplateName() != null && !templateName.equals(builder.getTemplateName()))
        throw new ParseException(
            "Block template name disagrees with its resource ID: " + templateName);
      builder = builder.setName(templateName);
    }
    return builder;
  }

  @Override
  protected BlockBuilder parseKey(String key) {
    return builder = builder.setName(key);
  }
}
