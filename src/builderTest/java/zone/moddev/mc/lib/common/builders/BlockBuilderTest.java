package zone.moddev.mc.lib.common.builders;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.codecs.BlockTemplate;
import zone.moddev.mc.lib.common.parsers.BlockParser;

public final class BlockBuilderTest {
  public static void main(String[] args) {
    var root = BlockBuilder.create();
    require(root == BlockBuilder.INSTANCE);
    var template =
        root.setName("mmdlib:block").setStrength(3, 8).setRequiresCorrectToolForDrops(true);
    var copper = template.forMaterial("test:copper");
    var tin = template.forMaterial("test:tin");
    require(root.getTemplateName() == null && template.getMaterialName() == null);
    require(copper.build().name().equals(ResourceLocation.parse("test:copper_block")));
    require(tin.build().name().equals(ResourceLocation.parse("test:tin_block")));
    require(copper.build().item().name().equals(copper.build().name()));
    require(copper.build() != copper.build());
    var glowing = copper.setLightLevel(12).setCollision(false).setOcclusion(false);
    require(
        glowing.build().properties().lightLevel() == 12
            && !glowing.build().properties().collision());
    require(
        copper.build().properties().lightLevel() == 0 && copper.build().properties().collision());
    var tags = new HashSet<>(Set.of(ResourceLocation.parse("minecraft:mineable/pickaxe")));
    var events = new HashMap<String, ResourceLocation>();
    events.put("use", ResourceLocation.parse("test:use"));
    var configured =
        copper
            .setBlockTags(tags)
            .setEventMap(events)
            .setFriction(.8F)
            .setSpeedFactor(.7F)
            .setJumpFactor(1.5F)
            .setRandomTicks(true)
            .setReplaceable(true)
            .setSound("metal")
            .setMapColor("gold");
    tags.clear();
    events.clear();
    var built = configured.build();
    require(built.tags().size() == 1 && built.events().size() == 1);
    require(built.properties().friction() == .8F && built.properties().speedFactor() == .7F);
    require(
        built.properties().jumpFactor() == 1.5F
            && built.properties().randomTicks()
            && built.properties().replaceable());
    var item =
        ItemBuilder.create()
            .setName("mmdlib:block_item")
            .setFireResistant(true)
            .setMaxStackSize(16);
    var withItem = copper.setItemTemplate(item).build();
    require(withItem.item().fireResistant() && withItem.item().maxStackSize() == 16);
    require(withItem.item().name().equals(withItem.name()) && item.getMaterialName() == null);
    require(copper.setHasItem(false).build().item() == null && copper.build().item() != null);
    expectFailure(root::build);
    expectFailure(template::build);
    expectFailure(() -> copper.setHardness(-2));
    expectFailure(() -> copper.setBlastResistance(Float.NaN));
    expectFailure(() -> copper.setLightLevel(16));
    expectFailure(() -> copper.setSpeedFactor(-1));
    var json =
        JsonParser.parseString(
                """
{"properties":{"hardness":4,"blast_resistance":10,"light_level":5,"requires_correct_tool":true},
 "tags":["minecraft:mineable/pickaxe","minecraft:needs_iron_tool"],
 "events":{"use":"test:use"},"item":{"max_stack_size":16,"fire_resistant":true}}
""")
            .getAsJsonObject();
    var decoded = BlockTemplate.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    var encoded = BlockTemplate.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
    require(BlockTemplate.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(decoded));
    var nbt = BlockTemplate.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow();
    require(BlockTemplate.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(decoded));
    var parsed =
        new BlockParser(ResourceLocation.parse("mmdlib:block"), json)
            .parse()
            .forMaterial("test:copper")
            .build();
    require(
        parsed.properties().hardness() == 4
            && parsed.tags().size() == 2
            && parsed.item().maxStackSize() == 16);
    for (String invalid :
        new String[] {
          "{\"properties\":{\"hardness\":-2}}",
          "{\"properties\":{\"light_level\":16}}",
          "{\"properties\":{\"blast_resistance\":-1}}",
          "{\"type\":\"\"}",
          "{\"has_item\":false,\"item\":{}}"
        })
      require(
          BlockTemplate.CODEC
              .parse(JsonOps.INSTANCE, JsonParser.parseString(invalid))
              .error()
              .isPresent());
    System.out.println(
        "Immutable material block builder, item binding, JSON/NBT codec, and validation checks"
            + " passed");
  }

  private static void require(boolean condition) {
    if (!condition) throw new AssertionError("Block builder assertion failed");
  }

  private static void expectFailure(Runnable action) {
    try {
      action.run();
    } catch (IllegalArgumentException | IllegalStateException ex) {
      return;
    }
    throw new AssertionError("Expected invalid block definition to fail");
  }
}
