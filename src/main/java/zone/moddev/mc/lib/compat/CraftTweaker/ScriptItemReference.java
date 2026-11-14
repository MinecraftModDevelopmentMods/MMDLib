package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

/**
 * An item ID, for builder methods that name an item. It is not looked up until
 * the item is needed, so it can name an item built later in the same script.
 *
 * @docParam this new ItemReference("mmdlib:wrench")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/references/ItemReference")
@ZenCodeType.Name("mods.mmdlib.ItemReference")
public final class ScriptItemReference {
    private final ResourceLocation id;

    /**
     * Refers to the item with this ID.
     *
     * @param id the item ID
     * @docParam id "mmdlib:wrench"
     */
    @ZenCodeType.Constructor
    public ScriptItemReference(String id) {
        this.id = ResourceLocation.parse(id);
    }

    /**
     * The item ID.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("id")
    public String getId() {
        return id.toString();
    }
}
