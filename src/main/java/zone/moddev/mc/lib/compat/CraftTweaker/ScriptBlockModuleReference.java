package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A block module ID, for builders that take a module. It is not looked up until
 * it is needed.
 *
 * @docParam this new BlockModuleReference("mmdlib:stairs")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/references/BlockModuleReference")
@ZenCodeType.Name("mods.mmdlib.BlockModuleReference")
public final class ScriptBlockModuleReference {
    private final ResourceLocation id;

    /**
     * Refers to the block module with this ID.
     *
     * @param id the block module ID
     * @docParam id "mmdlib:stairs"
     */
    @ZenCodeType.Constructor
    public ScriptBlockModuleReference(String id) {
        this.id = ResourceLocation.parse(id);
    }

    /**
     * The block module ID.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("id")
    public String getId() {
        return id.toString();
    }
}
