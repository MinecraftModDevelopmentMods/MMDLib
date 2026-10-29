package zone.moddev.mc.lib.common.builders;

import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.common.codecs.MMDMaterialCodec;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.types.*;

public final class MaterialExtensionsTest {
  public static final class Probe extends Event {}

  private static final class OtherItem extends Item {
    OtherItem() {
      super(new Item.Properties());
    }
  }

  public static void main(String[] args) throws Exception {
    var api = new MaterialExtensions();
    var codec = MMDMaterialCodec.forExtensions(api); // created before the types are registered
    AtomicInteger templateCalls = new AtomicInteger();
    AtomicInteger creations = new AtomicInteger();
    ItemTypeFactory<Item> itemFactory =
        new ItemTypeFactory<>() {
          public Class<Item> itemClass() {
            return Item.class;
          }

          public ItemBuilder template(MMDMaterial material) {
            templateCalls.incrementAndGet();
            return ItemBuilder.create().setMaxStackSize(16);
          }

          public Item create(MMDMaterial material, MMDItem definition) {
            creations.incrementAndGet();
            throw new AssertionError("Creation must be deferred");
          }
        };
    BlockTypeFactory<Block> blockFactory =
        new BlockTypeFactory<>() {
          public Class<Block> blockClass() {
            return Block.class;
          }

          public BlockBuilder template(MMDMaterial material) {
            return BlockBuilder.create().setStrength((float) material.properties().hardness(), 10);
          }

          public Block create(MMDMaterial material, MMDBlock definition) {
            creations.incrementAndGet();
            throw new AssertionError("Creation must be deferred");
          }
        };
    var gadget = ResourceLocation.parse("addon:gadget");
    var crusher = ResourceLocation.parse("addon:crusher");
    var compat = ResourceLocation.parse("addon:compat");
    api.registerItemType(gadget, itemFactory);
    api.registerBlockType(crusher, blockFactory);
    api.registerItemType(compat, itemFactory, Optional.of("mekanism"));
    var tick = api.events().registerEvent("tick", Integer.class);
    var text = api.events().registerEvent("text", String.class);
    AtomicInteger calls = new AtomicInteger();
    var boost = ResourceLocation.parse("addon:boost");
    api.events().registerCallback(boost, tick, value -> calls.addAndGet(value));
    var factory = new MaterialFactory(api, mod -> false);
    var result =
        factory.load(
            ResourceLocation.parse("test:copper"),
            new StringReader(
                """
                {"hardness":4,"items":"addon:gadget", "blocks":"addon:crusher", "has_fluid":false,
                 "event":{"tick":"addon:boost"}}
                """));
    require(result.content().items().size() == 2 && result.content().blocks().size() == 1);
    require(
        result.content().extraItems().size() == 2 && result.content().extraBlocks().size() == 1);
    require(
        result
            .content()
            .extraItems()
            .get(gadget)
            .name()
            .equals(ResourceLocation.parse("test:copper_gadget")));
    require(result.content().extraItems().get(gadget).maxStackSize() == 16);
    require(result.content().extraBlocks().get(crusher).properties().hardness() == 4);
    require(
        result
            .content()
            .extraItems()
            .get(crusher)
            .name()
            .equals(result.content().extraBlocks().get(crusher).name()));
    require(creations.get() == 0);
    api.events().dispatch(result.material(), tick, 3);
    require(calls.get() == 3);
    var encoded = codec.encodeStart(JsonOps.INSTANCE, result.material()).getOrThrow();
    require(encoded.getAsJsonObject().has("event") && !encoded.getAsJsonObject().has("events"));
    require(codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(result.material()));
    var nbt = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, result.material()).getOrThrow();
    require(
        codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt).getOrThrow().equals(result.material()));
    var alias =
        factory.decode(
            ResourceLocation.parse("test:alias"),
            new StringReader("{\"events\":{\"tick\":\"addon:boost\"}}"));
    api.events().dispatch(alias, tick, 2);
    require(calls.get() == 5);
    var all = factory.load(ResourceLocation.parse("test:all"), new StringReader("{}"));
    require(
        all.content().extraItems().containsKey(gadget)
            && all.content().extraBlocks().containsKey(crusher));
    require(!all.content().extraItems().containsKey(compat));
    int beforeExcluded = templateCalls.get();
    var noCustom =
        factory.load(
            ResourceLocation.parse("test:exclude"),
            new StringReader(
                "{\"items\":\"all !addon:gadget !addon:compat\",\"blocks\":\"all"
                    + " !addon:crusher\"}"));
    require(
        noCustom.content().extraItems().isEmpty() && noCustom.content().extraBlocks().isEmpty());
    require(templateCalls.get() == beforeExcluded);
    var enabled =
        new MaterialFactory(api, mod -> mod.equals("mekanism"))
            .load(ResourceLocation.parse("test:enabled"), new StringReader("{}"));
    require(enabled.content().extraItems().containsKey(compat));
    var probe = api.events().registerEvent("probe", Probe.class);
    api.events()
        .registerCallback(
            ResourceLocation.parse("addon:probe"), probe, event -> calls.addAndGet(10));
    var probeMaterial =
        factory.decode(
            ResourceLocation.parse("test:probe"),
            new StringReader("{\"event\":{\"probe\":\"addon:probe\"}}"));
    var bus = BusBuilder.builder().build();
    var subscription =
        api.events().listen(bus, probe, event -> List.of(probeMaterial, probeMaterial));
    bus.post(new Probe());
    require(calls.get() == 15);
    subscription.close();
    bus.post(new Probe());
    require(calls.get() == 15);
    for (String invalid :
        new String[] {
          "{\"items\":\"missing:type\"}",
          "{\"items\":\"addon:crusher\"}",
          "{\"event\":{\"unknown\":\"addon:boost\"}}",
          "{\"event\":{\"tick\":\"addon:missing\"}}",
          "{\"event\":{\"text\":\"addon:boost\"}}",
          "{\"event\":{},\"events\":{}}"
        })
      require(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent());
    expectFailure(() -> api.registerItemType(gadget, itemFactory));
    expectFailure(() -> api.events().registerCallback(boost, tick, value -> {}));
    expectFailure(() -> api.events().registerEvent("tick", Integer.class));
    var wrongRegistry =
        DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, "wrong");
    expectFailure(
        () ->
            api.queueItem(
                wrongRegistry,
                gadget,
                Item.class,
                result.material(),
                result.content().extraItems().get(gadget)));
    expectFailure(
        () ->
            api.queueItem(
                wrongRegistry,
                gadget,
                OtherItem.class,
                result.material(),
                result.content().extraItems().get(gadget)));
    api.registerItemType(ResourceLocation.parse("other:gadget"), itemFactory);
    expectFailure(
        () ->
            factory.load(
                ResourceLocation.parse("test:collision"),
                new StringReader(
                    "{\"items\":\"addon:gadget"
                        + " other:gadget\",\"blocks\":\"\",\"has_fluid\":false}")));
    require(creations.get() == 0);
    var globalId = ResourceLocation.parse("unique:component");
    MaterialExtensions.GLOBAL.registerItemType(globalId, itemFactory);
    var globalMaterial =
        MaterialBuilder.create()
            .setName("test:global")
            .setItems("unique:component")
            .setBlocks("")
            .setEntities("")
            .setHasFluid(false)
            .build();
    require(
        new MaterialFactory(mod -> false)
            .create(globalMaterial)
            .content()
            .extraItems()
            .containsKey(globalId));
    require(
        MaterialBuilder.from(globalMaterial)
            .setItems("all !unique:component")
            .build()
            .selection()
            .customItems()
            .isEmpty());
    System.out.println(
        "Custom type selection/planning, typed callbacks, NeoForge event bridge, optional mods, and"
            + " error checks passed");
  }

  private static void require(boolean condition) {
    if (!condition) throw new AssertionError("Material extension assertion failed");
  }

  private static void expectFailure(Runnable action) {
    try {
      action.run();
    } catch (IllegalArgumentException | IllegalStateException ex) {
      return;
    }
    throw new AssertionError("Expected extension failure");
  }
}
