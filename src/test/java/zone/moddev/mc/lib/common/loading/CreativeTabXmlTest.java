package zone.moddev.mc.lib.common.loading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreativeTabXmlTest {
    @TempDir
    Path directory;

    @Test
    void includesOwningModWithoutIndividualItemEntries() throws Exception {
        var path = directory.resolve("main.xml");
        Files.writeString(path, xml(
            "version=\"1\" icon=\"example:item\" include-mod-items=\"true\"",
            "<exclude id=\"example:internal\"/><exclude tag=\"example:internal_items\"/>"));
        var definition = CreativeTabXml.read(path);
        assertTrue(definition.includeModItems());
        assertTrue(definition.contents().isEmpty());
        assertEquals(List.of(ResourceLocation.parse("example:internal")),
            definition.exclusions());
        assertEquals(List.of(ResourceLocation.parse("example:internal_items")),
            definition.excludedTags());
    }

    @Test
    void rejectsInvalidDefinitionsWithFileDiagnostic() throws Exception {
        var path = directory.resolve("main.xml");
        for (String invalid : List.of(
            "<creative-tab version=\"1\" icon=\"example:item\"/>",
            xml("version=\"2\" icon=\"example:item\"", ""),
            xml("version=\"1\" search-bar=\"yes\" icon=\"example:item\"", ""),
            xml("version=\"1\" include-mod-items=\"yes\" icon=\"example:item\"",
                ""),
            xml("version=\"1\" icon=\"example:item\"", "<exclude/>"),
            xml("version=\"1\" icon=\"example:item\"",
                "<exclude id=\"example:item\" tag=\"example:internal\"/>"),
            xml("version=\"1\" icon=\"example:item\"", "<exclude tag=\"\"/>"),
            xml("version=\"1\" icon=\"example:item\"",
                "<exclude tag=\"Not a tag\"/>"),
            xml("version=\"1\" default-for-mode=\"true\" icon=\"example:item\"",
                ""),
            xml("version=\"1\" icon=\"example:item\"",
                "<unknown id=\"example:item\"/>"),
            xml("version=\"1\"", "<item/>"),
            xml("version=\"1\"", "<tag id=\"c:gears\"/>"),
            xml("version=\"1\"",
                "<item id=\"example:item\"><item id=\"example:other\"/></item>"),
            "<!DOCTYPE creative-tab [<!ENTITY title 'expanded'>]>" + xml(
                "version=\"1\" icon=\"example:item\" title=\"&title;\"", ""))) {
            Files.writeString(path, invalid);
            var error = assertThrows(IllegalArgumentException.class,
                () -> CreativeTabXml.read(path), invalid);
            assertTrue(error.getMessage().contains(path.toString()));
        }
    }

    private static String xml(String attributes, String children) {
        return "<creative-tab xmlns=\"" + CreativeTabXml.NAMESPACE + "\" "
            + attributes + ">" + children + "</creative-tab>";
    }
}
