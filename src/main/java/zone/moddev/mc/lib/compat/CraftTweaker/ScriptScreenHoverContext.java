package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.gui.runtime.ScreenHover;

/**
 * The mouse entering or leaving a screen element, as a {@code
 * ScreenHoverHandler} sees it. Hovering happens on the client: use it for
 * display, not to change the world.
 *
 * @docParam this hover
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/screens/ScreenHoverContext")
@ZenCodeType.Name("mods.mmdlib.ScreenHoverContext")
public final class ScriptScreenHoverContext {
    private final ScreenHover hover;

    ScriptScreenHoverContext(ScreenHover hover) {
        this.hover = hover;
    }

    /**
     * The block entity whose screen it is, as the client knows it.
     *
     * @return the block entity
     */
    @ZenCodeType.Getter("blockEntity")
    public BlockEntity getBlockEntity() {
        return hover.menu().blockEntity;
    }

    /**
     * The block entity's level, on the client.
     *
     * @return the level
     */
    @ZenCodeType.Getter("level")
    public Level getLevel() {
        return hover.menu().blockEntity.getLevel();
    }

    /**
     * The block entity's position.
     *
     * @return the position
     */
    @ZenCodeType.Getter("pos")
    public BlockPos getPos() {
        return hover.menu().blockEntity.getBlockPos();
    }

    /**
     * The screen's ID.
     *
     * @return the screen ID
     */
    @ZenCodeType.Getter("screen")
    public String getScreen() {
        return hover.menu().bound.screen().id().toString();
    }

    /**
     * The element's {@code id}; empty if it has none.
     *
     * @return the element ID, or an empty string
     */
    @ZenCodeType.Getter("element")
    public String getElement() {
        return hover.element().common().id().orElse("");
    }

    /**
     * True when the mouse entered the element, false when it left.
     *
     * @return true on entering, false on leaving
     */
    @ZenCodeType.Getter("entered")
    public boolean getEntered() {
        return hover.entered();
    }
}
