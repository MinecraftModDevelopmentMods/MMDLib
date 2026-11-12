package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.elements.RecipeSelectorElement;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;

class RecipeSelectorTest {
    private RecipeSelectorElement read(String attributes) throws Exception {
        String xml = "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\">"
            + "<recipe-selector " + attributes + "/></screen>";
        return (RecipeSelectorElement) ScreenReader.read(ResourceLocation.parse("test:selector"),
            "selector.xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).body()
            .getFirst();
    }

    @Test
    void selectorOwnsStateAndActionsAndValidatesItsProcessorBinding() throws Exception {
        var element = read("processor-key=\"stew\"");
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{18, 18},
            element.measure(text -> 0));
        element.validateBindings(Map.of("processors", Set.of("stew")));
        assertThrows(IllegalArgumentException.class,
            () -> element.validateBindings(Map.of("processors", Set.of("other"))));
        assertEquals(java.util.List.of(RecipeSelectorElement.EDIT), element.actions());
        assertEquals(java.util.List.of(RecipeSelectorElement.VIEW), element.synchronizedValues());
        for (String attributes : java.util.List.of("", "processor-key=\"\"",
            "processor-key=\"stew\" value=\"#getValue\"",
            "processor-key=\"stew\" on-click=\"test:click\""))
            assertThrows(IllegalArgumentException.class, () -> read(attributes));
    }

    @Test
    void oldSaveAndExplicitlyClearedSelectionHaveDifferentPersistentStates() {
        var legacy = RecipeProcessor.State.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertTrue(legacy.selection().isEmpty());
        var cleared = RecipeProcessor.State.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"selection\":{}}")).getOrThrow();
        assertTrue(cleared.selection().isPresent());
        assertTrue(cleared.selection().orElseThrow().recipe().isEmpty());
        var selected = RecipeProcessor.State.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"selection\":{\"recipe\":\"test:stone\"}}")).getOrThrow();
        assertEquals(ResourceLocation.parse("test:stone"),
            selected.selection().orElseThrow().recipe().orElseThrow());
        assertFalse(selected.equals(cleared));
        for (var state : java.util.List.of(legacy, cleared, selected)) {
            var encoded = RecipeProcessor.State.CODEC.encodeStart(JsonOps.INSTANCE, state)
                .getOrThrow();
            assertEquals(state,
                RecipeProcessor.State.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
    }
}
