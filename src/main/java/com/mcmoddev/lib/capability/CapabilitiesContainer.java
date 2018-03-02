package com.mcmoddev.lib.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import com.mcmoddev.lib.inventory.CombinedFluidHandler;
import com.mcmoddev.lib.inventory.CombinedItemHandler;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import mcp.MethodsReturnNonnullByDefault;

/**
 * Reference implementation of an {@link ICapabilitiesContainer} that will automatically merge all {@link IItemHandler}
 * and {@link IFluidHandler} into just one capability.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CapabilitiesContainer implements ICapabilitiesContainer, ICapabilityProvider {
    private class CapabilityInfo<T> implements ICapabilityProvider {
        private final Capability<T> capability;
        private final Function<EnumFacing, T> capabilityGetter;
        private final Predicate<EnumFacing> sideFilter;

        CapabilityInfo(final Capability<T> capability, final Function<EnumFacing, T> capabilityGetter, final @Nullable Predicate<EnumFacing> sideFilter) {
            this.capability = capability;
            this.capabilityGetter = capabilityGetter;
            this.sideFilter = sideFilter;
        }

        @Override
        public boolean hasCapability(@Nonnull final Capability<?> capability, @Nullable final EnumFacing facing) {
            return ((facing == null) || (this.sideFilter == null) || this.sideFilter.test(facing))
                && (this.getCapability(capability, facing) != null);
        }

        @Nullable
        @Override
        public <C> C getCapability(@Nonnull final Capability<C> capability, @Nullable final EnumFacing facing) {
            if ((capability == this.capability) && ((this.sideFilter == null) || this.sideFilter.test(facing))) {
                //noinspection unchecked // "should" not be a problem
                return capability.cast((C)this.capabilityGetter.apply(facing));
            }
            return null;
        }
    }

    // TODO: handle these via a registry
    private final List<CapabilityInfo<IItemHandler>> itemCaps = new ArrayList<>();
    private final List<CapabilityInfo<IFluidHandler>> fluidCaps = new ArrayList<>();

    private final List<CapabilityInfo<?>> capabilities = new ArrayList<>();

    @Override
    public boolean hasCapability(final Capability<?> capability, @Nullable final EnumFacing facing) {
        return this.capabilities.stream().anyMatch(c -> ((ICapabilityProvider)c).hasCapability(capability, facing));
    }

    @Nullable
    @Override
    public <T> T getCapability(final Capability<T> capability, @Nullable final EnumFacing facing) {
        for(final ICapabilityProvider provider: this.capabilities) {
            final T thing = provider.getCapability(capability, facing);
            if (thing != null) {
                return thing;
            }
        }

        return null;
    }

    @Override
    public <T> void addCapability(final Capability<T> capability, final Function<EnumFacing, T> capabilitySupplier,
                                  @Nullable final Predicate<EnumFacing> facingFilter) {
        // TODO: handle these special cases via a registry
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (this.itemCaps.isEmpty()) {
                // first one, add cap
                this.capabilities.add(new CapabilityInfo<>(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, (facing) -> {
                    final CombinedItemHandler combined = new CombinedItemHandler();
                    for (final CapabilityInfo<IItemHandler> cap : this.itemCaps) {
                        final IItemHandler handler = cap.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing);
                        if (handler != null) {
                            combined.addItemHandler(handler);
                        }
                    }
                    return combined;
                }, null));
            }
            //noinspection unchecked // should work :|
            this.itemCaps.add(new CapabilityInfo<>(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, 
                (Function<EnumFacing, IItemHandler>)capabilitySupplier, facingFilter));
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (this.fluidCaps.isEmpty()) {
                // first one, add cap
                this.capabilities.add(new CapabilityInfo<>(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, (facing) -> {
                    final CombinedFluidHandler combined = new CombinedFluidHandler();
                    for (final CapabilityInfo<IFluidHandler> cap : this.fluidCaps) {
                        final IFluidHandler handler = cap.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing);
                        if (handler != null) {
                            combined.addFluidHandler(handler);
                        }
                    }
                    return combined;
                }, null));
            }
            //noinspection unchecked // should work :|
            this.fluidCaps.add(new CapabilityInfo<>(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                (Function<EnumFacing, IFluidHandler>)capabilitySupplier, facingFilter));
        } else {
            this.capabilities.add(new CapabilityInfo<>(capability, capabilitySupplier, facingFilter));
        }
    }
}
