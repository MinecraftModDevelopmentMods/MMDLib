package zone.moddev.mc.lib.compat.CraftTweaker;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.UnaryOperator;

import javax.imageio.ImageIO;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;

/**
 * Generates the resources script materials, blocks and items need and cannot get from a mod: blockstates,
 * models, English names and (for materials) colored textures as assets, and block loot tables and
 * tags as data. Assets come from {@link MaterialTemplates}, with {@link MaterialTemplates#TOKEN}
 * replaced by the material's or block's name; material textures are multiplied by the material
 * color. Recipes are left to scripts.
 */
final class ScriptMaterialResources {
  private static final String T = MaterialTemplates.TOKEN;
  private static final String NS = MMDLib.MODID;
  private static final Set<String> ARMOR = Set.of("helmet", "chestplate", "leggings", "boots");
  private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

  private ScriptMaterialResources() {}

  /**
   * Client resources for script materials and blocks, keyed by path in the pack ({@code
   * assets/...}). Material textures are generated in the material color; block textures are not
   * generated, and any the {@code userFolder} pack does not supply are logged.
   *
   * @throws IllegalStateException if a template references a template that does not exist
   */
  static SortedMap<String, byte[]> assets(
      Collection<MaterialFactory.Result> materials,
      Collection<MaterialFactory.Result> blocks,
      Collection<MaterialFactory.Result> items,
      Map<ResourceLocation, ResourceLocation> basedOn,
      MaterialTemplates templates,
      Path userFolder) {
    return assets(materials, blocks, items, basedOn, templates, userFolder, Map.of());
  }

