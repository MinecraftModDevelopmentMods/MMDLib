package zone.moddev.mc.mmdlib.energy.util;

import zone.moddev.mc.mmdlib.energy.EnergySystemRegistry;
import zone.moddev.mc.mmdlib.energy.ForgeEnergyValue;
import zone.moddev.mc.mmdlib.energy.IEnergyValue;

@SuppressWarnings("rawtypes")
public final class EnergyValueUtils {
    protected EnergyValueUtils() {}

    public static boolean canGetForgeEnergy(final IEnergyValue value) {
        return (value instanceof ForgeEnergyValue) || value.getSystem().isCompatibleWith(EnergySystemRegistry.FORGE_ENERGY);
    }

    public static int getForgeEnergy(final IEnergyValue value) {
        if (value instanceof ForgeEnergyValue) {
            return ((ForgeEnergyValue)value).getValue();
        }
        else if (EnergySystemRegistry.FORGE_ENERGY.isCompatibleWith(value.getSystem())) {
            final ForgeEnergyValue energy = EnergySystemRegistry.FORGE_ENERGY.convertFrom(value);
            return (energy == null) ? 0 : energy.getValue();
        }

        // Treat values from an unsupported energy system as zero.
        return 0;
    }
}
