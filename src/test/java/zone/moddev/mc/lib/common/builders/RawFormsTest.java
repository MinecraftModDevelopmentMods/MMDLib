package zone.moddev.mc.lib.common.builders;

import java.util.*;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import zone.moddev.mc.lib.common.runtime.MaterialItemTags;
import zone.moddev.mc.lib.common.types.*;

/**
 * Raw ores: the {@code raw} and {@code raw_block} forms, named like vanilla's
 * and selected only by name.
 */
public final class RawFormsTest {
    private static MaterialPart part(String name) {
        return ModuleCatalog.bundled().parts().resolve(name).getOrThrow();
    }

    @Test
    void allLeavesOutRawFormsUnlessNamed() {
        var raw = part("raw");
        var block = part("raw_block");
        require(!raw.inAll() && !block.inAll() && part("ingot").inAll());
        require(!PartSelection.parse("all", PartSelection.ITEMS).contains(raw));
        require(
            !PartSelection.parse("all", PartSelection.BLOCKS).contains(block));
        require(PartSelection.parse("all raw", PartSelection.ITEMS)
            .containsAll(Set.of(raw, part("ingot"))));
        require(PartSelection.parse("raw !all", PartSelection.ITEMS).isEmpty());
        require(PartSelection.parse("all raw !raw", PartSelection.ITEMS)
            .equals(PartSelection.parse("all", PartSelection.ITEMS)));
        require(!MaterialSelection.DEFAULT.items().contains(raw));
        var typed = TypeSelection.parse("all raw", PartSelection.ITEMS,
            Set.of());
        require(typed.standard().contains(raw));
        require(TypeSelection.parse("raw !all", PartSelection.ITEMS, Set.of())
            .standard().isEmpty());
    }

    @Test
    void omittedSectionsAndCodecsKeepRawFormsOptIn() {
        var codec = PartSelection.codec(PartSelection.ITEMS);
        var withRaw = PartSelection.parse("all raw", PartSelection.ITEMS);
        require(codec.encodeStart(JsonOps.INSTANCE, withRaw).getOrThrow()
            .getAsString().equals("all raw"));
        require(codec
            .encodeStart(JsonOps.INSTANCE,
                PartSelection.parse("all", PartSelection.ITEMS))
            .getOrThrow().getAsString().equals("all"));
        require(
            codec.parse(JsonOps.INSTANCE, JsonParser.parseString("\"all raw\""))
                .getOrThrow().equals(withRaw));
        var material = MaterialBuilder.create().setName("test:tin").build();
        require(!material.parts().parts().contains(part("raw"))
            && !material.parts().parts().contains(part("raw_block")));
    }

    @Test
    void rawFormsAreNamedLikeVanilla() {
        var material = MaterialBuilder.create().setName("test:tin")
            .setItems("ingot raw").setBlocks("block raw_block ore").build();
        var plan = new MaterialFactory(mod -> false).create(material).content();
        require(plan.items().get(part("raw")).name()
            .equals(ResourceLocation.parse("test:raw_tin")));
        require(plan.blocks().get(part("raw_block")).name()
            .equals(ResourceLocation.parse("test:raw_tin_block")));
        require(plan.items().get(part("raw_block")).name()
            .equals(ResourceLocation.parse("test:raw_tin_block")));
        require(plan.items().get(part("ingot")).name()
            .equals(ResourceLocation.parse("test:tin_ingot")));
    }

    @Test
    void namePatternsAreValidated() {
        for (var bad : List.of("\"raw\"", "\"{material}_{material}\"",
            "\"Raw_{material}\""))
            require(
                PartMetadata.CODEC
                    .parse(JsonOps.INSTANCE,
                        JsonParser
                            .parseString("{\"name_pattern\": " + bad + "}"))
                    .isError());
        require(
            PartMetadata.CODEC
                .parse(JsonOps.INSTANCE,
                    JsonParser
                        .parseString("{\"name_pattern\": \"raw_{material}\","
                            + " \"entity_factory\": \"test:dart\"}"))
                .isError());
        var parsed = PartMetadata.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"name_pattern\": \"crude_{material}\","
                + " \"in_all\": false}"))
            .getOrThrow();
        require(!parsed.inAll()
            && parsed.name("tin").orElseThrow().equals("crude_tin"));
        require(PartMetadata.EMPTY.inAll()
            && PartMetadata.EMPTY.name("tin").isEmpty());
    }

    @Test
    void rawItemsAreTaggedAsRawMaterials() {
        var tags = new HashMap<String, String>();
        MaterialItemTags.forPart("raw", "tin", "test:raw_tin", tags::put);
        require("test:raw_tin".equals(tags.get("c:raw_materials/tin")));
        require("#c:raw_materials/tin".equals(tags.get("c:raw_materials")));
    }

    private static void require(boolean condition) {
        if (!condition)
            throw new AssertionError("Raw form assertion failed");
    }
}
