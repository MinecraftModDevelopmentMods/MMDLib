package com.mcmoddev.lib.util;

import javax.annotation.ParametersAreNonnullByDefault;
import com.mcmoddev.lib.gui.IGuiHolder;
import com.mcmoddev.lib.gui.IGuiHolderProxy;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GuiUtils {
    private GuiUtils() {}

    public static IGuiHolder getGuiHolder(Object thing) {
        if (thing instanceof IGuiHolder) {
            return IGuiHolder.class.cast(thing);
        }

        if (thing instanceof IGuiHolderProxy) {
            return IGuiHolderProxy.class.cast(thing).getGuiHolder();
        }

        return null;
    }
}
