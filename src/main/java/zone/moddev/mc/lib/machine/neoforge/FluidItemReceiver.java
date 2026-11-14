package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import zone.moddev.mc.lib.machine.feature.ItemReceiver;

/**
 * Container capability adapter; supports buckets and modded fillable/drainable
 * items.
 */
public final class FluidItemReceiver implements ItemReceiver {
    private final IFluidHandler fluids;

    public FluidItemReceiver(IFluidHandler fluids) {
        this.fluids = java.util.Objects.requireNonNull(fluids);
    }

    public boolean accepts(ItemStack offered) {
        return FluidUtil.getFluidHandler(offered.copy()).isPresent();
    }

    public Exchange receive(ItemStack offered, UseOnContext context) {
        // Match NeoForge's normal right-click preference: fill the item, then
        // try emptying it.
        var result = FluidUtil.tryFillContainer(offered, fluids,
            Integer.MAX_VALUE, context.getPlayer(), true);
        if (!result.isSuccess())
            result = FluidUtil.tryEmptyContainer(offered, fluids,
                Integer.MAX_VALUE, context.getPlayer(), true);
        return result.isSuccess()
            ? Exchange.reusable(result.getResult())
            : Exchange.reject();
    }
}
