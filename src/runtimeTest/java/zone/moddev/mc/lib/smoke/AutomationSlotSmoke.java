package zone.moddev.mc.lib.smoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.SideConfig;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.containers.AutomationInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.AutomationMenu;
import zone.moddev.mc.lib.gui.runtime.InventoryMenuComponent;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.MenuRequests;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

final class AutomationSlotSmoke {
    private static class Custom implements SlotElement {
        private final int index;
        int created;
        ItemStack hint = new ItemStack(Items.DIAMOND, 2);

        Custom(int index) {
            this.index = index;
        }

        public Common common() {
            return Common.empty();
        }

        public int index() {
            return index;
        }

        public SlotKind kind() {
            return SlotKind.NORMAL;
        }

        public Appearance appearance() {
            return AutomationInventoryElement.defaultSlot(index).appearance();
        }

        public MenuSlots.Role role() {
            return MenuSlots.Role.EQUIPMENT;
        }

        public ItemStack hint(ScreenHost host, String inventory) {
            require(inventory.equals("custom"), "Hint receives contributed inventory scope");
            return hint;
        }

        public int[] itemPosition(Rect bounds) {
            return new int[]{bounds.x() + 3, bounds.y() + 2};
        }

        public Slot createSlot(
            IItemHandlerModifiable storage,
            int local,
            Rect bounds,
            BooleanSupplier visible,
            BooleanSupplier interacting,
            Runnable changed) {
            created++;
            require(local == index, "Factory receives local backing index");
            return SlotElement.super.createSlot(storage, local, bounds, visible, interacting,
                changed);
        }
    }

    static void check(MachineMenu menu, Player player) {
        var group = new ResourceGroup<>(
            "custom",
            Component.literal("Custom"),
            0xabcdef,
            ResourceType.ITEMS,
            IoMode.BOTH,
            List.of(0),
            List.of(),
            new SideConfig(IoMode.BOTH));
        for (boolean client : new boolean[]{false, true}) {
            var inventory = new AutomationInventory(menu.blockEntity, List.of(group));
            var elements = new ArrayList<Custom>();
            int[] viewCalls = {0};
            var automation = new AutomationMenu(
                menu,
                inventory,
                player,
                client,
                resource -> {
                    viewCalls[0]++;
                    return new AutomationInventoryElement(
                        resource.id(),
                        index -> {
                            var element = new Custom(index);
                            elements.add(element);
                            return element;
                        }) {
                        public boolean slotVisible(SlotElement slot, Presentation context) {
                            return slot.index() != 1
                                && (slot.index() == 4 || super.slotVisible(slot, context));
                        }

                        public Object drawerValue(
                            Element element, boolean maximum, Presentation context) {
                            return maximum
                                ? 99
                                : element == this
                                    ? Component.literal("custom caption")
                                    : super.drawerValue(element, false, context);
                        }

                        public Rect drawerSlotBounds(int index) {
                            return super.drawerSlotBounds(index).offset(2, 4);
                        }
                    };
                });
            var nativeSlots = new MenuSlots();
            automation.addSlots(
                nativeSlots.registrar(slot -> slot.index = nativeSlots.entries().size()),
                menu.layout());
            require(
                viewCalls[0] == 1 && elements.size() == AutomationInventory.SLOTS_PER_GROUP,
                "Factories run once for stable per-menu slot identities");
            for (int i = 0; i < elements.size(); i++) {
                var entry = nativeSlots.entry(i);
                require(
                    entry.element() == elements.get(i) && elements.get(i).created == 1,
                    "Direct SlotElement factory is used on both sides");
                require(
                    entry.role() == MenuSlots.Role.EQUIPMENT,
                    "Automation respects element transfer role");
            }
            require(!automation.visible(0, elements.getFirst()), "Closed drawer hides slots");
            try {
                var open = AutomationMenu.class.getDeclaredField("open");
                open.setAccessible(true);
                open.setBoolean(automation, true);
                require(
                    automation.visible(0, elements.getFirst())
                        && nativeSlots.entry(0).slot().isActive(),
                    "Native and visual visibility share the policy");
                require(
                    !automation.visible(0, elements.get(1))
                        && !nativeSlots.entry(1).slot().isActive(),
                    "Custom hidden addon is inactive on both sides");
                require(
                    automation.visible(0, elements.get(4))
                        && nativeSlots.entry(4).slot().isActive(),
                    "Custom visibility can show an otherwise hidden filter slot");
                require(
                    automation.drawerValue(0, elements.getFirst(), true).equals(99),
                    "Custom maximum supplied");
                require(
                    ((Component) automation.drawerValue(0, automation.inventoryView(0), false))
                        .getString()
                        .equals("custom caption"),
                    "Custom inventory value supplied");
                ((net.minecraft.network.chat.MutableComponent) automation.presentation(0).name())
                    .append(" changed");
                require(group.name().getString().equals("Custom"),
                    "Presentation labels are detached");
                if (client) {
                    var requests = AutomationMenu.class.getDeclaredField("requests");
                    requests.setAccessible(true);
                    ((MenuRequests.Client) requests.get(automation)).begin();
                    require(
                        automation.visible(0, elements.getFirst())
                            && !nativeSlots.entry(0).slot().isActive(),
                        "Pending locks interaction without changing visibility");
                }
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            for (int x : new int[]{20, 60}) {
                automation.position(x, 30);
                for (int i = 0; i < elements.size(); i++) {
                    var xy = elements
                        .get(i)
                        .itemPosition(
                            automation.inventoryView(0).drawerSlotBounds(i).offset(x, 30));
                    require(
                        nativeSlots.entry(i).slot().x == xy[0]
                            && nativeSlots.entry(i).slot().y == xy[1],
                        "Repositioning uses the same view and element geometry as initial assembly");
                }
            }
            require(
                automation.inventoryView(0).filterToggleBounds(0)
                    .y() == automation.inventoryView(0).drawerSlotBounds(4).bottom() + 2,
                "Filter control follows custom frames");
            if (!client) {
                var hints = nativeSlots.hints((ScreenHost) menu.blockEntity);
                require(
                    hints.size() == elements.size(),
                    "All contributed elements supply hints outside screen tree");
                hints.get(0).setCount(1);
                require(elements.getFirst().hint.getCount() == 2,
                    "Hint snapshot copies source stacks");
                var field = InventoryMenuComponent.HINTS;
                var registries = player.registryAccess();
                var decoded = field.decode(
                    registries, field.encode(registries, Map.of(5000, elements.getFirst().hint)));
                require(
                    decoded.get(5000).getCount() == 2,
                    "Hints support native indexes beyond screen element limit");
                for (int invalid : new int[]{-1, 32767}) {
                    boolean rejected = false;
                    try {
                        field.encode(registries, Map.of(invalid, elements.getFirst().hint));
                    } catch (IllegalArgumentException expected) {
                        rejected = true;
                    }
                    require(rejected, "Invalid native hint index rejected");
                }
                elements.forEach(element -> element.hint = ItemStack.EMPTY);
                require(
                    nativeSlots.hints((ScreenHost) menu.blockEntity).isEmpty(),
                    "Complete snapshot clears contributed hints");
            }
        }
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
