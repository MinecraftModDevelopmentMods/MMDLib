package com.mcmoddev.lib.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.mcmoddev.lib.gui.IGuiHolder;
import com.mcmoddev.lib.gui.IGuiHolderProxy;

public final class GuiUtils {
    private GuiUtils() {}

    @Nullable
    public static IGuiHolder getGuiHolder(@Nonnull Object thing) {
        if (thing instanceof IGuiHolder) {
            return IGuiHolder.class.cast(thing);
        }

        if (thing instanceof IGuiHolderProxy) {
            return IGuiHolderProxy.class.cast(thing).getGuiHolder();
        }

        return null;
    }
}
