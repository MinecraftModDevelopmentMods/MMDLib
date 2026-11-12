package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A block ID, for builder methods that name a block. It is not looked up until
 * the block is needed, so it can name a block built later in the same script.
 *
 * @docParam this new BlockReference("mmdlib:marble")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/references/BlockReference")
@ZenCodeType.Name("mods.mmdlib.BlockReference")
public final class ScriptBlockReference {
    private final ResourceLocation id;

    /**
     * Refers to the block with this ID.
     *
     * @param id the block ID
     * @docParam id "mmdlib:marble"
     */
    @ZenCodeType.Constructor
    public ScriptBlockReference(String id) {
        this.id = ResourceLocation.parse(id);
    }

    /**
     * The block ID.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("id")
    public String getId() {
        return id.toString();
    }
}
