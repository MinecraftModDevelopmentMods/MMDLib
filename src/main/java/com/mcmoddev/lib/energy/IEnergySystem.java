package com.mcmoddev.lib.energy;

import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;

/**
 * Represents an energy system wrapper (ie. RedstoneFlux, ForgeEnergy, Tesla etc).
 * @param <T> The type of the value this energy system works with.
 *           (for example: {@link Integer} for RF and ForgeEnergy, {@link Long} for Tesla)
 */
@SuppressWarnings("rawtypes")
public interface IEnergySystem<T> extends IEnergyCapabilityProvider {
    /**
     * Returns the mod id of the mod containing the interfaces for this energy system.
     * @return The mod id of the mod containing the interfaces for this energy system
     */
    String getDependencyModId();

    /**
     * Represents a value indicating if this energy system is currently available or not.
     * @return Returns true if this energy system is available.
     * @implNote By default this will check if a mod with id == {@link IEnergySystem#getDependencyModId()} is loaded.
     */
    default boolean isAvailable() {
        return Loader.isModLoaded(this.getDependencyModId());
    }

    /**
     * Creates an {@link IEnergyAdapter} that can be used to manipulate the energy contained in the target tile entity.
     * @param tile The tile entity the adapter should be created for.
     * @param facing The facing of the tile entity the adapter should be created for.
     * @return An {@link IEnergyAdapter} instance if the target tile entity supports this energy system. Null if the current energy system is not supported by the targeted tile entity and/or facing.
     */
    @Nullable
    IEnergyAdapter createAdapter(TileEntity tile, @Nullable EnumFacing facing);

    /**
     * Creates an {@link IEnergyAdapter} that can be used to manipulate the energy contained in the target item stack.
     * @param stack The item stack the adapter should be created for.
     * @return An {@link IEnergyAdapter} instance if the target tile entity supports this energy system. Null if the current energy system is not supported by the targeted tile entity and/or facing.
     */
    @Nullable
    IEnergyAdapter createAdapter(ItemStack stack);

    /**
     * Tests if the energy from this energy system can be converted from the source energy system.
     * @param system The energy system to be used as a source of energy.
     * @return True if energy from the source energy system can be converted to this system's energy. False otherwise.
     */
    boolean isCompatibleWith(IEnergySystem system);

    /**
     * Converts energy from the source energy system to this energy system.
     * @param value An energy value from the source system.
     * @return The converted energy value. Or the equivalent of 0 if conversion fails.
     * @implNote One should test if conversion is possible using {@link IEnergySystem#isCompatibleWith(IEnergySystem)} before calling this in order to prevent energy loss.
     * @implNote This method can cause energy loss. For example converting a Tesla value greater than Integer.MAX_VALUE will cause all that extra energy to be lost.
     */
    @Nullable
    IEnergyValue<T> convertFrom(IEnergyValue value);

    @Override
    default boolean hasCapability(final Capability<?> capability, final IGenericEnergyStorage storage) {
        return false;
    }

    @Nullable
    @Override
    default <C> C getCapability(final Capability<C> capability, final IGenericEnergyStorage storage) {
        return null;
    }
}
