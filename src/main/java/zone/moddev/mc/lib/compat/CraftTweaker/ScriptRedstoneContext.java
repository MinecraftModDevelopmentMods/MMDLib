package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.RedstoneChange;

/**
 * What an {@code onRedstoneChanged} handler receives: the block and the power
 * reaching it.
 *
 * @docParam this context
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/RedstoneContext")
@ZenCodeType.Name("mods.mmdlib.RedstoneContext")
public final class ScriptRedstoneContext extends ScriptBlockContext {
    private final RedstoneChange change;

    ScriptRedstoneContext(RedstoneChange change) {
        super(change.level(), change.position());
        this.change = change;
    }

    /**
     * The strongest power reaching the sides the block reads, 0 to 15.
     *
     * @return the power
     */
    @ZenCodeType.Getter("power")
    public int getPower() {
        return change.power();
    }

    /**
     * That power before this change.
     *
     * @return the previous power
     */
    @ZenCodeType.Getter("previous")
    public int getPrevious() {
        return change.previous();
    }

    /**
     * Whether any read side receives power.
     *
     * @return true if powered
     */
    @ZenCodeType.Getter("powered")
    public boolean isPowered() {
        return change.powered();
    }
}
