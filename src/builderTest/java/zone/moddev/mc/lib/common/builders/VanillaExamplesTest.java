package zone.moddev.mc.lib.common.builders;

import java.nio.file.*;
import java.util.*;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.codecs.MMDMaterialCodec;
import zone.moddev.mc.lib.common.loading.DefinitionLoader;

/**
 * Loads the shipped files through the same API as a contributing mod, then checks gameplay
 * resources.
 */
public final class VanillaExamplesTest {
  public static void main(String[] args) throws Exception {
    var source = Path.of(System.getProperty("mmdlib.resources"));
    var generated = Path.of(System.getProperty("mmdlib.generated"));
    var api = new MaterialExtensions();
    var loaded =
        DefinitionLoader.load(
            List.of(
                new DefinitionLoader.SourceRoot(
                    "mmdlib",
                    source.resolve("mmdlib"),
                    VanillaExamplesTest.class.getClassLoader())),
            api,
            mod -> false);
    require(
        loaded
            .materials()
            .keySet()
            .equals(
                Set.of(
                        "wood",
                        "stone",
                        "iron",
                        "gold",
                        "diamond",
                        "coal",
                        "charcoal",
                        "emerald",
                        "ender",
                        "quartz",
                        "obsidian",
                        "lapis",
                        "prismarine",
                        "redstone",
                        "copper")
                    .stream()
                    .map(name -> id("mmdlib:" + name))
                    .collect(java.util.stream.Collectors.toSet())));
    // Standalone blocks keep their own ID and are built by the named module's factory, merged
    // factory_data included.
    require(loaded.standalone().keySet().equals(Set.of(id("mmdlib:human_detector"))));
    var detector =
        loaded
            .standalone()
            .get(id("mmdlib:human_detector"))
            .content()
            .blocks()
            .entrySet()
            .iterator()
            .next();
    require(
        detector.getValue().name().equals(id("mmdlib:human_detector"))
            && detector.getValue().item().name().equals(id("mmdlib:human_detector")));
    require(
        detector.getKey().factory().equals(id("mmdlib:pressure_plate"))
            && detector.getValue().properties().hardness() == .5F);
    require(
        zone.moddev.mc.lib.common.runtime.StandardBlocks.Detects.of(detector.getKey()).isPresent());
    require(
        Files.isRegularFile(
            generated.resolve("data/mmdlib/loot_table/blocks/human_detector.json")));
    // Crackhammer right-click is data-bound through the bundled extension, not hard-wired in the
    // item.
    var crush = id("mmdlib:crackhammer_crush");
    require(
        api.events()
            .resolvedHandler(crush)
            .definition()
            .handler()
            .endsWith("CrackhammerEvents#crush"));
    var hammers =
        loaded.materials().values().stream()
            .flatMap(result -> result.content().items().entrySet().stream())
            .filter(entry -> entry.getKey().templateId().getPath().equals("crackhammer"))
            .map(Map.Entry::getValue)
            .toList();
    var harvest = id("mmdlib:crackhammer_harvest");
    require(
        api.events()
            .resolvedHandler(harvest)
            .definition()
            .handler()
            .endsWith("CrackhammerEvents#harvest"));
    require(
        !hammers.isEmpty()
            && hammers.stream()
                .allMatch(
                    item ->
                        crush.equals(item.events().get("tool-right-click"))
                            && harvest.equals(item.events().get("tool-harvest"))));
    hammers.forEach(item -> api.events().validate(item.events()));
    // Anvils open their GUI only through the bundled block-right-click binding.
    var openMenu = id("mmdlib:open_menu");
    require(
        api.events()
            .resolvedHandler(openMenu)
            .definition()
            .handler()
            .endsWith("BlockEvents#openMenu"));
    // The vanilla example materials select no anvils, so check the bundled module that any material
    // would use.
    var anvil =
        zone.moddev.mc.lib.common.modules.ModuleCatalog.bundled().blocks().get(id("mmdlib:anvil"));
    require(anvil != null && openMenu.equals(anvil.template().events().get("block-right-click")));
    api.events().validate(anvil.template().events());
    // Fixed stone crushing chain: smooth stone -> stone -> cobblestone -> gravel -> sand; all but
    // sand are crushable.
    // Cobblestone and gravel crush by their c: tags, so every variant follows the chain.
    var chain = List.of("smooth_stone", "stone", "cobblestone", "gravel", "sand");
    var inputs =
        List.of(
            "item=minecraft:smooth_stone",
            "item=minecraft:stone",
            "tag=c:cobblestones",
            "tag=c:gravels");
    var crushable =
        Files.readString(source.resolve("data/mmdlib/tags/block/crackhammer_crushable.json"));
    for (int step = 0; step < chain.size() - 1; step++) {
      var recipe =
          JsonParser.parseString(
                  Files.readString(
                      source.resolve("data/mmdlib/recipe/" + chain.get(step) + "_crushing.json")))
              .getAsJsonObject();
      var input = inputs.get(step).split("=");
      require(
          recipe.get("type").getAsString().equals("mmdlib:crushing")
              && recipe.getAsJsonObject("ingredient").get(input[0]).getAsString().equals(input[1])
              && recipe
                  .getAsJsonObject("result")
                  .get("id")
                  .getAsString()
                  .equals("minecraft:" + chain.get(step + 1)));
      require(crushable.contains("\"minecraft:" + chain.get(step) + "\""));
    }
    require(
        !crushable.contains("\"minecraft:sand\"")
            && crushable.contains("#c:storage_blocks/raw_iron"));
    int items = 0, blocks = 0, fluids = 0, entities = 0;
    var codec = MMDMaterialCodec.forExtensions(api);
    for (var result : loaded.materials().values()) {
      var material = result.material();
      var plan = result.content();
      require(!material.runtime().repairItem().isEmpty());
      require(MaterialBuilder.from(material).build().runtime().equals(material.runtime()));
      var json = codec.encodeStart(JsonOps.INSTANCE, material).getOrThrow();
      require(codec.parse(JsonOps.INSTANCE, json).getOrThrow().equals(material));
      var nbt = codec.encodeStart(NbtOps.INSTANCE, material).getOrThrow();
      require(codec.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(material));
      require(plan.fluid() != null);
      fluids++;
      entities += plan.entities().size();
      for (var item : plan.itemDefinitions().values()) {
        require(
            Files.exists(
                source.resolve("assets/mmdlib/models/item/" + item.name().getPath() + ".json")));
        items++;
      }
      for (var block : plan.blockDefinitions().values()) {
        require(
            Files.exists(
                source.resolve("assets/mmdlib/blockstates/" + block.name().getPath() + ".json")));
        blocks++;
      }
      for (var block : plan.blocks().values())
        require(
            Files.exists(
                generated.resolve(
                    "data/mmdlib/loot_table/blocks/" + block.name().getPath() + ".json")));
      require(
          plan.blocks().values().stream()
              .noneMatch(
                  block ->
                      Set.of("ore", "endore", "netherore").contains(block.template().getPath())));
    }
    require(items == 233 && blocks == 79 && fluids == 15 && entities == 14);
    var iron = loaded.materials().get(id("mmdlib:iron"));
    for (var part :
        List.of("sword", "axe", "pickaxe", "hoe", "helmet", "shears", "ingot", "nugget"))
      require(
          !iron.content().items().containsKey(api.snapshot().parts().resolve(part).getOrThrow()));
    require(
        !iron.content().blocks().containsKey(api.snapshot().parts().resolve("door").getOrThrow()));
    var diamond = loaded.materials().get(id("mmdlib:diamond")).material().properties();
    require(
        diamond.hardness() == 10
            && diamond.strength() == 15
            && diamond.magic() == 4
            && diamond.color() == 0xFF8CF4E1);
    var charcoal = loaded.materials().get(id("mmdlib:charcoal"));
    require(charcoal.material().properties().special().get("block_burn_time") == 16000);
    require(
        loaded
            .materials()
            .get(id("mmdlib:quartz"))
            .material()
            .runtime()
            .blockSetType()
            .equals(id("minecraft:stone")));
    require(!loaded.materials().get(id("mmdlib:emerald")).material().runtime().tintedArmor());
    require(
        loaded
            .materials()
            .get(id("mmdlib:emerald"))
            .material()
            .runtime()
            .armorTexture()
            .equals(id("mmdlib:emerald")));
    require(
        zone.moddev.mc.lib.client.StaticAssetRules.INSTANCE
            .untintedItems()
            .contains(id("mmdlib:emerald_scythe")));
    if (Files.isDirectory(generated.resolve("assets")))
      try (var files = Files.walk(generated.resolve("assets"))) {
        require(
            files.noneMatch(
                Files::isRegularFile)); // Artwork belongs exclusively to static main resources.
      }
    var manifest =
        JsonParser.parseString(
                Files.readString(generated.resolve("data/mmdlib/example_manifest.json")))
            .getAsJsonObject();
    require(
        manifest.get("items").getAsInt() == items
            && manifest.get("materials").getAsInt() == loaded.materials().size()
            && manifest.get("standalone_blocks").getAsInt() == loaded.standalone().size());
    try (var paths = Files.walk(generated)) {
      for (var file :
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".json"))
              .toList()) {
        var json = JsonParser.parseString(Files.readString(file));
        require(json.isJsonObject());
      }
    }
    var crafting = new HashMap<String, Path>();
    try (var paths = Files.list(generated.resolve("data/mmdlib/recipe"))) {
      for (var file : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
        var recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var type = recipe.get("type").getAsString();
        String signature = null;
        if (type.equals("minecraft:crafting_shapeless")) {
          var ingredients = new ArrayList<String>();
          recipe.getAsJsonArray("ingredients").forEach(value -> ingredients.add(value.toString()));
          Collections.sort(ingredients);
          signature = "shapeless:" + ingredients;
        } else if (type.equals("minecraft:crafting_shaped")) {
          var pattern = new ArrayList<String>();
          var mirrored = new ArrayList<String>();
          for (var row : recipe.getAsJsonArray("pattern")) {
            var cells = new ArrayList<String>();
            for (char symbol : row.getAsString().toCharArray())
              cells.add(
                  symbol == ' '
                      ? "_"
                      : recipe.getAsJsonObject("key").get(String.valueOf(symbol)).toString());
            pattern.add(cells.toString());
            Collections.reverse(cells);
            mirrored.add(cells.toString());
          }
          signature =
              "shaped:"
                  + (pattern.toString().compareTo(mirrored.toString()) < 0 ? pattern : mirrored);
          if (recipe.getAsJsonArray("pattern").size() == 1
              && recipe.getAsJsonArray("pattern").get(0).getAsString().length() == 1)
            signature =
                "shapeless:["
                    + recipe
                        .getAsJsonObject("key")
                        .get(recipe.getAsJsonArray("pattern").get(0).getAsString())
                    + "]";
        }
        if (signature != null) {
          var previous = crafting.putIfAbsent(signature, file);
          if (previous != null)
            throw new AssertionError("Ambiguous recipes: " + previous + " and " + file);
        }
      }
    }
    System.out.println(
        "Shipped vanilla definitions: 15 materials, 233 items, 79 blocks including fluids, 14"
            + " projectile types; codec and resource coverage passed");
  }

  private static ResourceLocation id(String value) {
    return ResourceLocation.parse(value);
  }

  private static void require(boolean value) {
    if (!value) throw new AssertionError("Vanilla example assertion failed");
  }
}
