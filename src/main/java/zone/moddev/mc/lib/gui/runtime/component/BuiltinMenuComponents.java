package zone.moddev.mc.lib.gui.runtime.component;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.runtime.AutomationMenu;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers;
import zone.moddev.mc.lib.gui.runtime.InventoryMenuComponent;
import zone.moddev.mc.lib.gui.runtime.RedstoneMenu;
import zone.moddev.mc.lib.gui.runtime.StatsMenuComponent;
import zone.moddev.mc.lib.gui.runtime.TooltipMenuComponent;
import zone.moddev.mc.lib.gui.runtime.slot.ReservationMenuComponent;

/**
 * Built-in registrations use the same component factory and lifecycle contracts
 * as mods.
 */
public final class BuiltinMenuComponents {
    public static final MenuComponentKey<TooltipMenuComponent> TOOLTIPS = new MenuComponentKey<>(
        ResourceLocation.parse("mmdlib:tooltips"), TooltipMenuComponent.class);
    public static final MenuComponentKey<ReservationMenuComponent> RESERVATIONS = new MenuComponentKey<>(
        ResourceLocation.parse("mmdlib:reservations"),
        ReservationMenuComponent.class);
    public static final MenuComponentKey<InventoryMenuComponent> INVENTORY = new MenuComponentKey<>(
        ResourceLocation.parse("mmdlib:inventory"),
        InventoryMenuComponent.class);
    public static final MenuComponentKey<StatsMenuComponent> STATS = new MenuComponentKey<>(
        BuiltinDrawers.STATS, StatsMenuComponent.class);
    public static final MenuComponentKey<AutomationMenu> AUTOMATION = new MenuComponentKey<>(
        BuiltinDrawers.SIDES, AutomationMenu.class);
    public static final MenuComponentKey<RedstoneMenu> REDSTONE = new MenuComponentKey<>(
        BuiltinDrawers.REDSTONE, RedstoneMenu.class);
    public static final MenuComponentKey<ContainerDrawerMenu> BACKPACK = new MenuComponentKey<>(
        BuiltinDrawers.BACKPACK, ContainerDrawerMenu.class),
        CURIOS = new MenuComponentKey<>(BuiltinDrawers.CURIOS,
            ContainerDrawerMenu.class);

    private BuiltinMenuComponents() {
    }

    static void register(MenuComponentRegistry registry) {
        registry.register(TOOLTIPS, 25, TooltipMenuComponent::new,
            TooltipMenuComponent.ROWS);
        registry.register(INVENTORY, -1000, InventoryMenuComponent::new,
            InventoryMenuComponent.HINTS);
        registry.register(RESERVATIONS, -900,
            c -> new ReservationMenuComponent(c.menu().nativeSlots(),
                c.client()),
            ReservationMenuComponent.PLANS);
        registry.register(AUTOMATION, 0,
            c -> c.host() != null && c.host().screenAutomation() != null
                ? new AutomationMenu(c.menu(), c.host().screenAutomation(),
                    c.inventory().player, c.client())
                : null);
        registry.register(REDSTONE, 10,
            c -> c.host() != null && c.host().screenRedstone() != null
                ? new RedstoneMenu(c.menu(), c.inventory().player,
                    c.host().screenRedstone(), c.client())
                : null);
        registry.register(STATS, 20, StatsMenuComponent::new,
            StatsMenuComponent.PAGES);
        registry.register(BACKPACK, 30, c -> equipment(c, false));
        registry.register(CURIOS, 40, c -> equipment(c, true));
    }

    private static ContainerDrawerMenu equipment(
        MenuComponentRegistry.Context context, boolean equipment) {
        var discovery = context.shared(EquippedContainers.Discovery.class,
            () -> new EquippedContainers.Discovery(context.inventory().player));
        return new ContainerDrawerMenu(context.menu(),
            context.inventory().player,
            () -> context.menu().bound.screen().playerInventory()
                ? (equipment
                    ? discovery.get().equipment()
                    : discovery.get().containers())
                : List.of(),
            equipment, discovery::invalidate);
    }
}
