package zone.moddev.mc.companion.test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.gui.client.DefaultElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementFragment;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.client.SlotBehavior;
import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.model.TooltipContext;
import zone.moddev.mc.lib.gui.model.containers.AutomationInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.AutomationSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.AutomationMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;

/**
 * Hints for elements outside the screen tree, through the real server snapshot
 * and drawer.
 */
final class ContributedHintClientSmoke {
    private static volatile boolean enabled = true;
    private static int stage, frames, nativeMode;
    private static boolean tooltipChecked;
    private static final Map<Integer, EnumSet<ElementRenderers.Pass>> rendered = new HashMap<>();

    private static class ProbeSlot extends AutomationSlotElement {
        final boolean client;
        final String inventory;

        ProbeSlot(int index, boolean client, String inventory) {
            super(AutomationInventoryElement.defaultSlot(index).common(), index,
                AutomationInventoryElement.defaultSlot(index).appearance());
            this.client = client;
            this.inventory = inventory;
        }

        public zone.moddev.mc.lib.gui.layout.Rect hitBounds(
            zone.moddev.mc.lib.gui.layout.Rect bounds) {
            if (index() == 0)
                return new zone.moddev.mc.lib.gui.layout.Rect(bounds.x() - 6,
                    bounds.y(), 22, 16);
            if (index() == 2)
                return new zone.moddev.mc.lib.gui.layout.Rect(bounds.x() + 4,
                    bounds.y() + 4, 6, 6);
            return super.hitBounds(bounds);
        }

        public ItemStack hint(ScreenHost host, String name) {
            require(!client && name.equals(inventory),
                "Server-only contributed hint scope");
            return enabled && index() == 0
                ? new ItemStack(Items.DIAMOND, 2)
                : ItemStack.EMPTY;
        }

        public List<Component> tooltip(ScreenHost host, ItemStack carried) {
            require(!client,
                "Server slot tooltip is never sampled on the client");
            return List.of(Component.literal("server drawer slot"));
        }

        public void appendTooltip(TooltipContext context,
            List<Component> rows) {
            super.appendTooltip(context, rows);
            rows.add(Component.literal("client drawer slot"));
        }

        public List<Component> nativeTooltip(ItemStack stack,
            List<Component> vanilla) {
            return nativeMode == 0
                ? vanilla
                : nativeMode == 1
                    ? List.of(Component.literal("custom native row"))
                    : List.of();
        }
    }

    private static class ProbeInventory extends AutomationInventoryElement {
        final boolean client;

        ProbeInventory(String inventory, boolean client) {
            super(inventory, index -> new ProbeSlot(index, client, inventory));
            this.client = client;
        }

        public boolean slotVisible(SlotElement slot, Presentation context) {
            return slot.index() != 1 && super.slotVisible(slot, context);
        }

        public Object drawerValue(zone.moddev.mc.lib.gui.model.Element element,
            boolean maximum, Presentation context) {
            if (element == slots().get(2))
                return maximum ? 888 : 777;
            if (element == this && !maximum)
                return Component.literal("custom drawer caption");
            return super.drawerValue(element, maximum, context);
        }

        public List<Component> tooltip(ScreenHost host, ItemStack carried) {
            require(!client,
                "Server parent tooltip is never sampled on the client");
            return List.of(Component.literal("server drawer parent"));
        }

        public void appendChildTooltip(TooltipContext context,
            List<Component> rows) {
            super.appendChildTooltip(context, rows);
            rows.addAll(context.serverLines());
            rows.add(Component.literal("client drawer parent"));
        }
    }

