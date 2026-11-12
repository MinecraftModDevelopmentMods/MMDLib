package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.core.Direction;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Controller resource lookup. Return role/filter/reservation-aware views, never bypass handlers.
 */
public interface MultiblockResources {
    default IItemHandler multiblockItems(String resource, Direction worldFace) {
        return null;
    }
    default IFluidHandler multiblockFluids(String resource, Direction worldFace) {
        return null;
    }
    default IEnergyStorage multiblockEnergy(String resource, Direction worldFace) {
        return null;
    }
}
