package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A block tag ID, without {@code #}, for builder methods that name a tag. It is
 * not looked up until it is needed.
 *
 * @docParam this new BlockTagReference("minecraft:mineable/pickaxe")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/references/BlockTagReference")
@ZenCodeType.Name("mods.mmdlib.BlockTagReference")
public final class ScriptBlockTagReference {
    private final ResourceLocation id;

    /**
     * Refers to the block tag with this ID.
     *
     * @param id the block tag ID
     * @docParam id "minecraft:mineable/pickaxe"
     */
    @ZenCodeType.Constructor
    public ScriptBlockTagReference(String id) {
        this.id = ResourceLocation.parse(id);
    }

    /**
     * The block tag ID.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("id")
    public String getId() {
        return id.toString();
    }
}