    static void install() {
        MenuComponentRegistry.GLOBAL.replace(BuiltinMenuComponents.AUTOMATION,
            0,
            c -> c.host() == null || c.host().screenAutomation() == null
                ? null
                : new AutomationMenu(c.menu(), c.host().screenAutomation(),
                    c.inventory().player, c.client(),
                    group -> new ProbeInventory(group.id(), c.client())));
        var base = ElementRenderers.GLOBAL.require(SlotElement.class);
        for (var pass : ElementRenderers.Pass.values())
            ElementRenderers.GLOBAL.register(pass, ProbeSlot.class,
                ElementRenderers.Clip.ELEMENT, (slot, context) -> {
                    rendered.computeIfAbsent(slot.index(),
                        ignored -> EnumSet.noneOf(ElementRenderers.Pass.class))
                        .add(pass);
                    if (slot.index() == 2)
                        require(
                            context.value().equals(777)
                                && context.maximum().equals(888),
                            "Renderer receives overridden drawer values");
                    if (pass == ElementRenderers.Pass.BACKGROUND)
                        base.accept(context);
                    if (pass == ElementRenderers.Pass.TOP)
                        context.graphics().renderOutline(context.bounds().x(),
                            context.bounds().y(), context.bounds().width(),
                            context.bounds().height(), 0xffcc44ff);
                });
        ElementBehaviors.GLOBAL.register(ProbeSlot.class,
            (element, access) -> new SlotBehavior(element, access) {
                public List<Component> tooltip(int x, int y) {
                    return List.of(Component.literal("behavior drawer slot"));
                }
            });
        ElementBehaviors.GLOBAL.register(ProbeInventory.class,
            (element, access) -> new DefaultElementBehavior(access) {
                public void appendChildTooltip(TooltipContext context,
                    List<Component> rows) {
                    rows.add(Component.literal("behavior drawer parent"));
                }
            });
    }

