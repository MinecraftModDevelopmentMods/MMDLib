package zone.moddev.mc.lib.api.automation;

import net.minecraft.world.item.ItemStack;

/**
 * Stateless capability provider; all persistent addon/filter data belongs to
 * its installed stack.
 */
public interface InventoryAddon {
    AddonSpec specification(ItemStack stack);

    default boolean acceptsFilter(ItemStack addon, ItemStack filter) {
        var spec = specification(addon);
        var capability = filter.getCapability(AddonCapabilities.FILTER);
        return spec.filters() && capability != null
            && capability.resource(filter) == spec.resource();
    }
}
