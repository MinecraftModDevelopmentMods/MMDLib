package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function giving a screen element a decimal number from the screen's
 * block entity; runs on the server every tick the screen is open.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ScreenDecimalValue")
@FunctionalInterface
public interface ScriptScreenDecimalValue {
    @ZenCodeType.Method
    double read(BlockEntity blockEntity);
}
