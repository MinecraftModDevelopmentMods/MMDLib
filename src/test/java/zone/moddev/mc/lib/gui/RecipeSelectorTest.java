package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.elements.RecipeSelectorElement;

class RecipeSelectorTest {
    private RecipeSelectorElement read(String attributes) throws Exception {
        String xml = "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\""
            + " block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\">"
            + "<recipe-selector " + attributes + "/></screen>";
        return (RecipeSelectorElement) ScreenReader
            .read(ResourceLocation.parse("test:selector"), "selector.xml",
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
            .body().getFirst();
    }

    @Test
    void selectorOwnsStateAndActionsAndValidatesItsProcessorBinding()
        throws Exception {
        var element = read("processor-key=\"stew\"");
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{18, 18},
            element.measure(text -> 0));
        element.validateBindings(Map.of("processors", Set.of("stew")));
        assertThrows(IllegalArgumentException.class, () -> element
            .validateBindings(Map.of("processors", Set.of("other"))));
        assertEquals(java.util.List.of(RecipeSelectorElement.EDIT),
            element.actions());
        assertEquals(java.util.List.of(RecipeSelectorElement.VIEW),
            element.synchronizedValues());
        for (String attributes : java.util.List.of("", "processor-key=\"\"",
            "processor-key=\"stew\" value=\"#getValue\"",
            "processor-key=\"stew\" on-click=\"test:click\""))
            assertThrows(IllegalArgumentException.class,
                () -> read(attributes));
    }

}
