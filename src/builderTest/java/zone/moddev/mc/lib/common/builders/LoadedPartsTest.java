package zone.moddev.mc.lib.common.builders;

import java.io.StringReader;
import java.util.Map;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.codecs.MMDMaterialCodec;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.*;
import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import zone.moddev.mc.lib.common.types.*;

public final class LoadedPartsTest {
  public static void main(String[] args) {
    var api = new MaterialExtensions(false);
    var codec = MMDMaterialCodec.forExtensions(api); // resolves the catalog at each operation
    var factory = new MaterialFactory(api, mod -> false);
    require(
        factory
            .load(id("test:empty"), new StringReader("{\"has_fluid\":false}"))
            .content()
            .items()
            .isEmpty());
    var projectile =
        DataModules.Item.CODEC
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                    """
{"factory":"addon:projectile_item", "part":{"entity_factory":"addon:projectile_entity",
 "aliases":["dart"]},"template":{"max_stack_size":24}}
"""))
            .getOrThrow();
    var panel =
        DataModules.Block.CODEC
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                    """
                    {"factory":"mmdlib:stairs", "template":{}}
                    """))
            .getOrThrow();
    var helmet =
        DataModules.Item.CODEC
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                    """
{"factory":"mmdlib:armor", "factory_data":{"slot":"helmet"},"template":{"max_stack_size":1}}
"""))
            .getOrThrow();
    var gated =
        DataModules.Item.CODEC
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                    """
                    {"factory":"mmdlib:basic_item","requires_mod":"optional_mod","template":{}}
                    """))
            .getOrThrow();
    new ModuleCatalog(
            Map.of(
                id("addon:needle"), projectile, id("addon:cap"), helmet, id("addon:gated"), gated),
            Map.of(id("addon:step"), panel),
            Map.of())
        .install(api);
    var registry = api.snapshot().parts();
    var needle = registry.resolve("addon:dart").getOrThrow();
    require(needle.templateId().equals(id("addon:needle")) && needle.needsEntity());
    require(ModuleCatalog.bundled().parts().resolve("addon:needle").error().isPresent());
    var result =
        factory.load(
            id("test:metal"),
            new StringReader(
                """
                {"items":"all !addon:cap","blocks":"all","entities":"all","has_fluid":false}
                """));
    require(result.material().parts().parts().contains(needle));
    require(result.content().items().size() == 2 && result.content().blocks().size() == 1);
    require(
        result.content().entities().get(needle).factory().equals(id("addon:projectile_entity")));
    require(
        result
            .content()
            .items()
            .get(needle)
            .name()
            .equals(result.content().entities().get(needle).name()));
    require(result.content().items().get(needle).maxStackSize() == 24);
    require(result.content().itemDefinitions().size() == result.content().items().size());
    require(result.content().blockDefinitions().size() == result.content().blocks().size());
    var oldStyle =
        new MaterialContentPlan(
            result.content().material(),
            Map.of(),
            Map.of(),
            Map.of(),
            null,
            result.content().extraItems(),
            result.content().extraBlocks());
    require(oldStyle.itemDefinitions().equals(result.content().itemDefinitions()));
    var conflict =
        ItemBuilder.create()
            .setName(needle.templateId())
            .forMaterial(result.content().material())
            .setMaxStackSize(1)
            .build();
    var conflicting =
        new MaterialContentPlan(
            result.content().material(),
            result.content().items(),
            Map.of(),
            Map.of(),
            null,
            Map.of(needle.templateId(), conflict),
            Map.of());
    expectFailure(conflicting::itemDefinitions);
    var noProjectile =
        factory
            .load(
                id("test:no_entity"),
                new StringReader(
                    """
{"items":"addon:dart","blocks":"","entities":"all !addon:dart","has_fluid":false}
"""))
            .content();
    require(noProjectile.items().isEmpty() && noProjectile.entities().isEmpty());
    var restricted =
        MaterialParts.forRegistry(registry)
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"parts\":[\"addon:dart\"],\"has_fluid\":false}"))
            .getOrThrow();
    require(MaterialSelection.fromParts(restricted).parts().equals(restricted));
    require(
        MaterialParts.forRegistry(registry)
            .parse(
                NbtOps.INSTANCE,
                MaterialParts.forRegistry(registry)
                    .encodeStart(NbtOps.INSTANCE, restricted)
                    .getOrThrow())
            .getOrThrow()
            .equals(restricted));
    var json = codec.encodeStart(JsonOps.INSTANCE, result.material()).getOrThrow();
    require(codec.parse(JsonOps.INSTANCE, json).getOrThrow().equals(result.material()));
    var nbt = codec.encodeStart(NbtOps.INSTANCE, result.material()).getOrThrow();
    require(codec.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(result.material()));
    require(
        new MaterialExtensions()
                .blockImplementation(registry.resolve("addon:step").getOrThrow().factory())
                .blockClass()
            == net.minecraft.world.level.block.StairBlock.class);
    require(
        net.minecraft.world.item.ArmorItem.class.isAssignableFrom(
            new MaterialExtensions()
                .itemImplementation(registry.resolve("addon:cap").getOrThrow().factory())
                .itemClass()));
    require(
        new MaterialFactory(api, mod -> true)
            .load(id("test:enabled"), new StringReader("{\"has_fluid\":false}"))
            .content()
            .items()
            .containsKey(registry.resolve("addon:gated").getOrThrow()));
    var isolated = new MaterialExtensions(false);
    require(
        MMDMaterialCodec.forExtensions(isolated).parse(JsonOps.INSTANCE, json).error().isPresent());
    require(MaterialParts.CODEC.encodeStart(JsonOps.INSTANCE, restricted).error().isPresent());
    require(
        MaterialParts.forRegistry(registry)
            .parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"parts\":[\"addon:needle\",\"addon:dart\"]}"))
            .error()
            .isPresent());
    expectFailure(() -> api.registerItemModule(id("addon:dart"), gated));
    expectFailure(() -> api.registerBlockModule(id("addon:needle"), panel));
    require(api.snapshot().parts().entries().size() == 4);
    System.out.println(
        "Loaded part identities, aliases, independent catalogs, projectile companions, behaviors,"
            + " and scoped codecs passed");
  }

  private static ResourceLocation id(String value) {
    return ResourceLocation.parse(value);
  }

  private static void require(boolean condition) {
    if (!condition) throw new AssertionError("Loaded parts assertion failed");
  }

  private static void expectFailure(Runnable action) {
    try {
      action.run();
    } catch (IllegalArgumentException ex) {
      return;
    }
    throw new AssertionError("Invalid part registration accepted");
  }
}