  static SortedMap<String, byte[]> assets(
      Collection<MaterialFactory.Result> materials, Collection<MaterialFactory.Result> blocks,
      Collection<MaterialFactory.Result> items, Map<ResourceLocation, ResourceLocation> basedOn,
      MaterialTemplates templates, Path userFolder, Map<String, ScriptResourceOptions> options) {
    var files = new TreeMap<String, byte[]>();
    var lang = new TreeMap<String, String>();
    for (var result : materials) {
      var material = result.material();
      var name = name(material);
      var plan = result.content();
      // Output texture path -> template texture path.
      var textures = new TreeMap<String, String>();
      // A part without a template, such as a code-registered type from another mod, is skipped:
      // its resources come from the mod that registers it.
      plan.items()
          .forEach(
              (part, item) ->
                  templatePart(part, "models/item/", templates, basedOn)
                      .ifPresent(
                          tpl ->
                              expand(
                                  "models/item/" + T + "_" + tpl,
                                  materialNames(tpl, item.name().getPath(), name),
                                  templates,
                                  files,
                                  textures)));
      plan.blocks()
          .forEach(
              (part, block) ->
                  templatePart(part, "blockstates/", templates, basedOn)
                      .ifPresent(
                          tpl ->
                              expand(
                                  "blockstates/" + T + "_" + tpl,
                                  materialNames(tpl, block.name().getPath(), name),
                                  templates,
                                  files,
                                  textures)));
      if (plan.fluid() != null) {
        expand("blockstates/" + T + "_molten", s -> s.replace(T, name), templates, files, textures);
        expand(
            "models/item/" + T + "_molten_bucket", s -> s.replace(T, name), templates, files, textures);
      }
      if (plan.items().keySet().stream().anyMatch(part -> ARMOR.contains(part.id()))) {
        for (var layer : List.of("_layer_1", "_layer_2"))
          textures.put(
              "models/armor/" + name + layer, "models/armor/" + T + layer);
      }
      if (plan.items().keySet().stream().anyMatch(part -> part.id().equals("horse_armor")))
        textures.put("entity/horse/armor/horse_armor_" + name, "entity/horse/armor/horse_armor_" + T);
      int color = material.properties().color();
      textures.forEach(
          (out, texture) -> {
            var tint =
                templates
                    .read("textures/" + texture + ".png")
                    .orElseThrow(() -> new IllegalStateException("No texture template " + texture));
            var target = "assets/" + NS + "/textures/" + out + ".png";
            files.put(
                target,
                colorize(tint, templates.read("textures/" + texture + ".untinted.png"), color));
            templates
                .read("textures/" + texture + ".png.mcmeta")
                .ifPresent(meta -> files.put(target + ".mcmeta", meta));
          });
      var display = options.getOrDefault(ScriptResourceOptions.key("material",
          material.name().orElseThrow()), ScriptResourceOptions.DEFAULT).label(display(name), display(name));
      plan.items()
          .forEach(
              (part, item) -> {
                if (!plan.blocks().containsKey(part))
                  lang.put("item." + NS + "." + item.name().getPath(), ScriptResourceOptions.find(options, "item", item.name(), "item_module", part.templateId()).label(label(part.id(), display), display));
              });
      plan.blocks()
          .forEach(
              (part, block) ->
                  lang.put("block." + NS + "." + block.name().getPath(), ScriptResourceOptions.find(options, "block", block.name(), "block_module", part.templateId()).label(label(part.id(), display), display)));
      plan.entities()
          .forEach(
              (part, entity) ->
                  lang.put("entity." + NS + "." + entity.name().getPath(), label(part.id(), display)));
      if (plan.fluid() != null) {
        lang.put("fluid_type." + NS + "." + name + "_molten", "Molten " + display);
        lang.put("item." + NS + "." + name + "_molten_bucket", "Molten " + display + " Bucket");
      }
    }
    for (var result : blocks)
      result
          .content()
          .blocks()
          .forEach(
              (part, block) -> {
                var id = block.name();
                lang.put("block." + id.getNamespace() + "." + id.getPath(), ScriptResourceOptions.find(options, "block", id, "block_module", part.templateId()).label(display(id.getPath()), display(id.getPath())));
                var tpl =
                    templatePart(part, "blockstates/", templates, basedOn)
                        // A plain block that turns to face its placer gets front and top faces.
                        .map(found -> found.equals("block") && directional(part) ? "block_facing" : found);
                if (tpl.isEmpty()) {
                  noTemplate("block", id, part);
                  return;
                }
                var textures = new TreeMap<String, String>();
                var names = ownNames(tpl.get(), id.getPath());
                expand("blockstates/" + T + "_" + tpl.get(), names, templates, files, textures);
                if (block.item() != null)
                  expand("models/item/" + T + "_" + tpl.get(), names, templates, files, textures);
                reportMissing("block", id, textures, userFolder);
              });
    for (var result : items)
      result
          .content()
          .items()
          .forEach(
              (part, item) -> {
                var id = item.name();
                lang.put("item." + id.getNamespace() + "." + id.getPath(), ScriptResourceOptions.find(options, "item", id, "item_module", part.templateId()).label(display(id.getPath()), display(id.getPath())));
                var tpl = templatePart(part, "models/item/", templates, basedOn);
                if (tpl.isEmpty()) {
                  noTemplate("item", id, part);
                  return;
                }
                var textures = new TreeMap<String, String>();
                expand(
                    "models/item/" + T + "_" + tpl.get(),
                    ownNames(tpl.get(), id.getPath()),
                    templates,
                    files,
                    textures);
                reportMissing("item", id, textures, userFolder);
              });
    if (!lang.isEmpty())
      files.put("assets/" + NS + "/lang/en_us.json", json(lang).getBytes(StandardCharsets.UTF_8));
    return files;
  }

  /** Whether the part's redstone settings turn it to face its placer. */
  private static boolean directional(MaterialPart part) {
    var json = part.factoryData().json();
    if (!json.isJsonObject() || !json.getAsJsonObject().has("redstone")) return false;
    var redstone = json.getAsJsonObject().get("redstone");
    return redstone.isJsonObject()
        && redstone.getAsJsonObject().has("facing")
        && redstone.getAsJsonObject().get("facing").getAsBoolean();
  }

  private static void noTemplate(String kind, ResourceLocation id, MaterialPart part) {
    LOGGER.warn(
        "Script {} {} uses {}, which has no MMDLib template; supply its models and textures in"
            + " scripts/mmdlibdata",
        kind,
        id,
        part.templateId());
  }

  private static void reportMissing(
      String kind, ResourceLocation id, Map<String, String> textures, Path userFolder) {
    var missing =
        textures.keySet().stream()
            .map(out -> "assets/" + NS + "/textures/" + out + ".png")
            .filter(path -> !Files.isRegularFile(userFolder.resolve(path)))
            .toList();
    if (!missing.isEmpty())
      LOGGER.warn("Script {} {} needs these textures in {}: {}", kind, id, userFolder, missing);
  }

