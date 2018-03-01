package com.mcmoddev.lib.energy;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import com.mcmoddev.lib.energy.rf.RFEnergySystem;
import com.mcmoddev.lib.energy.tesla.TeslaEnergySystem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/**
 * Provides registration support for {@link IEnergySystem energy system}.
 */
public final class EnergySystemRegistry {
    public static final ForgeEnergySystem FORGE_ENERGY = new ForgeEnergySystem();
    public static final TeslaEnergySystem TESLA = new TeslaEnergySystem();
    public static final RFEnergySystem RF = new RFEnergySystem();

    public static final IEnergySystem[] ALL = new IEnergySystem[] { FORGE_ENERGY, TESLA, RF };

    /**
     * Returns all the registered energy systems compatible with a provided one.
     * @param baseSystem The base energy system one is interested in.
     * @return All the registered energy systems that are compatible with base system.
     */
    public static IEnergySystem[] getAllCompatible(final IEnergySystem baseSystem) {
        return Arrays.stream(ALL)
            .filter(IEnergySystem::isAvailable)
            .filter(baseSystem::isCompatibleWith)
            .toArray(IEnergySystem[]::new);
    }

    /**
     * Looks through all registered energy systems compatible with the provided base system and tries to create
     * an adapter for the specified tile entity.
     * @param baseSystem Base energy system one is interested in.
     * @param tile Tile Entity that one wants an energy adapter for.
     * @param facing Facing of the tile entities that will be used when testing for capabilities.
     * @return An energy adapter that can be used with the tile entity. Or null if no such an adapter was found.
     */
    @Nullable
    public static IEnergyAdapter findAdapter(final IEnergySystem baseSystem, final TileEntity tile, @Nullable final EnumFacing facing) {
        return Arrays.stream(EnergySystemRegistry.getAllCompatible(baseSystem))
            .map(s -> s.createAdapter(tile, facing))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    /**
     * Looks through all registered energy systems compatible with the provided base system and tries to create
     * an adapter for the specified item stack.
     * @param baseSystem Base energy system one is interested in.
     * @param stack ItemStack that one wants an energy adapter for.
     * @return An energy adapter that can be used with the item stack. Or null if no such an adapter was found.
     */
    @Nullable
    public static IEnergyAdapter findAdapter(final IEnergySystem baseSystem, final ItemStack stack) {
        return Arrays.stream(EnergySystemRegistry.getAllCompatible(baseSystem))
            .map(s -> s.createAdapter(stack))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
}
