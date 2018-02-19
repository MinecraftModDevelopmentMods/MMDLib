package com.mcmoddev.lib.inventory;

import java.util.function.Consumer;
import net.minecraftforge.fluids.IFluidTank;

public interface IResponsiveFluidTank extends IFluidTank {
    <T extends IResponsiveFluidTank> void addResponsiveTarget(Consumer<T> consumer);

    // TODO: consider adding remove/clear methods
}
