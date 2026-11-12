package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function giving a screen element a line of text from the screen's
 * block entity; runs on the server every tick the screen is open.
 *
 * @docParam this (machine) => "Ready"
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/screens/ScreenTextValue")
@ZenCodeType.Name("mods.mmdlib.ScreenTextValue")
@FunctionalInterface
public interface ScriptScreenTextValue {
    /**
     * Reads the value from the screen's block entity.
     *
     * @param blockEntity the block entity whose screen is open
     * @return the text to show
     * @docParam blockEntity machine
     */
    @ZenCodeType.Method
    String read(BlockEntity blockEntity);
}
