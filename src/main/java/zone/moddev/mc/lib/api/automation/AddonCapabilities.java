package zone.moddev.mc.lib.api.automation;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.capabilities.ItemCapability;

/**
 * Register providers through RegisterCapabilitiesEvent.registerItem, including
 * third-party items.
 */
public final class AddonCapabilities {
    public static final ItemCapability<InventoryAddon, Void> ADDON = ItemCapability
        .createVoid(ResourceLocation.parse("mmdlib:inventory_addon"),
            InventoryAddon.class);
    public static final ItemCapability<InventoryFilter, Void> FILTER = ItemCapability
        .createVoid(ResourceLocation.parse("mmdlib:inventory_filter"),
            InventoryFilter.class);

    private AddonCapabilities() {
    }
}
