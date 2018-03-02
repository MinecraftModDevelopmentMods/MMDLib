package com.mcmoddev.lib.inventory;

import java.util.function.Consumer;
import net.minecraftforge.fluids.IFluidTank;

/**
 * Represents a fluid tank that can notify about its content being changed.
 */
public interface IResponsiveFluidTank extends IFluidTank {
    /**
     * Adds a new listener to be notified when this tank's content changes.
     * @param consumer The callback method to be called when this tank's content changes.
     * @param <T> Usually the type of the implemented of this interface.
     */
    <T extends IResponsiveFluidTank> void addResponsiveTarget(Consumer<T> consumer);

    // TODO: consider adding remove/clear methods
}
