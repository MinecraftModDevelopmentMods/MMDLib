package zone.moddev.mc.lib.common.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.common.types.CreativeTabDefinition;

class ModCreativeTabsTest {
    @Test
    void explicitDefaultWinsAndStaysWithinOwnerNamespace() {
        var definitions = Map.of(id("example:tools"), definition(),
            id("other:main"), definition());
        var tabs = List.of(id("example:machines"), id("example:tools"),
            id("other:main"));
        assertEquals(id("example:tools"),
            ModCreativeTabs.choose("example", definitions, tabs).orElseThrow());
        assertTrue(
            ModCreativeTabs.choose("absent", definitions, tabs).isEmpty());
        assertTrue(ModCreativeTabs
            .choose("example", definitions, List.of(id("example:machines")))
            .isEmpty());
    }

    @Test
    void javaTabsTakePrecedenceOverGeneratedFallbacks() {
        var tabs = List.of(id("example:mmdlib_content"),
            id("example:materials"), id("example:tools"),
            id("example:machines"), id("other:main"));
        assertEquals(id("example:machines"),
            ModCreativeTabs.choose("example", Map.of(), tabs).orElseThrow());
        assertEquals(id("example:materials"), ModCreativeTabs
            .choose("example", Map.of(), tabs.subList(0, 2)).orElseThrow());
        assertEquals(id("example:mmdlib_content"), ModCreativeTabs
            .choose("example", Map.of(), tabs.subList(0, 1)).orElseThrow());
    }

    @Test
    void rejectsAmbiguousDefaults() {
        var definitions = Map.of(id("example:tools"), definition(),
            id("example:machines"), definition());
        assertThrows(IllegalArgumentException.class, () -> ModCreativeTabs
            .choose("example", definitions, definitions.keySet()));
    }

    private static CreativeTabDefinition definition() {
        return CreativeTabDefinition.builder().icon(id("minecraft:stone"))
            .defaultForMod().build();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
