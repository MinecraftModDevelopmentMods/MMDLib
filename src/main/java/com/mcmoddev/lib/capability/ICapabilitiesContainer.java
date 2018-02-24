package com.mcmoddev.lib.capability;

import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import mcp.MethodsReturnNonnullByDefault;

/**
 * A container to hold capabilities from multiple sources.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public interface ICapabilitiesContainer {
    /**
     * Adds a capability to this container.
     * @param capability The capability to be supported by this container.
     * @param <T> The actual capability interface.
     * @param capabilitySupplier The supplier of the actual capability interface. It's ok to return null when asked for one.
     */
    default <T> void addCapability(final Capability<T> capability, final Function<EnumFacing, T> capabilitySupplier) {
        this.addCapability(capability, capabilitySupplier, null);
    }

    /**
     * Adds a capability to this container.
     * @param capability The capability to be supported by this container.
     * @param <T> The actual capability interface.
     * @param capabilitySupplier The supplier of the actual capability interface. It's ok to return null when asked for one.
     * @param facingFilter Predicate returning true if the capability is available for a specific {@link EnumFacing}.
     */
    <T> void addCapability(final Capability<T> capability, final Function<EnumFacing, T> capabilitySupplier,
                                  @Nullable final Predicate<EnumFacing> facingFilter);
}
