package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function giving a screen element a whole number from the screen's
 * block entity; runs on the server every tick the screen is open.
 *
 * @docParam this (machine) => machine.blockPos.y
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/screens/ScreenWholeValue")
@ZenCodeType.Name("mods.mmdlib.ScreenWholeValue")
@FunctionalInterface
public interface ScriptScreenWholeValue {
    /**
     * Reads the value from the screen's block entity.
     *
     * @param blockEntity the block entity whose screen is open
     * @return the whole number to show
     * @docParam blockEntity machine
     */
    @ZenCodeType.Method
    long read(BlockEntity blockEntity);
}
