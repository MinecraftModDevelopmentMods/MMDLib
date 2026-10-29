package zone.moddev.mc.lib.common.builders;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.codecs.ItemTemplate;
import zone.moddev.mc.lib.common.parsers.ItemParser;

public final class ItemTemplateTest {
  public static void main(String[] args) {
    var json =
        JsonParser.parseString(
                """
{"type":"tool","max_damage":250,"fire_resistant":true,
 "food":"test:food", "container":"test:container",
 "delayed_use":{"duration":20,"animation":"eat","on_complete":"test:finish"},
 "group":"ingredients","lore":["A tool"],"tool_actions":["axe_strip"],
 "events":{"use":"test:use"},
 "attribute_modifiers":[{"type":"minecraft:generic.attack_damage","id":"test:damage",
   "amount":3,"operation":"add_value","slot":"mainhand"}]}
""")
            .getAsJsonObject();
    var template = ItemTemplate.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    var roundTrip =
        ItemTemplate.CODEC
            .parse(
                JsonOps.INSTANCE,
                ItemTemplate.CODEC.encodeStart(JsonOps.INSTANCE, template).getOrThrow())
            .getOrThrow();
    require(template.equals(roundTrip));
    var parsed = new ItemParser(ResourceLocation.parse("mmdlib:axe"), json).parse();
    var copper = parsed.forMaterial("test:copper").build();
    require(copper.maxDamage() == 250 && copper.maxStackSize() == 1 && copper.fireResistant());
    require(copper.attributeModifiers().size() == 1 && copper.useTime() == 20);
    require(copper.creativeStacks().size() == 1 && copper.events().size() == 1);
    require(parsed.getMaterialName() == null);
    for (String invalid :
        new String[] {
          "{\"max_stack_size\":100}",
          "{\"max_damage\":-1}",
          "{\"food\":{}}",
          "{\"group\":\"ingredients\",\"creative_menu_stacks\":[{\"tabs\":[\"tools\"]}]}",
          "{\"delayed_use\":{\"duration\":2,\"animation\":\"unknown\"}}"
        }) {
      require(
          ItemTemplate.CODEC
              .parse(JsonOps.INSTANCE, JsonParser.parseString(invalid))
              .error()
              .isPresent());
    }
    require(
        ItemTemplate.CODEC
                .parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseString(
                        "{\"creative_menu_stacks\":[{\"count\":2,\"tabs\":[\"tools\"]}]}"))
                .getOrThrow()
                .toBuilder()
                .setName("test:gear")
                .forMaterial("test:copper")
                .build()
                .creativeStacks()
                .get(0)
                .count()
            == 2);
    System.out.println(
        "DFU item template round-trip, parser, attributes, and validation checks passed");
  }

  private static void require(boolean condition) {
    if (!condition) throw new AssertionError("Item template assertion failed");
  }
}
