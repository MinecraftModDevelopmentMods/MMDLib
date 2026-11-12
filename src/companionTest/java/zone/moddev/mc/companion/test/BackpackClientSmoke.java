package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.capabilities.Capabilities;

import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/**
 * Real negotiated client/server coverage for the optional Curios and backpack
 * runtime fixtures.
 */
final class BackpackClientSmoke {
    private static int step;
    private static volatile boolean verified;

    static boolean run(MachineScreen screen) {
        if (!BackpackFixture.installed()) {
            require(screen.getMenu().components()
                .get(BuiltinMenuComponents.BACKPACK).state().icons().isEmpty(),
                "No backpack button without optional mods");
            return true;
        }
        var mc = Minecraft.getInstance();
        var menu = screen.getMenu();
        var drawer = menu.components().get(BuiltinMenuComponents.BACKPACK);
        if (drawer.pending())
            return false;
        var state = drawer.state();
        if (step == 5)
            return verified && state.icons().isEmpty();
        if (state.icons().isEmpty())
            return false;
        var layout = DrawerExtensionClientSmoke.containerLayout(screen, false);
        require(layout.pageSize() > 0, "Smoke window has room for storage");
        require(layout.columns() == 6, "Backpack storage wraps at six columns");
        require(
            layout.body().y() == screen.panelBounds().y() + 4
                && layout.body().bottom() == screen.panelBounds().bottom() - 4,
            "Drawer spans the full machine height");
        switch (step) {
            case 0 -> {
                require(
                    state.icons().getFirst().getHoverName().getString()
                        .equals("Copper supplies"),
                    "Actual named equipped item is the tab icon");
                click(screen, layout.tab(), 0);
                step++;
            }
            case 1 -> {
                if (!state.open() || state.pageSize() != layout.pageSize())
                    return false;
                require(state.contents().getFirst().getCount() == 24,
                    "Backpack contents reached client");
                require(screen.exclusions().contains(layout.body()),
                    "Drawer is excluded from JEI");
                require(screen.exclusions().contains(layout.rail()),
                    "Full-height icon rail is excluded from JEI");
                Screenshot.grab(mc.gameDirectory, "backpack-drawer.png",
                    mc.getMainRenderTarget(), message -> {
                    });
                click(screen, layout.cell(0), 1);
                for (int tick = 0; tick < 19; tick++)
                    drawer.tickClient();
                require(drawer.pendingInventory(),
                    "Container transfer stays locked before timeout");
                drawer.tickClient();
                require(drawer.pendingInventory(),
                    "Recovery sync preserves the inventory lock");
                step++;
            }
            case 2 -> {
                if (menu.getCarried().getCount() != 12)
                    return false;
                require(
                    menu.getCarried().is(Items.IRON_INGOT)
                        && state.contents().getFirst().getCount() == 12,
                    "Right click transfers half through the network");
                click(screen, new zone.moddev.mc.lib.gui.layout.Rect(
                    layout.rail().x(), layout.rail().y() + 60, 22, 24), 0);
                require(menu.getCarried().getCount() == 12 && !drawer.pending(),
                    "Rail clicks retain carried items");
                click(screen, layout.cell(0), 0);
                step++;
            }
            case 3 -> {
                if (!menu.getCarried().isEmpty()
                    || state.contents().getFirst().getCount() != 24)
                    return false;
                drawer
                    .request(ContainerDrawerMenu.QUICK_TAKE, 0,
                        state.pageSize())
                    .ifPresent(
                        net.neoforged.neoforge.network.PacketDistributor::sendToServer);
                step++;
            }
            case 4 -> {
                if (!BackpackInteractionClientSmoke.run(screen))
                    return false;
                if (!state.contents().getFirst().isEmpty())
                    return false;
                require(
                    mc.player.getInventory().countItem(Items.IRON_INGOT) == 24,
                    "Shift transfer reaches player inventory");
                step++;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList()
                            .getPlayers().getFirst();
                        var source = zone.moddev.mc.lib.gui.runtime.EquippedContainers
                            .find(player).getFirst();
                        var saved = source.stack()
                            .save(player.registryAccess());
                        var restored = ItemStack
                            .parse(player.registryAccess(), saved)
                            .orElseThrow();
                        require(
                            restored
                                .getCapability(Capabilities.ItemHandler.ITEM)
                                .getStackInSlot(0).isEmpty(),
                            "Modified storage survives item serialization");
                        require(
                            restored
                                .getCapability(Capabilities.ItemHandler.ITEM)
                                .getStackInSlot(1).getCount() == 12,
                            "Other storage remains intact");
                        BackpackFixture.unequip(player);
                        ((MachineMenu) player.containerMenu).components()
                            .get(BuiltinMenuComponents.BACKPACK).broadcast();
                        verified = true;
                    } catch (Throwable error) {
                        CompanionClientSmoke.fail(error);
                    }
                });
            }
            default ->
                throw new AssertionError("Unexpected backpack test step");
        }
        return false;
    }

    private static void click(MachineScreen screen,
        zone.moddev.mc.lib.gui.layout.Rect rect, int button) {
        require(screen.mouseClicked(rect.x() + 5, rect.y() + 5, button),
            "Drawer click handled");
        screen.mouseReleased(rect.x() + 5, rect.y() + 5, button);
    }

    private static void require(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }
}
