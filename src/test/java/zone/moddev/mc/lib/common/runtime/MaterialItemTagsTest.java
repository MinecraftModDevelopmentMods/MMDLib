package zone.moddev.mc.lib.common.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.builders.MaterialBuilder;
import zone.moddev.mc.lib.common.factories.MaterialFactory;

class MaterialItemTagsTest {
    private static MaterialFactory.Result material(String id) {
        return new MaterialFactory(MaterialExtensions.GLOBAL, mod -> true)
            .create(
                MaterialBuilder.create().setName(id).setHardness(8).build());
    }

    private static JsonObject tag(Map<String, byte[]> files, String path) {
        var bytes = files.get(path);
        assertTrue(bytes != null, "missing " + path);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    private static List<String> ids(JsonObject tag) {
        var ids = new ArrayList<String>();
        tag.getAsJsonArray("values").forEach(value -> {
            var entry = value.getAsJsonObject();
            assertFalse(entry.get("required").getAsBoolean(),
                "entries are optional");
            ids.add(entry.get("id").getAsString());
        });
        return ids;
    }

    @Test
    void tagsEveryMaterialsItemsByPart() {
        var files = MaterialItemTags.data(
            List.of(material("alpha:orichalcum"), material("beta:mithril")));
        var swords = tag(files, "data/minecraft/tags/item/swords.json");
        assertFalse(swords.get("replace").getAsBoolean(), "adds to the tag");
        assertEquals(List.of("alpha:orichalcum_sword", "beta:mithril_sword"),
            ids(swords));
        assertTrue(
            ids(tag(files, "data/minecraft/tags/item/enchantable/sword.json"))
                .contains("beta:mithril_sword"));
        assertTrue(ids(
            tag(files, "data/minecraft/tags/item/enchantable/foot_armor.json"))
            .contains("alpha:orichalcum_boots"));
        assertTrue(
            ids(tag(files, "data/minecraft/tags/item/enchantable/mining.json"))
                .containsAll(List.of("alpha:orichalcum_pickaxe",
                    "alpha:orichalcum_crackhammer",
                    "alpha:orichalcum_scythe")));
        assertTrue(ids(tag(files, "data/c/tags/item/arrows.json")).containsAll(
            List.of("alpha:orichalcum_arrow", "alpha:orichalcum_bolt")));
        assertEquals(List.of("alpha:orichalcum_ingot"),
            ids(tag(files, "data/c/tags/item/ingots/orichalcum.json")));
        assertTrue(ids(tag(files, "data/c/tags/item/ingots.json"))
            .contains("#c:ingots/orichalcum"));
    }

    @Test
    void listsEachEntryOnce() {
        var files = MaterialItemTags.data(List.of(material("alpha:orichalcum"),
            material("alpha:orichalcum")));
        assertEquals(List.of("alpha:orichalcum_sword"),
            ids(tag(files, "data/minecraft/tags/item/swords.json")));
    }

    @Test
    void blockItemsGetNoPartTags() {
        var files = MaterialItemTags
            .data(List.of(material("alpha:orichalcum")));
        files.values()
            .forEach(bytes -> assertFalse(
                new String(bytes, StandardCharsets.UTF_8)
                    .contains("alpha:orichalcum_block\""),
                "blocks are tagged by their own data"));
    }
}
