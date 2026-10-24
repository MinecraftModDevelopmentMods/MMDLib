package zone.moddev.mc.lib.common.builders;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.codecs.MMDMaterialCodec;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.types.*;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Set;

public final class MaterialFactoryTest {
    public static void main(String[] args) throws Exception {
        var root = MaterialBuilder.create();
        require(root == MaterialBuilder.INSTANCE);
        var template = root.setType("metal").setColor(0xFFAA6600).setHardness(3.5).setStrength(8).setMagic(4.5)
                .setDamage(2).setItems("all !hoe").setBlocks("all !door");
        var copper = template.setName("test:copper").build();
        var tin = template.setName("test:tin").build();
        require(root.getRegistryName() == null && template.getRegistryName() == null);
        require(copper.properties().magic() == 4.5 && tin.properties().strength() == 8);
        require(!copper.parts().parts().contains(MaterialPart.HOE));
        require(PartSelection.parse("!hoe all", PartSelection.ITEMS).equals(PartSelection.parse("all !hoe", PartSelection.ITEMS)));
        require(PartSelection.parse("all !all hoe", PartSelection.ITEMS).isEmpty());
        require(PartSelection.parse("!hoe", PartSelection.ITEMS).isEmpty());
        require(PartSelection.parse("all !powder", PartSelection.ITEMS).equals(PartSelection.parse("all !dust", PartSelection.ITEMS)));
        var special = new HashMap<String, Double>(); special.put("density", 7000D); special.put("temperature", 1100D);
        var tagged = MaterialBuilder.from(copper).setHarvestTags(Set.of(ResourceLocation.parse("minecraft:mineable/pickaxe")))
                .setSpecial(special).build();
        special.clear();
        var factory = new MaterialFactory(mod -> false);
        var result = factory.create(tagged);
        require(!result.content().items().containsKey(MaterialPart.HOE));
        require(!result.content().blocks().containsKey(MaterialPart.DOOR));
        require(!result.content().items().containsKey(MaterialPart.CRYSTAL));
        require(!result.content().items().containsKey(MaterialPart.MEK_CRYSTAL));
        require(result.content().blocks().get(MaterialPart.BLOCK).properties().hardness() == 3.5F);
        require(result.content().blocks().get(MaterialPart.BLOCK).tags().contains(ResourceLocation.parse("minecraft:mineable/pickaxe")));
        require(result.content().fluid().appearance().tint() == 0xFFAA6600);
        require(result.content().fluid().typeProperties().density() == 7000 && result.content().fluid().typeProperties().temperature() == 1100);
        require(new MaterialFactory(mod -> mod.equals("mekanism")).create(copper).content().items().containsKey(MaterialPart.CRYSTAL));
        var mekFactory = new MaterialFactory(mod -> mod.equals("mekanism"));
        var aliasCrystal = mekFactory.load(ResourceLocation.parse("test:crystal"), new StringReader(
                "{\"items\":\"mek_crystal\",\"blocks\":\"\",\"has_fluid\":false}"));
        require(aliasCrystal.content().items().size() == 1 && aliasCrystal.content().items().containsKey(MaterialPart.CRYSTAL));
        require(aliasCrystal.content().blocks().isEmpty());
        var excludedCrystal = mekFactory.load(ResourceLocation.parse("test:excluded"), new StringReader(
                "{\"items\":\"all !mek_crystal\",\"blocks\":\"\",\"has_fluid\":false}"));
        require(!excludedCrystal.content().items().containsKey(MaterialPart.CRYSTAL));
        require(MMDMaterialCodec.MMD_MATERIAL_CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"blocks\":\"mek_crystal\"}")).error().isPresent());
        var noEntities = MaterialBuilder.from(copper).setEntities("all !bolt").build();
        require(!factory.create(noEntities).content().entities().containsKey(MaterialPart.BOLT));
        require(!factory.create(noEntities).content().items().containsKey(MaterialPart.BOLT));
        require(factory.create(MaterialBuilder.from(copper).setHasFluid(false).build()).content().fluid() == null);
        var json = JsonParser.parseString("""
            {"type":"metal","color":-1,"hardness":3.5,"strength":8,"magic":4.5,
             "items":["all","!hoe","!powder"],"blocks":"all !door", "entities":"all !bolt",
             "harvest_tags":["minecraft:mineable/pickaxe"],"special":{"temperature":1200}}
            """);
        var material = MMDMaterialCodec.MMD_MATERIAL_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        require(material.name().isEmpty());
        require(!material.parts().parts().contains(MaterialPart.HOE) && !material.parts().parts().contains(MaterialPart.POWDER));
        var encoded = MMDMaterialCodec.MMD_MATERIAL_CODEC.encodeStart(JsonOps.INSTANCE, material).getOrThrow();
        require(MMDMaterialCodec.MMD_MATERIAL_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(material));
        var nbt = MMDMaterialCodec.MMD_MATERIAL_CODEC.encodeStart(NbtOps.INSTANCE, material).getOrThrow();
        require(MMDMaterialCodec.MMD_MATERIAL_CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(material));
        var loaded = factory.load(ResourceLocation.parse("test:copper"), new StringReader(json.toString()));
        require(loaded.material().name().orElseThrow().equals(ResourceLocation.parse("test:copper")));
        require(!loaded.content().items().containsKey(MaterialPart.BOLT));
        var overridden = factory.withTemplates(planner -> planner.withBlockTemplate(MaterialPart.BLOCK,
                BlockBuilder.create().setStrength(9, 20)));
        require(overridden.create(copper).content().blocks().get(MaterialPart.BLOCK).properties().hardness() == 9);
        require(factory.create(copper).content().blocks().get(MaterialPart.BLOCK).properties().hardness() == 3.5F);
        var empty = factory.load(ResourceLocation.parse("test:empty"), new StringReader("{\"items\":\"\",\"blocks\":[],\"has_fluid\":false}"));
        require(empty.content().items().isEmpty() && empty.content().blocks().isEmpty() && empty.content().fluid() == null);
        for (String invalid : new String[] {"{\"items\":\"all !ho\"}", "{\"items\":\"door\"}",
                "{\"blocks\":\"hoe\"}", "{\"entities\":\"pickaxe\"}", "{\"hardness\":-1}",
                "{\"strength\":\"bad\"}", "{\"special\":{\"\":1}}", "{\"events\":{\"\":\"test:a\"}}"})
            require(MMDMaterialCodec.MMD_MATERIAL_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent());
        expectFailure(() -> factory.decode(ResourceLocation.parse("test:a"), new StringReader("{\"name\":\"test:b\"}")));
        expectFailure(root::build);
        var file = Files.createTempFile("mmdlib-material", ".json");
        try {
            Files.writeString(file, "{\"items\":\"invalid\"}");
            try { factory.load(ResourceLocation.parse("test:a"), file); throw new AssertionError("Expected file error"); }
            catch (IllegalArgumentException ex) { require(ex.getMessage().contains(file.toString())); }
        } finally { Files.delete(file); }
        System.out.println("Material builder/factory, section selections, stats, optional mods, and JSON/NBT checks passed");
    }
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Material factory assertion failed");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException ex) { return; }
        throw new AssertionError("Expected material error");
    }
}
