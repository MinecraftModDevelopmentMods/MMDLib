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
    public static final MenuComponentKey<zone.moddev.mc.lib.gui.runtime.UpgradesMenu> UPGRADES = new MenuComponentKey<>(
        BuiltinDrawers.UPGRADES,
        zone.moddev.mc.lib.gui.runtime.UpgradesMenu.class);
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
        registry.register(UPGRADES, 15, c -> c.menu().layout().drawers()
            .stream()
            .filter(drawer -> drawer.type().equals(BuiltinDrawers.UPGRADES))
            .flatMap(drawer -> drawer.element().stream())
            .filter(
                zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement.class::isInstance)
            .map(
                zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement.class::cast)
            .findFirst()
            .map(element -> new zone.moddev.mc.lib.gui.runtime.UpgradesMenu(
                c.menu(), element))
            .orElse(null));
        registry.register(TOOLTIPS, 25, TooltipMenuComponent::new,
            TooltipMenuComponent.ROWS);
        registry.register(INVENTORY, -1000, InventoryMenuComponent::new,
            InventoryMenuComponent.HINTS);
        registry.register(RESERVATIONS, -900,
            c -> new ReservationMenuComponent(c.menu().nativeSlots(),
                c.client(), c.host() == null ? null : c.host().screenFluids()),
            ReservationMenuComponent.PLANS,
            ReservationMenuComponent.TANK_PLANS);
        registry.register(AUTOMATION, 0,
            c -> hasDrawer(c, BuiltinDrawers.SIDES) && c.host() != null
                && c.host().screenAutomation() != null
                    ? new AutomationMenu(c.menu(), c.host().screenAutomation(),
                        c.inventory().player, c.client())
                    : null);
        registry.register(REDSTONE, 10,
            c -> hasDrawer(c, BuiltinDrawers.REDSTONE) && c.host() != null
                && c.host().screenRedstone() != null
                    ? new RedstoneMenu(c.menu(), c.inventory().player,
                        c.host().screenRedstone(), c.client())
                    : null);
        registry.register(STATS, 20,
            c -> hasDrawer(c, BuiltinDrawers.STATS)
                ? new StatsMenuComponent(c)
                : null,
            StatsMenuComponent.PAGES);
        registry.register(BACKPACK, 30,
            c -> hasDrawer(c, BuiltinDrawers.BACKPACK)
                ? equipment(c, false)
                : null);
        registry.register(CURIOS, 40,
            c -> hasDrawer(c, BuiltinDrawers.CURIOS)
                ? equipment(c, true)
                : null);
    }

    private static boolean hasDrawer(MenuComponentRegistry.Context context,
        ResourceLocation type) {
        return context.menu().layout().drawers().stream()
            .anyMatch(drawer -> drawer.type().equals(type));
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