    static void rendered(MachineScreen screen, GuiGraphics graphics) {
        frames++;
        if (stage != 3 || tooltipChecked)
            return;
        var model = screen.getMenu().components()
            .require(BuiltinMenuComponents.AUTOMATION);
        var slot = screen.getMenu().getSlot(model.slotStart());
        if (slot.getItem().isEmpty() || model.pending())
            return;
        int x = screen.guiLeft() + slot.x + 8, y = screen.guiTop() + slot.y + 8;
        nativeMode = 1;
        screen.render(graphics, x, y, 0);
        for (nativeMode = 0; nativeMode < 3; nativeMode++) {
            try {
                var method = MachineScreen.class.getDeclaredMethod(
                    "getTooltipFromContainerItem", ItemStack.class);
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                var rows = ((List<Component>) method.invoke(screen,
                    slot.getItem())).stream().map(Component::getString)
                    .toList();
                var suffix = List.of("server drawer slot", "client drawer slot",
                    "behavior drawer slot", "server drawer parent",
                    "client drawer parent", "behavior drawer parent");
                require(
                    rows.size() >= suffix.size() && rows
                        .subList(rows.size() - suffix.size(), rows.size())
                        .equals(suffix),
                    "Drawer parent/behavior/server rows preserved: " + rows);
                if (nativeMode == 1)
                    require(
                        rows.getFirst().equals("custom native row")
                            && rows.size() == suffix.size() + 1,
                        "Custom drawer native replacement");
                if (nativeMode == 2)
                    require(rows.equals(suffix),
                        "Native suppression preserves all composed rows");
                if (nativeMode == 0)
                    require(rows.size() > suffix.size(),
                        "Default native rows retained");
                if (nativeMode == 1) {
                    graphics.flush();
                    var mc = Minecraft.getInstance();
                    Screenshot.grab(mc.gameDirectory, "drawer-slot-tooltip.png",
                        mc.getMainRenderTarget(), ignored -> {
                        });
                }
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
        nativeMode = 0;
        tooltipChecked = true;
    }

    static boolean run(MachineScreen screen) {
        var menu = screen.getMenu();
        var automation = menu.components()
            .require(BuiltinMenuComponents.AUTOMATION);
        if (stage == 4)
            return !automation.pending() && !automation.open();
        var element = automation.slotElement(automation.slotStart());
        var mc = Minecraft.getInstance();
        if (automation.pending())
            return false;
        if (stage == 0) {
            if (menu.slotHint(element).isEmpty())
                return false;
            require(menu.elements.stream().noneMatch(e -> e == element),
                "Hint element is absent from screen tree");
            require(menu.nativeSlots().slot(element).getItem().isEmpty(),
                "Hints do not populate actual storage");
            require(menu.slotHint(element).getCount() == 2,
                "Contributed hint arrives through typed field");
            menu.slotHint(element).setCount(1);
            require(menu.slotHint(element).getCount() == 2,
                "Hint lookup returns detached stacks");
            var drawer = screen.drawers().entries().stream()
                .filter(d -> d.definition().type().equals(BuiltinDrawers.SIDES))
                .findFirst().orElseThrow();
            var tab = drawer.tabBounds();
            require(screen.mouseClicked(tab.x() + 2, tab.y() + 2, 0),
                "Open hint drawer through tab");
            stage = 1;
            return false;
        }
        if (stage == 1) {
            if (!automation.open())
                return false;
            if (automation.selected() != 0) {
                require(automation.send("select", 0, -1, "", 0, ""),
                    "Select hinted group");
                return false;
            }
            frames = 0;
            stage = 2;
            return false;
        }
        if (stage == 2) {
            if (frames < 3)
                return false;
            require(
                rendered
                    .getOrDefault(0,
                        EnumSet.noneOf(ElementRenderers.Pass.class))
                    .size() == ElementRenderers.Pass.values().length,
                "Custom drawer slot renderer participates in every pass");
            require(!rendered.containsKey(4),
                "Hidden filter slots are not rendered");
            require(
                !rendered.containsKey(1)
                    && !menu.getSlot(automation.slotStart() + 1).isActive(),
                "Hidden addon is neither drawn nor interactive");
            var drawer = screen.drawers().entries().stream()
                .filter(d -> d.definition().type().equals(BuiltinDrawers.SIDES))
                .findFirst().orElseThrow();
            try {
                var field = drawer.getClass().getDeclaredField("slotFragment");
                field.setAccessible(true);
                var fragment = (ElementFragment) field.get(drawer);
                var bounds = fragment.bounds(element);
                var rows = fragment.tooltip(bounds.x() + 2, bounds.y() + 2)
                    .stream().map(Component::getString).toList();
                require(
                    rows.contains("server drawer slot")
                        && rows.contains("server drawer parent")
                        && rows.contains("client drawer parent"),
                    "Empty drawer slots compose element and parent rows");
                require(
                    rows.stream()
                        .anyMatch(row -> row.startsWith(Component
                            .translatable("screen.mmdlib.automation.addon")
                            .getString() + ":")),
                    "Default addon hint is supplied by parent inventory");
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            Screenshot.grab(mc.gameDirectory, "contributed-slot-hints.png",
                mc.getMainRenderTarget(), ignored -> {
                });
            var small = menu.getSlot(automation.slotStart() + 2);
            double sx = screen.guiLeft() + small.x + 1,
                sy = screen.guiTop() + small.y + 1;
            require(
                screen.nativeSlotAt(sx, sy) == null && drawer.click(sx, sy, 0),
                "Shrunken native bounds leave frame clicks to the drawer");
            require(
                drawer.nativeTooltip(small,
                    List.of(Component.literal("vanilla")), (int) sx, (int) sy)
                    .isEmpty(),
                "Frame-only hover cannot claim a native tooltip");
            mc.getSingleplayerServer().execute(() -> {
                enabled = false;
                var player = mc.getSingleplayerServer().getPlayerList()
                    .getPlayers().getFirst();
                var serverMenu = (zone.moddev.mc.lib.gui.runtime.MachineMenu) player.containerMenu;
                serverMenu.components()
                    .require(BuiltinMenuComponents.AUTOMATION).inventory.slots()
                    .setStackInSlot(0, new ItemStack(
                        zone.moddev.mc.companion.CompanionAddons.PULLER.get()));
                serverMenu.broadcastChanges();
            });
            stage = 3;
            return false;
        }
        var slot = menu.slot(element);
        double x = screen.guiLeft() + slot.x - 4,
            y = screen.guiTop() + slot.y + 8;
        if (stage == 3) {
            if (!menu.slotHint(element).isEmpty() || !tooltipChecked)
                return false;
            require(automation.send("select", 0, -1, "", 0, ""),
                "Begin pending request");
            require(automation.visible(0, element) && !slot.isActive(),
                "Pending interaction lock is independent of rendering");
            var drawer = screen.drawers().entries().stream()
                .filter(d -> d.definition().type().equals(BuiltinDrawers.SIDES))
                .findFirst().orElseThrow();
            require(screen.nativeSlotAt(x, y) == null && drawer.click(x, y, 0),
                "Pending expanded slot hit is consumed without native interaction");
            stage = 5;
            return false;
        }
        if (stage == 5) {
            require(screen.nativeSlotAt(x, y) == slot,
                "Enlarged hit area resolves original native slot");
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
            stage = 6;
            return false;
        }
        if (stage == 6) {
            if (menu.getCarried().isEmpty() || slot.hasItem())
                return false;
            require(
                menu.getCarried()
                    .is(zone.moddev.mc.companion.CompanionAddons.PULLER.get()),
                "Click outside frame extracts the actual addon");
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
            stage = 7;
            return false;
        }
        if (!menu.getCarried().isEmpty() || !slot.hasItem())
            return false;
        require(automation.send("close", 0, -1, "", 0, ""),
            "Close drawer after native round trip");
        stage = 4;
        return false;
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