  /**
   * The template a part uses: its module's own ({@code __material___<id>}) if MMDLib has one, else
   * that of the module a script module was copied from (and so on), else its Java factory's. So a
   * script module copied from {@code mmdlib:stairs} looks like stairs.
   */
  private static Optional<String> templatePart(
      MaterialPart part,
      String folder,
      MaterialTemplates templates,
      Map<ResourceLocation, ResourceLocation> basedOn) {
    var candidates = new ArrayList<String>();
    var seen = new HashSet<ResourceLocation>();
    for (var module = part.templateId(); module != null && seen.add(module); module = basedOn.get(module))
      if (module.getNamespace().equals(NS)) candidates.add(module.getPath());
    if (part.factory().getNamespace().equals(NS)) candidates.add(part.factory().getPath());
    return candidates.stream()
        .filter(c -> templates.read("assets/" + NS + "/" + folder + T + "_" + c + ".json").isPresent())
        .findFirst();
  }

  /** Names a material part's files: {@code __material___stairs_inner} -> {@code copper_stairs_inner}. */
  private static UnaryOperator<String> materialNames(String tpl, String output, String material) {
    return s -> s.replace(T + "_" + tpl, output).replace(T, material);
  }

  /**
   * Names a standalone block's or item's files after its ID: {@code marble_stairs}, {@code
   * marble_stairs_inner}, and the material block texture becomes the block's own texture.
   */
  private static UnaryOperator<String> ownNames(String tpl, String id) {
    return s ->
        s.replace(T + "_" + tpl, id)
            .replace(T + "_block", id)
            .replace(T + "_", id + "_")
            .replace(T, id);
  }

