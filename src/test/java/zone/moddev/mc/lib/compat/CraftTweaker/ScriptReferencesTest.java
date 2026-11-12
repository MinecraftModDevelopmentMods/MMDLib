package zone.moddev.mc.lib.compat.CraftTweaker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ScriptReferencesTest {
    @Test
    void referencesAcceptUnregisteredIdsWithoutLookingUpRegistries() {
        String id = "uninstalled_mod:future_content";
        assertEquals(id, new ScriptItemReference(id).getId());
        assertEquals(id, new ScriptBlockReference(id).getId());
        assertEquals(id, new ScriptBlockTagReference(id).getId());
        assertEquals(id, new ScriptItemModuleReference(id).getId());
        assertEquals(id, new ScriptBlockModuleReference(id).getId());
    }

    @Test
    void malformedIdsFailAtReferenceConstruction() {
        assertThrows(net.minecraft.ResourceLocationException.class,
            () -> new ScriptItemReference("Invalid Name"));
        assertThrows(net.minecraft.ResourceLocationException.class,
            () -> new ScriptBlockReference("Invalid Name"));
        assertThrows(net.minecraft.ResourceLocationException.class,
            () -> new ScriptBlockTagReference("#minecraft:stone"));
        assertThrows(net.minecraft.ResourceLocationException.class,
            () -> new ScriptItemModuleReference("Invalid Name"));
        assertThrows(net.minecraft.ResourceLocationException.class,
            () -> new ScriptBlockModuleReference("Invalid Name"));
    }

}
