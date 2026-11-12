package zone.moddev.mc.lib.smoke;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * Focused real-client verification of clicks and JEI drops sharing one
 * acknowledged action.
 */
final class GuiGhostSmoke {
    private static int stage, ticks;
    private static Consumer<ItemStack> target;

    static boolean tick(MachineScreen screen) throws Exception {
        if (++ticks > 400)
            throw new AssertionError(
                "Ghost interaction stalled at stage " + stage);
        var menu = screen.getMenu();
        var ghost = menu.bound.screen().find("ghost").orElseThrow();
        var slot = menu
            .slot((zone.moddev.mc.lib.gui.model.elements.SlotElement) ghost);
        switch (stage) {
            case 0 -> {
                var gold = new ItemStack(Items.GOLD_INGOT, 32);
                target = GuiJeiSmoke.ghostTarget(screen, gold).orElseThrow(
                    () -> new AssertionError("JEI ghost target missing"));
                target.accept(gold);
                require(menu.elementPending(ghost),
                    "JEI drop did not enter typed pending state");
                target.accept(new ItemStack(Items.STONE));
                require(gold.getCount() == 32 && menu.getCarried().isEmpty(),
                    "JEI drop consumed an item");
                next();
            }
            case 1 -> {
                if (menu.elementPending(ghost)
                    || !slot.getItem().is(Items.GOLD_INGOT))
                    return false;
                require(slot.getItem().getCount() == 1,
                    "JEI ghost was not normalized");
                var mc = Minecraft.getInstance();
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList()
                        .getPlayers().getFirst();
                    player.containerMenu
                        .setCarried(new ItemStack(Items.DIAMOND, 12));
                    player.containerMenu.broadcastChanges();
                });
                next();
            }
            case 2 -> {
                if (!menu.getCarried().is(Items.DIAMOND))
                    return false;
                click(screen, ghost, 0);
                require(menu.elementPending(ghost),
                    "Ghost click bypassed typed pending state");
                next();
            }
            case 3 -> {
                if (menu.elementPending(ghost)
                    || !slot.getItem().is(Items.DIAMOND))
                    return false;
                require(
                    slot.getItem().getCount() == 1
                        && menu.getCarried().getCount() == 12,
                    "Ghost click moved real items");
                click(screen, ghost, 1);
                next();
            }
            case 4 -> {
                if (menu.elementPending(ghost) || !slot.getItem().isEmpty())
                    return false;
                require(menu.getCarried().getCount() == 12,
                    "Clearing a ghost consumed cursor items");
                click(screen,
                    menu.bound.screen().find("navigate").orElseThrow(), 0);
                next();
            }
            case 5 -> {
                if (screen.visible(ghost))
                    return false;
                require(GuiJeiSmoke
                    .ghostTarget(screen, new ItemStack(Items.GOLD_INGOT))
                    .isEmpty(), "Hidden ghost advertised a JEI target");
                target.accept(new ItemStack(Items.GOLD_INGOT));
                require(!menu.elementPending(ghost),
                    "Stale JEI target sent a hidden action");
                return true;
            }
        }
        return false;
    }

    private static void click(MachineScreen screen, Element element,
        int button) {
        var bounds = screen.bounds(element);
        screen.mouseClicked(bounds.x() + bounds.width() / 2.0,
            bounds.y() + bounds.height() / 2.0, button);
        screen.mouseReleased(bounds.x() + bounds.width() / 2.0,
            bounds.y() + bounds.height() / 2.0, button);
    }

    private static void next() {
        stage++;
        ticks = 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
