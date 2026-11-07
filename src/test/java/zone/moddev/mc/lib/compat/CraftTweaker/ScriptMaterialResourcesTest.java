package zone.moddev.mc.lib.compat.CraftTweaker;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.builders.MaterialBuilder;
import zone.moddev.mc.lib.common.factories.MaterialFactory;

final class ScriptMaterialResourcesTest {
  private static final Path TEMPLATES = Path.of("src/main/resources/mmdlib_templates");

  private static MaterialFactory.Result everything() {
    var material =
        MaterialBuilder.create()
            .setName("mmdlib:cold_iron")
            .setColor(0xFF4080C0)
            .setHardness(8)
            .setHarvestTags(java.util.Set.of(net.minecraft.resources.ResourceLocation.parse("c:test")))
            .build();
    return new MaterialFactory(MaterialExtensions.GLOBAL, mod -> true).create(material);
  }

  @Test
  void generatesCompleteAssetsForEveryPart() throws Exception {
    var files =
        ScriptMaterialResources.assets(
            List.of(everything()), List.of(), List.of(), Map.of(), new MaterialTemplates(List.of(TEMPLATES)), TEMPLATES);
    var textures = new HashSet<String>();
    var models = new HashSet<String>();
    for (var entry : files.entrySet()) {
      var path = entry.getKey();
      require(!path.contains(MaterialTemplates.TOKEN), "token left in path " + path);
      if (!path.endsWith(".json")) continue;
      var text = new String(entry.getValue(), StandardCharsets.UTF_8);
      require(!text.contains(MaterialTemplates.TOKEN), "token left in " + path);
      collect(JsonParser.parseString(text), null, textures, models);
    }
    for (var model : models)
      require(files.containsKey("assets/mmdlib/models/" + model + ".json"), "missing model " + model);
    for (var texture : textures)
      require(
          files.containsKey("assets/mmdlib/textures/" + texture + ".png"),
          "missing texture " + texture);
    for (var path :
        List.of(
            "assets/mmdlib/blockstates/cold_iron_stairs.json",
            "assets/mmdlib/blockstates/cold_iron_anvil.json",
            "assets/mmdlib/models/item/cold_iron_bow.json",
            "assets/mmdlib/models/item/cold_iron_molten_bucket.json",
            "assets/mmdlib/textures/models/armor/cold_iron_layer_1.png",
            "assets/mmdlib/textures/entity/horse/armor/horse_armor_cold_iron.png",
            "assets/mmdlib/lang/en_us.json"))
      require(files.containsKey(path), "missing " + path);
    var lang =
        JsonParser.parseString(new String(files.get("assets/mmdlib/lang/en_us.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
    require(lang.get("item.mmdlib.cold_iron_ingot").getAsString().equals("Cold Iron Ingot"), "ingot name");
    require(lang.get("block.mmdlib.cold_iron_block").getAsString().equals("Block of Cold Iron"), "block name");
    require(lang.get("fluid_type.mmdlib.cold_iron_molten").getAsString().equals("Molten Cold Iron"), "fluid name");
    require(!lang.has("item.mmdlib.cold_iron_stairs"), "block items use the block's name");
  }

  @Test
  void colorizesGrayscaleAndKeepsUntintedPixels() throws Exception {
    var gray = Files.readAllBytes(TEMPLATES.resolve("textures/block/__material___block.png"));
    var image = ImageIO.read(new ByteArrayInputStream(ScriptMaterialResources.colorize(gray, Optional.empty(), 0xFFFF8000)));
    var source = ImageIO.read(new ByteArrayInputStream(gray));
    int g = (source.getRGB(5, 5) >> 16) & 255, p = image.getRGB(5, 5);
    require(((p >> 16) & 255) == g && ((p >> 8) & 255) == g * 0x80 / 255 && (p & 255) == 0, "multiplied");
    var tint = Files.readAllBytes(TEMPLATES.resolve("textures/item/__material___pickaxe.png"));
    var keep = Files.readAllBytes(TEMPLATES.resolve("textures/item/__material___pickaxe.untinted.png"));
    var handle = ImageIO.read(new ByteArrayInputStream(keep));
    var out = ImageIO.read(new ByteArrayInputStream(ScriptMaterialResources.colorize(tint, Optional.of(keep), 0xFF0000FF)));
    boolean checked = false;
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++)
        if ((handle.getRGB(x, y) >>> 24) == 255) {
          require(out.getRGB(x, y) == handle.getRGB(x, y), "handle pixel recolored");
          checked = true;
        }
    require(checked, "pickaxe template has a handle");
  }

  @Test
  void generatesLootAndTags() {
    var files = ScriptMaterialResources.data(List.of(everything()), List.of(), List.of(), Map.of());
    var slab = text(files, "data/mmdlib/loot_table/blocks/cold_iron_double_slab.json");
    require(slab.contains("mmdlib:cold_iron_slab") && slab.contains("set_count"), "double slab drops slabs");
    require(text(files, "data/mmdlib/loot_table/blocks/cold_iron_door.json").contains("\"half\": \"lower\""), "door lower half");
    require(text(files, "data/minecraft/tags/block/needs_iron_tool.json").contains("mmdlib:cold_iron_block"), "harvest level");
    require(text(files, "data/c/tags/block/test.json").contains("mmdlib:cold_iron_ore"), "harvest tags");
    require(text(files, "data/minecraft/tags/block/walls.json").contains("mmdlib:cold_iron_wall"), "template tags");
    require(text(files, "data/minecraft/tags/item/arrows.json").contains("mmdlib:cold_iron_arrow"), "arrows");
    require(text(files, "data/c/tags/item/ingots/cold_iron.json").contains("mmdlib:cold_iron_ingot"), "c:ingots");
    require(text(files, "data/c/tags/fluid/molten_cold_iron.json").contains("mmdlib:flowing_cold_iron_molten"), "fluid tag");
  }

  @Test
  void generatesStandaloneBlocksNamedAfterTheirId() {
    var stairs = net.minecraft.resources.ResourceLocation.parse("mmdlib:stairs");
    var marble =
        new zone.moddev.mc.lib.common.types.StandaloneBlock(
                stairs,
                zone.moddev.mc.lib.common.types.FactoryData.EMPTY,
                Optional.empty(),
                zone.moddev.mc.lib.common.types.MaterialRuntimeSettings.DEFAULT)
            .resolve(
                net.minecraft.resources.ResourceLocation.parse("mmdlib:marble_stairs"),
                MaterialExtensions.GLOBAL.blockModule(stairs));
    var files =
        ScriptMaterialResources.assets(
            List.of(), List.of(marble), List.of(), Map.of(), new MaterialTemplates(List.of(TEMPLATES)), TEMPLATES);
    require(files.containsKey("assets/mmdlib/blockstates/marble_stairs.json"), "blockstate");
    require(files.containsKey("assets/mmdlib/models/block/marble_stairs_inner.json"), "inner model");
    require(files.containsKey("assets/mmdlib/models/item/marble_stairs.json"), "item model");
    require(
        text(files, "assets/mmdlib/models/block/marble_stairs.json").contains("mmdlib:block/marble_stairs\""),
        "uses its own texture");
    require(files.keySet().stream().noneMatch(path -> path.endsWith(".png")), "no generated textures");
    require(text(files, "assets/mmdlib/lang/en_us.json").contains("\"Marble Stairs\""), "name");
    var data = ScriptMaterialResources.data(List.of(), List.of(marble), List.of(), Map.of());
    require(data.containsKey("data/mmdlib/loot_table/blocks/marble_stairs.json"), "loot");
  }

  @Test
  void materialPartsFromNewModulesUseTheirFactoryTemplate() {
    var api = new MaterialExtensions();
    var stairs = api.blockModule(net.minecraft.resources.ResourceLocation.parse("mmdlib:stairs"));
    api.registerBlockModule(net.minecraft.resources.ResourceLocation.parse("mmdlib:test_steps"), stairs);
    var material =
        MaterialBuilder.create()
            .setName("mmdlib:tin")
            .setSelection(
                new zone.moddev.mc.lib.common.types.MaterialSelection(
                    java.util.Set.of(),
                    java.util.Set.of(api.snapshot().parts().resolve("test_steps").getOrThrow()),
                    java.util.Set.of(),
                    false))
            .build();
    var result = new MaterialFactory(api, mod -> true).create(material);
    var files =
        ScriptMaterialResources.assets(
            List.of(result), List.of(), List.of(), Map.of(), new MaterialTemplates(List.of(TEMPLATES)), TEMPLATES);
    require(files.containsKey("assets/mmdlib/blockstates/tin_test_steps.json"), "blockstate");
    require(files.containsKey("assets/mmdlib/models/block/tin_test_steps_outer.json"), "outer model");
    require(files.containsKey("assets/mmdlib/textures/block/tin_block.png"), "material texture");
    require(text(files, "assets/mmdlib/lang/en_us.json").contains("\"Tin Test Steps\""), "name");
  }

  @Test
  void directionalRedstoneBlocksTurnToFaceTheirPlacer() {
    var block = net.minecraft.resources.ResourceLocation.parse("mmdlib:block");
    var gate =
        new zone.moddev.mc.lib.common.types.StandaloneBlock(
                block,
                new zone.moddev.mc.lib.common.types.FactoryData(
                    JsonParser.parseString("{\"redstone\":{\"facing\":true,\"output\":\"state\"}}")),
                Optional.empty(),
                zone.moddev.mc.lib.common.types.MaterialRuntimeSettings.DEFAULT)
            .resolve(
                net.minecraft.resources.ResourceLocation.parse("mmdlib:nand_gate"),
                MaterialExtensions.GLOBAL.blockModule(block));
    var files =
        ScriptMaterialResources.assets(
            List.of(), List.of(gate), List.of(), Map.of(), new MaterialTemplates(List.of(TEMPLATES)), TEMPLATES);
    var state = text(files, "assets/mmdlib/blockstates/nand_gate.json");
    require(state.contains("facing=east") && state.contains("\"y\": 90"), "turned variants");
    var model = text(files, "assets/mmdlib/models/block/nand_gate.json");
    require(
        model.contains("mmdlib:block/nand_gate_front")
            && model.contains("mmdlib:block/nand_gate_top")
            && model.contains("\"side\": \"mmdlib:block/nand_gate\""),
        "front, top and side textures");
  }

  private static MaterialFactory.Result item(String id, String module) {
    var rl = net.minecraft.resources.ResourceLocation.parse(module);
    return new zone.moddev.mc.lib.common.types.StandaloneItem(
            rl,
            zone.moddev.mc.lib.common.types.FactoryData.EMPTY,
            Optional.empty(),
            zone.moddev.mc.lib.common.types.MaterialRuntimeSettings.DEFAULT)
        .resolve(net.minecraft.resources.ResourceLocation.parse(id), MaterialExtensions.GLOBAL.itemModule(rl));
  }

  @Test
  void generatesStandaloneItemsNamedAfterTheirId() {
    var wrench = item("mmdlib:wrench", "mmdlib:ingot");
    var longbow = item("mmdlib:longbow", "mmdlib:bow");
    var files =
        ScriptMaterialResources.assets(
            List.of(), List.of(), List.of(wrench, longbow), Map.of(),
            new MaterialTemplates(List.of(TEMPLATES)), TEMPLATES);
    require(text(files, "assets/mmdlib/models/item/wrench.json").contains("mmdlib:item/wrench\""), "plain item texture");
    require(text(files, "assets/mmdlib/models/item/longbow.json").contains("mmdlib:item/longbow_pulling_0"), "bow overrides");
    require(files.containsKey("assets/mmdlib/models/item/longbow_pulling_2.json"), "pulling model");
    require(text(files, "assets/mmdlib/models/item/longbow.json").contains("mmdlib:item/longbow_standby"), "bow texture");
    require(files.keySet().stream().noneMatch(path -> path.endsWith(".png")), "no generated textures");
    require(text(files, "assets/mmdlib/lang/en_us.json").contains("\"item.mmdlib.wrench\": \"Wrench\""), "name");
    var wrenchId = net.minecraft.resources.ResourceLocation.parse("mmdlib:wrench");
    var data =
        ScriptMaterialResources.data(
            List.of(), List.of(), List.of(wrench),
            Map.of(wrenchId, java.util.Set.of(net.minecraft.resources.ResourceLocation.parse("c:tools/wrench"))));
    require(text(data, "data/c/tags/item/tools/wrench.json").contains("mmdlib:wrench"), "item tag");
  }

  @Test
  void materialPartsFromItemModulesUseTheModuleTheyCopy() {
    var api = new MaterialExtensions();
    var ingot = net.minecraft.resources.ResourceLocation.parse("mmdlib:ingot");
    var widget = net.minecraft.resources.ResourceLocation.parse("mmdlib:test_widget");
    api.registerItemModule(widget, api.itemModule(ingot));
    var material =
        MaterialBuilder.create()
            .setName("mmdlib:tin")
            .setSelection(
                new zone.moddev.mc.lib.common.types.MaterialSelection(
                    java.util.Set.of(api.snapshot().parts().resolve("test_widget").getOrThrow()),
                    java.util.Set.of(), java.util.Set.of(), false))
            .build();
    var result = new MaterialFactory(api, mod -> true).create(material);
    var files =
        ScriptMaterialResources.assets(
            List.of(result), List.of(), List.of(), Map.of(widget, ingot),
            new MaterialTemplates(List.of(TEMPLATES)), TEMPLATES);
    require(files.containsKey("assets/mmdlib/models/item/tin_test_widget.json"), "model from the copied module");
    require(files.containsKey("assets/mmdlib/textures/item/tin_test_widget.png"), "colored texture");
    var data =
        ScriptMaterialResources.data(
            List.of(result), List.of(), List.of(),
            Map.of(widget, java.util.Set.of(net.minecraft.resources.ResourceLocation.parse("c:widgets"))));
    require(text(data, "data/c/tags/item/widgets.json").contains("mmdlib:tin_test_widget"), "module tags");
  }

  private static String text(Map<String, byte[]> files, String path) {
    require(files.containsKey(path), "missing " + path);
    return new String(files.get(path), StandardCharsets.UTF_8);
  }

  private static void collect(JsonElement element, String key, java.util.Set<String> textures, java.util.Set<String> models) {
    if (element.isJsonObject())
      element.getAsJsonObject().entrySet().forEach(e -> collect(e.getValue(), e.getKey(), textures, models));
    else if (element.isJsonArray()) element.getAsJsonArray().forEach(e -> collect(e, key, textures, models));
    else if (element.getAsJsonPrimitive().isString()
        && element.getAsString().startsWith("mmdlib:")
        && element.getAsString().contains("cold_iron")
        && !"fluid".equals(key)) {
      // Generated references to this material's models and textures, such as mmdlib:block/cold_iron_block.
      var value = element.getAsString().substring("mmdlib:".length());
      if ("model".equals(key) || "parent".equals(key)) models.add(value);
      else textures.add(value);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