  /**
   * Writes the template {@code root} and every template it references, renamed by {@code names},
   * into {@code files}, and collects the textures they use as output path -> template path.
   */
  private static void expand(
      String root,
      UnaryOperator<String> names,
      MaterialTemplates templates,
      Map<String, byte[]> files,
      Map<String, String> textures) {
    var json = new ArrayDeque<String>(List.of(root));
    var seen = new HashSet<String>();
    while (!json.isEmpty()) {
      var path = json.pop();
      if (!seen.add(path)) continue;
      // Templates referenced from other templates must exist.
      var text =
          new String(
              templates
                  .read("assets/" + NS + "/" + path + ".json")
                  .orElseThrow(() -> new IllegalStateException("No material template " + path)),
              StandardCharsets.UTF_8);
      var used = new TreeSet<String>();
      references(JsonParser.parseString(text), null, json, used);
      used.forEach(texture -> textures.put(names.apply(texture), texture));
      files.put(
          "assets/" + NS + "/" + names.apply(path) + ".json",
          names.apply(text).getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * Server data for script materials and blocks: block loot tables and tags, keyed by path in the
   * pack.
   */
  static SortedMap<String, byte[]> data(
      Collection<MaterialFactory.Result> materials,
      Collection<MaterialFactory.Result> blocks,
      Collection<MaterialFactory.Result> items,
      Map<ResourceLocation, Set<ResourceLocation>> itemTags) {
    return data(materials, blocks, items, itemTags, Map.of());
  }

  static SortedMap<String, byte[]> data(
      Collection<MaterialFactory.Result> materials, Collection<MaterialFactory.Result> blocks,
      Collection<MaterialFactory.Result> items, Map<ResourceLocation, Set<ResourceLocation>> itemTags,
      Map<String, ScriptResourceOptions> options) {
    var files = new TreeMap<String, byte[]>();
    var tags = new TreeMap<String, TreeSet<String>>();
    for (var result : materials) {
      var material = result.material();
      var name = name(material);
      var plan = result.content();
      var properties = material.properties();
      plan.items()
          .forEach(
              (part, item) -> {
                if (!plan.blocks().containsKey(part))
                  itemTags(part.id(), name, item.name().toString(), tags);
                // Tags a script item module gives its parts.
                itemTags
                    .getOrDefault(part.templateId(), Set.of())
                    .forEach(tag -> tag(tags, "item", tag.toString(), item.name().toString()));
              });
      var hardness = properties.hardness();
      var level =
          hardness >= 10 ? "diamond" : hardness >= 8 ? "iron" : hardness >= 5 ? "stone" : null;
      plan.blocks()
          .forEach(
              (part, block) -> {
                var id = block.name().toString();
                block.tags().forEach(tag -> tag(tags, "block", tag.toString(), id));
                properties.harvestTags().forEach(tag -> tag(tags, "block", tag.toString(), id));
                tag(tags, "block", "minecraft:mineable/pickaxe", id);
                if (level != null) tag(tags, "block", "minecraft:needs_" + level + "_tool", id);
                var hasSlab =
                    plan.blocks().keySet().stream().anyMatch(other -> other.id().equals("slab"));
                lootTable(part.factory(), id, NS + ":" + name + "_slab", hasSlab)
                    .ifPresent(
                        table ->
                            files.put(
                                "data/" + NS + "/loot_table/blocks/" + block.name().getPath() + ".json",
                                table.getBytes(StandardCharsets.UTF_8)));
              });
      if (plan.fluid() != null) {
        var fluid = plan.fluid().name();
        tag(tags, "fluid", "c:molten_" + name, fluid.toString());
        tag(tags, "fluid", "c:molten_" + name, NS + ":flowing_" + fluid.getPath());
      }
    }
    for (var result : blocks)
      result
          .content()
          .blocks()
          .forEach(
              (part, block) -> {
                var id = block.name().toString();
                block.tags().forEach(tag -> tag(tags, "block", tag.toString(), id));
                lootTable(part.factory(), id, null, false)
                    .ifPresent(
                        table ->
                            files.put(
                                "data/" + block.name().getNamespace() + "/loot_table/blocks/"
                                    + block.name().getPath() + ".json",
                                table.getBytes(StandardCharsets.UTF_8)));
              });
    for (var result : items)
      result
          .content()
          .items()
          .values()
          .forEach(
              item ->
                  itemTags
                      .getOrDefault(item.name(), Set.of())
                      .forEach(tag -> tag(tags, "item", tag.toString(), item.name().toString())));
    tags.forEach(
        (key, values) -> {
          var parts = key.split("\\|", 2);
          var id = ResourceLocation.parse(parts[1]);
          var body = new JsonObject();
          body.addProperty("replace", false);
          var list = new JsonArray();
          values.forEach(list::add);
          body.add("values", list);
          files.put(
              "data/" + id.getNamespace() + "/tags/" + parts[0] + "/" + id.getPath() + ".json",
              json(body).getBytes(StandardCharsets.UTF_8));
        });
    java.util.stream.Stream.concat(materials.stream(), blocks.stream()).forEach(result ->
        result.content().blocks().forEach((part, block) -> {
          var id = block.name();
          var option = ScriptResourceOptions.find(options, "block", id, "block_module", part.templateId());
          var path = "data/" + id.getNamespace() + "/loot_table/blocks/" + id.getPath() + ".json";
          switch (option.loot()) {
            case "default" -> {
              // A double slab intentionally has no own item and drops its matching slabs.
              if (block.item() == null && !part.factory().equals(ResourceLocation.parse("mmdlib:double_slab")))
                files.remove(path);
            }
            case "none" -> files.put(path, "{\"type\":\"minecraft:block\",\"pools\":[]}".getBytes(StandardCharsets.UTF_8));
            case "self" -> {
              if (block.item() == null) throw new IllegalArgumentException("dropsSelf requires an item: " + id);
              files.put(path, lootTable(ResourceLocation.parse("mmdlib:block"), id.toString(), null, false)
                  .orElseThrow().getBytes(StandardCharsets.UTF_8));
            }
            case "table" -> {
              if (option.table().equals(id.withPrefix("blocks/").toString()))
                throw new IllegalArgumentException("Loot table cannot reference itself: " + id);
              var table = new JsonObject();
              table.addProperty("type", "minecraft:block");
              var pool = new JsonObject(); pool.addProperty("rolls", 1);
              var entry = new JsonObject(); entry.addProperty("type", "minecraft:loot_table");
              entry.addProperty("value", option.table());
              var entries = new JsonArray(); entries.add(entry); pool.add("entries", entries);
              var pools = new JsonArray(); pools.add(pool); table.add("pools", pools);
              files.put(path, json(table).getBytes(StandardCharsets.UTF_8));
            }
            default -> throw new IllegalArgumentException("Unknown loot mode: " + option.loot());
          }
        }));
    return files;
  }

  /** A material's name as used in its registry IDs: the path of its ID. */
  static String name(MMDMaterial material) {
    return material.name().orElseThrow().getPath();
  }

  /** {@code cold_iron} as {@code Cold Iron}. */
  static String display(String name) {
    var words = new ArrayList<String>();
    for (var word : name.split("[_/]"))
      if (!word.isEmpty()) words.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
    return String.join(" ", words);
  }

  /** The English name of a part of the material named {@code display}. */
  static String label(String part, String display) {
    return switch (part) {
      case "block" -> "Block of " + display;
      case "smallpowder" -> display + " Small Dust";
      case "smallblend" -> display + " Small Blend";
      case "powder_dirty" -> "Dirty " + display + " Dust";
      case "netherore" -> "Nether " + display + " Ore";
      case "endore" -> "End " + display + " Ore";
      case "weighted_pressure_plate" -> "Weighted " + display + " Pressure Plate";
      default -> display + " " + display(part.substring(part.indexOf(':') + 1));
    };
  }

  private static void itemTags(
      String part, String name, String id, Map<String, TreeSet<String>> tags) {
    switch (part) {
      case "bow" -> tag(tags, "item", "minecraft:enchantable/bow", id);
      case "crossbow" -> tag(tags, "item", "minecraft:enchantable/crossbow", id);
      case "fishing_rod" -> tag(tags, "item", "minecraft:enchantable/fishing", id);
      default -> {}
    }
    if (Set.of(
            "bow", "crossbow", "fishing_rod", "shears", "shield", "axe", "pickaxe", "shovel", "hoe",
            "sword", "crackhammer", "scythe", "helmet", "chestplate", "leggings", "boots")
        .contains(part)) tag(tags, "item", "minecraft:enchantable/durability", id);
    if (Set.of("axe", "pickaxe", "shovel", "hoe", "crackhammer", "scythe").contains(part)) {
      tag(tags, "item", "minecraft:enchantable/mining", id);
      tag(tags, "item", "minecraft:enchantable/mining_loot", id);
    }
    if (Set.of("sword", "axe", "scythe", "crackhammer").contains(part)) {
      tag(tags, "item", "minecraft:enchantable/weapon", id);
      tag(tags, "item", "minecraft:enchantable/sharp_weapon", id);
    }
    if (part.equals("sword")) tag(tags, "item", "minecraft:enchantable/sword", id);
    if (part.equals("arrow") || part.equals("bolt")) {
      tag(tags, "item", "minecraft:arrows", id);
      tag(tags, "item", "c:arrows", id);
    }
    if (Set.of("pickaxe", "axe", "shovel", "hoe", "sword").contains(part))
      tag(tags, "item", "minecraft:" + part + "s", id);
    if (ARMOR.contains(part)) {
      tag(tags, "item", "minecraft:enchantable/armor", id);
      tag(
          tags,
          "item",
          "minecraft:enchantable/"
              + switch (part) {
                case "helmet" -> "head_armor";
                case "chestplate" -> "chest_armor";
                case "leggings" -> "leg_armor";
                default -> "foot_armor";
              },
          id);
    }
    var common =
        switch (part) {
          case "dust" -> "dusts";
          case "ingot" -> "ingots";
          case "nugget" -> "nuggets";
          case "rod" -> "rods";
          case "gear" -> "gears";
          default -> null;
        };
    if (common != null) {
      tag(tags, "item", "c:" + common + "/" + name, id);
      tag(tags, "item", "c:" + common, "#c:" + common + "/" + name);
    }
  }

  /**
   * A block's loot table, by its Java factory: slabs drop two when double, a double slab drops two
   * {@code slab}s (nothing without one), doors drop from their lower half, anvils keep their wear.
   */
  private static Optional<String> lootTable(
      ResourceLocation factory, String id, String slab, boolean hasSlab) {
    var part = factory.getNamespace().equals(NS) ? factory.getPath() : "";
    var entry = new JsonObject();
    entry.addProperty("type", "minecraft:item");
    var functions = new JsonArray();
    switch (part) {
      case "double_slab" -> {
        // A double slab is two slabs; without a slab part there is nothing to drop.
        if (!hasSlab) return Optional.empty();
        functions.add(setCount(null));
      }
      case "slab" -> {
        var condition = new JsonObject();
        condition.addProperty("condition", "minecraft:block_state_property");
        condition.addProperty("block", id);
        var state = new JsonObject();
        state.addProperty("type", "double");
        condition.add("properties", state);
        functions.add(setCount(condition));
      }
      case "anvil" -> {
        var copy = new JsonObject();
        copy.addProperty("function", "minecraft:copy_state");
        copy.addProperty("block", id);
        var properties = new JsonArray();
        properties.add("damage");
        copy.add("properties", properties);
        functions.add(copy);
      }
      default -> {}
    }
    entry.addProperty("name", part.equals("double_slab") ? slab : id);
    if (!functions.isEmpty()) entry.add("functions", functions);
    var conditions = new JsonArray();
    var survives = new JsonObject();
    survives.addProperty("condition", "minecraft:survives_explosion");
    conditions.add(survives);
    if (part.equals("door")) {
      var lower = new JsonObject();
      lower.addProperty("condition", "minecraft:block_state_property");
      lower.addProperty("block", id);
      var half = new JsonObject();
      half.addProperty("half", "lower");
      lower.add("properties", half);
      conditions.add(lower);
    }
    var pool = new JsonObject();
    pool.addProperty("rolls", 1);
    var entries = new JsonArray();
    entries.add(entry);
    pool.add("entries", entries);
    pool.add("conditions", conditions);
    var table = new JsonObject();
    table.addProperty("type", "minecraft:block");
    var pools = new JsonArray();
    pools.add(pool);
    table.add("pools", pools);
    return Optional.of(json(table));
  }

  private static JsonObject setCount(JsonObject condition) {
    var function = new JsonObject();
    function.addProperty("function", "minecraft:set_count");
    function.addProperty("count", 2);
    if (condition != null) {
      var conditions = new JsonArray();
      conditions.add(condition);
      function.add("conditions", conditions);
    }
    return function;
  }

  private static void tag(Map<String, TreeSet<String>> tags, String kind, String tag, String id) {
    tags.computeIfAbsent(kind + "|" + tag, key -> new TreeSet<>()).add(id);
  }

  /**
   * Collects template references below {@code element}: models (under "model" or "parent") into
   * {@code models} and textures (under "textures") into {@code textures}, both as template paths.
   */
  private static void references(
      JsonElement element, String key, Deque<String> models, Set<String> textures) {
    if (element.isJsonObject())
      element.getAsJsonObject().entrySet().forEach(e -> references(e.getValue(), e.getKey(), models, textures));
    else if (element.isJsonArray())
      element.getAsJsonArray().forEach(child -> references(child, key, models, textures));
    else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
      var value = element.getAsString();
      if (!value.startsWith(NS + ":") || !value.contains(T)) return;
      var path = value.substring(NS.length() + 1);
      if ("model".equals(key) || "parent".equals(key)) models.add("models/" + path);
      else if (!"fluid".equals(key)) textures.add(path);
    }
  }

  /**
   * The template's grayscale pixels multiplied by {@code argb}, with the untinted layer drawn over
   * them, as PNG bytes.
   */
  static byte[] colorize(byte[] tint, Optional<byte[]> untinted, int argb) {
    try {
      var base = ImageIO.read(new ByteArrayInputStream(tint));
      var over = untinted.isPresent() ? ImageIO.read(new ByteArrayInputStream(untinted.get())) : null;
      int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
      var out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < base.getHeight(); y++)
        for (int x = 0; x < base.getWidth(); x++) {
          int p = base.getRGB(x, y);
          int gray = (p >> 16) & 255;
          int pixel =
              (p & 0xFF000000) | (gray * r / 255) << 16 | (gray * g / 255) << 8 | gray * b / 255;
          if (over != null) pixel = over(over.getRGB(x, y), pixel);
          out.setRGB(x, y, pixel);
        }
      var bytes = new ByteArrayOutputStream();
      ImageIO.write(out, "png", bytes);
      return bytes.toByteArray();
    } catch (IOException ex) {
      throw new UncheckedIOException("Cannot color a material template", ex);
    }
  }

  /** Source-over alpha compositing of two ARGB pixels. */
  private static int over(int top, int bottom) {
    int ta = top >>> 24;
    if (ta == 255) return top;
    if (ta == 0) return bottom;
    int ba = bottom >>> 24;
    int a = ta + ba * (255 - ta) / 255;
    int[] result = new int[3];
    for (int shift = 16, i = 0; shift >= 0; shift -= 8, i++) {
      int tc = (top >> shift) & 255, bc = (bottom >> shift) & 255;
      result[i] = a == 0 ? 0 : (tc * ta + bc * ba * (255 - ta) / 255) / a;
    }
    return a << 24 | result[0] << 16 | result[1] << 8 | result[2];
  }

  private static String json(Map<String, String> values) {
    var object = new JsonObject();
    values.forEach(object::addProperty);
    return json(object);
  }

  private static String json(JsonElement element) {
    return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(element)
        + "\n";
  }
}
