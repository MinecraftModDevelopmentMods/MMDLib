package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

/**
 * An item module ID, for builders that take a module. It is not looked up until
 * it is needed.
 *
 * @docParam this new ItemModuleReference("mmdlib:ingot")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/references/ItemModuleReference")
@ZenCodeType.Name("mods.mmdlib.ItemModuleReference")
public final class ScriptItemModuleReference {
    private final ResourceLocation id;

    /**
     * Refers to the item module with this ID.
     *
     * @param id the item module ID
     * @docParam id "mmdlib:ingot"
     */
    @ZenCodeType.Constructor
    public ScriptItemModuleReference(String id) {
        this.id = ResourceLocation.parse(id);
    }

    /**
     * The item module ID.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("id")
    public String getId() {
        return id.toString();
    }
}
