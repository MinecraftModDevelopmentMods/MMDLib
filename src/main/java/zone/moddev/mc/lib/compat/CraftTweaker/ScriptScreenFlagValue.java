package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function giving a screen element a true/false value from the
 * screen's block entity; runs on the server every tick the screen is open.
 *
 * @docParam this (machine) => machine.hasLevel
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/screens/ScreenFlagValue")
@ZenCodeType.Name("mods.mmdlib.ScreenFlagValue")
@FunctionalInterface
public interface ScriptScreenFlagValue {
    /**
     * Reads the value from the screen's block entity.
     *
     * @param blockEntity the block entity whose screen is open
     * @return the value to show
     * @docParam blockEntity machine
     */
    @ZenCodeType.Method
    boolean read(BlockEntity blockEntity);
}
