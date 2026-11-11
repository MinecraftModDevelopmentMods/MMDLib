package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.gui.runtime.ScreenClick;

/** A click on a screen element, as a {@code ScreenClickHandler} sees it. */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ScreenClickContext")
public final class ScriptScreenClickContext {
    private final ScreenClick click;

    ScriptScreenClickContext(ScreenClick click) {
        this.click = click;
    }

    /** The player who clicked. */
    @ZenCodeType.Getter("player")
    public Player getPlayer() {
        return click.player();
    }

    /** The block entity whose screen it is, such as an XML machine. */
    @ZenCodeType.Getter("blockEntity")
    public BlockEntity getBlockEntity() {
        return click.menu().blockEntity;
    }

    /** The block entity's level. */
    @ZenCodeType.Getter("level")
    public Level getLevel() {
        return click.menu().blockEntity.getLevel();
    }

    /** The block entity's position. */
    @ZenCodeType.Getter("pos")
    public BlockPos getPos() {
        return click.menu().blockEntity.getBlockPos();
    }

    /** The screen's ID. */
    @ZenCodeType.Getter("screen")
    public String getScreen() {
        return click.menu().bound.screen().id().toString();
    }

    /** The clicked element's {@code id}; empty if it has none. */
    @ZenCodeType.Getter("element")
    public String getElement() {
        return click.element().common().id().orElse("");
    }

    /** The mouse button: 0 left, 1 right. */
    @ZenCodeType.Getter("button")
    public int getButton() {
        return click.button();
    }

    /** Whether Shift was held. */
    @ZenCodeType.Getter("shift")
    public boolean getShift() {
        return click.shift();
    }

    /** Whether Control was held. */
    @ZenCodeType.Getter("control")
    public boolean getControl() {
        return click.control();
    }

    /** Whether Alt was held. */
    @ZenCodeType.Getter("alt")
    public boolean getAlt() {
        return click.alt();
    }

    /**
     * The option chosen, for elements with several, such as a radio group's
     * index; 0 otherwise.
     */
    @ZenCodeType.Getter("option")
    public int getOption() {
        return click.option();
    }
}
